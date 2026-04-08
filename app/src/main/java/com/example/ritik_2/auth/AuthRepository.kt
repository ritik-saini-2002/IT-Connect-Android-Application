package com.example.ritik_2.auth

import android.util.Log
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.SyncManager
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.pocketbase.PocketBaseDataSource
import com.example.ritik_2.pocketbase.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val dataSource    : AppDataSource,
    private val pbDataSource  : PocketBaseDataSource,
    private val sessionManager: SessionManager,
    private val syncManager   : SyncManager
) {
    companion object { private const val TAG = "AuthRepository" }

    val isLoggedIn: Boolean get() = sessionManager.isLoggedIn()

    /**
     * True when the currently logged-in user's email matches the PocketBase
     * admin credentials configured in local.properties.
     *
     * DB admin can:
     *  - See ALL companies (not just their own)
     *  - Access Database Manager regardless of role permissions
     *  - Edit any user profile in any company
     */
    fun isDbAdmin(): Boolean {
        val email = sessionManager.get()?.email ?: return false
        return email.equals(AppConfig.ADMIN_EMAIL, ignoreCase = true)
    }

    /**
     * Returns the sanitized company name the current user belongs to.
     * DB admin returns null (can access all companies).
     */
    fun userCompany(): String? {
        if (isDbAdmin()) return null          // null = unrestricted
        return sessionManager.get()?.let { s ->
            com.example.ritik_2.core.StringUtils.sanitize(
                // documentPath: "users/{sc}/{sd}/{role}/{uid}"
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
            Log.d(TAG, "Session restored for ${session.email}")
        } catch (e: Exception) {
            Log.w(TAG, "Session restore failed (possibly offline): ${e.message}")
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
                logout()
                return SessionStatus.TokenInvalid
            }
            val isActive = try {
                pbDataSource.checkIsActive(session.userId)
            } catch (e: Exception) {
                Log.w(TAG, "isActive check failed (network?): ${e.message}")
                true
            }
            if (!isActive) {
                Log.w(TAG, "Account deactivated — forcing logout")
                logout()
                return SessionStatus.Deactivated
            }
            syncManager.setUserToken(session.token)
            SessionStatus.Valid(session)
        } catch (e: Exception) {
            Log.w(TAG, "validateSession unexpected error: ${e.message}")
            SessionStatus.Valid(session)
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> =
        try {
            val session = dataSource.login(email, password)
            sessionManager.save(session)
            syncManager.setUserToken(session.token)
            Log.d(TAG, "Login success: $email role=${session.role} dbAdmin=${isDbAdmin()} ✅")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Login failed: ${e.message}")
            Result.failure(e)
        }

    suspend fun sendPasswordReset(email: String): Result<Unit> =
        dataSource.sendPasswordReset(email)

    suspend fun logout() {
        try { dataSource.logout() } catch (_: Exception) {}
        sessionManager.clear()
        syncManager.setUserToken("")
        Log.d(TAG, "Logged out ✅")
    }

    fun getSession() = sessionManager.get()
}

sealed class SessionStatus {
    object NoSession    : SessionStatus()
    object TokenInvalid : SessionStatus()
    object Deactivated  : SessionStatus()
    data class Valid(val session: com.example.ritik_2.data.model.AuthSession) : SessionStatus()
}