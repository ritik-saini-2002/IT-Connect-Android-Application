package com.example.ritik_2.pocketbase

import android.util.Log
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.dsl.login
import io.github.agrevster.pocketbaseKotlin.models.AuthRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object PocketBaseInitializer {

    private const val TAG = "PocketBaseInit"

    const val ADMIN_EMAIL    = "ritiksaini19757@gmail.com"   // ← your admin email
    const val ADMIN_PASSWORD = "Ritik@2002"       // ← your admin password

    const val COL_USERS          = "users"
    const val COL_COMPANIES      = "companies_metadata"
    const val COL_ACCESS_CONTROL = "user_access_control"
    const val COL_SEARCH_INDEX   = "user_search_index"

    private val httpClient = OkHttpClient()

    suspend fun initializeCollections(client: PocketbaseClient) {
        withContext(Dispatchers.IO) {
            try {
                // ✅ SDK 2.7.3 admin login via _superusers
                val authResponse = client.records
                    .authWithPassword<AuthRecord>("_superusers", ADMIN_EMAIL, ADMIN_PASSWORD)
                val token = authResponse.token
                    ?: throw Exception("No admin token received")
                client.login { this.token = token }
                Log.d(TAG, "Admin authenticated ✅")

                ensureCollection(token, COL_COMPANIES,      buildCompaniesSchema())
                ensureCollection(token, COL_ACCESS_CONTROL, buildAccessControlSchema())
                ensureCollection(token, COL_SEARCH_INDEX,   buildSearchIndexSchema())

                Log.d(TAG, "All collections initialized ✅")

            } catch (e: Exception) {
                Log.e(TAG, "initializeCollections failed: ${e.message}", e)
            }
        }
    }

    private fun ensureCollection(token: String, name: String, schema: String) {
        try {
            // Check if exists via HTTP GET
            val checkRequest = Request.Builder()
                .url("${PocketBaseClient.BASE_URL}/api/collections/$name")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val checkResponse = httpClient.newCall(checkRequest).execute()

            if (checkResponse.isSuccessful) {
                Log.d(TAG, "Collection '$name' already exists — skipping ✅")
                checkResponse.close()
                return
            }
            checkResponse.close()

            // Create via HTTP POST
            val body = """{"name":"$name","type":"base","schema":$schema}"""
                .toRequestBody("application/json".toMediaType())

            val createRequest = Request.Builder()
                .url("${PocketBaseClient.BASE_URL}/api/collections")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            val createResponse = httpClient.newCall(createRequest).execute()
            if (createResponse.isSuccessful) {
                Log.d(TAG, "Collection '$name' created ✅")
            } else {
                Log.e(TAG, "Failed to create '$name': ${createResponse.code} ${createResponse.body?.string()}")
            }
            createResponse.close()

        } catch (e: Exception) {
            Log.e(TAG, "ensureCollection '$name' error: ${e.message}", e)
        }
    }

    private fun buildCompaniesSchema() = """
    [
        {"name":"originalName",   "type":"text",   "required":true},
        {"name":"sanitizedName",  "type":"text",   "required":true},
        {"name":"totalUsers",     "type":"number", "required":false},
        {"name":"activeUsers",    "type":"number", "required":false},
        {"name":"availableRoles", "type":"json",   "required":false},
        {"name":"departments",    "type":"json",   "required":false}
    ]
    """.trimIndent()

    private fun buildAccessControlSchema() = """
    [
        {"name":"userId",               "type":"text",  "required":true},
        {"name":"name",                 "type":"text",  "required":false},
        {"name":"email",                "type":"email", "required":false},
        {"name":"companyName",          "type":"text",  "required":false},
        {"name":"sanitizedCompanyName", "type":"text",  "required":false},
        {"name":"department",           "type":"text",  "required":false},
        {"name":"sanitizedDepartment",  "type":"text",  "required":false},
        {"name":"role",                 "type":"text",  "required":false},
        {"name":"permissions",          "type":"json",  "required":false},
        {"name":"isActive",             "type":"bool",  "required":false},
        {"name":"documentPath",         "type":"text",  "required":false}
    ]
    """.trimIndent()

    private fun buildSearchIndexSchema() = """
    [
        {"name":"userId",               "type":"text",  "required":true},
        {"name":"name",                 "type":"text",  "required":false},
        {"name":"email",                "type":"email", "required":false},
        {"name":"companyName",          "type":"text",  "required":false},
        {"name":"sanitizedCompanyName", "type":"text",  "required":false},
        {"name":"department",           "type":"text",  "required":false},
        {"name":"sanitizedDepartment",  "type":"text",  "required":false},
        {"name":"role",                 "type":"text",  "required":false},
        {"name":"designation",          "type":"text",  "required":false},
        {"name":"isActive",             "type":"bool",  "required":false},
        {"name":"searchTerms",          "type":"json",  "required":false},
        {"name":"documentPath",         "type":"text",  "required":false}
    ]
    """.trimIndent()
}