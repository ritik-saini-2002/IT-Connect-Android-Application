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
                                sanitizedCompanyName = userData["sanitizedCompanyName"] as? String ?: "",
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
                        imageUrl = uploadProfileImage(userId, uri)
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

        // Also update access control and search index
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

        // Create user profile document
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

        // Save user data in hierarchical structure
        try {
            val userDocRef = firestore.document(documentPath)
            batch.set(userDocRef, user.toMap())
        } catch (e: Exception) {
            Log.w("ProfileCompletion", "Could not save to hierarchical path: $documentPath", e)
            // Continue with flat collection save
        }

        // Always save in flat collection for easy access
        val flatUserDocRef = firestore.collection("users").document(userId)
        batch.set(flatUserDocRef, user.toMap())

        // Save profile data in flat collection
        val profileDocRef = firestore.collection("user_profiles").document(userId)
        batch.set(profileDocRef, userProfile.toMap())

        // Save work stats in flat collection
        val statsDocRef = firestore.collection("work_stats").document(userId)
        batch.set(statsDocRef, workStats.toMap())

        // Save search index for easy user lookup
        val searchIndexRef = firestore.collection(FirestorePaths.USER_SEARCH_INDEX).document(userId)
        batch.set(searchIndexRef, mapOf(
            "userId" to userId,
            "name" to user.name,
            "email" to user.email,
            "role" to user.role,
            "companyName" to user.companyName,
            "department" to user.department,
            "designation" to user.designation,
            "documentPath" to documentPath,
            "lastUpdated" to System.currentTimeMillis()
        ))

        // Save user access control
        val accessControlRef = firestore.collection(FirestorePaths.USER_ACCESS_CONTROL).document(userId)
        batch.set(accessControlRef, mapOf(
            "userId" to userId,
            "name" to user.name,
            "email" to user.email,
            "role" to user.role,
            "permissions" to DataUtils.getRolePermissions(user.role),
            "companyName" to user.companyName,
            "sanitizedCompanyName" to user.sanitizedCompanyName,
            "department" to user.department,
            "sanitizedDepartment" to user.sanitizedDepartment,
            "designation" to user.designation,
            "documentPath" to documentPath,
            "lastUpdated" to System.currentTimeMillis()
        ))

        // Update company and department metadata
        updateCompanyMetadata(batch, user)
        updateDepartmentMetadata(batch, user)

        // Commit batch
        batch.commit().await()

        viewModel.setLoading(false)
        Toast.makeText(this@ProfileCompletionActivity,
            "Profile saved successfully!", Toast.LENGTH_SHORT).show()

        setResult(RESULT_OK)
        finish()
    }

    private fun updateAccessControlAndSearchIndex(
        batch: WriteBatch,
        userId: String,
        profileData: ProfileData
    ) {
        // Update access control
        val accessControlRef = firestore.collection("user_access_control").document(userId)
        batch.update(accessControlRef, mapOf(
            "name" to profileData.name,
            "designation" to profileData.designation,
            "lastAccess" to Timestamp.now()
        ))

        // Update search index
        val searchIndexRef = firestore.collection("user_search_index").document(userId)
        batch.update(searchIndexRef, mapOf(
            "name" to profileData.name.lowercase(),
            "designation" to profileData.designation,
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

    private suspend fun uploadProfileImage(userId: String, imageUri: Uri): String {
        return try {
            val imageRef = storage.reference.child("users/companies/{sanitizedCompany}/departments/{sanitizedDepartment}/roles/{role}/users/{userId}/profile.jpg")
            imageRef.putFile(imageUri).await()
            imageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
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
                "lastUpdated" to System.currentTimeMillis()
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
                "lastUpdated" to System.currentTimeMillis()
            )

            batch.set(deptRef, departmentData, SetOptions.merge())

        } catch (e: Exception) {
            Log.e("ProfileCompletion", "Error updating department metadata", e)
        }
    }
}