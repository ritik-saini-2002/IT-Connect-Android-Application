//package com.example.ritik_2.authentication
//
//import android.content.Intent
//import android.os.Bundle
//import android.util.Log
//import androidx.activity.ComponentActivity
//import androidx.lifecycle.lifecycleScope
//import com.example.ritik_2.login.LoginActivity
//import kotlinx.coroutines.flow.collectLatest
//import kotlinx.coroutines.launch
//
//abstract class BaseActivity : ComponentActivity() {
//
//    companion object {
//        private const val TAG = "BaseActivity"
//    }
//
//    protected val authManager by lazy { AuthManager.getInstance() }
//    private var isAuthCheckEnabled = true
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        if (shouldCheckAuth()) {
//            observeAuthState()
//        }
//    }
//
//    // Override this in activities that don't need auth checking (like LoginActivity)
//    protected open fun shouldCheckAuth(): Boolean = true
//
//    // Disable auth checking (useful for login flow)
//    protected fun disableAuthCheck() {
//        isAuthCheckEnabled = false
//    }
//
//    // Enable auth checking
//    protected fun enableAuthCheck() {
//        isAuthCheckEnabled = true
//        if (shouldCheckAuth()) {
//            observeAuthState()
//        }
//    }
//
//    private fun observeAuthState() {
//        lifecycleScope.launch {
//            authManager.authStateFlow.collectLatest { authState ->
//                if (isAuthCheckEnabled) {
//                    handleAuthStateChange(authState)
//                }
//            }
//        }
//    }
//
//    private fun handleAuthStateChange(authState: AuthState) {
//        when (authState) {
//            is AuthState.NotAuthenticated -> {
//                Log.d(TAG, "User not authenticated, redirecting to login")
//                redirectToLogin()
//            }
//
//            is AuthState.InvalidRole -> {
//                Log.w(TAG, "Invalid user role: ${authState.role}")
//                authManager.signOut()
//                redirectToLogin()
//            }
//
//            is AuthState.UserNotFound -> {
//                Log.w(TAG, "User document not found")
//                authManager.signOut()
//                redirectToLogin()
//            }
//
//            is AuthState.Error -> {
//                Log.e(TAG, "Authentication error: ${authState.message}")
//                authManager.signOut()
//                redirectToLogin()
//            }
//
//            is AuthState.Authenticated -> {
//                Log.d(TAG, "User authenticated: ${authState.user.name}")
//                onUserAuthenticated(authState.user)
//            }
//
//            is AuthState.Loading -> {
//                Log.d(TAG, "Authentication state loading")
//                onAuthLoading()
//            }
//        }
//    }
//
//    // Override these methods in child activities
//    protected open fun onUserAuthenticated(userData: com.example.ritik_2.authentication.UserData) {
//        // Handle authenticated user
//    }
//
//    protected open fun onAuthLoading() {
//        // Handle loading state
//    }
//
//    private fun redirectToLogin() {
//        if (!isFinishing && !isDestroyed) {
//            val intent = Intent(this, LoginActivity::class.java).apply {
//                Intent.setFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//            }
//            startActivity(intent)
//            finish()
//        }
//    }
//
//    // Utility method to check if user is logged in
//    protected fun isUserLoggedIn(): Boolean {
//        return authManager.isUserLoggedIn
//    }
//
//    // Utility method to get current user
//    protected fun getCurrentUser() = authManager.currentUser
//
//    // Utility method to sign out
//    protected fun signOut() {
//        authManager.signOut()
//    }
//}