package com.example.ritik_2.profile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.profile.profilecompletion.ProfileCompletionActivity
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileActivity : ComponentActivity() {

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val userId = intent.getStringExtra("userId") ?: run { finish(); return }
        viewModel.loadProfile(userId)

        setContent {
            ITConnectTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                when {
                    uiState.isLoading -> CircularProgressIndicator()

                    uiState.profile != null -> {
                        val p = uiState.profile!!
                        ProfileScreen(
                            imageUrl           = p.imageUrl,
                            name               = p.name,
                            email              = p.email,
                            phoneNumber        = p.phoneNumber,
                            designation        = p.designation,
                            companyName        = p.companyName,
                            department         = p.department,
                            role               = p.role,
                            userId             = p.id,
                            experience         = p.experience,
                            completedProjects  = p.completedProjects,
                            activeProjects     = p.activeProjects,
                            pendingTasks       = p.pendingTasks,
                            totalComplaints    = p.totalComplaints,
                            resolvedComplaints = p.resolvedComplaints,
                            isLoading          = uiState.isLoading,
                            onEditClick        = {
                                startActivity(
                                    ProfileCompletionActivity.createIntent(
                                        this, userId, isEditMode = true
                                    )
                                )
                            },
                            onLogoutClick = {
                                viewModel.logout()
                                finish()
                            },
                            onBackClick = { finish() }
                        )
                    }

                    else -> {
                        Toast.makeText(
                            this,
                            uiState.error ?: "Profile not found",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh when returning from edit screen so changes show immediately
        intent.getStringExtra("userId")?.let { viewModel.loadProfile(it) }
    }
}