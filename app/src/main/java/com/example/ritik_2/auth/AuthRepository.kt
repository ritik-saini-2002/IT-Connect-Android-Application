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
            Log.w(TAG, "Session restore failed: ${e.message}")
            sessionManager.clear()
        }
    }

    /**
     * Validates the current session token AND checks isActive on the server.
     * Returns [SessionStatus] so the caller knows exactly what to do.
     * Called on every app resume / SplashActivity launch.
     */
    suspend fun validateSession(): SessionStatus {
        val session = sessionManager.get() ?: return SessionStatus.NoSession

        // 1. Validate token is still accepted by PocketBase
        val tokenValid = pbDataSource.validateCurrentToken()
        if (!tokenValid) {
            Log.w(TAG, "Token rejected — forcing logout")
            logout()
            return SessionStatus.TokenInvalid
        }

        // 2. Check isActive on the server — catches deactivation by admin
        val isActive = pbDataSource.checkIsActive(session.userId)
        if (!isActive) {
            Log.w(TAG, "Account deactivated — forcing logout")
            logout()
            return SessionStatus.Deactivated
        }

        return SessionStatus.Valid(session)
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
        dataSource.logout()
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