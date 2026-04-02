package com.example.ritik_2.profile.profilecompletion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.pocketbase.SessionManager
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProfileCompletionActivity : ComponentActivity() {

    private val viewModel: ProfileCompletionViewModel by viewModels()
    @Inject lateinit var sessionManager: SessionManager

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.setSelectedImage(it) } }

    companion object {
        const val EXTRA_USER_ID      = "user_id"
        const val EXTRA_IS_EDIT_MODE = "is_edit_mode"

        fun createIntent(context: Context, userId: String, isEditMode: Boolean = false) =
            Intent(context, ProfileCompletionActivity::class.java).apply {
                putExtra(EXTRA_USER_ID,      userId)
                putExtra(EXTRA_IS_EDIT_MODE, isEditMode)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId     = intent.getStringExtra(EXTRA_USER_ID)
            ?: sessionManager.get()?.userId ?: ""
        val isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT_MODE, false)

        if (userId.isNotEmpty()) {
            viewModel.loadUser(userId)
        } else {
            Toast.makeText(this, "User ID not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state.isSaved) {
                    Toast.makeText(this@ProfileCompletionActivity,
                        "Profile saved! ✅", Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this@ProfileCompletionActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                    finish()
                }
                state.error?.let { err ->
                    Toast.makeText(this@ProfileCompletionActivity, err, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }

        setContent {
            ITConnectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    ProfileCompletionScreen(
                        viewModel        = viewModel,
                        onImagePickClick = { imagePickerLauncher.launch("image/*") },
                        onSaveProfile    = { data ->
                            // Read image bytes only if user picked a new image
                            val imageBytes = viewModel.uiState.value.selectedImageUri?.let { uri ->
                                try { contentResolver.openInputStream(uri)?.readBytes() }
                                catch (_: Exception) { null }
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
    }
}