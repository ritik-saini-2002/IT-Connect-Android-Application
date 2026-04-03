package com.example.ritik_2.pocketbase

import android.util.Log
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.StringUtils
import com.example.ritik_2.core.parseJsonList
import com.example.ritik_2.core.parseJsonMap
import com.example.ritik_2.data.model.*
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.data.source.dto.*
import io.github.agrevster.pocketbaseKotlin.dsl.login
import io.github.agrevster.pocketbaseKotlin.dsl.logout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

internal const val COL_USERS          = "users"
internal const val COL_COMPANIES      = "companies_metadata"
internal const val COL_ACCESS_CONTROL = "user_access_control"
internal const val COL_SEARCH_INDEX   = "user_search_index"

@Singleton
class PocketBaseDataSource @Inject constructor(
    private val http      : OkHttpClient,
    private val pbProvider: PocketBaseClientProvider
) : AppDataSource {

    private val pb  get() = pbProvider.client
    private val tag = "PBDataSource"

    // ── Auth ──────────────────────────────────────────────────────────────────

    override suspend fun login(email: String, password: String): AuthSession =
        withContext(Dispatchers.IO) {
            val (code, resBody) = authWithPassword(email, password)
            Log.d(tag, "login HTTP $code")

            if (code !in 200..299) {
                val msg = try {
                    JSONObject(resBody).optString("message", "Login failed")
                } catch (_: Exception) { "Login failed: HTTP $code" }
                error(msg)
            }

            val json   = JSONObject(resBody)
            val token  = json.optString("token").ifEmpty { error("No token received") }
            val record = json.optJSONObject("record") ?: error("No user record")
            val uid    = record.optString("id").ifEmpty { error("No user ID") }

            pb.login { this.token = token }

            val isActive = record.optBoolean("isActive", true)
            if (!isActive) {
                pb.logout()
                error("Your account has been disabled. Contact your administrator.")
            }

            val access = fetchAccessControl(uid).getOrNull()
            val role   = when {
                !access?.role.isNullOrBlank()         -> access!!.role
                record.optString("role").isNotBlank() -> record.optString("role")
                else                                  -> ""
            }

            AuthSession(
                userId       = uid,
                token        = token,
                email        = email,
                name         = access?.name         ?: record.optString("name"),
                role         = role,
                documentPath = access?.documentPath ?: record.optString("documentPath")
            )
        }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        try { pb.logout() } catch (_: Exception) {}
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                pb.records.requestPasswordReset(COL_USERS, email)
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }

    override suspend fun createUser(
        email     : String,
        password  : String,
        name      : String,
        adminToken: String
    ): String = withContext(Dispatchers.IO) {
        val token = adminToken.ifEmpty { getAdminToken() }

        val body = JSONObject().apply {
            put("email",           email)
            put("password",        password)
            put("passwordConfirm", password)
            put("name",            name)
            put("emailVisibility", true)
        }.toString().toRequestBody("application/json".toMediaType())

        val url = "${AppConfig.BASE_URL}/api/collections/$COL_USERS/records"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        val res     = http.newCall(req).execute()
        val resBody = res.body?.string() ?: ""
        val code    = res.code
        res.close()

        Log.d(tag, "createUser HTTP $code: $resBody")

        if (res.isSuccessful) {
            val userId = JSONObject(resBody).optString("id")
            if (userId.isNotEmpty()) {
                Log.d(tag, "createUser ✅ userId=$userId")
                return@withContext userId
            }
        }

        // Parse validation errors for clear message
        val errorMsg = try {
            val json = JSONObject(resBody)
            val data = json.optJSONObject("data")
            if (data != null && data.length() > 0) {
                val fieldErrors = data.keys().asSequence().joinToString(", ") { key ->
                    "$key: ${data.optJSONObject(key)?.optString("message") ?: "invalid"}"
                }
                "Validation failed — $fieldErrors"
            } else json.optString("message", "HTTP $code")
        } catch (_: Exception) { "HTTP $code — $resBody" }

        error("createUser failed: $errorMsg")
    }

    override suspend fun restoreSession(token: String) = withContext(Dispatchers.IO) {
        pb.login { this.token = token }
    }

    // ── User Profile ──────────────────────────────────────────────────────────

    override suspend fun getUserProfile(userId: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val user      = pb.records.getOne<UserRecord>(COL_USERS, userId)
                val access    = fetchAccessControl(userId).getOrNull()

                // profile/workStats/issues stored as JSON objects — use parseJsonElement
                val profile   = parseJsonElement(user.profile)
                val workStats = parseJsonElement(user.workStats)
                val issues    = parseJsonElement(user.issues)

                Result.success(UserProfile(
                    id                       = userId,
                    name                     = user.name.ifBlank { access?.name ?: "" },
                    email                    = user.email.ifBlank { access?.email ?: "" },
                    role                     = if (access?.role.isNullOrBlank()) user.role else access!!.role,
                    companyName              = user.companyName.ifBlank { access?.companyName ?: "" },
                    sanitizedCompany         = user.sanitizedCompanyName,
                    department               = user.department,
                    sanitizedDept            = user.sanitizedDepartment,
                    designation              = user.designation,
                    imageUrl                 = profile["imageUrl"]    ?: "",
                    phoneNumber              = profile["phoneNumber"] ?: "",
                    address                  = profile["address"]     ?: "",
                    employeeId               = profile["employeeId"]  ?: "",
                    reportingTo              = profile["reportingTo"] ?: "",
                    salary                   = profile["salary"]?.toDoubleOrNull() ?: 0.0,
                    experience               = workStats["experience"]?.toIntOrNull()        ?: 0,
                    completedProjects        = workStats["completedProjects"]?.toIntOrNull() ?: 0,
                    activeProjects           = workStats["activeProjects"]?.toIntOrNull()    ?: 0,
                    pendingTasks             = workStats["pendingTasks"]?.toIntOrNull()      ?: 0,
                    completedTasks           = workStats["completedTasks"]?.toIntOrNull()    ?: 0,
                    totalComplaints          = issues["totalComplaints"]?.toIntOrNull()      ?: 0,
                    resolvedComplaints       = issues["resolvedComplaints"]?.toIntOrNull()   ?: 0,
                    pendingComplaints        = issues["pendingComplaints"]?.toIntOrNull()    ?: 0,
                    isActive                 = access?.isActive ?: user.isActive,
                    documentPath             = user.documentPath.ifBlank { access?.documentPath ?: "" },
                    permissions              = parseJsonList(
                        if (access?.permissions.isNullOrBlank()) user.permissions
                        else access!!.permissions),
                    emergencyContactName     = profile["emergencyContactName"]     ?: "",
                    emergencyContactPhone    = profile["emergencyContactPhone"]    ?: "",
                    emergencyContactRelation = profile["emergencyContactRelation"] ?: "",
                    needsProfileCompletion   = user.needsProfileCompletion || user.designation.isBlank()
                ))
            } catch (e: Exception) {
                Log.e(tag, "getUserProfile failed: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun updateUserProfile(userId: String, fields: Map<String, Any>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject(fields).toString()
                    .toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                    .addHeader("Authorization", "Bearer ${getAuthToken()}")
                    .patch(body).build()
                val res = http.newCall(req).execute()
                if (!res.isSuccessful) {
                    val err = res.body?.string() ?: "Unknown error"
                    res.close()
                    return@withContext Result.failure(Exception("Update failed: $err"))
                }
                res.close()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(tag, "updateUserProfile failed: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun uploadProfileImage(
        userId  : String,
        bytes   : ByteArray,
        filename: String,
        token   : String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resolvedToken = token.ifEmpty { getAuthToken() }
            val requestBody   = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("avatar", filename,
                    bytes.toRequestBody("image/jpeg".toMediaType()))
                .build()
            val req = Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                .addHeader("Authorization", "Bearer $resolvedToken")
                .patch(requestBody).build()
            val res     = http.newCall(req).execute()
            val resBody = res.body?.string() ?: ""
            val resCode = res.code
            res.close()

            if (!res.isSuccessful)
                return@withContext Result.failure(Exception("Upload failed: $resCode — $resBody"))

            val storedFilename = try {
                JSONObject(resBody).optString("avatar", filename)
            } catch (_: Exception) { filename }

            Result.success("${AppConfig.BASE_URL}/api/files/$COL_USERS/$userId/$storedFilename")
        } catch (e: Exception) {
            Log.e(tag, "uploadProfileImage failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Registration ──────────────────────────────────────────────────────────

    override suspend fun registerUser(request: RegistrationRequest): Result<String> =
        withContext(Dispatchers.IO) {
            var createdUserId: String? = null
            try {
                val sc = StringUtils.sanitize(request.companyName)
                val sd = StringUtils.sanitize(request.department)

                if (companyExists(sc)) {
                    return@withContext Result.failure(
                        Exception("Company '${request.companyName}' already exists.")
                    )
                }

                val adminToken = getAdminToken()
                val userId     = createUser(request.email, request.password, request.name, adminToken)
                createdUserId  = userId

                // Login after creation using the shared helper (handles v0.36 URL)
                val (loginCode, loginJson) = authWithPassword(request.email, request.password)
                if (loginCode in 200..299) {
                    pb.login { this.token = JSONObject(loginJson).optString("token") }
                }

                val imageUrl = if (request.imageBytes != null) {
                    uploadProfileImage(userId, request.imageBytes, "profile_$userId.jpg", adminToken)
                        .getOrDefault("")
                } else ""

                val documentPath = "users/$sc/$sd/${request.role}/$userId"
                val permissions  = Json.encodeToString(Permissions.forRole(request.role))

                val profileJson = JSONObject().apply {
                    put("imageUrl",    imageUrl)
                    put("phoneNumber", request.phoneNumber)
                    put("address",     "")
                    put("employeeId",  "")
                    put("reportingTo", "")
                    put("salary",      0)
                    put("emergencyContactName",     "")
                    put("emergencyContactPhone",    "")
                    put("emergencyContactRelation", "")
                }.toString()

                val workJson = JSONObject().apply {
                    put("experience", 0); put("completedProjects", 0)
                    put("activeProjects", 0); put("pendingTasks", 0)
                    put("completedTasks", 0); put("totalWorkingHours", 0)
                    put("avgPerformanceRating", 0.0)
                }.toString()

                val issuesJson = JSONObject().apply {
                    put("totalComplaints", 0)
                    put("resolvedComplaints", 0)
                    put("pendingComplaints", 0)
                }.toString()

                httpPatch(
                    url   = "${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId",
                    token = adminToken,
                    body  = JSONObject().apply {
                        put("userId",               userId)
                        put("role",                 request.role)
                        put("companyName",          request.companyName)
                        put("sanitizedCompanyName", sc)
                        put("department",           request.department)
                        put("sanitizedDepartment",  sd)
                        put("designation",          request.designation)
                        put("isActive",             true)
                        put("documentPath",         documentPath)
                        put("permissions",          permissions)
                        put("profile",              profileJson)
                        put("workStats",            workJson)
                        put("issues",               issuesJson)
                        put("needsProfileCompletion", true)
                    }.toString()
                )

                upsertCompany(sc, request.companyName, request.role, request.department, adminToken)

                httpPost(
                    url   = "${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CONTROL/records",
                    token = adminToken,
                    body  = JSONObject().apply {
                        put("userId",               userId)
                        put("name",                 request.name)
                        put("email",                request.email)
                        put("companyName",          request.companyName)
                        put("sanitizedCompanyName", sc)
                        put("department",           request.department)
                        put("sanitizedDepartment",  sd)
                        put("role",                 request.role)
                        put("designation",          request.designation)
                        put("permissions",          permissions)
                        put("isActive",             true)
                        put("documentPath",         documentPath)
                        put("needsProfileCompletion", true)
                    }.toString()
                )

                val searchTerms = Json.encodeToString(
                    listOf(request.name, request.email, request.companyName,
                        request.department, request.role, request.designation)
                        .map { it.lowercase() }.filter { it.isNotEmpty() }
                )
                httpPost(
                    url   = "${AppConfig.BASE_URL}/api/collections/$COL_SEARCH_INDEX/records",
                    token = adminToken,
                    body  = JSONObject().apply {
                        put("userId",               userId)
                        put("name",                 request.name.lowercase())
                        put("email",                request.email.lowercase())
                        put("companyName",          request.companyName)
                        put("sanitizedCompanyName", sc)
                        put("department",           request.department)
                        put("sanitizedDepartment",  sd)
                        put("role",                 request.role)
                        put("designation",          request.designation)
                        put("isActive",             true)
                        put("searchTerms",          searchTerms)
                        put("documentPath",         documentPath)
                    }.toString()
                )

                Log.d(tag, "registerUser ✅  userId=$userId")
                Result.success(userId)

            } catch (e: Exception) {
                createdUserId?.let { deleteUserSilently(it) }
                Log.e(tag, "registerUser failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    // ── Company ───────────────────────────────────────────────────────────────

    override suspend fun companyExists(sanitizedName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records" +
                            "?filter=sanitizedName%3D%27$sanitizedName%27&perPage=1")
                    .get().build()
                val res  = http.newCall(req).execute()
                val body = res.body?.string() ?: ""
                res.close()
                JSONObject(body).optInt("totalItems", 0) > 0
            } catch (_: Exception) { false }
        }

    private fun upsertCompany(
        sc: String, companyName: String, role: String,
        department: String, adminToken: String
    ) {
        val url    = "${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records"
        val getRes = http.newCall(Request.Builder()
            .url("$url?filter=(sanitizedName='$sc')")
            .addHeader("Authorization", "Bearer $adminToken").get().build()).execute()
        val getBody   = getRes.body?.string() ?: ""
        getRes.close()
        val companyId = JSONObject(getBody).optJSONArray("items")
            ?.optJSONObject(0)?.optString("id")

        val payload = JSONObject().apply {
            put("sanitizedName",  sc)
            put("originalName",   companyName)
            put("departments",    JSONArray().put(department))
            put("availableRoles", JSONArray().put(role))
            put("totalUsers",     1)
            put("activeUsers",    1)
            put("lastUpdated",    System.currentTimeMillis())
        }.toString()

        if (companyId.isNullOrEmpty()) httpPost(url, adminToken, payload)
        else                           httpPatch("$url/$companyId", adminToken, payload)
    }

    override suspend fun getOrCreateCompany(
        sanitizedName: String, originalName: String,
        role: String, department: String, adminToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = adminToken.ifEmpty { getAdminToken() }
            val req   = Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records" +
                        "?filter=sanitizedName%3D%27$sanitizedName%27&perPage=1")
                .addHeader("Authorization", "Bearer $token").get().build()
            val res  = http.newCall(req).execute()
            val body = res.body?.string() ?: ""
            res.close()

            val json       = JSONObject(body)
            val totalItems = json.optInt("totalItems", 0)

            if (totalItems > 0) {
                val item      = json.getJSONArray("items").getJSONObject(0)
                val id        = item.optString("id")
                val rolesJson = JSONArray(item.optString("availableRoles", "[]"))
                val rolesList = ArrayList<String>()
                for (i in 0 until rolesJson.length()) rolesList.add(rolesJson.getString(i))
                if (role !in rolesList) rolesList.add(role)
                val deptsJson = JSONArray(item.optString("departments", "[]"))
                val deptsList = ArrayList<String>()
                for (i in 0 until deptsJson.length()) deptsList.add(deptsJson.getString(i))
                if (department !in deptsList) deptsList.add(department)
                httpPatch(
                    "${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records/$id", token,
                    JSONObject().apply {
                        put("totalUsers",     item.optInt("totalUsers",  0) + 1)
                        put("activeUsers",    item.optInt("activeUsers", 0) + 1)
                        put("availableRoles", Json.encodeToString(rolesList))
                        put("departments",    Json.encodeToString(deptsList))
                    }.toString()
                )
            } else {
                httpPost(
                    "${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records", token,
                    JSONObject().apply {
                        put("originalName",   originalName)
                        put("sanitizedName",  sanitizedName)
                        put("totalUsers",     1)
                        put("activeUsers",    1)
                        put("availableRoles", Json.encodeToString(listOf(role)))
                        put("departments",    Json.encodeToString(listOf(department)))
                    }.toString()
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "getOrCreateCompany failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Collections setup ─────────────────────────────────────────────────────

    override suspend fun ensureCollectionsExist(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val token = getAdminToken()
                listOf(
                    COL_COMPANIES      to buildCompaniesSchema(),
                    COL_ACCESS_CONTROL to buildAccessControlSchema(),
                    COL_SEARCH_INDEX   to buildSearchIndexSchema()
                ).forEach { (name, schema) -> ensureCollection(token, name, schema) }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(tag, "ensureCollectionsExist failed: ${e.message}")
                Result.failure(e)
            }
        }

    // ── Private helpers ───────────────────────────────────────────────────────

    // Tries both auth-with-password URL forms for PocketBase v0.36 compatibility
    private fun authWithPassword(email: String, password: String): Pair<Int, String> {
        val urlsToTry = listOf(
            "${AppConfig.BASE_URL}/api/collections/$COL_USERS/auth-with-password",
            "${AppConfig.BASE_URL}/api/collections/_pb_users_auth_/auth-with-password"
        )
        for (url in urlsToTry) {
            val body = JSONObject().apply {
                put("identity", email)
                put("password", password)
            }.toString().toRequestBody("application/json".toMediaType())
            val res     = http.newCall(Request.Builder().url(url).post(body).build()).execute()
            val resBody = res.body?.string() ?: ""
            val code    = res.code
            res.close()
            Log.d(tag, "authWithPassword $url → HTTP $code")
            if (code != 404) return Pair(code, resBody)
        }
        return Pair(404, """{"message":"Missing or invalid auth collection context."}""")
    }

    // Converts JsonElement (object or string) → Map<String, String>
    private fun parseJsonElement(
        element: kotlinx.serialization.json.JsonElement
    ): Map<String, String> {
        return try {
            when (element) {
                is JsonObject -> element.entries.associate { (k, v) ->
                    k to v.toString().trim('"')
                }
                is JsonPrimitive -> parseJsonMap(element.content)
                else             -> emptyMap()
            }
        } catch (_: Exception) { emptyMap() }
    }

    private suspend fun fetchAccessControl(userId: String): Result<AccessControlRecord> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CONTROL/records" +
                            "?filter=userId%3D%27$userId%27&perPage=1")
                    .addHeader("Authorization", "Bearer ${getAuthToken()}")
                    .get().build()
                val res  = http.newCall(req).execute()
                val body = res.body?.string() ?: ""
                res.close()
                val json = JSONObject(body)
                if (json.optInt("totalItems", 0) == 0)
                    return@withContext Result.failure(Exception("No access control for $userId"))
                val item   = json.getJSONArray("items").getJSONObject(0)
                val record = AccessControlRecord(
                    userId               = item.optString("userId"),
                    name                 = item.optString("name"),
                    email                = item.optString("email"),
                    companyName          = item.optString("companyName"),
                    sanitizedCompanyName = item.optString("sanitizedCompanyName"),
                    department           = item.optString("department"),
                    sanitizedDepartment  = item.optString("sanitizedDepartment"),
                    role                 = item.optString("role"),
                    permissions          = item.optString("permissions", "[]"),
                    isActive             = item.optBoolean("isActive", true),
                    documentPath         = item.optString("documentPath")
                )
                Result.success(record.apply { recordId = item.optString("id") })
            } catch (e: Exception) { Result.failure(e) }
        }

    private fun httpPost(url: String, token: String = getAuthToken(), body: String) {
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        val res     = http.newCall(req).execute()
        val resBody = res.body?.string() ?: ""
        if (!res.isSuccessful) {
            Log.e(tag, "POST $url → HTTP ${res.code} ❌  body: $resBody")
            res.close(); error("POST failed: HTTP ${res.code} — $resBody")
        }
        res.close()
    }

    private fun httpPatch(url: String, token: String = getAuthToken(), body: String) {
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token")
            .patch(body.toRequestBody("application/json".toMediaType())).build()
        val res     = http.newCall(req).execute()
        val resBody = res.body?.string() ?: ""
        if (!res.isSuccessful) {
            Log.e(tag, "PATCH $url → HTTP ${res.code} ❌  body: $resBody")
            res.close(); error("PATCH failed: HTTP ${res.code} — $resBody")
        }
        res.close()
    }

    private fun getAuthToken(): String =
        try { pb.authStore.token ?: "" } catch (_: Exception) { "" }

    private fun deleteUserSilently(userId: String) {
        try {
            http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                .addHeader("Authorization", "Bearer ${getAdminToken()}").delete().build()
            ).execute().close()
        } catch (_: Exception) {}
    }

    private fun getAdminToken(): String {
        val endpoints = listOf(
            "${AppConfig.BASE_URL}/api/collections/_superusers/auth-with-password",
            "${AppConfig.BASE_URL}/api/admins/auth-with-password"
        )
        for (url in endpoints) {
            try {
                val body = JSONObject().apply {
                    put("identity", AppConfig.ADMIN_EMAIL)
                    put("password", AppConfig.ADMIN_PASS)
                }.toString().toRequestBody("application/json".toMediaType())
                val res     = http.newCall(Request.Builder().url(url).post(body).build()).execute()
                val resBody = res.body?.string() ?: ""
                val ok      = res.isSuccessful; res.close()
                if (ok) {
                    val t = JSONObject(resBody).optString("token")
                    if (t.isNotEmpty()) return t
                }
            } catch (e: Exception) {
                Log.w(tag, "Admin auth exception at $url: ${e.message}")
            }
        }
        error("No admin token")
    }

    private fun ensureCollection(token: String, name: String, schema: String) {
        val checkRes = http.newCall(Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections/$name")
            .addHeader("Authorization", "Bearer $token").get().build()).execute()
        if (checkRes.isSuccessful) { checkRes.close(); updateCollectionRules(token, name); return }
        checkRes.close()

        val createBody = JSONObject().apply {
            put("name", name); put("type", "base")
            put("schema",     JSONArray(schema))
            put("listRule",   JSONObject.NULL); put("viewRule",   JSONObject.NULL)
            put("createRule", JSONObject.NULL); put("updateRule", JSONObject.NULL)
            put("deleteRule", JSONObject.NULL)
        }.toString().toRequestBody("application/json".toMediaType())

        val createRes = http.newCall(Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections")
            .addHeader("Authorization", "Bearer $token").post(createBody).build()).execute()
        Log.d(tag, "Create '$name' → HTTP ${createRes.code}")
        createRes.close()
    }

    private fun updateCollectionRules(token: String, name: String) {
        try {
            val body = """{"listRule":null,"viewRule":null,"createRule":null,"updateRule":null,"deleteRule":null}"""
                .toRequestBody("application/json".toMediaType())
            http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$name")
                .addHeader("Authorization", "Bearer $token").patch(body).build()).execute().close()
        } catch (e: Exception) { Log.w(tag, "updateCollectionRules '$name': ${e.message}") }
    }

    private fun buildCompaniesSchema() = """[
        {"name":"originalName","type":"text","required":true},
        {"name":"sanitizedName","type":"text","required":true},
        {"name":"totalUsers","type":"number"},{"name":"activeUsers","type":"number"},
        {"name":"availableRoles","type":"json"},{"name":"departments","type":"json"}]"""

    private fun buildAccessControlSchema() = """[
        {"name":"userId","type":"text","required":true},
        {"name":"name","type":"text"},{"name":"email","type":"email"},
        {"name":"companyName","type":"text"},{"name":"sanitizedCompanyName","type":"text"},
        {"name":"department","type":"text"},{"name":"sanitizedDepartment","type":"text"},
        {"name":"role","type":"text"},{"name":"designation","type":"text"},
        {"name":"permissions","type":"json"},{"name":"isActive","type":"bool"},
        {"name":"documentPath","type":"text"},{"name":"needsProfileCompletion","type":"bool"}]"""

    private fun buildSearchIndexSchema() = """[
        {"name":"userId","type":"text","required":true},
        {"name":"name","type":"text"},{"name":"email","type":"email"},
        {"name":"companyName","type":"text"},{"name":"sanitizedCompanyName","type":"text"},
        {"name":"department","type":"text"},{"name":"sanitizedDepartment","type":"text"},
        {"name":"role","type":"text"},{"name":"designation","type":"text"},
        {"name":"isActive","type":"bool"},{"name":"searchTerms","type":"json"},
        {"name":"documentPath","type":"text"}]"""
}