package com.example.ritik_2.administrator.administratorpanel

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ritik_2.administrator.administratorpanel.databasemanager.DatabaseManagerActivity
import com.example.ritik_2.administrator.administratorpanel.newusercreation.CreateUserActivity
import com.example.ritik_2.administrator.administratorpanel.usermanagement.ManageUserActivity
import com.example.ritik_2.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*

class AdministratorPanelActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // State variables - Using ArrayList() instead of emptyList() or listOf()
    private var adminData = mutableStateOf<AdminData?>(null)
    var departmentData = mutableStateOf<List<DepartmentData>>(ArrayList())
    private var organizationStats = mutableStateOf<OrganizationStats?>(null)
    private var isLoading = mutableStateOf(true)
    private var hasAccess = mutableStateOf(false)

    // Firebase listeners for real-time updates
    private var companyMetadataListener: ListenerRegistration? = null
    //private var departmentListeners: MutableList<ListenerRegistration> = mutableListOf()
    private var userSearchListener: ListenerRegistration? = null

    data class AdminData(
        val name: String,
        val email: String,
        val companyName: String,
        val role: String,
        val department: String,
        val permissions: List<String>,
        val imageUrl: Uri? = null  // Add this field
    )

    data class DepartmentData(
        val name: String,
        val sanitizedName: String,
        val userCount: Int,
        val availableRoles: List<String>
    )

    data class OrganizationStats(
        val totalUsers: Int,
        val totalDepartments: Int,
        val totalRoles: Int
    )

    data class AdminFunction(
        val id: String,
        val title: String,
        val description: String,
        val icon: ImageVector,
        val color: Color,
        val activityClass: Class<*>? = null,
        val permissions: List<String> = ArrayList()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        verifyAdminAccess(currentUserId)

        setContent {
            Ritik_2Theme {
                AdministratorPanelScreen(
                    adminData = adminData.value,
                    departmentData = departmentData.value,
                    organizationStats = organizationStats.value,
                    isLoading = isLoading.value,
                    hasAccess = hasAccess.value,
                    onFunctionClick = { adminFunction ->
                        handleFunctionClick(adminFunction)
                    }
                )
            }
        }
    }

    private fun verifyAdminAccess(userId: String) {
        firestore.collection("user_access_control").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val role = doc.getString("role")
                    val permissions = doc.get("permissions") as? List<String>

                    if (role == "Administrator" || permissions?.contains("access_admin_panel") == true) {
                        hasAccess.value = true
                        setupRealtimeDataListeners(userId)
                    } else {
                        Toast.makeText(this, "Access denied: Administrator privileges required", Toast.LENGTH_LONG).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this, "User access control not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error verifying access: ${exception.message}", Toast.LENGTH_SHORT).show()
                Log.e("AdminPanel", "Error verifying access", exception)
                finish()
            }
    }

    private fun setupRealtimeDataListeners(adminId: String) {
        // Listen to admin data changes
        firestore.collection("user_access_control").document(adminId)
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    Log.e("AdminPanel", "Error listening to admin data", error)
                    return@addSnapshotListener
                }

                if (doc != null && doc.exists()) {
                    val name = doc.getString("name") ?: "Administrator"
                    val email = doc.getString("email") ?: ""
                    val companyName = doc.getString("companyName") ?: "Default Organization"
                    val role = doc.getString("role") ?: "Administrator"
                    val department = doc.getString("department") ?: "Administration"
                    val permissions = doc.get("permissions") as? List<String> ?: ArrayList()
                    val sanitizedCompany = doc.getString("sanitizedCompanyName") ?: ""
                    val documentPath = doc.getString("documentPath") ?: ""

                    // Fetch image URL from the actual user document
                    if (documentPath.isNotEmpty()) {
                        firestore.document(documentPath).get()
                            .addOnSuccessListener { userDoc ->
                                val imageUrlString = if (userDoc.exists()) {
                                    // Try to get from profile map first
                                    val profile = userDoc.get("profile") as? Map<String, Any>
                                    profile?.get("imageUrl")?.toString()
                                        ?: userDoc.getString("imageUrl") // Fallback to direct field
                                } else {
                                    null
                                }

                                val imageUri = if (!imageUrlString.isNullOrEmpty()) {
                                    try {
                                        Uri.parse(imageUrlString)
                                    } catch (e: Exception) {
                                        Log.e("AdminPanel", "Error parsing image URL", e)
                                        null
                                    }
                                } else null

                                adminData.value = AdminData(
                                    name = name,
                                    email = email,
                                    companyName = companyName,
                                    role = role,
                                    department = department,
                                    permissions = permissions,
                                    imageUrl = imageUri
                                )

                                Log.d("AdminPanel", "Admin data updated with image: $imageUrlString")
                            }
                            .addOnFailureListener { e ->
                                Log.e("AdminPanel", "Error fetching user document for image", e)
                                // Set admin data without image
                                adminData.value = AdminData(
                                    name = name,
                                    email = email,
                                    companyName = companyName,
                                    role = role,
                                    department = department,
                                    permissions = permissions,
                                    imageUrl = null
                                )
                            }
                    } else {
                        // No document path, set admin data without image
                        adminData.value = AdminData(
                            name = name,
                            email = email,
                            companyName = companyName,
                            role = role,
                            department = department,
                            permissions = permissions,
                            imageUrl = null
                        )
                    }

                    if (sanitizedCompany.isNotEmpty()) {
                        setupCompanyDataListeners(sanitizedCompany)
                    }
                }
            }
    }

    private fun setupCompanyDataListeners(sanitizedCompany: String) {
        try {
            // Listen to company metadata changes
            companyMetadataListener = firestore.collection("companies_metadata")
                .document(sanitizedCompany)
                .addSnapshotListener { doc, error ->
                    if (error != null) {
                        Log.e("AdminPanel", "Error listening to company metadata", error)
                        return@addSnapshotListener
                    }

                    if (doc != null && doc.exists()) {
                        updateOrganizationStats(sanitizedCompany, doc.data)
                    }
                }

            // Listen to departments metadata changes
            firestore.collection("companies_metadata")
                .document(sanitizedCompany)
                .collection("departments_metadata")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("AdminPanel", "Error listening to departments", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val departments = mutableListOf<DepartmentData>()

                        for (doc in snapshot.documents) {
                            val name = doc.getString("departmentName") ?: ""
                            val sanitizedName = doc.getString("sanitizedName") ?: ""
                            val userCount = doc.getLong("userCount")?.toInt() ?: 0
                            val availableRoles = doc.get("availableRoles") as? List<String> ?: ArrayList()

                            if (name.isNotEmpty()) {
                                departments.add(
                                    DepartmentData(
                                        name = name,
                                        sanitizedName = sanitizedName,
                                        userCount = userCount,
                                        availableRoles = availableRoles
                                    )
                                )
                            }
                        }

                        departmentData.value = departments.sortedByDescending { it.userCount }
                        updateOrganizationStatsFromDepartments(departments, sanitizedCompany)
                    }
                    isLoading.value = false
                }

            // Also setup enhanced real-time listeners for more accurate data
            setupEnhancedRealTimeListeners(sanitizedCompany)
        } catch (e: Exception) {
            Log.e("AdminPanel", "Error setting up company data listeners", e)
            isLoading.value = false
        }
    }

    private fun updateOrganizationStats(sanitizedCompany: String, companyData: Map<String, Any>?) {
        if (companyData == null) return

        try {
            val totalUsers = (companyData["totalUsers"] as? Long)?.toInt() ?: 0
            val departments = companyData["departments"] as? List<String> ?: ArrayList()
            val availableRoles = companyData["availableRoles"] as? List<String> ?: ArrayList()

            organizationStats.value = OrganizationStats(
                totalUsers = totalUsers,
                totalDepartments = departments.size,
                totalRoles = availableRoles.size
            )
        } catch (e: Exception) {
            Log.e("AdminPanel", "Error updating organization stats", e)
        }
    }

    private fun updateOrganizationStatsFromDepartments(departments: List<DepartmentData>, sanitizedCompany: String) {
        try {
            val totalUsers = departments.sumOf { it.userCount }
            val totalDepartments = departments.size
            val allRoles = departments.flatMap { it.availableRoles }.distinct()

            organizationStats.value = OrganizationStats(
                totalUsers = totalUsers,
                totalDepartments = totalDepartments,
                totalRoles = allRoles.size
            )
        } catch (e: Exception) {
            Log.e("AdminPanel", "Error updating stats from departments", e)
        }
    }

    private fun handleFunctionClick(adminFunction: AdminFunction) {
        try {
            when (adminFunction.id) {
                "create_user" -> {
                    startActivity(Intent(this, CreateUserActivity::class.java))
                }
                "manage_users" -> {
                    startActivity(Intent(this, ManageUserActivity::class.java))
                }
                "database_manager" -> {
                    startActivity(Intent(this, DatabaseManagerActivity::class.java))
                }
                "company_settings" -> {
                    Toast.makeText(this, "Navigate to Company Settings", Toast.LENGTH_SHORT).show()
                }
                "role_management" -> {
                    Toast.makeText(this, "Navigate to Role Management", Toast.LENGTH_SHORT).show()
                }
                "reports" -> {
                    Toast.makeText(this, "Navigate to Reports & Export", Toast.LENGTH_SHORT).show()
                }
                "system_settings" -> {
                    Toast.makeText(this, "Navigate to System Settings", Toast.LENGTH_SHORT).show()
                }
                "audit_logs" -> {
                    Toast.makeText(this, "Navigate to Audit Logs", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "Feature coming soon!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("AdminPanel", "Error handling function click", e)
            Toast.makeText(this, "Error opening feature", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to manually count users from all departments (alternative method)
    private fun countUsersFromUserSearchIndex(sanitizedCompany: String) {
        firestore.collection("user_search_index")
            .whereEqualTo("sanitizedCompanyName", sanitizedCompany)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                val totalUsers = documents.size()
                val departments = documents.groupBy { it.getString("department") ?: "" }
                val roles = documents.mapNotNull { it.getString("role") }.distinct().filter { it.isNotEmpty() }

                // Update organization stats with real counts
                organizationStats.value = OrganizationStats(
                    totalUsers = totalUsers,
                    totalDepartments = departments.keys.filter { it.isNotEmpty() }.size,
                    totalRoles = roles.size
                )

                Log.d("AdminPanel", "Real user count: $totalUsers, Departments: ${departments.size}, Roles: ${roles.size}")
            }
            .addOnFailureListener { exception ->
                Log.e("AdminPanel", "Failed to count users from search index", exception)
            }
    }

    // Function to get real-time user counts by department
    private fun getRealTimeUserCountsByDepartment(sanitizedCompany: String) {
        userSearchListener = firestore.collection("user_search_index")
            .whereEqualTo("sanitizedCompanyName", sanitizedCompany)
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AdminPanel", "Error listening to user search index", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    try {
                        // Group users by department
                        val usersByDepartment = mutableMapOf<String, Int>()
                        val rolesByDepartment = mutableMapOf<String, MutableSet<String>>()

                        for (document in snapshot.documents) {
                            val department = document.getString("department") ?: ""
                            val role = document.getString("role") ?: ""

                            if (department.isNotEmpty()) {
                                usersByDepartment[department] = (usersByDepartment[department] ?: 0) + 1

                                if (role.isNotEmpty()) {
                                    rolesByDepartment.getOrPut(department) { mutableSetOf() }.add(role)
                                }
                            }
                        }

                        // Update department data with real counts
                        val updatedDepartments = mutableListOf<DepartmentData>()
                        usersByDepartment.forEach { (deptName, userCount) ->
                            val sanitizedDeptName = sanitizeDocumentId(deptName)
                            val availableRoles = rolesByDepartment[deptName]?.toList() ?: ArrayList()

                            updatedDepartments.add(
                                DepartmentData(
                                    name = deptName,
                                    sanitizedName = sanitizedDeptName,
                                    userCount = userCount,
                                    availableRoles = availableRoles
                                )
                            )
                        }

                        departmentData.value = updatedDepartments.sortedByDescending { it.userCount }

                        // Update organization stats
                        val totalUsers = snapshot.size()
                        val totalDepartments = usersByDepartment.keys.size
                        val totalRoles = rolesByDepartment.values.flatten().distinct().size

                        organizationStats.value = OrganizationStats(
                            totalUsers = totalUsers,
                            totalDepartments = totalDepartments,
                            totalRoles = totalRoles
                        )

                        Log.d("AdminPanel", "Live update - Users: $totalUsers, Departments: $totalDepartments, Roles: $totalRoles")
                    } catch (e: Exception) {
                        Log.e("AdminPanel", "Error processing real-time user data", e)
                    }
                }
            }
    }

    // Enhanced method to get comprehensive real-time data
    private fun setupEnhancedRealTimeListeners(sanitizedCompany: String) {
        try {
            // Get real-time user counts
            getRealTimeUserCountsByDepartment(sanitizedCompany)

            // Also listen to company metadata for consistency
            companyMetadataListener = firestore.collection("companies_metadata")
                .document(sanitizedCompany)
                .addSnapshotListener { doc, error ->
                    if (error != null) {
                        Log.e("AdminPanel", "Error listening to company metadata", error)
                        return@addSnapshotListener
                    }

                    if (doc != null && doc.exists()) {
                        Log.d("AdminPanel", "Company metadata updated: ${doc.data}")
                    }
                }
        } catch (e: Exception) {
            Log.e("AdminPanel", "Error setting up enhanced real-time listeners", e)
        }
    }

    // Utility function to sanitize document IDs (same as in registration)
    private fun sanitizeDocumentId(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), "_")
            .take(50)
    }

    // Function to validate data consistency
    private fun validateDataConsistency(sanitizedCompany: String) {
        try {
            // Compare metadata counts with actual user counts
            firestore.collection("companies_metadata").document(sanitizedCompany).get()
                .addOnSuccessListener { metadataDoc ->
                    firestore.collection("user_search_index")
                        .whereEqualTo("sanitizedCompanyName", sanitizedCompany)
                        .whereEqualTo("isActive", true)
                        .get()
                        .addOnSuccessListener { userDocs ->
                            val metadataCount = metadataDoc.getLong("totalUsers")?.toInt() ?: 0
                            val actualCount = userDocs.size()

                            if (metadataCount != actualCount) {
                                Log.w("AdminPanel", "Data inconsistency detected - Metadata: $metadataCount, Actual: $actualCount")
                                // Could trigger a data synchronization process here
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("AdminPanel", "Error getting user documents for validation", e)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("AdminPanel", "Error getting metadata document for validation", e)
                }
        } catch (e: Exception) {
            Log.e("AdminPanel", "Error validating data consistency", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Clean up listeners
            companyMetadataListener?.remove()
            //departmentListeners.forEach { it.remove() }
            userSearchListener?.remove()
        } catch (e: Exception) {
            Log.e("AdminPanel", "Error cleaning up listeners", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            // Optionally validate data consistency when returning to the activity
            adminData.value?.let { admin ->
                val sanitizedCompany = sanitizeDocumentId(admin.companyName)
                if (sanitizedCompany.isNotEmpty()) {
                    validateDataConsistency(sanitizedCompany)
                }
            }
        } catch (e: Exception) {
            Log.e("AdminPanel", "Error in onResume", e)
        }
    }

    // Function to force refresh data (can be called programmatically if needed)
    private fun forceRefreshData() {
        try {
            val currentUserId = auth.currentUser?.uid ?: return

            firestore.collection("user_access_control").document(currentUserId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val sanitizedCompany = doc.getString("sanitizedCompanyName") ?: ""
                        if (sanitizedCompany.isNotEmpty()) {
                            // Re-setup listeners to get fresh data
                            companyMetadataListener?.remove()
                            //departmentListeners.forEach { it.remove() }
                            userSearchListener?.remove()

                            setupEnhancedRealTimeListeners(sanitizedCompany)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("AdminPanel", "Error force refreshing data", e)
                }
        } catch (e: Exception) {
            Log.e("AdminPanel", "Error in forceRefreshData", e)
        }
    }

    // Function to get detailed department statistics
    private fun getDetailedDepartmentStats(sanitizedCompany: String) {
        firestore.collection("user_search_index")
            .whereEqualTo("sanitizedCompanyName", sanitizedCompany)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                try {
                    val departmentStats = mutableMapOf<String, MutableMap<String, Any>>()

                    for (doc in documents) {
                        val department = doc.getString("department") ?: ""
                        val role = doc.getString("role") ?: ""
                        val designation = doc.getString("designation") ?: ""

                        if (department.isNotEmpty()) {
                            val deptData = departmentStats.getOrPut(department) {
                                mutableMapOf(
                                    "userCount" to 0,
                                    "roles" to mutableSetOf<String>(),
                                    "designations" to mutableSetOf<String>()
                                )
                            }

                            deptData["userCount"] = (deptData["userCount"] as Int) + 1

                            if (role.isNotEmpty()) {
                                @Suppress("UNCHECKED_CAST")
                                (deptData["roles"] as MutableSet<String>).add(role)
                            }

                            if (designation.isNotEmpty()) {
                                @Suppress("UNCHECKED_CAST")
                                (deptData["designations"] as MutableSet<String>).add(designation)
                            }
                        }
                    }

                    // Convert to DepartmentData list
                    val detailedDepartments = departmentStats.map { (deptName, stats) ->
                        @Suppress("UNCHECKED_CAST")
                        DepartmentData(
                            name = deptName,
                            sanitizedName = sanitizeDocumentId(deptName),
                            userCount = stats["userCount"] as Int,
                            availableRoles = (stats["roles"] as Set<String>).toList()
                        )
                    }.sortedByDescending { it.userCount }

                    departmentData.value = detailedDepartments

                    Log.d("AdminPanel", "Detailed department stats updated: ${detailedDepartments.size} departments")
                } catch (e: Exception) {
                    Log.e("AdminPanel", "Error processing detailed department stats", e)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("AdminPanel", "Failed to get detailed department stats", exception)
            }
    }
}