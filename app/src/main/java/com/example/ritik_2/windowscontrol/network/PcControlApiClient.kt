package com.example.ritik_2.windowscontrol.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.ritik_2.windowscontrol.PcControlSettings
import com.example.ritik_2.windowscontrol.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val JSON_MT = "application/json; charset=utf-8".toMediaType()

// ─────────────────────────────────────────────────────────────
//  BASE CLIENT
// ─────────────────────────────────────────────────────────────

abstract class PcBaseClient(protected val settings: PcControlSettings) {

    protected val gson = Gson()

    protected val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    protected fun baseRequest(path: String) = Request.Builder()
        .url("${settings.baseUrl}$path")
        .header("X-Secret-Key", settings.secretKey)

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
                val json = gson.toJson(body)
                val rb = json.toRequestBody(JSON_MT)
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
            val req = Request.Builder().url("${settings.baseUrl}/ping").get().build()
            val resp = http.newCall(req).execute()
            if (resp.isSuccessful) {
                val ping = gson.fromJson(resp.body?.string(), PcPingResponse::class.java)
                PcNetworkResult(true, ping)
            } else PcNetworkResult(false, error = "HTTP ${resp.code}")
        } catch (e: Exception) {
            PcNetworkResult(false, error = e.message ?: "Unreachable")
        }
    }

    suspend fun executePlan(plan: com.example.ritik_2.windowscontrol.data.PcPlan): PcNetworkResult<PcExecuteResponse> {
        // CRITICAL: Agent expects {"planName":"...", "steps":[{...},...]}
        // PcPlan stores steps as stepsJson (String), so we must build the payload manually
        val stepsArray = com.google.gson.JsonParser.parseString(plan.stepsJson).asJsonArray
        val payload = com.google.gson.JsonObject().apply {
            addProperty("planName", plan.planName)
            add("steps", stepsArray)         // steps as real JSON array, not string
        }
        val result = post("/execute", payload)
        return if (result.success) {
            val r = gson.fromJson(result.data, PcExecuteResponse::class.java)
            PcNetworkResult(true, r)
        } else PcNetworkResult(false, error = result.error)
    }

    suspend fun executeQuickStep(step: PcStep): PcNetworkResult<String> {
        // Serialize PcStep directly — all fields map 1:1 to agent step format
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
            val w = (map["width"] as? Double)?.toInt() ?: 1920
            val h = (map["height"] as? Double)?.toInt() ?: 1080
            PcNetworkResult(true, Pair(w, h))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    /** Capture live screenshot — returns base64 JPEG */
    suspend fun captureScreen(quality: Int = 25, scale: Int = 4): PcNetworkResult<String> {
        val r = get("/screen/capture?q=$quality&s=$scale")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val map = gson.fromJson(r.data, Map::class.java)
            val img = map["image"] as? String ?: return PcNetworkResult(false, error = "No image")
            PcNetworkResult(true, img)
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }
}

// ─────────────────────────────────────────────────────────────
//  BROWSE CLIENT — File/App browsing
// ─────────────────────────────────────────────────────────────

class PcControlBrowseClient(settings: PcControlSettings) : PcBaseClient(settings) {

    /** List all drives: C:/, D:/ etc */
    suspend fun getDrives(): PcNetworkResult<List<PcDrive>> {
        val r = get("/browse/drives")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val type = object : TypeToken<List<PcDrive>>() {}.type
            PcNetworkResult(true, gson.fromJson(r.data, type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    /** List files/folders in a directory with optional filter */
    suspend fun browseDir(
        path: String,
        filter: PcFileFilter = PcFileFilter.ALL
    ): PcNetworkResult<List<PcFileItem>> {
        val filterParam = if (filter.extensions.isEmpty()) ""
        else "&exts=${filter.extensions.joinToString(",")}"
        val r = get("/browse/dir?path=${java.net.URLEncoder.encode(path, "UTF-8")}$filterParam")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val type = object : TypeToken<List<PcFileItem>>() {}.type
            PcNetworkResult(true, gson.fromJson(r.data, type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    /** Get all installed applications */
    suspend fun getInstalledApps(): PcNetworkResult<List<PcInstalledApp>> {
        val r = get("/browse/apps")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val type = object : TypeToken<List<PcInstalledApp>>() {}.type
            PcNetworkResult(true, gson.fromJson(r.data, type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }

    /** Get recently used paths */
    suspend fun getRecentPaths(): PcNetworkResult<List<PcRecentPath>> {
        val r = get("/browse/recent")
        if (!r.success) return PcNetworkResult(false, error = r.error)
        return try {
            val type = object : TypeToken<List<PcRecentPath>>() {}.type
            PcNetworkResult(true, gson.fromJson(r.data, type))
        } catch (e: Exception) { PcNetworkResult(false, error = e.message) }
    }
}

// ─────────────────────────────────────────────────────────────
//  INPUT CLIENT — Touchpad / Keyboard
// ─────────────────────────────────────────────────────────────

class PcControlInputClient(settings: PcControlSettings) : PcBaseClient(settings) {

    /** Move mouse by delta (touchpad drag) */
    suspend fun moveMouse(dx: Float, dy: Float) =
        post("/input/mouse/move", mapOf("dx" to dx, "dy" to dy))

    /** Mouse click */
    suspend fun clickMouse(button: String = "left", double: Boolean = false) =
        post("/input/mouse/click", mapOf("button" to button, "double" to double))

    /** Scroll wheel */
    suspend fun scrollMouse(amount: Int) =
        post("/input/mouse/scroll", mapOf("amount" to amount))

    /** Press a key or shortcut */
    suspend fun pressKey(key: String) =
        post("/input/keyboard/key", mapOf("value" to key))

    /** Type a full string */
    suspend fun typeText(text: String) =
        post("/input/keyboard/type", mapOf("value" to text))

    /** Hold mouse button down (start drag) */
    suspend fun mouseButtonDown(button: String = "left") =
        post("/input/mouse/down", mapOf("button" to button))

    /** Release held mouse button (end drag) */
    suspend fun mouseButtonUp() =
        post("/input/mouse/up", emptyMap<String, Any>())

    /** Fetch compressed screenshot as base64 JPEG string */
    suspend fun fetchScreenSnapshot(): PcNetworkResult<String> = withContext(Dispatchers.IO) {
        try {
            val resp = http.newCall(baseRequest("/screen/snapshot").get().build()).execute()
            if (!resp.isSuccessful) return@withContext PcNetworkResult(false, error = "HTTP ${resp.code}")
            val body = resp.body?.string() ?: return@withContext PcNetworkResult(false, error = "Empty")
            val json = org.json.JSONObject(body)
            if (!json.optBoolean("ok", false)) return@withContext PcNetworkResult(false, error = "Agent error")
            PcNetworkResult(true, json.optString("data"))
        } catch (e: Exception) {
            PcNetworkResult(false, error = e.message)
        }
    }

    /** Get cursor position and active window title */
    suspend fun fetchScreenInfo(): PcNetworkResult<org.json.JSONObject> = withContext(Dispatchers.IO) {
        try {
            val resp = http.newCall(baseRequest("/screen/info").get().build()).execute()
            if (!resp.isSuccessful) return@withContext PcNetworkResult(false, error = "HTTP ${resp.code}")
            val body = resp.body?.string() ?: return@withContext PcNetworkResult(false, error = "Empty")
            PcNetworkResult(true, org.json.JSONObject(body))
        } catch (e: Exception) {
            PcNetworkResult(false, error = e.message)
        }
    }
}