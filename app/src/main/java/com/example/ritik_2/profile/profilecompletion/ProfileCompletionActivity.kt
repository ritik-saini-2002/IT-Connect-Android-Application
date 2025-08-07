package com.example.ritik_2.profile.profilecompletion

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
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.theme.Ritik_2Theme
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileCompletionActivity : ComponentActivity() {

    companion object {
        const val TAG = "ProfileCompletion"
        const val EXTRA_USER_ID = "extra_user_id"

        fun createIntent(context: Context, userId: String): Intent {
            return Intent(context, ProfileCompletionActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
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
            var userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""

            if (userId.isEmpty()) {
                userId = authManager.currentUser?.uid ?: ""
                Log.w(TAG, "No userId in intent, using AuthManager: $userId")
            }

            if (userId.isEmpty()) {
                Log.e(TAG, "❌ No valid user ID found")
                handleAuthError("Session expired. Please log in again.")
                return
            }

            Log.d(TAG, "✅ ProfileCompletionActivity started for user: $userId")
            verifyAuthentication(userId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onCreate", e)
            Toast.makeText(this, "Error starting profile completion: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun verifyAuthentication(userId: String) {
        lifecycleScope.launch {
            try {
                val authState = authManager.checkAuthenticationState()
                Log.d(TAG, "📊 Current auth state: $authState")

                when (authState) {
                    is AuthState.Authenticated -> {
                        Log.d(TAG, "✅ User properly authenticated, initializing UI")
                        initializeUI(userId, authState.user.role)
                    }
                    is AuthState.InvalidRole -> {
                        Log.w(TAG, "⚠️ Invalid role detected: ${authState.role}")
                        initializeUI(userId, authState.role ?: "Employee")
                    }
                    is AuthState.UserNotFound -> {
                        Log.w(TAG, "⚠️ User document not found")
                        initializeUI(userId, "Employee")
                    }
                    is AuthState.NotAuthenticated -> {
                        Log.e(TAG, "❌ User not authenticated")
                        handleAuthError("Please log in to continue")
                    }
                    is AuthState.Error -> {
                        Log.e(TAG, "❌ Authentication error: ${authState.message}")
                        handleAuthError("Authentication error: ${authState.message}")
                    }
                    is AuthState.Loading -> {
                        Log.d(TAG, "⏳ Auth state loading...")
                        kotlinx.coroutines.delay(1000)
                        verifyAuthentication(userId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error verifying authentication", e)
                handleAuthError("Session verification failed")
            }
        }
    }

    private fun initializeUI(userId: String, userRole: String) {
        Log.d(TAG, "🎨 Initializing UI for user: $userId with role: $userRole")

        setContent {
            Ritik_2Theme {
                val isDarkTheme = isSystemInDarkTheme()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDarkTheme) Color(0xFF121212) else Color(0xFFFAFAFA)
                ) {
                    ProfileCompletionScreen(
                        userId = userId,
                        userRole = userRole,
                        isDarkTheme = isDarkTheme,
                        onProfileUpdateClick = { updatedData, newPassword, imageUri ->
                            handleProfileUpdate(updatedData, newPassword, imageUri, userId)
                        },
                        onSkipClick = {
                            handleSkip(userId)
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
        userId: String
    ) {
        Log.d(TAG, "🔄 Starting profile update for user: $userId")

        lifecycleScope.launch {
            try {
                if (imageUri != null) {
                    uploadImageAndUpdateProfile(imageUri, updatedData, newPassword, userId)
                } else {
                    updateProfileData(updatedData, newPassword, userId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in profile update process", e)
                Toast.makeText(this@ProfileCompletionActivity, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun uploadImageAndUpdateProfile(
        imageUri: Uri,
        updatedData: Map<String, Any>,
        newPassword: String?,
        userId: String
    ) {
        Log.d(TAG, "📷 Uploading image for user: $userId")

        try {
            // Get user info for storage path
            val accessControlDoc = firestore.collection("user_access_control").document(userId).get().await()
            val companyName = sanitizeDocumentId(accessControlDoc.getString("sanitizedCompanyName") ?: "default_company")
            val department = sanitizeDocumentId(accessControlDoc.getString("sanitizedDepartment") ?: "default_department")
            val role = accessControlDoc.getString("role") ?: "Employee"

            val imageRef = storage.reference.child("users/$companyName/$department/$role/$userId/profile.jpg")

            val uploadTask = imageRef.putFile(imageUri).await()
            val downloadUrl = imageRef.downloadUrl.await()

            Log.d(TAG, "✅ Image uploaded successfully: $downloadUrl")

            val dataWithImage = updatedData.toMutableMap()
            dataWithImage["imageUrl"] = downloadUrl.toString()
            updateProfileData(dataWithImage, newPassword, userId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Image upload failed", e)
            Toast.makeText(this, "Image upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            throw e
        }
    }

    private suspend fun updateProfileData(
        updatedData: Map<String, Any>,
        newPassword: String?,
        userId: String
    ) {
        Log.d(TAG, "💾 Updating profile data for user: $userId")

        try {
            // Get current user access control data
            val accessControlDoc = firestore.collection("user_access_control").document(userId).get().await()

            if (!accessControlDoc.exists()) {
                throw Exception("User access control not found")
            }

            val currentDocumentPath = accessControlDoc.getString("documentPath")
            val currentRole = accessControlDoc.getString("role")
            val currentCompanyName = accessControlDoc.getString("companyName")
            val currentDepartment = accessControlDoc.getString("department")

            // Check if core data is being changed
            val newRole = updatedData["role"]?.toString() ?: currentRole
            val newCompanyName = updatedData["companyName"]?.toString() ?: currentCompanyName
            val newDepartment = updatedData["department"]?.toString() ?: currentDepartment

            val coreDataChanged = (newRole != currentRole) ||
                    (newCompanyName != currentCompanyName) ||
                    (newDepartment != currentDepartment)

            if (coreDataChanged) {
                // Need to create new hierarchical structure
                createOrUpdateHierarchicalStructure(userId, updatedData, newPassword)
            } else {
                // Simple update to existing document
                updateExistingDocument(userId, currentDocumentPath, updatedData, newPassword)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Profile update failed", e)
            Toast.makeText(this, "Profile update failed: ${e.message}", Toast.LENGTH_LONG).show()
            throw e
        }
    }

    private suspend fun updateExistingDocument(
        userId: String,
        documentPath: String?,
        updatedData: Map<String, Any>,
        newPassword: String?
    ) {
        Log.d(TAG, "📝 Updating existing document at path: $documentPath")

        val timestamp = Timestamp.now()
        val finalData = updatedData.toMutableMap().apply {
            put("lastUpdated", timestamp)
            put("isProfileComplete", true)
        }

        // Update main user document
        if (!documentPath.isNullOrEmpty()) {
            firestore.document(documentPath).update(finalData).await()
        } else {
            // Fallback to users collection
            firestore.collection("users").document(userId).update(finalData).await()
        }

        // Update access control
        val accessControlUpdate = mutableMapOf<String, Any>(
            "lastAccess" to timestamp
        )

        // Add searchable fields if they exist
        updatedData["name"]?.let { accessControlUpdate["name"] = it }
        updatedData["email"]?.let { accessControlUpdate["email"] = it }

        firestore.collection("user_access_control").document(userId).update(accessControlUpdate).await()

        // Update search index
        val searchUpdate = mutableMapOf<String, Any>(
            "lastUpdated" to timestamp
        )

        updatedData["name"]?.let { name ->
            searchUpdate["name"] = name.toString().lowercase()
        }

        firestore.collection("user_search_index").document(userId).update(searchUpdate).await()

        Log.d(TAG, "✅ Existing document updated successfully")

        if (newPassword != null && newPassword.isNotEmpty()) {
            updatePassword(newPassword)
        } else {
            handleProfileUpdateSuccess()
        }
    }

    private suspend fun createOrUpdateHierarchicalStructure(
        userId: String,
        updatedData: Map<String, Any>,
        newPassword: String?
    ) {
        Log.d(TAG, "🏗️ Creating new hierarchical structure for user: $userId")

        val timestamp = Timestamp.now()
        val batch = firestore.batch()

        // Get current user data first
        val accessControlDoc = firestore.collection("user_access_control").document(userId).get().await()
        val currentUserData = if (accessControlDoc.exists()) {
            accessControlDoc.data ?: emptyMap()
        } else {
            emptyMap()
        }

        // Merge existing data with updates
        val finalData = currentUserData.toMutableMap().apply {
            putAll(updatedData)
            put("lastUpdated", timestamp)
            put("isProfileComplete", true)
        }

        val role = finalData["role"]?.toString() ?: "Employee"
        val companyName = finalData["companyName"]?.toString() ?: "Default Company"
        val department = finalData["department"]?.toString() ?: "General"

        val sanitizedCompanyName = sanitizeDocumentId(companyName)
        val sanitizedDepartment = sanitizeDocumentId(department)

        // Create new document path
        val newDocumentPath = "users/$sanitizedCompanyName/$sanitizedDepartment/$role/users/$userId"

        // 1. Create/Update hierarchical user document
        val userDocRef = firestore.document(newDocumentPath)

        val hierarchicalUserData = createHierarchicalUserData(userId, finalData, timestamp, newDocumentPath)
        batch.set(userDocRef, hierarchicalUserData)

        // 2. Update company metadata
        val companyMetaRef = firestore.collection("companies_metadata").document(sanitizedCompanyName)
        val companyMetaData = mapOf(
            "originalName" to companyName,
            "sanitizedName" to sanitizedCompanyName,
            "lastUpdated" to timestamp,
            "totalUsers" to FieldValue.increment(1),
            "activeUsers" to FieldValue.increment(1),
            "availableRoles" to FieldValue.arrayUnion(role),
            "departments" to FieldValue.arrayUnion(department)
        )
        batch.set(companyMetaRef, companyMetaData, SetOptions.merge())

        // 3. Update department metadata
        val departmentMetaRef = firestore
            .collection("companies_metadata")
            .document(sanitizedCompanyName)
            .collection("departments_metadata")
            .document(sanitizedDepartment)

        val departmentMetaData = mapOf(
            "departmentName" to department,
            "companyName" to companyName,
            "sanitizedName" to sanitizedDepartment,
            "userCount" to FieldValue.increment(1),
            "activeUsers" to FieldValue.increment(1),
            "availableRoles" to FieldValue.arrayUnion(role),
            "lastUpdated" to timestamp
        )
        batch.set(departmentMetaRef, departmentMetaData, SetOptions.merge())

        // 4. Update role metadata
        val roleMetaRef = firestore
            .collection("companies_metadata")
            .document(sanitizedCompanyName)
            .collection("departments_metadata")
            .document(sanitizedDepartment)
            .collection("roles_metadata")
            .document(role)

        val roleMetaData = mapOf(
            "roleName" to role,
            "companyName" to companyName,
            "department" to department,
            "permissions" to getRolePermissions(role),
            "userCount" to FieldValue.increment(1),
            "activeUsers" to FieldValue.increment(1),
            "lastUpdated" to timestamp
        )
        batch.set(roleMetaRef, roleMetaData, SetOptions.merge())

        // 5. Update user access control
        val userAccessControlRef = firestore.collection("user_access_control").document(userId)
        val accessControlData = mapOf(
            "userId" to userId,
            "name" to (finalData["name"] ?: "Unknown User"),
            "email" to (finalData["email"] ?: ""),
            "companyName" to companyName,
            "sanitizedCompanyName" to sanitizedCompanyName,
            "department" to department,
            "sanitizedDepartment" to sanitizedDepartment,
            "role" to role,
            "permissions" to getRolePermissions(role),
            "isActive" to true,
            "documentPath" to newDocumentPath,
            "lastAccess" to timestamp
        )
        batch.set(userAccessControlRef, accessControlData)

        // 6. Update user search index
        val userSearchIndexRef = firestore.collection("user_search_index").document(userId)
        val searchIndexData = mapOf(
            "userId" to userId,
            "name" to (finalData["name"]?.toString()?.lowercase() ?: ""),
            "email" to (finalData["email"]?.toString()?.lowercase() ?: ""),
            "companyName" to companyName,
            "sanitizedCompanyName" to sanitizedCompanyName,
            "department" to department,
            "sanitizedDepartment" to sanitizedDepartment,
            "role" to role,
            "designation" to (finalData["designation"] ?: ""),
            "isActive" to true,
            "documentPath" to newDocumentPath,
            "searchTerms" to listOf(
                finalData["name"]?.toString()?.lowercase() ?: "",
                finalData["email"]?.toString()?.lowercase() ?: "",
                companyName.lowercase(),
                department.lowercase(),
                role.lowercase(),
                finalData["designation"]?.toString()?.lowercase() ?: ""
            ).filter { it.isNotEmpty() }
        )
        batch.set(userSearchIndexRef, searchIndexData)

        // Execute batch operation
        batch.commit().await()

        Log.d(TAG, "✅ Hierarchical structure created at: $newDocumentPath")

        if (newPassword != null && newPassword.isNotEmpty()) {
            updatePassword(newPassword)
        } else {
            handleProfileUpdateSuccess()
        }
    }

    private fun createHierarchicalUserData(
        userId: String,
        userData: Map<String, Any>,
        timestamp: Timestamp,
        documentPath: String
    ): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "name" to (userData["name"] ?: "Unknown User"),
            "email" to (userData["email"] ?: ""),
            "role" to (userData["role"] ?: "Employee"),
            "companyName" to (userData["companyName"] ?: ""),
            "sanitizedCompanyName" to sanitizeDocumentId(userData["companyName"]?.toString() ?: ""),
            "department" to (userData["department"] ?: ""),
            "sanitizedDepartment" to sanitizeDocumentId(userData["department"]?.toString() ?: ""),
            "designation" to (userData["designation"] ?: ""),
            "createdAt" to (userData["createdAt"] ?: timestamp),
            "lastUpdated" to timestamp,
            "isActive" to true,
            "isProfileComplete" to true,
            "lastLogin" to (userData["lastLogin"]),

            // Profile section
            "profile" to mapOf(
                "imageUrl" to (userData["imageUrl"] ?: ""),
                "phoneNumber" to (userData["phoneNumber"] ?: ""),
                "address" to (userData["address"] ?: ""),
                "dateOfBirth" to (userData["dateOfBirth"]),
                "joiningDate" to (userData["joiningDate"] ?: timestamp),
                "employeeId" to (userData["employeeId"] ?: ""),
                "reportingTo" to (userData["reportingTo"] ?: ""),
                "salary" to (userData["salary"] ?: 0),
                "emergencyContact" to mapOf(
                    "name" to (userData["emergencyContactName"] ?: ""),
                    "phone" to (userData["emergencyContactPhone"] ?: ""),
                    "relation" to (userData["emergencyContactRelation"] ?: "")
                )
            ),

            // Work stats
            "workStats" to mapOf(
                "experience" to (userData["experience"] ?: 0),
                "completedProjects" to (userData["completedProjects"] ?: 0),
                "activeProjects" to (userData["activeProjects"] ?: 0),
                "pendingTasks" to (userData["pendingTasks"] ?: 0),
                "completedTasks" to (userData["completedTasks"] ?: 0),
                "totalWorkingHours" to (userData["totalWorkingHours"] ?: 0),
                "avgPerformanceRating" to (userData["avgPerformanceRating"] ?: 0.0)
            ),

            // Issues/Complaints
            "issues" to mapOf(
                "totalComplaints" to (userData["totalComplaints"] ?: 0),
                "resolvedComplaints" to (userData["resolvedComplaints"] ?: 0),
                "pendingComplaints" to (userData["pendingComplaints"] ?: 0),
                "lastComplaintDate" to (userData["lastComplaintDate"])
            ),

            // Permissions
            "permissions" to getRolePermissions(userData["role"]?.toString() ?: "Employee"),

            // Skills if provided
            "skills" to (userData["skills"] ?: emptyList<String>()),

            // Document path reference
            "documentPath" to documentPath
        ) as Map<String, Any>
    }

    private fun getRolePermissions(role: String): List<String> {
        return when (role) {
            "Administrator" -> listOf(
                "create_user", "delete_user", "modify_user", "view_all_users",
                "manage_roles", "view_analytics", "system_settings", "manage_companies",
                "access_all_data", "export_data", "manage_permissions"
            )
            "Manager" -> listOf(
                "view_team_users", "modify_team_user", "view_team_analytics",
                "assign_projects", "approve_requests", "view_reports"
            )
            "HR" -> listOf(
                "view_all_users", "modify_user", "view_hr_analytics", "manage_employees",
                "access_personal_data", "generate_reports"
            )
            "Team Lead" -> listOf(
                "view_team_users", "assign_tasks", "view_team_performance", "approve_leave"
            )
            "Employee" -> listOf(
                "view_profile", "edit_profile", "view_assigned_projects", "submit_reports"
            )
            "Intern" -> listOf(
                "view_profile", "edit_basic_profile", "view_assigned_tasks"
            )
            else -> listOf("view_profile", "edit_basic_profile")
        }
    }

    private fun updatePassword(newPassword: String) {
        Log.d(TAG, "🔒 Updating password")

        val user = firebaseAuth.currentUser
        if (user != null) {
            user.updatePassword(newPassword)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Password updated successfully")
                    handleProfileUpdateSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Password update failed", e)
                    Toast.makeText(this, "Password update failed: ${e.message}", Toast.LENGTH_LONG).show()
                    handleProfileUpdateSuccess() // Still navigate even if password update fails
                }
        } else {
            Log.w(TAG, "⚠️ No current user for password update")
            handleProfileUpdateSuccess()
        }
    }

    private fun handleProfileUpdateSuccess() {
        Log.d(TAG, "🎉 Profile update completed successfully")
        Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            kotlinx.coroutines.delay(1000)
            navigateToMainActivity()
        }
    }

    private fun handleSkip(userId: String) {
        Log.d(TAG, "⏭️ User skipped profile completion: $userId")

        lifecycleScope.launch {
            try {
                val minimalData = mapOf(
                    "isProfileComplete" to true,
                    "lastUpdated" to Timestamp.now()
                )

                // Try to update existing document first
                val accessControlDoc = firestore.collection("user_access_control").document(userId).get().await()
                val documentPath = accessControlDoc.getString("documentPath")

                if (!documentPath.isNullOrEmpty()) {
                    firestore.document(documentPath).update(minimalData).await()
                } else {
                    firestore.collection("users").document(userId).update(minimalData).await()
                }

                Log.d(TAG, "✅ Minimal profile data saved")
                Toast.makeText(this@ProfileCompletionActivity, "Profile setup skipped", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to save minimal profile", e)
                navigateToMainActivity() // Still navigate even if this fails
            }
        }
    }

    private fun handleLogout() {
        Log.d(TAG, "🚪 User logging out from profile completion")
        authManager.signOut()
        navigateToLogin()
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

    private fun navigateToMainActivity() {
        Log.d(TAG, "🏠 Navigating to main activity")
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to main activity", e)
            Toast.makeText(this, "Error opening main app: ${e.message}", Toast.LENGTH_LONG).show()
            navigateToLogin()
        }
    }

    private fun sanitizeDocumentId(input: String): String {
        return input
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(100)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "📱 ProfileCompletionActivity resumed")

        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: authManager.currentUser?.uid
        if (userId != null) {
            lifecycleScope.launch {
                val authState = authManager.checkAuthenticationState()
                if (authState is AuthState.NotAuthenticated) {
                    Log.w(TAG, "⚠️ User no longer authenticated on resume")
                    handleAuthError("Session expired")
                }
            }
        }
    }

    override fun onBackPressed() {
        Log.d(TAG, "🔙 Back pressed - showing options")
        super.onBackPressed()
    }
}