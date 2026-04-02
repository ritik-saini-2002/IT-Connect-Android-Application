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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal const val COL_USERS          = "users"
internal const val COL_COMPANIES      = "companies_metadata"
internal const val COL_ACCESS_CONTROL = "user_access_control"
internal const val COL_SEARCH_INDEX   = "user_search_index"

@Singleton
class PocketBaseDataSource @Inject constructor(
    private val clientProvider: PocketBaseClientProvider
) : AppDataSource {

    private val pb  get() = clientProvider.client
    private val TAG = "PBDataSource"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Auth ──────────────────────────────────────────────────
    override suspend fun login(email: String, password: String): AuthSession =
        withContext(Dispatchers.IO) {
            val response = pb.records.authWithPassword<UserRecord>(COL_USERS, email, password)
            val token    = response.token ?: error("No token received")
            val uid      = response.record?.id ?: error("No user ID received")
            pb.login { this.token = token }

            val access = fetchAccessControl(uid).getOrNull()
            AuthSession(
                userId       = uid,
                token        = token,
                email        = email,
                name         = access?.name         ?: "",
                role         = access?.role         ?: "",
                documentPath = access?.documentPath ?: ""
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

    override suspend fun createUser(email: String, password: String, name: String, adminToken: String): String =
        withContext(Dispatchers.IO) {
            val adminToken = adminToken.ifEmpty { getAdminToken() }
            val body = JSONObject().apply {
                put("email",           email)
                put("password",        password)
                put("passwordConfirm", password)
                put("name",            name)
                put("emailVisibility", true)
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records")
                .addHeader("Authorization", "Bearer $adminToken")
                .post(body)
                .build()

            val res     = http.newCall(req).execute()
            val resBody = res.body?.string() ?: error("Empty response")
            val resCode = res.code
            res.close()

            Log.d(TAG, "createUser HTTP $resCode: $resBody")
            if (!res.isSuccessful) error("createUser failed: HTTP $resCode — $resBody")

            val json = JSONObject(resBody)
            json.optString("id").ifEmpty { error("No user ID in response") }
        }

    override suspend fun restoreSession(token: String) = withContext(Dispatchers.IO) {
        pb.login { this.token = token }
    }

    // ── User Profile ──────────────────────────────────────────
    override suspend fun getUserProfile(userId: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val user   = pb.records.getOne<UserRecord>(COL_USERS, userId)
                val access = fetchAccessControl(userId).getOrNull()

                val profile   = parseJsonMap(user.profile)
                val workStats = parseJsonMap(user.workStats)
                val issues    = parseJsonMap(user.issues)

                Result.success(UserProfile(
                    id                       = userId,
                    name                     = user.name.ifBlank { access?.name ?: "" },
                    email                    = user.email.ifBlank { access?.email ?: "" },
                    role                     = access?.role         ?: user.role,
                    companyName              = user.companyName,
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
                    emergencyContactName     = profile["emergencyContactName"]     ?: "",
                    emergencyContactPhone    = profile["emergencyContactPhone"]    ?: "",
                    emergencyContactRelation = profile["emergencyContactRelation"] ?: "",
                    experience               = workStats["experience"]?.toIntOrNull()        ?: 0,
                    completedProjects        = workStats["completedProjects"]?.toIntOrNull() ?: 0,
                    activeProjects           = workStats["activeProjects"]?.toIntOrNull()    ?: 0,
                    pendingTasks             = workStats["pendingTasks"]?.toIntOrNull()      ?: 0,
                    completedTasks           = workStats["completedTasks"]?.toIntOrNull()    ?: 0,
                    totalComplaints          = issues["totalComplaints"]?.toIntOrNull()      ?: 0,
                    resolvedComplaints       = issues["resolvedComplaints"]?.toIntOrNull()   ?: 0,
                    pendingComplaints        = issues["pendingComplaints"]?.toIntOrNull()    ?: 0,
                    isActive                 = access?.isActive ?: user.isActive,
                    documentPath             = access?.documentPath ?: user.documentPath,
                    permissions              = parseJsonList(access?.permissions ?: user.permissions)
                ))
            } catch (e: Exception) {
                Log.e(TAG, "getUserProfile failed: ${e.message}")
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
                    .patch(body)
                    .build()
                val res = http.newCall(req).execute()
                if (!res.isSuccessful) {
                    val err = res.body?.string() ?: "Unknown error"
                    res.close()
                    return@withContext Result.failure(Exception("Update failed: $err"))
                }
                res.close()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "updateUserProfile failed: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun uploadProfileImage(
        userId: String, bytes: ByteArray, filename: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mediaType   = "image/jpeg".toMediaType()
            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("profileImage", filename,
                    okhttp3.RequestBody.create(mediaType, bytes))
                .build()
            val req = Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                .addHeader("Authorization", "Bearer ${getAuthToken()}")
                .patch(requestBody)
                .build()
            val res = http.newCall(req).execute()
            res.close()
            Result.success("${AppConfig.BASE_URL}/api/files/$COL_USERS/$userId/$filename")
        } catch (e: Exception) {
            Log.e(TAG, "uploadProfileImage failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Registration ──────────────────────────────────────────
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

                // Fetch admin token once — reuse for all privileged operations
                val adminToken = getAdminToken()

                val userId = createUser(request.email, request.password, request.name, adminToken)
                createdUserId = userId

                // Login as the new user to get their token
                val loginBody = JSONObject().apply {
                    put("identity", request.email)
                    put("password", request.password)
                }.toString().toRequestBody("application/json".toMediaType())
                val loginReq = Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/auth-with-password")
                    .post(loginBody).build()
                val loginRes  = http.newCall(loginReq).execute()
                val loginJson = JSONObject(loginRes.body?.string() ?: "")
                loginRes.close()
                val userToken = loginJson.optString("token")
                pb.login { this.token = userToken }

                val imageUrl = request.imageBytes?.let { bytes ->
                    uploadProfileImage(userId, bytes, "profile_$userId.jpg").getOrDefault("")
                } ?: ""

                val documentPath = "users/$sc/$sd/${request.role}/$userId"
                val permissions  = Json.encodeToString(Permissions.forRole(request.role))

                val profileJson = JSONObject().apply {
                    put("imageUrl",    imageUrl)
                    put("phoneNumber", request.phoneNumber)
                    put("address",     "")
                    put("employeeId",  "")
                    put("reportingTo", "")
                    put("salary",      0)
                }.toString()

                val workJson = JSONObject().apply {
                    put("experience",           request.experience)
                    put("completedProjects",    request.completedProjects)
                    put("activeProjects",       request.activeProjects)
                    put("pendingTasks",         0)
                    put("completedTasks",       0)
                    put("totalWorkingHours",    0)
                    put("avgPerformanceRating", 0.0)
                }.toString()

                val issuesJson = JSONObject().apply {
                    put("totalComplaints",    request.complaints)
                    put("resolvedComplaints", 0)
                    put("pendingComplaints",  request.complaints)
                }.toString()

                // Patch user record — use admin token so no permission issues
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
                    }.toString()
                )

                // Pass cached admin token — no extra network call
                getOrCreateCompany(sc, request.companyName, request.role, request.department, adminToken)

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
                        put("permissions",          permissions)
                        put("isActive",             true)
                        put("documentPath",         documentPath)
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

                Log.d(TAG, "User registered: $userId ✅")
                Result.success(userId)

            } catch (e: Exception) {
                createdUserId?.let { deleteUserSilently(it) }
                Log.e(TAG, "registerUser failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    // ── Company ───────────────────────────────────────────────
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
                val json = JSONObject(body)
                json.optInt("totalItems", 0) > 0
            } catch (_: Exception) { false }
        }

    override suspend fun getOrCreateCompany(
        sanitizedName: String,
        originalName : String,
        role         : String,
        department   : String,
        adminToken   : String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = adminToken.ifEmpty { getAdminToken() }

            val req = Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records" +
                        "?filter=sanitizedName%3D%27$sanitizedName%27&perPage=1")
                .addHeader("Authorization", "Bearer $token")
                .get().build()
            val res  = http.newCall(req).execute()
            val body = res.body?.string() ?: ""
            res.close()

            val json       = JSONObject(body)
            val totalItems = json.optInt("totalItems", 0)

            if (totalItems > 0) {
                val item  = json.getJSONArray("items").getJSONObject(0)
                val id    = item.optString("id")
                val roles = JSONArray(item.optString("availableRoles", "[]")).let { arr ->
                    val list = List(arr.length()) { arr.optString(it) }.toMutableList()
                    if (role !in list) list.add(role)
                    Json.encodeToString(list)
                }
                val depts = JSONArray(item.optString("departments", "[]")).let { arr ->
                    val list = List(arr.length()) { arr.optString(it) }.toMutableList()
                    if (department !in list) list.add(department)
                    Json.encodeToString(list)
                }
                httpPatch(
                    url   = "${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records/$id",
                    token = token,
                    body  = JSONObject().apply {
                        put("totalUsers",     item.optInt("totalUsers", 0) + 1)
                        put("activeUsers",    item.optInt("activeUsers", 0) + 1)
                        put("availableRoles", roles)
                        put("departments",    depts)
                    }.toString()
                )
            } else {
                httpPost(
                    url   = "${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records",
                    token = token,
                    body  = JSONObject().apply {
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
            Log.e(TAG, "getOrCreateCompany failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Collections Setup ─────────────────────────────────────
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
                Log.e(TAG, "ensureCollectionsExist failed: ${e.message}")
                Result.failure(e)
            }
        }

    // ── Private Helpers ───────────────────────────────────────
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
                if (json.optInt("totalItems", 0) == 0) {
                    return@withContext Result.failure(Exception("No access control for $userId"))
                }
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
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val res = http.newCall(req).execute()
        if (!res.isSuccessful) Log.w(TAG, "POST $url failed: ${res.code} ${res.body?.string()}")
        res.close()
    }

    private fun httpPatch(url: String, token: String = getAuthToken(), body: String) {
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .patch(body.toRequestBody("application/json".toMediaType()))
            .build()
        val res = http.newCall(req).execute()
        if (!res.isSuccessful) Log.w(TAG, "PATCH $url failed: ${res.code} ${res.body?.string()}")
        res.close()
    }

    private fun getAuthToken(): String =
        try { pb.authStore.token ?: "" } catch (_: Exception) { "" }

    private fun deleteUserSilently(userId: String) {
        try {
            val token = getAdminToken()
            val req = Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                .addHeader("Authorization", "Bearer $token")
                .delete().build()
            http.newCall(req).execute().close()
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
                val req     = Request.Builder().url(url).post(body).build()
                val res     = http.newCall(req).execute()
                val resBody = res.body?.string() ?: ""
                val resCode = res.code
                res.close()

                Log.d(TAG, "Admin auth $url → HTTP $resCode")
                Log.d(TAG, "Admin auth response: $resBody")

                if (res.isSuccessful) {
                    val token = JSONObject(resBody).optString("token")
                    if (token.isNotEmpty()) {
                        Log.d(TAG, "✅ Admin token obtained")
                        return token
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Admin auth exception at $url: ${e.message}")
            }
        }
        Log.e(TAG, "❌ No admin token obtained")
        Log.e(TAG, "   BASE_URL     : ${AppConfig.BASE_URL}")
        Log.e(TAG, "   ADMIN_EMAIL  : ${AppConfig.ADMIN_EMAIL}")
        Log.e(TAG, "   ADMIN_PASS   : ${AppConfig.ADMIN_PASS.take(3)}***")
        error("No admin token")
    }

    private fun ensureCollection(token: String, name: String, schema: String) {
        // Check existence
        val checkReq = Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections/$name")
            .addHeader("Authorization", "Bearer $token").get().build()
        val checkRes = http.newCall(checkReq).execute()
        if (checkRes.isSuccessful) {
            checkRes.close()
            // Ensure rules are open on already-existing collections
            updateCollectionRules(token, name)
            return
        }
        checkRes.close()

        // Create with open API rules
        val createBody = JSONObject().apply {
            put("name",       name)
            put("type",       "base")
            put("schema",     JSONArray(schema))
            put("listRule",   "")
            put("viewRule",   "")
            put("createRule", "")
            put("updateRule", "")
            put("deleteRule", JSONObject.NULL)
        }.toString().toRequestBody("application/json".toMediaType())

        val createReq = Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections")
            .addHeader("Authorization", "Bearer $token").post(createBody).build()
        val createRes = http.newCall(createReq).execute()
        val createResBody = createRes.body?.string() ?: ""
        Log.d(TAG, "Create '$name' → HTTP ${createRes.code}: $createResBody")
        createRes.close()
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

    private fun buildCompaniesSchema() = """[
        {"name":"originalName","type":"text","required":true},
        {"name":"sanitizedName","type":"text","required":true},
        {"name":"totalUsers","type":"number"},
        {"name":"activeUsers","type":"number"},
        {"name":"availableRoles","type":"json"},
        {"name":"departments","type":"json"}]"""

    private fun buildAccessControlSchema() = """[
        {"name":"userId","type":"text","required":true},
        {"name":"name","type":"text"},
        {"name":"email","type":"email"},
        {"name":"companyName","type":"text"},
        {"name":"sanitizedCompanyName","type":"text"},
        {"name":"department","type":"text"},
        {"name":"sanitizedDepartment","type":"text"},
        {"name":"role","type":"text"},
        {"name":"permissions","type":"json"},
        {"name":"isActive","type":"bool"},
        {"name":"documentPath","type":"text"}]"""

    private fun buildSearchIndexSchema() = """[
        {"name":"userId","type":"text","required":true},
        {"name":"name","type":"text"},
        {"name":"email","type":"email"},
        {"name":"companyName","type":"text"},
        {"name":"sanitizedCompanyName","type":"text"},
        {"name":"department","type":"text"},
        {"name":"sanitizedDepartment","type":"text"},
        {"name":"role","type":"text"},
        {"name":"designation","type":"text"},
        {"name":"isActive","type":"bool"},
        {"name":"searchTerms","type":"json"},
        {"name":"documentPath","type":"text"}]"""
}