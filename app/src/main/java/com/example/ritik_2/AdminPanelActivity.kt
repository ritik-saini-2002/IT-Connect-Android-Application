package com.example.ritik_2

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.ritik_2.ui.theme.AdminPanelScreen
import com.example.ritik_2.ui.theme.ui.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class AdminPanelActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var isCreatingUser = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Verify admin role
        verifyAdminRole(currentUserId)

        setContent {
            Ritik_2Theme {
                AdminPanelScreen(
                    isCreating = isCreatingUser.value,
                    onCreateUserClick = { name, email, role, companyName, designation, password ->
                        createUserAccount(name, email, role, companyName, designation, password, currentUserId)
                    }
                )
            }
        }
    }

    private fun verifyAdminRole(userId: String) {
        // Check in user_access_control collection for role verification
        firestore.collection("user_access_control").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val role = doc.getString("role")
                    val permissions = doc.get("permissions") as? List<String>

                    if (role != "Administrator" && permissions?.contains("create_user") != true) {
                        Toast.makeText(this, "Access denied: Administrator privileges required", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this, "User access control not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error verifying permissions: ${exception.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun createUserAccount(
        name: String,
        email: String,
        role: String,
        companyName: String,
        designation: String,
        password: String,
        createdBy: String
    ) {
        if (isCreatingUser.value) {
            Toast.makeText(this, "User creation in progress, please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        // Validation
        if (!validateUserInput(name, email, companyName, designation, password)) {
            return
        }

        isCreatingUser.value = true

        // Sanitize company name for use as document ID (remove special characters, spaces to underscores)
        val sanitizedCompanyName = sanitizeDocumentId(companyName)

        // Create user authentication
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val newUserId = authResult.user?.uid
                if (newUserId != null) {
                    // Create the strict hierarchical structure
                    createStrictUserHierarchy(
                        newUserId, name, email, role, companyName, sanitizedCompanyName, designation, createdBy
                    )
                } else {
                    Toast.makeText(this, "Failed to create user account", Toast.LENGTH_SHORT).show()
                    isCreatingUser.value = false
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Registration failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                isCreatingUser.value = false
            }
    }

    private fun createStrictUserHierarchy(
        userId: String,
        name: String,
        email: String,
        role: String,
        originalCompanyName: String,
        sanitizedCompanyName: String,
        designation: String,
        createdBy: String
    ) {
        val timestamp = Timestamp.now()
        val batch = firestore.batch()

        // STRICT STRUCTURE: users/{companyName}/{role}/{userId}
        val userDocRef = firestore
            .collection("users")
            .document(sanitizedCompanyName)
            .collection(role)
            .document(userId)

        // Complete user data stored in the hierarchical path
        val userData = mapOf(
            "userId" to userId,
            "name" to name,
            "email" to email,
            "role" to role,
            "companyName" to originalCompanyName,
            "sanitizedCompanyName" to sanitizedCompanyName,
            "designation" to designation,
            "createdAt" to timestamp,
            "createdBy" to createdBy,
            "isActive" to true,
            "lastLogin" to null,
            "lastUpdated" to timestamp,

            // User Profile Details
            "profile" to mapOf(
                "imageUrl" to "",
                "phoneNumber" to "",
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

            // Work Statistics
            "workStats" to mapOf(
                "experience" to 0,
                "completedProjects" to 0,
                "activeProjects" to 0,
                "pendingTasks" to 0,
                "completedTasks" to 0,
                "totalWorkingHours" to 0,
                "avgPerformanceRating" to 0.0
            ),

            // Complaints & Issues
            "issues" to mapOf(
                "totalComplaints" to 0,
                "resolvedComplaints" to 0,
                "pendingComplaints" to 0,
                "lastComplaintDate" to null
            ),

            // Permissions for this user
            "permissions" to getRolePermissions(role),

            // Full path for reference
            "documentPath" to "users/$sanitizedCompanyName/$role/$userId"
        )

        batch.set(userDocRef, userData)

        // Create company metadata (if doesn't exist)
        val companyMetaRef = firestore.collection("companies_metadata").document(sanitizedCompanyName)
        val companyMetaData = mapOf(
            "originalName" to originalCompanyName,
            "sanitizedName" to sanitizedCompanyName,
            "createdAt" to timestamp,
            "lastUpdated" to timestamp,
            "totalUsers" to com.google.firebase.firestore.FieldValue.increment(1),
            "activeUsers" to com.google.firebase.firestore.FieldValue.increment(1),
            "availableRoles" to com.google.firebase.firestore.FieldValue.arrayUnion(role)
        )
        batch.set(companyMetaRef, companyMetaData, com.google.firebase.firestore.SetOptions.merge())

        // Create role metadata within company
        val roleMetaRef = firestore
            .collection("companies_metadata")
            .document(sanitizedCompanyName)
            .collection("roles_metadata")
            .document(role)

        val roleMetaData = mapOf(
            "roleName" to role,
            "companyName" to originalCompanyName,
            "permissions" to getRolePermissions(role),
            "userCount" to com.google.firebase.firestore.FieldValue.increment(1),
            "activeUsers" to com.google.firebase.firestore.FieldValue.increment(1),
            "createdAt" to timestamp,
            "lastUpdated" to timestamp
        )
        batch.set(roleMetaRef, roleMetaData, com.google.firebase.firestore.SetOptions.merge())

        // Create user access control for quick authentication checks
        val userAccessControlRef = firestore.collection("user_access_control").document(userId)
        val accessControlData = mapOf(
            "userId" to userId,
            "name" to name,
            "email" to email,
            "companyName" to originalCompanyName,
            "sanitizedCompanyName" to sanitizedCompanyName,
            "role" to role,
            "permissions" to getRolePermissions(role),
            "isActive" to true,
            "documentPath" to "users/$sanitizedCompanyName/$role/$userId",
            "createdAt" to timestamp,
            "lastAccess" to null
        )
        batch.set(userAccessControlRef, accessControlData)

        // Create user search index for quick searches across all companies
        val userSearchIndexRef = firestore.collection("user_search_index").document(userId)
        val searchIndexData = mapOf(
            "userId" to userId,
            "name" to name.lowercase(), // For case-insensitive search
            "email" to email.lowercase(),
            "companyName" to originalCompanyName,
            "sanitizedCompanyName" to sanitizedCompanyName,
            "role" to role,
            "designation" to designation,
            "isActive" to true,
            "documentPath" to "users/$sanitizedCompanyName/$role/$userId",
            "searchTerms" to listOf(
                name.lowercase(),
                email.lowercase(),
                originalCompanyName.lowercase(),
                role.lowercase(),
                designation.lowercase()
            )
        )
        batch.set(userSearchIndexRef, searchIndexData)

        // Commit all operations
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "User created successfully in company directory structure!", Toast.LENGTH_LONG).show()

                // Sign out the newly created user
                auth.signOut()

                // Log the created path for debugging
                android.util.Log.d("AdminPanel", "User created at path: users/$sanitizedCompanyName/$role/$userId")
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error creating user structure: ${exception.message}", Toast.LENGTH_SHORT).show()
                android.util.Log.e("AdminPanel", "Failed to create user", exception)
            }
            .addOnCompleteListener {
                isCreatingUser.value = false
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
            .replace(Regex("[^a-zA-Z0-9_-]"), "_") // Replace special chars with underscore
            .replace(Regex("_+"), "_") // Replace multiple underscores with single
            .trim('_') // Remove leading/trailing underscores
            .take(100) // Firestore document ID limit
    }

    private fun validateUserInput(
        name: String,
        email: String,
        companyName: String,
        designation: String,
        password: String
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
            companyName.isBlank() -> {
                Toast.makeText(this, "Company name is required", Toast.LENGTH_SHORT).show()
                return false
            }
            companyName.length < 2 -> {
                Toast.makeText(this, "Company name must be at least 2 characters", Toast.LENGTH_SHORT).show()
                return false
            }
            designation.isBlank() -> {
                Toast.makeText(this, "Designation is required", Toast.LENGTH_SHORT).show()
                return false
            }
            password.length < 6 -> {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}