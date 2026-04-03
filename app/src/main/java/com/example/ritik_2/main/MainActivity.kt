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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ritik_2.administrator.AdministratorPanelActivity
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.contact.ContactActivity
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.profile.ProfileActivity
import com.example.ritik_2.profile.profilecompletion.ProfileCompletionActivity
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

        val needsProfileCompletion = intent.getBooleanExtra("SHOW_COMPLETE_PROFILE_TOGGLE", false)

        val session = authRepository.getSession()
        val userId  = session?.userId
        userId?.let { viewModel.loadUserProfile(it) }

        // Refresh profile in background every time MainActivity resumes
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                userId?.let { viewModel.loadUserProfile(it, forceRefresh = true) }
            }
        }

        setContent {
            ITConnectTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                // Show profile completion toast once on entry
                LaunchedEffect(Unit) {
                    if (needsProfileCompletion) {
                        Toast.makeText(
                            this@MainActivity,
                            "Please complete your profile",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

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
                    uiState                  = uiState,
                    onLogout                 = { performLogout() },
                    onCardClick              = { handleCardClick(it) },
                    onProfileClick = {
                        val uid = uiState.userProfile?.id ?: return@MainScreen
                        // Always go to ProfileActivity — edit button visibility
                        // is controlled by role inside ProfileActivity itself
                        navigateToProfile(uid)
                    },
                    showCompleteProfileBanner = needsProfileCompletion
                )
            }
        }
    }

    private fun performLogout() {
        lifecycleScope.launch {
            authRepository.logout()
            navigateToLogin()
        }
    }

    private fun handleCardClick(cardId: Int) {
        val intent = when (cardId) {
            3    -> Intent(this, AdministratorPanelActivity::class.java)
            4    -> Intent(this, ServerConnectActivity::class.java)
            6    -> Intent(this, PcControlActivity::class.java)
            8    -> Intent(this, ContactActivity::class.java)
            else -> null
        }
        if (intent != null) startActivity(intent)
        else toast("Feature coming soon!")
    }

    private fun navigateToProfile(userId: String) {
        startActivity(Intent(this, ProfileActivity::class.java).putExtra("userId", userId))
    }

    private fun navigateToProfileCompletion(userId: String) {
        startActivity(ProfileCompletionActivity.createIntent(this, userId))
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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}