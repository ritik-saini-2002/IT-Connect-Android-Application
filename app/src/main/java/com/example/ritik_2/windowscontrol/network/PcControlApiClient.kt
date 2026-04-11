package com.example.ritik_2.windowscontrol.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.example.ritik_2.windowscontrol.PcControlSettings
import com.example.ritik_2.windowscontrol.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private val JSON_MT   = "application/json; charset=utf-8".toMediaType()
private val BINARY_MT = "application/octet-stream".toMediaType()

// ── Transfer constants ────────────────────────────────────────────────────────
private const val CHUNK_SIZE      = 4 * 1024 * 1024   // 4 MB per chunk
private const val SOCKET_BUF      = 8 * 1024 * 1024   // 8 MB socket buffer
private const val CONNECT_TIMEOUT = 6L
private const val READ_TIMEOUT    = 0L                 // 0 = infinite (streaming)
private const val WRITE_TIMEOUT   = 0L                 // 0 = infinite (large upload)
private const val PING_TIMEOUT    = 3L

// ── Base client ───────────────────────────────────────────────────────────────
abstract class PcBaseClient(protected val settings: PcControlSettings) {

    protected val gson = Gson()

    protected val http = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .socketFactory(tunedSocketFactory())
        .build()

    protected val httpFast = OkHttpClient.Builder()
        .connectTimeout(PING_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(PING_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(PING_TIMEOUT, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    protected val httpTransfer = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .socketFactory(tunedSocketFactory())
        .connectionPool(ConnectionPool(5, 60, TimeUnit.SECONDS))
        .build()

    private fun tunedSocketFactory(): javax.net.SocketFactory {
        return object : javax.net.SocketFactory() {
            private val delegate = javax.net.SocketFactory.getDefault()
            private fun tune(s: java.net.Socket): java.net.Socket {
                runCatching {
                    s.sendBufferSize    = SOCKET_BUF
                    s.receiveBufferSize = SOCKET_BUF
                    s.tcpNoDelay        = true
                    s.keepAlive         = true
                }
                return s
            }
            override fun createSocket() = tune(delegate.createSocket())
            override fun createSocket(host: String, port: Int) = tune(delegate.createSocket(host, port))
            override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int) = tune(delegate.createSocket(host, port, localHost, localPort))
            override fun createSocket(host: java.net.InetAddress, port: Int) = tune(delegate.createSocket(host, port))
            override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int) = tune(delegate.createSocket(address, port, localAddress, localPort))
        }
    }

    protected fun baseRequest(path: String) = Request.Builder()
        .url("${settings.baseUrl}$path")
        .header("X-Secret-Key", settings.secretKey)
        .header("X-Device-Name", runCatching {
            android.os.Build.MODEL + "/" + android.os.Build.MANUFACTURER
        }.getOrElse { "Android" })
        .header("X-Device-Id", runCatching {
            android.os.Build.MODEL + "_" + android.os.Build.SERIAL.takeLast(6)
        }.getOrElse { "android_device" })

    protected suspend fun get(path: String): PcNetworkResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val resp = http.newCall(baseRequest(path).get().build()).execute()
                resp.use { r ->
                    if (r.isSuccessful) PcNetworkResult(true, r.body?.string())
                    else PcNetworkResult(false, error = "HTTP ${r.code}")
                }
            } catch (e: Exception) {
                PcNetworkResult(false, error = e.message ?: "Network error")
            }
        }

    suspend fun post(path: String, body: Any): PcNetworkResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val rb   = gson.toJson(body).toRequestBody(JSON_MT)
                val resp = http.newCall(baseRequest(path).post(rb).build()).execute()
                resp.use { r ->
                    if (r.isSuccessful) PcNetworkResult(true, r.body?.string())
                    else PcNetworkResult(false, error = "HTTP ${r.code}")
                }
            } catch (e: Exception) {
                PcNetworkResult(false, error = e.message ?: "Network error")
            }
        }

    protected fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}

// ── Plan execution client ─────────────────────────────────────────────────────
class PcControlApiClient(settings: PcControlSettings) : PcBaseClient(settings) {

