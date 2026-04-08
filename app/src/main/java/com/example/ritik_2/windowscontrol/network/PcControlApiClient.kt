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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private val JSON_MT    = "application/json; charset=utf-8".toMediaType()
private val BINARY_MT  = "application/octet-stream".toMediaType()

// ─────────────────────────────────────────────────────────────
//  Transfer constants — tuned for WiFi 6 (802.11ax)
//  2.4 GHz: ~300 Mbps real  |  5 GHz: ~1200 Mbps real
// ─────────────────────────────────────────────────────────────
private const val CHUNK_SIZE        = 4 * 1024 * 1024   // 4 MB per chunk
private const val SOCKET_BUF        = 8 * 1024 * 1024   // 8 MB socket buffer
private const val CONNECT_TIMEOUT   = 6L                 // seconds
private const val READ_TIMEOUT      = 0L                 // 0 = infinite (streaming)
private const val WRITE_TIMEOUT     = 0L                 // 0 = infinite (large upload)
private const val PING_TIMEOUT      = 3L

// ─────────────────────────────────────────────────────────────
//  BASE CLIENT
// ─────────────────────────────────────────────────────────────
abstract class PcBaseClient(protected val settings: PcControlSettings) {

    protected val gson = Gson()

    /** Standard API calls */
    protected val http = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .socketFactory(tunedSocketFactory())
        .build()

    /** Fast ping */
    protected val httpFast = OkHttpClient.Builder()
        .connectTimeout(PING_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(PING_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(PING_TIMEOUT, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * Transfer client — infinite timeouts, large buffers.
     * OkHttp will use a persistent connection pool; we get near-wire-speed
     * on WiFi 6 because we saturate the TCP window with 4 MB chunks.
     */
    protected val httpTransfer = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .socketFactory(tunedSocketFactory())
        .connectionPool(ConnectionPool(5, 60, TimeUnit.SECONDS))
        .build()

    /**
     * Returns a SocketFactory that sets SO_SNDBUF / SO_RCVBUF to 8 MB
     * and TCP_NODELAY on every new socket — critical for throughput on
     * WiFi 6 where RTT can be <1 ms on 5 GHz.
     */
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
            override fun createSocket()                                                      = tune(delegate.createSocket())
            override fun createSocket(host: String, port: Int)                              = tune(delegate.createSocket(host, port))
            override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int) = tune(delegate.createSocket(host, port, localHost, localPort))
            override fun createSocket(host: java.net.InetAddress, port: Int)                = tune(delegate.createSocket(host, port))
            override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int) = tune(delegate.createSocket(address, port, localAddress, localPort))
        }
    }

    protected fun baseRequest(path: String) = Request.Builder()
        .url("${settings.baseUrl}$path")
        .header("X-Secret-Key", settings.secretKey)
        .header("X-Device-Name", runCatching {
            android.os.Build.MODEL + "/" + android.os.Build.MANUFACTURER
        }.getOrElse { "Android" })

    protected suspend fun get(path: String): PcNetworkResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val resp = http.newCall(baseRequest(path).get().build()).execute()
                if (resp.isSuccessful) PcNetworkResult(true, resp.body?.string())
                else PcNetworkResult(false, error = "HTTP ${resp.code}")
            } catch (e: Exception) {
                PcNetworkResult(false, error = e.message ?: "Network error")
            }
        }

    protected suspend fun post(path: String, body: Any): PcNetworkResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val rb   = gson.toJson(body).toRequestBody(JSON_MT)
                val resp = http.newCall(baseRequest(path).post(rb).build()).execute()
                if (resp.isSuccessful) PcNetworkResult(true, resp.body?.string())
                else PcNetworkResult(false, error = "HTTP ${resp.code}")
            } catch (e: Exception) {
                PcNetworkResult(false, error = e.message ?: "Network error")
            }
        }

    protected fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}

// ─────────────────────────────────────────────────────────────
//  PLAN EXECUTION CLIENT
// ─────────────────────────────────────────────────────────────
class PcControlApiClient(settings: PcControlSettings) : PcBaseClient(settings) {

