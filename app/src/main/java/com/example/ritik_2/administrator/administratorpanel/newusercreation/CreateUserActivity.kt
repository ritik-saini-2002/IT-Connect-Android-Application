package com.example.ritik_2.administrator.administratorpanel.newusercreation

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.ritik_2.theme.Ritik_2Theme
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class CreateUserActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var isCreatingUser = mutableStateOf(false)
    private var adminCompanyName = mutableStateOf("")

    // Store admin credentials to re-sign in after creating new user
    private var adminEmail: String = ""
    private var adminPassword: String = ""
    private var adminUserId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        adminUserId = currentUserId
        adminEmail = auth.currentUser?.email ?: ""

        verifyAdminRole(currentUserId)
        loadAdminCompanyName(currentUserId)

        setContent {
            Ritik_2Theme {
                CreateUserScreen(
                    isCreating = isCreatingUser.value,
                    companyName = adminCompanyName.value,
                    onCreateUserClick = { name, email, role, department, designation, password ->
                        createUserAccount(
                            name,
                            email,
                            role,
                            department,
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
        firestore.collection("user_access_control").document(adminId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val companyName = document.getString("companyName")
                    if (!companyName.isNullOrBlank()) {
                        adminCompanyName.value = companyName
                        return@addOnSuccessListener
                    }
                }
                firestore.collection("user_search_index").document(adminId).get()
                    .addOnSuccessListener { searchDoc ->
                        adminCompanyName.value = if (searchDoc.exists())
                            searchDoc.getString("companyName") ?: "Default Organization"
                        else "Default Organization"
                    }
                    .addOnFailureListener { adminCompanyName.value = "Default Organization" }
            }
            .addOnFailureListener { adminCompanyName.value = "Default Organization" }
    }

    /**
     * Creates the new user account using the Firebase Admin SDK pattern:
     * 1. Save admin's current session token / uid
     * 2. Create new user (Firebase Auth will auto-switch to new user session)
     * 3. Immediately sign the new user out
     * 4. Sign the admin back in using their stored credentials
     *
     * Since we don't store the admin password in the app,
     * we use a secondary FirebaseAuth instance so the main auth
     * session is never disturbed.
     */
    private fun createUserAccount(
        name: String,
        email: String,
        role: String,
        department: String,
        designation: String,
        password: String,
        createdBy: String
    ) {
        if (isCreatingUser.value) {
            Toast.makeText(this, "User creation in progress, please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        if (!validateUserInput(name, email, department, designation, password)) return

        isCreatingUser.value = true

        val companyName = adminCompanyName.value
        val sanitizedCompany = sanitizeDocumentId(companyName)

        // ── KEY FIX ──────────────────────────────────────────────────────────
        // Use a SECONDARY FirebaseAuth instance so the admin session is
        // never switched away from. The secondary instance creates the account
        // and is then discarded — the primary instance (admin) stays signed in.
        val secondaryApp = com.google.firebase.FirebaseApp.getApps(this).firstOrNull { it.name == "secondary" }
            ?: com.google.firebase.FirebaseApp.initializeApp(
                this,
                com.google.firebase.FirebaseApp.getInstance().options,
                "secondary"
            )
        val secondaryAuth = com.google.firebase.auth.FirebaseAuth.getInstance(secondaryApp!!)

        secondaryAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val newUserId = authResult.user?.uid
                if (newUserId != null) {
                    // Sign out the new user from the secondary instance immediately
                    secondaryAuth.signOut()

                    // Admin (primary auth) is still signed in — untouched
                    Log.d("AdminPanel", "Admin still signed in: ${auth.currentUser?.uid}")

                    createStrictUserHierarchy(
                        newUserId,
                        name,
                        email,
                        role,
                        companyName,
                        sanitizedCompany,
                        department,
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
        originalCompany: String,
        sanitizedCompany: String,
        department: String,
        designation: String,
        createdBy: String
    ) {
        val timestamp = Timestamp.now()
        val batch = firestore.batch()
        val sanitizedDepartment = sanitizeDocumentId(department)

        val userDocRef = firestore
            .collection("users")
            .document(sanitizedCompany)
            .collection(sanitizedDepartment)
            .document(role)
            .collection("users")
            .document(userId)

        val documentPath = "users/$sanitizedCompany/$sanitizedDepartment/$role/users/$userId"

        val userData = mapOf(
            "userId" to userId,
            "name" to name,
            "email" to email,
            "role" to role,
            "companyName" to originalCompany,
            "sanitizedCompany" to sanitizedCompany,
            "department" to department,
            "sanitizedDepartment" to sanitizedDepartment,
            "designation" to designation,
            "createdAt" to timestamp,
            "createdBy" to createdBy,
            "isActive" to true,
            "lastLogin" to null,
            "lastUpdated" to timestamp,
            "profile" to mapOf(
                "imageUrl" to "",
                "phoneNumber" to "",
                "address" to "",
                "dateOfBirth" to null,
                "joiningDate" to timestamp,
                "employeeId" to "",
                "reportingTo" to "",
                "salary" to 0,
                "emergencyContact" to mapOf("name" to "", "phone" to "", "relation" to "")
            ),
            "workStats" to mapOf(
                "experience" to 0,
                "completedProjects" to 0,
                "activeProjects" to 0,
                "pendingTasks" to 0,
                "completedTasks" to 0,
                "totalWorkingHours" to 0,
                "avgPerformanceRating" to 0.0
            ),
            "issues" to mapOf(
                "totalComplaints" to 0,
                "resolvedComplaints" to 0,
                "pendingComplaints" to 0,
                "lastComplaintDate" to null
            ),
            "permissions" to getRolePermissions(role),
            "documentPath" to documentPath
        )
        batch.set(userDocRef, userData)

        val companyMetaRef = firestore.collection("companies_metadata").document(sanitizedCompany)
        batch.set(
            companyMetaRef,
            mapOf(
                "originalName" to originalCompany,
                "sanitizedName" to sanitizedCompany,
                "createdAt" to timestamp,
                "lastUpdated" to timestamp,
                "totalUsers" to FieldValue.increment(1),
                "activeUsers" to FieldValue.increment(1),
                "availableRoles" to FieldValue.arrayUnion(role),
                "departments" to FieldValue.arrayUnion(department)
            ),
            SetOptions.merge()
        )

        val roleMetaRef = firestore
            .collection("companies_metadata")
            .document(sanitizedCompany)
            .collection("departments_metadata")
            .document(sanitizedDepartment)
            .collection("roles_metadata")
            .document(role)
        batch.set(
            roleMetaRef,
            mapOf(
                "roleName" to role,
                "companyName" to originalCompany,
                "department" to department,
                "permissions" to getRolePermissions(role),
                "userCount" to FieldValue.increment(1),
                "activeUsers" to FieldValue.increment(1),
                "createdAt" to timestamp,
                "lastUpdated" to timestamp
            ),
            SetOptions.merge()
        )

        val departmentMetaRef = firestore
            .collection("companies_metadata")
            .document(sanitizedCompany)
            .collection("departments_metadata")
            .document(sanitizedDepartment)
        batch.set(
            departmentMetaRef,
            mapOf(
                "departmentName" to department,
                "companyName" to originalCompany,
                "sanitizedName" to sanitizedDepartment,
                "userCount" to FieldValue.increment(1),
                "activeUsers" to FieldValue.increment(1),
                "availableRoles" to FieldValue.arrayUnion(role),
                "createdAt" to timestamp,
                "lastUpdated" to timestamp
            ),
            SetOptions.merge()
        )

        val userAccessControlRef = firestore.collection("user_access_control").document(userId)
        batch.set(
            userAccessControlRef,
            mapOf(
                "userId" to userId,
                "name" to name,
                "email" to email,
                "companyName" to originalCompany,
                "sanitizedCompany" to sanitizedCompany,
                "department" to department,
                "sanitizedDepartment" to sanitizedDepartment,
                "role" to role,
                "permissions" to getRolePermissions(role),
                "isActive" to true,
                "documentPath" to documentPath,
                "createdAt" to timestamp,
                "lastAccess" to null
            )
        )

        val userSearchIndexRef = firestore.collection("user_search_index").document(userId)
        batch.set(
            userSearchIndexRef,
            mapOf(
                "userId" to userId,
                "name" to name.lowercase(),
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
                    name.lowercase(), email.lowercase(),
                    originalCompany.lowercase(), department.lowercase(),
                    role.lowercase(), designation.lowercase()
                )
            )
        )

        batch.commit()
            .addOnSuccessListener {
                // Show success with the user's name — admin remains signed in
                Toast.makeText(
                    this,
                    "✓ $name has been successfully added to $originalCompany",
                    Toast.LENGTH_LONG
                ).show()

                Log.d("AdminPanel", "User created at: $documentPath")
                Log.d("AdminPanel", "Admin session preserved: ${auth.currentUser?.uid}")
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error creating user: ${exception.message}", Toast.LENGTH_SHORT).show()
                Log.e("AdminPanel", "Failed to create user hierarchy", exception)
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
                "view_all_users", "modify_user", "view_hr_analytics",
                "manage_employees", "access_personal_data", "generate_reports"
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
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(100)
    }

    private fun validateUserInput(
        name: String,
        email: String,
        department: String,
        designation: String,
        password: String
    ): Boolean {
        when {
            name.isBlank() -> Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
            name.length < 2 -> Toast.makeText(this, "Name must be at least 2 characters", Toast.LENGTH_SHORT).show()
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            department.isBlank() -> Toast.makeText(this, "Department is required", Toast.LENGTH_SHORT).show()
            department.length < 2 -> Toast.makeText(this, "Department must be at least 2 characters", Toast.LENGTH_SHORT).show()
            designation.isBlank() -> Toast.makeText(this, "Designation is required", Toast.LENGTH_SHORT).show()
            password.length < 4 -> Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
            else -> return true
        }
        return false
    }
}