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
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

private val JSON_MT = "application/json; charset=utf-8".toMediaType()

// ─────────────────────────────────────────────────────────────
//  BASE CLIENT
// ─────────────────────────────────────────────────────────────

abstract class PcBaseClient(protected val settings: PcControlSettings) {

    protected val gson = Gson()

    protected val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    // Separate fast client for ping — fails quickly instead of blocking UI
    protected val httpFast = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    // Long-timeout client for file transfers
    protected val httpTransfer = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // no read timeout for large files
        .writeTimeout(0, TimeUnit.SECONDS)  // no write timeout
        .build()

    protected fun baseRequest(path: String) = Request.Builder()
        .url("${settings.baseUrl}$path")
        .header("X-Secret-Key", settings.secretKey)
        .header("X-Device-Name", try {
            android.os.Build.MODEL + "/" + android.os.Build.MANUFACTURER
        } catch (e: Exception) { "Android" })

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
}

// ─────────────────────────────────────────────────────────────
//  PLAN EXECUTION CLIENT
// ─────────────────────────────────────────────────────────────

class PcControlApiClient(settings: PcControlSettings) : PcBaseClient(settings) {

    suspend fun ping(): PcNetworkResult<PcPingResponse> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("PcControl", "ping → ${settings.baseUrl}/ping  key=${settings.secretKey}")
            // Ping uses the auth header too — agent checks it on all paths except /ping
            val req  = baseRequest("/ping").get().build()
            val resp = httpFast.newCall(req).execute()
            android.util.Log.d("PcControl", "ping response → ${resp.code}")
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                android.util.Log.d("PcControl", "ping body → $body")
                val ping = gson.fromJson(body, PcPingResponse::class.java)
                PcNetworkResult(true, ping)
            } else {
                PcNetworkResult(false, error = "HTTP ${resp.code}")
            }
        } catch (e: Exception) {
            android.util.Log.e("PcControl", "ping exception → ${e.javaClass.simpleName}: ${e.message}")
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
        return if (result.success) {
            PcNetworkResult(true, gson.fromJson(result.data, PcExecuteResponse::class.java))
        } else PcNetworkResult(false, error = result.error)
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
            val img = map["image"] as? String ?: return PcNetworkResult(false, error = "No image")
            PcNetworkResult(true, img)
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    /** Poll for pending "Open With" dialog on PC */
    suspend fun pollOpenWithDialog(): PcNetworkResult<PcOpenWithDialog?> {
        val r = get("/dialog/openwith/poll")
        if (!r.success) return PcNetworkResult(true, null) // no dialog = ok
        return try {
            val json = gson.fromJson(r.data, Map::class.java)
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

    /** Tell PC which app was selected in Open With dialog */
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
        val r = get("/browse/dir?path=${java.net.URLEncoder.encode(path, "UTF-8")}$fp")
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

    // ── File Transfer ──────────────────────────────────────────

    /**
     * Download a file from the PC.
     * Returns an InputStream for streaming — caller must close.
     * onProgress(bytesRead, totalBytes, speedBps)
     */
    suspend fun downloadFile(
        remotePath : String,
        onProgress : (Long, Long, Long) -> Unit
    ): PcNetworkResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = "${settings.baseUrl}/file/download?path=${
                java.net.URLEncoder.encode(remotePath, "UTF-8")}"
            val req  = Request.Builder()
                .url(url)
                .header("X-Secret-Key", settings.secretKey)
                .get().build()

            val resp = httpTransfer.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext PcNetworkResult(
                false, error = "HTTP ${resp.code}")

            val total     = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            val body      = resp.body ?: return@withContext PcNetworkResult(false, error = "No body")
            val source    = body.source()
            val buf       = okio.Buffer()
            var done      = 0L
            var lastTime  = System.currentTimeMillis()
            var lastBytes = 0L
            val chunks    = mutableListOf<ByteArray>()

            while (!source.exhausted()) {
                val read = source.read(buf, 8192)
                if (read == -1L) break
                done += read
                val chunk = ByteArray(buf.size.toInt())
                buf.read(chunk)
                chunks.add(chunk)

                val now   = System.currentTimeMillis()
                val dtMs  = (now - lastTime).coerceAtLeast(1)
                val speed = if (dtMs > 200) {
                    val s = ((done - lastBytes) * 1000L / dtMs)
                    lastTime = now; lastBytes = done; s
                } else 0L
                onProgress(done, total, speed)
            }

            val result = ByteArray(done.toInt())
            var offset = 0
            for (c in chunks) { c.copyInto(result, offset); offset += c.size }
            PcNetworkResult(true, result)
        } catch (e: Exception) {
            PcNetworkResult(false, error = e.message ?: "Download failed")
        }
    }

    /**
     * Upload a file to the PC at remotePath.
     * onProgress(bytesRead, totalBytes, speedBps)
     */
    suspend fun uploadFile(
        localBytes  : ByteArray,
        fileName    : String,
        remotePath  : String,
        onProgress  : (Long, Long, Long) -> Unit
    ): PcNetworkResult<String> = withContext(Dispatchers.IO) {
        try {
            val total     = localBytes.size.toLong()
            var done      = 0L
            var lastTime  = System.currentTimeMillis()
            var lastBytes = 0L

            // Wrap bytes in a counting RequestBody
            // Build multipart/form-data body so agent can use request.files["file"]
            val filePart = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = total
                override fun writeTo(sink: okio.BufferedSink) {
                    var offset = 0
                    val chunk  = 8192
                    while (offset < localBytes.size) {
                        val end  = minOf(offset + chunk, localBytes.size)
                        sink.write(localBytes, offset, end - offset)
                        done += (end - offset).toLong()
                        val now  = System.currentTimeMillis()
                        val dtMs = (now - lastTime).coerceAtLeast(1)
                        val spd  = if (dtMs > 200) {
                            val s = (done - lastBytes) * 1000L / dtMs
                            lastTime = now; lastBytes = done; s
                        } else 0L
                        onProgress(done, total, spd)
                        offset = end
                    }
                }
            }

            val destEnc = java.net.URLEncoder.encode(remotePath, "UTF-8")
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
            onProgress(total, total, 0L) // mark complete
            if (resp.isSuccessful) PcNetworkResult(true, resp.body?.string())
            else PcNetworkResult(false, error = "HTTP ${resp.code}: ${resp.body?.string()}")
        } catch (e: Exception) {
            PcNetworkResult(false, error = e.message ?: "Upload failed")
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

    suspend fun scrollMouse(amount: Int) =
        post("/input/mouse/scroll", mapOf("amount" to amount))

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