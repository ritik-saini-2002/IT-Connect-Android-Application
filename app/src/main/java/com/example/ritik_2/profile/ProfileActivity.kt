package com.example.ritik_2.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.pocketbase.PocketBaseClient
import com.example.ritik_2.pocketbase.PocketBaseInitializer.COL_ACCESS_CONTROL
import com.example.ritik_2.pocketbase.PocketBaseInitializer.COL_SEARCH_INDEX
import com.example.ritik_2.pocketbase.PocketBaseInitializer.COL_USERS
import com.example.ritik_2.pocketbase.PocketBaseSessionManager
import com.example.ritik_2.registration.models.AccessControlRecord
import com.example.ritik_2.registration.models.SearchIndexRecord
import com.example.ritik_2.registration.models.UserRecord
import com.example.ritik_2.theme.Ritik_2Theme
import io.github.agrevster.pocketbaseKotlin.FileUpload
import io.github.agrevster.pocketbaseKotlin.toJsonPrimitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ProfileActivity : ComponentActivity() {

    private val pb = PocketBaseClient.instance

    private var profileImageUri   by mutableStateOf<Uri?>(null)
    private var name              by mutableStateOf("Loading...")
    private var email             by mutableStateOf("")
    private var phoneNumber       by mutableStateOf("")
    private var designation       by mutableStateOf("")
    private var companyName       by mutableStateOf("")
    private var role              by mutableStateOf("")
    private var experience        by mutableStateOf(0)
    private var completedProjects by mutableStateOf(0)
    private var activeProjects    by mutableStateOf(0)
    private var isLoading         by mutableStateOf(true)

    private var sanitizedCompanyName by mutableStateOf<String?>(null)

    private val userId get() = PocketBaseSessionManager.getUserId()

    companion object {
        const val TAG = "ProfileActivity"

        fun createIntent(context: Context, userId: String): Intent =
            Intent(context, ProfileActivity::class.java).apply {
                putExtra("userId", userId)
            }
    }

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                profileImageUri = it
                uploadProfilePicture(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PocketBaseSessionManager.init(this)

        Log.d(TAG, "🚀 ProfileActivity started")

        if (userId == null) {
            Log.e(TAG, "❌ No authenticated user found")
            navigateToLogin()
            return
        }

        loadUserProfile()

        setContent {
            Ritik_2Theme {
                ProfileScreen(
                    profileImageUrl   = profileImageUri,
                    userId            = userId ?: "",
                    name              = name,
                    email             = email,
                    phoneNumber       = phoneNumber,
                    designation       = designation,
                    companyName       = companyName,
                    role              = role,
                    experience        = experience,
                    completedProjects = completedProjects,
                    activeProjects    = activeProjects,
                    isLoading         = isLoading,
                    onLogoutClick     = { logoutUser() },
                    onEditClick       = { field, newValue -> updateUserData(field, newValue) },
                    onChangeProfilePic = { imagePicker.launch("image/*") },
                    onBackClick       = { finish() }
                )
            }
        }
    }

    // ── Load profile ──────────────────────────────────────────
    private fun loadUserProfile() {
        val uid = userId ?: return
        Log.d(TAG, "📊 Loading user profile for: $uid")
        isLoading = true

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. Fetch ALL access control records, then filter in-memory
                //    This avoids any Filter DSL issues with the SDK version
                val accessResult = withContext(Dispatchers.IO) {
                    pb.records.getList<AccessControlRecord>(
                        COL_ACCESS_CONTROL,
                        page     = 1,
                        perPage  = 200   // fetch enough to find the user
                    )
                }

                val access = accessResult.items.firstOrNull { it.userId == uid }

                if (access == null) {
                    Log.e(TAG, "❌ User access control not found for uid: $uid")
                    showToast("User access not found")
                    isLoading = false
                    return@launch
                }

                role                 = access.role
                sanitizedCompanyName = access.sanitizedCompanyName

                if (!access.isActive) {
                    showToast("Your account is deactivated")
                    logoutUser()
                    return@launch
                }

                // 2. Get main user record
                val userRecord = withContext(Dispatchers.IO) {
                    pb.records.getOne<UserRecord>(COL_USERS, uid)
                }

                name        = userRecord.name.ifBlank { "Unknown" }
                email       = PocketBaseSessionManager.getEmail() ?: ""
                designation = userRecord.designation
                companyName = userRecord.companyName

                // 3. Parse profile JSON
                userRecord.profile.takeIf { it.isNotBlank() && it != "{}" }?.let { profileJson ->
                    phoneNumber = extractString(profileJson, "phoneNumber")
                    val imageUrl = extractString(profileJson, "imageUrl")
                    if (imageUrl.isNotEmpty()) {
                        profileImageUri = Uri.parse(imageUrl)
                    }
                }

                // 4. Parse workStats JSON
                userRecord.workStats.takeIf { it.isNotBlank() && it != "{}" }?.let { workJson ->
                    experience        = extractInt(workJson, "experience")
                    completedProjects = extractInt(workJson, "completedProjects")
                    activeProjects    = extractInt(workJson, "activeProjects")
                }

                Log.d(TAG, "✅ User profile loaded: $name")
                isLoading = false

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading profile", e)
                showToast("Failed to load profile: ${e.message}")
                isLoading = false
            }
        }
    }

    // ── Update user data ──────────────────────────────────────
    private fun updateUserData(field: String, newValue: String) {
        val uid = userId ?: return
        Log.d(TAG, "📝 Updating field: $field")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    when (field) {
                        "name", "designation" -> {
                            pb.records.update<UserRecord>(
                                COL_USERS, uid,
                                body = mapOf(field to newValue.toJsonPrimitive()).toString()
                            )
                            if (field == "name") {
                                syncNameToAccessControl(uid, newValue)
                                syncNameToSearchIndex(uid, newValue)
                            }
                        }

                        "phoneNumber" -> {
                            val current = pb.records.getOne<UserRecord>(COL_USERS, uid)
                            val updated = mergeJsonString(
                                current.profile, "phoneNumber", newValue
                            )
                            pb.records.update<UserRecord>(
                                COL_USERS, uid,
                                body = mapOf("profile" to updated.toJsonPrimitive()).toString()
                            )
                        }

                        "experience", "completedProjects", "activeProjects" -> {
                            val intVal  = newValue.toIntOrNull() ?: 0
                            val current = pb.records.getOne<UserRecord>(COL_USERS, uid)
                            val updated = mergeJsonInt(current.workStats, field, intVal)
                            pb.records.update<UserRecord>(
                                COL_USERS, uid,
                                body = mapOf("workStats" to updated.toJsonPrimitive()).toString()
                            )
                        }
                    }
                }

                // Update local state
                when (field) {
                    "name"              -> name = newValue
                    "designation"       -> designation = newValue
                    "phoneNumber"       -> phoneNumber = newValue
                    "experience"        -> experience = newValue.toIntOrNull() ?: 0
                    "completedProjects" -> completedProjects = newValue.toIntOrNull() ?: 0
                    "activeProjects"    -> activeProjects = newValue.toIntOrNull() ?: 0
                }

                showToast("$field updated successfully!")
                Log.d(TAG, "✅ Updated $field")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to update $field", e)
                showToast("Failed to update $field: ${e.message}")
            }
        }
    }

    // ── Sync helpers (in-memory filter) ──────────────────────
    private suspend fun syncNameToAccessControl(uid: String, newName: String) {
        try {
            val result = pb.records.getList<AccessControlRecord>(
                COL_ACCESS_CONTROL, page = 1, perPage = 200
            )
            val record = result.items.firstOrNull { it.userId == uid } ?: return
            val id = record.id ?: return

            pb.records.update<AccessControlRecord>(
                COL_ACCESS_CONTROL, id,
                body = mapOf("name" to newName.toJsonPrimitive()).toString()
            )
            Log.d(TAG, "✅ Name synced to access control")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to sync name to access control: ${e.message}")
        }
    }

    private suspend fun syncNameToSearchIndex(uid: String, newName: String) {
        try {
            val result = pb.records.getList<SearchIndexRecord>(
                COL_SEARCH_INDEX, page = 1, perPage = 200
            )
            val record = result.items.firstOrNull { it.userId == uid } ?: return
            val id = record.id ?: return

            pb.records.update<SearchIndexRecord>(
                COL_SEARCH_INDEX, id,
                body = mapOf("name" to newName.lowercase().toJsonPrimitive()).toString()
            )
            Log.d(TAG, "✅ Name synced to search index")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to sync name to search index: ${e.message}")
        }
    }

    // ── Upload profile picture ────────────────────────────────
    private fun uploadProfilePicture(imageUri: Uri) {
        val uid = userId ?: return
        Log.d(TAG, "📸 Uploading profile picture")
        showToast("Uploading image...")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val downloadUrl = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(imageUri)
                    val tempFile    = File(cacheDir, "profile_$uid.jpg")
                    FileOutputStream(tempFile).use { out -> inputStream?.copyTo(out) }
                    inputStream?.close()

                    pb.records.update<UserRecord>(
                        COL_USERS, uid,
                        body  = mapOf("name" to uid.toJsonPrimitive()),
                        files = listOf(
                            FileUpload("profileImage", tempFile.readBytes(), "profile_$uid.jpg")
                        )
                    )

                    "${PocketBaseClient.BASE_URL}/api/files/$COL_USERS/$uid/profile_$uid.jpg"
                }

                profileImageUri = Uri.parse(downloadUrl)
                showToast("Profile picture updated successfully!")
                Log.d(TAG, "✅ Profile picture uploaded")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to upload image", e)
                showToast("Failed to upload image: ${e.message}")
            }
        }
    }

    // ── Auth ──────────────────────────────────────────────────
    private fun logoutUser() {
        Log.d(TAG, "🚪 User logging out")
        PocketBaseSessionManager.clearSession()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        Log.d(TAG, "🔙 Back pressed")
        finish()
    }

    // ── JSON helpers ──────────────────────────────────────────
    private fun extractString(json: String, key: String): String =
        Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1) ?: ""

    private fun extractInt(json: String, key: String): Int =
        Regex(""""$key"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /**
     * Replaces or inserts a string value in a JSON string.
     * e.g. mergeJsonString("""{"phoneNumber":"old"}""", "phoneNumber", "new")
     *      → """{"phoneNumber":"new"}"""
     */
    private fun mergeJsonString(json: String, key: String, value: String): String {
        val pattern = Regex(""""$key"\s*:\s*"[^"]*"""")
        return if (pattern.containsMatchIn(json)) {
            pattern.replace(json, """"$key":"$value"""")
        } else {
            // Insert before closing brace
            json.trimEnd().removeSuffix("}") + """"$key":"$value"}"""
        }
    }

    /**
     * Replaces or inserts an int value in a JSON string.
     */
    private fun mergeJsonInt(json: String, key: String, value: Int): String {
        val pattern = Regex(""""$key"\s*:\s*\d+""")
        return if (pattern.containsMatchIn(json)) {
            pattern.replace(json, """"$key":$value""")
        } else {
            json.trimEnd().removeSuffix("}") + """"$key":$value}"""
        }
    }
}