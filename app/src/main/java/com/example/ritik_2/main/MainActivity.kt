package com.example.ritik_2.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
//import com.example.ritik_2.administrator.administratorpanel.AdministratorPanelActivity
import com.example.ritik_2.authentication.AuthManager
//import com.example.ritik_2.complaint.newcomplaintmodel.ComplaintManagementActivity
//import com.example.ritik_2.complaint.newcomplaintregistration.NewRegisterComplaintActivity
import com.example.ritik_2.contact.ContactActivity
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.pocketbase.PocketBaseSessionManager
import com.example.ritik_2.profile.ProfileActivity
import com.example.ritik_2.theme.ITConnectTheme
import com.example.ritik_2.windowscontrol.PcControlActivity
import com.example.ritik_2.winshare.ServerConnectActivity

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private val authManager = AuthManager.getInstance()

    companion object { const val TAG = "MainActivity" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        PocketBaseSessionManager.init(this)
        authManager.restoreSession(this)

        if (!authManager.isLoggedIn) { navigateToLogin(); return }

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        val userId = PocketBaseSessionManager.getUserId()
        if (userId == null) { navigateToLogin(); return }

        viewModel.loadUserProfile(userId)

        setContent {
            ITConnectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()

                    uiState.error?.let { error ->
                        if (error.contains("deactivated") || error.contains("not authenticated")) {
                            authManager.signOut()
                            navigateToLogin()
                            return@Surface
                        }
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    MainScreen(
                        userProfile    = uiState.userProfile,
                        isLoading      = uiState.isLoading,
                        onLogout       = { authManager.signOut(); navigateToLogin() },
                        onCardClick    = { cardId -> handleCardClick(cardId) },
                        onProfileClick = { navigateToProfile() }
                    )
                }
            }
        }
    }

    private fun handleCardClick(cardId: Int) {
        val profile = viewModel.uiState.value.userProfile
        if (profile == null) { toast("Please wait for profile to load"); return }

        try {
            when (cardId) {
//                1 -> startActivity(Intent(this, NewRegisterComplaintActivity::class.java))
//                2 -> startActivity(Intent(this, ComplaintManagementActivity::class.java))
//                3 -> if (profile.role in listOf("Administrator","Manager","HR")) {
//                    startActivity(Intent(this, AdministratorPanelActivity::class.java))
//                } else toast("Access denied. Admin privileges required.")
                4 -> startActivity(Intent(this, ServerConnectActivity::class.java))
                5 -> startActivity(Intent(this, ContactActivity::class.java))
                6 -> startActivity(Intent(this, PcControlActivity::class.java))
                else -> toast("Feature coming soon!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Navigation error: ${e.message}", e)
            toast("Error opening feature: ${e.message}")
        }
    }

    private fun navigateToProfile() {
        val profile = viewModel.uiState.value.userProfile ?: run { toast("Profile not loaded"); return }
        startActivity(Intent(this, ProfileActivity::class.java).apply { putExtra("userId", profile.id) })
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (!authManager.isLoggedIn) navigateToLogin()
        else viewModel.refresh()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}