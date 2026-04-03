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

        // Step 1: Add fields to users auth collection
        patchUsersCollection(token)

        // Step 2: Create/patch base collections
        ensureBaseCollection(token, "companies_metadata",  companiesFields())
        ensureBaseCollection(token, "user_access_control", accessControlFields())
        ensureBaseCollection(token, "user_search_index",   searchIndexFields())

        // Step 3: Open API rules on everything
        listOf("users", "companies_metadata", "user_access_control", "user_search_index")
            .forEach { openRules(token, it) }

        Log.d(TAG, "✅ Init complete")
        Log.d(TAG, "========================================")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PocketBase v0.36 uses "fields" array (not "schema") for base collections
    // and still uses "schema" for auth collections.
    // ─────────────────────────────────────────────────────────────────────────

    private fun patchUsersCollection(token: String) {
        try {
            // GET current users collection
            val getRes  = http.newCall(req("GET",
                "${AppConfig.BASE_URL}/api/collections/users", token)).execute()
            val getBody = getRes.body?.string() ?: ""; getRes.close()

            if (!getRes.isSuccessful) {
                Log.e(TAG, "❌ Cannot fetch users: ${getRes.code} $getBody"); return
            }

            val col         = JSONObject(getBody)
            // v0.36 auth collections use "schema" key
            val existing    = col.optJSONArray("schema") ?: JSONArray()
            val existNames  = (0 until existing.length())
                .map { existing.getJSONObject(it).optString("name") }.toSet()

            val toAdd = usersExtraFields().filter {
                it.optString("name") !in existNames
            }
            if (toAdd.isEmpty()) { Log.d(TAG, "✅ users fields OK"); return }

            val merged = JSONArray()
            for (i in 0 until existing.length()) merged.put(existing.getJSONObject(i))
            toAdd.forEach { merged.put(it) }

            val patchRes = http.newCall(req("PATCH",
                "${AppConfig.BASE_URL}/api/collections/users", token,
                JSONObject().put("schema", merged).toString())).execute()
            Log.d(TAG, if (patchRes.isSuccessful)
                "✅ users patched — added ${toAdd.size} fields"
            else "❌ users patch failed: ${patchRes.code} ${patchRes.body?.string()}")
            patchRes.close()
        } catch (e: Exception) {
            Log.e(TAG, "❌ patchUsersCollection: ${e.message}", e)
        }
    }

    private fun ensureBaseCollection(token: String, name: String, fields: List<JSONObject>) {
        try {
            // Check if collection exists
            val checkRes  = http.newCall(req("GET",
                "${AppConfig.BASE_URL}/api/collections/$name", token)).execute()
            val checkBody = checkRes.body?.string() ?: ""
            val exists    = checkRes.isSuccessful
            checkRes.close()

            if (exists) {
                Log.d(TAG, "Collection '$name' exists — patching fields...")
                patchBaseCollectionFields(token, name, fields, checkBody)
            } else {
                Log.d(TAG, "Creating collection '$name'...")
                createBaseCollection(token, name, fields)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ensureBaseCollection '$name': ${e.message}", e)
        }
    }

    private fun createBaseCollection(token: String, name: String, fields: List<JSONObject>) {
        // v0.36 base collections: use "fields" key
        val fieldsArr = JSONArray().also { arr -> fields.forEach { arr.put(it) } }
        val body = JSONObject().apply {
            put("name",       name)
            put("type",       "base")
            put("fields",     fieldsArr)   // v0.36 uses "fields"
            put("listRule",   JSONObject.NULL)
            put("viewRule",   JSONObject.NULL)
            put("createRule", JSONObject.NULL)
            put("updateRule", JSONObject.NULL)
            put("deleteRule", JSONObject.NULL)
        }.toString()

        val res     = http.newCall(req("POST",
            "${AppConfig.BASE_URL}/api/collections", token, body)).execute()
        val resBody = res.body?.string() ?: ""
        Log.d(TAG, if (res.isSuccessful)
            "✅ '$name' created"
        else "❌ '$name' create failed: ${res.code} $resBody")
        res.close()
    }

    private fun patchBaseCollectionFields(
        token: String, name: String,
        newFields: List<JSONObject>, existingJson: String
    ) {
        try {
            val col = JSONObject(existingJson)
            // v0.36 base collections return "fields" key
            val existing   = col.optJSONArray("fields")
                ?: col.optJSONArray("schema")   // fallback
                ?: JSONArray()
            val existNames = (0 until existing.length())
                .map { existing.getJSONObject(it).optString("name") }.toSet()

            val toAdd = newFields.filter { it.optString("name") !in existNames }
            if (toAdd.isEmpty()) { Log.d(TAG, "✅ '$name' fields OK"); return }

            val merged = JSONArray()
            for (i in 0 until existing.length()) merged.put(existing.getJSONObject(i))
            toAdd.forEach { merged.put(it) }

            // Try "fields" key first (v0.36), fallback to "schema"
            val patchBody = JSONObject().put("fields", merged).toString()
            val res       = http.newCall(req("PATCH",
                "${AppConfig.BASE_URL}/api/collections/$name", token, patchBody)).execute()
            val resBody   = res.body?.string() ?: ""

            if (res.isSuccessful) {
                Log.d(TAG, "✅ '$name' patched — added ${toAdd.size} fields")
            } else {
                // Retry with "schema" key for older PB versions
                res.close()
                val res2 = http.newCall(req("PATCH",
                    "${AppConfig.BASE_URL}/api/collections/$name", token,
                    JSONObject().put("schema", merged).toString())).execute()
                Log.d(TAG, if (res2.isSuccessful)
                    "✅ '$name' patched (schema key) — added ${toAdd.size} fields"
                else "❌ '$name' patch failed: ${res2.code} ${res2.body?.string()}")
                res2.close()
                return
            }
            res.close()
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

    // ─────────────────────────────────────────────────────────────────────────
    // Admin auth
    // ─────────────────────────────────────────────────────────────────────────

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
        Log.e(TAG, "EMAIL=${AppConfig.ADMIN_EMAIL} BASE=${AppConfig.BASE_URL}")
        return null
    }

    private fun isServerReachable(): Boolean {
        return try {
            val res = http.newCall(req("GET", "${AppConfig.BASE_URL}/api/health", "")).execute()
            val ok  = res.isSuccessful; res.close(); ok
        } catch (_: Exception) { false }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP helper
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // Schema definitions
    // v0.36 field format: {"name":"x","type":"text","required":false}
    // ─────────────────────────────────────────────────────────────────────────

    // For users (auth collection) — uses "schema" key
    private fun usersExtraFields(): List<JSONObject> = listOf(
        f("userId",               "text"),
        f("role",                 "text"),
        f("companyName",          "text"),
        f("sanitizedCompanyName", "text"),
        f("department",           "text"),
        f("sanitizedDepartment",  "text"),
        f("designation",          "text"),
        f("isActive",             "bool"),
        f("documentPath",         "text"),
        f("permissions",          "json"),
        f("profile",              "json"),
        f("workStats",            "json"),
        f("issues",               "json"),
        f("needsProfileCompletion", "bool")
    )

    // For base collections — uses "fields" key in v0.36
    private fun companiesFields(): List<JSONObject> = listOf(
        f("originalName",   "text", required = true),
        f("sanitizedName",  "text", required = true),
        f("totalUsers",     "number"),
        f("activeUsers",    "number"),
        f("availableRoles", "json"),
        f("departments",    "json")
    )

    private fun accessControlFields(): List<JSONObject> = listOf(
        f("userId",               "text", required = true),
        f("name",                 "text"),
        f("email",                "email"),
        f("companyName",          "text"),
        f("sanitizedCompanyName", "text"),
        f("department",           "text"),
        f("sanitizedDepartment",  "text"),
        f("role",                 "text"),
        f("designation",          "text"),
        f("permissions",          "json"),
        f("isActive",             "bool"),
        f("documentPath",         "text"),
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
        put("name",     name)
        put("type",     type)
        put("required", required)
    }
}