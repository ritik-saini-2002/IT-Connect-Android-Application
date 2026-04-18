package com.example.ritik_2.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.ConnectivityMonitor
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.localdatabase.AppDatabase
import com.example.ritik_2.profile.toUiModel
import com.example.ritik_2.core.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfileData(
    val id                       : String  = "",
    val name                     : String  = "",
    val email                    : String  = "",
    val role                     : String  = "",
    val department               : String  = "",
    val designation              : String  = "",
    val imageUrl                 : String  = "",
    val phoneNumber              : String  = "",
    val companyName              : String  = "",
    val address                  : String  = "",
    val employeeId               : String  = "",
    val reportingTo              : String  = "",
    val salary                   : Double  = 0.0,
    val experience               : Int     = 0,
    val completedProjects        : Int     = 0,
    val activeProjects           : Int     = 0,
    val pendingTasks             : Int     = 0,
    val completedTasks           : Int     = 0,
    val totalComplaints          : Int     = 0,
    val resolvedComplaints       : Int     = 0,
    val pendingComplaints        : Int     = 0,
    val isActive                 : Boolean = true,
    val documentPath             : String  = "",
    val permissions              : List<String> = emptyList(),
    val emergencyContactName     : String  = "",
    val emergencyContactPhone    : String  = "",
    val emergencyContactRelation : String  = "",
    val needsProfileCompletion   : Boolean = true
)

data class MainUiState(
    val isLoading  : Boolean          = true,
    val userProfile: UserProfileData? = null,
    val error      : String?          = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val authRepository: AuthRepository,
    private val syncManager   : SyncManager,
    private val monitor       : ConnectivityMonitor,
    private val db            : AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    // Foreground role_definitions sync — drives the thin top progress bar.
    private val _roleSyncing = MutableStateFlow(false)
    val roleSyncing: StateFlow<Boolean> = _roleSyncing.asStateFlow()

    init {
        load()
        checkPending()
        syncRoleDefinitionsForeground()
    }

    fun reload() {
        if (!_uiState.value.isLoading) load()
    }

    private fun load() {
        val userId = authRepository.getSession()?.userId ?: run {
            _uiState.value = MainUiState(isLoading = false, error = "No session")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // ── Step 1: Load from local Room cache immediately (never hangs) ──
            val cached = try { db.userDao().getById(userId) } catch (_: Exception) { null }
            if (cached != null) {
                _uiState.value = MainUiState(
                    isLoading   = false,
                    userProfile = cachedToUiModel(cached)
                )
            }

            // ── Step 2: If server is reachable, refresh from network ──────────
            if (monitor.serverReachable.value) {
                try {
                    dataSource.getUserProfile(userId)
                        .onSuccess { profile ->
                            _uiState.value = MainUiState(
                                isLoading   = false,
                                userProfile = profile.toUiModel()
                            )
                        }
                        .onFailure { e ->
                            // Only show error if we have nothing cached
                            if (cached == null)
                                _uiState.value = MainUiState(isLoading = false, error = e.message)
                            else
                                _uiState.value = _uiState.value.copy(isLoading = false)
                        }
                } catch (e: Exception) {
                    // Network failed after probe said reachable — stay on cached data
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } else {
                // Offline — cached data already shown, just stop loading
                if (cached == null)
                    _uiState.value = MainUiState(isLoading = false,
                        error = "Server unreachable and no cached data")
                else
                    _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun checkPending() {
        viewModelScope.launch {
            _pendingCount.value = syncManager.pendingCount()
        }
    }

    /**
     * Pulls the latest role_definitions for the current user's company so
     * permission-gated tiles render with up-to-date role templates before the
     * user taps into anything. Runs on the IO dispatcher via SyncManager and
     * exposes [roleSyncing] for a thin progress bar in the UI.
     */
    private fun syncRoleDefinitionsForeground() {
        viewModelScope.launch {
            val session = authRepository.getSession() ?: return@launch
            val sc = try {
                db.userDao().getById(session.userId)?.sanitizedCompanyName
            } catch (_: Exception) { null } ?: return@launch
            if (sc.isBlank()) return@launch
            _roleSyncing.value = true
            try {
                syncManager.syncRoleDefinitions(sc)
            } finally {
                _roleSyncing.value = false
            }
        }
    }

    // Convert Room entity directly to UI model without going through the network
    private fun cachedToUiModel(u: com.example.ritik_2.localdatabase.UserEntity) = UserProfileData(
        id                       = u.id,
        name                     = u.name,
        email                    = u.email,
        role                     = u.role,
        department               = u.department,
        designation              = u.designation,
        imageUrl                 = u.imageUrl,
        phoneNumber              = u.phoneNumber,
        companyName              = u.companyName,
        address                  = u.address,
        employeeId               = u.employeeId,
        reportingTo              = u.reportingTo,
        salary                   = u.salary,
        experience               = u.experience,
        completedProjects        = u.completedProjects,
        activeProjects           = u.activeProjects,
        pendingTasks             = u.pendingTasks,
        completedTasks           = u.completedTasks,
        totalComplaints          = u.totalComplaints,
        resolvedComplaints       = u.resolvedComplaints,
        pendingComplaints        = u.pendingComplaints,
        isActive                 = u.isActive,
        documentPath             = u.documentPath,
        permissions              = u.permissions,
        emergencyContactName     = u.emergencyContactName,
        emergencyContactPhone    = u.emergencyContactPhone,
        emergencyContactRelation = u.emergencyContactRelation,
        needsProfileCompletion   = u.needsProfileCompletion
    )
}