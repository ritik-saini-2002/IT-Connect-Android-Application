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
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG           = "ChatRepository"
private const val COL_ROOMS     = "chat_rooms"
private const val COL_MESSAGES  = "chat_messages"

@Singleton
class ChatRepository @Inject constructor(
    private val syncManager: SyncManager
) {
    // Long-lived SSE client for realtime
    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // infinite — SSE never closes
        .build()

    // ── Rooms ─────────────────────────────────────────────────────────────────

    suspend fun getRooms(userId: String): List<ChatRoom> = withContext(Dispatchers.IO) {
        try {
            val token = syncManager.getAdminToken()
            val res   = syncManager.pbGet(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records" +
                        "?filter=(members~'$userId')&sort=-lastMessageAt&perPage=100",
                token
            )
            val items = JSONObject(res).optJSONArray("items") ?: return@withContext emptyList()
            (0 until items.length()).map { i -> items.getJSONObject(i).toRoom(userId) }
        } catch (e: Exception) {
            Log.e(TAG, "getRooms: ${e.message}")
            emptyList()
        }
    }

    suspend fun getOrCreateDirectRoom(
        myId: String, myName: String,
        otherId: String, otherName: String,
        companyName: String
    ): ChatRoom? = withContext(Dispatchers.IO) {
        try {
            val token = syncManager.getAdminToken()
            // Check if DM room already exists between these two users
            val res   = syncManager.pbGet(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records" +
                        "?filter=(type='direct'%26%26members~'$myId'%26%26members~'$otherId')&perPage=1",
                token
            )
            val existing = JSONObject(res).optJSONArray("items")?.optJSONObject(0)
            if (existing != null) return@withContext existing.toRoom(myId)

            // Create new DM room
            val membersJson = JSONArray().apply { put(myId); put(otherId) }.toString()
            val body = JSONObject().apply {
                put("name",         "$myName & $otherName")
                put("type",         "direct")
                put("members",      membersJson)
                put("memberNames",  JSONArray().apply { put(myName); put(otherName) }.toString())
                put("adminIds",     JSONArray().apply { put(myId) }.toString())
                put("companyName",  companyName)
                put("lastMessage",  "")
                put("lastMessageAt", System.currentTimeMillis())
            }.toString()

            val created = syncManager.pbPost(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records", token, body)
            JSONObject(created).toRoom(myId)
        } catch (e: Exception) {
            Log.e(TAG, "getOrCreateDirectRoom: ${e.message}")
            null
        }
    }

    suspend fun createGroupRoom(
        name       : String,
        creatorId  : String,
        memberIds  : List<String>,
        memberNames: List<String>,
        companyName: String,
        avatarBytes: ByteArray? = null
    ): ChatRoom? = withContext(Dispatchers.IO) {
        try {
            val token   = syncManager.getAdminToken()
            val allIds  = (listOf(creatorId) + memberIds).distinct()
            val allNames= (listOf("") + memberNames).distinct() // creator name filled below

            val body = JSONObject().apply {
                put("name",          name)
                put("type",          "group")
                put("members",       JSONArray(allIds).toString())
                put("memberNames",   JSONArray(memberNames).toString())
                put("adminIds",      JSONArray().apply { put(creatorId) }.toString())
                put("companyName",   companyName)
                put("lastMessage",   "Group created")
                put("lastMessageAt", System.currentTimeMillis())
            }.toString()

            val res     = syncManager.pbPost(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records", token, body)
            val room    = JSONObject(res).toRoom(creatorId)

            // Upload group avatar if provided
            if (avatarBytes != null) {
                uploadRoomAvatar(room.id, avatarBytes, token)
            }

            // Post system message
            sendMessage(room.id, creatorId, "You",
                MessageType.TEXT, "Group \"$name\" created", null, "", "")

            room
        } catch (e: Exception) {
            Log.e(TAG, "createGroupRoom: ${e.message}")
            null
        }
    }

    suspend fun updateGroupInfo(
        roomId     : String,
        name       : String,
        avatarBytes: ByteArray?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = syncManager.getAdminToken()
            syncManager.pbPatch(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records/$roomId",
                token,
                JSONObject().apply { put("name", name) }.toString()
            )
            if (avatarBytes != null) uploadRoomAvatar(roomId, avatarBytes, token)
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateGroupInfo: ${e.message}")
            false
        }
    }

    suspend fun addMembers(roomId: String, currentMembers: List<String>,
                           newIds: List<String>, newNames: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val token   = syncManager.getAdminToken()
                val allIds  = (currentMembers + newIds).distinct()
                syncManager.pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records/$roomId",
                    token,
                    JSONObject().apply {
                        put("members", JSONArray(allIds).toString())
                    }.toString()
                )
                true
            } catch (e: Exception) { false }
        }

    suspend fun removeMember(roomId: String, currentMembers: List<String>,
                             removeId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token  = syncManager.getAdminToken()
            val newIds = currentMembers.filter { it != removeId }
            syncManager.pbPatch(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records/$roomId",
                token,
                JSONObject().apply {
                    put("members", JSONArray(newIds).toString())
                }.toString()
            )
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
                            "?filter=(roomId='$roomId')&sort=-sentAt&perPage=50&page=$page",
                    token
                )
                val items = JSONObject(res).optJSONArray("items") ?: return@withContext emptyList()
                (0 until items.length()).map { i -> items.getJSONObject(i).toMessage() }
                    .reversed()   // oldest first for display
            } catch (e: Exception) {
                Log.e(TAG, "getMessages: ${e.message}")
                emptyList()
            }
        }

    suspend fun sendMessage(
        roomId     : String,
        senderId   : String,
        senderName : String,
        type       : MessageType,
        text       : String,
        fileBytes  : ByteArray?,
        fileName   : String,
        fileMime   : String,
        replyToId  : String = "",
        replyToText: String = ""
    ): ChatMessage? = withContext(Dispatchers.IO) {
        try {
            val token = syncManager.getAdminToken()
            val msgId = java.util.UUID.randomUUID().toString()
            val now   = System.currentTimeMillis()

            var fileUrl  = ""
            var fileSize = 0L

            // Upload attachment first if present
            if (fileBytes != null && fileName.isNotBlank()) {
                val uploadRes = uploadMessageFile(msgId, fileBytes, fileName, fileMime, token)
                fileUrl  = uploadRes.first
                fileSize = uploadRes.second
            }

            val body = JSONObject().apply {
                put("roomId",      roomId)
                put("senderId",    senderId)
                put("senderName",  senderName)
                put("type",        type.name)
                put("text",        text)
                put("fileUrl",     fileUrl)
                put("fileName",    fileName)
                put("fileSize",    fileSize)
                put("fileMime",    fileMime)
                put("sentAt",      now)
                put("replyToId",   replyToId)
                put("replyToText", replyToText)
            }.toString()

            val res = syncManager.pbPost(
                "${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records", token, body)

            // Update room's last message
            syncManager.pbPatch(
                "${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records/$roomId",
                token,
                JSONObject().apply {
                    put("lastMessage",   if (type == MessageType.TEXT) text
                    else "📎 ${fileName.ifBlank { type.name }}")
                    put("lastMessageAt", now)
                }.toString()
            )

            JSONObject(res).toMessage()
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage: ${e.message}")
            null
        }
    }

    suspend fun deleteMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = syncManager.getAdminToken()
            syncManager.pbDelete(
                "${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records/$messageId", token)
            true
        } catch (e: Exception) { false }
    }

    // ── PocketBase Realtime (SSE) ─────────────────────────────────────────────

    /**
     * Subscribes to new messages in [roomId] via PocketBase SSE realtime.
     * Emits each new [ChatMessage] as it arrives.
     * The flow is cancelled when the coroutine scope is cancelled.
     */
    fun subscribeToRoom(roomId: String): Flow<ChatMessage> = callbackFlow {
        val token = try { syncManager.getAdminToken() } catch (e: Exception) {
            close(e); return@callbackFlow
        }

        // Step 1: Get a client ID from PocketBase
        val clientId = try {
            val initRes = syncManager.pbGet(
                "${AppConfig.BASE_URL}/api/realtime", token)
            JSONObject(initRes).optString("clientId")
        } catch (e: Exception) { close(e); return@callbackFlow }

        // Step 2: Subscribe to the messages collection filtered by roomId
        try {
            syncManager.pbPost(
                "${AppConfig.BASE_URL}/api/realtime",
                token,
                JSONObject().apply {
                    put("clientId",      clientId)
                    put("subscriptions", JSONArray().apply {
                        put("$COL_MESSAGES?filter=(roomId='$roomId')")
                    }.toString())
                }.toString()
            )
        } catch (e: Exception) {
            close(e); return@callbackFlow
        }

        // Step 3: Open SSE stream
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/api/realtime?clientId=$clientId")
            .addHeader("Authorization", "Bearer $token")
            .build()

        val call = sseClient.newCall(request)
        val response = try { call.execute() } catch (e: Exception) { close(e); return@callbackFlow }

        val source = response.body?.source() ?: run { close(); return@callbackFlow }

        // Read SSE events line by line
        val job = launch(Dispatchers.IO) {
            try {
                val buffer = StringBuilder()
                while (isActive) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.startsWith("data:") -> {
                            buffer.append(line.removePrefix("data:").trim())
                        }
                        line.isEmpty() && buffer.isNotEmpty() -> {
                            // Full SSE event received
                            val data = buffer.toString()
                            buffer.clear()
                            try {
                                val json   = JSONObject(data)
                                val action = json.optString("action")
                                if (action == "create") {
                                    val record = json.optJSONObject("record") ?: continue
                                    trySend(record.toMessage())
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) Log.w(TAG, "SSE stream ended: ${e.message}")
            }
        }

        awaitClose {
            job.cancel()
            call.cancel()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    // ── Members (company-scoped) ───────────────────────────────────────────────

    suspend fun getCompanyMembers(sanitizedCompany: String): List<ChatMember> =
        withContext(Dispatchers.IO) {
            try {
                val token = syncManager.getAdminToken()
                val res   = syncManager.pbGet(
                    "${AppConfig.BASE_URL}/api/collections/users/records" +
                            "?filter=(sanitizedCompanyName='$sanitizedCompany'%26%26isActive=true)" +
                            "&perPage=200&sort=name",
                    token
                )
                val items = JSONObject(res).optJSONArray("items") ?: return@withContext emptyList()
                (0 until items.length()).map { i ->
                    val o       = items.getJSONObject(i)
                    val profile = try {
                        val raw = o.optString("profile", "{}")
                        JSONObject(if (raw.isBlank()) "{}" else raw)
                    } catch (_: Exception) { JSONObject() }
                    ChatMember(
                        userId      = o.optString("id"),
                        name        = o.optString("name"),
                        role        = o.optString("role"),
                        department  = o.optString("department"),
                        imageUrl    = profile.optString("imageUrl", ""),
                        companyName = o.optString("companyName"),
                        sanitizedCompany = o.optString("sanitizedCompanyName")
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "getCompanyMembers: ${e.message}")
                emptyList()
            }
        }

    // ── File upload ───────────────────────────────────────────────────────────

    private fun uploadMessageFile(
        msgId   : String,
        bytes   : ByteArray,
        filename: String,
        mime    : String,
        token   : String
    ): Pair<String, Long> {
        val safeMime = mime.ifBlank { "application/octet-stream" }
        val body     = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", filename, bytes.toRequestBody(safeMime.toMediaType()))
            .build()
        val res = sseClient.newCall(
            Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records")
                .addHeader("Authorization", "Bearer $token")
                .post(body).build()
        ).execute()
        val resBody = res.body?.string() ?: "{}"
        res.close()
        val storedFile = JSONObject(resBody).optString("file", filename)
        val url = "${AppConfig.BASE_URL}/api/files/$COL_MESSAGES/$msgId/$storedFile"
        return url to bytes.size.toLong()
    }

    private fun uploadRoomAvatar(roomId: String, bytes: ByteArray, token: String) {
        val body = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("avatar", "avatar_$roomId.jpg",
                bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        sseClient.newCall(
            Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records/$roomId")
                .addHeader("Authorization", "Bearer $token")
                .patch(body).build()
        ).execute().close()
    }

    // ── JSON → Model helpers ──────────────────────────────────────────────────

    private fun JSONObject.toRoom(myId: String): ChatRoom {
        val members = parseJsonArray(optString("members", "[]"))
        val type    = if (optString("type") == "direct") RoomType.DIRECT else RoomType.GROUP
        return ChatRoom(
            id            = optString("id"),
            name          = optString("name"),
            type          = type,
            avatarUrl     = optString("avatarUrl", ""),
            members       = members,
            memberNames   = parseJsonArray(optString("memberNames", "[]")),
            adminIds      = parseJsonArray(optString("adminIds", "[]")),
            companyName   = optString("companyName"),
            lastMessage   = optString("lastMessage"),
            lastMessageAt = optLong("lastMessageAt", 0L),
            unreadCount   = optInt("unreadCount", 0)
        )
    }

    private fun JSONObject.toMessage(): ChatMessage {
        val typeStr = optString("type", "TEXT")
        val type    = try { MessageType.valueOf(typeStr) } catch (_: Exception) { MessageType.TEXT }
        return ChatMessage(
            id           = optString("id"),
            roomId       = optString("roomId"),
            senderId     = optString("senderId"),
            senderName   = optString("senderName"),
            type         = type,
            text         = optString("text"),
            fileUrl      = optString("fileUrl"),
            fileName     = optString("fileName"),
            fileSize     = optLong("fileSize", 0L),
            fileMime     = optString("fileMime"),
            sentAt       = optLong("sentAt", System.currentTimeMillis()),
            replyToId    = optString("replyToId"),
            replyToText  = optString("replyToText")
        )
    }

    private fun parseJsonArray(json: String): List<String> = try {
        val arr = JSONArray(json); List(arr.length()) { arr.optString(it) }
    } catch (_: Exception) { emptyList() }
}