// ─── profile/ProfileActivity.kt ───────────────────────────────
package com.example.ritik_2.profile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.main.UserProfileData
import com.example.ritik_2.main.toUiModel
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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

                uiState.profile?.let { profile ->
                    ProfileScreen(
                        profileImageUrl    = null,  // Uri? — null since we use String URL
                        name               = profile.name,
                        email              = profile.email,
                        phoneNumber        = profile.phoneNumber,
                        designation        = profile.designation,
                        companyName        = profile.companyName,
                        role               = profile.role,
                        userId             = profile.id,
                        complaints         = profile.totalComplaints,
                        experience         = profile.experience,
                        completedProjects  = profile.completedProjects,
                        activeProjects     = profile.activeProjects,
                        isLoading          = uiState.isLoading,
                        onLogoutClick      = { viewModel.logout(); finish() },
                        onEditClick        = { field, value -> viewModel.updateField(userId, field, value) },
                        onChangeProfilePic = { },
                        onBackClick        = { finish() }
                    )
                } ?: run {
                    if (uiState.isLoading) {
                        androidx.compose.material3.CircularProgressIndicator()
                    } else {
                        Toast.makeText(this, uiState.error ?: "Profile not found",
                            android.widget.Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }
}


// ─── profile/profilecompletion/ProfileCompletionActivity.kt ───
