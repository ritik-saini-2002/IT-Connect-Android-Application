package com.example.ritik_2.main

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.data.repository.UserProfileDomain
import com.example.ritik_2.data.repository.UserRepository
import com.example.ritik_2.pocketbase.PocketBaseSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isLoading: Boolean            = true,
    val userProfile: UserProfileData? = null,
    val error: String?                = null,
    val isRefreshing: Boolean         = false
)

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG          = "MainViewModel"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val MAX_RETRIES  = 3
        private const val RETRY_DELAY  = 2000L
    }

    private val repository = UserRepository.getInstance()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var cachedProfile: UserProfileData? = null
    private var cacheTimestamp: Long            = 0L
    private var loadJob: Job?                   = null

    fun loadUserProfile(userId: String, forceRefresh: Boolean = false) {
        if (loadJob?.isActive == true && !forceRefresh) return

        loadJob = viewModelScope.launch {
            try {
                if (!forceRefresh && isCacheValid()) {
                    _uiState.update { it.copy(userProfile = cachedProfile, isLoading = false) }
                    return@launch
                }

                _uiState.update { it.copy(isLoading = true, error = null) }

                var lastError: Exception? = null
                var profile: UserProfileData? = null

                repeat(MAX_RETRIES) { attempt ->
                    if (profile != null) return@repeat
                    try {
                        val result = repository.getUserProfile(userId)
                        if (result.isSuccess) {
                            profile = result.getOrThrow().toUiModel()
                        } else {
                            lastError = result.exceptionOrNull() as? Exception
                            if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY)
                        }
                    } catch (e: Exception) {
                        lastError = e
                        if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY)
                    }
                }

                if (profile != null) {
                    cachedProfile  = profile
                    cacheTimestamp = System.currentTimeMillis()
                    _uiState.update { it.copy(userProfile = profile, isLoading = false, error = null) }
                    Log.d(TAG, "Profile loaded: ${profile!!.name} ✅")
                } else {
                    val errMsg = lastError?.message ?: "Failed to load profile"
                    if (errMsg.contains("deactivated")) {
                        _uiState.update { it.copy(isLoading = false, error = "deactivated") }
                    } else {
                        // Fallback to session data
                        val fallback = buildSessionFallback()
                        _uiState.update { it.copy(userProfile = fallback, isLoading = false) }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "loadUserProfile error: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refresh() {
        val userId = PocketBaseSessionManager.getUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadUserProfile(userId, forceRefresh = true)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun isCacheValid() =
        cachedProfile != null &&
                (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS

    private fun buildSessionFallback() = UserProfileData(
        id          = PocketBaseSessionManager.getUserId() ?: "",
        name        = PocketBaseSessionManager.getName() ?: "User",
        email       = PocketBaseSessionManager.getEmail() ?: "",
        role        = PocketBaseSessionManager.getRole() ?: "",
        companyName = ""
    )

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
    }
}

// ── Extension: domain → UI model ─────────────────────────────
fun UserProfileDomain.toUiModel() = UserProfileData(
    id                 = id,
    name               = name,
    email              = email,
    role               = role,
    companyName        = companyName,
    designation        = designation,
    imageUrl           = if (imageUrl.isNotBlank()) try { Uri.parse(imageUrl) } catch (_: Exception) { null } else null,
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

// ── UI data model ─────────────────────────────────────────────
data class UserProfileData(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val companyName: String,
    val designation: String         = "IT Professional",
    val imageUrl: Uri?              = null,
    val phoneNumber: String         = "",
    val experience: Int             = 0,
    val completedProjects: Int      = 0,
    val activeProjects: Int         = 0,
    val pendingTasks: Int           = 0,
    val completedTasks: Int         = 0,
    val totalComplaints: Int        = 0,
    val resolvedComplaints: Int     = 0,
    val pendingComplaints: Int      = 0,
    val isActive: Boolean           = true,
    val documentPath: String        = "",
    val permissions: List<String>   = emptyList(),

    val performanceScore: Double = if (completedProjects + activeProjects > 0)
        (completedProjects.toDouble() / (completedProjects + activeProjects)) * 100 else 0.0,

    val complaintsRate: Double = if (totalComplaints > 0)
        (resolvedComplaints.toDouble() / totalComplaints) * 100 else 100.0
)