package com.example.ritik_2.authentication

import android.content.Context
import android.util.Log
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.data.source.PocketBaseDataSource
import com.example.ritik_2.pocketbase.PocketBaseSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthManager private constructor() {

    companion object {
        private const val TAG = "AuthManager"

        @Volatile private var INSTANCE: AuthManager? = null
        fun getInstance(): AuthManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager().also { INSTANCE = it }
            }
    }

    // ── DataSource — swap here to change auth backend ─────────
    private val dataSource: AppDataSource = PocketBaseDataSource()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val isLoggedIn: Boolean
        get() = PocketBaseSessionManager.isLoggedIn()

    val currentUserId: String?
        get() = PocketBaseSessionManager.getUserId()

    // ── Restore session ───────────────────────────────────────
    fun restoreSession(context: Context) {
        PocketBaseSessionManager.init(context)

        if (PocketBaseSessionManager.isLoggedIn()) {
            val token = PocketBaseSessionManager.getToken()!!

            kotlinx.coroutines.runBlocking {
                try { dataSource.restoreSession(token) } catch (_: Exception) {}
            }

            val userData = UserData(
                uid          = PocketBaseSessionManager.getUserId() ?: "",
                name         = PocketBaseSessionManager.getName() ?: "",
                email        = PocketBaseSessionManager.getEmail() ?: "",
                role         = PocketBaseSessionManager.getRole() ?: "",
                documentPath = PocketBaseSessionManager.getDocPath() ?: ""
            )
            _authState.value = AuthState.Authenticated(userData)
            Log.d(TAG, "Session restored for: ${userData.email} ✅")
        } else {
            _authState.value = AuthState.NotAuthenticated
        }
    }

    // ── Login ─────────────────────────────────────────────────
    suspend fun login(email: String, password: String): LoginResult {
        return try {
            _authState.value = AuthState.Loading

            val authResult = dataSource.login(email, password)

            // Verify access control
            val accessResult = dataSource.getUserAccessControl(authResult.userId)
            if (accessResult.isFailure) {
                dataSource.logout()
                return LoginResult.Error("Profile not configured. Contact administrator.")
            }

            val access = accessResult.getOrThrow()
            if (!access.isActive) {
                dataSource.logout()
                return LoginResult.Error("Account deactivated. Contact administrator.")
            }

            // Save session
            PocketBaseSessionManager.saveSession(
                token        = authResult.token,
                userId       = authResult.userId,
                email        = email,
                name         = access.name,
                role         = access.role,
                documentPath = access.documentPath
            )

            val userData = UserData(
                uid          = authResult.userId,
                name         = access.name,
                email        = email,
                role         = access.role,
                documentPath = access.documentPath
            )
            _authState.value = AuthState.Authenticated(userData)

            Log.d(TAG, "Login success: $email ✅")
            LoginResult.Success(userData)

        } catch (e: Exception) {
            _authState.value = AuthState.NotAuthenticated
            Log.e(TAG, "Login failed: ${e.message}", e)
            LoginResult.Error(mapError(e.message))
        }
    }

    // ── Password reset ────────────────────────────────────────
    suspend fun sendPasswordResetEmail(email: String): LoginResult {
        return try {
            dataSource.sendPasswordReset(email).getOrThrow()
            LoginResult.Success(null)
        } catch (e: Exception) {
            LoginResult.Error(mapError(e.message))
        }
    }

    // ── Sign out ──────────────────────────────────────────────
    fun signOut() {
        kotlinx.coroutines.runBlocking {
            try { dataSource.logout() } catch (_: Exception) {}
        }
        PocketBaseSessionManager.clearSession()
        _authState.value = AuthState.NotAuthenticated
        Log.d(TAG, "Signed out ✅")
    }

    // ── Used by SplashActivity ────────────────────────────────
    fun checkAuthenticationState(): AuthState = _authState.value

    private fun mapError(msg: String?): String = when {
        msg == null                                -> "Unknown error"
        msg.contains("400")                        -> "Invalid email or password"
        msg.contains("network", ignoreCase = true) -> "Network error — check Wi-Fi"
        msg.contains("timeout", ignoreCase = true) -> "Connection timed out"
        msg.contains("connect", ignoreCase = true) -> "Cannot reach server — check IP"
        else                                       -> "Login failed: $msg"
    }
}

// ── Sealed states ─────────────────────────────────────────────
sealed class AuthState {
    object Loading          : AuthState()
    object NotAuthenticated : AuthState()
    data class Authenticated(val user: UserData) : AuthState()
    data class Error(val message: String)        : AuthState()
}

sealed class LoginResult {
    data class Success(val user: UserData?) : LoginResult()
    data class Error(val message: String)   : LoginResult()
}

data class UserData(
    val uid: String,
    val name: String,
    val email: String,
    val role: String,
    val documentPath: String = ""
)