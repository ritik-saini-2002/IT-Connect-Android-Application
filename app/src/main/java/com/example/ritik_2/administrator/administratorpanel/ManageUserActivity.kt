package com.example.ritik_2.administrator.administratorpanel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
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

// ViewModel for Managing Users
class ManageUserViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _currentUserRole = mutableStateOf("")
    val currentUserRole: State<String> = _currentUserRole

    private val _currentUserCompany = mutableStateOf("")
    val currentUserCompany: State<String> = _currentUserCompany

    private val _companies = mutableStateOf<List<Company>>(emptyList())
    val companies: State<List<Company>> = _companies

    private val _departments = mutableStateOf<List<Department>>(emptyList())
    val departments: State<List<Department>> = _departments

    private val _roles = mutableStateOf<List<RoleInfo>>(emptyList())
    val roles: State<List<RoleInfo>> = _roles

    private val _users = mutableStateOf<List<UserProfile>>(emptyList())
    val users: State<List<UserProfile>> = _users

    private val _selectedCompany = mutableStateOf("")
    val selectedCompany: State<String> = _selectedCompany

    private val _selectedDepartment = mutableStateOf("")
    val selectedDepartment: State<String> = _selectedDepartment

    private val _selectedRole = mutableStateOf("")
    val selectedRole: State<String> = _selectedRole

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorMessage = mutableStateOf("")
    val errorMessage: State<String> = _errorMessage

    init {
        loadCurrentUserInfo()
    }

    private fun loadCurrentUserInfo() {
        val currentUser = auth.currentUser ?: return

        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("user_access_control")
                    .document(currentUser.uid)
                    .get()
                    .await()

                _currentUserRole.value = userDoc.getString("role") ?: ""
                _currentUserCompany.value = userDoc.getString("sanitizedCompanyName") ?: ""

                // Load companies based on user role
                if (_currentUserRole.value == "Administrator") {
                    loadAllCompanies()
                } else {
                    // Managers can only see their own company
                    loadSingleCompany(_currentUserCompany.value)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load user info: ${e.message}"
            }
        }
    }

    private suspend fun loadAllCompanies() {
        try {
            _isLoading.value = true
            val companiesSnapshot = firestore.collection("companies_metadata").get().await()
            val companiesList = companiesSnapshot.documents.mapNotNull { doc ->
                Company(
                    name = doc.getString("originalName") ?: "",
                    sanitizedName = doc.id,
                    totalUsers = doc.getLong("totalUsers")?.toInt() ?: 0,
                    activeUsers = doc.getLong("activeUsers")?.toInt() ?: 0,
                    departments = doc.get("departments") as? List<String> ?: emptyList(),
                    availableRoles = doc.get("availableRoles") as? List<String> ?: emptyList()
                )
            }
            _companies.value = companiesList
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load companies: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun loadSingleCompany(companyName: String) {
        try {
            _isLoading.value = true
            val companyDoc = firestore.collection("companies_metadata")
                .document(companyName)
                .get()
                .await()

            if (companyDoc.exists()) {
                val company = Company(
                    name = companyDoc.getString("originalName") ?: "",
                    sanitizedName = companyDoc.id,
                    totalUsers = companyDoc.getLong("totalUsers")?.toInt() ?: 0,
                    activeUsers = companyDoc.getLong("activeUsers")?.toInt() ?: 0,
                    departments = companyDoc.get("departments") as? List<String> ?: emptyList(),
                    availableRoles = companyDoc.get("availableRoles") as? List<String> ?: emptyList()
                )
                _companies.value = listOf(company)

                // Auto-select the company for managers
                selectCompany(companyName)
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load company: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun selectCompany(companyName: String) {
        _selectedCompany.value = companyName
        _selectedDepartment.value = ""
        _selectedRole.value = ""
        _departments.value = emptyList()
        _roles.value = emptyList()
        _users.value = emptyList()

        if (companyName.isNotEmpty()) {
            loadDepartments(companyName)
        }
    }

    private fun loadDepartments(companyName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val departmentsSnapshot = firestore
                    .collection("companies_metadata")
                    .document(companyName)
                    .collection("departments_metadata")
                    .get()
                    .await()

                val departmentsList = departmentsSnapshot.documents.mapNotNull { doc ->
                    Department(
                        name = doc.getString("departmentName") ?: "",
                        sanitizedName = doc.id,
                        companyName = doc.getString("companyName") ?: "",
                        userCount = doc.getLong("userCount")?.toInt() ?: 0,
                        activeUsers = doc.getLong("activeUsers")?.toInt() ?: 0,
                        availableRoles = doc.get("availableRoles") as? List<String> ?: emptyList()
                    )
                }
                _departments.value = departmentsList
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load departments: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectDepartment(departmentName: String) {
        _selectedDepartment.value = departmentName
        _selectedRole.value = ""
        _roles.value = emptyList()
        _users.value = emptyList()

        if (departmentName.isNotEmpty()) {
            loadRoles(_selectedCompany.value, departmentName)
        }
    }

    private fun loadRoles(companyName: String, departmentName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val rolesSnapshot = firestore
                    .collection("companies_metadata")
                    .document(companyName)
                    .collection("departments_metadata")
                    .document(departmentName)
                    .collection("roles_metadata")
                    .get()
                    .await()

                val rolesList = rolesSnapshot.documents.mapNotNull { doc ->
                    RoleInfo(
                        roleName = doc.getString("roleName") ?: "",
                        companyName = doc.getString("companyName") ?: "",
                        department = doc.getString("department") ?: "",
                        userCount = doc.getLong("userCount")?.toInt() ?: 0,
                        activeUsers = doc.getLong("activeUsers")?.toInt() ?: 0,
                        permissions = doc.get("permissions") as? List<String> ?: emptyList()
                    )
                }
                _roles.value = rolesList
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load roles: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectRole(roleName: String) {
        _selectedRole.value = roleName
        _users.value = emptyList()

        if (roleName.isNotEmpty()) {
            loadUsers(_selectedCompany.value, _selectedDepartment.value, roleName)
        }
    }

    private fun loadUsers(companyName: String, departmentName: String, roleName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val usersSnapshot = firestore
                    .collection("users")
                    .document(companyName)
                    .collection(departmentName)
                    .document(roleName)
                    .collection("users")
                    .get()
                    .await()

                val usersList = usersSnapshot.documents.mapNotNull { doc ->
                    val profile = doc.get("profile") as? Map<String, Any> ?: emptyMap()
                    val workStats = doc.get("workStats") as? Map<String, Any> ?: emptyMap()
                    val issues = doc.get("issues") as? Map<String, Any> ?: emptyMap()

                    UserProfile(
                        userId = doc.getString("userId") ?: "",
                        name = doc.getString("name") ?: "",
                        email = doc.getString("email") ?: "",
                        role = doc.getString("role") ?: "",
                        companyName = doc.getString("companyName") ?: "",
                        department = doc.getString("department") ?: "",
                        designation = doc.getString("designation") ?: "",
                        isActive = doc.getBoolean("isActive") ?: true,
                        imageUrl = profile["imageUrl"] as? String ?: "",
                        phoneNumber = profile["phoneNumber"] as? String ?: "",
                        experience = (workStats["experience"] as? Long)?.toInt() ?: 0,
                        completedProjects = (workStats["completedProjects"] as? Long)?.toInt() ?: 0,
                        activeProjects = (workStats["activeProjects"] as? Long)?.toInt() ?: 0,
                        totalComplaints = (issues["totalComplaints"] as? Long)?.toInt() ?: 0,
                        documentPath = doc.getString("documentPath") ?: "",
                        createdAt = doc.getTimestamp("createdAt"),
                        lastLogin = doc.getTimestamp("lastLogin")
                    )
                }
                _users.value = usersList
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load users: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleUserActiveStatus(user: UserProfile) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val newStatus = !user.isActive

                // Update user document
                val userDocRef = firestore
                    .collection("users")
                    .document(_selectedCompany.value)
                    .collection(_selectedDepartment.value)
                    .document(_selectedRole.value)
                    .collection("users")
                    .document(user.userId)

                val batch = firestore.batch()

                // Update main user document
                batch.update(userDocRef, mapOf(
                    "isActive" to newStatus,
                    "lastUpdated" to Timestamp.now()
                ))

                // Update access control
                val accessControlRef = firestore.collection("user_access_control").document(user.userId)
                batch.update(accessControlRef, mapOf("isActive" to newStatus))

                // Update search index
                val searchIndexRef = firestore.collection("user_search_index").document(user.userId)
                batch.update(searchIndexRef, mapOf("isActive" to newStatus))

                // Update metadata counters
                val companyMetaRef = firestore.collection("companies_metadata").document(_selectedCompany.value)
                val departmentMetaRef = companyMetaRef.collection("departments_metadata").document(_selectedDepartment.value)
                val roleMetaRef = departmentMetaRef.collection("roles_metadata").document(_selectedRole.value)

                val increment = if (newStatus) 1L else -1L
                batch.update(companyMetaRef, mapOf("activeUsers" to FieldValue.increment(increment)))
                batch.update(departmentMetaRef, mapOf("activeUsers" to FieldValue.increment(increment)))
                batch.update(roleMetaRef, mapOf("activeUsers" to FieldValue.increment(increment)))

                batch.commit().await()

                // Refresh the users list
                loadUsers(_selectedCompany.value, _selectedDepartment.value, _selectedRole.value)

            } catch (e: Exception) {
                _errorMessage.value = "Failed to update user status: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUserRole(user: UserProfile, newRole: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // This would involve moving the user document to a new path
                // and updating all metadata - complex operation
                // Implementation would be similar to the registration process
                // but moving from one role to another

                _errorMessage.value = "Role update functionality needs implementation"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update user role: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteUser(user: UserProfile) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val batch = firestore.batch()

                // Delete main user document
                val userDocRef = firestore
                    .collection("users")
                    .document(_selectedCompany.value)
                    .collection(_selectedDepartment.value)
                    .document(_selectedRole.value)
                    .collection("users")
                    .document(user.userId)

                batch.delete(userDocRef)

                // Delete access control
                val accessControlRef = firestore.collection("user_access_control").document(user.userId)
                batch.delete(accessControlRef)

                // Delete search index
                val searchIndexRef = firestore.collection("user_search_index").document(user.userId)
                batch.delete(searchIndexRef)

                // Update metadata counters
                val companyMetaRef = firestore.collection("companies_metadata").document(_selectedCompany.value)
                val departmentMetaRef = companyMetaRef.collection("departments_metadata").document(_selectedDepartment.value)
                val roleMetaRef = departmentMetaRef.collection("roles_metadata").document(_selectedRole.value)

                batch.update(companyMetaRef, mapOf(
                    "totalUsers" to FieldValue.increment(-1),
                    "activeUsers" to FieldValue.increment(if (user.isActive) -1 else 0)
                ))
                batch.update(departmentMetaRef, mapOf(
                    "userCount" to FieldValue.increment(-1),
                    "activeUsers" to FieldValue.increment(if (user.isActive) -1 else 0)
                ))
                batch.update(roleMetaRef, mapOf(
                    "userCount" to FieldValue.increment(-1),
                    "activeUsers" to FieldValue.increment(if (user.isActive) -1 else 0)
                ))

                batch.commit().await()

                // Refresh the users list
                loadUsers(_selectedCompany.value, _selectedDepartment.value, _selectedRole.value)

            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete user: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = ""
    }
}