package com.example.ritik_2.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ritik_2.authentication.AuthManager
import com.example.ritik_2.authentication.AuthResult
import com.example.ritik_2.contact.ContactActivity
import com.example.ritik_2.data.pocketbase.PocketBaseSessionManager
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.registration.RegistrationActivity
import com.example.ritik_2.theme.Ritik_2Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : ComponentActivity() {

    private val authManager = AuthManager.getInstance()

    companion object {
        const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Init encrypted session storage
        PocketBaseSessionManager.init(this)

        Log.d(TAG, "LoginActivity started")

        // Restore existing session if available
        authManager.restoreSession(this)

        if (authManager.isLoggedIn) {
            Log.d(TAG, "Active session found — skipping login screen")
            navigateToMain()
            return
        }

        showLoginScreen()
    }

    private fun showLoginScreen() {
        setContent {
            Ritik_2Theme {
                LoginScreen(
                    onLoginClick = { email, password ->
                        performLogin(email, password)
                    },
                    onRegisterClick = {
                        startActivity(Intent(this, RegistrationActivity::class.java))
                    },
                    onForgotPasswordClick = { email ->
                        sendPasswordReset(email)
                    },
                    onInfoClick = {
                        startActivity(Intent(this, ContactActivity::class.java))
                    }
                )
            }
        }
    }

    private fun performLogin(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            toast("Please enter both email and password")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Please enter a valid email address")
            return
        }

        Log.d(TAG, "Attempting PocketBase login for: $email")

        CoroutineScope(Dispatchers.IO).launch {
            when (val result = authManager.login(email, password)) {
                is AuthResult.Success -> {
                    Log.d(TAG, "Login successful ✅")
                    withContext(Dispatchers.Main) {
                        toast("Welcome back, ${result.user?.name ?: email}!")
                        navigateToMain()
                    }
                }
                is AuthResult.Error -> {
                    Log.e(TAG, "Login failed: ${result.message}")
                    withContext(Dispatchers.Main) {
                        toast(result.message)
                    }
                }
            }
        }
    }

    private fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            toast("Please enter your email address")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Please enter a valid email address")
            return
        }

        Log.d(TAG, "Sending password reset to: $email")

        CoroutineScope(Dispatchers.IO).launch {
            when (val result = authManager.sendPasswordResetEmail(email)) {
                is AuthResult.Success -> {
                    withContext(Dispatchers.Main) {
                        toast("Password reset link sent to $email — check your inbox!")
                    }
                }
                is AuthResult.Error -> {
                    withContext(Dispatchers.Main) {
                        toast(result.message)
                    }
                }
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishAffinity()
        super.onBackPressed()
    }
}