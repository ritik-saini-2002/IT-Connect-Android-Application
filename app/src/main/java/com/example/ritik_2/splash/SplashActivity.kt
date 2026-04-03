package com.example.ritik_2.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.auth.SessionStatus
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var dataSource    : AppDataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ITConnectTheme { ITConnectSplashScreen(onSplashComplete = {}) } }
        lifecycleScope.launch { route() }
    }

    private suspend fun route() {
        // 1. No local session → Login
        if (!authRepository.isLoggedIn) { go(LoginActivity::class.java); return }

        // 2. Restore token into memory
        authRepository.restoreSession()

        // 3. Validate token + check isActive in one call
        when (val status = authRepository.validateSession()) {
            is SessionStatus.NoSession -> {
                go(LoginActivity::class.java); return
            }
            is SessionStatus.TokenInvalid -> {
                Toast.makeText(this,
                    "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
                go(LoginActivity::class.java); return
            }
            is SessionStatus.Deactivated -> {
                Toast.makeText(this,
                    "Your account has been deactivated. Contact your administrator.",
                    Toast.LENGTH_LONG).show()
                go(LoginActivity::class.java); return
            }
            is SessionStatus.Valid -> {
                val userId = status.session.userId

                // 4. Check profile completion
                val needsCompletion = try {
                    dataSource.getUserProfile(userId).getOrNull()?.isProfileIncomplete ?: false
                } catch (_: Exception) { false }

                if (needsCompletion) {
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("SHOW_COMPLETE_PROFILE_TOGGLE", true)
                        }
                    )
                    finish()
                } else {
                    go(MainActivity::class.java)
                }
            }
        }
    }

    private fun go(cls: Class<*>) {
        startActivity(Intent(this, cls).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}