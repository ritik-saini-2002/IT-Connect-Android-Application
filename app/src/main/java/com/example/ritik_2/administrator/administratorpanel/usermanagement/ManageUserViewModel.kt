package com.example.ritik_2.administrator.administratorpanel.usermanagement

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ManageUserViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _currentUserRole = mutableStateOf("")
    val currentUserRole: State<String> = _currentUserRole

    private val _currentUserCompany = mutableStateOf("")
    val currentUserCompany: State<String> = _currentUserCompany

    private val _companies = mutableStateOf<List<Company>>(emptyList())
    val companies: State<List<Company>> = _companies

    private val _allDepartments = mutableStateOf<List<Department>>(emptyList())
    val departments: State<List<Department>> = _allDepartments

    private val _allRoles = mutableStateOf<List<RoleInfo>>(emptyList())
    val roles: State<List<RoleInfo>> = _allRoles

    private val _allUsers = mutableStateOf<List<UserProfile>>(emptyList())
    val users: State<List<UserProfile>> = _allUsers

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorMessage = mutableStateOf("")
    val errorMessage: State<String> = _errorMessage

    private val _expandedCompanies = mutableStateOf<Set<String>>(emptySet())
    val expandedCompanies: State<Set<String>> = _expandedCompanies

    private val _expandedDepartments = mutableStateOf<Set<String>>(emptySet())
    val expandedDepartments: State<Set<String>> = _expandedDepartments

    private val _expandedRoles = mutableStateOf<Set<String>>(emptySet())
    val expandedRoles: State<Set<String>> = _expandedRoles

    init {
        loadCurrentUserInfo()
    }

    private fun loadCurrentUserInfo() {
        val currentUser = auth.currentUser ?: return

        viewModelScope.launch {
            try {
                _isLoading.value = true
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
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadAllCompanies() {
        try {
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
        }
    }

    private suspend fun loadSingleCompany(companyName: String) {
        try {
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
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load company: ${e.message}"
        }
    }

    private suspend fun loadDepartments(companyName: String) {
        try {
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
                    companyName = companyName,
                    userCount = doc.getLong("userCount")?.toInt() ?: 0,
                    activeUsers = doc.getLong("activeUsers")?.toInt() ?: 0,
                    availableRoles = doc.get("availableRoles") as? List<String> ?: emptyList()
                )
            }

            // Update the departments list by removing old departments for this company and adding new ones
            val currentDepartments = _allDepartments.value.filterNot { it.companyName == companyName }
            _allDepartments.value = currentDepartments + departmentsList

        } catch (e: Exception) {
            _errorMessage.value = "Failed to load departments: ${e.message}"
        }
    }

    private suspend fun loadRoles(companyName: String, departmentName: String) {
        try {
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
                    companyName = companyName,
                    department = departmentName,
                    userCount = doc.getLong("userCount")?.toInt() ?: 0,
                    activeUsers = doc.getLong("activeUsers")?.toInt() ?: 0,
                    permissions = doc.get("permissions") as? List<String> ?: emptyList()
                )
            }

            // Update the roles list by removing old roles for this department and adding new ones
            val currentRoles = _allRoles.value.filterNot {
                it.companyName == companyName && it.department == departmentName
            }
            _allRoles.value = currentRoles + rolesList

        } catch (e: Exception) {
            _errorMessage.value = "Failed to load roles: ${e.message}"
        }
    }

    private suspend fun loadUsers(companyName: String, departmentName: String, roleName: String) {
        try {
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
                    role = roleName,
                    companyName = companyName,
                    department = departmentName,
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

            // Update the users list by removing old users for this role and adding new ones
            val currentUsers = _allUsers.value.filterNot {
                it.companyName == companyName && it.department == departmentName && it.role == roleName
            }
            _allUsers.value = currentUsers + usersList

        } catch (e: Exception) {
            _errorMessage.value = "Failed to load users: ${e.message}"
        }
    }

    fun toggleCompanyExpanded(companyName: String) {
        val currentExpanded = _expandedCompanies.value.toMutableSet()
        if (currentExpanded.contains(companyName)) {
            currentExpanded.remove(companyName)
        } else {
            currentExpanded.add(companyName)
            // Load departments when company is expanded
            viewModelScope.launch {
                loadDepartments(companyName)
            }
        }
        _expandedCompanies.value = currentExpanded
    }

    fun toggleDepartmentExpanded(companyName: String, departmentName: String) {
        val key = "$companyName-$departmentName"
        val currentExpanded = _expandedDepartments.value.toMutableSet()
        if (currentExpanded.contains(key)) {
            currentExpanded.remove(key)
        } else {
            currentExpanded.add(key)
            // Load roles when department is expanded
            viewModelScope.launch {
                loadRoles(companyName, departmentName)
            }
        }
        _expandedDepartments.value = currentExpanded
    }

    fun toggleRoleExpanded(companyName: String, departmentName: String, roleName: String) {
        val key = "$companyName-$departmentName-$roleName"
        val currentExpanded = _expandedRoles.value.toMutableSet()
        if (currentExpanded.contains(key)) {
            currentExpanded.remove(key)
        } else {
            currentExpanded.add(key)
            // Load users when role is expanded
            viewModelScope.launch {
                loadUsers(companyName, departmentName, roleName)
            }
        }
        _expandedRoles.value = currentExpanded
    }

    // Method to get departments for a specific company
    fun getDepartmentsForCompany(companyName: String): List<Department> {
        return _allDepartments.value.filter { it.companyName == companyName }
    }

    // Method to get roles for a specific department
    fun getRolesForDepartment(companyName: String, departmentName: String): List<RoleInfo> {
        return _allRoles.value.filter {
            it.companyName == companyName && it.department == departmentName
        }
    }

    // Method to get users for a specific role
    fun getUsersForRole(companyName: String, departmentName: String, roleName: String): List<UserProfile> {
        return _allUsers.value.filter {
            it.companyName == companyName &&
                    it.department == departmentName &&
                    it.role == roleName
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
                    .document(user.companyName)
                    .collection(user.department)
                    .document(user.role)
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
                val companyMetaRef = firestore.collection("companies_metadata").document(user.companyName)
                val departmentMetaRef = companyMetaRef.collection("departments_metadata").document(user.department)
                val roleMetaRef = departmentMetaRef.collection("roles_metadata").document(user.role)

                val increment = if (newStatus) 1L else -1L
                batch.update(companyMetaRef, mapOf("activeUsers" to FieldValue.increment(increment)))
                batch.update(departmentMetaRef, mapOf("activeUsers" to FieldValue.increment(increment)))
                batch.update(roleMetaRef, mapOf("activeUsers" to FieldValue.increment(increment)))

                batch.commit().await()

                // Update local state
                val updatedUsers = _allUsers.value.map {
                    if (it.userId == user.userId) it.copy(isActive = newStatus) else it
                }
                _allUsers.value = updatedUsers

            } catch (e: Exception) {
                _errorMessage.value = "Failed to update user status: ${e.message}"
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
                    .document(user.companyName)
                    .collection(user.department)
                    .document(user.role)
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
                val companyMetaRef = firestore.collection("companies_metadata").document(user.companyName)
                val departmentMetaRef = companyMetaRef.collection("departments_metadata").document(user.department)
                val roleMetaRef = departmentMetaRef.collection("roles_metadata").document(user.role)

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

                // Remove from local state
                val updatedUsers = _allUsers.value.filterNot { it.userId == user.userId }
                _allUsers.value = updatedUsers

                // Update company, department, and role counts in local state
                updateLocalCounts(user, isDelete = true)

            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete user: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUserRole(userProfile: UserProfile, newRole: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // This is a complex operation that would involve:
                // 1. Creating a new document in the new role path
                // 2. Deleting the old document
                // 3. Updating all metadata counters
                // 4. Updating access control and search index

                _errorMessage.value = "Role update functionality is complex and requires careful implementation to maintain data integrity. This feature will be available in a future update."

            } catch (e: Exception) {
                _errorMessage.value = "Failed to update user role: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateLocalCounts(user: UserProfile, isDelete: Boolean = false) {
        // Update companies
        val updatedCompanies = _companies.value.map { company ->
            if (company.sanitizedName == user.companyName) {
                company.copy(
                    totalUsers = if (isDelete) company.totalUsers - 1 else company.totalUsers,
                    activeUsers = if (isDelete && user.isActive) company.activeUsers - 1 else company.activeUsers
                )
            } else {
                company
            }
        }
        _companies.value = updatedCompanies

        // Update departments
        val updatedDepartments = _allDepartments.value.map { department ->
            if (department.companyName == user.companyName && department.sanitizedName == user.department) {
                department.copy(
                    userCount = if (isDelete) department.userCount - 1 else department.userCount,
                    activeUsers = if (isDelete && user.isActive) department.activeUsers - 1 else department.activeUsers
                )
            } else {
                department
            }
        }
        _allDepartments.value = updatedDepartments

        // Update roles
        val updatedRoles = _allRoles.value.map { role ->
            if (role.companyName == user.companyName &&
                role.department == user.department &&
                role.roleName == user.role) {
                role.copy(
                    userCount = if (isDelete) role.userCount - 1 else role.userCount,
                    activeUsers = if (isDelete && user.isActive) role.activeUsers - 1 else role.activeUsers
                )
            } else {
                role
            }
        }
        _allRoles.value = updatedRoles
    }

    fun clearErrorMessage() {
        _errorMessage.value = ""
    }

    fun refreshData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Clear current data
                _companies.value = emptyList()
                _allDepartments.value = emptyList()
                _allRoles.value = emptyList()
                _allUsers.value = emptyList()

                // Reset expanded states
                _expandedCompanies.value = emptySet()
                _expandedDepartments.value = emptySet()
                _expandedRoles.value = emptySet()

                // Reload data
                loadCurrentUserInfo()

            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchUsers(query: String): List<UserProfile> {
        return if (query.isBlank()) {
            _allUsers.value
        } else {
            _allUsers.value.filter { user ->
                user.name.contains(query, ignoreCase = true) ||
                        user.email.contains(query, ignoreCase = true) ||
                        user.role.contains(query, ignoreCase = true) ||
                        user.department.contains(query, ignoreCase = true) ||
                        user.companyName.contains(query, ignoreCase = true) ||
                        user.designation.contains(query, ignoreCase = true)
            }
        }
    }

    fun filterUsersByStatus(activeOnly: Boolean? = null): List<UserProfile> {
        return when (activeOnly) {
            true -> _allUsers.value.filter { it.isActive }
            false -> _allUsers.value.filter { !it.isActive }
            null -> _allUsers.value
        }
    }

    fun getUserStats(): UserAnalytics {
        val allUsers = _allUsers.value
        val activeUsers = allUsers.filter { it.isActive }

        return UserAnalytics(
            totalUsers = allUsers.size,
            activeUsers = activeUsers.size,
            inactiveUsers = allUsers.size - activeUsers.size,
            usersByDepartment = allUsers.groupBy { it.department }.mapValues { it.value.size },
            usersByRole = allUsers.groupBy { it.role }.mapValues { it.value.size },
            averageExperience = if (allUsers.isNotEmpty()) allUsers.map { it.experience }.average() else 0.0,
            totalActiveProjects = allUsers.sumOf { it.activeProjects },
            totalComplaints = allUsers.sumOf { it.totalComplaints },
            lastUpdated = Timestamp.now()
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up any resources if needed
    }
}