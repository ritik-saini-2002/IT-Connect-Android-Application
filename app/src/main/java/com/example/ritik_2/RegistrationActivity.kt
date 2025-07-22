package com.example.ritik_2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.ritik_2.ui.theme.RegistrationScreen
import com.example.ritik_2.ui.theme.ui.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.Timestamp

class RegistrationActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var isRegistering = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()

        setContent {
            Ritik_2Theme {
                RegistrationScreen(
                    //isRegistering = isRegistering.value,
                    onRegisterClick = { email, password, name, phoneNumber, designation, companyName,
                                        experience, completedProjects, activeProjects, complaints, imageUri, role ->

                        performRegistration(
                            email, password, name, phoneNumber, designation, companyName,
                            experience, completedProjects, activeProjects, complaints, imageUri, role ?: "Administrator"
                        )
                    },
                    onLoginClick = { navigateToLoginActivity() }
                )
            }
        }
    }

    private fun performRegistration(
        email: String,
        password: String,
        name: String,
        phoneNumber: String,
        designation: String,
        companyName: String,
        experience: Int,
        completedProjects: Int,
        activeProjects: Int,
        complaints: Int,
        imageUri: Uri?,
        role: String
    ) {
        if (isRegistering.value) {
            Toast.makeText(this, "Registration in progress, please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        // Validation
        if (!validateRegistrationInput(email, password, name, companyName, designation)) {
            return
        }

        isRegistering.value = true

        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = firebaseAuth.currentUser?.uid
                    if (userId != null) {
                        Log.d("Registration", "User created with ID: $userId")

                        if (imageUri != null) {
                            uploadImageAndCreateHierarchy(
                                userId, imageUri, name, phoneNumber, designation, companyName,
                                experience, completedProjects, activeProjects, complaints, email, role
                            )
                        } else {
                            createUserHierarchicalStructure(
                                userId, "", name, phoneNumber, designation, companyName,
                                experience, completedProjects, activeProjects, complaints, email, role
                            )
                        }
                    } else {
                        Toast.makeText(this, "Failed to get user ID", Toast.LENGTH_SHORT).show()
                        isRegistering.value = false
                    }
                } else {
                    Toast.makeText(
                        this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT
                    ).show()
                    isRegistering.value = false
                }
            }
    }

    private fun uploadImageAndCreateHierarchy(
        userId: String,
        imageUri: Uri,
        name: String,
        phoneNumber: String,
        designation: String,
        companyName: String,
        experience: Int,
        completedProjects: Int,
        activeProjects: Int,
        complaints: Int,
        email: String,
        role: String
    ) {
        val sanitizedCompanyName = sanitizeDocumentId(companyName)
        val storageRef = storage.reference.child("users/$sanitizedCompanyName/$role/$userId/profile.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    // Update Firebase Auth profile
                    updateUserProfile(downloadUri)

                    // Create hierarchical structure with image URL
                    createUserHierarchicalStructure(
                        userId, downloadUri.toString(), name, phoneNumber, designation, companyName,
                        experience, completedProjects, activeProjects, complaints, email, role
                    )
                }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "Failed to get download URL: ${exception.message}", Toast.LENGTH_SHORT).show()
                        Log.e("Registration", "Download URL failed", exception)
                        isRegistering.value = false
                    }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Image upload failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                Log.e("Registration", "Image upload failed", exception)
                isRegistering.value = false
            }
    }

    private fun updateUserProfile(photoUrl: Uri) {
        firebaseAuth.currentUser?.updateProfile(userProfileChangeRequest {
            photoUri = photoUrl
        })?.addOnCompleteListener { authUpdateTask ->
            if (authUpdateTask.isSuccessful) {
                Log.d("Registration", "Firebase Auth profile updated")
            }
        }
    }

    private fun createUserHierarchicalStructure(
        userId: String,
        imageUrl: String,
        name: String,
        phoneNumber: String,
        designation: String,
        companyName: String,
        experience: Int,
        completedProjects: Int,
        activeProjects: Int,
        complaints: Int,
        email: String,
        role: String
    ) {
        val timestamp = Timestamp.now()
        val sanitizedCompanyName = sanitizeDocumentId(companyName)
        val batch = firestore.batch()

        // 1. STRICT HIERARCHICAL STRUCTURE: users/{companyName}/{role}/{userId}
        val userDocRef = firestore
            .collection("users")
            .document(sanitizedCompanyName)
            .collection(role)
            .document(userId)

        val userData = mapOf(
            "userId" to userId,
            "name" to name,
            "email" to email,
            "role" to role,
            "companyName" to companyName,
            "sanitizedCompanyName" to sanitizedCompanyName,
            "designation" to designation,
            "createdAt" to timestamp,
            "createdBy" to "self_registration",
            "isActive" to true,
            "lastLogin" to null,
            "lastUpdated" to timestamp,

            // User Profile Details
            "profile" to mapOf(
                "imageUrl" to imageUrl,
                "phoneNumber" to phoneNumber,
                "address" to "",
                "dateOfBirth" to null,
                "joiningDate" to timestamp,
                "employeeId" to "",
                "department" to "",
                "reportingTo" to "",
                "salary" to 0,
                "emergencyContact" to mapOf(
                    "name" to "",
                    "phone" to "",
                    "relation" to ""
                )
            ),

            // Work Statistics (from registration form)
            "workStats" to mapOf(
                "experience" to experience,
                "completedProjects" to completedProjects,
                "activeProjects" to activeProjects,
                "pendingTasks" to 0,
                "completedTasks" to 0,
                "totalWorkingHours" to 0,
                "avgPerformanceRating" to 0.0
            ),

            // Issues/Complaints (from registration form)
            "issues" to mapOf(
                "totalComplaints" to complaints,
                "resolvedComplaints" to 0,
                "pendingComplaints" to complaints,
                "lastComplaintDate" to if (complaints > 0) timestamp else null
            ),

            // Permissions based on role
            "permissions" to getRolePermissions(role),

            // Document path for reference
            "documentPath" to "users/$sanitizedCompanyName/$role/$userId"
        )

        batch.set(userDocRef, userData)

        // 2. Create company metadata
        val companyMetaRef = firestore.collection("companies_metadata").document(sanitizedCompanyName)
        val companyMetaData = mapOf(
            "originalName" to companyName,
            "sanitizedName" to sanitizedCompanyName,
            "createdAt" to timestamp,
            "lastUpdated" to timestamp,
            "totalUsers" to com.google.firebase.firestore.FieldValue.increment(1),
            "activeUsers" to com.google.firebase.firestore.FieldValue.increment(1),
            "availableRoles" to com.google.firebase.firestore.FieldValue.arrayUnion(role)
        )
        batch.set(companyMetaRef, companyMetaData, com.google.firebase.firestore.SetOptions.merge())

        // 3. Create role metadata
        val roleMetaRef = firestore
            .collection("companies_metadata")
            .document(sanitizedCompanyName)
            .collection("roles_metadata")
            .document(role)

        val roleMetaData = mapOf(
            "roleName" to role,
            "companyName" to companyName,
            "permissions" to getRolePermissions(role),
            "userCount" to com.google.firebase.firestore.FieldValue.increment(1),
            "activeUsers" to com.google.firebase.firestore.FieldValue.increment(1),
            "createdAt" to timestamp,
            "lastUpdated" to timestamp
        )
        batch.set(roleMetaRef, roleMetaData, com.google.firebase.firestore.SetOptions.merge())

        // 4. Create user access control
        val userAccessControlRef = firestore.collection("user_access_control").document(userId)
        val accessControlData = mapOf(
            "userId" to userId,
            "name" to name,
            "email" to email,
            "companyName" to companyName,
            "sanitizedCompanyName" to sanitizedCompanyName,
            "role" to role,
            "permissions" to getRolePermissions(role),
            "isActive" to true,
            "documentPath" to "users/$sanitizedCompanyName/$role/$userId",
            "createdAt" to timestamp,
            "lastAccess" to null
        )
        batch.set(userAccessControlRef, accessControlData)

        // 5. Create user search index
        val userSearchIndexRef = firestore.collection("user_search_index").document(userId)
        val searchIndexData = mapOf(
            "userId" to userId,
            "name" to name.lowercase(),
            "email" to email.lowercase(),
            "companyName" to companyName,
            "sanitizedCompanyName" to sanitizedCompanyName,
            "role" to role,
            "designation" to designation,
            "isActive" to true,
            "documentPath" to "users/$sanitizedCompanyName/$role/$userId",
            "searchTerms" to listOf(
                name.lowercase(),
                email.lowercase(),
                companyName.lowercase(),
                role.lowercase(),
                designation.lowercase()
            ).filter { it.isNotEmpty() }
        )
        batch.set(userSearchIndexRef, searchIndexData)

        // Execute batch operation
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Registration successful! Please complete your profile.", Toast.LENGTH_LONG).show()
                Log.d("Registration", "User created at path: users/$sanitizedCompanyName/$role/$userId")

                // Navigate to profile completion
                navigateToProfileCompletion(userId)
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error creating user structure: ${exception.message}", Toast.LENGTH_SHORT).show()
                Log.e("Registration", "Failed to create user hierarchy", exception)
            }
            .addOnCompleteListener {
                isRegistering.value = false
            }
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

    private fun sanitizeDocumentId(input: String): String {
        return input
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(100)
    }

    private fun validateRegistrationInput(
        email: String,
        password: String,
        name: String,
        companyName: String,
        designation: String
    ): Boolean {
        when {
            name.isBlank() -> {
                Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
                return false
            }
            name.length < 2 -> {
                Toast.makeText(this, "Name must be at least 2 characters", Toast.LENGTH_SHORT).show()
                return false
            }
            !isValidEmail(email) -> {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return false
            }
            password.length < 6 -> {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return false
            }
            companyName.isBlank() -> {
                Toast.makeText(this, "Company name is required", Toast.LENGTH_SHORT).show()
                return false
            }
            designation.isBlank() -> {
                Toast.makeText(this, "Designation is required", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun navigateToProfileCompletion(userId: String) {
        val intent = ProfileCompletionActivity.createIntent(this, userId)
        startActivity(intent)
        finish()
    }

    private fun navigateToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        const val TAG = "Registration"
    }
}