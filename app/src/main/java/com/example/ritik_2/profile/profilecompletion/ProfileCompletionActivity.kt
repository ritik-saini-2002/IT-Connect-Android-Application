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
import com.example.ritik_2.data.*
import com.example.ritik_2.theme.AppTheme
import com.example.ritik_2.profile.profilecompletion.components.ProfileCompletionViewModel
import com.example.ritik_2.profile.profilecompletion.components.ProfileData
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class ProfileCompletionActivity : ComponentActivity() {

    private lateinit var viewModel: ProfileCompletionViewModel
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.setSelectedImageUri(it) }
    }

    companion object {
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_IS_EDIT_MODE = "is_edit_mode"

        fun createIntent(context: Context, userId: String, isEditMode: Boolean = false): Intent {
            return Intent(context, ProfileCompletionActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_IS_EDIT_MODE, isEditMode)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[ProfileCompletionViewModel::class.java]

        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: auth.currentUser?.uid ?: ""
        val isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT_MODE, false)

        if (userId.isNotEmpty()) {
            loadUserData(userId)
        } else {
            viewModel.setError("User ID not found")
        }

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProfileCompletionScreen(
                        viewModel = viewModel,
                        onImagePickClick = { imagePickerLauncher.launch("image/*") },
                        onSaveProfile = { profileData -> saveProfile(profileData) },
                        onNavigateBack = { finish() },
                        isEditMode = isEditMode,
                        userId = userId
                    )
                }
            }
        }
    }

    private fun loadUserData(userId: String) {
        lifecycleScope.launch {
            try {
                viewModel.setLoading(true)
                viewModel.setError(null)

                var userFound = false

                // First try to get user from access control (fastest lookup)
                try {
                    val accessDoc = firestore.collection("user_access_control")
                        .document(userId)
                        .get()
                        .await()

                    if (accessDoc.exists()) {
                        val userData = accessDoc.data!!
                        val documentPath = userData["documentPath"] as? String

                        if (!documentPath.isNullOrEmpty() && documentPath.contains("/users/")) {
                            // User was created with hierarchical structure
                            if (loadFromHierarchicalStructure(userId, documentPath)) {
                                userFound = true
                            }
                        } else {
                            // Create user from access control data
                            val user = User(
                                userId = userId,
                                email = auth.currentUser?.email ?: "",
                                name = userData["name"] as? String ?: "",
                                role = userData["role"] as? String ?: "Employee",
                                companyName = userData["companyName"] as? String ?: "",
                                sanitizedCompanyName = userData["sanitizedCompanyName"] as? String
                                    ?: userData["sanitizedCompany"] as? String ?: "", // Fixed: Support both key names
                                department = userData["department"] as? String ?: "",
                                sanitizedDepartment = userData["sanitizedDepartment"] as? String ?: "",
                                designation = userData["designation"] as? String ?: "",
                                documentPath = userData["documentPath"] as? String ?: ""
                            )
                            viewModel.setCurrentUser(user)
                            userFound = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProfileCompletion", "Error loading from access control", e)
                }

                // Fallback approaches if access control failed
                if (!userFound) {
                    // Try search index
                    try {
                        val userSearchDoc = firestore.collection("user_search_index")
                            .document(userId)
                            .get()
                            .await()

                        if (userSearchDoc.exists()) {
                            val userPath = userSearchDoc.getString("documentPath")
                            if (!userPath.isNullOrEmpty()) {
                                try {
                                    val userDoc = firestore.document(userPath).get().await()
                                    if (userDoc.exists()) {
                                        if (userPath.contains("/users/")) {
                                            loadFromHierarchicalStructure(userId, userPath)
                                        } else {
                                            val user = User.fromMap(userDoc.data ?: mapOf())
                                            viewModel.setCurrentUser(user)
                                        }
                                        userFound = true
                                    }
                                } catch (e: Exception) {
                                    Log.e("ProfileCompletion", "Failed to load user from path: $userPath", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ProfileCompletion", "Search index approach failed", e)
                    }
                }

                // Try direct user collection
                if (!userFound) {
                    try {
                        val userDoc = firestore.collection("users").document(userId).get().await()
                        if (userDoc.exists()) {
                            val user = User.fromMap(userDoc.data ?: mapOf())
                            viewModel.setCurrentUser(user)
                            userFound = true
                        }
                    } catch (e: Exception) {
                        Log.e("ProfileCompletion", "Direct user collection approach failed", e)
                    }
                }

                // Try to load additional profile data from flat collections
                if (userFound) {
                    loadAdditionalProfileData(userId)
                }

                // Handle case where no user data exists
                if (!userFound) {
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        viewModel.initializeNewUser(
                            userId = userId,
                            email = currentUser.email ?: "",
                            displayName = currentUser.displayName
                        )
                        Log.d("ProfileCompletion", "Initialized new user with basic data")
                    } else {
                        throw Exception("No authenticated user found")
                    }
                } else {
                    viewModel.setDataExists(true)
                }

                viewModel.setLoading(false)

            } catch (e: Exception) {
                viewModel.setLoading(false)
                viewModel.setError("Error loading profile: ${e.message}")
                Log.e("ProfileCompletion", "Error loading user data", e)

                // Try to initialize with minimal data as fallback
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    viewModel.initializeNewUser(
                        userId = userId,
                        email = currentUser.email ?: "",
                        displayName = currentUser.displayName
                    )
                }

                Toast.makeText(this@ProfileCompletionActivity,
                    "Profile not found. You can create a new one.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun loadFromHierarchicalStructure(userId: String, documentPath: String): Boolean {
        return try {
            val userDoc = firestore.document(documentPath).get().await()
            if (userDoc.exists()) {
                val userDocData = userDoc.data!!

                // Create User object from hierarchical data
                val user = User(
                    userId = userDocData["userId"] as? String ?: userId,
                    name = userDocData["name"] as? String ?: "",
                    email = userDocData["email"] as? String ?: "",
                    role = userDocData["role"] as? String ?: "Employee",
                    companyName = userDocData["companyName"] as? String ?: "",
                    sanitizedCompanyName = userDocData["sanitizedCompanyName"] as? String ?: "",
                    department = userDocData["department"] as? String ?: "",
                    sanitizedDepartment = userDocData["sanitizedDepartment"] as? String ?: "",
                    designation = userDocData["designation"] as? String ?: "",
                    documentPath = documentPath,
                    isActive = userDocData["isActive"] as? Boolean ?: true,
                    createdAt = (userDocData["createdAt"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                    lastUpdated = (userDocData["lastUpdated"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                    createdBy = userDocData["createdBy"] as? String ?: userId
                )

                viewModel.setCurrentUser(user)

                // Extract profile data from nested structure
                val profileData = userDocData["profile"] as? Map<String, Any>
                if (profileData != null) {
                    val emergencyContact = profileData["emergencyContact"] as? Map<String, Any> ?: mapOf()

                    val profile = UserProfile(
                        userId = userId,
                        imageUrl = profileData["imageUrl"] as? String ?: "",
                        phoneNumber = profileData["phoneNumber"] as? String ?: "",
                        address = profileData["address"] as? String ?: "",
                        dateOfBirth = (profileData["dateOfBirth"] as? Timestamp)?.toDate()?.time,
                        joiningDate = (profileData["joiningDate"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                        employeeId = profileData["employeeId"] as? String ?: "",
                        reportingTo = profileData["reportingTo"] as? String ?: "",
                        salary = (profileData["salary"] as? Number)?.toDouble() ?: 0.0,
                        emergencyContactName = emergencyContact["name"] as? String ?: "",
                        emergencyContactPhone = emergencyContact["phone"] as? String ?: "",
                        emergencyContactRelation = emergencyContact["relation"] as? String ?: ""
                    )

                    viewModel.setCurrentProfile(profile)
                }

                // Extract work stats from nested structure
                val workStatsData = userDocData["workStats"] as? Map<String, Any>
                if (workStatsData != null) {
                    val workStats = WorkStats(
                        userId = userId,
                        experience = (workStatsData["experience"] as? Number)?.toInt() ?: 0,
                        completedProjects = (workStatsData["completedProjects"] as? Number)?.toInt() ?: 0,
                        activeProjects = (workStatsData["activeProjects"] as? Number)?.toInt() ?: 0,
                        pendingTasks = (workStatsData["pendingTasks"] as? Number)?.toInt() ?: 0,
                        completedTasks = (workStatsData["completedTasks"] as? Number)?.toInt() ?: 0,
                        totalWorkingHours = (workStatsData["totalWorkingHours"] as? Number)?.toInt() ?: 0,
                        avgPerformanceRating = (workStatsData["avgPerformanceRating"] as? Number)?.toDouble() ?: 0.0
                    )

                    viewModel.setCurrentWorkStats(workStats)
                }

                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("ProfileCompletion", "Error loading from hierarchical path: $documentPath", e)
            false
        }
    }

    private suspend fun loadAdditionalProfileData(userId: String) {
        // Try to load profile data from flat collection
        try {
            val profileDoc = firestore.collection("user_profiles")
                .document(userId)
                .get()
                .await()

            if (profileDoc.exists()) {
                val profile = UserProfile.fromMap(userId, profileDoc.data ?: mapOf())
                viewModel.setCurrentProfile(profile)
            }
        } catch (e: Exception) {
            Log.d("ProfileCompletion", "Profile loading from flat collection failed: ${e.message}")
        }

        // Try to load work stats from flat collection
        try {
            val statsDoc = firestore.collection("work_stats")
                .document(userId)
                .get()
                .await()

            if (statsDoc.exists()) {
                val stats = WorkStats.fromMap(userId, statsDoc.data ?: mapOf())
                viewModel.setCurrentWorkStats(stats)
            }
        } catch (e: Exception) {
            Log.d("ProfileCompletion", "Work stats loading from flat collection failed: ${e.message}")
        }
    }

    private fun saveProfile(profileData: ProfileData) {
        lifecycleScope.launch {
            try {
                viewModel.setLoading(true)
                viewModel.setError(null)

                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(this@ProfileCompletionActivity,
                        "User not authenticated", Toast.LENGTH_SHORT).show()
                    viewModel.setLoading(false)
                    return@launch
                }

                val userId = currentUser.uid
                var imageUrl = profileData.imageUrl

                // Upload image if new image is selected
                profileData.imageUri?.let { uri ->
                    try {
                        imageUrl = uploadProfileImage(userId, uri, profileData)
                    } catch (e: Exception) {
                        Toast.makeText(this@ProfileCompletionActivity,
                            "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                        // Continue without image update
                    }
                }

                // Validate required fields
                if (profileData.companyName.isBlank()) {
                    viewModel.setError("Company name is required")
                    viewModel.setLoading(false)
                    return@launch
                }

                if (profileData.department.isBlank()) {
                    viewModel.setError("Department is required")
                    viewModel.setLoading(false)
                    return@launch
                }

                // Get current user's document path to determine structure
                val accessDoc = firestore.collection("user_access_control").document(userId).get().await()
                val documentPath = accessDoc.getString("documentPath")

                if (!documentPath.isNullOrEmpty() && documentPath.contains("/users/") &&
                    documentPath.split("/").size >= 6) {
                    // User was created with hierarchical structure
                    updateHierarchicalStructure(userId, documentPath, profileData, imageUrl)
                } else {
                    // User was created with flat structure or needs migration, use flat collections
                    saveToFlatCollections(userId, profileData, imageUrl)
                }

            } catch (e: Exception) {
                viewModel.setLoading(false)
                viewModel.setError("Error saving profile: ${e.message}")
                Toast.makeText(this@ProfileCompletionActivity,
                    "Error saving profile: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("ProfileCompletion", "Error saving profile", e)
            }
        }
    }

    private suspend fun updateHierarchicalStructure(
        userId: String,
        documentPath: String,
        profileData: ProfileData,
        imageUrl: String
    ) {
        val batch = firestore.batch()

        // Update the hierarchical document
        val userDocRef = firestore.document(documentPath)

        val updateData = mutableMapOf<String, Any?>(
            "name" to profileData.name,
            "designation" to profileData.designation,
            "lastUpdated" to Timestamp.now(),
            "profile.imageUrl" to imageUrl,
            "profile.phoneNumber" to profileData.phoneNumber,
            "profile.address" to profileData.address,
            "profile.employeeId" to profileData.employeeId,
            "profile.reportingTo" to profileData.reportingTo,
            "profile.salary" to profileData.salary,
            "profile.emergencyContact.name" to profileData.emergencyContactName,
            "profile.emergencyContact.phone" to profileData.emergencyContactPhone,
            "profile.emergencyContact.relation" to profileData.emergencyContactRelation,
            "workStats.experience" to profileData.experience
        )

        // Handle nullable date fields
        profileData.dateOfBirth?.let {
            updateData["profile.dateOfBirth"] = Timestamp(Date(it))
        }
        profileData.joiningDate?.let {
            updateData["profile.joiningDate"] = Timestamp(Date(it))
        }

        batch.update(userDocRef, updateData)

        // Also update access control and search index with fixed field names
        updateAccessControlAndSearchIndex(batch, userId, profileData)

        batch.commit().await()

        viewModel.setLoading(false)
        Toast.makeText(this@ProfileCompletionActivity, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private suspend fun saveToFlatCollections(userId: String, profileData: ProfileData, imageUrl: String) {
        // Create sanitized names
        val sanitizedCompany = DataUtils.sanitizeDocumentId(profileData.companyName)
        val sanitizedDepartment = DataUtils.sanitizeDocumentId(profileData.department)

        // Generate document path
        val documentPath = FirestorePaths.getUserPath(
            sanitizedCompany,
            sanitizedDepartment,
            profileData.role,
            userId
        )

        // Create user document
        val user = User(
            userId = userId,
            name = profileData.name,
            email = profileData.email,
            role = profileData.role,
            companyName = profileData.companyName,
            sanitizedCompanyName = sanitizedCompany,
            department = profileData.department,
            sanitizedDepartment = sanitizedDepartment,
            designation = profileData.designation,
            documentPath = documentPath,
            isActive = true,
            createdAt = viewModel.currentUser.value?.createdAt ?: System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis(),
            createdBy = viewModel.currentUser.value?.createdBy ?: userId
        )

        // Create user profile document with nested structure for hierarchical storage
        val userProfile = UserProfile(
            userId = userId,
            imageUrl = imageUrl,
            phoneNumber = profileData.phoneNumber,
            address = profileData.address,
            dateOfBirth = profileData.dateOfBirth,
            joiningDate = profileData.joiningDate ?: System.currentTimeMillis(),
            employeeId = profileData.employeeId,
            reportingTo = profileData.reportingTo,
            salary = profileData.salary,
            emergencyContactName = profileData.emergencyContactName,
            emergencyContactPhone = profileData.emergencyContactPhone,
            emergencyContactRelation = profileData.emergencyContactRelation
        )

        // Create or update work stats document
        val existingStats = viewModel.uiState.value.currentWorkStats
        val workStats = WorkStats(
            userId = userId,
            experience = profileData.experience,
            completedProjects = existingStats?.completedProjects ?: 0,
            activeProjects = existingStats?.activeProjects ?: 0,
            pendingTasks = existingStats?.pendingTasks ?: 0,
            completedTasks = existingStats?.completedTasks ?: 0,
            totalWorkingHours = existingStats?.totalWorkingHours ?: 0,
            avgPerformanceRating = existingStats?.avgPerformanceRating ?: 0.0
        )

        // Save to Firestore using batch write
        val batch: WriteBatch = firestore.batch()

        // 1. CREATE HIERARCHICAL STRUCTURE (Primary storage as per ChatGPT analysis)
        try {
            val userDocRef = firestore.document(documentPath)
            val hierarchicalUserData = user.toMap().toMutableMap()

            // Add nested profile data to hierarchical structure
            hierarchicalUserData["profile"] = mapOf(
                "imageUrl" to imageUrl,
                "phoneNumber" to profileData.phoneNumber,
                "address" to profileData.address,
                "dateOfBirth" to profileData.dateOfBirth?.let { Timestamp(Date(it)) },
                "joiningDate" to Timestamp(Date(profileData.joiningDate ?: System.currentTimeMillis())),
                "employeeId" to profileData.employeeId,
                "reportingTo" to profileData.reportingTo,
                "salary" to profileData.salary,
                "emergencyContact" to mapOf(
                    "name" to profileData.emergencyContactName,
                    "phone" to profileData.emergencyContactPhone,
                    "relation" to profileData.emergencyContactRelation
                )
            )

            // Add nested work stats to hierarchical structure
            hierarchicalUserData["workStats"] = mapOf(
                "experience" to profileData.experience,
                "completedProjects" to (existingStats?.completedProjects ?: 0),
                "activeProjects" to (existingStats?.activeProjects ?: 0),
                "pendingTasks" to (existingStats?.pendingTasks ?: 0),
                "completedTasks" to (existingStats?.completedTasks ?: 0),
                "totalWorkingHours" to (existingStats?.totalWorkingHours ?: 0),
                "avgPerformanceRating" to (existingStats?.avgPerformanceRating ?: 0.0)
            )

            batch.set(userDocRef, hierarchicalUserData)
            Log.d("ProfileCompletion", "Hierarchical user document created at: $documentPath")
        } catch (e: Exception) {
            Log.w("ProfileCompletion", "Could not save to hierarchical path: $documentPath", e)
            // Continue with other saves
        }

        // 2. CREATE/UPDATE FLAT COLLECTIONS (For easy access and compatibility)

        // Always save in flat collection for easy access
        val flatUserDocRef = firestore.collection("users").document(userId)
        batch.set(flatUserDocRef, user.toMap())

        // Save profile data in flat collection
        val profileDocRef = firestore.collection("user_profiles").document(userId)
        batch.set(profileDocRef, userProfile.toMap())

        // Save work stats in flat collection
        val statsDocRef = firestore.collection("work_stats").document(userId)
        batch.set(statsDocRef, workStats.toMap())

        // 3. CREATE USER SEARCH INDEX (Essential for user lookup)
        val searchIndexRef = firestore.collection(FirestorePaths.USER_SEARCH_INDEX).document(userId)
        batch.set(searchIndexRef, mapOf(
            "userId" to userId,
            "name" to user.name.lowercase(),
            "email" to user.email.lowercase(),
            "role" to user.role,
            "companyName" to user.companyName,
            "sanitizedCompanyName" to user.sanitizedCompanyName, // Fixed: Consistent key name
            "department" to user.department,
            "sanitizedDepartment" to user.sanitizedDepartment,
            "designation" to user.designation,
            "documentPath" to documentPath,
            "searchTerms" to listOf(
                user.name.lowercase(),
                user.email.lowercase(),
                user.companyName.lowercase(),
                user.department.lowercase(),
                user.role.lowercase(),
                user.designation.lowercase()
            ).filter { it.isNotEmpty() },
            "lastUpdated" to System.currentTimeMillis()
        ))

        // 4. CREATE USER ACCESS CONTROL (Critical for admin panel access AND complaints module)
        val accessControlRef = firestore.collection(FirestorePaths.USER_ACCESS_CONTROL).document(userId)
        batch.set(accessControlRef, mapOf(
            "userId" to userId,
            "name" to user.name,
            "email" to user.email,
            "role" to user.role,
            "permissions" to getEnhancedRolePermissions(user.role), // Enhanced permissions including access_admin_panel
            "companyName" to user.companyName,
            "sanitizedCompanyName" to user.sanitizedCompanyName, // CRITICAL: This is required for complaints module
            "department" to user.department,
            "sanitizedDepartment" to user.sanitizedDepartment,
            "designation" to user.designation,
            "documentPath" to documentPath,
            "isActive" to true,
            "lastAccess" to Timestamp.now(),
            "lastUpdated" to System.currentTimeMillis(),
            // Additional fields needed for complaints and other modules
            "canSubmitComplaints" to true,
            "canViewComplaints" to when(user.role.trim()) {
                "Administrator", "HR", "Manager" -> true
                else -> false
            },
            "canResolveComplaints" to when(user.role.trim()) {
                "Administrator", "HR", "Manager" -> true
                else -> false
            },
            "departmentAccess" to listOf(user.sanitizedDepartment), // Departments this user can access
            "companyAccess" to listOf(user.sanitizedCompanyName) // Companies this user can access
        ))

        // 5. CREATE/UPDATE ALL METADATA STRUCTURES
        createCompanyStructure(batch, user)
        createDepartmentStructure(batch, user)
        createRoleStructure(batch, user)

        // 6. UPDATE USER COUNTERS AND ANALYTICS
        updateUserCounters(batch, user)

        // 7. INITIALIZE COMPLAINTS MODULE STRUCTURE (CRITICAL for complaints functionality)
        initializeComplaintsModule(batch, user)

        // Commit all changes
        batch.commit().await()

        Log.d("ProfileCompletion", "All database structures created/updated successfully for user: $userId")

        viewModel.setLoading(false)
        Toast.makeText(this@ProfileCompletionActivity,
            "Profile saved successfully! All access permissions configured.", Toast.LENGTH_SHORT).show()

        setResult(RESULT_OK)
        finish()
    }

    // Enhanced role permissions to match the analysis feedback + complaints module support
    private fun getEnhancedRolePermissions(role: String): List<String> {
        return when (role.trim()) {
            "Administrator" -> listOf(
                "access_admin_panel",           // CRITICAL: Added missing admin panel permission
                "create_user", "delete_user", "modify_user", "view_all_users",
                "manage_roles", "view_analytics", "system_settings", "manage_companies",
                "access_all_data", "export_data", "manage_permissions",
                // Complaints module permissions
                "submit_complaints", "view_all_complaints", "resolve_complaints",
                "manage_complaint_categories", "view_complaint_analytics", "escalate_complaints"
            )
            "Manager" -> listOf(
                "create_user", "modify_user", "view_department_users",
                "view_analytics", "manage_department", "view_reports",
                "assign_tasks", "approve_requests",
                // Complaints module permissions
                "submit_complaints", "view_department_complaints", "resolve_complaints",
                "assign_complaints", "escalate_complaints"
            )
            "HR" -> listOf(
                "create_user", "modify_user", "view_all_users",
                "manage_roles", "view_analytics", "manage_employee_data",
                "access_hr_panel", "manage_attendance", "manage_payroll",
                // Complaints module permissions
                "submit_complaints", "view_all_complaints", "resolve_complaints",
                "manage_complaint_categories", "view_complaint_analytics", "assign_complaints"
            )
            "Team Lead" -> listOf(
                "view_team_users", "assign_tasks", "view_team_analytics",
                "manage_team_projects", "approve_team_requests",
                // Complaints module permissions
                "submit_complaints", "view_team_complaints", "resolve_team_complaints"
            )
            "Employee" -> listOf(
                "view_own_profile", "update_own_profile", "view_company_directory",
                "submit_requests", "view_own_analytics",
                // Complaints module permissions
                "submit_complaints", "view_own_complaints", "update_own_complaints"
            )
            else -> listOf(
                "view_own_profile", "update_own_profile",
                // Basic complaints permission
                "submit_complaints", "view_own_complaints"
            )
        }
    }

    private fun updateAccessControlAndSearchIndex(
        batch: WriteBatch,
        userId: String,
        profileData: ProfileData
    ) {
        // Update access control with consistent field names
        val accessControlRef = firestore.collection("user_access_control").document(userId)
        batch.update(accessControlRef, mapOf(
            "name" to profileData.name,
            "designation" to profileData.designation,
            "sanitizedCompanyName" to DataUtils.sanitizeDocumentId(profileData.companyName), // Fixed: Consistent key
            "lastAccess" to Timestamp.now()
        ))

        // Update search index with consistent field names
        val searchIndexRef = firestore.collection("user_search_index").document(userId)
        batch.update(searchIndexRef, mapOf(
            "name" to profileData.name.lowercase(),
            "designation" to profileData.designation,
            "sanitizedCompanyName" to DataUtils.sanitizeDocumentId(profileData.companyName), // Fixed: Consistent key
            "searchTerms" to listOf(
                profileData.name.lowercase(),
                profileData.email.lowercase(),
                profileData.companyName.lowercase(),
                profileData.department.lowercase(),
                profileData.role.lowercase(),
                profileData.designation.lowercase()
            ).filter { it.isNotEmpty() }
        ))
    }

    private suspend fun uploadProfileImage(
        userId: String,
        imageUri: Uri,
        profileData: ProfileData
    ): String {
        return try {
            val sanitizedCompany = DataUtils.sanitizeDocumentId(profileData.companyName)
            val sanitizedDepartment = DataUtils.sanitizeDocumentId(profileData.department)

            // Construct the storage path
            val imagePath = "users/$sanitizedCompany/$sanitizedDepartment/${profileData.role}/users/$userId/profile_image.jpg"

            val imageRef = storage.reference.child(imagePath)

            // Upload the image
            imageRef.putFile(imageUri).await()

            // Get download URL
            val downloadUrl = imageRef.downloadUrl.await().toString()

            Log.d("ProfileCompletion", "Image uploaded successfully to: $imagePath")
            downloadUrl

        } catch (e: Exception) {
            Log.e("ProfileCompletion", "Failed to upload image", e)
            throw Exception("Failed to upload image: ${e.message}")
        }
    }

    private fun updateCompanyMetadata(batch: WriteBatch, user: User) {
        try {
            val companyRef = firestore.collection("companies_metadata")
                .document(user.sanitizedCompanyName)

            val companyData = mapOf(
                "sanitizedName" to user.sanitizedCompanyName,
                "originalName" to user.companyName,
                "lastUpdated" to System.currentTimeMillis(),
                "activeUsers" to 1, // This should be incremented properly in production
                "totalDepartments" to 1 // This should be calculated properly
            )

            batch.set(companyRef, companyData, SetOptions.merge())

        } catch (e: Exception) {
            Log.e("ProfileCompletion", "Error updating company metadata", e)
        }
    }

    private fun updateDepartmentMetadata(batch: WriteBatch, user: User) {
        try {
            val deptRef = firestore.collection("companies_metadata")
                .document(user.sanitizedCompanyName)
                .collection("departments_metadata")
                .document(user.sanitizedDepartment)

            val departmentData = mapOf(
                "departmentName" to user.department,
                "sanitizedName" to user.sanitizedDepartment,
                "companyName" to user.companyName,
                "sanitizedCompanyName" to user.sanitizedCompanyName,
                "userCount" to 1, // Fixed: Use userCount instead of activeUsers boolean
                "lastUpdated" to System.currentTimeMillis()
            )

            batch.set(deptRef, departmentData, SetOptions.merge())

        } catch (e: Exception) {
            Log.e("ProfileCompletion", "Error updating department metadata", e)
        }
    }

    // NEW: Create comprehensive company structure
    private fun createCompanyStructure(batch: WriteBatch, user: User) {
        try {
            // 1. Company metadata in companies_metadata collection
            val companyRef = firestore.collection("companies_metadata")
                .document(user.sanitizedCompanyName)

            val companyData = mapOf(
                "companyId" to user.sanitizedCompanyName,
                "sanitizedName" to user.sanitizedCompanyName,
                "originalName" to user.companyName,
                "createdAt" to System.currentTimeMillis(),
                "lastUpdated" to System.currentTimeMillis(),
                "isActive" to true,
                "totalUsers" to 1,
                "totalDepartments" to 1,
                "adminUsers" to if (user.role == "Administrator") 1 else 0,
                "settings" to mapOf(
                    "allowSelfRegistration" to false,
                    "requireApproval" to true,
                    "maxUsers" to 1000
                )
            )

            batch.set(companyRef, companyData, SetOptions.merge())

            // 2. Company in hierarchical structure
            val companyHierarchyRef = firestore.collection("companies")
                .document(user.sanitizedCompanyName)

            batch.set(companyHierarchyRef, mapOf(
                "name" to user.companyName,
                "sanitizedName" to user.sanitizedCompanyName,
                "createdAt" to Timestamp.now(),
                "isActive" to true
            ), SetOptions.merge())

            Log.d("ProfileCompletion", "Company structure created for: ${user.companyName}")

        } catch (e: Exception) {
            Log.e("ProfileCompletion", "Error creating company structure", e)
        }
    }

    // NEW: Create comprehensive department structure
    private fun createDepartmentStructure(batch: WriteBatch, user: User) {
        try {
            // 1. Department metadata under company (CRITICAL for complaints module department loading)
            val deptMetadataRef = firestore.collection("companies_metadata")
                .document(user.sanitizedCompanyName)
                .collection("departments_metadata")
                .document(user.sanitizedDepartment)

            val departmentMetadata = mapOf(
                "departmentId" to user.sanitizedDepartment,
                "departmentName" to user.department,
                "sanitizedName" to user.sanitizedDepartment,
                "companyName" to user.companyName,
                "sanitizedCompanyName" to user.sanitizedCompanyName,
                "userCount" to 1, // FIXED: numeric value not boolean - this was breaking department queries
                "activeUsers" to 1, // Keep both for compatibility
                "isActive" to true, // Add boolean for active status
                "activeProjects" to 0,
                "totalBudget" to 0.0,
                "createdAt" to System.currentTimeMillis(),
                "lastUpdated" to System.currentTimeMillis(),
                "managers" to if (user.role.contains("Manager", ignoreCase = true)) listOf(user.userId) else listOf(),
                "teamLeads" to if (user.role.contains("Lead", ignoreCase = true)) listOf(user.userId) else listOf(),
                // Additional fields for complaints module
                "allowComplaints" to true,
                "complaintCategories" to listOf("General", "Technical", "HR", "Workplace", "Equipment"),
                "complaintHandlers" to when {
                    user.role.contains("HR", ignoreCase = true) -> listOf(user.userId)
                    user.role.contains("Manager", ignoreCase = true) -> listOf(user.userId)
                    user.role.contains("Administrator", ignoreCase = true) -> listOf(user.userId)
                    else -> listOf()
                }
            )

            batch.set(deptMetadataRef, departmentMetadata, SetOptions.merge())

            // 2. Department in hierarchical structure
            val deptHierarchyRef = firestore.collection("companies")
                .document(user.sanitizedCompanyName)
                .collection("departments")
                .document(user.sanitizedDepartment)

            batch.set(deptHierarchyRef, mapOf(
                "name" to user.department,
                "sanitizedName" to user.sanitizedDepartment,
                "createdAt" to Timestamp.now(),
                "isActive" to true,
                "userCount" to 1,
                "activeUsers" to 1 // Add both formats for compatibility
            ), SetOptions.merge())

            // 3. Standalone departments collection for easy querying (IMPORTANT for complaints module)
            val standaloneDeptRef = firestore.collection("departments").document("${user.sanitizedCompanyName}_${user.sanitizedDepartment}")

            batch.set(standaloneDeptRef, mapOf(
                "departmentName" to user.department,
                "companyName" to user.companyName,
                "sanitizedCompanyName" to user.sanitizedCompanyName,
                "sanitizedDepartment" to user.sanitizedDepartment,
                "userCount" to 1,
                "activeUsers" to 1, // CRITICAL: The complaints query was looking for this
                "isActive" to true,
                "createdAt" to System.currentTimeMillis(),
                // Complaints-specific fields
                "complaintSettings" to mapOf(
                    "allowAnonymous" to true,
                    "requireApproval" to false,
                    "autoAssignToManager" to true,
                    "escalationDays" to 7
                )
            ), SetOptions.merge())

            // 4. Create department specifically for complaints module queries
            val complaintsDeptRef = firestore.collection("departments_metadata")
                .document("${user.sanitizedCompanyName}_${user.sanitizedDepartment}")

            batch.set(complaintsDeptRef, mapOf(
                "departmentName" to user.department,
                "sanitizedDepartment" to user.sanitizedDepartment,
                "companyName" to user.companyName,
                "sanitizedCompanyName" to user.sanitizedCompanyName,
                "userCount" to 1, // FIXED: Use userCount instead of boolean activeUsers
                "isActive" to true,
                "createdAt" to System.currentTimeMillis(),
                "lastUpdated" to System.currentTimeMillis()
            ), SetOptions.merge())

            Log.d("ProfileCompletion", "Department structure created for complaints module: ${user.department}")

        } catch (e: Exception) {
            Log.e("ProfileCompletion", "Error creating department structure for complaints", e)
        }
    }

    // NEW: Create role-based structure for better organization
    private fun createRoleStructure(batch: WriteBatch, user: User) {
        try {
            // Role-based collection for easy role management
            val roleRef = firestore.collection("companies")
                .document(user.sanitizedCompanyName)
                .collection("departments")
                .document(user.sanitizedDepartment)
                .collection("roles")
                .document(user.role.lowercase().replace(" ", "_"))

            val roleData = mapOf(
                "roleName" to user.role,
                "permissions" to getEnhancedRolePermissions(user.role),
                "userCount" to 1,
                "users" to listOf(user.userId),
                "createdAt" to System.currentTimeMillis(),
                "lastUpdated" to System.currentTimeMillis()
            )

            batch.set(roleRef, roleData, SetOptions.merge())

            Log.d("ProfileCompletion", "Role structure created for: ${user.role}")

        } catch (e: Exception) {
            Log.e("ProfileCompletion", "Error creating role structure", e)
        }
    }

    // NEW: Update various counters for analytics
    private fun updateUserCounters(batch: WriteBatch, user: User) {
        try {
            val timestamp = System.currentTimeMillis()

            // 1. Update company user count
            val companyStatsRef = firestore.collection("analytics")
                .document("companies")
                .collection("stats")
                .document(user.sanitizedCompanyName)

            batch.set(companyStatsRef, mapOf(
                "totalUsers" to 1,
                "activeUsers" to 1,
                "newUsersThisMonth" to 1,
                "lastUserAdded" to timestamp,
                "departmentCounts" to mapOf(user.sanitizedDepartment to 1)
            ), SetOptions.merge())

            // 2. Update department user count
            val deptStatsRef = firestore.collection("analytics")
                .document("departments")
                .collection("stats")
                .document("${user.sanitizedCompanyName}_${user.sanitizedDepartment}")

            batch.set(deptStatsRef, mapOf(
                "totalUsers" to 1,
                "activeUsers" to 1,
                "newUsersThisMonth" to 1,
                "roleCounts" to mapOf(user.role to 1),
                "lastUserAdded" to timestamp
            ), SetOptions.merge())

            // 3. Global system stats
            val globalStatsRef = firestore.collection("analytics")
                .document("global_stats")

            batch.set(globalStatsRef, mapOf(
                "totalUsers" to 1,
                "totalCompanies" to 1,
                "totalDepartments" to 1,
                "lastUserRegistered" to timestamp,
                "registrationsThisMonth" to 1
            ), SetOptions.merge())

            Log.d("ProfileCompletion", "User counters updated successfully")

        } catch (e: Exception) {
            Log.e("ProfileCompletion", "Error updating user counters", e)
        }
    }

    // NEW: Initialize complaints module structure for proper functionality
    private fun initializeComplaintsModule(batch: WriteBatch, user: User) {
        try {
            // 1. Company complaints configuration
            val companyComplaintsRef = firestore.collection("companies")
                .document(user.sanitizedCompanyName)
                .collection("complaints_config")
                .document("settings")

            batch.set(companyComplaintsRef, mapOf(
                "allowAnonymousComplaints" to true,
                "requireDepartmentApproval" to false,
                "autoAssignToManager" to true,
                "escalationTimeLimit" to 7, // days
                "categories" to listOf(
                    "General", "Technical Issues", "HR Related",
                    "Workplace Environment", "Equipment", "Policy Violation"
                ),
                "priority" to listOf("Low", "Medium", "High", "Critical"),
                "status" to listOf("Submitted", "In Review", "In Progress", "Resolved", "Closed"),
                "notificationSettings" to mapOf(
                    "emailNotifications" to true,
                    "inAppNotifications" to true,
                    "managerAutoNotify" to true
                ),
                "createdAt" to System.currentTimeMillis(),
                "lastUpdated" to System.currentTimeMillis()
            ), SetOptions.merge())

            // 2. Department complaints handlers
            val deptComplaintsRef = firestore.collection("companies")
                .document(user.sanitizedCompanyName)
                .collection("departments")
                .document(user.sanitizedDepartment)
                .collection("complaints_handlers")
                .document("config")

            val handlers = mutableListOf<String>()
            when {
                user.role.contains("Administrator", ignoreCase = true) -> handlers.add(user.userId)
                user.role.contains("Manager", ignoreCase = true) -> handlers.add(user.userId)
                user.role.contains("HR", ignoreCase = true) -> handlers.add(user.userId)
                user.role.contains("Team Lead", ignoreCase = true) -> handlers.add(user.userId)
            }

            batch.set(deptComplaintsRef, mapOf(
                "primaryHandlers" to handlers,
                "backupHandlers" to listOf<String>(),
                "autoAssignEnabled" to true,
                "escalationChain" to handlers,
                "workingHours" to mapOf(
                    "start" to "09:00",
                    "end" to "18:00",
                    "timezone" to "UTC"
                ),
                "lastUpdated" to System.currentTimeMillis()
            ), SetOptions.merge())

            // 3. User complaint profile
            val userComplaintProfileRef = firestore.collection("user_complaint_profiles")
                .document(user.userId)

            batch.set(userComplaintProfileRef, mapOf(
                "userId" to user.userId,
                "canSubmit" to true,
                "canView" to when(user.role.trim()) {
                    "Administrator", "HR" -> "all"
                    "Manager" -> "department"
                    "Team Lead" -> "team"
                    else -> "own"
                },
                "canResolve" to listOf("Administrator", "HR", "Manager").contains(user.role.trim()),
                "canEscalate" to listOf("Administrator", "HR", "Manager", "Team Lead").contains(user.role.trim()),
                "department" to user.department,
                "sanitizedDepartment" to user.sanitizedDepartment,
                "companyName" to user.companyName,
                "sanitizedCompanyName" to user.sanitizedCompanyName,
                "notificationPreferences" to mapOf(
                    "email" to true,
                    "inApp" to true,
                    "sms" to false
                ),
                "createdAt" to System.currentTimeMillis()
            ))

            // 4. Initialize complaints analytics for the user's department
            val complaintsAnalyticsRef = firestore.collection("complaints_analytics")
                .document(user.sanitizedCompanyName)
                .collection("departments")
                .document(user.sanitizedDepartment)

            batch.set(complaintsAnalyticsRef, mapOf(
                "totalComplaints" to 0,
                "resolvedComplaints" to 0,
                "pendingComplaints" to 0,
                "averageResolutionTime" to 0,
                "satisfactionRating" to 0.0,
                "categoryCounts" to mapOf<String, Int>(),
                "monthlyStats" to mapOf<String, Int>(),
                "lastUpdated" to System.currentTimeMillis()
            ), SetOptions.merge())

            Log.d("ProfileCompletion", "Complaints module initialized successfully for user: ${user.userId}")

        } catch (e: Exception) {
            Log.e("ProfileCompletion", "Error initializing complaints module", e)
        }
    }
}