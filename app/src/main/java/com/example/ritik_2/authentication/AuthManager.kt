package com.example.ritik_2.authentication

import android.content.Context
import android.util.Log
import com.example.ritik_2.data.pocketbase.PocketBaseClient
import com.example.ritik_2.data.pocketbase.PocketBaseSessionManager
import com.example.ritik_2.registration.models.UserRecord
import io.github.agrevster.pocketbaseKotlin.models.AuthRecord
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

    private val pb = PocketBaseClient.instance

    // ── Auth state exposed as StateFlow ───────────────────────
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // ── Current user shortcut ─────────────────────────────────
    var currentUserId: String? = null
        private set
    var currentUserEmail: String? = null
        private set
    var currentUserName: String? = null
        private set
    var currentUserRole: String? = null
        private set
    var currentDocumentPath: String? = null
        private set

    // ── Restore session from encrypted prefs ──────────────────
    fun restoreSession(context: Context) {
        PocketBaseSessionManager.init(context)

        if (PocketBaseSessionManager.isLoggedIn()) {
            val token = PocketBaseSessionManager.getToken()!!
            // Restore token into PocketBase client
            pb.login { this.token = token }

            currentUserId       = PocketBaseSessionManager.getUserId()
            currentUserEmail    = PocketBaseSessionManager.getEmail()
            currentUserName     = PocketBaseSessionManager.getName()
            currentUserRole     = PocketBaseSessionManager.getRole()
            currentDocumentPath = PocketBaseSessionManager.getDocPath()

            _authState.value = AuthState.Authenticated(
                UserData(
                    uid          = currentUserId!!,
                    name         = currentUserName ?: "",
                    email        = currentUserEmail ?: "",
                    role         = currentUserRole ?: "",
                    documentPath = currentDocumentPath ?: ""
                )
            )
            Log.d(TAG, "Session restored for: $currentUserEmail ✅")
        } else {
            _authState.value = AuthState.NotAuthenticated
            Log.d(TAG, "No saved session found")
        }
    }

    // ── Login ─────────────────────────────────────────────────
    suspend fun login(email: String, password: String): AuthResult {
        return try {
            _authState.value = AuthState.Loading

            // 1. Authenticate with PocketBase
            val authResponse = pb.records
                .authWithPassword<AuthRecord>(
                    PocketBaseInitializer.COL_USERS,
                    email,
                    password
                )

            val token  = authResponse.token
                ?: return AuthResult.Error("Authentication failed — no token received")
            val userId = authResponse.record?.id
                ?: return AuthResult.Error("Authentication failed — no user ID")

            // 2. Login client with token
            pb.login { this.token = token }
            Log.d(TAG, "PocketBase auth successful for: $email ✅")

            // 3. Fetch user access control record for role + documentPath
            val accessResult = pb.records.getList<UserAccessRecord>(
                PocketBaseInitializer.COL_ACCESS_CONTROL, 1, 1,
                "userId='$userId'"
            )

            if (accessResult.totalItems == 0) {
                return AuthResult.Error("User profile not configured. Contact administrator.")
            }

            val access = accessResult.items.first()

            if (!access.isActive) {
                pb.logout()
                return AuthResult.Error("Your account is deactivated. Contact administrator.")
            }

            // 4. Save session
            currentUserId       = userId
            currentUserEmail    = email
            currentUserName     = access.name
            currentUserRole     = access.role
            currentDocumentPath = access.documentPath

            PocketBaseSessionManager.saveSession(
                token        = token,
                userId       = userId,
                email        = email,
                name         = access.name,
                role         = access.role,
                documentPath = access.documentPath
            )

            // 5. Update auth state
            val userData = UserData(
                uid          = userId,
                name         = access.name,
                email        = email,
                role         = access.role,
                documentPath = access.documentPath
            )
            _authState.value = AuthState.Authenticated(userData)

            // 6. Update last login in background (fire-and-forget)
            try {
                pb.records.update<UserRecord>(
                    PocketBaseInitializer.COL_USERS,
                    userId,
                    """{"lastLogin":"${System.currentTimeMillis()}"}"""
                )
            } catch (_: Exception) { /* non-critical */ }

            Log.d(TAG, "Login complete for: $email ✅")
            AuthResult.Success(userData)

        } catch (e: Exception) {
            _authState.value = AuthState.NotAuthenticated
            Log.e(TAG, "Login failed: ${e.message}", e)
            AuthResult.Error(mapLoginError(e.message))
        }
    }

    // ── Send password reset email (PocketBase built-in) ───────
    suspend fun sendPasswordResetEmail(email: String): AuthResult {
        return try {
            pb.records.requestPasswordReset(PocketBaseInitializer.COL_USERS, email)
            Log.d(TAG, "Password reset email sent to: $email ✅")
            AuthResult.Success(null)
        } catch (e: Exception) {
            Log.e(TAG, "Password reset failed: ${e.message}", e)
            AuthResult.Error(mapLoginError(e.message))
        }
    }

    // ── Sign out ──────────────────────────────────────────────
    fun signOut() {
        try {
            pb.logout()
            PocketBaseSessionManager.clearSession()
            currentUserId       = null
            currentUserEmail    = null
            currentUserName     = null
            currentUserRole     = null
            currentDocumentPath = null
            _authState.value    = AuthState.NotAuthenticated
            Log.d(TAG, "User signed out ✅")
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error: ${e.message}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────
    val isLoggedIn: Boolean
        get() = PocketBaseSessionManager.isLoggedIn()

    private fun mapLoginError(msg: String?): String = when {
        msg == null                                         -> "Unknown error occurred"
        msg.contains("400", ignoreCase = true)             -> "Invalid email or password"
        msg.contains("403", ignoreCase = true)             -> "Account is not active"
        msg.contains("network", ignoreCase = true)         -> "Network error. Check your connection"
        msg.contains("timeout", ignoreCase = true)         -> "Connection timed out. Try again"
        msg.contains("connect", ignoreCase = true)         -> "Cannot connect to server. Check Wi-Fi"
        else                                               -> "Login failed: $msg"
    }
}

// ── Lightweight model for access control records ──────────────
@kotlinx.serialization.Serializable
data class UserAccessRecord(
    val userId: String       = "",
    val name: String         = "",
    val email: String        = "",
    val role: String         = "",
    val isActive: Boolean    = true,
    val documentPath: String = "",
    val permissions: String  = "[]"
) : io.github.agrevster.pocketbaseKotlin.models.Record()

// ── Sealed auth states ────────────────────────────────────────
sealed class AuthState {
    object Loading          : AuthState()
    object NotAuthenticated : AuthState()
    data class Authenticated(val user: UserData) : AuthState()
    data class Error(val message: String)        : AuthState()
}

// ── Auth operation results ────────────────────────────────────
sealed class AuthResult {
    data class Success(val user: UserData?) : AuthResult()
    data class Error(val message: String)   : AuthResult()
}

// ── User data model ───────────────────────────────────────────
data class UserData(
    val uid: String,
    val name: String,
    val email: String,
    val role: String,
    val documentPath: String = ""
)

// ── Keep PocketBaseInitializer constants accessible ───────────
private object PocketBaseInitializer {
    const val COL_USERS          = "users"
    const val COL_ACCESS_CONTROL = "user_access_control"
}