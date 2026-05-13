package com.saini.ritik.auth

import android.util.Log
import com.saini.ritik.core.AdminTokenProvider
import com.saini.ritik.core.PermissionGuard
import com.saini.ritik.core.SyncManager
import com.saini.ritik.data.source.AppDataSource
import com.saini.ritik.pocketbase.PocketBaseDataSource
import com.saini.ritik.pocketbase.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val dataSource         : AppDataSource,
    private val pbDataSource       : PocketBaseDataSource,
    private val sessionManager     : SessionManager,
    private val syncManager        : SyncManager,
    private val adminTokenProvider : AdminTokenProvider
) {
    companion object { private const val TAG = "AuthRepository" }

    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val isLoggedIn: Boolean get() = sessionManager.isLoggedIn()

    /**
     * Returns true when the session role is System_Administrator.
     *
     * The role is assigned automatically by PocketBaseDataSource.login():
     *  1. Queries PocketBase _superusers collection (+ legacy /api/admins fallback)
     *     to check if this email is a PocketBase superuser.
     *  2. If yes → role is set to System_Administrator, persisted to Room cache
     *     AND back-filled in user_access_control + users records in PocketBase.
     *
     * No hardcoded email comparison anywhere.
     * Source of truth is always the database.
     */
    fun isDbAdmin(): Boolean =
        PermissionGuard.isSystemAdmin(sessionManager.get()?.role ?: "")

    /** Alias used in some screens. */
    fun isSystemAdmin(): Boolean = isDbAdmin()

    /**
     * Sanitized company name for the current user.
     * System_Administrator returns null (unrestricted — sees all companies).
     */
    fun userCompany(): String? {
        if (isDbAdmin()) return null
        return sessionManager.get()?.let { s ->
            com.saini.ritik.core.StringUtils.sanitize(
                s.documentPath.split("/").getOrNull(1) ?: ""
            ).ifBlank { null }
        }
    }

    suspend fun restoreSession() {
        val session = sessionManager.get()
        if (session == null) { Log.d(TAG, "restoreSession: no saved session"); return }
        try {
            dataSource.restoreSession(session.token)
            syncManager.setUserToken(session.token)
            // Resume admin-token keep-alive for the restored session.
            adminTokenProvider.startKeepAlive()
            Log.d(TAG, "Session restored  email=${session.email}  role=${session.role}")
        } catch (e: Exception) {
            Log.w(TAG, "Session restore failed (possibly offline): ${e.message}")
        }
        // For SA users who are not PB superusers, the admin token IS their user JWT.
// Re-seed it on session restore so it survives process death.
        val s = sessionManager.get()
        if (s != null && PermissionGuard.isSystemAdmin(s.role)) {
            adminTokenProvider.setTokenDirectly(s.token)
        }
    }

    suspend fun validateSession(): SessionStatus {
        val session = sessionManager.get() ?: return SessionStatus.NoSession
        return try {
            val tokenValid = try {
                pbDataSource.validateCurrentToken()
            } catch (e: Exception) {
                Log.w(TAG, "Token validation failed (network?): ${e.message}")
                return SessionStatus.Valid(session)
            }
            if (!tokenValid) {
                Log.w(TAG, "Token rejected — forcing logout")
                logout(); return SessionStatus.TokenInvalid
            }
            val isActive = try {
                pbDataSource.checkIsActive(session.userId)
            } catch (e: Exception) {
                Log.w(TAG, "isActive check failed (network?): ${e.message}"); true
            }
            if (!isActive) {
                Log.w(TAG, "Account deactivated — forcing logout")
                logout(); return SessionStatus.Deactivated
            }
            syncManager.setUserToken(session.token)
            SessionStatus.Valid(session)
        } catch (e: Exception) {
            Log.w(TAG, "validateSession unexpected error: ${e.message}")
            SessionStatus.Valid(session)
        }
    }

    /**
     * Fast login — role detection (including System_Administrator auto-assign)
     * is handled inside PocketBaseDataSource.login().
     * isActive re-check runs in background so the UI transition is never blocked.
     */
    suspend fun login(email: String, password: String): Result<Unit> =
        try {
            val session = dataSource.login(email, password)
            sessionManager.save(session)
            syncManager.setUserToken(session.token)
            // Start proactive admin-token keep-alive for the duration of this session.
            // The loop refreshes the token every 9 min so it never expires while logged in.
            adminTokenProvider.startKeepAlive()
            Log.d(TAG, "Login ✅  email=$email  role=${session.role}  sysAdmin=${isDbAdmin()}")

            bgScope.launch {
                try {
                    if (!pbDataSource.checkIsActive(session.userId))
                        Log.w(TAG, "Account deactivated — will be caught on next validateSession()")
                } catch (e: Exception) {
                    Log.w(TAG, "Background isActive: ${e.message}")
                }
            }

            // Sync role permission templates in background — does NOT block login
            val sanitizedCompany = session.documentPath.split("/").getOrNull(1) ?: ""
            if (sanitizedCompany.isNotBlank()) {
                bgScope.launch {
                    try { syncManager.syncRoleDefinitions(sanitizedCompany) }
                    catch (e: Exception) { Log.w(TAG, "Background syncRoleDefinitions: ${e.message}") }
                }
            }

            // System_Administrator: sync all users' permissions from user_access_control
            // so every user's local Room cache has up-to-date Map<String,Boolean> data.
            // Note: the superuser token was seeded into AdminTokenProvider during login,
            // so syncAllUserPermissions() will use getAdminToken() to retrieve it.
            if (PermissionGuard.isSystemAdmin(session.role)) {
                bgScope.launch {
                    try { syncManager.syncAllUserPermissions() }
                    catch (e: Exception) {
                        Log.w(TAG, "Background syncAllUserPermissions: ${e.message}")
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Login failed: ${e.message}")
            Result.failure(e)
        }

    suspend fun sendLoginOtp(email: String): Result<Unit> =
        dataSource.sendLoginOtp(email)

    suspend fun loginWithOtp(email: String, otp: String): Result<Unit> =
        try {
            val session = dataSource.loginWithOtp(email, otp).getOrThrow()
            sessionManager.save(session)
            syncManager.setUserToken(session.token)
            adminTokenProvider.startKeepAlive()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun sendPasswordReset(email: String): Result<Unit> =
        dataSource.sendPasswordReset(email)

    // AuthRepository.kt

    suspend fun sendOtp(email: String): Result<Unit> =
        dataSource.sendOtp(email)

    suspend fun verifyOtp(email: String, otp: String): Result<Unit> =
        dataSource.verifyOtp(email, otp)

    suspend fun verifyOtpAndResetPassword(email: String, otp: String, newPassword: String): Result<Unit> =
        dataSource.verifyOtpAndResetPassword(email, otp, newPassword)

    suspend fun logout() {
        try { dataSource.logout() } catch (_: Exception) {}
        adminTokenProvider.stopKeepAlive()   // stop background refresh, clear cached token
        sessionManager.clear()
        syncManager.setUserToken("")
        Log.d(TAG, "Logged out")
    }

    fun getSession() = sessionManager.get()
}

sealed class SessionStatus {
    object NoSession    : SessionStatus()
    object TokenInvalid : SessionStatus()
    object Deactivated  : SessionStatus()
    data class Valid(val session: com.saini.ritik.data.model.AuthSession) : SessionStatus()
}