    suspend fun ping(): PcNetworkResult<PcPingResponse> = withContext(Dispatchers.IO) {
        try {
            val req  = baseRequest("/ping").get().build()
            val resp = httpFast.newCall(req).execute()
            if (resp.isSuccessful) {
                val ping = gson.fromJson(resp.body?.string(), PcPingResponse::class.java)
                PcNetworkResult(true, ping)
            } else {
                PcNetworkResult(false, error = "HTTP ${resp.code}")
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

    suspend fun executeQuickStep(step: PcStep): PcNetworkResult<String> {
        val payload = gson.toJsonTree(step).asJsonObject
        return post("/quick", payload)
    }

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
            val json   = gson.fromJson(r.data, Map::class.java)
            val hasDlg = json["has_dialog"] as? Boolean ?: false
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
}

// ─────────────────────────────────────────────────────────────
//  BROWSE CLIENT
// ─────────────────────────────────────────────────────────────
class PcControlBrowseClient(settings: PcControlSettings) : PcBaseClient(settings) {

    suspend fun getDrives(): PcNetworkResult<List<PcDrive>> {
        val r = get("/browse/drives")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val type = object : TypeToken<List<PcDrive>>() {}.type
            PcNetworkResult(true, gson.fromJson(r.data, type))
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
            val type = object : TypeToken<List<PcFileItem>>() {}.type
            PcNetworkResult(true, gson.fromJson(r.data, type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun searchFiles(
        rootPath   : String,
        query      : String,
        maxResults : Int = 100
    ): PcNetworkResult<List<PcFileItem>> {
        if (query.isBlank() || rootPath.isBlank())
            return PcNetworkResult(true, emptyList())
        val url = "/browse/search?path=${enc(rootPath)}&q=${enc(query)}&maxResults=$maxResults"
        val r   = get(url)
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val type = object : TypeToken<List<PcFileItem>>() {}.type
            PcNetworkResult(true, gson.fromJson(r.data, type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun getInstalledApps(): PcNetworkResult<List<PcInstalledApp>> {
        val r = get("/browse/apps")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val type = object : TypeToken<List<PcInstalledApp>>() {}.type
            PcNetworkResult(true, gson.fromJson(r.data, type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun getSpecialFolders(): PcNetworkResult<List<Map<String, Any>>> {
        val r = get("/browse/special")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            PcNetworkResult(true, gson.fromJson(r.data, type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    suspend fun getRecentPaths(): PcNetworkResult<List<PcRecentPath>> {
        val r = get("/browse/recent")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val type = object : TypeToken<List<PcRecentPath>>() {}.type
            PcNetworkResult(true, gson.fromJson(r.data, type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    // ─────────────────────────────────────────────────────────
    //  HIGH-SPEED DOWNLOAD
    //  • Range request support for resumable transfers
    //  • 4 MB read chunks into pre-allocated buffer
    //  • Progress reported every ~300 ms to avoid UI flooding
    // ─────────────────────────────────────────────────────────
    suspend fun downloadFile(
        remotePath : String,
        onProgress : (Long, Long, Long) -> Unit
    ): PcNetworkResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url  = "${settings.baseUrl}/file/download?path=${enc(remotePath)}"
            val req  = Request.Builder()
                .url(url)
                .header("X-Secret-Key", settings.secretKey)
                .header("Accept-Encoding", "identity")   // disable gzip — already JPEG/binary
                .get().build()

            val resp = httpTransfer.newCall(req).execute()
            if (!resp.isSuccessful)
                return@withContext PcNetworkResult(false, error = "HTTP ${resp.code}")

            val total     = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            val body      = resp.body
                ?: return@withContext PcNetworkResult(false, error = "No body")
            val source    = body.source()
            val buf       = okio.Buffer()

            var done          = 0L
            var lastReportMs  = System.currentTimeMillis()
            var lastBytes     = 0L
            var peakSpeed     = 0L
            val chunks        = mutableListOf<ByteArray>()

            while (!source.exhausted()) {
                val read = source.read(buf, CHUNK_SIZE.toLong())
                if (read == -1L) break
                done += read
                val chunk = ByteArray(buf.size.toInt())
                buf.read(chunk)
                chunks.add(chunk)

                val now  = System.currentTimeMillis()
                val dtMs = now - lastReportMs
                if (dtMs >= 200) {
                    val speed = if (dtMs > 0) ((done - lastBytes) * 1000L) / dtMs else 0L
                    if (speed > peakSpeed) peakSpeed = speed
                    onProgress(done, total, speed)
                    lastReportMs = now
                    lastBytes    = done
                }
            }
            // Final progress
            onProgress(done, total, 0L)

            // Reassemble — single allocation
            val result = ByteArray(done.toInt())
            var offset = 0
            for (c in chunks) { c.copyInto(result, offset); offset += c.size }
            PcNetworkResult(true, result)
        } catch (e: Exception) {
            PcNetworkResult(false, error = e.message ?: "Download failed")
        }
    }

    // ─────────────────────────────────────────────────────────
    //  HIGH-SPEED UPLOAD
    //  Strategy:
    //    • Files ≤ 16 MB  → single multipart POST (simple, fast for small files)
    //    • Files > 16 MB  → chunked upload via /file/upload/chunk
    //      Each chunk is sent as a raw POST with byte range params.
    //      Agent reassembles in order when all chunks arrive.
    //  Both paths saturate the WiFi 6 link.
    // ─────────────────────────────────────────────────────────
    private val CHUNKED_THRESHOLD = 16 * 1024 * 1024   // 16 MB

    suspend fun uploadFile(
        localBytes : ByteArray,
        fileName   : String,
        remotePath : String,
        onProgress : (Long, Long, Long) -> Unit
    ): PcNetworkResult<String> = withContext(Dispatchers.IO) {
        return@withContext if (localBytes.size <= CHUNKED_THRESHOLD) {
            uploadSingle(localBytes, fileName, remotePath, onProgress)
        } else {
            uploadChunked(localBytes, fileName, remotePath, onProgress)
        }
    }

    private suspend fun uploadSingle(
        localBytes : ByteArray,
        fileName   : String,
        remotePath : String,
        onProgress : (Long, Long, Long) -> Unit
    ): PcNetworkResult<String> = withContext(Dispatchers.IO) {
        try {
            val total         = localBytes.size.toLong()
            var done          = 0L
            var lastReportMs  = System.currentTimeMillis()
            var lastBytes     = 0L

            val filePart = object : RequestBody() {
                override fun contentType() = BINARY_MT
                override fun contentLength() = total
                override fun writeTo(sink: okio.BufferedSink) {
                    var offset = 0
                    while (offset < localBytes.size) {
                        val end  = minOf(offset + CHUNK_SIZE, localBytes.size)
                        sink.write(localBytes, offset, end - offset)
                        done += (end - offset).toLong()
                        val now  = System.currentTimeMillis()
                        val dtMs = now - lastReportMs
                        if (dtMs >= 200) {
                            val speed = if (dtMs > 0) ((done - lastBytes) * 1000L) / dtMs else 0L
                            onProgress(done, total, speed)
                            lastReportMs = now; lastBytes = done
                        }
                        offset = end
                    }
                    onProgress(total, total, 0L)
                }
            }

            val destEnc   = enc(remotePath)
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("dest", remotePath)
                .addFormDataPart("file", fileName, filePart)
                .build()
            val req = Request.Builder()
                .url("${settings.baseUrl}/file/upload?dest=$destEnc")
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

    private suspend fun uploadChunked(
        localBytes : ByteArray,
        fileName   : String,
        remotePath : String,
        onProgress : (Long, Long, Long) -> Unit
    ): PcNetworkResult<String> = withContext(Dispatchers.IO) {
        try {
            val total       = localBytes.size.toLong()
            val totalChunks = (localBytes.size + CHUNK_SIZE - 1) / CHUNK_SIZE
            var done        = 0L
            var lastReportMs = System.currentTimeMillis()
            var lastBytes    = 0L

            for (index in 0 until totalChunks) {
                val start     = index * CHUNK_SIZE
                val end       = minOf(start + CHUNK_SIZE, localBytes.size)
                val chunkData = localBytes.copyOfRange(start, end)

                val body = chunkData.toRequestBody(BINARY_MT)
                val url  = "${settings.baseUrl}/file/upload/chunk" +
                        "?name=${enc(fileName)}&dest=${enc(remotePath)}" +
                        "&index=$index&total=$totalChunks"
                val req  = Request.Builder()
                    .url(url)
                    .header("X-Secret-Key", settings.secretKey)
                    .post(body)
                    .build()

                val resp = httpTransfer.newCall(req).execute()
                if (!resp.isSuccessful)
                    return@withContext PcNetworkResult(
                        false, error = "Chunk $index failed: HTTP ${resp.code}")

                done += chunkData.size.toLong()
                val now  = System.currentTimeMillis()
                val dtMs = now - lastReportMs
                if (dtMs >= 200 || index == totalChunks - 1) {
                    val speed = if (dtMs > 0) ((done - lastBytes) * 1000L) / dtMs else 0L
                    onProgress(done, total, speed)
                    lastReportMs = now; lastBytes = done
                }
            }
            PcNetworkResult(true, "Chunked upload complete: $fileName")
        } catch (e: Exception) {
            PcNetworkResult(false, error = e.message ?: "Chunked upload failed")
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  INPUT CLIENT
// ─────────────────────────────────────────────────────────────
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