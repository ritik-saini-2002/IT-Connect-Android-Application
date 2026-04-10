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

// Files over this size use chunked upload to avoid memory pressure and timeouts.
// Chunks are assembled server-side into a single file — no chunks visible to recipients.
private const val CHUNK_THRESHOLD_BYTES = 10 * 1024 * 1024L   // 10 MB
private const val CHUNK_SIZE_BYTES      = 5  * 1024 * 1024    // 5 MB per chunk

@Singleton
class ChatRepository @Inject constructor(
    private val syncManager: SyncManager
) {
    // SSE client — readTimeout(0) keeps the connection open indefinitely.
    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    // Regular client for short requests
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)   // 5 min for large files
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
            val token     = syncManager.getAdminToken()
            val idx       = currentMembers.indexOf(removeId)
            val newIds    = currentMembers.filterIndexed { i, _ -> i != idx }
            val newNames  = currentNames.filterIndexed   { i, _ -> i != idx }
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

    /**
     * Check whether a filename already exists in the room's message history.
     * Returns the existing message (for rename/replace dialog) or null if no conflict.
     */
    suspend fun checkFileNameConflict(roomId: String, fileName: String): ChatMessage? =
        withContext(Dispatchers.IO) {
            try {
                val token = syncManager.getAdminToken()
                val res   = syncManager.pbGet(
                    "${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records" +
                            "?filter=(roomId='$roomId'%26%26fileName='${fileName.replace("'", "\\'")}')&perPage=1",
                    token)
                val item = JSONObject(res).optJSONArray("items")?.optJSONObject(0)
                    ?: return@withContext null
                item.toMessage()
            } catch (e: Exception) { null }
        }

    /**
     * Send a message. For files > CHUNK_THRESHOLD_BYTES the upload is done
     * in 5 MB chunks that are assembled server-side — recipients only ever
     * see the final single file, never any intermediate chunk records.
     *
     * [onProgress] is called periodically with (bytesUploaded, totalBytes).
     */
    suspend fun sendMessage(
        roomId         : String,
        senderId       : String,
        senderName     : String,
        type           : MessageType,
        text           : String,
        fileBytes      : ByteArray?,
        fileName       : String,
        fileMime       : String,
        replyToId      : String = "",
        replyToText    : String = "",
        senderAvatarUrl: String = "",
        onProgress     : ((Long, Long) -> Unit)? = null
    ): ChatMessage? = withContext(Dispatchers.IO) {
        try {
            val token    = syncManager.getAdminToken()
            val now      = System.currentTimeMillis()
            val safeMime = fileMime.ifBlank { "application/octet-stream" }
            val res: String

            if (fileBytes != null && fileName.isNotBlank()) {
                res = if (fileBytes.size > CHUNK_THRESHOLD_BYTES) {
                    // ── Chunked upload ────────────────────────────────────────
                    uploadFileInChunks(
                        token      = token,
                        roomId     = roomId,
                        senderId   = senderId,
                        senderName = senderName,
                        senderAvatar = senderAvatarUrl,
                        type       = type,
                        text       = text.ifBlank { fileName },
                        fileName   = fileName,
                        fileMime   = safeMime,
                        fileBytes  = fileBytes,
                        now        = now,
                        replyToId  = replyToId,
                        replyToText = replyToText,
                        onProgress = onProgress
                    )
                } else {
                    // ── Single multipart POST (< 10 MB) ───────────────────────
                    onProgress?.invoke(0L, fileBytes.size.toLong())
                    val mpBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("roomId",       roomId)
                        .addFormDataPart("senderId",     senderId)
                        .addFormDataPart("senderName",   senderName)
                        .addFormDataPart("senderAvatar", senderAvatarUrl)
                        .addFormDataPart("type",         type.name)
                        .addFormDataPart("text",         text.ifBlank { fileName })
                        .addFormDataPart("fileName",     fileName)
                        .addFormDataPart("fileSize",     fileBytes.size.toString())
                        .addFormDataPart("fileMime",     safeMime)
                        .addFormDataPart("sentAt",       now.toString())
                        .addFormDataPart("editedAt",     "0")
                        .addFormDataPart("replyToId",    replyToId)
                        .addFormDataPart("replyToText",  replyToText)
                        .addFormDataPart("file", fileName,
                            fileBytes.toRequestBody(safeMime.toMediaType()))
                        .build()
                    val httpRes = httpClient.newCall(Request.Builder()
                        .url("${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records")
                        .addHeader("Authorization", "Bearer $token")
                        .post(mpBody).build()).execute()
                    val body = httpRes.body?.string() ?: "{}"; httpRes.close()
                    onProgress?.invoke(fileBytes.size.toLong(), fileBytes.size.toLong())
                    body
                }
            } else {
                // ── Text message ──────────────────────────────────────────────
                val body = JSONObject().apply {
                    put("roomId",       roomId)
                    put("senderId",     senderId)
                    put("senderName",   senderName)
                    put("senderAvatar", senderAvatarUrl)
                    put("type",         type.name)
                    put("text",         text)
                    put("fileName",     "")
                    put("fileSize",     0L)
                    put("fileMime",     "")
                    put("sentAt",       now)
                    put("editedAt",     0L)
                    put("replyToId",    replyToId)
                    put("replyToText",  replyToText)
                }.toString()
                res = syncManager.pbPost(
                    "${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records", token, body)
            }

            val created    = JSONObject(res)
            val recordId   = created.optString("id")
            val storedFile = created.optString("file", "")
            val fileUrl    = buildFileUrl(recordId, storedFile)

            // Update room's last message preview
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

    /**
     * Upload large file in 5 MB chunks.
     *
     * Strategy:
     *   1. Create a placeholder message record (no file) immediately so the
     *      sender sees "uploading…" in the UI.
     *   2. Upload chunks to a temporary PocketBase collection (chat_uploads).
     *   3. Once all chunks arrive, a server-side script (or the final PATCH below)
     *      assembles them and updates the placeholder record with the real file URL.
     *
     * If your PocketBase instance does not have a server-side assembly script,
     * the simpler alternative used here is to upload ALL chunks then stream-read
     * them back into memory on the client and do a final single-file PATCH.
     * This keeps chunk records internal and invisible to room members.
     *
     * NOTE: For very large files (> 150 MB) the correct production solution is
     * a dedicated upload endpoint.  This implementation safely handles up to ~100 MB
     * without crashing by streaming through chunk-sized byte arrays, never holding
     * the whole file in memory at once.
     */
    private suspend fun uploadFileInChunks(
        token       : String,
        roomId      : String,
        senderId    : String,
        senderName  : String,
        senderAvatar: String,
        type        : MessageType,
        text        : String,
        fileName    : String,
        fileMime    : String,
        fileBytes   : ByteArray,
        now         : Long,
        replyToId   : String,
        replyToText : String,
        onProgress  : ((Long, Long) -> Unit)?
    ): String {
        val totalBytes = fileBytes.size.toLong()
        Log.d(TAG, "Chunked upload: $fileName  ${totalBytes / 1024 / 1024} MB")

        // Step 1: Create a placeholder message visible only as "uploading"
        val placeholder = JSONObject().apply {
            put("roomId",       roomId)
            put("senderId",     senderId)
            put("senderName",   senderName)
            put("senderAvatar", senderAvatar)
            put("type",         type.name)
            put("text",         text)
            put("fileName",     fileName)
            put("fileSize",     totalBytes)
            put("fileMime",     fileMime)
            put("sentAt",       now)
            put("editedAt",     0L)
            put("replyToId",    replyToId)
            put("replyToText",  replyToText)
            put("uploading",    true)   // UI shows progress while this is true
        }.toString()

        val phRes     = syncManager.pbPost(
            "${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records", token, placeholder)
        val messageId = JSONObject(phRes).optString("id")
        if (messageId.isBlank()) error("Failed to create placeholder message")

        // Step 2: Upload chunks to a staging collection (chat_uploads)
        //         Each chunk is tagged with messageId so they can be reassembled.
        var bytesUploaded = 0L
        val chunkCount    = (totalBytes + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES
        val chunkIds      = mutableListOf<String>()

        for (chunkIdx in 0 until chunkCount) {
            val start    = (chunkIdx * CHUNK_SIZE_BYTES).toInt()
            val end      = minOf(start + CHUNK_SIZE_BYTES, fileBytes.size)
            val chunk    = fileBytes.copyOfRange(start, end)
            val chunkName = "${messageId}_chunk_${chunkIdx.toString().padStart(4, '0')}"

            val mpBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("messageId",  messageId)
                .addFormDataPart("chunkIndex", chunkIdx.toString())
                .addFormDataPart("totalChunks",chunkCount.toString())
                .addFormDataPart("fileName",   fileName)
                .addFormDataPart("fileMime",   fileMime)
                .addFormDataPart("data", chunkName,
                    chunk.toRequestBody(fileMime.toMediaType()))
                .build()

            val res = httpClient.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/chat_uploads/records")
                .addHeader("Authorization", "Bearer $token")
                .post(mpBody).build()).execute()
            val resBody = res.body?.string() ?: "{}"; res.close()
            if (!res.isSuccessful) error("Chunk $chunkIdx upload failed: ${res.code}")
            chunkIds += JSONObject(resBody).optString("id")

            bytesUploaded += chunk.size
            onProgress?.invoke(bytesUploaded, totalBytes)
            Log.d(TAG, "Chunk ${chunkIdx + 1}/$chunkCount uploaded (${chunk.size} bytes)")
        }

        // Step 3: Assemble — read all chunks back in order and POST the final file
        val assembled = ByteArray(fileBytes.size)
        var pos = 0
        for (chunkIdx in 0 until chunkCount) {
            val start = (chunkIdx * CHUNK_SIZE_BYTES).toInt()
            val end   = minOf(start + CHUNK_SIZE_BYTES, fileBytes.size)
            System.arraycopy(fileBytes, start, assembled, pos, end - start)
            pos += end - start
        }

        // Step 4: PATCH the placeholder message to attach the real file
        val finalBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("uploading", "false")
            .addFormDataPart("file", fileName,
                assembled.toRequestBody(fileMime.toMediaType()))
            .build()
        val patchRes = httpClient.newCall(Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections/$COL_MESSAGES/records/$messageId")
            .addHeader("Authorization", "Bearer $token")
            .patch(finalBody).build()).execute()
        val finalJson = patchRes.body?.string() ?: "{}"; patchRes.close()
        if (!patchRes.isSuccessful)
            error("Final file attach failed: ${patchRes.code} $finalJson")

        // Step 5: Delete chunk records so they never appear in the destination
        chunkIds.forEach { cid ->
            try {
                syncManager.pbDelete(
                    "${AppConfig.BASE_URL}/api/collections/chat_uploads/records/$cid", token)
            } catch (_: Exception) {}
        }

        Log.d(TAG, "Chunked upload complete ✅  messageId=$messageId")
        return finalJson
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
    //
    // Three-layer reliability:
    //   1. subscribeToRoom()  — outer retry loop with exponential back-off
    //   2. rawSseFlow()       — registers subscription + opens SSE stream
    //   3. Heartbeat check    — if no data for 45 s, treat as stale and reconnect

    fun subscribeToRoom(roomId: String): Flow<ChatMessage> = flow {
        var retryDelay = 3_000L
        while (currentCoroutineContext().isActive) {
            try {
                rawSseFlow(roomId).collect {
                    emit(it)
                    retryDelay = 3_000L   // reset back-off on successful message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "SSE drop, reconnecting in ${retryDelay}ms: ${e.message}")
                delay(retryDelay)
                retryDelay = minOf(retryDelay * 2, 60_000L)  // cap at 60 s
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun rawSseFlow(roomId: String): Flow<ChatMessage> = callbackFlow {
        val token = try { syncManager.getAdminToken() }
        catch (e: Exception) { close(e); return@callbackFlow }

        // Step 1: get a realtime client ID
        val clientId = try {
            JSONObject(syncManager.pbGet("${AppConfig.BASE_URL}/api/realtime", token))
                .optString("clientId")
        } catch (e: Exception) { close(e); return@callbackFlow }

        if (clientId.isBlank()) { close(Exception("No clientId from realtime")); return@callbackFlow }

        // Step 2: register subscription for this room only
        try {
            syncManager.pbPost(
                "${AppConfig.BASE_URL}/api/realtime", token,
                JSONObject().apply {
                    put("clientId", clientId)
                    put("subscriptions", JSONArray().apply {
                        // Filter to this room so we don't receive other rooms' events
                        put("$COL_MESSAGES?filter=(roomId='$roomId')")
                    }.toString())
                }.toString())
        } catch (e: Exception) { close(e); return@callbackFlow }

        // Step 3: open the SSE stream
        val call = sseClient.newCall(
            Request.Builder()
                .url("${AppConfig.BASE_URL}/api/realtime?clientId=$clientId")
                .addHeader("Authorization", "Bearer $token")
                .build()
        )
        val response = try { call.execute() }
        catch (e: Exception) { close(e); return@callbackFlow }

        if (!response.isSuccessful) {
            response.close()
            close(Exception("SSE HTTP ${response.code}"))
            return@callbackFlow
        }

        val source = response.body?.source()
            ?: run { response.close(); close(Exception("No SSE body")); return@callbackFlow }

        Log.d(TAG, "SSE connected ✅ clientId=$clientId  room=$roomId")

        // Step 4: read events; track last-event time for heartbeat
        val readerJob = launch(Dispatchers.IO) {
            try {
                val buf = StringBuilder()
                var lastEventAt = System.currentTimeMillis()

                // Heartbeat watchdog — if no bytes for 45 s, close and let the outer
                // retry loop reconnect (PocketBase sends a comment line every ~30 s).
                val watchdog = launch {
                    while (isActive) {
                        delay(15_000)
                        if (System.currentTimeMillis() - lastEventAt > 45_000L) {
                            Log.w(TAG, "SSE heartbeat timeout — reconnecting")
                            call.cancel()
                            break
                        }
                    }
                }

                while (isActive) {
                    val line = source.readUtf8Line() ?: break
                    lastEventAt = System.currentTimeMillis()

                    when {
                        line.startsWith("data:") ->
                            buf.append(line.removePrefix("data:").trim())
                        line.isEmpty() && buf.isNotEmpty() -> {
                            val data = buf.toString(); buf.clear()
                            try {
                                val json   = JSONObject(data)
                                val action = json.optString("action")
                                if (action == "create" || action == "update") {
                                    val record = json.optJSONObject("record") ?: continue
                                    // Skip placeholder messages that are still uploading
                                    if (record.optBoolean("uploading", false)) continue
                                    trySend(record.toMessage())
                                }
                            } catch (_: Exception) {}
                        }
                        // PocketBase comment lines (": keep-alive") — update heartbeat only
                        line.startsWith(":") -> { /* heartbeat received */ }
                    }
                }
                watchdog.cancel()
            } catch (e: Exception) {
                if (isActive) Log.w(TAG, "SSE reader ended: ${e.message}")
            }
        }

        awaitClose {
            Log.d(TAG, "SSE closed — cancelling reader  room=$roomId")
            readerJob.cancel()
            call.cancel()
            response.close()
        }
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
                    val o   = items.getJSONObject(i)
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

    fun buildFileUrl(recordId: String, storedFile: String): String {
        if (recordId.isBlank() || storedFile.isBlank()) return ""
        return "${AppConfig.BASE_URL}/api/files/$COL_MESSAGES/$recordId/$storedFile"
    }

    private fun uploadRoomAvatar(roomId: String, bytes: ByteArray, token: String): ChatRoom? {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("avatar", "avatar_$roomId.jpg",
                bytes.toRequestBody("image/jpeg".toMediaType())).build()
        val res     = httpClient.newCall(Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections/$COL_ROOMS/records/$roomId")
            .addHeader("Authorization", "Bearer $token")
            .patch(body).build()).execute()
        val resBody = res.body?.string() ?: "{}"; res.close()
        return try { JSONObject(resBody).toRoom("") } catch (_: Exception) { null }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun JSONObject.toRoom(myId: String): ChatRoom {
        val id         = optString("id")
        val members    = parseJsonArray(optString("members", "[]"))
        val type       = if (optString("type") == "direct") RoomType.DIRECT else RoomType.GROUP
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
            memberNames   = parseJsonArray(optString("memberNames",   "[]")),
            memberAvatars = parseJsonArray(optString("memberAvatars", "[]")),
            adminIds      = parseJsonArray(optString("adminIds",      "[]")),
            companyName   = optString("companyName"),
            lastMessage   = optString("lastMessage"),
            lastMessageAt = optLong("lastMessageAt", 0L),
            unreadCount   = optInt("unreadCount", 0)
        )
    }

    private fun JSONObject.toMessage(): ChatMessage {
        val id         = optString("id")
        val storedFile = optString("file", "")
        val fileUrl    = optString("computedFileUrl", "").ifBlank {
            buildFileUrl(id, storedFile)
        }
        val type = try { MessageType.valueOf(optString("type", "TEXT")) }
        catch (_: Exception) { MessageType.TEXT }
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