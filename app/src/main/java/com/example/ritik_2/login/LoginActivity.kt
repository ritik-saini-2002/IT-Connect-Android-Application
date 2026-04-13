package com.example.ritik_2.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.auth.AuthState
import com.example.ritik_2.auth.LoginViewModel
import com.example.ritik_2.contact.ContactActivity
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.registration.RegistrationActivity
import com.example.ritik_2.windowscontrol.PcControlActivity
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        observeState()

        setContent {
            ITConnectTheme {
                LoginScreen(
                    onLoginClick          = { email, pw -> viewModel.login(email, pw) },
                    onRegisterClick       = { startActivity(Intent(this, RegistrationActivity::class.java)) },
                    onForgotPasswordClick = { email -> viewModel.sendPasswordReset(email) },
                    onInfoClick           = { startActivity(Intent(this, ContactActivity::class.java)) },
                    onPcControlClick      = { startActivity(Intent(this, PcControlActivity::class.java)) },
                    onContactClick        = { startActivity(Intent(this, ContactActivity::class.java)) },
                    loginState            = viewModel.loginState,
                    resetState            = viewModel.resetState
                )
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                when (state) {
                    is AuthState.Success -> navigateToMain()
                    is AuthState.Error   -> toast(state.message)
                    else -> {}
                }
            }
        }
        lifecycleScope.launch {
            viewModel.resetState.collect { state ->
                when (state) {
                    is AuthState.Success -> toast("Reset link sent! Check your inbox.")
                    is AuthState.Error   -> toast(state.message)
                    else -> {}
                }
            }
        }
    }

    // Login never knows needsProfileCompletion — SplashActivity handles that.
    // Here we just clear the back stack and go to MainActivity.
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        viewModel.resetLoginState()
        viewModel.resetResetState()
    }
}