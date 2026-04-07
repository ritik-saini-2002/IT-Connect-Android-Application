package com.example.ritik_2.profile.profilecompletion

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.administrator.manageuser.ManageUserActivity
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProfileCompletionActivity : ComponentActivity() {

    private val viewModel: ProfileCompletionViewModel by viewModels()

    @Inject lateinit var authRepository: AuthRepository

    // Step 1 — pick image from gallery
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Go to crop screen
            cropLauncher.launch(ImageCropActivity.createIntent(this, it))
        }
    }

    // Step 2 — receive cropped image from ImageCropActivity
    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uriStr = result.data?.getStringExtra(ImageCropActivity.EXTRA_RESULT_URI)
            uriStr?.toUri()?.let { croppedUri ->
                viewModel.setCroppedImage(croppedUri)
            }
        }
    }

    companion object {
        private const val EXTRA_USER_ID     = "user_id"
        private const val EXTRA_EDIT_MODE   = "edit_mode"
        private const val EXTRA_TARGET_ROLE = "target_role"
        private const val EXTRA_EDITOR_ROLE = "editor_role"

        fun createIntent(
            context       : Context,
            userId        : String,
            isEditMode    : Boolean = false,
            targetUserRole: String  = "",
            editorRole    : String  = ""
        ) = Intent(context, ProfileCompletionActivity::class.java).apply {
            putExtra(EXTRA_USER_ID,     userId)
            putExtra(EXTRA_EDIT_MODE,   isEditMode)
            putExtra(EXTRA_TARGET_ROLE, targetUserRole)
            putExtra(EXTRA_EDITOR_ROLE, editorRole)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId        = intent.getStringExtra(EXTRA_USER_ID)    ?: ""
        val isEditMode    = intent.getBooleanExtra(EXTRA_EDIT_MODE,  false)
        val targetRole    = intent.getStringExtra(EXTRA_TARGET_ROLE) ?: ""
        val editorRoleArg = intent.getStringExtra(EXTRA_EDITOR_ROLE) ?: ""

        if (userId.isBlank()) {
            Toast.makeText(this, "Invalid user session", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val sessionRole = authRepository.getSession()?.role    ?: ""
        val sessionId   = authRepository.getSession()?.userId  ?: ""
        val editorRole  = editorRoleArg.ifBlank { sessionRole }

        val canEdit = canEditorModifyTarget(
            editorRole = editorRole,
            targetRole = targetRole,
            editorId   = sessionId,
            targetId   = userId
        )

        val isAdmin      = editorRole == "Administrator"
        val isManager    = editorRole in setOf("Manager", "HR")
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
                ProfileCompletionScreen(
                    viewModel        = viewModel,
                    onImagePickClick = { imagePicker.launch("image/*") },  // triggers pick → crop
                    onSaveProfile    = { data ->
                        // Use croppedImageUri if available, else selectedImageUri
                        val imageUri = viewModel.uiState.value.croppedImageUri
                            ?: viewModel.uiState.value.selectedImageUri
                        val imageBytes = imageUri?.let { uri ->
                            runCatching {
                                contentResolver.openInputStream(uri)?.readBytes()
                            }.getOrNull()
                        }
                        viewModel.saveProfile(
                            userId     = userId,
                            data       = data,
                            imageBytes = imageBytes,
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
                    Toast.makeText(this@ProfileCompletionActivity,
                        "Profile saved!", Toast.LENGTH_SHORT).show()
                    if (isEditMode) {
                        setResult(Activity.RESULT_OK,
                            Intent().apply {
                                putExtra(ManageUserActivity.EXTRA_EDITED_USER_ID, userId)
                            })
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

    private fun canEditorModifyTarget(
        editorRole: String, targetRole: String,
        editorId: String,   targetId: String
    ): Boolean {
        if (editorId == targetId) return true
        return when (editorRole) {
            "Administrator" -> true
            "Manager", "HR" -> targetRole in setOf("Employee", "Intern", "Team Lead")
            else            -> false
        }
    }
}