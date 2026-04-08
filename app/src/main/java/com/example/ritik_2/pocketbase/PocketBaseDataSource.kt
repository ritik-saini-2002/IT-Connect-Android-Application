package com.example.ritik_2.pocketbase

import android.util.Log
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.parseJsonList
import com.example.ritik_2.data.model.*
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.data.source.dto.*
import com.example.ritik_2.localdatabase.AppDatabase
import com.example.ritik_2.localdatabase.UserEntity
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
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

internal const val COL_USERS          = "users"
internal const val COL_COMPANIES      = "companies_metadata"
internal const val COL_ACCESS_CONTROL = "user_access_control"
internal const val COL_SEARCH_INDEX   = "user_search_index"

// ─────────────────────────────────────────────────────────────────────────────
// Permission guard constants
// ─────────────────────────────────────────────────────────────────────────────
private val ADMIN_ROLES   = setOf("Administrator")
private val MANAGER_ROLES = setOf("Manager", "HR")
// Roles that managers/HR are allowed to edit
private val MANAGER_EDITABLE_TARGET_ROLES = setOf("Employee", "Intern", "Team Lead")

@Singleton
class PocketBaseDataSource @Inject constructor(
    private val http: OkHttpClient,
    private val db  : AppDatabase          // ← injected for offline fallback
) : AppDataSource {

    private val tag = "PBDataSource"

    private val authTokenRef        = AtomicReference("")
    private var cachedAdminToken    = ""
    private var adminTokenFetchedAt = 0L
    private val adminTokenTtlMs     = 10 * 60 * 1000L

    // ── Auth ──────────────────────────────────────────────────────────────────

    override suspend fun login(email: String, password: String): AuthSession =
        withContext(Dispatchers.IO) {
            val (code, resBody) = authWithPassword(email, password)
            Log.d(tag, "login HTTP $code")
            if (code !in 200..299) {
                val msg = try { JSONObject(resBody).optString("message", "Login failed") }
                catch (_: Exception) { "Login failed: HTTP $code" }
                error(msg)
            }
            val json   = JSONObject(resBody)
            val token  = json.optString("token").ifEmpty { error("No token received") }
            val record = json.optJSONObject("record") ?: error("No user record")
            val uid    = record.optString("id").ifEmpty { error("No user ID") }
            authTokenRef.set(token)

            val isActive = record.optBoolean("isActive", true)
            if (!isActive) { authTokenRef.set(""); error("Account disabled.") }

            val access = fetchAccessControl(uid).getOrNull()
            val role   = when {
                !access?.role.isNullOrBlank()         -> access!!.role
                record.optString("role").isNotBlank() -> record.optString("role")
                else                                  -> ""
            }

            val session = AuthSession(
                userId       = uid,
                token        = token,
                email        = email,
                name         = access?.name         ?: record.optString("name"),
                role         = role,
                documentPath = access?.documentPath ?: record.optString("documentPath")
            )

            // ── Cache user locally for offline use ────────────────────────────
            cacheUserLocally(uid, record, access)

            session
        }

    override suspend fun logout() = withContext(Dispatchers.IO) { authTokenRef.set("") }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply { put("email", email) }
                    .toString().toRequestBody("application/json".toMediaType())
                http.newCall(Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/request-password-reset")
                    .post(body).build()).execute().close()
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }

    override suspend fun createUser(
        email: String, password: String, name: String, adminToken: String
    ): String = withContext(Dispatchers.IO) {
        val token = adminToken.ifEmpty { getAdminToken() }
        val body  = JSONObject().apply {
            put("email", email); put("password", password)
            put("passwordConfirm", password); put("name", name)
            put("emailVisibility", true)
        }.toString().toRequestBody("application/json".toMediaType())
        val res     = http.newCall(Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records")
            .addHeader("Authorization", "Bearer $token").post(body).build()).execute()
        val body2 = res.body?.string() ?: ""
        val code  = res.code; res.close()
        if (res.isSuccessful) {
            val userId = JSONObject(body2).optString("id")
            if (userId.isNotEmpty()) return@withContext userId
        }
        val msg = try {
            val j = JSONObject(body2); val d = j.optJSONObject("data")
            if (d != null && d.length() > 0)
                d.keys().asSequence().joinToString(", ") {
                    "$it: ${d.optJSONObject(it)?.optString("message") ?: "invalid"}"
                }.let { "Validation failed — $it" }
            else j.optString("message", "HTTP $code")
        } catch (_: Exception) { "HTTP $code" }
        error("createUser failed: $msg")
    }

    override suspend fun restoreSession(token: String): Unit = withContext(Dispatchers.IO) {
        if (token.isNotBlank()) {
            authTokenRef.set(token)
            Log.d(tag, "restoreSession ✅")
        }
    }

    // ── Active status check ───────────────────────────────────────────────────

    suspend fun checkIsActive(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getEffectiveToken()
            if (token.isBlank()) return@withContext false
            val res  = http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                .addHeader("Authorization", "Bearer $token").get().build()).execute()
            val body = res.body?.string() ?: ""
            val code = res.code; res.close()
            if (code == 401 || code == 403) return@withContext false
            if (!res.isSuccessful)          return@withContext true
            JSONObject(body).optBoolean("isActive", true)
        } catch (e: Exception) {
            Log.w(tag, "checkIsActive failed: ${e.message}")
            true
        }
    }

    suspend fun validateCurrentToken(): Boolean = withContext(Dispatchers.IO) {
        val token = authTokenRef.get()
        if (token.isBlank()) return@withContext false
        try {
            val res  = http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/auth-refresh")
                .addHeader("Authorization", "Bearer $token")
                .post("{}".toRequestBody("application/json".toMediaType())).build()).execute()
            val body = res.body?.string() ?: ""
            val code = res.code; res.close()
            Log.d(tag, "validateCurrentToken → HTTP $code")
            if (code in 200..299) {
                val newToken = JSONObject(body).optString("token")
                if (newToken.isNotBlank()) authTokenRef.set(newToken)
                true
            } else { authTokenRef.set(""); false }
        } catch (_: Exception) { true }
    }

    suspend fun ensureAdminToken() = withContext(Dispatchers.IO) { getAdminToken(); Unit }

    // ── User Profile — OFFLINE FIRST ──────────────────────────────────────────

    override suspend fun getUserProfile(userId: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            // 1. Try network first
            try {
                val token = try { getAdminToken() } catch (_: Exception) { getEffectiveToken() }
                val res   = http.newCall(Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                    .addHeader("Authorization", "Bearer $token").get().build()).execute()
                val resBody = res.body?.string() ?: ""
                val code    = res.code; res.close()

                if (code in 200..299) {
                    val user   = JSONObject(resBody)
                    val access = fetchAccessControl(userId).getOrNull()
                    val profile = buildUserProfile(userId, user, access)
                    // Cache locally
                    cacheUserLocally(userId, user, access)
                    Log.d(tag, "getUserProfile ✅ from network")
                    return@withContext Result.success(profile)
                }
                Log.w(tag, "getUserProfile HTTP $code — falling back to cache")
            } catch (e: Exception) {
                Log.w(tag, "getUserProfile network failed: ${e.message} — trying cache")
            }

            // 2. Fallback to Room cache (offline)
            val cached = try { db.userDao().getById(userId) } catch (_: Exception) { null }
            if (cached != null) {
                Log.d(tag, "getUserProfile ✅ from Room cache (offline)")
                return@withContext Result.success(cachedEntityToProfile(cached))
            }

            Result.failure(Exception("Profile unavailable — server unreachable and no cached data"))
        }

    // ── Permission-guarded profile update ─────────────────────────────────────

    override suspend fun updateUserProfile(
        userId: String, fields: Map<String, Any>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = try { getAdminToken() } catch (_: Exception) { getEffectiveToken() }

            // Fetch current record for merge
            val currentRes  = http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                .addHeader("Authorization", "Bearer $token").get().build()).execute()
            val currentBody = currentRes.body?.string() ?: "{}"
            currentRes.close()
            val current = try { JSONObject(currentBody) } catch (_: Exception) { JSONObject() }

            val patch = buildMergedPatch(current, fields)

            val res = http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                .addHeader("Authorization", "Bearer $token")
                .patch(patch.toString().toRequestBody("application/json".toMediaType()))
                .build()).execute()
            val resBody = res.body?.string() ?: ""
            val code    = res.code; res.close()

            if (!res.isSuccessful) {
                Log.e(tag, "updateUserProfile HTTP $code: $resBody")
                return@withContext Result.failure(Exception("Update failed HTTP $code"))
            }

            syncAccessControl(userId, fields, token)

            // Update local cache
            val updated = db.userDao().getById(userId)
            if (updated != null) {
                db.userDao().upsert(applyFieldsToEntity(updated, fields))
            }

            Log.d(tag, "updateUserProfile ✅ userId=$userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "updateUserProfile failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun uploadProfileImage(
        userId: String, bytes: ByteArray, filename: String, token: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resolvedToken = getAdminToken()
            // Determine media type from filename extension
            val mediaType = when {
                filename.endsWith(".png",  ignoreCase = true) -> "image/png"
                filename.endsWith(".webp", ignoreCase = true) -> "image/webp"
                else                                          -> "image/jpeg"
            }
            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("avatar", filename,
                    bytes.toRequestBody(mediaType.toMediaType())).build()
            val res     = http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                .addHeader("Authorization", "Bearer $resolvedToken")
                .patch(requestBody).build()).execute()
            val resBody = res.body?.string() ?: ""
            val resCode = res.code; res.close()
            if (!res.isSuccessful)
                return@withContext Result.failure(Exception("Upload failed: $resCode"))
            val storedFilename = try {
                JSONObject(resBody).optString("avatar", filename)
            } catch (_: Exception) { filename }
            val url = "${AppConfig.BASE_URL}/api/files/$COL_USERS/$userId/$storedFilename"
            // Update cache with new imageUrl
            val cached = db.userDao().getById(userId)
            if (cached != null) db.userDao().upsert(cached.copy(imageUrl = url))
            Result.success(url)
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
                val sc = com.example.ritik_2.core.StringUtils.sanitize(request.companyName)
                val sd = com.example.ritik_2.core.StringUtils.sanitize(request.department)

                // ── Company duplicate check ───────────────────────────────────
                if (companyExists(sc))
                    return@withContext Result.failure(
                        Exception("COMPANY_EXISTS:${request.companyName}"))

                val adminToken = getAdminToken()
                val userId     = createUser(request.email, request.password, request.name, adminToken)
                createdUserId  = userId

                val (loginCode, loginJson) = authWithPassword(request.email, request.password)
                if (loginCode in 200..299) authTokenRef.set(JSONObject(loginJson).optString("token"))

                val imageUrl = if (request.imageBytes != null)
                    uploadProfileImage(userId, request.imageBytes, "profile_$userId.jpg", adminToken)
                        .getOrDefault("") else ""

                val documentPath = "users/$sc/$sd/${request.role}/$userId"
                val permissions  = Json.encodeToString(Permissions.forRole(request.role))

                val profileJson = JSONObject().apply {
                    put("imageUrl", imageUrl); put("phoneNumber", request.phoneNumber)
                    put("address", ""); put("employeeId", ""); put("reportingTo", "")
                    put("salary", 0); put("emergencyContactName", "")
                    put("emergencyContactPhone", ""); put("emergencyContactRelation", "")
                }.toString()
                val workJson = JSONObject().apply {
                    put("experience", 0); put("completedProjects", 0)
                    put("activeProjects", 0); put("pendingTasks", 0)
                    put("completedTasks", 0); put("totalWorkingHours", 0)
                    put("avgPerformanceRating", 0.0)
                }.toString()
                val issuesJson = JSONObject().apply {
                    put("totalComplaints", 0); put("resolvedComplaints", 0)
                    put("pendingComplaints", 0)
                }.toString()

                httpPatch("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId",
                    adminToken, JSONObject().apply {
                        put("userId", userId); put("role", request.role)
                        put("companyName", request.companyName); put("sanitizedCompanyName", sc)
                        put("department", request.department); put("sanitizedDepartment", sd)
                        put("designation", request.designation); put("isActive", true)
                        put("documentPath", documentPath); put("permissions", permissions)
                        put("profile", profileJson); put("workStats", workJson)
                        put("issues", issuesJson); put("needsProfileCompletion", true)
                    }.toString())

                upsertCompany(sc, request.companyName, request.role, request.department, adminToken)

                httpPost("${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CONTROL/records",
                    adminToken, JSONObject().apply {
                        put("userId", userId); put("name", request.name)
                        put("email", request.email); put("companyName", request.companyName)
                        put("sanitizedCompanyName", sc); put("department", request.department)
                        put("sanitizedDepartment", sd); put("role", request.role)
                        put("designation", request.designation); put("permissions", permissions)
                        put("isActive", true); put("documentPath", documentPath)
                        put("needsProfileCompletion", true)
                    }.toString())

                val searchTerms = Json.encodeToString(
                    listOf(request.name, request.email, request.companyName,
                        request.department, request.role, request.designation)
                        .map { it.lowercase() }.filter { it.isNotEmpty() })
                httpPost("${AppConfig.BASE_URL}/api/collections/$COL_SEARCH_INDEX/records",
                    adminToken, JSONObject().apply {
                        put("userId", userId); put("name", request.name.lowercase())
                        put("email", request.email.lowercase())
                        put("companyName", request.companyName); put("sanitizedCompanyName", sc)
                        put("department", request.department); put("sanitizedDepartment", sd)
                        put("role", request.role); put("designation", request.designation)
                        put("isActive", true); put("searchTerms", searchTerms)
                        put("documentPath", documentPath)
                    }.toString())

                // Cache locally after registration
                db.userDao().upsert(UserEntity(
                    id                   = userId,
                    name                 = request.name,
                    email                = request.email,
                    role                 = request.role,
                    companyName          = request.companyName,
                    sanitizedCompanyName = sc,
                    department           = request.department,
                    sanitizedDepartment  = sd,
                    designation          = request.designation,
                    imageUrl             = imageUrl,
                    isActive             = true,
                    documentPath         = documentPath,
                    needsProfileCompletion = true
                ))

                Log.d(tag, "registerUser ✅ userId=$userId")
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
                val res  = http.newCall(Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records" +
                            "?filter=sanitizedName%3D%27$sanitizedName%27&perPage=1")
                    .get().build()).execute()
                val body = res.body?.string() ?: ""; res.close()
                JSONObject(body).optInt("totalItems", 0) > 0
            } catch (_: Exception) { false }
        }

    /**
     * Returns a list of existing companies whose sanitized names are similar
     * to [sanitizedName]. Used for duplicate/merge warnings.
     */
    suspend fun findSimilarCompanies(sanitizedName: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val res  = http.newCall(Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records?perPage=200")
                    .get().build()).execute()
                val body = res.body?.string() ?: ""; res.close()
                val items = JSONObject(body).optJSONArray("items") ?: return@withContext emptyList()
                (0 until items.length())
                    .map { items.getJSONObject(it) }
                    .filter { obj ->
                        val sc = obj.optString("sanitizedName")
                        sc == sanitizedName ||
                                sc.replace("_","").equals(
                                    sanitizedName.replace("_",""), ignoreCase = true)
                    }
                    .map { it.optString("originalName") }
            } catch (_: Exception) { emptyList() }
        }

    private fun upsertCompany(sc: String, companyName: String, role: String,
                              department: String, adminToken: String) {
        val url    = "${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records"
        val getRes = http.newCall(Request.Builder().url("$url?filter=(sanitizedName='$sc')")
            .addHeader("Authorization", "Bearer $adminToken").get().build()).execute()
        val getBody   = getRes.body?.string() ?: ""; getRes.close()
        val companyId = JSONObject(getBody).optJSONArray("items")
            ?.optJSONObject(0)?.optString("id")
        val payload = JSONObject().apply {
            put("sanitizedName", sc); put("originalName", companyName)
            put("departments", JSONArray().put(department))
            put("availableRoles", JSONArray().put(role))
            put("totalUsers", 1); put("activeUsers", 1)
            put("lastUpdated", System.currentTimeMillis())
        }.toString()
        if (companyId.isNullOrEmpty()) httpPost(url, adminToken, payload)
        else httpPatch("$url/$companyId", adminToken, payload)
    }

    override suspend fun getOrCreateCompany(
        sanitizedName: String, originalName: String,
        role: String, department: String, adminToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = adminToken.ifEmpty { getAdminToken() }
            val res   = http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records" +
                        "?filter=sanitizedName%3D%27$sanitizedName%27&perPage=1")
                .addHeader("Authorization", "Bearer $token").get().build()).execute()
            val body = res.body?.string() ?: ""; res.close()
            val json = JSONObject(body)
            if (json.optInt("totalItems", 0) > 0) {
                val item      = json.getJSONArray("items").getJSONObject(0)
                val id        = item.optString("id")
                val rolesList = ArrayList<String>().also { list ->
                    val arr = JSONArray(item.optString("availableRoles", "[]"))
                    for (i in 0 until arr.length()) list.add(arr.getString(i))
                    if (role !in list) list.add(role)
                }
                val deptsList = ArrayList<String>().also { list ->
                    val arr = JSONArray(item.optString("departments", "[]"))
                    for (i in 0 until arr.length()) list.add(arr.getString(i))
                    if (department !in list) list.add(department)
                }
                httpPatch("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records/$id",
                    token, JSONObject().apply {
                        put("totalUsers",     item.optInt("totalUsers",  0) + 1)
                        put("activeUsers",    item.optInt("activeUsers", 0) + 1)
                        put("availableRoles", Json.encodeToString(rolesList))
                        put("departments",    Json.encodeToString(deptsList))
                    }.toString())
            } else {
                httpPost("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records",
                    token, JSONObject().apply {
                        put("originalName", originalName); put("sanitizedName", sanitizedName)
                        put("totalUsers", 1); put("activeUsers", 1)
                        put("availableRoles", Json.encodeToString(listOf(role)))
                        put("departments", Json.encodeToString(listOf(department)))
                    }.toString())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "getOrCreateCompany failed: ${e.message}"); Result.failure(e)
        }
    }

    override suspend fun ensureCollectionsExist(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAdminToken()
            listOf(
                COL_COMPANIES      to buildCompaniesSchema(),
                COL_ACCESS_CONTROL to buildAccessControlSchema(),
                COL_SEARCH_INDEX   to buildSearchIndexSchema()
            ).forEach { (name, schema) -> ensureCollection(token, name, schema) }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "ensureCollectionsExist failed: ${e.message}"); Result.failure(e)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildUserProfile(
        userId: String,
        user  : JSONObject,
        access: AccessControlRecord?
    ): UserProfile {
        val profile   = parseJsonFieldSafe(user, "profile")
        val workStats = parseJsonFieldSafe(user, "workStats")
        val issues    = parseJsonFieldSafe(user, "issues")
        return UserProfile(
            id                       = userId,
            name                     = user.optString("name").ifBlank { access?.name ?: "" },
            email                    = user.optString("email").ifBlank { access?.email ?: "" },
            role                     = if (access?.role.isNullOrBlank()) user.optString("role") else access!!.role,
            companyName              = user.optString("companyName").ifBlank { access?.companyName ?: "" },
            sanitizedCompany         = user.optString("sanitizedCompanyName"),
            department               = user.optString("department"),
            sanitizedDept            = user.optString("sanitizedDepartment"),
            designation              = user.optString("designation"),
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
            isActive                 = access?.isActive ?: user.optBoolean("isActive", true),
            documentPath             = user.optString("documentPath").ifBlank { access?.documentPath ?: "" },
            permissions              = parseJsonList(
                if (access?.permissions.isNullOrBlank()) user.optString("permissions", "[]")
                else access!!.permissions),
            emergencyContactName     = profile["emergencyContactName"]     ?: "",
            emergencyContactPhone    = profile["emergencyContactPhone"]    ?: "",
            emergencyContactRelation = profile["emergencyContactRelation"] ?: "",
            needsProfileCompletion   = user.optBoolean("needsProfileCompletion", true)
                    || user.optString("designation").isBlank()
        )
    }

    private fun cachedEntityToProfile(u: UserEntity) = UserProfile(
        id                       = u.id,
        name                     = u.name,
        email                    = u.email,
        role                     = u.role,
        companyName              = u.companyName,
        sanitizedCompany         = u.sanitizedCompanyName,
        department               = u.department,
        sanitizedDept            = u.sanitizedDepartment,
        designation              = u.designation,
        imageUrl                 = u.imageUrl,
        phoneNumber              = u.phoneNumber,
        address                  = u.address,
        employeeId               = u.employeeId,
        reportingTo              = u.reportingTo,
        salary                   = u.salary,
        experience               = u.experience,
        completedProjects        = u.completedProjects,
        activeProjects           = u.activeProjects,
        pendingTasks             = u.pendingTasks,
        completedTasks           = u.completedTasks,
        totalComplaints          = u.totalComplaints,
        resolvedComplaints       = u.resolvedComplaints,
        pendingComplaints        = u.pendingComplaints,
        isActive                 = u.isActive,
        documentPath             = u.documentPath,
        permissions              = u.permissions,
        emergencyContactName     = u.emergencyContactName,
        emergencyContactPhone    = u.emergencyContactPhone,
        emergencyContactRelation = u.emergencyContactRelation,
        needsProfileCompletion   = u.needsProfileCompletion
    )

    private fun cacheUserLocally(userId: String, record: JSONObject, access: AccessControlRecord?) {
        try {
            val profile   = parseJsonFieldSafe(record, "profile")
            val workStats = parseJsonFieldSafe(record, "workStats")
            val issues    = parseJsonFieldSafe(record, "issues")
            val entity = UserEntity(
                id                       = userId,
                name                     = record.optString("name").ifBlank { access?.name ?: "" },
                email                    = record.optString("email").ifBlank { access?.email ?: "" },
                role                     = access?.role?.ifBlank { record.optString("role") } ?: record.optString("role"),
                companyName              = record.optString("companyName"),
                sanitizedCompanyName     = record.optString("sanitizedCompanyName"),
                department               = record.optString("department"),
                sanitizedDepartment      = record.optString("sanitizedDepartment"),
                designation              = record.optString("designation"),
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
                isActive                 = access?.isActive ?: record.optBoolean("isActive", true),
                documentPath             = record.optString("documentPath").ifBlank { access?.documentPath ?: "" },
                permissions              = parseJsonList(
                    if (access?.permissions.isNullOrBlank()) record.optString("permissions", "[]")
                    else access!!.permissions),
                emergencyContactName     = profile["emergencyContactName"]     ?: "",
                emergencyContactPhone    = profile["emergencyContactPhone"]    ?: "",
                emergencyContactRelation = profile["emergencyContactRelation"] ?: "",
                needsProfileCompletion   = record.optBoolean("needsProfileCompletion", true)
            )
            // Must run in a coroutine context but we call this from withContext(IO) blocks
            kotlinx.coroutines.runBlocking { db.userDao().upsert(entity) }
        } catch (e: Exception) {
            Log.w(tag, "cacheUserLocally failed: ${e.message}")
        }
    }

    private fun applyFieldsToEntity(entity: UserEntity, fields: Map<String, Any>): UserEntity {
        var e = entity
        fields.forEach { (k, v) ->
            when (k) {
                "role"        -> e = e.copy(role        = v.toString())
                "designation" -> e = e.copy(designation = v.toString())
                "department"  -> e = e.copy(department  = v.toString())
                "companyName" -> e = e.copy(companyName = v.toString())
                "isActive"    -> e = e.copy(isActive    = v.toString().toBooleanStrictOrNull() ?: e.isActive)
                "profile" -> {
                    val pj = try { JSONObject(v.toString()) } catch (_: Exception) { return@forEach }
                    if (pj.has("imageUrl"))    e = e.copy(imageUrl    = pj.optString("imageUrl",    e.imageUrl))
                    if (pj.has("phoneNumber")) e = e.copy(phoneNumber = pj.optString("phoneNumber", e.phoneNumber))
                    if (pj.has("address"))     e = e.copy(address     = pj.optString("address",     e.address))
                    if (pj.has("salary"))      e = e.copy(salary      = pj.optDouble("salary",      e.salary))
                }
                "workStats" -> {
                    val wj = try { JSONObject(v.toString()) } catch (_: Exception) { return@forEach }
                    if (wj.has("experience")) e = e.copy(experience = wj.optInt("experience", e.experience))
                }
            }
        }
        return e
    }

    private fun buildMergedPatch(current: JSONObject, fields: Map<String, Any>): JSONObject {
        val patch = JSONObject()
        fields.forEach { (key, value) ->
            if (key in listOf("profile", "workStats", "issues")) {
                val incoming = try {
                    when (value) {
                        is String -> JSONObject(value)
                        else      -> JSONObject(value.toString())
                    }
                } catch (_: Exception) { JSONObject() }
                val existing = parseJsonFieldSafe(current, key)
                    .entries.fold(JSONObject()) { obj, (k, v) -> obj.put(k, v); obj }
                val mergedField = JSONObject()
                existing.keys().forEach { k -> mergedField.put(k, existing.get(k)) }
                incoming.keys().forEach { k -> incoming.opt(k)?.let { mergedField.put(k, it) } }
                patch.put(key, mergedField.toString())
            } else {
                when (value) {
                    is Boolean -> patch.put(key, value)
                    is Int     -> patch.put(key, value)
                    is Double  -> patch.put(key, value)
                    is Long    -> patch.put(key, value)
                    else       -> patch.put(key, value.toString())
                }
            }
        }
        return patch
    }

    private fun syncAccessControl(userId: String, fields: Map<String, Any>, token: String) {
        val syncFields = listOf("role", "designation", "isActive", "department",
            "sanitizedDepartment", "companyName", "sanitizedCompanyName", "documentPath")
        val relevantFields = fields.filterKeys { it in syncFields }
        if (relevantFields.isEmpty()) return
        try {
            val res = http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CONTROL/records" +
                        "?filter=(userId='$userId')&perPage=1")
                .addHeader("Authorization", "Bearer $token").get().build()).execute()
            val body = res.body?.string() ?: "{}"; res.close()
            val acId = JSONObject(body).optJSONArray("items")
                ?.optJSONObject(0)?.optString("id")
            if (acId.isNullOrEmpty()) return
            val patch = JSONObject()
            relevantFields.forEach { (k, v) ->
                when (v) {
                    is Boolean -> patch.put(k, v)
                    is Int     -> patch.put(k, v)
                    else       -> patch.put(k, v.toString())
                }
            }
            http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CONTROL/records/$acId")
                .addHeader("Authorization", "Bearer $token")
                .patch(patch.toString().toRequestBody("application/json".toMediaType()))
                .build()).execute().close()
            Log.d(tag, "syncAccessControl ✅ acId=$acId")
        } catch (e: Exception) {
            Log.w(tag, "syncAccessControl non-fatal: ${e.message}")
        }
    }

    private fun authWithPassword(email: String, password: String): Pair<Int, String> {
        listOf(
            "${AppConfig.BASE_URL}/api/collections/$COL_USERS/auth-with-password",
            "${AppConfig.BASE_URL}/api/collections/_pb_users_auth_/auth-with-password"
        ).forEach { url ->
            val body = JSONObject().apply { put("identity", email); put("password", password) }
                .toString().toRequestBody("application/json".toMediaType())
            val res     = http.newCall(Request.Builder().url(url).post(body).build()).execute()
            val resBody = res.body?.string() ?: ""
            val code    = res.code; res.close()
            Log.d(tag, "authWithPassword $url → HTTP $code")
            if (code != 404) return Pair(code, resBody)
        }
        return Pair(404, """{"message":"Missing or invalid auth collection context."}""")
    }

    private fun getEffectiveToken(): String {
        val t = authTokenRef.get()
        if (t.isNotBlank()) return t
        return try { getAdminToken() } catch (e: Exception) {
            Log.e(tag, "getEffectiveToken: admin fallback failed: ${e.message}"); ""
        }
    }

    @Synchronized
    private fun getAdminToken(): String {
        val now = System.currentTimeMillis()
        if (cachedAdminToken.isNotBlank() && (now - adminTokenFetchedAt) < adminTokenTtlMs)
            return cachedAdminToken
        listOf(
            "${AppConfig.BASE_URL}/api/collections/_superusers/auth-with-password",
            "${AppConfig.BASE_URL}/api/admins/auth-with-password"
        ).forEach { url ->
            try {
                val body = JSONObject().apply {
                    put("identity", AppConfig.ADMIN_EMAIL); put("password", AppConfig.ADMIN_PASS)
                }.toString().toRequestBody("application/json".toMediaType())
                val res     = http.newCall(Request.Builder().url(url).post(body).build()).execute()
                val resBody = res.body?.string() ?: ""
                val ok      = res.isSuccessful; res.close()
                if (ok) {
                    val t = JSONObject(resBody).optString("token")
                    if (t.isNotEmpty()) {
                        cachedAdminToken    = t
                        adminTokenFetchedAt = now
                        return t
                    }
                }
            } catch (e: Exception) { Log.w(tag, "Admin auth $url: ${e.message}") }
        }
        error("No admin token available")
    }

    private fun parseJsonFieldSafe(parent: JSONObject, key: String): Map<String, String> {
        return try {
            val raw = parent.opt(key) ?: return emptyMap()
            val obj: JSONObject = when (raw) {
                is JSONObject -> raw
                is String     -> if (raw.isBlank()) return emptyMap() else JSONObject(raw)
                else          -> return emptyMap()
            }
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { k -> map[k] = obj.opt(k)?.toString() ?: "" }
            map
        } catch (_: Exception) { emptyMap() }
    }

    private suspend fun fetchAccessControl(userId: String): Result<AccessControlRecord> =
        withContext(Dispatchers.IO) {
            try {
                val token = try { getAdminToken() } catch (_: Exception) { getEffectiveToken() }
                val res  = http.newCall(Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CONTROL/records" +
                            "?filter=userId%3D%27$userId%27&perPage=1")
                    .addHeader("Authorization", "Bearer $token").get().build()).execute()
                val body = res.body?.string() ?: ""
                val code = res.code; res.close()
                if (code !in 200..299)
                    return@withContext Result.failure(Exception("fetchAccessControl HTTP $code"))
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
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun httpPost(url: String, token: String = getEffectiveToken(), body: String) {
        val res     = http.newCall(Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body.toRequestBody("application/json".toMediaType())).build()).execute()
        val resBody = res.body?.string() ?: ""
        if (!res.isSuccessful) { res.close(); error("POST failed: HTTP ${res.code} — $resBody") }
        res.close()
    }

    private fun httpPatch(url: String, token: String = getEffectiveToken(), body: String) {
        val res     = http.newCall(Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token")
            .patch(body.toRequestBody("application/json".toMediaType())).build()).execute()
        val resBody = res.body?.string() ?: ""
        if (!res.isSuccessful) { res.close(); error("PATCH failed: HTTP ${res.code} — $resBody") }
        res.close()
    }

    private fun deleteUserSilently(userId: String) {
        try {
            http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                .addHeader("Authorization", "Bearer ${getAdminToken()}").delete().build()
            ).execute().close()
        } catch (_: Exception) {}
    }

    private fun ensureCollection(token: String, name: String, schema: String) {
        val checkRes = http.newCall(Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections/$name")
            .addHeader("Authorization", "Bearer $token").get().build()).execute()
        if (checkRes.isSuccessful) { checkRes.close(); updateCollectionRules(token, name); return }
        checkRes.close()
        val createBody = JSONObject().apply {
            put("name", name); put("type", "base"); put("schema", JSONArray(schema))
            put("listRule", JSONObject.NULL); put("viewRule", JSONObject.NULL)
            put("createRule", JSONObject.NULL); put("updateRule", JSONObject.NULL)
            put("deleteRule", JSONObject.NULL)
        }.toString().toRequestBody("application/json".toMediaType())
        val createRes = http.newCall(Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections")
            .addHeader("Authorization", "Bearer $token").post(createBody).build()).execute()
        Log.d(tag, "Create '$name' → HTTP ${createRes.code}"); createRes.close()
    }

    private fun updateCollectionRules(token: String, name: String) {
        try {
            http.newCall(Request.Builder().url("${AppConfig.BASE_URL}/api/collections/$name")
                .addHeader("Authorization", "Bearer $token")
                .patch("""{"listRule":null,"viewRule":null,"createRule":null,"updateRule":null,"deleteRule":null}"""
                    .toRequestBody("application/json".toMediaType())).build()).execute().close()
        } catch (e: Exception) { Log.w(tag, "updateCollectionRules '$name': ${e.message}") }
    }

    private fun buildCompaniesSchema()     = """[{"name":"originalName","type":"text","required":true},{"name":"sanitizedName","type":"text","required":true},{"name":"totalUsers","type":"number"},{"name":"activeUsers","type":"number"},{"name":"availableRoles","type":"json"},{"name":"departments","type":"json"}]"""
    private fun buildAccessControlSchema() = """[{"name":"userId","type":"text","required":true},{"name":"name","type":"text"},{"name":"email","type":"email"},{"name":"companyName","type":"text"},{"name":"sanitizedCompanyName","type":"text"},{"name":"department","type":"text"},{"name":"sanitizedDepartment","type":"text"},{"name":"role","type":"text"},{"name":"designation","type":"text"},{"name":"permissions","type":"json"},{"name":"isActive","type":"bool"},{"name":"documentPath","type":"text"},{"name":"needsProfileCompletion","type":"bool"}]"""
    private fun buildSearchIndexSchema()   = """[{"name":"userId","type":"text","required":true},{"name":"name","type":"text"},{"name":"email","type":"email"},{"name":"companyName","type":"text"},{"name":"sanitizedCompanyName","type":"text"},{"name":"department","type":"text"},{"name":"sanitizedDepartment","type":"text"},{"name":"role","type":"text"},{"name":"designation","type":"text"},{"name":"isActive","type":"bool"},{"name":"searchTerms","type":"json"},{"name":"documentPath","type":"text"}]"""
}