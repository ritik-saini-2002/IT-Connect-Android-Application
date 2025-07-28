package com.example.ritik_2.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.authentication.AuthManager
import com.example.ritik_2.authentication.AuthState
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.theme.Ritik_2Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.getValue

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val MINIMUM_SPLASH_DURATION = 3000L
    }

    private val authManager by lazy { AuthManager.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "SplashActivity created")

        setContent {
            Ritik_2Theme {
                SplashScreenContent()
            }
        }
    }

    @Composable
    private fun SplashScreenContent() {
        var splashAnimationComplete by remember { mutableStateOf(false) }
        var minimumDurationComplete by remember { mutableStateOf(false) }
        var authCheckComplete by remember { mutableStateOf(false) }
        var authState by remember { mutableStateOf<AuthState>(AuthState.Loading) }

        // Ensure minimum splash duration
        LaunchedEffect(Unit) {
            Log.d(TAG, "Starting minimum splash duration")
            delay(MINIMUM_SPLASH_DURATION)
            minimumDurationComplete = true
            Log.d(TAG, "Minimum splash duration completed")
        }

        // Perform authentication check
        LaunchedEffect(Unit) {
            Log.d(TAG, "Starting authentication check")
            authState = authManager.checkAuthenticationState()
            authCheckComplete = true
            Log.d(TAG, "Authentication check completed: $authState")
        }

        // Handle navigation when all conditions are met
        LaunchedEffect(splashAnimationComplete, minimumDurationComplete, authCheckComplete) {
            if (splashAnimationComplete && minimumDurationComplete && authCheckComplete) {
                Log.d(TAG, "All conditions met, handling navigation")
                handleAuthenticationResult(authState)
            }
        }

        ITConnectSplashScreen(
            onSplashComplete = {
                Log.d(TAG, "Splash animation completed")
                splashAnimationComplete = true
            }
        )
    }

    private fun handleAuthenticationResult(authState: AuthState) {
        lifecycleScope.launch {
            when (authState) {
                is AuthState.Authenticated -> {
                    Log.d(TAG, "User authenticated with role: ${authState.user.role}")
                    navigateToMain()
                }

                is AuthState.NotAuthenticated -> {
                    Log.d(TAG, "User not authenticated")
                    navigateToLogin()
                }

                is AuthState.InvalidRole -> {
                    Log.w(TAG, "Invalid user role: ${authState.role}")
                    authManager.signOut()
                    navigateToLogin()
                }

                is AuthState.UserNotFound -> {
                    Log.w(TAG, "User document not found")
                    authManager.signOut()
                    navigateToLogin()
                }

                is AuthState.Error -> {
                    Log.e(TAG, "Authentication error: ${authState.message}")
                    authManager.signOut()
                    navigateToLogin()
                }

                is AuthState.Loading -> {
                    Log.d(TAG, "Still loading authentication state")
                    // This shouldn't happen as we wait for completion
                    navigateToLogin()
                }
            }
        }
    }

    private fun navigateToMain() {
        if (!isFinishing && !isDestroyed) {
            Log.d(TAG, "Navigating to MainActivity")
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun navigateToLogin() {
        if (!isFinishing && !isDestroyed) {
            Log.d(TAG, "Navigating to LoginActivity")
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "SplashActivity destroyed")
        super.onDestroy()
    }
}