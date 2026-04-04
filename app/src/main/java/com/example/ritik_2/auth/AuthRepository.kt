package com.example.ritik_2.auth

import android.util.Log
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.pocketbase.PocketBaseDataSource
import com.example.ritik_2.pocketbase.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val dataSource    : AppDataSource,
    private val pbDataSource  : PocketBaseDataSource,
    private val sessionManager: SessionManager
) {
    companion object { private const val TAG = "AuthRepository" }

    val isLoggedIn: Boolean get() = sessionManager.isLoggedIn()

    suspend fun restoreSession() {
        val session = sessionManager.get()
        if (session == null) { Log.d(TAG, "restoreSession: no saved session"); return }
        try {
            dataSource.restoreSession(session.token)
            Log.d(TAG, "Session restored for ${session.email}")
        } catch (e: Exception) {
            // Don't clear session on restore failure — we may be offline
            Log.w(TAG, "Session restore failed (possibly offline): ${e.message}")
        }
    }

    /**
     * Validates token + isActive. Returns [SessionStatus].
     *
     * If a network error occurs (server unreachable), returns [SessionStatus.Valid]
     * optimistically so the app doesn't force-logout when offline.
     * The ConnectivityMonitor + MainActivity handle showing the offline banner.
     */
    suspend fun validateSession(): SessionStatus {
        val session = sessionManager.get() ?: return SessionStatus.NoSession

        return try {
            // 1. Validate token — if network fails, assume valid
            val tokenValid = try {
                pbDataSource.validateCurrentToken()
            } catch (e: Exception) {
                Log.w(TAG, "Token validation failed (network?): ${e.message}")
                // Network error — assume token is still valid, go offline
                return SessionStatus.Valid(session)
            }

            if (!tokenValid) {
                Log.w(TAG, "Token rejected — forcing logout")
                logout()
                return SessionStatus.TokenInvalid
            }

            // 2. Check isActive — if network fails, assume active
            val isActive = try {
                pbDataSource.checkIsActive(session.userId)
            } catch (e: Exception) {
                Log.w(TAG, "isActive check failed (network?): ${e.message}")
                true  // optimistic — don't deactivate offline users
            }

            if (!isActive) {
                Log.w(TAG, "Account deactivated — forcing logout")
                logout()
                return SessionStatus.Deactivated
            }

            SessionStatus.Valid(session)

        } catch (e: Exception) {
            // Catch-all for unexpected errors — go offline rather than force logout
            Log.w(TAG, "validateSession unexpected error: ${e.message}")
            SessionStatus.Valid(session)
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> =
        try {
            val session = dataSource.login(email, password)
            sessionManager.save(session)
            Log.d(TAG, "Login success: $email role=${session.role} ✅")
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