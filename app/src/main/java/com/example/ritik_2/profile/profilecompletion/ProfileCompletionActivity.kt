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
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.pocketbase.PocketBaseClient
import com.example.ritik_2.pocketbase.PocketBaseInitializer.COL_ACCESS_CONTROL
import com.example.ritik_2.pocketbase.PocketBaseInitializer.COL_SEARCH_INDEX
import com.example.ritik_2.pocketbase.PocketBaseInitializer.COL_USERS
import com.example.ritik_2.pocketbase.PocketBaseSessionManager
import com.example.ritik_2.profile.profilecompletion.components.ProfileCompletionViewModel
import com.example.ritik_2.profile.profilecompletion.components.ProfileData
import com.example.ritik_2.registration.models.AccessControlRecord
import com.example.ritik_2.registration.models.SearchIndexRecord
import com.example.ritik_2.registration.models.UserRecord
import com.example.ritik_2.theme.Ritik_2Theme
import io.github.agrevster.pocketbaseKotlin.FileUpload
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import io.github.agrevster.pocketbaseKotlin.toJsonPrimitive
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
        PocketBaseSessionManager.init(this)

        viewModel = ViewModelProvider(this)[ProfileCompletionViewModel::class.java]

        val userId     = intent.getStringExtra(EXTRA_USER_ID)
            ?: PocketBaseSessionManager.getUserId() ?: ""
        val isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT_MODE, false)

        if (userId.isNotEmpty()) loadUserData(userId)
        else viewModel.setError("User ID not found")

        setContent {
            Ritik_2Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ProfileCompletionScreen(
                        viewModel        = viewModel,
                        onImagePickClick = { imagePickerLauncher.launch("image/*") },
                        onSaveProfile    = { profileData -> saveProfile(userId, profileData) },
                        onNavigateBack   = { finish() },
                        isEditMode       = isEditMode,
                        userId           = userId
                    )
                }
            }
        }
    }

    // ── Load existing data ────────────────────────────────────
    private fun loadUserData(userId: String) {
        lifecycleScope.launch {
            try {
                viewModel.setLoading(true)

                val userRecord = withContext(Dispatchers.IO) {
                    pb.records.getOne<UserRecord>(COL_USERS, userId)
                }

                viewModel.initializeFromPocketBase(
                    userId      = userId,
                    name        = userRecord.name,
                    email       = PocketBaseSessionManager.getEmail() ?: "",
                    role        = userRecord.role.ifBlank { "Administrator" },
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
                Log.e("ProfileCompletion", "Load error: ${e.message}")
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

                if (profileData.name.isBlank())        { viewModel.setError("Name is required");        viewModel.setLoading(false); return@launch }
                if (profileData.companyName.isBlank()) { viewModel.setError("Company is required");     viewModel.setLoading(false); return@launch }
                if (profileData.department.isBlank())  { viewModel.setError("Department is required");  viewModel.setLoading(false); return@launch }
                if (profileData.designation.isBlank()) { viewModel.setError("Designation is required"); viewModel.setLoading(false); return@launch }

                // Upload image
                var imageUrl = profileData.imageUrl
                profileData.imageUri?.let { uri ->
                    imageUrl = uploadImage(userId, uri)
                }

                val profileJson = buildProfileJson(imageUrl, profileData)
                val workJson    = """{"experience":${profileData.experience},"completedProjects":0,"activeProjects":0,"pendingTasks":0,"completedTasks":0,"totalWorkingHours":0,"avgPerformanceRating":0.0}"""

                withContext(Dispatchers.IO) {

                    // 1. Update user record
                    pb.records.update<UserRecord>(
                        COL_USERS, userId,
                        body = mapOf(
                            "name"        to profileData.name.toJsonPrimitive(),
                            "designation" to profileData.designation.toJsonPrimitive(),
                            "profile"     to profileJson.toJsonPrimitive(),
                            "workStats"   to workJson.toJsonPrimitive()
                        ).toString()
                    )

                    // 2. Update access control
                    try {
                        val accessResult = pb.records.getList<AccessControlRecord>(
                            COL_ACCESS_CONTROL, 1, 1,
                            //filter = Filter("userId='$userId'")
                        )
                        if (accessResult.totalItems > 0) {
                            val accessId = accessResult.items.first().id!!
                            pb.records.update<AccessControlRecord>(
                                COL_ACCESS_CONTROL, accessId,
                                body = mapOf("name" to profileData.name.toJsonPrimitive()).toString()
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("ProfileCompletion", "Access control update skipped: ${e.message}")
                    }

                    // 3. Update search index
                    try {
                        val searchResult = pb.records.getList<SearchIndexRecord>(
                            COL_SEARCH_INDEX, 1, 1,
                            //filter = Filter("userId='$userId'")
                        )
                        if (searchResult.totalItems > 0) {
                            val searchId = searchResult.items.first().id!!
                            pb.records.update<SearchIndexRecord>(
                                COL_SEARCH_INDEX, searchId,
                                body = mapOf(
                                    "name"        to profileData.name.lowercase().toJsonPrimitive(),
                                    "designation" to profileData.designation.toJsonPrimitive()
                                ).toString()
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("ProfileCompletion", "Search index update skipped: ${e.message}")
                    }
                }

                viewModel.setLoading(false)
                Toast.makeText(this@ProfileCompletionActivity,
                    "Profile saved successfully! ✅", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)

                startActivity(Intent(this@ProfileCompletionActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()

            } catch (e: Exception) {
                viewModel.setLoading(false)
                viewModel.setError("Error: ${e.message}")
                Log.e("ProfileCompletion", "Save error", e)
                Toast.makeText(this@ProfileCompletionActivity,
                    "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun uploadImage(userId: String, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val tempFile    = File(cacheDir, "profile_$userId.jpg")
                FileOutputStream(tempFile).use { out -> inputStream?.copyTo(out) }
                inputStream?.close()

                pb.records.update<UserRecord>(
                    COL_USERS, userId,
                    body  = mapOf("name" to userId.toJsonPrimitive()),
                    files = listOf(FileUpload("profileImage", tempFile.readBytes(), "profile_$userId.jpg"))
                )

                "${PocketBaseClient.BASE_URL}/api/files/$COL_USERS/$userId/profile_$userId.jpg"
            } catch (e: Exception) {
                Log.e("ProfileCompletion", "Image upload failed: ${e.message}")
                ""
            }
        }
    }

    private fun buildProfileJson(imageUrl: String, data: ProfileData) = """
        {
            "imageUrl":"$imageUrl",
            "phoneNumber":"${data.phoneNumber}",
            "address":"${data.address}",
            "employeeId":"${data.employeeId}",
            "reportingTo":"${data.reportingTo}",
            "salary":${data.salary},
            "emergencyContact":{
                "name":"${data.emergencyContactName}",
                "phone":"${data.emergencyContactPhone}",
                "relation":"${data.emergencyContactRelation}"
            }
        }
    """.trimIndent()
}