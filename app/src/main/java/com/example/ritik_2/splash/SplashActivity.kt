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
import com.example.ritik_2.pocketbase.PocketBaseSessionManager
import com.example.ritik_2.theme.Ritik_2Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    companion object {
        private const val TAG                    = "SplashActivity"
        private const val MINIMUM_SPLASH_DURATION = 3000L
    }

    private val authManager by lazy { AuthManager.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init session before auth check
        PocketBaseSessionManager.init(this)
        authManager.restoreSession(this)

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
        var authCheckComplete       by remember { mutableStateOf(false) }
        var resolvedAuthState       by remember { mutableStateOf<AuthState>(AuthState.Loading) }

        // Minimum splash duration
        LaunchedEffect(Unit) {
            delay(MINIMUM_SPLASH_DURATION)
            minimumDurationComplete = true
            Log.d(TAG, "Minimum duration completed")
        }

        // Auth check — just read restored state
        LaunchedEffect(Unit) {
            delay(300)
            resolvedAuthState = authManager.checkAuthenticationState()
            authCheckComplete = true
            Log.d(TAG, "Auth check completed: $resolvedAuthState")
        }

        // Navigate when all conditions met
        LaunchedEffect(splashAnimationComplete, minimumDurationComplete, authCheckComplete) {
            if (splashAnimationComplete && minimumDurationComplete && authCheckComplete) {
                handleNavigation(resolvedAuthState)
            }
        }

        ITConnectSplashScreen(
            onSplashComplete = {
                splashAnimationComplete = true
                Log.d(TAG, "Splash animation completed")
            }
        )
    }

    private fun handleNavigation(state: AuthState) {
        lifecycleScope.launch {
            Log.d(TAG, "Navigating based on state: $state")
            when (state) {
                is AuthState.Authenticated -> {
                    Log.d(TAG, "Authenticated as: ${state.user.role}")
                    navigateToMain()
                }
                is AuthState.NotAuthenticated -> {
                    Log.d(TAG, "Not authenticated")
                    navigateToLogin()
                }
                is AuthState.Error -> {
                    Log.e(TAG, "Auth error: ${state.message}")
                    authManager.signOut()
                    navigateToLogin()
                }
                is AuthState.Loading -> {
                    Log.d(TAG, "Still loading — defaulting to login")
                    navigateToLogin()
                }
            }
        }
    }

    private fun navigateToMain() {
        if (isFinishing || isDestroyed) return
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun navigateToLogin() {
        if (isFinishing || isDestroyed) return
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    override fun onDestroy() {
        Log.d(TAG, "SplashActivity destroyed")
        super.onDestroy()
    }
}