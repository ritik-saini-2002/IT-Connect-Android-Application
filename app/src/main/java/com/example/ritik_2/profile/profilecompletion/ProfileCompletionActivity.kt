package com.example.ritik_2.profile.profilecompletion

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.administrator.manageuser.ManageUserActivity
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.data.model.Permissions
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProfileCompletionActivity : ComponentActivity() {

    private val viewModel: ProfileCompletionViewModel by viewModels()

    @Inject lateinit var authRepository: AuthRepository

    // ── Image picker restricted to JPEG / PNG / WebP ──────────────────────────
    // The URI is forwarded into Compose state via a MutableState bridge.
    private val _pickedUri  = mutableStateOf<Uri?>(null)
    private val _showCrop   = mutableStateOf(false)

    private val imageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            _pickedUri.value = uri
            _showCrop.value  = true
        }
    }

    companion object {
        private const val EXTRA_USER_ID        = "user_id"
        private const val EXTRA_EDIT_MODE      = "edit_mode"
        private const val EXTRA_TARGET_ROLE    = "target_role"
        private const val EXTRA_EDITOR_ROLE    = "editor_role"

        fun createIntent(
            context        : Context,
            userId         : String,
            isEditMode     : Boolean = false,
            targetUserRole : String  = "",
            editorRole     : String  = ""
        ) = Intent(context, ProfileCompletionActivity::class.java).apply {
            putExtra(EXTRA_USER_ID,     userId)
            putExtra(EXTRA_EDIT_MODE,   isEditMode)
            putExtra(EXTRA_TARGET_ROLE, targetUserRole)
            putExtra(EXTRA_EDITOR_ROLE, editorRole)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId        = intent.getStringExtra(EXTRA_USER_ID)     ?: ""
        val isEditMode    = intent.getBooleanExtra(EXTRA_EDIT_MODE,   false)
        val targetRole    = intent.getStringExtra(EXTRA_TARGET_ROLE)  ?: ""
        val editorRoleArg = intent.getStringExtra(EXTRA_EDITOR_ROLE)  ?: ""

        if (userId.isBlank()) {
            Toast.makeText(this, "Invalid user session", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val session           = authRepository.getSession()
        val sessionRole       = session?.role        ?: ""
        val sessionId         = session?.userId      ?: ""
        val isDbAdmin         = authRepository.isDbAdmin()
        val editorRole        = editorRoleArg.ifBlank { sessionRole }
        // Declare FIRST — used by both canEdit and isAdmin checks below
        val editorPermissions = session?.permissions ?: emptyList()

        val canEditByRole = PermissionGuard.canEditProfile(
            editorRole = editorRole,
            targetRole = targetRole,
            editorId   = sessionId,
            targetId   = userId,
            isDbAdmin  = isDbAdmin
        )
        // Own-profile: PERM_EDIT_PROFILE or PERM_EDIT_BASIC_PROFILE is sufficient
        val canEditByPermission = (sessionId == userId) &&
                (Permissions.PERM_EDIT_PROFILE       in editorPermissions ||
                        Permissions.PERM_EDIT_BASIC_PROFILE in editorPermissions)
        val canEdit = canEditByRole || canEditByPermission

        // Permission-based: isAdmin means editor can write all fields.
        val isAdmin   = PermissionGuard.isSystemAdmin(editorRole)
                || isDbAdmin
                || Permissions.PERM_ACCESS_ALL_DATA in editorPermissions
                || editorRole == Permissions.ROLE_ADMIN
        val isManager = PermissionGuard.canEditProfile(
            editorRole = editorRole, targetRole = targetRole,
            editorId   = sessionId,  targetId   = userId, isDbAdmin = false
        ) && !isAdmin
        val editingOther = sessionId != userId

        val selfUserToken = if (!editingOther) session?.token ?: "" else ""


        // Per-field permission gate. Fed into ProfileCompletionScreen so each
        // sensitive input renders only when the editor is allowed to write that
        // specific field — own-profile users always get SELF_EDITABLE_FIELDS,
        // admins/sysadmins get ALL_FIELDS, managers get MANAGER_EDITABLE_FIELDS,
        // everyone else gets nothing.
        val editableFields = PermissionGuard.editableFields(
            editorRole        = editorRole,
            targetRole        = targetRole,
            editorId          = sessionId,
            targetId          = userId,
            isDbAdmin         = isDbAdmin,
            editorPermissions = editorPermissions
        )

        // Block only when editing ANOTHER user without permission.
        // Own-profile users are handled by canEditByPermission above and must not be blocked here.
        if (isEditMode && editingOther && !canEdit) {
            Toast.makeText(this,
                "You don't have permission to edit this user", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        // Permission panel: only users who can grant/revoke may see or change permissions.
        // Self-escalation is blocked (cannot edit your own permissions).
        val canManagePermissions = editingOther && (
                isDbAdmin ||
                        PermissionGuard.isSystemAdmin(editorRole) ||
                        (editorRole == Permissions.ROLE_ADMIN && !PermissionGuard.isSystemAdmin(targetRole))
                )

        viewModel.loadUser(userId, isEditMode)
        observeState(userId, isEditMode)

        setContent {
            ITConnectTheme {
                // Read activity-level state bridges into Compose state
                val pickedUri by _pickedUri
                val showCrop  by _showCrop

                // Show crop dialog when a valid URI has been picked
                if (showCrop && pickedUri != null) {
                    ImageCropDialog(
                        sourceUri = pickedUri!!,
                        onCropped = { bytes, filename ->
                            viewModel.setSelectedImageBytes(bytes, filename)
                            _showCrop.value  = false
                            _pickedUri.value = null
                        },
                        onDismiss = {
                            _showCrop.value  = false
                            _pickedUri.value = null
                        }
                    )
                }

                ProfileCompletionScreen(
                    viewModel             = viewModel,
                    onImagePickClick      = {
                        imageLauncher.launch(ELIGIBLE_IMAGE_MIME_TYPES)
                    },
                    onSaveProfile         = { data ->
                        viewModel.saveProfile(
                            userId         = userId,
                            data           = data,
                            imageBytes     = viewModel.uiState.value.pendingImageBytes,
                            isAdmin        = isAdmin,
                            isManager      = isManager && editingOther,
                            editableFields = editableFields,
                            userToken      = selfUserToken
                        )
                    },
                    onSavePermissions     = { updated ->
                        viewModel.updateUserPermissions(
                            targetUserId      = userId,
                            perms             = updated,
                            editorPermissions = editorPermissions
                        )
                    },
                    onNavigateBack        = { finish() },
                    isEditMode            = isEditMode,
                    isAdmin               = isAdmin,
                    isManager             = isManager && editingOther,
                    isSelfEdit            = !editingOther,
                    userId                = userId,
                    editableFields        = editableFields,
                    canManagePermissions  = canManagePermissions,
                    editorPermissions     = editorPermissions
                )
            }
        }
    }

    private fun observeState(userId: String, isEditMode: Boolean) {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state.isSaved) {
                    Toast.makeText(
                        this@ProfileCompletionActivity,
                        "Profile saved!", Toast.LENGTH_SHORT
                    ).show()
                    if (isEditMode) {
                        val result = Intent().apply {
                            putExtra(ManageUserActivity.EXTRA_EDITED_USER_ID, userId)
                        }
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    } else {
                        startActivity(
                            Intent(this@ProfileCompletionActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                        finish()
                    }
                }
            }
        }
    }
}