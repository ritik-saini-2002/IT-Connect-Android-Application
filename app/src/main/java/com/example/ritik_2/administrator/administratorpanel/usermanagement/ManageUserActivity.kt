package com.example.ritik_2.administrator.administratorpanel.usermanagement

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ManageUserActivity : ComponentActivity() {

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, ManageUserActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check user permissions before allowing access
        checkUserPermissions { hasAccess ->
            if (hasAccess) {
                setContent {
                    ManageUserScreen()
                }
            } else {
                Toast.makeText(this, "Access Denied: Only Administrators and Managers can access this feature", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun checkUserPermissions(callback: (Boolean) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            callback(false)
            return
        }

        FirebaseFirestore.getInstance()
            .collection("user_access_control")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                val role = document.getString("role") ?: ""
                val hasAccess = role == "Administrator" || role == "Manager"
                callback(hasAccess)
            }
            .addOnFailureListener {
                callback(false)
            }
    }
}

// Data Models
data class Company(
    val name: String = "",
    val sanitizedName: String = "",
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val departments: List<String> = emptyList(),
    val availableRoles: List<String> = emptyList()
)

data class Department(
    val name: String = "",
    val sanitizedName: String = "",
    val companyName: String = "",
    val userCount: Int = 0,
    val activeUsers: Int = 0,
    val availableRoles: List<String> = emptyList()
)

data class RoleInfo(
    val roleName: String = "",
    val companyName: String = "",
    val department: String = "",
    val userCount: Int = 0,
    val activeUsers: Int = 0,
    val permissions: List<String> = emptyList()
)

data class UserProfile(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val companyName: String = "",
    val department: String = "",
    val designation: String = "",
    val isActive: Boolean = true,
    val imageUrl: String = "",
    val phoneNumber: String = "",
    val experience: Int = 0,
    val completedProjects: Int = 0,
    val activeProjects: Int = 0,
    val totalComplaints: Int = 0,
    val documentPath: String = "",
    val createdAt: Timestamp? = null,
    val lastLogin: Timestamp? = null
)

// Supporting data classes for better organization
data class ProfileInfo(
    val imageUrl: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val dateOfBirth: Timestamp? = null,
    val emergencyContact: String = "",
    val bio: String = ""
)

data class WorkStats(
    val experience: Int = 0, // in years
    val completedProjects: Int = 0,
    val activeProjects: Int = 0,
    val onHoldProjects: Int = 0,
    val totalTasksCompleted: Int = 0,
    val averageTaskCompletionTime: Double = 0.0, // in hours
    val performanceRating: Double = 0.0 // out of 5
)

data class IssuesInfo(
    val totalComplaints: Int = 0,
    val resolvedComplaints: Int = 0,
    val pendingComplaints: Int = 0,
    val escalatedComplaints: Int = 0,
    val lastComplaintDate: Timestamp? = null,
    val complaintCategories: List<String> = emptyList()
)

// For dropdown selections and filtering
data class FilterOptions(
    val companies: List<String> = emptyList(),
    val departments: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
    val designations: List<String> = emptyList(),
    val statuses: List<String> = listOf("All", "Active", "Inactive")
)

// For search and pagination
data class UserSearchResult(
    val users: List<UserProfile> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val nextPageToken: String? = null
)

// For analytics and reporting
data class UserAnalytics(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val inactiveUsers: Int = 0,
    val usersByDepartment: Map<String, Int> = emptyMap(),
    val usersByRole: Map<String, Int> = emptyMap(),
    val averageExperience: Double = 0.0,
    val totalActiveProjects: Int = 0,
    val totalComplaints: Int = 0,
    val lastUpdated: Timestamp? = null
)