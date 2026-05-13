package com.saini.ritik.pocketbase

import android.util.Log
import com.saini.ritik.core.AppConfig
import com.saini.ritik.core.parseJsonList
import com.saini.ritik.data.model.*
import com.saini.ritik.data.source.AppDataSource
import com.saini.ritik.data.source.dto.*
import com.saini.ritik.localdatabase.AppDatabase
import com.saini.ritik.localdatabase.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections.emptyList
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

internal const val COL_USERS          = "users"
internal const val COL_COMPANIES      = "companies_metadata"
internal const val COL_ACCESS_CONTROL = "user_access_control"
internal const val COL_SEARCH_INDEX   = "user_search_index"

private val ADMIN_ROLES                   = setOf("Administrator")
private val MANAGER_ROLES                 = setOf("Manager", "HR")
private val MANAGER_EDITABLE_TARGET_ROLES = setOf("Employee", "Intern", "Team Lead")

@Singleton
class PocketBaseDataSource @Inject constructor(
    private val http: OkHttpClient,
    private val db  : AppDatabase,
    private val adminTokenProvider: com.saini.ritik.core.AdminTokenProvider
) : AppDataSource {

    private val tag = "PBDataSource"

    private val authTokenRef        = AtomicReference("")
    private val loginOtpIdStore = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val otpStore = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
    private val OTP_VALID_MS = 10 * 60 * 1000L
    private val verifiedTokenStore = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()


    // Background scope for fire-and-forget work that must not block login
    // (e.g. role back-fill after SA auto-detection).
    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

            // ── Steps 1+3 in parallel ─────────────────────────────────────────
            // fetchAccessControl (user_access_control lookup) and the PB
            // superuser probe are independent HTTPs — running them concurrently
            // shaves one full round-trip off the login critical path.
            val (access, isSuperuser) = coroutineScope {
                val accessDeferred     = async(Dispatchers.IO) { fetchAccessControl(uid).getOrNull() }
                val superuserDeferred  = async(Dispatchers.IO) { checkIsPocketBaseSuperuser(email, password) }
                accessDeferred.await() to superuserDeferred.await()
            }

            // ── Step 2: Determine role ────────────────────────────────────────
            // Priority order:
            //  a) user_access_control.role   (most authoritative — set by admins)
            //  b) users record role field
            var role = when {
                !access?.role.isNullOrBlank()         -> access!!.role
                record.optString("role").isNotBlank() -> record.optString("role")
                else                                  -> ""
            }

            // ── Step 3 (post-process): Auto-detect System_Administrator ──────
            // adminTokenProvider was already seeded inside checkIsPocketBaseSuperuser()
            // when isSuperuser == true.
            if (isSuperuser) {
                if (role != Permissions.ROLE_SYSTEM_ADMIN) {
                    role = Permissions.ROLE_SYSTEM_ADMIN
                    Log.d(tag, "Auto-assigned System_Administrator role to $email (PB superuser)")
                    // Back-fill is fire-and-forget — the user has already been authenticated,
                    // so we MUST NOT block the UI transition on additional admin-token
                    // round-trips and PocketBase PATCH calls.
                    bgScope.launch {
                        try { backfillSystemAdminRole(uid, isSuperuser = true) }
                        catch (e: Exception) { Log.w(tag, "bg backfillSystemAdminRole: ${e.message}") }
                    }
                }
                // adminTokenProvider was seeded inside checkIsPocketBaseSuperuser()
            } else if (role == Permissions.ROLE_SYSTEM_ADMIN) {
                // Not a PB superuser but has SA role — seed their regular JWT as the
                // admin token so RoleManagement / SyncManager admin operations work.
                adminTokenProvider.seedSaUserToken(
                    userToken = authTokenRef.get(),
                    email     = email,
                    password  = password
                )
                Log.d(tag, "SA role user (non-superuser) — JWT seeded as admin token: $email")
            }

            val sessionPermissions: List<String> = when {
                role == Permissions.ROLE_SYSTEM_ADMIN     -> Permissions.ALL_PERMISSIONS
                !access?.permissions.isNullOrBlank()      -> parseJsonList(access!!.permissions)
                else                                      -> emptyList<String>()
            }

            val session = AuthSession(
                userId       = uid,
                token        = token,
                email        = email,
                name         = access?.name         ?: record.optString("name"),
                role         = role,
                documentPath = access?.documentPath ?: record.optString("documentPath"),
                permissions  = sessionPermissions
            )

            Log.d(tag, "Login ✅  uid=$uid  role=$role")
            // Off-load Room write — login coroutine must not block on disk I/O.
            bgScope.launch {
                try { cacheUserLocally(uid, record, access, overrideRole = role) }
                catch (e: Exception) { Log.w(tag, "bg cacheUserLocally: ${e.message}") }
            }
            session
        }

    override suspend fun sendLoginOtp(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply { put("email", email) }
                    .toString().toRequestBody("application/json".toMediaType())
                val res     = http.newCall(
                    Request.Builder()
                        .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/request-otp")
                        .post(body).build()
                ).execute()
                val resBody = res.body?.string() ?: ""
                val code    = res.code; res.close()

                when (code) {
                    400  -> return@withContext Result.failure(Exception("No account found for this email."))
                    404  -> return@withContext Result.failure(Exception("OTP not enabled. Contact admin."))
                    !in 200..299 -> return@withContext Result.failure(Exception("Server error ($code)."))
                }
                val otpId = JSONObject(resBody).optString("otpId")
                    .ifBlank { return@withContext Result.failure(Exception("Invalid server response.")) }

                loginOtpIdStore[email.lowercase().trim()] = otpId
                Log.d(tag, "Login OTP sent for $email, otpId=$otpId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(tag, "sendLoginOtp failed: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun loginWithOtp(email: String, otp: String): Result<AuthSession> =
        withContext(Dispatchers.IO) {
            try {
                val key   = email.lowercase().trim()
                val otpId = loginOtpIdStore[key]
                    ?: return@withContext Result.failure(Exception("OTP session expired. Please request a new one."))

                val authBody = JSONObject().apply {
                    put("otpId",    otpId)
                    put("password", otp)
                }.toString().toRequestBody("application/json".toMediaType())

                val res     = http.newCall(
                    Request.Builder()
                        .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/auth-with-otp")
                        .post(authBody).build()
                ).execute()
                val resBody = res.body?.string() ?: ""
                val code    = res.code; res.close()

                if (code == 400 || code == 401)
                    return@withContext Result.failure(Exception("Incorrect OTP. Please try again."))
                if (code !in 200..299)
                    return@withContext Result.failure(Exception("Login failed ($code)."))

                val json      = JSONObject(resBody)
                val token     = json.optString("token")
                    .ifBlank { return@withContext Result.failure(Exception("No token received.")) }
                val record    = json.optJSONObject("record")
                    ?: return@withContext Result.failure(Exception("No user record."))
                val uid       = record.optString("id")
                    .ifBlank { return@withContext Result.failure(Exception("No user ID.")) }

                authTokenRef.set(token)
                loginOtpIdStore.remove(key)

                // Reuse existing access control fetch to get role/permissions
                val access = fetchAccessControl(uid).getOrNull()
                val role   = access?.role?.ifBlank { record.optString("role") } ?: record.optString("role")
                val perms  = if (!access?.permissions.isNullOrBlank())
                    com.saini.ritik.core.parseJsonList(access!!.permissions)
                else emptyList()

                val session = AuthSession(
                    userId       = uid,
                    token        = token,
                    email        = email,
                    name         = access?.name ?: record.optString("name"),
                    role         = role,
                    documentPath = access?.documentPath ?: record.optString("documentPath"),
                    permissions  = perms
                )
                bgScope.launch {
                    try { cacheUserLocally(uid, record, access) }
                    catch (e: Exception) { Log.w(tag, "bg cache after OTP login: ${e.message}") }
                }
                Log.d(tag, "OTP Login ✅ uid=$uid role=$role")
                Result.success(session)
            } catch (e: Exception) {
                Log.e(tag, "loginWithOtp failed: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun logout() = withContext(Dispatchers.IO) { authTokenRef.set("") }

    // In PocketBaseDataSource.kt

    // Stores otpId returned by PocketBase per email
    private val otpIdStore = java.util.concurrent.ConcurrentHashMap<String, String>()

    override suspend fun sendOtp(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Fully public endpoint — no token needed
                // PocketBase generates OTP, emails it to the user, returns an otpId
                val body = JSONObject().apply { put("email", email) }
                    .toString().toRequestBody("application/json".toMediaType())

                val res = http.newCall(
                    Request.Builder()
                        .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/request-otp")
                        .post(body).build()
                ).execute()
                val resBody = res.body?.string() ?: ""
                val code    = res.code
                res.close()

                when (code) {
                    400  -> return@withContext Result.failure(Exception("No account found for this email."))
                    404  -> return@withContext Result.failure(Exception("OTP not enabled. Contact admin."))
                    !in 200..299 -> return@withContext Result.failure(Exception("Server error ($code)."))
                }

                // Save the otpId — needed to verify later
                val otpId = JSONObject(resBody).optString("otpId")
                    .ifBlank { return@withContext Result.failure(Exception("Invalid server response.")) }

                otpIdStore[email.lowercase().trim()] = otpId
                Log.d(tag, "OTP requested for $email, otpId=$otpId")
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(tag, "sendOtp failed: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun verifyOtpAndResetPassword(
        email: String, otp: String, newPassword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val key   = email.lowercase().trim()

            // Use the token from Step 2 (verifyOtp already called auth-with-otp)
            val (userToken, userId) = verifiedTokenStore[key]
                ?: return@withContext Result.failure(Exception("OTP session expired. Please go back and verify again."))

            val patchBody = JSONObject().apply {
                put("password",        newPassword)
                put("passwordConfirm", newPassword)
            }.toString().toRequestBody("application/json".toMediaType())

            val res  = http.newCall(
                Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                    .addHeader("Authorization", "Bearer $userToken")
                    .patch(patchBody).build()
            ).execute()
            val code = res.code
            res.close()

            if (code !in 200..299)
                return@withContext Result.failure(Exception("Failed to update password ($code)."))

            // Clean up both stores
            otpIdStore.remove(key)
            verifiedTokenStore.remove(key)
            Log.d(tag, "Password reset ✅ for $email")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(tag, "verifyOtpAndResetPassword failed: ${e.message}")
            Result.failure(e)
        }
    }

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

    override suspend fun verifyOtp(email: String, otp: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val key   = email.lowercase().trim()
                val otpId = otpIdStore[key]
                    ?: return@withContext Result.failure(Exception("OTP session expired. Please request a new one."))

                val authBody = JSONObject().apply {
                    put("otpId",    otpId)
                    put("password", otp)
                }.toString().toRequestBody("application/json".toMediaType())

                val res     = http.newCall(
                    Request.Builder()
                        .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/auth-with-otp")
                        .post(authBody).build()
                ).execute()
                val resBody = res.body?.string() ?: ""
                val code    = res.code
                res.close()

                if (code == 400 || code == 401)
                    return@withContext Result.failure(Exception("Incorrect OTP. Please try again."))
                if (code !in 200..299)
                    return@withContext Result.failure(Exception("Verification failed ($code)."))

                // OTP valid — store the token+userId for Step 3
                val json      = JSONObject(resBody)
                val userToken = json.optString("token")
                    .ifBlank { return@withContext Result.failure(Exception("Auth failed — no token.")) }
                val userId    = json.optJSONObject("record")?.optString("id")
                    ?: return@withContext Result.failure(Exception("Auth failed — no user record."))

                verifiedTokenStore[key] = Pair(userToken, userId)
                Log.d(tag, "OTP verified ✅ for $email")
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(tag, "verifyOtp failed: ${e.message}")
                Result.failure(e)
            }
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
        val res   = http.newCall(Request.Builder()
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

    // ── Superuser detection ───────────────────────────────────────────────────

    /**
     * Verifies whether [email]/[password] belongs to a PocketBase superuser by
     * authenticating directly against the _superusers (PB ≥ 0.23) or legacy
     * /api/admins endpoint — no stored admin credentials required.
     *
     * On success the obtained SUPERUSER token is seeded into [adminTokenProvider],
     * enabling all admin operations for the rest of the session without separate
     * credential setup in Admin Settings.
     *
     * Fails silently for non-SA users — the failed HTTP request is the only
     * side effect (one extra network call per login).
     */
    private suspend fun checkIsPocketBaseSuperuser(email: String, password: String): Boolean =
        coroutineScope {
            val credentials = JSONObject().apply {
                put("identity", email)
                put("password", password)
            }.toString()

            val endpoints = listOf(
                "${AppConfig.BASE_URL}/api/collections/_superusers/auth-with-password",
                "${AppConfig.BASE_URL}/api/admins/auth-with-password"
            )

            // Fire both endpoints in parallel and take the first success.
            // Sequential probes used to burn 30s per endpoint when the first was
            // unreachable (~60s total worst case). Parallel collapses that to a
            // single timeout window (~10s with the lowered OkHttp timeout).
            val results = endpoints.map { url ->
                async(Dispatchers.IO) {
                    try {
                        val body    = credentials.toRequestBody("application/json".toMediaType())
                        val res     = http.newCall(Request.Builder().url(url).post(body).build()).execute()
                        val resBody = res.body?.string() ?: ""
                        val ok      = res.isSuccessful; res.close()
                        if (ok) {
                            val superToken = JSONObject(resBody).optString("token")
                            if (superToken.isNotBlank()) {
                                adminTokenProvider.setTokenDirectly(superToken)
                                Log.d(tag, "SA superuser token seeded from login — endpoint: $url")
                            }
                            true
                        } else false
                    } catch (e: Exception) {
                        Log.w(tag, "checkIsPocketBaseSuperuser ($url): ${e.message}")
                        false
                    }
                }
            }
            results.awaitAll().any { it }
        }

    /**
     * Called after auto-detecting a System_Administrator at login.
     * Updates the users record and user_access_control so the role is
     * persisted for offline use and visible to other queries.
     *
     * Runs best-effort — failures are logged and swallowed.
     */
    private fun backfillSystemAdminRole(userId: String, isSuperuser: Boolean) {
        if (!isSuperuser) return
        try {
            val token      = getAdminToken()
            val roleJson   = JSONObject().apply {
                put("role", Permissions.ROLE_SYSTEM_ADMIN)
                put("permissions", Json.encodeToString(Permissions.ALL_PERMISSIONS))
            }.toString()

            // Update users record
            http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                .addHeader("Authorization", "Bearer $token")
                .patch(roleJson.toRequestBody("application/json".toMediaType()))
                .build()).execute().close()

            // Update user_access_control record
            val acRes = http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CONTROL/records" +
                        "?filter=(userId='$userId')&perPage=1")
                .addHeader("Authorization", "Bearer $token")
                .get().build()).execute()
            val acBody = acRes.body?.string() ?: "{}"; acRes.close()
            val acId   = JSONObject(acBody).optJSONArray("items")
                ?.optJSONObject(0)?.optString("id") ?: ""

            if (acId.isNotBlank()) {
                http.newCall(Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CONTROL/records/$acId")
                    .addHeader("Authorization", "Bearer $token")
                    .patch(roleJson.toRequestBody("application/json".toMediaType()))
                    .build()).execute().close()
            }
            Log.d(tag, "backfillSystemAdminRole ✅ userId=$userId")
        } catch (e: Exception) {
            Log.w(tag, "backfillSystemAdminRole: ${e.message}")
        }
    }

    // ── User Profile — OFFLINE FIRST ──────────────────────────────────────────

    override suspend fun getUserProfile(userId: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val token = try { getAdminToken() } catch (_: Exception) { getEffectiveToken() }
                val res   = http.newCall(Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                    .addHeader("Authorization", "Bearer $token").get().build()).execute()
                val resBody = res.body?.string() ?: ""
                val code    = res.code; res.close()

                if (code in 200..299) {
                    val user    = JSONObject(resBody)
                    val access  = fetchAccessControl(userId).getOrNull()
                    val profile = buildUserProfile(userId, user, access)
                    cacheUserLocally(userId, user, access)
                    Log.d(tag, "getUserProfile ✅ from network")
                    return@withContext Result.success(profile)
                }
                Log.w(tag, "getUserProfile HTTP $code — falling back to cache")
            } catch (e: Exception) {
                Log.w(tag, "getUserProfile network failed: ${e.message} — trying cache")
            }

            val cached = try { db.userDao().getById(userId) } catch (_: Exception) { null }
            if (cached != null) {
                Log.d(tag, "getUserProfile ✅ from Room cache (offline)")
                return@withContext Result.success(cachedEntityToProfile(cached))
            }

            Result.failure(Exception("Profile unavailable — server unreachable and no cached data"))
        }

    override suspend fun updateUserProfile(
        userId    : String,
        fields    : Map<String, Any>,
        userToken : String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Use the user's own token if provided (self-edit).
            // Fall back to admin token only when editing another user (admin action).
            val token = if (userToken.isNotBlank()) userToken else getAdminToken()

            val body = JSONObject(fields as Map<*, *>).toString()
            val res  = http.newCall(
                Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                    .addHeader("Authorization", "Bearer $token")
                    .patch(body.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            val code = res.code; res.close()

            if (code in 200..299) {
                val cached = try { db.userDao().getById(userId) } catch (_: Exception) { null }
                if (cached != null) {
                    val updated = applyFieldsToEntity(cached, fields)
                    db.userDao().upsert(updated)
                }
                if ("role" in fields || "permissions" in fields) {
                    // Role/permission sync always needs admin token
                    syncAccessControlRecord(userId, fields, getAdminToken())
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("updateUserProfile HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(tag, "updateUserProfile: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun uploadProfileImage(
        userId: String, bytes: ByteArray, filename: String, token: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val useToken = token.ifBlank { getAdminToken() }
            val body     = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("avatar", filename,
                    bytes.toRequestBody("image/png".toMediaType()))
                .build()
            val res      = http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                .addHeader("Authorization", "Bearer $useToken")
                .patch(body).build()).execute()
            val resBody  = res.body?.string() ?: ""; res.close()
            if (res.isSuccessful) {
                val storedFile = JSONObject(resBody).optString("avatar", "")
                val url        = AppConfig.avatarUrl(userId, storedFile) ?: ""
                // Update imageUrl in profile JSON
                if (url.isNotBlank()) {
                    val profileJson = JSONObject().apply { put("imageUrl", url) }.toString()
                    try {
                        http.newCall(Request.Builder()
                            .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/records/$userId")
                            .addHeader("Authorization", "Bearer $useToken")
                            .patch(JSONObject().apply { put("profile", profileJson) }
                                .toString().toRequestBody("application/json".toMediaType()))
                            .build()).execute().close()
                    } catch (_: Exception) {}
                }
                Result.success(url)
            } else {
                Result.failure(Exception("uploadProfileImage HTTP ${res.code}"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Registration ──────────────────────────────────────────────────────────

    override suspend fun registerUser(request: RegistrationRequest): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val adminToken = getAdminToken()
                val userId     = createUser(request.email, request.password, request.name, adminToken)

                val (loginCode, loginJson) = authWithPassword(request.email, request.password)
                if (loginCode in 200..299) authTokenRef.set(JSONObject(loginJson).optString("token"))

                var imageUrl = ""
                if (request.imageBytes != null) {
                    uploadProfileImage(userId, request.imageBytes, "profile_$userId.jpg", adminToken)
                        .onSuccess { imageUrl = it }
                }

                val sc           = com.saini.ritik.core.StringUtils.sanitize(request.companyName)
                val sd           = com.saini.ritik.core.StringUtils.sanitize(request.department)
                val documentPath = "users/$sc/$sd/${request.role}/$userId"
                val rolePerms    = run {
                    val roleEntity = db.roleDao().getById("${sc}_${request.role}")
                    when {
                        request.role == Permissions.ROLE_SYSTEM_ADMIN -> Permissions.ALL_PERMISSIONS
                        roleEntity != null && roleEntity.permissions.isNotEmpty() -> roleEntity.permissions
                        else -> listOf(Permissions.PERM_VIEW_PROFILE)
                    }
                }
                val permissions  = Json.encodeToString(rolePerms)

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
                        put("companyName", request.companyName)
                        put("sanitizedCompanyName", sc)
                        put("department", request.department)
                        put("sanitizedDepartment", sd)
                        put("designation", request.designation); put("isActive", true)
                        put("documentPath", documentPath); put("permissions", permissions)
                        put("profile", profileJson); put("workStats", workJson)
                        put("issues", issuesJson); put("needsProfileCompletion", true)
                    }.toString())

                httpPost("${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CONTROL/records",
                    adminToken, JSONObject().apply {
                        put("userId", userId); put("name", request.name); put("email", request.email)
                        put("companyName", request.companyName); put("sanitizedCompanyName", sc)
                        put("department", request.department); put("sanitizedDepartment", sd)
                        put("role", request.role); put("designation", request.designation)
                        put("permissions", permissions); put("isActive", true)
                        put("documentPath", documentPath); put("needsProfileCompletion", true)
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

                upsertCompany(sc, request.companyName, request.role, request.department, adminToken)

                val record = JSONObject().apply {
                    put("id", userId); put("email", request.email); put("name", request.name)
                    put("role", request.role); put("companyName", request.companyName)
                    put("sanitizedCompanyName", sc); put("department", request.department)
                    put("sanitizedDepartment", sd); put("designation", request.designation)
                    put("isActive", true); put("documentPath", documentPath)
                    put("needsProfileCompletion", true)
                }
                val accessRec = AccessControlRecord(
                    userId               = userId,
                    name                 = request.name,
                    email                = request.email,
                    companyName          = request.companyName,
                    sanitizedCompanyName = sc,
                    department           = request.department,
                    sanitizedDepartment  = sd,
                    role                 = request.role,
                    permissions          = permissions,
                    isActive             = true,
                    documentPath         = documentPath
                )
                cacheUserLocally(userId, record, accessRec)
                Result.success(userId)
            } catch (e: Exception) {
                Log.e(tag, "registerUser: ${e.message}", e)
                if (e.message?.contains("COMPANY_EXISTS:") == true) Result.failure(e)
                else Result.failure(e)
            }
        }

    override suspend fun companyExists(sanitizedName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val token = getAdminToken()
                val res   = http.newCall(Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records" +
                            "?filter=(sanitizedName='$sanitizedName')&perPage=1")
                    .addHeader("Authorization", "Bearer $token").get().build()).execute()
                val body  = res.body?.string() ?: ""; res.close()
                JSONObject(body).optInt("totalItems", 0) > 0
            } catch (_: Exception) { false }
        }

    override suspend fun getOrCreateCompany(
        sanitizedName: String, originalName: String,
        role: String, department: String, adminToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = adminToken.ifBlank { getAdminToken() }
            upsertCompany(sanitizedName, originalName, role, department, token)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun ensureCollectionsExist(): Result<Unit> = withContext(Dispatchers.IO) {
        try { PocketBaseInitializer.initialize(); Result.success(Unit) }
        catch (e: Exception) { Result.failure(e) }
    }

    // ── Company upsert ────────────────────────────────────────────────────────

    private fun upsertCompany(
        sc: String, companyName: String,
        role: String, department: String, adminToken: String
    ) {
        try {
            val token  = adminToken.ifBlank { getAdminToken() }
            val getRes = http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records" +
                        "?filter=(sanitizedName='$sc')&perPage=1")
                .addHeader("Authorization", "Bearer $token").get().build()).execute()
            val getBody = getRes.body?.string() ?: "{}"; getRes.close()
            val item    = JSONObject(getBody).optJSONArray("items")?.optJSONObject(0)
            val cId     = item?.optString("id") ?: ""

            val payload = JSONObject().apply {
                put("sanitizedName", sc); put("originalName", companyName)
                put("totalUsers",    (item?.optInt("totalUsers",  0) ?: 0) + 1)
                put("activeUsers",   (item?.optInt("activeUsers", 0) ?: 0) + 1)
                put("availableRoles", JSONArray().apply {
                    val existing = item?.optJSONArray("availableRoles") ?: JSONArray()
                    for (i in 0 until existing.length()) put(existing.optString(i))
                    if (!toString().contains(role)) put(role)
                })
                put("departments", JSONArray().apply {
                    val existing = item?.optJSONArray("departments") ?: JSONArray()
                    for (i in 0 until existing.length()) put(existing.optString(i))
                    if (!toString().contains(department)) put(department)
                })
            }.toString()

            if (cId.isBlank())
                httpPost("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records",
                    token, payload)
            else
                httpPatch("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records/$cId",
                    token, payload)
        } catch (e: Exception) {
            Log.w(tag, "upsertCompany: ${e.message}")
        }
    }

    // ── Access control ────────────────────────────────────────────────────────

    suspend fun findSimilarCompanies(sanitizedName: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val token = getAdminToken()
                val res   = http.newCall(Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPANIES/records?perPage=200")
                    .addHeader("Authorization", "Bearer $token").get().build()).execute()
                val body  = res.body?.string() ?: ""; res.close()
                val items = JSONObject(body).optJSONArray("items") ?: return@withContext emptyList()
                (0 until items.length()).mapNotNull { i ->
                    val it = items.getJSONObject(i)
                    val sn = it.optString("sanitizedName")
                    if (sn == sanitizedName ||
                        sn.replace("_","").equals(sanitizedName.replace("_",""), ignoreCase = true))
                        it.optString("originalName").ifBlank { null }
                    else null
                }
            } catch (_: Exception) { emptyList() }
        }

    private fun syncAccessControlRecord(userId: String, fields: Map<String, Any>, token: String) {
        try {
            val getRes = http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CONTROL/records" +
                        "?filter=(userId='$userId')&perPage=1")
                .addHeader("Authorization", "Bearer $token").get().build()).execute()
            val getBody = getRes.body?.string() ?: "{}"; getRes.close()
            val acId    = JSONObject(getBody).optJSONArray("items")?.optJSONObject(0)
                ?.optString("id") ?: return

            val patch = JSONObject()
            if ("role"        in fields) patch.put("role",        fields["role"])
            if ("permissions" in fields) patch.put("permissions", fields["permissions"])
            if ("department"  in fields) patch.put("department",  fields["department"])
            if ("designation" in fields) patch.put("designation", fields["designation"])

            http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CONTROL/records/$acId")
                .addHeader("Authorization", "Bearer $token")
                .patch(patch.toString().toRequestBody("application/json".toMediaType()))
                .build()).execute().close()
        } catch (e: Exception) {
            Log.w(tag, "syncAccessControlRecord: ${e.message}")
        }
    }

    // ── Profile building ──────────────────────────────────────────────────────

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

    private fun cacheUserLocally(
        userId      : String,
        record      : JSONObject,
        access      : AccessControlRecord?,
        overrideRole: String? = null           // ← pass resolved role from login()
    ) {
        try {
            val profile   = parseJsonFieldSafe(record, "profile")
            val workStats = parseJsonFieldSafe(record, "workStats")
            val issues    = parseJsonFieldSafe(record, "issues")
            val finalRole = overrideRole
                ?: access?.role?.ifBlank { record.optString("role") }
                ?: record.optString("role")
            val entity = UserEntity(
                id                       = userId,
                name                     = record.optString("name").ifBlank { access?.name ?: "" },
                email                    = record.optString("email").ifBlank { access?.email ?: "" },
                role                     = finalRole,
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
            kotlinx.coroutines.runBlocking { db.userDao().upsert(entity) }
        } catch (e: Exception) {
            Log.w(tag, "cacheUserLocally failed: ${e.message}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getEffectiveToken(): String {
        val t = authTokenRef.get()
        if (t.isNotBlank()) return t
        return try { getAdminToken() } catch (e: Exception) {
            Log.e(tag, "getEffectiveToken: admin fallback failed: ${e.message}"); ""
        }
    }

    @Synchronized
    fun getAdminToken(): String = adminTokenProvider.getAdminTokenSync()

    private fun applyFieldsToEntity(entity: UserEntity, fields: Map<String, Any>): UserEntity {
        var e = entity
        fields.forEach { (k, v) ->
            when (k) {
                "role"        -> e = e.copy(role        = v.toString())
                "designation" -> e = e.copy(designation = v.toString())
                "department"  -> e = e.copy(department  = v.toString())
                "companyName" -> e = e.copy(companyName = v.toString())
                "isActive"    -> e = e.copy(isActive    = v.toString().toBooleanStrictOrNull() ?: e.isActive)
                "needsProfileCompletion" -> e = e.copy(needsProfileCompletion =
                    v.toString().toBooleanStrictOrNull() ?: e.needsProfileCompletion)
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

    private fun authWithPassword(email: String, password: String): Pair<Int, String> {
        val body = JSONObject().apply { put("identity", email); put("password", password) }
            .toString().toRequestBody("application/json".toMediaType())
        val res  = http.newCall(Request.Builder()
            .url("${AppConfig.BASE_URL}/api/collections/$COL_USERS/auth-with-password")
            .post(body).build()).execute()
        val resBody = res.body?.string() ?: ""
        val code    = res.code; res.close()
        return Pair(code, resBody)
    }

    private fun fetchAccessControl(userId: String): Result<AccessControlRecord> {
        if (userId.isBlank())
            return Result.failure(IllegalArgumentException("fetchAccessControl: blank userId"))
        return try {
            val token = try { getAdminToken() } catch (_: Exception) { getEffectiveToken() }
            val res   = http.newCall(Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CONTROL/records" +
                        "?filter=userId%3D%27$userId%27&perPage=1")
                .addHeader("Authorization", "Bearer $token").get().build()).execute()
            val body  = res.body?.string() ?: ""
            val code  = res.code; res.close()
            if (code !in 200..299)
                return Result.failure(Exception("fetchAccessControl HTTP $code"))
            val json = JSONObject(body)
            if (json.optInt("totalItems", 0) == 0)
                return Result.failure(Exception("No access control for $userId"))
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
}