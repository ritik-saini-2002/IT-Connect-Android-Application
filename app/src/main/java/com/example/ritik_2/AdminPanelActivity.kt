package com.example.ritik_2

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.ritik_2.ui.theme.AdminPanelScreen
import com.example.ritik_2.ui.theme.ui.theme.Ritik_2Theme
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminPanelActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var isCreatingUser = mutableStateOf(false)
    private var adminCompanyName = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Verify admin role and load company
        verifyAdminRole(currentUserId)
        loadAdminCompanyName(currentUserId)

        setContent {
            Ritik_2Theme {
                AdminPanelScreen(
                    isCreating = isCreatingUser.value,
                    companyName = adminCompanyName.value, // Auto-filled
                    onCreateUserClick = { name, email, role, department, designation, password ->
                        createUserAccount(
                            name,
                            email,
                            role,
                            department, // Now this is the actual department (IT, HR, etc.)
                            designation,
                            password,
                            currentUserId
                        )
                    }
                )
            }
        }
    }

    private fun verifyAdminRole(userId: String) {
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

    private fun loadAdminCompanyName(adminId: String) {
        // First try to get from user_access_control
        firestore.collection("user_access_control").document(adminId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val companyName = document.getString("companyName")
                    if (!companyName.isNullOrBlank()) {
                        adminCompanyName.value = companyName
                        return@addOnSuccessListener
                    }
                }

                // Fallback: search in user_search_index
                firestore.collection("user_search_index").document(adminId).get()
                    .addOnSuccessListener { searchDoc ->
                        if (searchDoc.exists()) {
                            adminCompanyName.value = searchDoc.getString("companyName") ?: "Default Organization"
                        } else {
                            adminCompanyName.value = "Default Organization"
                        }
                    }
                    .addOnFailureListener {
                        adminCompanyName.value = "Default Organization"
                    }
            }
            .addOnFailureListener {
                adminCompanyName.value = "Default Organization"
            }
    }

    private fun createUserAccount(
        name: String,
        email: String,
        role: String,
        department: String, // This is now the actual department like "IT", "HR", etc.
        designation: String,
        password: String,
        createdBy: String
    ) {
        if (isCreatingUser.value) {
            Toast.makeText(this, "User creation in progress, please wait...", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (!validateUserInput(name, email, department, designation, password)) return

        isCreatingUser.value = true

        // Use admin's company name for the new user
        val companyName = adminCompanyName.value
        val sanitizedCompany = sanitizeDocumentId(companyName)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val newUserId = authResult.user?.uid
                if (newUserId != null) {
                    createStrictUserHierarchy(
                        newUserId,
                        name,
                        email,
                        role,
                        companyName, // Admin's company name
                        sanitizedCompany,
                        department, // User's department (IT, HR, etc.)
                        designation,
                        createdBy
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
        originalCompany: String, // Admin's company name
        sanitizedCompany: String,
        department: String, // User's department (IT, HR, etc.)
        designation: String,
        createdBy: String
    ) {
        val timestamp = Timestamp.now()
        val batch = firestore.batch()
        val sanitizedDepartment = sanitizeDocumentId(department)

        // NEW STRUCTURE: users/{company}/{department}/{role}/users/{userId}
        val userDocRef = firestore
            .collection("users")
            .document(sanitizedCompany)
            .collection(sanitizedDepartment)
            .document(role)
            .collection("users")
            .document(userId)

        val documentPath = "users/$sanitizedCompany/$sanitizedDepartment/$role/users/$userId"

        // Complete user data stored in the hierarchical path
        val userData = mapOf(
            "userId" to userId,
            "name" to name,
            "email" to email,
            "role" to role,
            "companyName" to originalCompany, // Admin's company name
            "sanitizedCompany" to sanitizedCompany,
            "department" to department, // User's actual department (IT, HR, etc.)
            "sanitizedDepartment" to sanitizedDepartment,
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
            "documentPath" to documentPath
        )

        batch.set(userDocRef, userData)

        // Create company metadata (if doesn't exist)
        val companyMetaRef = firestore.collection("companies_metadata").document(sanitizedCompany)
        val companyMetaData = mapOf(
            "originalName" to originalCompany,
            "sanitizedName" to sanitizedCompany,
            "createdAt" to timestamp,
            "lastUpdated" to timestamp,
            "totalUsers" to com.google.firebase.firestore.FieldValue.increment(1),
            "activeUsers" to com.google.firebase.firestore.FieldValue.increment(1),
            "availableRoles" to com.google.firebase.firestore.FieldValue.arrayUnion(role),
            "departments" to com.google.firebase.firestore.FieldValue.arrayUnion(department)
        )
        batch.set(companyMetaRef, companyMetaData, com.google.firebase.firestore.SetOptions.merge())

        // Create role metadata within company and department
        val roleMetaRef = firestore
            .collection("companies_metadata")
            .document(sanitizedCompany)
            .collection("departments_metadata")
            .document(sanitizedDepartment)
            .collection("roles_metadata")
            .document(role)

        val roleMetaData = mapOf(
            "roleName" to role,
            "companyName" to originalCompany,
            "department" to department,
            "permissions" to getRolePermissions(role),
            "userCount" to com.google.firebase.firestore.FieldValue.increment(1),
            "activeUsers" to com.google.firebase.firestore.FieldValue.increment(1),
            "createdAt" to timestamp,
            "lastUpdated" to timestamp
        )
        batch.set(roleMetaRef, roleMetaData, com.google.firebase.firestore.SetOptions.merge())

        // Create department metadata within company
        val departmentMetaRef = firestore
            .collection("companies_metadata")
            .document(sanitizedCompany)
            .collection("departments_metadata")
            .document(sanitizedDepartment)

        val departmentMetaData = mapOf(
            "departmentName" to department,
            "companyName" to originalCompany,
            "sanitizedName" to sanitizedDepartment,
            "userCount" to com.google.firebase.firestore.FieldValue.increment(1),
            "activeUsers" to com.google.firebase.firestore.FieldValue.increment(1),
            "availableRoles" to com.google.firebase.firestore.FieldValue.arrayUnion(role),
            "createdAt" to timestamp,
            "lastUpdated" to timestamp
        )
        batch.set(departmentMetaRef, departmentMetaData, com.google.firebase.firestore.SetOptions.merge())

        // Create user access control for quick authentication checks
        val userAccessControlRef = firestore.collection("user_access_control").document(userId)
        val accessControlData = mapOf(
            "userId" to userId,
            "name" to name,
            "email" to email,
            "companyName" to originalCompany,
            "sanitizedCompany" to sanitizedCompany,
            "department" to department, // User's actual department
            "sanitizedDepartment" to sanitizedDepartment,
            "role" to role,
            "permissions" to getRolePermissions(role),
            "isActive" to true,
            "documentPath" to documentPath,
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
            "companyName" to originalCompany,
            "sanitizedCompany" to sanitizedCompany,
            "department" to department,
            "sanitizedDepartment" to sanitizedDepartment,
            "role" to role,
            "designation" to designation,
            "isActive" to true,
            "documentPath" to documentPath,
            "searchTerms" to listOf(
                name.lowercase(),
                email.lowercase(),
                originalCompany.lowercase(),
                department.lowercase(),
                role.lowercase(),
                designation.lowercase()
            )
        )
        batch.set(userSearchIndexRef, searchIndexData)

        // Commit all operations
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "User created successfully in company directory structure!", Toast.LENGTH_LONG).show()

                // Sign out the newly created user and sign back in the admin
                auth.signOut()

                // Log the created path for debugging
                android.util.Log.d("AdminPanel", "User created at path: $documentPath")
                android.util.Log.d("AdminPanel", "Company: $originalCompany, Department: $department, Role: $role")
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
            "Team Leader" -> listOf(
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
        department: String,
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
            department.isBlank() -> {
                Toast.makeText(this, "Department is required", Toast.LENGTH_SHORT).show()
                return false
            }
            department.length < 2 -> {
                Toast.makeText(this, "Department must be at least 2 characters", Toast.LENGTH_SHORT).show()
                return false
            }
            designation.isBlank() -> {
                Toast.makeText(this, "Designation is required", Toast.LENGTH_SHORT).show()
                return false
            }
            password.length < 4 -> {
                Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}