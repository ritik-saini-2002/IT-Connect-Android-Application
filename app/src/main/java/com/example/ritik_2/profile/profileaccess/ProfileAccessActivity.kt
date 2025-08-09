package com.example.ritik_2.profile.profileaccess

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.authentication.AuthManager
import com.example.ritik_2.authentication.AuthState
import com.example.ritik_2.authentication.UserData
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.theme.Ritik_2Theme
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileAccessActivity : ComponentActivity() {

    companion object {
        const val TAG = "ProfileAccess"
        const val EXTRA_TARGET_USER_ID = "extra_target_user_id"
        const val EXTRA_TARGET_USER_NAME = "extra_target_user_name"

        fun createIntent(context: Context, targetUserId: String, targetUserName: String = ""): Intent {
            return Intent(context, ProfileAccessActivity::class.java).apply {
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
                putExtra(EXTRA_TARGET_USER_NAME, targetUserName)
            }
        }
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val authManager = AuthManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val targetUserId = intent.getStringExtra(EXTRA_TARGET_USER_ID) ?: ""
            val targetUserName = intent.getStringExtra(EXTRA_TARGET_USER_NAME) ?: ""

            if (targetUserId.isEmpty()) {
                Log.e(TAG, "❌ No target user ID provided")
                handleError("Invalid user ID")
                return
            }

            Log.d(TAG, "✅ ProfileAccessActivity started")
            Log.d(TAG, "   - Target User: $targetUserId")
            Log.d(TAG, "   - Target Name: $targetUserName")

            verifyCurrentUserAuthentication(targetUserId, targetUserName)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onCreate", e)
            Toast.makeText(this, "Error starting profile access: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun verifyCurrentUserAuthentication(targetUserId: String, targetUserName: String) {
        lifecycleScope.launch {
            try {
                // Check current user's authentication and permissions
                val currentUserAuthState = authManager.checkAuthenticationState()
                Log.d(TAG, "📊 Current user auth state: $currentUserAuthState")

                when (currentUserAuthState) {
                    is AuthState.Authenticated -> {
                        val currentUser = currentUserAuthState.user
                        Log.d(TAG, "✅ Current user authenticated:")
                        Log.d(TAG, "   - Current User ID: ${currentUser.uid}")
                        Log.d(TAG, "   - Current User Role: ${currentUser.role}")
                        Log.d(TAG, "   - Current User Name: ${currentUser.name}")

                        // Check if current user has permission to access target user's profile
                        val canAccess = checkAccessPermissions(currentUser.role, currentUser.uid, targetUserId)

                        if (canAccess.isAllowed) {
                            Log.d(TAG, "✅ Access granted with level: ${canAccess.accessLevel}")
                            loadTargetUserProfile(targetUserId, targetUserName, currentUser, canAccess)
                        } else {
                            Log.w(TAG, "❌ Access denied: ${canAccess.reason}")
                            handleAccessDenied(canAccess.reason)
                        }
                    }
                    is AuthState.NotAuthenticated -> {
                        Log.e(TAG, "❌ Current user not authenticated")
                        handleAuthError("Please log in to continue")
                    }
                    is AuthState.Error -> {
                        Log.e(TAG, "❌ Current user authentication error: ${currentUserAuthState.message}")
                        handleAuthError("Authentication error: ${currentUserAuthState.message}")
                    }
                    is AuthState.Loading -> {
                        Log.d(TAG, "⏳ Auth state loading...")
                        delay(1000)
                        verifyCurrentUserAuthentication(targetUserId, targetUserName)
                    }
                    else -> {
                        Log.e(TAG, "❌ Unexpected auth state: $currentUserAuthState")
                        handleAuthError("Authentication verification failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error verifying current user authentication", e)
                handleAuthError("Session verification failed")
            }
        }
    }

    private suspend fun checkAccessPermissions(
        currentUserRole: String,
        currentUserId: String,
        targetUserId: String
    ): AccessPermission {
        return try {
            // Self-access is always allowed
            if (currentUserId == targetUserId) {
                return AccessPermission(
                    isAllowed = true,
                    accessLevel = AccessLevel.FULL_ACCESS,
                    reason = "Self-profile access"
                )
            }

            // Get current user's detailed permissions
            val currentUserDoc = firestore.collection("user_access_control")
                .document(currentUserId)
                .get()
                .await()

            // Get target user's details
            val targetUserDoc = firestore.collection("user_access_control")
                .document(targetUserId)
                .get()
                .await()

            if (!currentUserDoc.exists()) {
                return AccessPermission(false, AccessLevel.NO_ACCESS, "Current user not found in system")
            }

            if (!targetUserDoc.exists()) {
                return AccessPermission(false, AccessLevel.NO_ACCESS, "Target user not found in system")
            }

            val currentUserData = currentUserDoc.data!!
            val targetUserData = targetUserDoc.data!!

            val currentUserPermissions = currentUserData["permissions"] as? List<String> ?: emptyList()
            val currentUserCompany = currentUserData["companyName"]?.toString() ?: ""
            val currentUserDepartment = currentUserData["department"]?.toString() ?: ""
            val targetUserCompany = targetUserData["companyName"]?.toString() ?: ""
            val targetUserDepartment = targetUserData["department"]?.toString() ?: ""
            val targetUserRole = targetUserData["role"]?.toString() ?: ""

            Log.d(TAG, "🔍 Permission Check:")
            Log.d(TAG, "   - Current Role: $currentUserRole")
            Log.d(TAG, "   - Current Permissions: $currentUserPermissions")
            Log.d(TAG, "   - Current Company: $currentUserCompany")
            Log.d(TAG, "   - Target Company: $targetUserCompany")
            Log.d(TAG, "   - Target Role: $targetUserRole")

            // Permission logic based on role hierarchy
            when (currentUserRole) {
                "Administrator" -> {
                    // Administrators can access anyone's profile with full permissions
                    AccessPermission(
                        isAllowed = true,
                        accessLevel = AccessLevel.FULL_ACCESS,
                        reason = "Administrator full access"
                    )
                }
                "Manager" -> {
                    when {
                        // Managers can fully access their company users
                        currentUserCompany == targetUserCompany -> AccessPermission(
                            isAllowed = true,
                            accessLevel = AccessLevel.COMPANY_ACCESS,
                            reason = "Manager company access"
                        )
                        else -> AccessPermission(
                            false,
                            AccessLevel.NO_ACCESS,
                            "Manager can only access users from same company"
                        )
                    }
                }
                "HR" -> {
                    when {
                        // HR can access all users in their company
                        currentUserCompany == targetUserCompany -> AccessPermission(
                            isAllowed = true,
                            accessLevel = AccessLevel.HR_ACCESS,
                            reason = "HR company access"
                        )
                        else -> AccessPermission(
                            false,
                            AccessLevel.NO_ACCESS,
                            "HR can only access users from same company"
                        )
                    }
                }
                "Team Lead" -> {
                    when {
                        // Team Leads can access users in their department
                        currentUserCompany == targetUserCompany &&
                                currentUserDepartment == targetUserDepartment -> AccessPermission(
                            isAllowed = true,
                            accessLevel = AccessLevel.TEAM_ACCESS,
                            reason = "Team Lead department access"
                        )
                        else -> AccessPermission(
                            false,
                            AccessLevel.NO_ACCESS,
                            "Team Lead can only access users from same department"
                        )
                    }
                }
                else -> {
                    // Regular employees can only access their own profile
                    AccessPermission(
                        false,
                        AccessLevel.NO_ACCESS,
                        "Insufficient permissions to access other profiles"
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking access permissions", e)
            AccessPermission(false, AccessLevel.NO_ACCESS, "Error checking permissions: ${e.message}")
        }
    }

    private suspend fun loadTargetUserProfile(
        targetUserId: String,
        targetUserName: String,
        currentUser: UserData,
        accessPermission: AccessPermission
    ) {
        Log.d(TAG, "📋 Loading target user profile: $targetUserId")

        try {
            // Get target user's role for proper permission setup
            val targetUserDoc = firestore.collection("user_access_control")
                .document(targetUserId)
                .get()
                .await()

            val targetUserRole = if (targetUserDoc.exists()) {
                targetUserDoc.getString("role") ?: "Employee"
            } else {
                "Employee"
            }

            Log.d(TAG, "✅ Target user role: $targetUserRole")
            initializeUI(targetUserId, targetUserName, currentUser, targetUserRole, accessPermission)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading target user profile", e)
            handleError("Failed to load user profile: ${e.message}")
        }
    }

    private fun initializeUI(
        targetUserId: String,
        targetUserName: String,
        currentUser: UserData,
        targetUserRole: String,
        accessPermission: AccessPermission
    ) {
        Log.d(TAG, "🎨 Initializing UI")
        Log.d(TAG, "   - Target User: $targetUserId ($targetUserName)")
        Log.d(TAG, "   - Target Role: $targetUserRole")
        Log.d(TAG, "   - Access Level: ${accessPermission.accessLevel}")

        setContent {
            Ritik_2Theme {
                val isDarkTheme = isSystemInDarkTheme()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDarkTheme) Color(0xFF121212) else Color(0xFFFAFAFA)
                ) {
                    ProfileAccessScreen(
                        targetUserId = targetUserId,
                        targetUserName = targetUserName,
                        targetUserRole = targetUserRole,
                        currentUser = currentUser,
                        accessPermission = accessPermission,
                        isDarkTheme = isDarkTheme,
                        onProfileUpdateClick = { updatedData, newPassword, imageUri ->
                            handleProfileUpdate(updatedData, newPassword, imageUri, targetUserId, accessPermission)
                        },
                        onBackClick = {
                            handleBackNavigation()
                        },
                        onLogoutClick = {
                            handleLogout()
                        }
                    )
                }
            }
        }
    }

    private fun handleProfileUpdate(
        updatedData: Map<String, Any>,
        newPassword: String?,
        imageUri: Uri?,
        targetUserId: String,
        accessPermission: AccessPermission
    ) {
        Log.d(TAG, "🔄 Starting profile update for target user: $targetUserId")
        Log.d(TAG, "   - Access Level: ${accessPermission.accessLevel}")

        lifecycleScope.launch {
            try {
                if (imageUri != null) {
                    uploadImageAndUpdateProfile(imageUri, updatedData, newPassword, targetUserId, accessPermission)
                } else {
                    updateProfileData(updatedData, newPassword, targetUserId, accessPermission)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in profile update process", e)
                Toast.makeText(this@ProfileAccessActivity, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun uploadImageAndUpdateProfile(
        imageUri: Uri,
        updatedData: Map<String, Any>,
        newPassword: String?,
        targetUserId: String,
        accessPermission: AccessPermission
    ) {
        Log.d(TAG, "📷 Uploading image for target user: $targetUserId")

        try {
            // Check if current user can modify images
            if (!canModifyImages(accessPermission.accessLevel)) {
                throw Exception("Insufficient permissions to modify profile image")
            }

            // Get target user info for storage path
            val accessControlDoc = firestore.collection("user_access_control")
                .document(targetUserId)
                .get()
                .await()

            val companyName = sanitizeDocumentId(accessControlDoc.getString("sanitizedCompany") ?: "default_company")
            val department = sanitizeDocumentId(accessControlDoc.getString("sanitizedDepartment") ?: "default_department")
            val role = accessControlDoc.getString("role") ?: "Employee"

            val imageRef = storage.reference.child("users/$companyName/$department/$role/$targetUserId/profile.jpg")

            val uploadTask = imageRef.putFile(imageUri).await()
            val downloadUrl = imageRef.downloadUrl.await()

            Log.d(TAG, "✅ Image uploaded successfully: $downloadUrl")

            val dataWithImage = updatedData.toMutableMap()
            dataWithImage["imageUrl"] = downloadUrl.toString()
            updateProfileData(dataWithImage, newPassword, targetUserId, accessPermission)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Image upload failed", e)
            Toast.makeText(this, "Image upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            throw e
        }
    }

    private suspend fun updateProfileData(
        updatedData: Map<String, Any>,
        newPassword: String?,
        targetUserId: String,
        accessPermission: AccessPermission
    ) {
        Log.d(TAG, "💾 Updating profile data for target user: $targetUserId")

        try {
            // Filter data based on access permissions
            val allowedData = filterDataByPermissions(updatedData, accessPermission.accessLevel)

            if (allowedData.isEmpty()) {
                throw Exception("No data to update with current permissions")
            }

            // Get current target user access control data
            val accessControlDoc = firestore.collection("user_access_control")
                .document(targetUserId)
                .get()
                .await()

            if (!accessControlDoc.exists()) {
                throw Exception("Target user not found in system")
            }

            val currentDocumentPath = accessControlDoc.getString("documentPath")

            // Update existing document
            updateExistingDocument(targetUserId, currentDocumentPath, allowedData, newPassword, accessPermission)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Profile update failed", e)
            Toast.makeText(this, "Profile update failed: ${e.message}", Toast.LENGTH_LONG).show()
            throw e
        }
    }

    private suspend fun updateExistingDocument(
        targetUserId: String,
        documentPath: String?,
        updatedData: Map<String, Any>,
        newPassword: String?,
        accessPermission: AccessPermission
    ) {
        Log.d(TAG, "📝 Updating existing document at path: $documentPath")

        val timestamp = Timestamp.now()
        val finalData = updatedData.toMutableMap().apply {
            put("lastUpdated", timestamp)
            put("lastModifiedBy", authManager.currentUser?.uid ?: "unknown")
        }

        // Update main user document
        if (!documentPath.isNullOrEmpty()) {
            firestore.document(documentPath).update(finalData).await()
        } else {
            // Fallback to users collection
            firestore.collection("users").document(targetUserId).update(finalData).await()
        }

        // Update access control if permissions allow
        if (canModifyCoreData(accessPermission.accessLevel)) {
            val accessControlUpdate = mutableMapOf<String, Any>(
                "lastAccess" to timestamp
            )

            // Add searchable fields if they exist
            updatedData["name"]?.let { accessControlUpdate["name"] = it }
            updatedData["email"]?.let { accessControlUpdate["email"] = it }

            firestore.collection("user_access_control")
                .document(targetUserId)
                .update(accessControlUpdate)
                .await()

            // Update search index
            val searchUpdate = mutableMapOf<String, Any>(
                "lastUpdated" to timestamp
            )

            updatedData["name"]?.let { name ->
                searchUpdate["name"] = name.toString().lowercase()
            }

            firestore.collection("user_search_index")
                .document(targetUserId)
                .update(searchUpdate)
                .await()
        }

        Log.d(TAG, "✅ Profile updated successfully")

        // Handle password update if allowed and requested
        if (newPassword != null && newPassword.isNotEmpty()) {
            if (canModifyPassword(accessPermission.accessLevel)) {
                updatePassword(newPassword, targetUserId)
            } else {
                Log.w(TAG, "⚠️ Password update not allowed for current access level")
                handleProfileUpdateSuccess()
            }
        } else {
            handleProfileUpdateSuccess()
        }
    }

    private fun filterDataByPermissions(data: Map<String, Any>, accessLevel: AccessLevel): Map<String, Any> {
        val filteredData = mutableMapOf<String, Any>()

        data.forEach { (key, value) ->
            val canModify = when (key) {
                // Core fields - only full access can modify
                "name", "email", "role", "companyName", "department", "designation", "employeeId" -> {
                    canModifyCoreData(accessLevel)
                }
                // Sensitive fields - HR+ access required
                "salary", "joiningDate", "reportingTo" -> {
                    canModifySensitiveData(accessLevel)
                }
                // Personal fields - team+ access required
                "phoneNumber", "address", "dateOfBirth", "skills",
                "emergencyContactName", "emergencyContactPhone", "emergencyContactRelation" -> {
                    canModifyPersonalData(accessLevel)
                }
                // Work stats - team+ access required
                "experience", "completedProjects", "activeProjects",
                "pendingTasks", "completedTasks", "totalWorkingHours" -> {
                    canModifyWorkStats(accessLevel)
                }
                // Images
                "imageUrl" -> canModifyImages(accessLevel)

                else -> canModifyPersonalData(accessLevel) // Default to personal data permission
            }

            if (canModify) {
                filteredData[key] = value
            } else {
                Log.d(TAG, "⚠️ Skipping field '$key' - insufficient permissions")
            }
        }

        return filteredData
    }

    // Permission helper methods
    private fun canModifyCoreData(accessLevel: AccessLevel): Boolean =
        accessLevel in listOf(AccessLevel.FULL_ACCESS)

    private fun canModifySensitiveData(accessLevel: AccessLevel): Boolean =
        accessLevel in listOf(AccessLevel.FULL_ACCESS, AccessLevel.HR_ACCESS)

    private fun canModifyPersonalData(accessLevel: AccessLevel): Boolean =
        accessLevel in listOf(AccessLevel.FULL_ACCESS, AccessLevel.COMPANY_ACCESS, AccessLevel.HR_ACCESS, AccessLevel.TEAM_ACCESS)

    private fun canModifyWorkStats(accessLevel: AccessLevel): Boolean =
        accessLevel in listOf(AccessLevel.FULL_ACCESS, AccessLevel.COMPANY_ACCESS, AccessLevel.HR_ACCESS, AccessLevel.TEAM_ACCESS)

    private fun canModifyImages(accessLevel: AccessLevel): Boolean =
        accessLevel in listOf(AccessLevel.FULL_ACCESS, AccessLevel.COMPANY_ACCESS, AccessLevel.HR_ACCESS)

    private fun canModifyPassword(accessLevel: AccessLevel): Boolean =
        accessLevel in listOf(AccessLevel.FULL_ACCESS)

    private fun updatePassword(newPassword: String, targetUserId: String) {
        Log.d(TAG, "🔒 Password update not supported for other users (Firebase Auth limitation)")
        // Note: Firebase Auth doesn't allow updating passwords for other users
        // This would require admin SDK on backend
        Toast.makeText(this, "Password update requires target user to change it themselves", Toast.LENGTH_LONG).show()
        handleProfileUpdateSuccess()
    }

    private fun handleProfileUpdateSuccess() {
        Log.d(TAG, "🎉 Profile update completed successfully")
        Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
    }

    private fun handleBackNavigation() {
        Log.d(TAG, "🔙 Navigating back")
        finish()
    }

    private fun handleLogout() {
        Log.d(TAG, "🚪 User logging out")
        authManager.signOut()
        navigateToLogin()
    }

    private fun handleError(message: String) {
        Log.e(TAG, "❌ Error: $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun handleAccessDenied(reason: String) {
        Log.w(TAG, "🚫 Access denied: $reason")
        Toast.makeText(this, "Access denied: $reason", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun handleAuthError(message: String) {
        Log.e(TAG, "🔒 Auth error: $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        Log.d(TAG, "🔐 Navigating to login")
        try {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to login", e)
            finishAffinity()
        }
    }

    private fun sanitizeDocumentId(input: String): String {
        return input
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(100)
    }
}

// Data classes for access control
data class AccessPermission(
    val isAllowed: Boolean,
    val accessLevel: AccessLevel,
    val reason: String
)

enum class AccessLevel {
    NO_ACCESS,          // No access
    TEAM_ACCESS,        // Can modify personal data within team/department
    HR_ACCESS,          // Can modify personal + sensitive data within company
    COMPANY_ACCESS,     // Can modify most data within company (Manager level)
    FULL_ACCESS         // Can modify everything (Administrator level)
}