    suspend fun ping(): PcNetworkResult<PcPingResponse> = withContext(Dispatchers.IO) {
        try {
            val resp = httpFast.newCall(baseRequest("/ping").get().build()).execute()
            resp.use { r ->
                if (r.isSuccessful)
                    PcNetworkResult(true, gson.fromJson(r.body?.string(), PcPingResponse::class.java))
                else
                    PcNetworkResult(false, error = "HTTP ${r.code}")
            }
        } catch (e: Exception) {
            PcNetworkResult(false, error = e.message ?: "Unreachable")
        }
    }

    suspend fun executePlan(plan: PcPlan): PcNetworkResult<PcExecuteResponse> {
        val stepsArray = JsonParser.parseString(plan.stepsJson).asJsonArray
        val payload    = com.google.gson.JsonObject().apply {
            addProperty("planName", plan.planName)
            add("steps", stepsArray)
        }
        val result = post("/execute", payload)
        return if (result.success)
            PcNetworkResult(true, gson.fromJson(result.data, PcExecuteResponse::class.java))
        else PcNetworkResult(false, error = result.error)
    }

    suspend fun executeQuickStep(step: PcStep): PcNetworkResult<String> =
        post("/quick", gson.toJsonTree(step).asJsonObject)

    suspend fun getProcesses(): PcNetworkResult<List<String>> {
        val r = get("/processes")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val map = gson.fromJson(r.data, Map::class.java)
            @Suppress("UNCHECKED_CAST")
            PcNetworkResult(true, map["processes"] as? List<String> ?: emptyList())
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun getScreenSize(): PcNetworkResult<Pair<Int, Int>> {
        val r = get("/screen_size")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val map = gson.fromJson(r.data, Map::class.java)
            val w   = (map["width"]  as? Double)?.toInt() ?: 1920
            val h   = (map["height"] as? Double)?.toInt() ?: 1080
            PcNetworkResult(true, Pair(w, h))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun captureScreen(quality: Int = 25, scale: Int = 4): PcNetworkResult<String> {
        val r = get("/screen/capture?q=$quality&s=$scale")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val map = gson.fromJson(r.data, Map::class.java)
            val img = map["image"] as? String
                ?: return PcNetworkResult(false, error = "No image")
            PcNetworkResult(true, img)
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun pollOpenWithDialog(): PcNetworkResult<PcOpenWithDialog?> {
        val r = get("/dialog/openwith/poll")
        if (!r.success) return PcNetworkResult(true, null)
        return try {
            val json    = gson.fromJson(r.data, Map::class.java)
            val hasDlg  = json["has_dialog"] as? Boolean ?: false
            if (!hasDlg) return PcNetworkResult(true, null)
            val filePath = json["file_path"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val rawChoices = json["choices"] as? List<Map<String, Any>> ?: emptyList()
            val choices = rawChoices.map {
                PcOpenWithChoice(
                    appName = it["name"] as? String ?: "",
                    exePath = it["exe"]  as? String ?: "",
                    icon    = it["icon"] as? String ?: "📦"
                )
            }
            PcNetworkResult(true, PcOpenWithDialog(filePath, choices))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun resolveOpenWithDialog(exePath: String): PcNetworkResult<String> =
        post("/dialog/openwith/resolve", mapOf("exe" to exePath))

    // ── Master Key Admin Endpoints ────────────────────────────────────────────
    // These require X-Secret-Key = MASTER_KEY (not user secret key).
    // The masterKey param overrides the header for these calls.

    suspend fun getConnectedUsers(masterKey: String): PcNetworkResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val resp = http.newCall(
                    Request.Builder()
                        .url("${settings.baseUrl}/connections")
                        .header("X-Secret-Key", masterKey)
                        .get().build()
                ).execute()
                resp.use { r ->
                    if (r.isSuccessful) PcNetworkResult(true, r.body?.string())
                    else PcNetworkResult(false, error = "HTTP ${r.code}")
                }
            } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
        }

    suspend fun kickUser(masterKey: String, deviceId: String): PcNetworkResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(mapOf("device_id" to deviceId)).toRequestBody(JSON_MT)
                val resp = http.newCall(
                    Request.Builder()
                        .url("${settings.baseUrl}/connections/kick")
                        .header("X-Secret-Key", masterKey)
                        .post(body).build()
                ).execute()
                resp.use { r ->
                    if (r.isSuccessful) PcNetworkResult(true, r.body?.string())
                    else PcNetworkResult(false, error = "HTTP ${r.code}")
                }
            } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
        }

    suspend fun changeSecretKey(masterKey: String, newKey: String): PcNetworkResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(mapOf("new_key" to newKey)).toRequestBody(JSON_MT)
                val resp = http.newCall(
                    Request.Builder()
                        .url("${settings.baseUrl}/settings/key")
                        .header("X-Secret-Key", masterKey)
                        .post(body).build()
                ).execute()
                resp.use { r ->
                    if (r.isSuccessful) PcNetworkResult(true, r.body?.string())
                    else PcNetworkResult(false, error = "HTTP ${r.code}")
                }
            } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
        }

