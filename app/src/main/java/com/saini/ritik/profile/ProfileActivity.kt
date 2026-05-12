package com.saini.ritik.profile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.PermissionGuard
import com.saini.ritik.profile.profilecompletion.ProfileCompletionActivity
import com.saini.ritik.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ProfileActivity : ComponentActivity() {

    private val viewModel: ProfileViewModel by viewModels()

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val userId = intent.getStringExtra("userId") ?: run { finish(); return }
        val targetUserId = intent.getStringExtra("targetUserId") ?: userId
        viewModel.loadProfile(userId)

        val session          = authRepository.getSession()
        val sessionRole      = session?.role        ?: ""
        val sessionId        = session?.userId      ?: ""
        val sessionPerms     = session?.permissions ?: emptyList()
        val isDbAdmin        = authRepository.isDbAdmin()
        val targetRole       = intent.getStringExtra("targetRole") ?: ""

        // Role-based gate (hierarchy check)
        val canEditByRole = PermissionGuard.canEditProfile(
            editorRole = sessionRole,
            targetRole = targetRole,
            editorId   = sessionId,
            targetId   = userId,
            isDbAdmin  = isDbAdmin
        )
        // Permission-based gate — own profile: edit_profile / edit_basic_profile grants access
        val canEditByPermission = (sessionId == userId) &&
                (com.saini.ritik.data.model.Permissions.PERM_EDIT_PROFILE       in sessionPerms ||
                        com.saini.ritik.data.model.Permissions.PERM_EDIT_BASIC_PROFILE in sessionPerms)

        val canEdit = canEditByRole || canEditByPermission

        // Admins/sysadmin who can edit the profile can also manage permissions.
        // Users cannot edit their own permissions (self-escalation guard).
        val canManagePermissions = canEdit && sessionId != userId

        setContent {
            ITConnectTheme {
                // Use getValue on the StateFlow directly — avoids smart-cast issues
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
                            isLoading             = uiState.isLoading,
                            canEdit               = canEdit,
//                            permissions           = p.permissions,
//                            canManagePermissions  = canManagePermissions,
                            onSavePermissions     = { updated ->
                                viewModel.updateUserPermissions(userId, updated)
                            },
                            onEditClick           = {
                                startActivity(
                                    ProfileCompletionActivity.createIntent(
                                        context        = this,
                                        userId         = userId,
                                        isEditMode     = true,
                                        targetUserRole = targetRole,
                                        editorRole     = sessionRole
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
                        Toast.makeText(this,
                            uiState.error ?: "Profile not found",
                            Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        intent.getStringExtra("userId")?.let { viewModel.loadProfile(it) }
    }
}