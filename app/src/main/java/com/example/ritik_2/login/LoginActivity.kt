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
                    onLoginClick                = { email, pw -> viewModel.login(email, pw) },
                    otpLoginState    = viewModel.otpLoginState,
                    onSendLoginOtp   = { email -> viewModel.sendLoginOtp(email) },
                    onLoginWithOtp   = { email, otp -> viewModel.loginWithOtp(email, otp) },
                    onRegisterClick             = { startActivity(Intent(this, RegistrationActivity::class.java)) },
                    onForgotPasswordClick       = { email -> viewModel.sendOtp(email) },
                    onVerifyOtpAndResetPassword = { email, otp, newPass ->
                        viewModel.verifyOtpAndResetPassword(email, otp, newPass)
                    },
                    onInfoClick      = { startActivity(Intent(this, ContactActivity::class.java)) },
                    onPcControlClick = { startActivity(Intent(this, PcControlActivity::class.java)) },
                    onContactClick   = { startActivity(Intent(this, ContactActivity::class.java)) },
                    loginState       = viewModel.loginState,
                    resetState       = viewModel.resetState,
                    onVerifyOtp      = { email, otp -> viewModel.verifyOtp(email, otp) },
                    onResetPassword  = { email, otp, pass -> viewModel.resetPassword(email, otp, pass) },
                )
            }
        }
    }

    private fun observeState() {
        // ── Login state — only handles login events ──────────────────────
        lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                when (state) {
                    is AuthState.Success -> navigateToMain()   // ← was missing!
                    is AuthState.Error   -> toast(state.message)
                    else -> {}
                }
            }
        }

        // ── OTP Based Login state — only handles login events ──────────────────────

        lifecycleScope.launch {
            viewModel.otpLoginState.collect { state ->
                when (state) {
                    is AuthState.Success -> navigateToMain()
                    is AuthState.Error   -> toast(state.message)
                    else -> {}
                }
            }
        }

        // ── Reset state — only handles OTP/reset events ──────────────────
        lifecycleScope.launch {
            viewModel.resetState.collect { state ->
                when (state) {
                    is AuthState.OtpSent -> { /* dialog advances itself via LaunchedEffect */ }
                    is AuthState.Success -> toast("Password reset successfully! Please log in.")
                    is AuthState.Error   -> toast(state.message)
                    else -> {}
                }
            }
        }
    }

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
        viewModel.resetOtpLoginState()

    }
}