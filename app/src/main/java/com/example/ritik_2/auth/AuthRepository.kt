package com.example.ritik_2.auth

import android.util.Log
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.pocketbase.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val dataSource    : AppDataSource,
    private val sessionManager: SessionManager
) {
    companion object { private const val TAG = "AuthRepository" }

    val isLoggedIn: Boolean get() = sessionManager.isLoggedIn()

    suspend fun restoreSession() {
        sessionManager.get()?.let { session ->
            try {
                dataSource.restoreSession(session.token)
                Log.d(TAG, "Session restored for ${session.email}")
            } catch (e: Exception) {
                Log.w(TAG, "Session restore failed, clearing: ${e.message}")
                sessionManager.clear()
            }
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> =
        try {
            val session = dataSource.login(email, password)
            // isActive=false is already rejected inside PocketBaseDataSource.login()
            // with a clear error message. If we reach here the account is active.
            // We no longer reject on empty role — new users may not have one yet.
            sessionManager.save(session)
            Log.d(TAG, "Login success: $email ✅")
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