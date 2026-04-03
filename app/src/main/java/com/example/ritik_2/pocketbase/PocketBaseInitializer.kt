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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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

        // Step 1: users fields must be added MANUALLY in PocketBase UI
        // (v0.36 does not expose the built-in users auth collection via REST API)
        Log.d(TAG, "ℹ️ users fields must be added manually in PocketBase UI")

        // Step 2: Create base collections
        ensureBaseCollection(token, "companies_metadata",  companiesFields())
        ensureBaseCollection(token, "user_access_control", accessControlFields())
        ensureBaseCollection(token, "user_search_index",   searchIndexFields())

        // Step 3: Open API rules
        listOf("companies_metadata", "user_access_control", "user_search_index")
            .forEach { openRules(token, it) }

        // Step 4: users rules must be opened MANUALLY in PocketBase UI
        Log.d(TAG, "ℹ️ Open users API rules manually in PocketBase UI")

        Log.d(TAG, "✅ Init complete")
        Log.d(TAG, "========================================")
    }

    // ── In PB v0.36, list ALL collections and find the auth one named "users" ──
    // Then use its ID (not name) for all subsequent requests

    private fun findUsersCollectionId(token: String): String? {
        return try {
            val res  = http.newCall(req("GET",
                "${AppConfig.BASE_URL}/api/collections?perPage=200", token)).execute()
            val body = res.body?.string() ?: ""; res.close()
            if (!res.isSuccessful) {
                Log.e(TAG, "❌ List collections failed: ${res.code} $body"); return null
            }

            val json  = JSONObject(body)
            // PB v0.36 returns { "items": [...] } OR just an array
            val items = when {
                json.has("items") -> json.optJSONArray("items")
                else              -> JSONArray(body)
            } ?: return null

            for (i in 0 until items.length()) {
                val col  = items.getJSONObject(i)
                val name = col.optString("name")
                val type = col.optString("type")
                // Match "users" auth collection (type = "auth")
                if ((name == "users" || name == "_pb_users_auth_") && type == "auth") {
                    val id = col.optString("id")
                    Log.d(TAG, "✅ Found users collection: name=$name id=$id")
                    return id
                }
            }

            // Fallback: return first auth collection found
            for (i in 0 until items.length()) {
                val col  = items.getJSONObject(i)
                if (col.optString("type") == "auth") {
                    val id   = col.optString("id")
                    val name = col.optString("name")
                    Log.d(TAG, "✅ Using auth collection: name=$name id=$id")
                    return id
                }
            }

            Log.e(TAG, "❌ No auth collection found in list")
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ findUsersCollectionId: ${e.message}"); null
        }
    }

    private fun patchUsersCollectionV036(token: String) {
        try {
            val collId = findUsersCollectionId(token) ?: run {
                Log.e(TAG, "❌ Cannot patch users — collection not found"); return
            }

            // GET current schema using collection ID
            val getRes  = http.newCall(req("GET",
                "${AppConfig.BASE_URL}/api/collections/$collId", token)).execute()
            val getBody = getRes.body?.string() ?: ""; getRes.close()

            if (!getRes.isSuccessful) {
                Log.e(TAG, "❌ Cannot fetch users by ID: ${getRes.code} $getBody"); return
            }

            val col        = JSONObject(getBody)
            // v0.36 auth collections use "schema" key
            val existing   = col.optJSONArray("schema") ?: JSONArray()
            val existNames = (0 until existing.length())
                .map { existing.getJSONObject(it).optString("name") }.toSet()

            Log.d(TAG, "Users existing fields: $existNames")

            val toAdd = usersExtraFields().filter { it.optString("name") !in existNames }
            if (toAdd.isEmpty()) {
                Log.d(TAG, "✅ users fields already complete"); return
            }

            val merged = JSONArray()
            for (i in 0 until existing.length()) merged.put(existing.getJSONObject(i))
            toAdd.forEach { merged.put(it) }

            // PATCH using collection ID
            val patchRes = http.newCall(req("PATCH",
                "${AppConfig.BASE_URL}/api/collections/$collId", token,
                JSONObject().put("schema", merged).toString())).execute()
            val patchBody = patchRes.body?.string() ?: ""
            Log.d(TAG, if (patchRes.isSuccessful)
                "✅ users patched — added ${toAdd.size} fields: ${toAdd.map { it.optString("name") }}"
            else
                "❌ users patch failed: ${patchRes.code} $patchBody")
            patchRes.close()
        } catch (e: Exception) {
            Log.e(TAG, "❌ patchUsersCollectionV036: ${e.message}", e)
        }
    }

    private fun openUsersRulesV036(token: String) {
        try {
            val collId = findUsersCollectionId(token) ?: return
            val body   = """{"listRule":null,"viewRule":null,"createRule":null,"updateRule":null,"deleteRule":null}"""
            val res    = http.newCall(req("PATCH",
                "${AppConfig.BASE_URL}/api/collections/$collId", token, body)).execute()
            Log.d(TAG, "Rules users (by ID) → ${res.code}")
            res.close()
        } catch (e: Exception) {
            Log.w(TAG, "openUsersRulesV036: ${e.message}")
        }
    }

    private fun ensureBaseCollection(token: String, name: String, fields: List<JSONObject>) {
        try {
            val checkRes  = http.newCall(req("GET",
                "${AppConfig.BASE_URL}/api/collections/$name", token)).execute()
            val checkBody = checkRes.body?.string() ?: ""
            val exists    = checkRes.isSuccessful
            checkRes.close()

            if (exists) {
                Log.d(TAG, "Collection '$name' exists — checking fields...")
                patchBaseCollectionFields(token, name, fields, checkBody)
            } else {
                createBaseCollection(token, name, fields)
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

        val res  = http.newCall(req("POST",
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

            // Try "fields" first (v0.36+), then "schema" (older)
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

    private fun openRules(token: String, name: String) {
        try {
            val body = """{"listRule":null,"viewRule":null,"createRule":null,"updateRule":null,"deleteRule":null}"""
            val res  = http.newCall(req("PATCH",
                "${AppConfig.BASE_URL}/api/collections/$name", token, body)).execute()
            Log.d(TAG, "Rules '$name' → ${res.code}")
            res.close()
        } catch (e: Exception) {
            Log.w(TAG, "openRules '$name': ${e.message}")
        }
    }

    // ── Admin auth ────────────────────────────────────────────────────────────

    private fun getAdminToken(): String? {
        listOf(
            "${AppConfig.BASE_URL}/api/collections/_superusers/auth-with-password",
            "${AppConfig.BASE_URL}/api/admins/auth-with-password"
        ).forEach { url ->
            try {
                val body = JSONObject().apply {
                    put("identity", AppConfig.ADMIN_EMAIL)
                    put("password", AppConfig.ADMIN_PASS)
                }.toString()
                val res     = http.newCall(req("POST", url, "", body)).execute()
                val resBody = res.body?.string() ?: ""
                val ok      = res.isSuccessful; res.close()
                Log.d(TAG, "Admin auth $url → ${res.code}")
                if (ok) {
                    val t = JSONObject(resBody).optString("token")
                    if (t.isNotEmpty()) return t
                }
            } catch (e: Exception) { Log.w(TAG, "Auth failed $url: ${e.message}") }
        }
        return null
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

    private fun usersExtraFields(): List<JSONObject> = listOf(
        f("userId",                 "text"),
        f("role",                   "text"),
        f("companyName",            "text"),
        f("sanitizedCompanyName",   "text"),
        f("department",             "text"),
        f("sanitizedDepartment",    "text"),
        f("designation",            "text"),
        f("isActive",               "bool"),
        f("documentPath",           "text"),
        f("permissions",            "json"),
        f("profile",                "json"),
        f("workStats",              "json"),
        f("issues",                 "json"),
        f("needsProfileCompletion", "bool")
    )

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