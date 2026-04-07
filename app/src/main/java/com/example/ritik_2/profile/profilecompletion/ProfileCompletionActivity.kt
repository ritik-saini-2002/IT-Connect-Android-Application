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

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.setSelectedImage(it) } }

    companion object {
        private const val EXTRA_USER_ID        = "user_id"
        private const val EXTRA_EDIT_MODE      = "edit_mode"
        private const val EXTRA_TARGET_ROLE    = "target_role"   // role of the user being edited
        private const val EXTRA_EDITOR_ROLE    = "editor_role"   // role of the person doing the edit

        /**
         * Use this when an admin/manager/HR opens someone else's profile.
         * [targetUserRole] = role of the user being edited (e.g. "Employee")
         * [editorRole]     = role of the logged-in user doing the edit
         */
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

        // Determine actual editor role:
        // prefer the intent argument (set by ManageUserActivity),
        // fall back to session (when the user opens their own profile)
        val sessionRole  = authRepository.getSession()?.role ?: ""
        val sessionId    = authRepository.getSession()?.userId ?: ""
        val editorRole   = editorRoleArg.ifBlank { sessionRole }

        // Permission matrix:
        // Administrator → can edit anyone
        // Manager / HR  → can edit Employee, Intern, Team Lead
        // Others        → can only edit their own profile
        val canEdit = canEditorModifyTarget(
            editorRole = editorRole,
            targetRole = targetRole,
            editorId   = sessionId,
            targetId   = userId
        )

        // isAdmin flag controls which fields are shown as editable in the screen
        // Admins see all fields editable; managers/HR see a restricted set
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
                    onImagePickClick = { imagePicker.launch("image/*") },
                    onSaveProfile    = { data ->
                        val imageBytes = viewModel.uiState.value.selectedImageUri?.let { uri ->
                            runCatching {
                                contentResolver.openInputStream(uri)?.readBytes()
                            }.getOrNull()
                        }
                        // Admins can change everything
                        // Managers/HR can change a limited set (handled in ViewModel)
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

    /**
     * Returns true if [editorRole] is allowed to edit a user with [targetRole].
     *
     * Rules:
     * - Administrator  → edit anyone
     * - Manager / HR   → edit Employee, Intern, Team Lead (not other Managers/HR/Admin)
     * - Anyone         → edit their own profile (editorId == targetId)
     */
    private fun canEditorModifyTarget(
        editorRole: String,
        targetRole: String,
        editorId  : String,
        targetId  : String
    ): Boolean {
        if (editorId == targetId) return true   // always can edit own profile
        return when (editorRole) {
            "Administrator" -> true
            "Manager", "HR" -> targetRole in setOf("Employee", "Intern", "Team Lead")
            else            -> false
        }
    }
}