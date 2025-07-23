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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class RegistrationActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var isRegistering = mutableStateOf(false)

    companion object {
        const val TAG = "Registration"
        const val DEFAULT_ROLE = "Administrator"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()

        Log.d(TAG, "🚀 RegistrationActivity started")

        setContent {
            Ritik_2Theme {
                RegistrationScreen(
                    onRegisterClick = { email, password, name, phoneNumber, designation, companyName,
                                        experience, completedProjects, activeProjects, complaints, imageUri, role ->

                        val finalRole = DEFAULT_ROLE
                        Log.d(TAG, "📝 Registration clicked for email: $email")

                        performRegistration(
                            email, password, name, phoneNumber, designation, companyName,
                            experience, completedProjects, activeProjects, complaints, imageUri, finalRole
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
        role: String = DEFAULT_ROLE
    ) {
        Log.d(TAG, "🔄 Starting registration process for: $email")

        if (isRegistering.value) {
            Toast.makeText(this, "Registration in progress, please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        if (!validateRegistrationInput(email, password, name, companyName, designation)) {
            Log.w(TAG, "❌ Registration validation failed")
            return
        }

        isRegistering.value = true

        // Create Firebase Auth user
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = firebaseAuth.currentUser?.uid
                    if (userId != null) {
                        Log.d(TAG, "✅ Firebase Auth user created successfully with ID: $userId")

                        // Give Firebase a moment to properly set up the auth context
                        android.os.Handler().postDelayed({
                            // Start creating user documents immediately
                            createCompleteUserStructure(
                                userId, email, name, phoneNumber, designation, companyName,
                                experience, completedProjects, activeProjects, complaints, imageUri, role
                            )
                        }, 1000) // 1 second delay to ensure auth context is ready

                    } else {
                        Log.e(TAG, "❌ Failed to get user ID after successful auth creation")
                        Toast.makeText(this, "Failed to get user ID", Toast.LENGTH_SHORT).show()
                        isRegistering.value = false
                    }
                } else {
                    val errorMessage = task.exception?.message ?: "Unknown error"
                    Log.e(TAG, "❌ Firebase Auth registration failed: $errorMessage", task.exception)
                    Toast.makeText(
                        this, "Registration failed: $errorMessage", Toast.LENGTH_LONG
                    ).show()
                    isRegistering.value = false
                }
            }
    }

    private fun createCompleteUserStructure(
        userId: String,
        email: String,
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
        Log.d(TAG, "🏗️ Creating complete user structure for: $userId")

        // Check if user is properly authenticated
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null || currentUser.uid != userId) {
            Log.e(TAG, "❌ User authentication mismatch or null")
            Toast.makeText(this, "Authentication error. Please try again.", Toast.LENGTH_LONG).show()
            isRegistering.value = false
            return
        }

        Log.d(TAG, "✅ User authentication verified: ${currentUser.email}")

        if (imageUri != null) {
            uploadImageThenCreateStructure(
                userId, imageUri, email, name, phoneNumber, designation, companyName,
                experience, completedProjects, activeProjects, complaints, role
            )
        } else {
            createUserDocuments(
                userId, "", email, name, phoneNumber, designation, companyName,
                experience, completedProjects, activeProjects, complaints, role
            )
        }
    }

    private fun uploadImageThenCreateStructure(
        userId: String,
        imageUri: Uri,
        email: String,
        name: String,
        phoneNumber: String,
        designation: String,
        companyName: String,
        experience: Int,
        completedProjects: Int,
        activeProjects: Int,
        complaints: Int,
        role: String
    ) {
        Log.d(TAG, "📸 Uploading image for user: $userId")
        val sanitizedCompanyName = sanitizeDocumentId(companyName)
        val storageRef = storage.reference.child("users/$sanitizedCompanyName/$role/$userId/profile.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Image uploaded successfully")
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    Log.d(TAG, "✅ Download URL obtained: $downloadUri")

                    // Update Firebase Auth profile
                    firebaseAuth.currentUser?.updateProfile(userProfileChangeRequest {
                        photoUri = downloadUri
                    })

                    // Create user documents with image URL
                    createUserDocuments(
                        userId, downloadUri.toString(), email, name, phoneNumber, designation, companyName,
                        experience, completedProjects, activeProjects, complaints, role
                    )
                }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "❌ Failed to get download URL", exception)
                        // Continue without image
                        createUserDocuments(
                            userId, "", email, name, phoneNumber, designation, companyName,
                            experience, completedProjects, activeProjects, complaints, role
                        )
                    }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Image upload failed", exception)
                // Continue without image
                createUserDocuments(
                    userId, "", email, name, phoneNumber, designation, companyName,
                    experience, completedProjects, activeProjects, complaints, role
                )
            }
    }

    private fun createUserDocuments(
        userId: String,
        imageUrl: String,
        email: String,
        name: String,
        phoneNumber: String,
        designation: String,
        companyName: String,
        experience: Int,
        completedProjects: Int,
        activeProjects: Int,
        complaints: Int,
        role: String
    ) {
        Log.d(TAG, "📄 Creating user documents for: $userId")

        val timestamp = Timestamp.now()
        val sanitizedCompanyName = sanitizeDocumentId(companyName)

        // Step 1: Create user_access_control document FIRST
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
            "isDefaultAdmin" to (role == DEFAULT_ROLE),
            "documentPath" to "users/$sanitizedCompanyName/$role/$userId",
            "createdAt" to timestamp,
            "createdBy" to "self_registration",
            "lastAccess" to null
        )

        Log.d(TAG, "🔑 Creating user_access_control document...")
        userAccessControlRef.set(accessControlData)
            .addOnSuccessListener {
                Log.d(TAG, "✅ user_access_control created successfully!")

                // Step 2: Create the batch for remaining documents
                createRemainingDocuments(userId, imageUrl, email, name, phoneNumber, designation, companyName,
                    experience, completedProjects, activeProjects, complaints, role, timestamp, sanitizedCompanyName)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to create user_access_control", exception)
                cleanupFailedRegistration(userId)
                Toast.makeText(this, "Registration failed: ${exception.message}", Toast.LENGTH_LONG).show()
                isRegistering.value = false
            }
    }

    private fun createRemainingDocuments(
        userId: String,
        imageUrl: String,
        email: String,
        name: String,
        phoneNumber: String,
        designation: String,
        companyName: String,
        experience: Int,
        completedProjects: Int,
        activeProjects: Int,
        complaints: Int,
        role: String,
        timestamp: Timestamp,
        sanitizedCompanyName: String
    ) {
        Log.d(TAG, "📦 Creating batch for remaining documents...")

        val batch = firestore.batch()

        try {
            // 1. Main user document
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
                "workStats" to mapOf(
                    "experience" to experience,
                    "completedProjects" to completedProjects,
                    "activeProjects" to activeProjects,
                    "pendingTasks" to 0,
                    "completedTasks" to 0,
                    "totalWorkingHours" to 0,
                    "avgPerformanceRating" to 0.0
                ),
                "issues" to mapOf(
                    "totalComplaints" to complaints,
                    "resolvedComplaints" to 0,
                    "pendingComplaints" to complaints,
                    "lastComplaintDate" to if (complaints > 0) timestamp else null
                ),
                "permissions" to getRolePermissions(role),
                "documentPath" to "users/$sanitizedCompanyName/$role/$userId"
            )
            batch.set(userDocRef, userData)

            // 2. Company metadata
            val companyMetaRef = firestore.collection("companies_metadata").document(sanitizedCompanyName)
            val companyMetaData = mapOf(
                "originalName" to companyName,
                "sanitizedName" to sanitizedCompanyName,
                "createdAt" to timestamp,
                "lastUpdated" to timestamp,
                "totalUsers" to com.google.firebase.firestore.FieldValue.increment(1),
                "activeUsers" to com.google.firebase.firestore.FieldValue.increment(1),
                "availableRoles" to com.google.firebase.firestore.FieldValue.arrayUnion(role),
                "adminUsers" to if (role == "Administrator") com.google.firebase.firestore.FieldValue.increment(1) else 0
            )
            batch.set(companyMetaRef, companyMetaData, com.google.firebase.firestore.SetOptions.merge())

            // 3. Role metadata
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
                "lastUpdated" to timestamp,
                "isDefaultRole" to (role == DEFAULT_ROLE)
            )
            batch.set(roleMetaRef, roleMetaData, com.google.firebase.firestore.SetOptions.merge())

            // 4. User search index
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
                "isDefaultAdmin" to (role == DEFAULT_ROLE),
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

            Log.d(TAG, "📦 Committing batch...")

            // Commit batch
            batch.commit()
                .addOnSuccessListener {
                    Log.d(TAG, "🎉 REGISTRATION COMPLETED SUCCESSFULLY!")
                    Toast.makeText(this, "🎉 Registration successful! Welcome, Administrator!", Toast.LENGTH_LONG).show()
                    navigateToProfileCompletion(userId)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "❌ Batch commit failed", exception)
                    cleanupFailedRegistration(userId)
                    Toast.makeText(this, "Registration failed: ${exception.message}", Toast.LENGTH_LONG).show()
                }
                .addOnCompleteListener {
                    isRegistering.value = false
                }

        } catch (exception: Exception) {
            Log.e(TAG, "❌ Exception creating batch", exception)
            cleanupFailedRegistration(userId)
            Toast.makeText(this, "Unexpected error: ${exception.message}", Toast.LENGTH_LONG).show()
            isRegistering.value = false
        }
    }

    private fun cleanupFailedRegistration(userId: String) {
        Log.d(TAG, "🧹 Cleaning up failed registration for user: $userId")

        // Delete Firebase Auth user
        firebaseAuth.currentUser?.delete()?.addOnCompleteListener { deleteTask ->
            if (deleteTask.isSuccessful) {
                Log.d(TAG, "✅ Firebase Auth user deleted successfully")
            } else {
                Log.w(TAG, "⚠️ Failed to delete Firebase Auth user: ${deleteTask.exception?.message}")
            }
        }

        // Clean up any partial Firestore documents
        firestore.collection("user_access_control").document(userId).delete()
            .addOnCompleteListener { Log.d(TAG, "Cleanup: user_access_control delete attempted") }
    }

    private fun getRolePermissions(role: String): List<String> {
        return when (role) {
            "Administrator" -> listOf(
                "create_user", "delete_user", "modify_user", "view_all_users",
                "manage_roles", "view_analytics", "system_settings", "manage_companies",
                "access_all_data", "export_data", "manage_permissions", "create_admin",
                "manage_user_roles", "full_system_access", "company_management"
            )
            "Manager" -> listOf(
                "view_team_users", "modify_team_user", "view_team_analytics",
                "assign_projects", "approve_requests", "view_reports", "manage_team"
            )
            "HR" -> listOf(
                "view_all_users", "modify_user", "view_hr_analytics", "manage_employees",
                "access_personal_data", "generate_reports", "employee_management"
            )
            "Team Lead" -> listOf(
                "view_team_users", "assign_tasks", "view_team_performance", "approve_leave",
                "team_coordination"
            )
            "Employee" -> listOf(
                "view_profile", "edit_profile", "view_assigned_projects", "submit_reports"
            )
            "Intern" -> listOf(
                "view_profile", "edit_basic_profile", "view_assigned_tasks"
            )
            else -> listOf(
                "create_user", "delete_user", "modify_user", "view_all_users",
                "manage_roles", "view_analytics", "system_settings", "manage_companies",
                "access_all_data", "export_data", "manage_permissions"
            )
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
            name.isBlank() || name.length < 2 -> {
                Toast.makeText(this, "Name must be at least 2 characters", Toast.LENGTH_SHORT).show()
                return false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return false
            }
            password.length < 6 -> {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return false
            }
            companyName.isBlank() || companyName.length < 2 -> {
                Toast.makeText(this, "Company name must be at least 2 characters", Toast.LENGTH_SHORT).show()
                return false
            }
            designation.isBlank() -> {
                Toast.makeText(this, "Designation is required", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun navigateToProfileCompletion(userId: String) {
        Log.d(TAG, "🚀 Navigating to profile completion for user: $userId")
        try {
            val intent = ProfileCompletionActivity.createIntent(this, userId)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to profile completion", e)
            Toast.makeText(this, "🎉 Registration completed successfully as Administrator!", Toast.LENGTH_LONG).show()
            navigateToLoginActivity()
        }
    }

    private fun navigateToLoginActivity() {Log.d(TAG, "🚀 Navigating to login activity")
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}