    suspend fun getConnectionLogs(masterKey: String): PcNetworkResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val resp = http.newCall(
                    Request.Builder()
                        .url("${settings.baseUrl}/connections/logs")
                        .header("X-Secret-Key", masterKey)
                        .get().build()
                ).execute()
                resp.use { r ->
                    if (r.isSuccessful) PcNetworkResult(true, r.body?.string())
                    else PcNetworkResult(false, error = "HTTP ${r.code}")
                }
            } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
        }
}

// ── Browse client ─────────────────────────────────────────────────────────────
class PcControlBrowseClient(settings: PcControlSettings) : PcBaseClient(settings) {

    suspend fun getDrives(): PcNetworkResult<List<PcDrive>> {
        val r = get("/browse/drives")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            PcNetworkResult(true, gson.fromJson(r.data, object : TypeToken<List<PcDrive>>() {}.type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun browseDir(
        path   : String,
        filter : PcFileFilter = PcFileFilter.ALL
    ): PcNetworkResult<List<PcFileItem>> {
        val fp = if (filter.extensions.isEmpty()) ""
        else "&exts=${filter.extensions.joinToString(",")}"
        val r  = get("/browse/dir?path=${enc(path)}$fp")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            PcNetworkResult(true, gson.fromJson(r.data, object : TypeToken<List<PcFileItem>>() {}.type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun searchFiles(
        rootPath   : String,
        query      : String,
        maxResults : Int = 100
    ): PcNetworkResult<List<PcFileItem>> {
        if (query.isBlank() || rootPath.isBlank())
            return PcNetworkResult(true, emptyList())
        val r = get("/browse/search?path=${enc(rootPath)}&q=${enc(query)}&maxResults=$maxResults")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            PcNetworkResult(true, gson.fromJson(r.data, object : TypeToken<List<PcFileItem>>() {}.type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun getInstalledApps(): PcNetworkResult<List<PcInstalledApp>> {
        val r = get("/browse/apps")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            PcNetworkResult(true, gson.fromJson(r.data, object : TypeToken<List<PcInstalledApp>>() {}.type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun getSpecialFolders(): PcNetworkResult<List<Map<String, Any>>> {
        val r = get("/browse/special")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            PcNetworkResult(true, gson.fromJson(r.data, object : TypeToken<List<Map<String, Any>>>() {}.type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun getRecentPaths(): PcNetworkResult<List<PcRecentPath>> {
        val r = get("/browse/recent")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            PcNetworkResult(true, gson.fromJson(r.data, object : TypeToken<List<PcRecentPath>>() {}.type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    // ── DOWNLOAD — streams directly to OutputStream ───────────────────────────
    suspend fun downloadFile(
        remotePath : String,
        outputStream: OutputStream,
        onProgress : (Long, Long, Long) -> Unit
    ): PcNetworkResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${settings.baseUrl}/file/download?path=${enc(remotePath)}"
            val req = Request.Builder()
                .url(url)
                .header("X-Secret-Key", settings.secretKey)
                .header("Accept-Encoding", "identity")
                .get().build()

            val resp = httpTransfer.newCall(req).execute()
            if (!resp.isSuccessful)
                return@withContext PcNetworkResult(false, error = "HTTP ${resp.code}")

            val total  = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            val source = resp.body?.source()
                ?: return@withContext PcNetworkResult(false, error = "No body")

            var done         = 0L
            var lastReportMs = System.currentTimeMillis()
            var lastBytes    = 0L

            val readBuf = ByteArray(64 * 1024)

            outputStream.buffered(256 * 1024).use { out ->
                while (true) {
                    val n = source.read(readBuf)
                    if (n == -1) break
                    out.write(readBuf, 0, n)
                    done += n

                    val now  = System.currentTimeMillis()
                    val dtMs = now - lastReportMs
                    if (dtMs >= 200) {
                        val speed = if (dtMs > 0) ((done - lastBytes) * 1000L) / dtMs else 0L
                        onProgress(done, total, speed)
                        lastReportMs = now
                        lastBytes    = done
                    }
                }
                out.flush()
            }
            source.close()

            onProgress(done, total, 0L)
            PcNetworkResult(true, Unit)
        } catch (e: Exception) {
            PcNetworkResult(false, error = e.message ?: "Download failed")
        }
    }

    // ── UPLOAD — streams from InputStream ─────────────────────────────────────
    private val CHUNKED_THRESHOLD = 16 * 1024 * 1024

    suspend fun uploadFile(
        inputStream  : InputStream,
        fileSize     : Long,
        fileName     : String,
        remotePath   : String,
        onProgress   : (Long, Long, Long) -> Unit
    ): PcNetworkResult<String> = withContext(Dispatchers.IO) {
        return@withContext if (fileSize <= CHUNKED_THRESHOLD) {
            uploadSingleStream(inputStream, fileSize, fileName, remotePath, onProgress)
        } else {
            uploadChunkedStream(inputStream, fileSize, fileName, remotePath, onProgress)
        }
    }

    private suspend fun uploadSingleStream(
        inputStream : InputStream,
        fileSize    : Long,
        fileName    : String,
        remotePath  : String,
        onProgress  : (Long, Long, Long) -> Unit
    ): PcNetworkResult<String> = withContext(Dispatchers.IO) {
        try {
            val total        = fileSize
            var done         = 0L
            var lastReportMs = System.currentTimeMillis()
            var lastBytes    = 0L

            val filePart = object : RequestBody() {
                override fun contentType()   = BINARY_MT
                override fun contentLength() = total
                override fun writeTo(sink: BufferedSink) {
                    val buf = ByteArray(64 * 1024)
                    inputStream.use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            sink.write(buf, 0, n)
                            done += n
                            val now  = System.currentTimeMillis()
                            val dtMs = now - lastReportMs
                            if (dtMs >= 200) {
                                val speed = if (dtMs > 0) ((done - lastBytes) * 1000L) / dtMs else 0L
                                onProgress(done, total, speed)
                                lastReportMs = now; lastBytes = done
                            }
                        }
                    }
                    onProgress(total, total, 0L)
                }
            }

            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("dest", remotePath)
                .addFormDataPart("file", fileName, filePart)
                .build()

            val req  = Request.Builder()
                .url("${settings.baseUrl}/file/upload?dest=${enc(remotePath)}")
                .header("X-Secret-Key", settings.secretKey)
                .post(multipart)
                .build()

            val resp = httpTransfer.newCall(req).execute()
            if (resp.isSuccessful) PcNetworkResult(true, resp.body?.string())
            else PcNetworkResult(false, error = "HTTP ${resp.code}: ${resp.body?.string()}")
        } catch (e: Exception) {
            PcNetworkResult(false, error = e.message ?: "Upload failed")
        }
    }

    private suspend fun uploadChunkedStream(
        inputStream : InputStream,
        fileSize    : Long,
        fileName    : String,
        remotePath  : String,
        onProgress  : (Long, Long, Long) -> Unit
    ): PcNetworkResult<String> = withContext(Dispatchers.IO) {
        try {
            val total       = fileSize
            val totalChunks = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
            var done        = 0L
            var lastReportMs = System.currentTimeMillis()
            var lastBytes    = 0L

            val chunkBuf = ByteArray(CHUNK_SIZE)

            inputStream.use { input ->
                var index = 0
                while (true) {
                    var bytesRead = 0
                    while (bytesRead < CHUNK_SIZE) {
                        val n = input.read(chunkBuf, bytesRead, CHUNK_SIZE - bytesRead)
                        if (n == -1) break
                        bytesRead += n
                    }
                    if (bytesRead == 0) break

                    val chunkSize = bytesRead
                    val capturedIndex = index

                    val body = object : RequestBody() {
                        override fun contentType()   = BINARY_MT
                        override fun contentLength() = chunkSize.toLong()
                        override fun writeTo(sink: BufferedSink) {
                            sink.write(chunkBuf, 0, chunkSize)
                        }
                    }

                    val url  = "${settings.baseUrl}/file/upload/chunk" +
                            "?name=${enc(fileName)}&dest=${enc(remotePath)}" +
                            "&index=$capturedIndex&total=$totalChunks"
                    val req  = Request.Builder()
                        .url(url)
                        .header("X-Secret-Key", settings.secretKey)
                        .post(body)
                        .build()

                    val resp = httpTransfer.newCall(req).execute()
                    resp.body?.string(); resp.close()
                    if (!resp.isSuccessful)
                        return@withContext PcNetworkResult(
                            false, error = "Chunk $capturedIndex failed: HTTP ${resp.code}")

                    done += chunkSize
                    val now  = System.currentTimeMillis()
                    val dtMs = now - lastReportMs
                    if (dtMs >= 200 || index == totalChunks - 1) {
                        val speed = if (dtMs > 0) ((done - lastBytes) * 1000L) / dtMs else 0L
                        onProgress(done, total, speed)
                        lastReportMs = now; lastBytes = done
                    }
                    index++
                }
            }
            PcNetworkResult(true, "Chunked upload complete: $fileName")
        } catch (e: Exception) {
            PcNetworkResult(false, error = e.message ?: "Chunked upload failed")
        }
    }
}

// ── Input client ──────────────────────────────────────────────────────────────
class PcControlInputClient(settings: PcControlSettings) : PcBaseClient(settings) {

    suspend fun moveMouse(dx: Float, dy: Float) =
        post("/input/mouse/move", mapOf("dx" to dx, "dy" to dy))

    suspend fun clickMouse(button: String = "left", double: Boolean = false) =
        post("/input/mouse/click", mapOf("button" to button, "double" to double))

    suspend fun scrollMouse(amount: Int, horizontal: Boolean = false) =
        post("/input/mouse/scroll", mapOf("amount" to amount, "horizontal" to horizontal))

    suspend fun mouseButtonDown(button: String = "left") =
        post("/input/mouse/down", mapOf("button" to button))

    suspend fun mouseButtonUp(button: String = "left") =
        post("/input/mouse/up", mapOf("button" to button))

    suspend fun pressKey(key: String) =
        post("/input/keyboard/key", mapOf("value" to key))

    suspend fun typeText(text: String) =
        post("/input/keyboard/type", mapOf("value" to text))

    // ── NEW: Key hold / release for functional keyboard bar ──────────────
    // Calls agent v10 /input/keyboard/hold and /input/keyboard/release endpoints.
    // Used for modifier keys (Shift, Ctrl, Alt, Win, AltGr) that stay pressed
    // until explicitly released.

    suspend fun holdKey(keyName: String) =
        post("/input/keyboard/hold", mapOf("value" to keyName))

    suspend fun releaseKey(keyName: String) =
        post("/input/keyboard/release", mapOf("value" to keyName))

    // ── NEW: App minimize / restore ──────────────────────────────────────
    // Calls agent v10 /app/minimize and /app/restore endpoints.

    suspend fun minimizeApp(name: String) =
        post("/app/minimize", mapOf("name" to name))

    suspend fun restoreApp(name: String) =
        post("/app/restore", mapOf("name" to name))

    // ── Screen ───────────────────────────────────────────────────────────

    suspend fun fetchScreenSnapshot(): PcNetworkResult<String> = withContext(Dispatchers.IO) {
        try {
            val resp = http.newCall(baseRequest("/screen/snapshot").get().build()).execute()
            if (!resp.isSuccessful)
                return@withContext PcNetworkResult(false, error = "HTTP ${resp.code}")
            val body = resp.body?.string()
                ?: return@withContext PcNetworkResult(false, error = "Empty")
            val json = org.json.JSONObject(body)
            if (!json.optBoolean("ok", false))
                return@withContext PcNetworkResult(false, error = "Agent error")
            PcNetworkResult(true, json.optString("data"))
        } catch (e: Exception) {
            PcNetworkResult(false, error = e.message)
        }
    }

    suspend fun fetchScreenInfo(): PcNetworkResult<org.json.JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val resp = http.newCall(baseRequest("/screen/info").get().build()).execute()
                if (!resp.isSuccessful)
                    return@withContext PcNetworkResult(false, error = "HTTP ${resp.code}")
                val body = resp.body?.string()
                    ?: return@withContext PcNetworkResult(false, error = "Empty")
                PcNetworkResult(true, org.json.JSONObject(body))
            } catch (e: Exception) {
                PcNetworkResult(false, error = e.message)
            }
        }
}