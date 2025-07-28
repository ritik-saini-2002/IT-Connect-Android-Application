package com.example.ritik_2.complaint.viewcomplaint

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.R
import com.example.ritik_2.complaint.complaintregistration.RegisterComplain
import com.example.ritik_2.complaint.viewcomplaint.data.ComplaintRepository
import com.example.ritik_2.complaint.viewcomplaint.data.UserRepository
import com.example.ritik_2.complaint.viewcomplaint.data.models.*
import com.example.ritik_2.complaint.viewcomplaint.ui.ComplaintViewScreen
import com.example.ritik_2.complaint.viewcomplaint.utils.NotificationManager
import com.example.ritik_2.complaint.viewcomplaint.utils.PermissionChecker
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ComplaintViewActivity : ComponentActivity() {
    // Repositories
    private lateinit var complaintRepository: ComplaintRepository
    private lateinit var userRepository: UserRepository
    private lateinit var notificationManager: NotificationManager
    private lateinit var permissionChecker: PermissionChecker

    // Firebase instances
    private val auth = FirebaseAuth.getInstance()

    // State flows
    private val _complaints = MutableStateFlow<List<ComplaintWithDetails>>(emptyList())
    val complaints: StateFlow<List<ComplaintWithDetails>> = _complaints

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    val sortOption: StateFlow<SortOption> = _sortOption

    private val _filterOption = MutableStateFlow<String?>(null)
    val filterOption: StateFlow<String?> = _filterOption

    private val _currentUserData = MutableStateFlow<UserData?>(null)
    val currentUserData: StateFlow<UserData?> = _currentUserData

    private val _userPermissions = MutableStateFlow<UserPermissions?>(null)
    val userPermissions: StateFlow<UserPermissions?> = _userPermissions

    private val _hasMoreData = MutableStateFlow(true)
    val hasMoreData: StateFlow<Boolean> = _hasMoreData

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _showRefreshAnimation = MutableStateFlow(false)
    val showRefreshAnimation: StateFlow<Boolean> = _showRefreshAnimation

    private val _viewMode = MutableStateFlow(ViewMode.PERSONAL)
    val viewMode: StateFlow<ViewMode> = _viewMode

    private val _availableEmployees = MutableStateFlow<List<UserData>>(emptyList())
    val availableEmployees: StateFlow<List<UserData>> = _availableEmployees

    private val _complaintStats = MutableStateFlow<ComplaintStats?>(null)
    val complaintStats: StateFlow<ComplaintStats?> = _complaintStats

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    companion object {
        private const val TAG = "ComplaintViewActivity"
        private const val DEBUG_MODE = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize repositories and utilities
        initializeRepositories()

        // Check authentication
        val currentUser = auth.currentUser
        if (currentUser == null) {
            handleAuthenticationFailure()
            return
        }

        // Load initial data
        loadUserDataAndPermissions()

        setContent {
            MaterialTheme {
                val complaintsList by complaints.collectAsState()
                val loading by isLoading.collectAsState()
                val query by searchQuery.collectAsState()
                val sort by sortOption.collectAsState()
                val filter by filterOption.collectAsState()
                val isRefreshing by refreshing.collectAsState()
                val moreData by hasMoreData.collectAsState()
                val showAnimation by showRefreshAnimation.collectAsState()
                val userData by currentUserData.collectAsState()
                val permissions by userPermissions.collectAsState()
                val currentViewMode by viewMode.collectAsState()
                val employees by availableEmployees.collectAsState()
                val stats by complaintStats.collectAsState()
                val error by errorMessage.collectAsState()

                if (loading && complaintsList.isEmpty() && userData == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colorResource(id = R.color.bright_orange))
                    }
                } else {
                    ComplaintViewScreen(
                        complaints = complaintsList,
                        isRefreshing = isRefreshing,
                        showRefreshAnimation = showAnimation,
                        searchQuery = query,
                        currentSortOption = sort,
                        currentFilterOption = filter,
                        hasMoreData = moreData,
                        currentUserData = userData,
                        userPermissions = permissions,
                        currentViewMode = currentViewMode,
                        availableEmployees = employees,
                        complaintStats = stats,
                        errorMessage = error,
                        onDeleteComplaint = ::deleteComplaint,
                        onUpdateComplaint = ::updateComplaint,
                        onAssignComplaint = ::assignComplaint,
                        onReopenComplaint = ::reopenComplaint,
                        onCloseComplaint = ::closeComplaint,
                        onChangeStatus = ::changeComplaintStatus,
                        onLoadMore = ::loadMoreComplaints,
                        onRefresh = ::refreshComplaints,
                        onSearchQueryChange = ::updateSearchQuery,
                        onSortOptionChange = ::updateSortOption,
                        onFilterOptionChange = ::updateFilterOption,
                        onViewModeChange = ::updateViewMode,
                        onNavigateToActivity = ::navigateToActivity,
                        onBackClick = { finish() },
                        onClearError = { _errorMessage.value = null }
                    )
                }
            }
        }
    }

    private fun initializeRepositories() {
        complaintRepository = ComplaintRepository()
        userRepository = UserRepository()
        notificationManager = NotificationManager(this)
        permissionChecker = PermissionChecker()
    }

    private fun handleAuthenticationFailure() {
        Log.e(TAG, "User not authenticated")
        _isLoading.value = false
        _errorMessage.value = "Authentication required. Please log in again."

        lifecycleScope.launch {
            delay(2000)
            finish()
        }
    }

    private fun loadUserDataAndPermissions() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            handleAuthenticationFailure()
            return
        }

        _isLoading.value = true
        Log.d(TAG, "Loading user data for ID: $userId")

        lifecycleScope.launch {
            try {
                val userData = userRepository.getUserData(userId)
                if (userData == null) {
                    _errorMessage.value = "User profile not found. Please contact administrator."
                    _isLoading.value = false
                    return@launch
                }

                _currentUserData.value = userData
                Log.d(TAG, "User data loaded successfully: ${userData.name} (${userData.role})")

                // Set permissions and continue with initialization
                setUserPermissions(userData)
                loadInitialComplaints()

                // Load additional data if permissions allow
                val permissions = _userPermissions.value
                if (permissions?.canAssignComplaints == true) {
                    loadAvailableEmployees(userData)
                }
                if (permissions?.canViewStatistics == true) {
                    loadComplaintStatistics(userData)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading user data", e)
                _errorMessage.value = "Error loading user data: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private fun setUserPermissions(userData: UserData) {
        val permissions = permissionChecker.getPermissionsForRole(userData.role)
        _userPermissions.value = permissions

        // Set initial view mode based on role
        val initialViewMode = permissionChecker.getInitialViewMode(userData.role)
        _viewMode.value = initialViewMode

        Log.d(TAG, "Permissions set for role: ${userData.role}")
        Log.d(TAG, "Initial view mode set to: $initialViewMode")
    }

    private fun loadInitialComplaints() {
        Log.d(TAG, "Loading initial complaints for view mode: ${_viewMode.value}")
        _isLoading.value = true
        _complaints.value = emptyList()
        _hasMoreData.value = true
        loadComplaints(true)
    }

    private fun loadMoreComplaints() {
        if (_hasMoreData.value && !_isLoading.value) {
            Log.d(TAG, "Loading more complaints")
            loadComplaints(false)
        }
    }

    private fun refreshComplaints() {
        Log.d(TAG, "Refreshing complaints")
        _refreshing.value = true
        _showRefreshAnimation.value = true
        _hasMoreData.value = true

        loadComplaints(true) {
            lifecycleScope.launch {
                delay(1000)
                _refreshing.value = false
                _showRefreshAnimation.value = false
            }
        }
    }

    private fun loadComplaints(isInitialLoad: Boolean, onComplete: () -> Unit = {}) {
        val userId = auth.currentUser?.uid
        val userData = _currentUserData.value

        if (userId == null || userData == null) {
            Log.w(TAG, "Cannot load complaints: userId or userData is null")
            _isLoading.value = false
            onComplete()
            return
        }

        lifecycleScope.launch {
            try {
                if (isInitialLoad) {
                    _isLoading.value = true
                }

                val complaints = complaintRepository.loadComplaints(
                    userId = userId,
                    userData = userData,
                    viewMode = _viewMode.value,
                    sortOption = _sortOption.value,
                    searchQuery = _searchQuery.value,
                    filterOption = _filterOption.value
                )

                if (isInitialLoad) {
                    _complaints.value = complaints
                } else {
                    val currentList = _complaints.value
                    _complaints.value = currentList + complaints
                }

                _hasMoreData.value = complaints.size >= 15 // Batch size

            } catch (e: Exception) {
                Log.e(TAG, "Error loading complaints", e)
                _errorMessage.value = "Error loading complaints: ${e.message}"

                if (isInitialLoad) {
                    _complaints.value = emptyList()
                }
                _hasMoreData.value = false
            } finally {
                _isLoading.value = false
                onComplete()
            }
        }
    }

    private fun loadAvailableEmployees(userData: UserData) {
        lifecycleScope.launch {
            try {
                val employees = userRepository.getAvailableEmployees(userData)
                _availableEmployees.value = employees
                Log.d(TAG, "Loaded ${employees.size} available employees")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading employees", e)
                _errorMessage.value = "Error loading employees: ${e.message}"
            }
        }
    }

    private fun loadComplaintStatistics(userData: UserData) {
        lifecycleScope.launch {
            try {
                val stats = complaintRepository.getComplaintStatistics(userData.sanitizedCompanyName)
                _complaintStats.value = stats
                Log.d(TAG, "Loaded complaint statistics: $stats")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading statistics", e)
                _errorMessage.value = "Error loading statistics: ${e.message}"
            }
        }
    }

    // Action handlers
    private fun assignComplaint(complaintId: String, assigneeId: String, assigneeName: String) {
        val currentUser = _currentUserData.value ?: return
        val permissions = _userPermissions.value ?: return

        if (!permissions.canAssignComplaints) {
            showError("You don't have permission to assign complaints")
            return
        }

        lifecycleScope.launch {
            try {
                val assigneeData = _availableEmployees.value.find { it.userId == assigneeId }
                if (assigneeData == null) {
                    showError("Assignee not found")
                    return@launch
                }

                complaintRepository.assignComplaintToUser(
                    complaintId = complaintId,
                    assigneeId = assigneeId,
                    assigneeName = assigneeName,
                    assigneeData = assigneeData,
                    currentUser = currentUser
                )

                // Send notifications
                notificationManager.sendAssignmentNotification(assigneeId, complaintId, currentUser.name)
                notificationManager.notifyManagement(complaintId, "assigned", currentUser)

                showSuccess("Complaint assigned to $assigneeName")
                refreshComplaints()

            } catch (e: Exception) {
                Log.e(TAG, "Error assigning complaint", e)
                showError("Error assigning complaint: ${e.message}")
            }
        }
    }

    private fun reopenComplaint(complaintId: String) {
        val currentUser = _currentUserData.value ?: return
        val permissions = _userPermissions.value ?: return

        if (!permissions.canReopenComplaints) {
            showError("You don't have permission to reopen complaints")
            return
        }

        lifecycleScope.launch {
            try {
                complaintRepository.reopenComplaint(complaintId, currentUser)
                showSuccess("Complaint reopened")
                refreshComplaints()
            } catch (e: Exception) {
                Log.e(TAG, "Error reopening complaint", e)
                showError("Error reopening complaint: ${e.message}")
            }
        }
    }

    private fun closeComplaint(complaintId: String, resolution: String) {
        val currentUser = _currentUserData.value ?: return
        val permissions = _userPermissions.value ?: return

        if (!permissions.canCloseComplaints) {
            showError("You don't have permission to close complaints")
            return
        }

        lifecycleScope.launch {
            try {
                complaintRepository.closeComplaint(complaintId, resolution, currentUser)
                notificationManager.notifyManagement(complaintId, "closed", currentUser)
                showSuccess("Complaint closed successfully")
                refreshComplaints()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing complaint", e)
                showError("Error closing complaint: ${e.message}")
            }
        }
    }

    private fun changeComplaintStatus(complaintId: String, newStatus: String, reason: String) {
        val currentUser = _currentUserData.value ?: return
        val permissions = _userPermissions.value ?: return

        if (!permissions.canEditComplaints) {
            showError("You don't have permission to change complaint status")
            return
        }

        lifecycleScope.launch {
            try {
                complaintRepository.changeComplaintStatus(complaintId, newStatus, reason, currentUser)
                showSuccess("Status updated to $newStatus")
                refreshComplaints()
            } catch (e: Exception) {
                Log.e(TAG, "Error changing status", e)
                showError("Error changing status: ${e.message}")
            }
        }
    }

    private fun updateComplaint(complaintId: String, updates: ComplaintUpdates) {
        val currentUser = _currentUserData.value ?: return
        val permissions = _userPermissions.value ?: return

        if (!permissions.canEditComplaints) {
            showError("You don't have permission to edit complaints")
            return
        }

        lifecycleScope.launch {
            try {
                complaintRepository.updateComplaint(complaintId, updates, currentUser)
                showSuccess("Complaint updated successfully")
                refreshComplaints()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating complaint", e)
                showError("Error updating complaint: ${e.message}")
            }
        }
    }

    private fun deleteComplaint(complaintId: String) {
        val currentUser = _currentUserData.value ?: return
        val permissions = _userPermissions.value ?: return

        if (!permissions.canDeleteComplaints) {
            showError("You don't have permission to delete complaints")
            return
        }

        lifecycleScope.launch {
            try {
                complaintRepository.deleteComplaint(complaintId, currentUser)
                showSuccess("Complaint deleted successfully")

                // Update local list immediately
                _complaints.value = _complaints.value.filter { it.id != complaintId }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting complaint", e)
                showError("Error deleting complaint: ${e.message}")
            }
        }
    }

    // UI state update functions
    private fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        loadInitialComplaints()
    }

    private fun updateSortOption(option: SortOption) {
        _sortOption.value = option
        loadInitialComplaints()
    }

    private fun updateFilterOption(category: String?) {
        _filterOption.value = category
        loadInitialComplaints()
    }

    private fun updateViewMode(mode: ViewMode) {
        Log.d(TAG, "Updating view mode to: $mode")
        _viewMode.value = mode
        loadInitialComplaints()
    }

    private fun navigateToActivity(activityClass: String) {
        try {
            when (activityClass) {
                "RegisterComplain" -> {
                    startActivity(Intent(this, RegisterComplain::class.java))
                }
                "UserManagement" -> {
                    showInfo("User Management - Coming Soon")
                }
                "Statistics" -> {
                    showInfo("Statistics Dashboard - Coming Soon")
                }
                "Settings" -> {
                    showInfo("Settings - Coming Soon")
                }
                else -> {
                    showError("Activity not found: $activityClass")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to activity: $activityClass", e)
            showError("Error opening $activityClass")
        }
    }

    // Helper functions for showing messages
    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        _errorMessage.value = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showInfo(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}