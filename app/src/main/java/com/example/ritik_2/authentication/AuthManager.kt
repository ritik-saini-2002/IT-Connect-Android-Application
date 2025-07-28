package com.example.ritik_2.authentication

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthManager private constructor() {

    companion object {
        private const val TAG = "AuthManager"
        private val VALID_ROLES = setOf("Administrator", "Manager", "Employee")

        @Volatile
        private var INSTANCE: AuthManager? = null

        fun getInstance(): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager().also { INSTANCE = it }
            }
        }
    }

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Flow to observe authentication state changes
    val authStateFlow: Flow<AuthState> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                Log.d(TAG, "User authenticated: ${user.uid}")
                trySend(AuthState.Loading)
                // Check user role in background
                checkUserRole(user.uid) { authState ->
                    trySend(authState)
                }
            } else {
                Log.d(TAG, "User not authenticated")
                trySend(AuthState.NotAuthenticated)
            }
        }

        firebaseAuth.addAuthStateListener(authStateListener)

        awaitClose {
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }

    // Get current user
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    // Check if user is logged in
    val isUserLoggedIn: Boolean
        get() = currentUser != null

    // Sign out user
    fun signOut() {
        try {
            firebaseAuth.signOut()
            Log.d(TAG, "User signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out user", e)
        }
    }

    // Check user authentication and role
    suspend fun checkAuthenticationState(): AuthState {
        return try {
            val user = currentUser
            if (user != null) {
                Log.d(TAG, "Checking user role for: ${user.uid}")
                checkUserRoleSync(user.uid)
            } else {
                Log.d(TAG, "No user logged in")
                AuthState.NotAuthenticated
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking authentication state", e)
            AuthState.Error(e.message ?: "Authentication check failed")
        }
    }

    // Synchronous role check
    private suspend fun checkUserRoleSync(userId: String): AuthState {
        return try {
            val document = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                val role = document.getString("role")
                val userName = document.getString("name") ?: "Unknown"
                val email = document.getString("email") ?: currentUser?.email ?: ""

                Log.d(TAG, "User role: $role")

                when {
                    role in VALID_ROLES -> {
                        Log.d(TAG, "Valid role found: $role")
                        AuthState.Authenticated(
                            user = UserData(
                                uid = userId,
                                name = userName,
                                email = email,
                                role = role ?: ""
                            )
                        )
                    }
                    else -> {
                        Log.w(TAG, "Invalid or missing role: $role")
                        AuthState.InvalidRole(role)
                    }
                }
            } else {
                Log.w(TAG, "User document doesn't exist")
                AuthState.UserNotFound
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user data", e)
            AuthState.Error(e.message ?: "Failed to fetch user data")
        }
    }

    // Async role check with callback
    private fun checkUserRole(userId: String, callback: (AuthState) -> Unit) {
        firestore.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role")
                    val userName = document.getString("name") ?: "Unknown"
                    val email = document.getString("email") ?: currentUser?.email ?: ""

                    when {
                        role in VALID_ROLES -> {
                            callback(
                                AuthState.Authenticated(
                                    user = UserData(
                                        uid = userId,
                                        name = userName,
                                        email = email,
                                        role = role ?: ""
                                    )
                                )
                            )
                        }
                        else -> {
                            callback(AuthState.InvalidRole(role))
                        }
                    }
                } else {
                    callback(AuthState.UserNotFound)
                }
            }
            .addOnFailureListener { e ->
                callback(AuthState.Error(e.message ?: "Failed to fetch user data"))
            }
    }
}

// Data classes for authentication state
sealed class AuthState {
    object Loading : AuthState()
    object NotAuthenticated : AuthState()
    data class Authenticated(val user: UserData) : AuthState()
    data class InvalidRole(val role: String?) : AuthState()
    object UserNotFound : AuthState()
    data class Error(val message: String) : AuthState()
}

data class UserData(
    val uid: String,
    val name: String,
    val email: String,
    val role: String
)