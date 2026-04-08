package com.example.ritik_2.chat

import android.util.Log
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.SyncManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG          = "ChatRepository"
private const val COL_ROOMS    = "chat_rooms"
private const val COL_MESSAGES = "chat_messages"

@Singleton
class ChatRepository @Inject constructor(
    private val syncManager: SyncManager
) {
    // SSE client — readTimeout(0) keeps the connection open indefinitely.
    // pingInterval is intentionally NOT set: it only works for WebSocket/HTTP2.
    // PocketBase SSE runs over HTTP/1.1 — reconnect logic handles drops instead.
    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    // Regular client for uploads / short requests
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    // ── Rooms ─────────────────────────────────────────────────────────────────

    suspend fun getRooms(userId: String): List<ChatRoom> = withContext(Dispatchers.IO) {
        try {
            val token = syncManager.getAdminToken()
            val res   = syncManager.pbGet(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records" +
                        "?filter=(members~'$userId')&sort=-lastMessageAt&perPage=100", token)
            val items = JSONObject(res).optJSONArray("items") ?: return@withContext emptyList()
            (0 until items.length()).map { items.getJSONObject(it).toRoom(userId) }
        } catch (e: Exception) { Log.e(TAG, "getRooms: ${e.message}"); emptyList() }
    }

    suspend fun getOrCreateDirectRoom(
        myId: String, myName: String, myAvatarUrl: String,
        otherId: String, otherName: String, otherAvatarUrl: String,
        companyName: String
    ): ChatRoom? = withContext(Dispatchers.IO) {
        try {
            val token = syncManager.getAdminToken()
            val res   = syncManager.pbGet(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records" +
                        "?filter=(type='direct'%26%26members~'$myId'%26%26members~'$otherId')&perPage=1",
                token)
            val existing = JSONObject(res).optJSONArray("items")?.optJSONObject(0)
            if (existing != null) return@withContext existing.toRoom(myId)

            val body = JSONObject().apply {
                put("name",          "$myName & $otherName")
                put("type",          "direct")
                put("members",       JSONArray().apply { put(myId); put(otherId) }.toString())
                put("memberNames",   JSONArray().apply { put(myName); put(otherName) }.toString())
                put("memberAvatars", JSONArray().apply { put(myAvatarUrl); put(otherAvatarUrl) }.toString())
                put("adminIds",      JSONArray().apply { put(myId) }.toString())
                put("companyName",   companyName)
                put("lastMessage",   "")
                put("lastMessageAt", System.currentTimeMillis())
            }.toString()
            JSONObject(syncManager.pbPost(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records", token, body)
            ).toRoom(myId)
        } catch (e: Exception) { Log.e(TAG, "getOrCreateDirectRoom: ${e.message}"); null }
    }

    suspend fun createGroupRoom(
        name: String, creatorId: String, creatorName: String,
        memberIds: List<String>, memberNames: List<String>, memberAvatars: List<String>,
        companyName: String, avatarBytes: ByteArray? = null
    ): ChatRoom? = withContext(Dispatchers.IO) {
        try {
            val token    = syncManager.getAdminToken()
            val allIds   = (listOf(creatorId) + memberIds).distinct()
            val allNames = (listOf(creatorName) + memberNames).distinct()

            val body = JSONObject().apply {
                put("name",          name)
                put("type",          "group")
                put("members",       JSONArray(allIds).toString())
                put("memberNames",   JSONArray(allNames).toString())
                put("memberAvatars", JSONArray(memberAvatars).toString())
                put("adminIds",      JSONArray().apply { put(creatorId) }.toString())
                put("companyName",   companyName)
                put("lastMessage",   "Group created")
                put("lastMessageAt", System.currentTimeMillis())
            }.toString()

            val res  = syncManager.pbPost(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records", token, body)
            var room = JSONObject(res).toRoom(creatorId)

            if (avatarBytes != null) {
                val updated = uploadRoomAvatar(room.id, avatarBytes, token)
                if (updated != null) room = updated
            }
            sendMessage(room.id, creatorId, creatorName,
                MessageType.TEXT, "Group \"$name\" created", null, "", "")
            room
        } catch (e: Exception) { Log.e(TAG, "createGroupRoom: ${e.message}"); null }
    }

    suspend fun updateGroupInfo(roomId: String, name: String, avatarBytes: ByteArray?): ChatRoom? =
        withContext(Dispatchers.IO) {
            try {
                val token = syncManager.getAdminToken()
                if (avatarBytes != null) {
                    // Single multipart PATCH — name + avatar together
                    val mpBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("name", name)
                        .addFormDataPart("avatar", "avatar_$roomId.jpg",
                            avatarBytes.toRequestBody("image/jpeg".toMediaType()))
                        .build()
                    val res = httpClient.newCall(Request.Builder()
                        .url("${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records/$roomId")
                        .addHeader("Authorization", "Bearer $token")
                        .patch(mpBody).build()).execute()
                    val body = res.body?.string() ?: "{}"; res.close()
                    JSONObject(body).toRoom("")
                } else {
                    syncManager.pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records/$roomId",
                        token, JSONObject().apply { put("name", name) }.toString())
                    null
                }
            } catch (e: Exception) { Log.e(TAG, "updateGroupInfo: ${e.message}"); null }
        }

    suspend fun addMembers(
        roomId: String,
        currentMembers: List<String>, newIds: List<String>,
        currentNames: List<String>,   newNames: List<String>,
        currentAvatars: List<String>, newAvatars: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = syncManager.getAdminToken()
            syncManager.pbPatch(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records/$roomId", token,
                JSONObject().apply {
                    put("members",       JSONArray((currentMembers + newIds).distinct()).toString())
                    put("memberNames",   JSONArray((currentNames + newNames).distinct()).toString())
                    put("memberAvatars", JSONArray((currentAvatars + newAvatars).distinct()).toString())
                }.toString())
            true
        } catch (e: Exception) { false }
    }

    suspend fun removeMember(
        roomId: String,
        currentMembers: List<String>, currentNames: List<String>, currentAvatars: List<String>,
        removeId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val token   = syncManager.getAdminToken()
            val idx     = currentMembers.indexOf(removeId)
            val newIds  = currentMembers.filterIndexed { i, _ -> i != idx }
            val newNames  = currentNames.filterIndexed  { i, _ -> i != idx }
            val newAvatars = currentAvatars.filterIndexed { i, _ -> i != idx }
            syncManager.pbPatch(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records/$roomId", token,
                JSONObject().apply {
                    put("members",       JSONArray(newIds).toString())
                    put("memberNames",   JSONArray(newNames).toString())
                    put("memberAvatars", JSONArray(newAvatars).toString())
                }.toString())
            true
        } catch (e: Exception) { false }
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    suspend fun getMessages(roomId: String, page: Int = 1): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            try {
                val token = syncManager.getAdminToken()
                val res   = syncManager.pbGet(
                    "${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records" +
                            "?filter=(roomId='$roomId')&sort=-sentAt&perPage=50&page=$page", token)
                val items = JSONObject(res).optJSONArray("items") ?: return@withContext emptyList()
                (0 until items.length()).map { items.getJSONObject(it).toMessage() }.reversed()
            } catch (e: Exception) { Log.e(TAG, "getMessages: ${e.message}"); emptyList() }
        }

    suspend fun sendMessage(
        roomId: String, senderId: String, senderName: String,
        type: MessageType, text: String, fileBytes: ByteArray?,
        fileName: String, fileMime: String,
        replyToId: String = "", replyToText: String = "",
        senderAvatarUrl: String = ""
    ): ChatMessage? = withContext(Dispatchers.IO) {
        try {
            val token = syncManager.getAdminToken()
            val now   = System.currentTimeMillis()
            val res: String

            if (fileBytes != null && fileName.isNotBlank()) {
                // Single multipart POST — all fields + file together
                // PocketBase returns the record with server-assigned id and stored filename
                val safeMime = fileMime.ifBlank { "application/octet-stream" }
                val mpBody   = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("roomId",      roomId)
                    .addFormDataPart("senderId",    senderId)
                    .addFormDataPart("senderName",  senderName)
                    .addFormDataPart("senderAvatar",senderAvatarUrl)
                    .addFormDataPart("type",        type.name)
                    .addFormDataPart("text",        text.ifBlank { fileName })
                    .addFormDataPart("fileName",    fileName)
                    .addFormDataPart("fileSize",    fileBytes.size.toString())
                    .addFormDataPart("fileMime",    fileMime)
                    .addFormDataPart("sentAt",      now.toString())
                    .addFormDataPart("editedAt",    "0")
                    .addFormDataPart("replyToId",   replyToId)
                    .addFormDataPart("replyToText", replyToText)
                    // "file" is the PocketBase field name for the attachment
                    .addFormDataPart("file", fileName,
                        fileBytes.toRequestBody(safeMime.toMediaType()))
                    .build()

                val httpRes = httpClient.newCall(Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records")
                    .addHeader("Authorization", "Bearer $token")
                    .post(mpBody).build()).execute()
                res = httpRes.body?.string() ?: "{}"; httpRes.close()
            } else {
                val body = JSONObject().apply {
                    put("roomId",      roomId)
                    put("senderId",    senderId)
                    put("senderName",  senderName)
                    put("senderAvatar",senderAvatarUrl)
                    put("type",        type.name)
                    put("text",        text)
                    put("fileName",    "")
                    put("fileSize",    0L)
                    put("fileMime",    "")
                    put("sentAt",      now)
                    put("editedAt",    0L)
                    put("replyToId",   replyToId)
                    put("replyToText", replyToText)
                }.toString()
                res = syncManager.pbPost(
                    "${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records", token, body)
            }

            val created    = JSONObject(res)
            val recordId   = created.optString("id")
            // PocketBase stores only the filename in the "file" field — build the full URL
            val storedFile = created.optString("file", "")
            val fileUrl    = buildFileUrl(recordId, storedFile)

            syncManager.pbPatch(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records/$roomId", token,
                JSONObject().apply {
                    put("lastMessage",   if (type == MessageType.TEXT) text
                    else "📎 ${fileName.ifBlank { type.name }}")
                    put("lastMessageAt", now)
                }.toString())

            created.put("computedFileUrl", fileUrl)
            created.toMessage()
        } catch (e: Exception) { Log.e(TAG, "sendMessage: ${e.message}"); null }
    }

    suspend fun editMessage(messageId: String, newText: String, sentAt: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (System.currentTimeMillis() - sentAt > 5 * 60_000L) return@withContext false
            try {
                val token = syncManager.getAdminToken()
                syncManager.pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records/$messageId",
                    token,
                    JSONObject().apply {
                        put("text",     newText)
                        put("editedAt", System.currentTimeMillis())
                    }.toString())
                true
            } catch (e: Exception) { Log.e(TAG, "editMessage: ${e.message}"); false }
        }

    suspend fun deleteMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            syncManager.pbDelete(
                "${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records/$messageId",
                syncManager.getAdminToken())
            true
        } catch (e: Exception) { false }
    }

    // ── Realtime SSE ──────────────────────────────────────────────────────────
    // Wraps the raw SSE flow with automatic reconnect so the calling ViewModel
    // never sees a timeout — it just keeps receiving messages.

    fun subscribeToRoom(roomId: String): Flow<ChatMessage> = flow {
        while (currentCoroutineContext().isActive) {
            try {
                rawSseFlow(roomId).collect { emit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "SSE drop, reconnecting in 3s: ${e.message}")
                delay(3_000)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun rawSseFlow(roomId: String): Flow<ChatMessage> = callbackFlow {
        val token = try { syncManager.getAdminToken() }
        catch (e: Exception) { close(e); return@callbackFlow }

        // Step 1: get client ID
        val clientId = try {
            JSONObject(syncManager.pbGet("${AppConfig.BASE_URL}/api/realtime", token))
                .optString("clientId")
        } catch (e: Exception) { close(e); return@callbackFlow }

        if (clientId.isBlank()) { close(Exception("No clientId")); return@callbackFlow }

        // Step 2: subscribe
        try {
            syncManager.pbPost(
                "${AppConfig.BASE_URL}/api/realtime", token,
                JSONObject().apply {
                    put("clientId",      clientId)
                    put("subscriptions", JSONArray().apply {
                        put("$COL_MESSAGES?filter=(roomId='$roomId')")
                    }.toString())
                }.toString())
        } catch (e: Exception) { close(e); return@callbackFlow }

        // Step 3: open SSE stream
        val call = sseClient.newCall(
            Request.Builder()
                .url("${AppConfig.BASE_URL}/api/realtime?clientId=$clientId")
                .addHeader("Authorization", "Bearer $token").build()
        )
        val response = try { call.execute() }
        catch (e: Exception) { close(e); return@callbackFlow }

        val source = response.body?.source()
            ?: run { response.close(); close(Exception("No body")); return@callbackFlow }

        val job = launch(Dispatchers.IO) {
            try {
                val buf = StringBuilder()
                while (isActive) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.startsWith("data:") ->
                            buf.append(line.removePrefix("data:").trim())
                        line.isEmpty() && buf.isNotEmpty() -> {
                            val data = buf.toString(); buf.clear()
                            try {
                                val json   = JSONObject(data)
                                val action = json.optString("action")
                                if (action == "create" || action == "update") {
                                    val record = json.optJSONObject("record") ?: return@launch
                                    trySend(record.toMessage())
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) Log.w(TAG, "SSE ended: ${e.message}")
            }
        }
        awaitClose { job.cancel(); call.cancel(); response.close() }
    }

    // ── Members ───────────────────────────────────────────────────────────────

    suspend fun getCompanyMembers(sanitizedCompany: String): List<ChatMember> =
        withContext(Dispatchers.IO) {
            try {
                val token = syncManager.getAdminToken()
                val res   = syncManager.pbGet(
                    "${AppConfig.BASE_URL}/api/collections/users/records" +
                            "?filter=(sanitizedCompanyName='$sanitizedCompany'%26%26isActive=true)" +
                            "&perPage=200&sort=name", token)
                val items = JSONObject(res).optJSONArray("items") ?: return@withContext emptyList()
                (0 until items.length()).map { i ->
                    val o  = items.getJSONObject(i)
                    val uid = o.optString("id")
                    ChatMember(
                        userId           = uid,
                        name             = o.optString("name"),
                        role             = o.optString("role"),
                        department       = o.optString("department"),
                        imageUrl         = AppConfig.avatarUrl(uid, o.optString("avatar")) ?: "",
                        companyName      = o.optString("companyName"),
                        sanitizedCompany = o.optString("sanitizedCompanyName")
                    )
                }
            } catch (e: Exception) { Log.e(TAG, "getCompanyMembers: ${e.message}"); emptyList() }
        }

    suspend fun getMemberProfile(userId: String): ChatMember? = withContext(Dispatchers.IO) {
        try {
            val token = syncManager.getAdminToken()
            val o     = JSONObject(syncManager.pbGet(
                "${AppConfig.BASE_URL}/api/collections/users/records/$userId", token))
            ChatMember(
                userId           = userId,
                name             = o.optString("name"),
                role             = o.optString("role"),
                department       = o.optString("department"),
                imageUrl         = AppConfig.avatarUrl(userId, o.optString("avatar")) ?: "",
                companyName      = o.optString("companyName"),
                sanitizedCompany = o.optString("sanitizedCompanyName")
            )
        } catch (e: Exception) { Log.e(TAG, "getMemberProfile: ${e.message}"); null }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Build the PocketBase file URL from a record ID and stored filename. */
    fun buildFileUrl(recordId: String, storedFile: String): String {
        if (recordId.isBlank() || storedFile.isBlank()) return ""
        return "${AppConfig.BASE_URL}/api/files/$COL_MESSAGES/$recordId/$storedFile"
    }

    private fun uploadRoomAvatar(roomId: String, bytes: ByteArray, token: String): ChatRoom? {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("avatar", "avatar_$roomId.jpg",
                bytes.toRequestBody("image/jpeg".toMediaType())).build()
        val res  = httpClient.newCall(Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records/$roomId")
            .addHeader("Authorization", "Bearer $token")
            .patch(body).build()).execute()
        val resBody = res.body?.string() ?: "{}"; res.close()
        return try { JSONObject(resBody).toRoom("") } catch (_: Exception) { null }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun JSONObject.toRoom(myId: String): ChatRoom {
        val id      = optString("id")
        val members = parseJsonArray(optString("members", "[]"))
        val type    = if (optString("type") == "direct") RoomType.DIRECT else RoomType.GROUP
        // Avatar: PocketBase stores filename, build full URL
        val avatarFile = optString("avatar", "")
        val avatarUrl  = if (avatarFile.isNotBlank())
            "${AppConfig.BASE_URL}/api/files/$COL_ROOMS/$id/$avatarFile"
        else optString("avatarUrl", "")
        return ChatRoom(
            id            = id,
            name          = optString("name"),
            type          = type,
            avatarUrl     = avatarUrl,
            members       = members,
            memberNames   = parseJsonArray(optString("memberNames", "[]")),
            memberAvatars = parseJsonArray(optString("memberAvatars", "[]")),
            adminIds      = parseJsonArray(optString("adminIds", "[]")),
            companyName   = optString("companyName"),
            lastMessage   = optString("lastMessage"),
            lastMessageAt = optLong("lastMessageAt", 0L),
            unreadCount   = optInt("unreadCount", 0)
        )
    }

    private fun JSONObject.toMessage(): ChatMessage {
        val id         = optString("id")
        val storedFile = optString("file", "")
        // Prefer pre-computed URL, fall back to building from stored filename
        val fileUrl    = optString("computedFileUrl", "").ifBlank {
            buildFileUrl(id, storedFile)
        }
        val typeStr = optString("type", "TEXT")
        val type    = try { MessageType.valueOf(typeStr) } catch (_: Exception) { MessageType.TEXT }
        return ChatMessage(
            id          = id,
            roomId      = optString("roomId"),
            senderId    = optString("senderId"),
            senderName  = optString("senderName"),
            senderAvatar= optString("senderAvatar", ""),
            type        = type,
            text        = optString("text"),
            fileUrl     = fileUrl,
            fileName    = optString("fileName"),
            fileSize    = optLong("fileSize", 0L),
            fileMime    = optString("fileMime"),
            sentAt      = optLong("sentAt", System.currentTimeMillis()),
            editedAt    = optLong("editedAt", 0L),
            replyToId   = optString("replyToId"),
            replyToText = optString("replyToText")
        )
    }

    private fun parseJsonArray(json: String): List<String> = try {
        val a = JSONArray(json); List(a.length()) { a.optString(it) }
    } catch (_: Exception) { emptyList() }
}