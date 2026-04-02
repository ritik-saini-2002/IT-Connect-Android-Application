package com.example.ritik_2.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.data.model.UserProfile
import com.example.ritik_2.data.source.AppDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State — uses UserProfileData (UI model) ─────────────────
data class MainUiState(
    val isLoading   : Boolean          = true,
    val isRefreshing: Boolean          = false,
    val userProfile : UserProfileData? = null,   // ✅ UI model, not domain model
    val error       : String?          = null
)

// ── UI model — lives here, used by MainScreen ──────────────────
data class UserProfileData(
    val id                : String,
    val name              : String,
    val email             : String,
    val role              : String,
    val companyName       : String,
    val designation       : String       = "IT Professional",
    val imageUrl          : String?      = null,
    val phoneNumber       : String       = "",
    val experience        : Int          = 0,
    val completedProjects : Int          = 0,
    val activeProjects    : Int          = 0,
    val pendingTasks      : Int          = 0,
    val completedTasks    : Int          = 0,
    val totalComplaints   : Int          = 0,
    val resolvedComplaints: Int          = 0,
    val pendingComplaints : Int          = 0,
    val isActive          : Boolean      = true,
    val documentPath      : String       = "",
    val permissions       : List<String> = emptyList()
) {
    // ✅ Computed properties — safe for data class (no constructor default issue)
    val performanceScore: Double
        get() = if (completedProjects + activeProjects > 0)
            completedProjects.toDouble() / (completedProjects + activeProjects) * 100.0
        else 0.0

    val complaintsRate: Double
        get() = if (totalComplaints > 0)
            resolvedComplaints.toDouble() / totalComplaints * 100.0
        else 100.0
}

// ── Domain → UI model mapper ───────────────────────────────────
fun UserProfile.toUiModel() = UserProfileData(
    id                 = id,
    name               = name,
    email              = email,
    role               = role,
    companyName        = companyName,
    designation        = designation,
    imageUrl           = imageUrl.ifBlank { null },
    phoneNumber        = phoneNumber,
    experience         = experience,
    completedProjects  = completedProjects,
    activeProjects     = activeProjects,
    pendingTasks       = pendingTasks,
    completedTasks     = completedTasks,
    totalComplaints    = totalComplaints,
    resolvedComplaints = resolvedComplaints,
    pendingComplaints  = pendingComplaints,
    isActive           = isActive,
    documentPath       = documentPath,
    permissions        = permissions
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataSource: AppDataSource
) : ViewModel() {

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val MAX_RETRIES  = 3
        private const val RETRY_DELAY  = 2000L
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var cachedProfile : UserProfileData? = null
    private var cacheTimestamp: Long             = 0L
    private var loadJob       : Job?             = null

    fun loadUserProfile(userId: String, forceRefresh: Boolean = false) {
        if (loadJob?.isActive == true && !forceRefresh) return
        loadJob = viewModelScope.launch {
            if (!forceRefresh && isCacheValid()) {
                _uiState.update { it.copy(userProfile = cachedProfile, isLoading = false) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }

            var profile: UserProfileData? = null
            var lastError: Throwable?     = null

            repeat(MAX_RETRIES) { attempt ->
                if (profile != null) return@repeat
                dataSource.getUserProfile(userId)
                    .onSuccess { profile = it.toUiModel() }   // ✅ map to UI model here
                    .onFailure {
                        lastError = it
                        if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY)
                    }
            }

            if (profile != null) {
                cachedProfile  = profile
                cacheTimestamp = System.currentTimeMillis()
                _uiState.update { it.copy(userProfile = profile, isLoading = false, error = null) }
            } else {
                val msg = lastError?.message ?: "Failed to load profile"
                _uiState.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    fun refresh(userId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            dataSource.getUserProfile(userId)
                .onSuccess { profile ->
                    val uiProfile  = profile.toUiModel()
                    cachedProfile  = uiProfile
                    cacheTimestamp = System.currentTimeMillis()
                    _uiState.update { it.copy(userProfile = uiProfile, isRefreshing = false, error = null) }
                }
                .onFailure {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun isCacheValid() =
        cachedProfile != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS

    override fun onCleared() { loadJob?.cancel() }
}