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
        .build()

    const val COL_COMPANIES      = "companies_metadata"
    const val COL_ACCESS_CONTROL = "user_access_control"
    const val COL_SEARCH_INDEX   = "user_search_index"

    suspend fun initialize() {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "Starting PocketBase initialization...")
            Log.d(TAG, "BASE_URL: ${AppConfig.BASE_URL}")

            if (!isServerReachable()) {
                Log.e(TAG, "❌ Server not reachable at ${AppConfig.BASE_URL}")
                return
            }
            Log.d(TAG, "✅ Server is reachable")

            val token = getAdminToken() ?: return

            ensureUserCollectionFields(token)
            ensureCollection(token, COL_COMPANIES,      companiesFields())
            ensureCollection(token, COL_ACCESS_CONTROL, accessControlFields())
            ensureCollection(token, COL_SEARCH_INDEX,   searchIndexFields())

            Log.d(TAG, "✅ All collections ready")
            Log.d(TAG, "========================================")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialization failed: ${e.message}", e)
        }
    }

    private fun isServerReachable(): Boolean {
        return try {
            val req = Request.Builder()
                .url("${AppConfig.BASE_URL}/api/health")
                .get().build()
            val res = http.newCall(req).execute()
            val ok  = res.isSuccessful
            Log.d(TAG, "Health check → HTTP ${res.code}")
            res.close()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed: ${e.message}")
            false
        }
    }

    // ── Add custom fields to users auth collection ────────────
    private fun ensureUserCollectionFields(token: String) {
        try {
            val getReq = Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/users")
                .addHeader("Authorization", "Bearer $token")
                .get().build()
            val getRes  = http.newCall(getReq).execute()
            val getBody = getRes.body?.string() ?: ""
            getRes.close()

            if (!getRes.isSuccessful) {
                Log.e(TAG, "❌ Failed to fetch users collection: ${getRes.code} — $getBody")
                return
            }

            val collection  = JSONObject(getBody)
            val existSchema = collection.optJSONArray("schema") ?: JSONArray()

            val existingFields = mutableSetOf<String>()
            for (i in 0 until existSchema.length()) {
                existingFields.add(existSchema.getJSONObject(i).optString("name"))
            }

            val requiredFields = usersExtraFields()
            val newFields      = JSONArray()

            for (i in 0 until existSchema.length()) newFields.put(existSchema.getJSONObject(i))

            var addedCount = 0
            for (i in 0 until requiredFields.length()) {
                val f = requiredFields.getJSONObject(i)
                if (f.optString("name") !in existingFields) {
                    newFields.put(f)
                    addedCount++
                }
            }

            if (addedCount == 0) {
                Log.d(TAG, "✅ users collection already has all required fields")
                return
            }

            val updateBody = JSONObject().apply {
                put("schema", newFields)
            }.toString().toRequestBody("application/json".toMediaType())

            val updateReq = Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/users")
                .addHeader("Authorization", "Bearer $token")
                .patch(updateBody).build()

            val updateRes  = http.newCall(updateReq).execute()
            val updateBody2 = updateRes.body?.string() ?: ""
            val updateCode  = updateRes.code
            updateRes.close()

            if (updateRes.isSuccessful) {
                Log.d(TAG, "✅ users collection updated — added $addedCount new fields")
            } else {
                Log.e(TAG, "❌ Failed to update users collection: $updateCode — $updateBody2")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ensureUserCollectionFields exception: ${e.message}", e)
        }
    }

    // ── Admin Auth ────────────────────────────────────────────
    private fun getAdminToken(): String? {
        val endpoints = listOf(
            "${AppConfig.BASE_URL}/api/collections/_superusers/auth-with-password",
            "${AppConfig.BASE_URL}/api/admins/auth-with-password",
            "${AppConfig.BASE_URL}/api/collections/_admins/auth-with-password"
        )
        for (url in endpoints) {
            val token = tryAdminAuth(url)
            if (token != null) {
                Log.d(TAG, "✅ Admin auth succeeded at: $url")
                return token
            }
        }
        Log.e(TAG, "❌ All admin auth endpoints failed.")
        Log.e(TAG, "   EMAIL used : '${AppConfig.ADMIN_EMAIL}'")
        Log.e(TAG, "   PASS length: ${AppConfig.ADMIN_PASS.length} chars")
        Log.e(TAG, "   BASE_URL   : '${AppConfig.BASE_URL}'")
        return null
    }

    private fun tryAdminAuth(url: String): String? {
        return try {
            val body = JSONObject().apply {
                put("identity", AppConfig.ADMIN_EMAIL)
                put("password", AppConfig.ADMIN_PASS)
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder().url(url).post(body).build()
            val res     = http.newCall(req).execute()
            val resBody = res.body?.string() ?: ""
            val success = res.isSuccessful
            res.close()

            Log.d(TAG, "   Tried $url → HTTP ${res.code}")

            if (success) {
                val token = JSONObject(resBody).optString("token")
                if (token.isNotEmpty()) token else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "   Exception at $url: ${e.message}")
            null
        }
    }

    // ── Collection Management ─────────────────────────────────
    private fun ensureCollection(token: String, name: String, fields: JSONArray) {
        // Check existence
        val checkReq = Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections/$name")
            .addHeader("Authorization", "Bearer $token")
            .get().build()
        val checkRes = http.newCall(checkReq).execute()
        val exists   = checkRes.isSuccessful
        checkRes.close()

        if (exists) {
            Log.d(TAG, "✅ Collection '$name' already exists — ensuring rules")
            updateCollectionRules(token, name)
            return
        }

        // Create with open API rules
        val body = JSONObject().apply {
            put("name",       name)
            put("type",       "base")
            put("schema",     fields)
            put("listRule",   "")
            put("viewRule",   "")
            put("createRule", "")
            put("updateRule", "")
            put("deleteRule", JSONObject.NULL)
        }.toString().toRequestBody("application/json".toMediaType())

        val createReq = Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections")
            .addHeader("Authorization", "Bearer $token")
            .post(body).build()

        val createRes  = http.newCall(createReq).execute()
        val createBody = createRes.body?.string() ?: ""
        val createCode = createRes.code
        createRes.close()

        if (createRes.isSuccessful) {
            Log.d(TAG, "✅ Collection '$name' created with open rules")
        } else {
            Log.e(TAG, "❌ Failed to create '$name': HTTP $createCode — $createBody")
        }
    }

    private fun updateCollectionRules(token: String, name: String) {
        try {
            val body = JSONObject().apply {
                put("listRule",   "")
                put("viewRule",   "")
                put("createRule", "")
                put("updateRule", "")
                put("deleteRule", JSONObject.NULL)
            }.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$name")
                .addHeader("Authorization", "Bearer $token")
                .patch(body).build()
            val res = http.newCall(req).execute()
            Log.d(TAG, "Rules update '$name' → HTTP ${res.code}")
            res.close()
        } catch (e: Exception) {
            Log.w(TAG, "updateCollectionRules '$name' failed: ${e.message}")
        }
    }

    // ── Schema Definitions ────────────────────────────────────
    private fun usersExtraFields() = JSONArray().apply {
        put(field("userId",               "text"))
        put(field("role",                 "text"))
        put(field("companyName",          "text"))
        put(field("sanitizedCompanyName", "text"))
        put(field("department",           "text"))
        put(field("sanitizedDepartment",  "text"))
        put(field("designation",          "text"))
        put(field("isActive",             "bool"))
        put(field("documentPath",         "text"))
        put(field("permissions",          "json"))
        put(field("profile",              "json"))
        put(field("workStats",            "json"))
        put(field("issues",               "json"))
    }

    private fun companiesFields() = JSONArray().apply {
        put(field("originalName",   "text", required = true))
        put(field("sanitizedName",  "text", required = true))
        put(field("totalUsers",     "number"))
        put(field("activeUsers",    "number"))
        put(field("availableRoles", "json"))
        put(field("departments",    "json"))
    }

    private fun accessControlFields() = JSONArray().apply {
        put(field("userId",               "text", required = true))
        put(field("name",                 "text"))
        put(field("email",                "email"))
        put(field("companyName",          "text"))
        put(field("sanitizedCompanyName", "text"))
        put(field("department",           "text"))
        put(field("sanitizedDepartment",  "text"))
        put(field("role",                 "text"))
        put(field("permissions",          "json"))
        put(field("isActive",             "bool"))
        put(field("documentPath",         "text"))
    }

    private fun searchIndexFields() = JSONArray().apply {
        put(field("userId",               "text", required = true))
        put(field("name",                 "text"))
        put(field("email",                "email"))
        put(field("companyName",          "text"))
        put(field("sanitizedCompanyName", "text"))
        put(field("department",           "text"))
        put(field("sanitizedDepartment",  "text"))
        put(field("role",                 "text"))
        put(field("designation",          "text"))
        put(field("isActive",             "bool"))
        put(field("searchTerms",          "json"))
        put(field("documentPath",         "text"))
    }

    private fun field(name: String, type: String, required: Boolean = false) =
        JSONObject().apply {
            put("name",     name)
            put("type",     type)
            put("required", required)
        }
}