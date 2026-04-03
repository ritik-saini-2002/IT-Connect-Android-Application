package com.example.ritik_2.main

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.administrator.AdministratorPanelActivity
import com.example.ritik_2.administrator.manageuser.ManageUserActivity
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.auth.SessionStatus
import com.example.ritik_2.contact.ContactActivity
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.macnet.MACNetActivity
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

        val showProfileBanner = intent.getBooleanExtra(
            "SHOW_COMPLETE_PROFILE_TOGGLE", false)

        setContent {
            ITConnectTheme {
                val uiState = viewModel.uiState
                MainScreen(
                    uiState                   = uiState,
                    onLogout                  = { handleLogout() },
                    onCardClick               = { id -> handleCardClick(id) },
                    onProfileClick            = { handleProfileClick() },
                    showCompleteProfileBanner = showProfileBanner
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
        lifecycleScope.launch { checkActiveStatus() }
    }

    private fun handleCardClick(id: Int) {
        when (id) {
            1 -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
            2 -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
            3 -> {
                // Open AdministratorPanelActivity for everyone —
                // it handles role verification and shows the animated
                // AccessDeniedScreen with auto-redirect for unauthorized users
                startActivity(Intent(this, AdministratorPanelActivity::class.java))
            }
            4 -> startActivity(Intent(this, ServerConnectActivity::class.java))
            5 -> startActivity(Intent(this, MACNetActivity::class.java))
            6 -> startActivity(Intent(this, PcControlActivity::class.java))
            7 -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
            8 -> {
            // Settings → view own profile (ProfileActivity)
            val userId = authRepository.getSession()?.userId ?: return
            startActivity(Intent(this, ContactActivity::class.java).apply {
                putExtra("userId", userId)
            })
        }
        }
    }

    // Profile card tap + sidebar header tap → view own profile
    private fun handleProfileClick() {
        val userId = authRepository.getSession()?.userId ?: return
        startActivity(Intent(this, ProfileActivity::class.java).apply {
            putExtra("userId", userId)
        })
    }

    private fun handleLogout() {
        lifecycleScope.launch {
            authRepository.logout()
            startActivity(Intent(this@MainActivity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private suspend fun checkActiveStatus() {
        when (authRepository.validateSession()) {
            is SessionStatus.Deactivated  -> forceLogout(
                "Your account has been deactivated. Contact your administrator.")
            is SessionStatus.TokenInvalid -> forceLogout(
                "Session expired. Please log in again.")
            is SessionStatus.NoSession    -> forceLogout("Please log in.")
            is SessionStatus.Valid        -> { /* all good */ }
        }
    }

    private fun forceLogout(message: String) {
        lifecycleScope.launch {
            authRepository.logout()
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            startActivity(Intent(this@MainActivity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }
}