package com.example.ritik_2.main

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope                          // ✅ correct import
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.contact.ContactActivity
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.profile.ProfileActivity
import com.example.ritik_2.theme.ITConnectTheme
import com.example.ritik_2.windowscontrol.PcControlActivity
import com.example.ritik_2.winshare.ServerConnectActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!authRepository.isLoggedIn) { navigateToLogin(); return }

        val session = authRepository.getSession()
        session?.userId?.let { viewModel.loadUserProfile(it) }

        setContent {
            ITConnectTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(uiState.error) {
                    uiState.error?.let { error ->
                        if (error.contains("deactivated") || error.contains("not authenticated")) {
                            authRepository.logout()
                            navigateToLogin()
                        } else {
                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }

                MainScreen(
                    uiState        = uiState,
                    onLogout       = { performLogout() },        // ✅ renamed
                    onCardClick    = { handleCardClick(it) },
                    onProfileClick = { uiState.userProfile?.id?.let { navigateToProfile(it) } }
                )
            }
        }
    }

    // ✅ Renamed from lifecycleScope() — was conflicting with AndroidX property
    private fun performLogout() {
        lifecycleScope.launch {                                  // ✅ now resolves correctly
            authRepository.logout()
            navigateToLogin()
        }
    }

    private fun handleCardClick(cardId: Int) {
        val intent = when (cardId) {
            4    -> Intent(this, ServerConnectActivity::class.java)
            6    -> Intent(this, PcControlActivity::class.java)
            8    -> Intent(this, ContactActivity::class.java)
            else -> null
        }
        if (intent != null) startActivity(intent)
        else toast("Feature coming soon!")
    }

    private fun navigateToProfile(userId: String) {
        startActivity(
            Intent(this, ProfileActivity::class.java)
                .putExtra("userId", userId)
        )
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (!authRepository.isLoggedIn) navigateToLogin()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}