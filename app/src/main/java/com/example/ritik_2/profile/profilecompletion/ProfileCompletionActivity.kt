package com.example.ritik_2.profile.profilecompletion

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
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileCompletionActivity : ComponentActivity() {

    private val viewModel: ProfileCompletionViewModel by viewModels()

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.setSelectedImage(it) } }

    companion object {
        private const val EXTRA_USER_ID   = "user_id"
        private const val EXTRA_EDIT_MODE = "edit_mode"

        fun createIntent(context: Context, userId: String, isEditMode: Boolean = false) =
            Intent(context, ProfileCompletionActivity::class.java).apply {
                putExtra(EXTRA_USER_ID,   userId)
                putExtra(EXTRA_EDIT_MODE, isEditMode)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId     = intent.getStringExtra(EXTRA_USER_ID) ?: ""
        val isEditMode = intent.getBooleanExtra(EXTRA_EDIT_MODE, false)

        if (userId.isBlank()) {
            Toast.makeText(this, "Invalid user session", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        viewModel.loadUser(userId)
        observeState()

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
                        viewModel.saveProfile(userId, data, imageBytes)
                    },
                    onNavigateBack   = { finish() },
                    isEditMode       = isEditMode,
                    userId           = userId
                )
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state.isSaved) {
                    Toast.makeText(this@ProfileCompletionActivity,
                        "Profile saved!", Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this@ProfileCompletionActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                    finish()
                }
            }
        }
    }
}