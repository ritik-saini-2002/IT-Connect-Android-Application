package com.example.ritik_2.pocketbase

import android.util.Log
import com.example.ritik_2.core.AppConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PocketBaseInitializer {

    private const val TAG = "PBInitializer"

    private val http = OkHttpClient.Builder()
        .connectTimeout(1330, TimeUnit.SECONDS)
        .readTimeout(1330, TimeUnit.SECONDS)
        .writeTimeout(1330, TimeUnit.SECONDS)
        .build()

    suspend fun initialize() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "BASE_URL: ${AppConfig.BASE_URL}")

        if (!isServerReachable()) {
            Log.e(TAG, "❌ Server not reachable"); return
        }

        val token = getAdminToken() ?: run {
            Log.e(TAG, "❌ No admin token"); return
        }
        Log.d(TAG, "✅ Admin token obtained")

        // Always re-apply users rules — PocketBase can reset auth collection
        // rules on its own restart, so we enforce them every launch.
        openUsersRules(token)

        // Create/verify base collections.
        // openRules() is called ONLY when a collection is newly created.
        // If it already exists we only add missing fields — rules are never touched.
        // This prevents the app from overwriting manually-set rules on every launch.
        ensureBaseCollection(token, "companies_metadata",  companiesFields())
        ensureBaseCollection(token, "user_access_control", accessControlFields())
        ensureBaseCollection(token, "user_search_index",   searchIndexFields())

        // chat_rooms and chat_messages rules are managed manually in PocketBase UI.
        // Do NOT call openRules() on them here.

        Log.d(TAG, "✅ Init complete")
        Log.d(TAG, "========================================")
    }

    // ── Fix users collection API rules ────────────────────────────────────────

    private fun openUsersRules(token: String) {
        try {
            val listRes  = http.newCall(req("GET",
                "${AppConfig.BASE_URL}/api/collections?perPage=200", token)).execute()
            val listBody = listRes.body?.string() ?: ""
            val listCode = listRes.code
            listRes.close()

            if (listCode !in 200..299) {
                Log.e(TAG, "❌ Cannot list collections: HTTP $listCode $listBody"); return
            }

            val items = JSONObject(listBody).optJSONArray("items") ?: JSONArray()
            var usersId   = ""
            var usersName = ""

            for (i in 0 until items.length()) {
                val col  = items.getJSONObject(i)
                val name = col.optString("name")
                val type = col.optString("type")
                if (type == "auth" && (name == "users" || name == "_pb_users_auth_")) {
                    usersId   = col.optString("id")
                    usersName = name
                    break
                }
            }

            if (usersId.isEmpty()) {
                for (i in 0 until items.length()) {
                    val col = items.getJSONObject(i)
                    if (col.optString("type") == "auth") {
                        usersId   = col.optString("id")
                        usersName = col.optString("name")
                        Log.w(TAG, "⚠️ 'users' not found by name, using first auth: $usersName")
                        break
                    }
                }
            }

            if (usersId.isEmpty()) {
                Log.e(TAG, "❌ No auth collection found"); return
            }

            Log.d(TAG, "Found users collection: name=$usersName id=$usersId")

            val rulesBody = JSONObject().apply {
                put("listRule",   "@request.auth.id != \"\"")
                put("viewRule",   "@request.auth.id != \"\"")
                put("createRule", "")
                put("updateRule", "@request.auth.id = id")
                put("deleteRule", JSONObject.NULL)
            }.toString()

            val patchRes  = http.newCall(req("PATCH",
                "${AppConfig.BASE_URL}/api/collections/$usersId", token, rulesBody)).execute()
            val patchCode = patchRes.code
            val patchBody = patchRes.body?.string() ?: ""
            patchRes.close()

            if (patchCode in 200..299)
                Log.d(TAG, "✅ users API rules opened (id=$usersId)")
            else
                Log.e(TAG, "❌ Failed to open users rules: HTTP $patchCode $patchBody")
        } catch (e: Exception) {
            Log.e(TAG, "❌ openUsersRules: ${e.message}", e)
        }
    }

    // ── Ensure collection exists — never resets rules on existing collections ─

    private fun ensureBaseCollection(token: String, name: String, fields: List<JSONObject>) {
        try {
            val checkRes  = http.newCall(req("GET",
                "${AppConfig.BASE_URL}/api/collections/$name", token)).execute()
            val checkBody = checkRes.body?.string() ?: ""
            val exists    = checkRes.isSuccessful
            checkRes.close()

            if (exists) {
                // Collection already exists — only add missing fields, do NOT touch rules.
                Log.d(TAG, "Collection '$name' exists — checking fields…")
                patchBaseCollectionFields(token, name, fields, checkBody)
            } else {
                // New collection — create it and open rules once.
                createBaseCollection(token, name, fields)
                openRules(token, name)
                Log.d(TAG, "✅ '$name' created with open rules")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ensureBaseCollection '$name': ${e.message}", e)
        }
    }

    private fun createBaseCollection(token: String, name: String, fields: List<JSONObject>) {
        val fieldsArr = JSONArray().also { arr -> fields.forEach { arr.put(it) } }
        val body = JSONObject().apply {
            put("name",       name)
            put("type",       "base")
            put("fields",     fieldsArr)
            put("listRule",   JSONObject.NULL)
            put("viewRule",   JSONObject.NULL)
            put("createRule", JSONObject.NULL)
            put("updateRule", JSONObject.NULL)
            put("deleteRule", JSONObject.NULL)
        }.toString()

        val res   = http.newCall(req("POST",
            "${AppConfig.BASE_URL}/api/collections", token, body)).execute()
        val body2 = res.body?.string() ?: ""
        Log.d(TAG, if (res.isSuccessful) "✅ '$name' created"
        else "❌ '$name' create failed: ${res.code} $body2")
        res.close()
    }

    private fun patchBaseCollectionFields(
        token: String, name: String,
        newFields: List<JSONObject>, existingJson: String
    ) {
        try {
            val col        = JSONObject(existingJson)
            val existing   = col.optJSONArray("fields")
                ?: col.optJSONArray("schema") ?: JSONArray()
            val existNames = (0 until existing.length())
                .map { existing.getJSONObject(it).optString("name") }.toSet()

            val toAdd = newFields.filter { it.optString("name") !in existNames }
            if (toAdd.isEmpty()) { Log.d(TAG, "✅ '$name' fields OK"); return }

            val merged = JSONArray()
            for (i in 0 until existing.length()) merged.put(existing.getJSONObject(i))
            toAdd.forEach { merged.put(it) }

            for (key in listOf("fields", "schema")) {
                val res = http.newCall(req("PATCH",
                    "${AppConfig.BASE_URL}/api/collections/$name", token,
                    JSONObject().put(key, merged).toString())).execute()
                val ok  = res.isSuccessful
                res.close()
                if (ok) {
                    Log.d(TAG, "✅ '$name' patched ($key) — added ${toAdd.size} fields")
                    return
                }
            }
            Log.e(TAG, "❌ '$name' patch failed with both 'fields' and 'schema' keys")
        } catch (e: Exception) {
            Log.e(TAG, "❌ patchBaseCollectionFields '$name': ${e.message}", e)
        }
    }

    // Opens all API rules to null (admin token bypasses these anyway)
    private fun openRules(token: String, name: String) {
        try {
            val body = JSONObject().apply {
                put("listRule",   JSONObject.NULL)
                put("viewRule",   JSONObject.NULL)
                put("createRule", JSONObject.NULL)
                put("updateRule", JSONObject.NULL)
                put("deleteRule", JSONObject.NULL)
            }.toString()
            val res  = http.newCall(req("PATCH",
                "${AppConfig.BASE_URL}/api/collections/$name", token, body)).execute()
            Log.d(TAG, "Rules '$name' → ${res.code}")
            res.close()
        } catch (e: Exception) {
            Log.w(TAG, "openRules '$name': ${e.message}")
        }
    }

    private var _adminTokenProvider: com.example.ritik_2.core.AdminTokenProvider? = null

    fun setAdminTokenProvider(provider: com.example.ritik_2.core.AdminTokenProvider) {
        _adminTokenProvider = provider
    }

    private fun getAdminToken(): String? {
        val provider = _adminTokenProvider ?: return null
        return try {
            provider.getAdminTokenSync()
        } catch (_: Exception) {
            Log.w(TAG, "Admin token not available — admin credentials not configured")
            null
        }
    }

    private fun isServerReachable(): Boolean {
        return try {
            val res = http.newCall(req("GET",
                "${AppConfig.BASE_URL}/api/health", "")).execute()
            val ok = res.isSuccessful; res.close(); ok
        } catch (_: Exception) { false }
    }

    private fun req(method: String, url: String, token: String, body: String? = null): Request {
        val builder = Request.Builder().url(url)
        if (token.isNotEmpty()) builder.addHeader("Authorization", "Bearer $token")
        when (method) {
            "GET"   -> builder.get()
            "POST"  -> builder.post((body ?: "{}").toRequestBody("application/json".toMediaType()))
            "PATCH" -> builder.patch((body ?: "{}").toRequestBody("application/json".toMediaType()))
        }
        return builder.build()
    }

    // ── Schema definitions ────────────────────────────────────────────────────

    private fun companiesFields(): List<JSONObject> = listOf(
        f("originalName",   "text", required = true),
        f("sanitizedName",  "text", required = true),
        f("totalUsers",     "number"),
        f("activeUsers",    "number"),
        f("availableRoles", "json"),
        f("departments",    "json")
    )

    private fun accessControlFields(): List<JSONObject> = listOf(
        f("userId",                 "text", required = true),
        f("name",                   "text"),
        f("email",                  "email"),
        f("companyName",            "text"),
        f("sanitizedCompanyName",   "text"),
        f("department",             "text"),
        f("sanitizedDepartment",    "text"),
        f("role",                   "text"),
        f("designation",            "text"),
        f("permissions",            "json"),
        f("isActive",               "bool"),
        f("documentPath",           "text"),
        f("needsProfileCompletion", "bool")
    )

    private fun searchIndexFields(): List<JSONObject> = listOf(
        f("userId",               "text", required = true),
        f("name",                 "text"),
        f("email",                "email"),
        f("companyName",          "text"),
        f("sanitizedCompanyName", "text"),
        f("department",           "text"),
        f("sanitizedDepartment",  "text"),
        f("role",                 "text"),
        f("designation",          "text"),
        f("isActive",             "bool"),
        f("searchTerms",          "json"),
        f("documentPath",         "text")
    )

    private fun f(name: String, type: String, required: Boolean = false) = JSONObject().apply {
        put("name", name); put("type", type); put("required", required)
    }
}