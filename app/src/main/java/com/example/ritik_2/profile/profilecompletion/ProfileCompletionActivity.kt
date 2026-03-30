package com.example.ritik_2.profile.profilecompletion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.data.pocketbase.PocketBaseClient
import com.example.ritik_2.data.pocketbase.PocketBaseSessionManager
import com.example.ritik_2.data.pocketbase.PocketBaseInitializer
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.pocketbase.PocketBaseClient
import com.example.ritik_2.profile.profilecompletion.components.ProfileCompletionViewModel
import com.example.ritik_2.profile.profilecompletion.components.ProfileData
import com.example.ritik_2.theme.Ritik_2Theme
import com.example.ritik_2.registration.models.UserRecord
import io.github.agrevster.pocketbaseKotlin.FileUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ProfileCompletionActivity : ComponentActivity() {

    private val pb = PocketBaseClient.instance
    private lateinit var viewModel: ProfileCompletionViewModel

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.setSelectedImageUri(it) } }

    companion object {
        const val EXTRA_USER_ID     = "user_id"
        const val EXTRA_IS_EDIT_MODE = "is_edit_mode"

        fun createIntent(context: Context, userId: String, isEditMode: Boolean = false) =
            Intent(context, ProfileCompletionActivity::class.java).apply {
                putExtra(EXTRA_USER_ID,      userId)
                putExtra(EXTRA_IS_EDIT_MODE, isEditMode)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PocketBaseSessionManager.init(this)

        viewModel = ViewModelProvider(this)[ProfileCompletionViewModel::class.java]

        val userId     = intent.getStringExtra(EXTRA_USER_ID)
            ?: PocketBaseSessionManager.getUserId()
            ?: ""
        val isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT_MODE, false)

        if (userId.isNotEmpty()) {
            loadUserData(userId)
        } else {
            viewModel.setError("User ID not found")
        }

        setContent {
            Ritik_2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProfileCompletionScreen(
                        viewModel       = viewModel,
                        onImagePickClick = { imagePickerLauncher.launch("image/*") },
                        onSaveProfile   = { profileData -> saveProfile(userId, profileData) },
                        onNavigateBack  = { finish() },
                        isEditMode      = isEditMode,
                        userId          = userId
                    )
                }
            }
        }
    }

    // ── Load existing user data ────────────────────────────────
    private fun loadUserData(userId: String) {
        lifecycleScope.launch {
            try {
                viewModel.setLoading(true)
                viewModel.setError(null)

                val userRecord = withContext(Dispatchers.IO) {
                    pb.records.getOne<UserRecord>("users", userId)
                }

                viewModel.initializeFromPocketBase(
                    userId      = userId,
                    name        = userRecord.name,
                    email       = PocketBaseSessionManager.getEmail() ?: "",
                    role        = userRecord.role,
                    companyName = userRecord.companyName,
                    department  = userRecord.department,
                    designation = userRecord.designation,
                    profileJson = userRecord.profile,
                    workJson    = userRecord.workStats
                )

                viewModel.setLoading(false)
                Log.d("ProfileCompletion", "User data loaded ✅")

            } catch (e: Exception) {
                viewModel.setLoading(false)
                Log.e("ProfileCompletion", "Load error: ${e.message}", e)

                // Initialize empty profile — user fills it in
                viewModel.initializeNewUser(
                    userId      = userId,
                    email       = PocketBaseSessionManager.getEmail() ?: "",
                    displayName = PocketBaseSessionManager.getName()
                )
                Toast.makeText(this@ProfileCompletionActivity,
                    "Please complete your profile.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Save profile ──────────────────────────────────────────
    private fun saveProfile(userId: String, profileData: ProfileData) {
        lifecycleScope.launch {
            try {
                viewModel.setLoading(true)
                viewModel.setError(null)

                if (profileData.name.isBlank()) {
                    viewModel.setError("Name is required"); viewModel.setLoading(false); return@launch
                }
                if (profileData.companyName.isBlank()) {
                    viewModel.setError("Company is required"); viewModel.setLoading(false); return@launch
                }
                if (profileData.department.isBlank()) {
                    viewModel.setError("Department is required"); viewModel.setLoading(false); return@launch
                }

                // Upload image if selected
                var imageUrl = profileData.imageUrl
                profileData.imageUri?.let { uri ->
                    imageUrl = uploadImage(userId, uri)
                }

                // Build profile JSON
                val profileJson = """
                {
                    "imageUrl":"$imageUrl",
                    "phoneNumber":"${profileData.phoneNumber}",
                    "address":"${profileData.address}",
                    "employeeId":"${profileData.employeeId}",
                    "reportingTo":"${profileData.reportingTo}",
                    "salary":${profileData.salary},
                    "emergencyContact":{
                        "name":"${profileData.emergencyContactName}",
                        "phone":"${profileData.emergencyContactPhone}",
                        "relation":"${profileData.emergencyContactRelation}"
                    }
                }
                """.trimIndent()

                val workJson = """
                {
                    "experience":${profileData.experience},
                    "completedProjects":0,
                    "activeProjects":0,
                    "pendingTasks":0,
                    "completedTasks":0,
                    "totalWorkingHours":0,
                    "avgPerformanceRating":0.0
                }
                """.trimIndent()

                withContext(Dispatchers.IO) {
                    // 1. Update user record
                    pb.records.update<UserRecord>(
                        "users", userId,
                        """
                        {
                            "name":"${profileData.name}",
                            "designation":"${profileData.designation}",
                            "profile":$profileJson,
                            "workStats":$workJson
                        }
                        """.trimIndent()
                    )

                    // 2. Update access control name + designation
                    val accessResult = pb.records.getList<com.example.ritik_2.main.AccessRecord>(
                        "user_access_control", 1, 1, "userId='$userId'"
                    )
                    if (accessResult.totalItems > 0) {
                        val accessId = accessResult.items.first().id!!
                        pb.records.update<com.example.ritik_2.main.AccessRecord>(
                            "user_access_control", accessId,
                            """{"name":"${profileData.name}"}"""
                        )
                    }

                    // 3. Update search index
                    val searchResult = pb.records.getList<com.example.ritik_2.main.AccessRecord>(
                        "user_search_index", 1, 1, "userId='$userId'"
                    )
                    if (searchResult.totalItems > 0) {
                        val searchId = searchResult.items.first().id!!
                        pb.records.update<com.example.ritik_2.main.AccessRecord>(
                            "user_search_index", searchId,
                            """{"name":"${profileData.name.lowercase()}","designation":"${profileData.designation}"}"""
                        )
                    }
                }

                viewModel.setLoading(false)
                Toast.makeText(this@ProfileCompletionActivity,
                    "Profile saved successfully! ✅", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)

                // Navigate to main
                startActivity(
                    Intent(this@ProfileCompletionActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()

            } catch (e: Exception) {
                viewModel.setLoading(false)
                viewModel.setError("Error saving profile: ${e.message}")
                Log.e("ProfileCompletion", "Save error", e)
                Toast.makeText(this@ProfileCompletionActivity,
                    "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Upload profile image ───────────────────────────────────
    private suspend fun uploadImage(userId: String, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream  = contentResolver.openInputStream(uri)
                val tempFile     = File(cacheDir, "profile_$userId.jpg")
                FileOutputStream(tempFile).use { out -> inputStream?.copyTo(out) }
                inputStream?.close()

                pb.records.update<UserRecord>(
                    "users", userId,
                    "{}",
                    files = listOf(
                        FileUpload("profileImage", tempFile.readBytes(), "profile_$userId.jpg")
                    )
                )

                "${PocketBaseClient.BASE_URL}/api/files/users/$userId/profile_$userId.jpg"
                    .also { Log.d("ProfileCompletion", "Image uploaded ✅") }

            } catch (e: Exception) {
                Log.e("ProfileCompletion", "Image upload failed: ${e.message}")
                ""
            }
        }
    }
}