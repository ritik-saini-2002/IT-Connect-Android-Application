package com.example.ritik_2.registration

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.profile.profilecompletion.ProfileCompletionActivity
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegistrationActivity : ComponentActivity() {

    private val viewModel: RegistrationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        observeState()

        setContent {
            ITConnectTheme {
                RegistrationScreen(
                    onRegisterClick   = { request -> viewModel.register(request) },
                    onLoginClick      = { navigateToLogin() },
                    registrationState = viewModel.state
                )
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    // ✅ Both use com.example.ritik_2.registration.RegistrationState
                    is RegistrationState.Success -> {
                        toast("Account created!")
                        navigateToProfileCompletion(state.userId)
                    }
                    is RegistrationState.Error -> toast(state.message)
                    else -> {}
                }
            }
        }
    }

    private fun navigateToProfileCompletion(userId: String) {
        startActivity(ProfileCompletionActivity.createIntent(this, userId))
        finish()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}