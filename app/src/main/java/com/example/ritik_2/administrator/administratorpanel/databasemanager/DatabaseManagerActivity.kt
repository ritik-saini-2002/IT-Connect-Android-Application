package com.example.ritik_2.administrator.administratorpanel.databasemanager

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.data.*
import com.example.ritik_2.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class DatabaseManagerActivity : ComponentActivity() {

    @Inject
    lateinit var appDatabase: AppDatabase

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // State flows for UI
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _complaints = MutableStateFlow<List<Complaint>>(emptyList())
    val complaints: StateFlow<List<Complaint>> = _complaints.asStateFlow()

    private val _companies = MutableStateFlow<List<Company>>(emptyList())
    val companies: StateFlow<List<Company>> = _companies.asStateFlow()

    private val _departments = MutableStateFlow<List<Department>>(emptyList())
    val departments: StateFlow<List<Department>> = _departments.asStateFlow()

    private val _currentTab = MutableStateFlow(DatabaseTab.USERS)
    val currentTab: StateFlow<DatabaseTab> = _currentTab.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _databaseMode = MutableStateFlow(DatabaseMode.FIREBASE)
    val databaseMode: StateFlow<DatabaseMode> = _databaseMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _adminUserData = MutableStateFlow<User?>(null)
    val adminUserData: StateFlow<User?> = _adminUserData.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Verify admin permissions
        verifyAdminAccess(currentUserId)

        setContent {
            Ritik_2Theme {
                DatabaseManagerScreen(
                    users = users.collectAsState().value,
                    complaints = complaints.collectAsState().value,
                    companies = companies.collectAsState().value,
                    departments = departments.collectAsState().value,
                    currentTab = currentTab.collectAsState().value,
                    isLoading = isLoading.collectAsState().value,
                    databaseMode = databaseMode.collectAsState().value,
                    searchQuery = searchQuery.collectAsState().value,
                    adminUserData = adminUserData.collectAsState().value,
                    onTabChanged = { tab ->
                        _currentTab.value = tab
                        loadDataForTab(tab)
                    },
                    onDatabaseModeChanged = { mode ->
                        _databaseMode.value = mode
                        loadDataForTab(_currentTab.value)
                    },
                    onSearchQueryChanged = { query ->
                        _searchQuery.value = query
                        performSearch(query)
                    },
                    onSyncData = { syncDataBetweenDatabases() },
                    onDeleteRecord = { recordId, recordType -> deleteRecord(recordId, recordType) },
                    onExportData = { exportDataToLocal() },
                    onImportData = { importDataFromLocal() },
                    onClearLocalDatabase = { clearLocalDatabase() },
                    onRefreshData = { loadDataForTab(_currentTab.value) }
                )
            }
        }
    }

    private fun verifyAdminAccess(userId: String) {
        lifecycleScope.launch {
            try {
                val doc = firestore.collection("user_access_control").document(userId).get().await()
                if (doc.exists()) {
                    val role = doc.getString("role")
                    val permissions = doc.get("permissions") as? List<String>

                    if (role == "Administrator" || permissions?.contains("manage_database") == true) {
                        // Load admin user data
                        val userData = User.fromMap(doc.data ?: emptyMap())
                        _adminUserData.value = userData

                        // Load initial data
                        loadDataForTab(DatabaseTab.USERS)
                    } else {
                        Toast.makeText(this@DatabaseManagerActivity, "Administrator access required", Toast.LENGTH_LONG).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this@DatabaseManagerActivity, "User access data not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying admin access", e)
                Toast.makeText(this@DatabaseManagerActivity, "Error verifying permissions", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadDataForTab(tab: DatabaseTab) {
        lifecycleScope.launch {
            _isLoading.value = true
            try {
                when (tab) {
                    DatabaseTab.USERS -> loadUsers()
                    DatabaseTab.COMPLAINTS -> loadComplaints()
                    DatabaseTab.COMPANIES -> loadCompanies()
                    DatabaseTab.DEPARTMENTS -> loadDepartments()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading data for tab: $tab", e)
                Toast.makeText(this@DatabaseManagerActivity, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadUsers() {
        when (_databaseMode.value) {
            DatabaseMode.FIREBASE -> {
                val adminData = _adminUserData.value
                if (adminData != null) {
                    val snapshot = firestore.collection("user_access_control")
                        .whereEqualTo("sanitizedCompanyName", adminData.sanitizedCompanyName)
                        .orderBy("name")
                        .get()
                        .await()

                    val usersList = snapshot.documents.mapNotNull { doc ->
                        try {
                            User.fromMap(doc.data ?: emptyMap()).copy(userId = doc.id)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing user document: ${doc.id}", e)
                            null
                        }
                    }
                    _users.value = usersList
                }
            }
            DatabaseMode.LOCAL -> {
                val usersList = appDatabase.userDao().getAllUsers()
                _users.value = usersList
            }
        }
    }

    private suspend fun loadComplaints() {
        when (_databaseMode.value) {
            DatabaseMode.FIREBASE -> {
                val adminData = _adminUserData.value
                if (adminData != null) {
                    val snapshot = firestore.collection("all_complaints")
                        .whereEqualTo("sanitizedCompanyName", adminData.sanitizedCompanyName)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(100)
                        .get()
                        .await()

                    val complaintsList = snapshot.documents.mapNotNull { doc ->
                        try {
                            Complaint.fromMap(doc.data ?: emptyMap()).copy(complaintId = doc.id)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing complaint document: ${doc.id}", e)
                            null
                        }
                    }
                    _complaints.value = complaintsList
                }
            }
            DatabaseMode.LOCAL -> {
                val complaintsList = appDatabase.complaintDao().getAllComplaints()
                _complaints.value = complaintsList
            }
        }
    }

    private suspend fun loadCompanies() {
        when (_databaseMode.value) {
            DatabaseMode.FIREBASE -> {
                val snapshot = firestore.collection("companies_metadata")
                    .orderBy("originalName")
                    .get()
                    .await()

                val companiesList = snapshot.documents.mapNotNull { doc ->
                    try {
                        Company.fromMap(doc.data ?: emptyMap()).copy(sanitizedName = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing company document: ${doc.id}", e)
                        null
                    }
                }
                _companies.value = companiesList
            }
            DatabaseMode.LOCAL -> {
                val companiesList = appDatabase.companyDao().getAllCompanies()
                _companies.value = companiesList
            }
        }
    }

    private suspend fun loadDepartments() {
        when (_databaseMode.value) {
            DatabaseMode.FIREBASE -> {
                val adminData = _adminUserData.value
                if (adminData != null) {
                    val snapshot = firestore.collection("companies_metadata")
                        .document(adminData.sanitizedCompanyName)
                        .collection("departments_metadata")
                        .orderBy("departmentName")
                        .get()
                        .await()

                    val departmentsList = snapshot.documents.mapNotNull { doc ->
                        try {
                            Department.fromMap(adminData.companyName, doc.data ?: emptyMap())
                                .copy(id = "${adminData.sanitizedCompanyName}_${doc.id}")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing department document: ${doc.id}", e)
                            null
                        }
                    }
                    _departments.value = departmentsList
                }
            }
            DatabaseMode.LOCAL -> {
                val departmentsList = appDatabase.departmentDao().getAllDepartments()
                _departments.value = departmentsList
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            loadDataForTab(_currentTab.value)
            return
        }

        lifecycleScope.launch {
            _isLoading.value = true
            try {
                when (_currentTab.value) {
                    DatabaseTab.USERS -> searchUsers(query)
                    DatabaseTab.COMPLAINTS -> searchComplaints(query)
                    DatabaseTab.COMPANIES -> searchCompanies(query)
                    DatabaseTab.DEPARTMENTS -> searchDepartments(query)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching", e)
                Toast.makeText(this@DatabaseManagerActivity, "Search error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun searchUsers(query: String) {
        when (_databaseMode.value) {
            DatabaseMode.FIREBASE -> {
                val snapshot = firestore.collection("user_search_index")
                    .whereArrayContains("searchTerms", query.lowercase())
                    .limit(50)
                    .get()
                    .await()

                val userIds = snapshot.documents.map { it.id }
                val usersList = mutableListOf<User>()

                userIds.forEach { userId ->
                    try {
                        val userDoc = firestore.collection("user_access_control").document(userId).get().await()
                        if (userDoc.exists()) {
                            val user = User.fromMap(userDoc.data ?: emptyMap()).copy(userId = userId)
                            usersList.add(user)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error loading user: $userId", e)
                    }
                }
                _users.value = usersList
            }
            DatabaseMode.LOCAL -> {
                val usersList = appDatabase.userDao().searchUsers("%$query%")
                _users.value = usersList
            }
        }
    }

    private suspend fun searchComplaints(query: String) {
        when (_databaseMode.value) {
            DatabaseMode.FIREBASE -> {
                val snapshot = firestore.collection("complaint_search_index")
                    .whereArrayContains("searchTerms", query.lowercase())
                    .limit(50)
                    .get()
                    .await()

                val complaintIds = snapshot.documents.map { it.id }
                val complaintsList = mutableListOf<Complaint>()

                complaintIds.forEach { complaintId ->
                    try {
                        val complaintDoc = firestore.collection("all_complaints").document(complaintId).get().await()
                        if (complaintDoc.exists()) {
                            val complaint = Complaint.fromMap(complaintDoc.data ?: emptyMap()).copy(complaintId = complaintId)
                            complaintsList.add(complaint)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error loading complaint: $complaintId", e)
                    }
                }
                _complaints.value = complaintsList
            }
            DatabaseMode.LOCAL -> {
                val complaintsList = appDatabase.complaintDao().searchComplaints("%$query%")
                _complaints.value = complaintsList
            }
        }
    }

    private suspend fun searchCompanies(query: String) {
        when (_databaseMode.value) {
            DatabaseMode.FIREBASE -> {
                val snapshot = firestore.collection("companies_metadata")
                    .orderBy("originalName")
                    .startAt(query)
                    .endAt(query + '\uf8ff')
                    .get()
                    .await()

                val companiesList = snapshot.documents.mapNotNull { doc ->
                    try {
                        Company.fromMap(doc.data ?: emptyMap()).copy(sanitizedName = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing company document: ${doc.id}", e)
                        null
                    }
                }
                _companies.value = companiesList
            }
            DatabaseMode.LOCAL -> {
                val companiesList = appDatabase.companyDao().searchCompanies("%$query%")
                _companies.value = companiesList
            }
        }
    }

    private suspend fun searchDepartments(query: String) {
        when (_databaseMode.value) {
            DatabaseMode.FIREBASE -> {
                val adminData = _adminUserData.value
                if (adminData != null) {
                    val snapshot = firestore.collection("companies_metadata")
                        .document(adminData.sanitizedCompanyName)
                        .collection("departments_metadata")
                        .orderBy("departmentName")
                        .startAt(query)
                        .endAt(query + '\uf8ff')
                        .get()
                        .await()

                    val departmentsList = snapshot.documents.mapNotNull { doc ->
                        try {
                            Department.fromMap(adminData.companyName, doc.data ?: emptyMap())
                                .copy(id = "${adminData.sanitizedCompanyName}_${doc.id}")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing department document: ${doc.id}", e)
                            null
                        }
                    }
                    _departments.value = departmentsList
                }
            }
            DatabaseMode.LOCAL -> {
                val departmentsList = appDatabase.departmentDao().searchDepartments("%$query%")
                _departments.value = departmentsList
            }
        }
    }

    private fun syncDataBetweenDatabases() {
        lifecycleScope.launch {
            _isLoading.value = true
            try {
                Toast.makeText(this@DatabaseManagerActivity, "Starting data sync...", Toast.LENGTH_SHORT).show()

                // Sync Users
                syncUsers()

                // Sync Complaints
                syncComplaints()

                // Sync Companies
                syncCompanies()

                // Sync Departments
                syncDepartments()

                Toast.makeText(this@DatabaseManagerActivity, "Data sync completed successfully", Toast.LENGTH_LONG).show()
                loadDataForTab(_currentTab.value)

            } catch (e: Exception) {
                Log.e(TAG, "Error syncing data", e)
                Toast.makeText(this@DatabaseManagerActivity, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun syncUsers() {
        // Get all users from Firebase
        val adminData = _adminUserData.value ?: return

        val snapshot = firestore.collection("user_access_control")
            .whereEqualTo("sanitizedCompanyName", adminData.sanitizedCompanyName)
            .get()
            .await()

        val firebaseUsers = snapshot.documents.mapNotNull { doc ->
            try {
                User.fromMap(doc.data ?: emptyMap()).copy(userId = doc.id)
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing user for sync: ${doc.id}", e)
                null
            }
        }

        // Clear local users and insert Firebase users
        appDatabase.userDao().deleteAll()
        appDatabase.userDao().insertUsers(firebaseUsers)

        Log.d(TAG, "Synced ${firebaseUsers.size} users to local database")
    }

    private suspend fun syncComplaints() {
        val adminData = _adminUserData.value ?: return

        val snapshot = firestore.collection("all_complaints")
            .whereEqualTo("sanitizedCompanyName", adminData.sanitizedCompanyName)
            .get()
            .await()

        val firebaseComplaints = snapshot.documents.mapNotNull { doc ->
            try {
                Complaint.fromMap(doc.data ?: emptyMap()).copy(complaintId = doc.id)
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing complaint for sync: ${doc.id}", e)
                null
            }
        }

        appDatabase.complaintDao().deleteAll()
        appDatabase.complaintDao().insertComplaints(firebaseComplaints)

        Log.d(TAG, "Synced ${firebaseComplaints.size} complaints to local database")
    }

    private suspend fun syncCompanies() {
        val snapshot = firestore.collection("companies_metadata").get().await()

        val firebaseCompanies = snapshot.documents.mapNotNull { doc ->
            try {
                Company.fromMap(doc.data ?: emptyMap()).copy(sanitizedName = doc.id)
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing company for sync: ${doc.id}", e)
                null
            }
        }

        appDatabase.companyDao().deleteAll()
        appDatabase.companyDao().insertCompanies(firebaseCompanies)

        Log.d(TAG, "Synced ${firebaseCompanies.size} companies to local database")
    }

    private suspend fun syncDepartments() {
        val adminData = _adminUserData.value ?: return

        val snapshot = firestore.collection("companies_metadata")
            .document(adminData.sanitizedCompanyName)
            .collection("departments_metadata")
            .get()
            .await()

        val firebaseDepartments = snapshot.documents.mapNotNull { doc ->
            try {
                Department.fromMap(adminData.companyName, doc.data ?: emptyMap())
                    .copy(id = "${adminData.sanitizedCompanyName}_${doc.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing department for sync: ${doc.id}", e)
                null
            }
        }

        appDatabase.departmentDao().deleteAll()
        appDatabase.departmentDao().insertDepartments(firebaseDepartments)

        Log.d(TAG, "Synced ${firebaseDepartments.size} departments to local database")
    }

    private fun deleteRecord(recordId: String, recordType: RecordType) {
        lifecycleScope.launch {
            try {
                when (recordType) {
                    RecordType.USER -> deleteUser(recordId)
                    RecordType.COMPLAINT -> deleteComplaint(recordId)
                    RecordType.COMPANY -> deleteCompany(recordId)
                    RecordType.DEPARTMENT -> deleteDepartment(recordId)
                }
                loadDataForTab(_currentTab.value)
                Toast.makeText(this@DatabaseManagerActivity, "Record deleted successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting record", e)
                Toast.makeText(this@DatabaseManagerActivity, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun deleteUser(userId: String) {
        when (_databaseMode.value) {
            DatabaseMode.FIREBASE -> {
                val batch = firestore.batch()

                // Delete from user_access_control
                batch.delete(firestore.collection("user_access_control").document(userId))

                // Delete from user_search_index
                batch.delete(firestore.collection("user_search_index").document(userId))

                // Delete from hierarchical structure (would need the full path)
                // This is complex and might require additional logic

                batch.commit().await()
            }
            DatabaseMode.LOCAL -> {
                appDatabase.userDao().deleteUser(userId)
            }
        }
    }

    private suspend fun deleteComplaint(complaintId: String) {
        when (_databaseMode.value) {
            DatabaseMode.FIREBASE -> {
                val batch = firestore.batch()

                // Delete from all_complaints
                batch.delete(firestore.collection("all_complaints").document(complaintId))

                // Delete from complaint_search_index
                batch.delete(firestore.collection("complaint_search_index").document(complaintId))

                batch.commit().await()
            }
            DatabaseMode.LOCAL -> {
                appDatabase.complaintDao().deleteComplaint(complaintId)
            }
        }
    }

    private suspend fun deleteCompany(companyId: String) {
        when (_databaseMode.value) {
            DatabaseMode.FIREBASE -> {
                // This is a complex operation that should cascade delete all related data
                firestore.collection("companies_metadata").document(companyId).delete().await()
            }
            DatabaseMode.LOCAL -> {
                appDatabase.companyDao().deleteCompany(companyId)
            }
        }
    }

    private suspend fun deleteDepartment(departmentId: String) {
        val adminData = _adminUserData.value ?: return
        val departmentName = departmentId.substringAfter("${adminData.sanitizedCompanyName}_")

        when (_databaseMode.value) {
            DatabaseMode.FIREBASE -> {
                firestore.collection("companies_metadata")
                    .document(adminData.sanitizedCompanyName)
                    .collection("departments_metadata")
                    .document(departmentName)
                    .delete()
                    .await()
            }
            DatabaseMode.LOCAL -> {
                appDatabase.departmentDao().deleteDepartment(departmentId)
            }
        }
    }

    private fun exportDataToLocal() {
        lifecycleScope.launch {
            try {
                _isLoading.value = true
                Toast.makeText(this@DatabaseManagerActivity, "Exporting data to local database...", Toast.LENGTH_SHORT).show()

                // Switch temporarily to Firebase mode and sync all data
                val originalMode = _databaseMode.value
                _databaseMode.value = DatabaseMode.FIREBASE

                syncDataBetweenDatabases()

                _databaseMode.value = originalMode

                Toast.makeText(this@DatabaseManagerActivity, "Data exported successfully", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting data", e)
                Toast.makeText(this@DatabaseManagerActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun importDataFromLocal() {
        lifecycleScope.launch {
            try {
                _isLoading.value = true
                Toast.makeText(this@DatabaseManagerActivity, "Importing data from local database...", Toast.LENGTH_SHORT).show()

                // Get all local data and upload to Firebase
                val localUsers = appDatabase.userDao().getAllUsers()
                val localComplaints = appDatabase.complaintDao().getAllComplaints()
                val localCompanies = appDatabase.companyDao().getAllCompanies()
                val localDepartments = appDatabase.departmentDao().getAllDepartments()

                // Upload users to Firebase (simplified)
                localUsers.forEach { user ->
                    firestore.collection("user_access_control").document(user.userId).set(user.toMap()).await()
                }

                // Upload complaints to Firebase (simplified)
                localComplaints.forEach { complaint ->
                    firestore.collection("all_complaints").document(complaint.complaintId).set(complaint.toMap()).await()
                }

                Toast.makeText(this@DatabaseManagerActivity, "Data imported successfully", Toast.LENGTH_LONG).show()
                loadDataForTab(_currentTab.value)

            } catch (e: Exception) {
                Log.e(TAG, "Error importing data", e)
                Toast.makeText(this@DatabaseManagerActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun clearLocalDatabase() {
        lifecycleScope.launch {
            try {
                _isLoading.value = true
                Toast.makeText(this@DatabaseManagerActivity, "Clearing local database...", Toast.LENGTH_SHORT).show()

                appDatabase.userDao().deleteAll()
                appDatabase.complaintDao().deleteAll()
                appDatabase.companyDao().deleteAll()
                appDatabase.departmentDao().deleteAll()

                Toast.makeText(this@DatabaseManagerActivity, "Local database cleared successfully", Toast.LENGTH_SHORT).show()

                if (_databaseMode.value == DatabaseMode.LOCAL) {
                    loadDataForTab(_currentTab.value)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing local database", e)
                Toast.makeText(this@DatabaseManagerActivity, "Clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        private const val TAG = "DatabaseManager"
    }
}

// Enums for better type safety
enum class DatabaseTab {
    USERS, COMPLAINTS, COMPANIES, DEPARTMENTS
}

enum class DatabaseMode {
    FIREBASE, LOCAL
}

enum class RecordType {
    USER, COMPLAINT, COMPANY, DEPARTMENT
}