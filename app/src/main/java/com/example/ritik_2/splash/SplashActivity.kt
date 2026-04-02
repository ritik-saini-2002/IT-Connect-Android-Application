package com.example.ritik_2.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ITConnectTheme {
                ITConnectSplashScreen(onSplashComplete = {
                    // animation done — navigation handled by coroutine below
                })
            }
        }

        lifecycleScope.launch {
            // Restore token into SDK (non-blocking, no runBlocking)
            authRepository.restoreSession()
            // Minimum splash display
            delay(2000L)
            navigate()
        }
    }

    private fun navigate() {
        if (isFinishing || isDestroyed) return
        val dest = if (authRepository.isLoggedIn) MainActivity::class.java
        else LoginActivity::class.java
        startActivity(Intent(this, dest).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}