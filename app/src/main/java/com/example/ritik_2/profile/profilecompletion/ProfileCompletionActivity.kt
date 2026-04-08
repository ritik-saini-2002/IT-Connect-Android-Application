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

        val session     = authRepository.getSession()
        val sessionRole = session?.role  ?: ""
        val sessionId   = session?.userId ?: ""
        val isDbAdmin   = authRepository.isDbAdmin()
        val editorRole  = editorRoleArg.ifBlank { sessionRole }

        val canEdit = PermissionGuard.canEditProfile(
            editorRole = editorRole,
            targetRole = targetRole,
            editorId   = sessionId,
            targetId   = userId,
            isDbAdmin  = isDbAdmin
        )

        val isAdmin      = editorRole == "Administrator" || isDbAdmin
        val isManager    = editorRole in setOf("Manager", "HR") &&
                targetRole in setOf("Employee", "Intern", "Team Lead")
        val editingOther = sessionId != userId

        if (isEditMode && editingOther && !canEdit) {
            Toast.makeText(this,
                "You don't have permission to edit this user", Toast.LENGTH_SHORT).show()
            finish(); return
        }

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
                    viewModel        = viewModel,
                    onImagePickClick = {
                        // Launch picker with allowed MIME types only
                        imageLauncher.launch(ELIGIBLE_IMAGE_MIME_TYPES)
                    },
                    onSaveProfile    = { data ->
                        viewModel.saveProfile(
                            userId     = userId,
                            data       = data,
                            imageBytes = viewModel.uiState.value.pendingImageBytes,
                            isAdmin    = isAdmin,
                            isManager  = isManager && editingOther
                        )
                    },
                    onNavigateBack   = { finish() },
                    isEditMode       = isEditMode,
                    isAdmin          = isAdmin,
                    isManager        = isManager && editingOther,
                    userId           = userId
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