package com.saini.ritik.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.AppConfig
import com.saini.ritik.core.SyncManager
import com.saini.ritik.data.model.UserProfile
import com.saini.ritik.data.source.AppDataSource
import com.saini.ritik.localdatabase.AppDatabase
import com.saini.ritik.localdatabase.UserEntity
import com.saini.ritik.main.UserProfileData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean          = true,
    val profile  : UserProfileData? = null,
    val error    : String?          = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val authRepository: AuthRepository,
    private val syncManager   : SyncManager,
    private val db            : AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            // Cache-first: render Room immediately so the screen never shows a
            // blank spinner while waiting on the network.
            val cached = try { db.userDao().getById(userId) } catch (_: Exception) { null }
            if (cached != null) {
                _uiState.update {
                    it.copy(isLoading = false, profile = cached.toUiModel(), error = null)
                }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            // Refresh from network in background — keep whatever we showed
            // from cache on failure.
            dataSource.getUserProfile(userId)
                .onSuccess { profile ->
                    _uiState.update { it.copy(isLoading = false, profile = profile.toUiModel()) }
                }
                .onFailure { e ->
                    if (cached == null)
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    else
                        _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    /** Update the target user's permissions in user_access_control + users records. */
    fun updateUserPermissions(targetUserId: String, perms: List<String>) {
        viewModelScope.launch {
            try {
                val token     = syncManager.getAdminToken()
                val permsJson = Json.encodeToString(perms)

                // 1) user_access_control.permissions
                val acRes = syncManager.pbGet(
                    "${AppConfig.BASE_URL}/api/collections/user_access_control/records" +
                            "?filter=(userId='$targetUserId')&perPage=1", token
                )
                val acId = JSONObject(acRes).optJSONArray("items")
                    ?.optJSONObject(0)?.optString("id")
                if (!acId.isNullOrEmpty()) {
                    syncManager.pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/user_access_control/records/$acId",
                        token,
                        JSONObject().put("permissions", permsJson).toString()
                    )
                }

                // 2) users.permissions (kept in sync)
                syncManager.pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/users/records/$targetUserId",
                    token,
                    JSONObject().put("permissions", permsJson).toString()
                )

                // 3) Refresh local profile state
                loadProfile(targetUserId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to update permissions: ${e.message}") }
            }
        }
    }
}

// Extension: UserEntity (Room) → UserProfileData — used for cache-first rendering
fun UserEntity.toUiModel() = UserProfileData(
    id                       = id,
    name                     = name,
    email                    = email,
    role                     = role,
    department               = department,
    designation              = designation,
    imageUrl                 = imageUrl,
    phoneNumber              = phoneNumber,
    companyName              = companyName,
    address                  = address,
    employeeId               = employeeId,
    reportingTo              = reportingTo,
    salary                   = salary,
    experience               = experience,
    completedProjects        = completedProjects,
    activeProjects           = activeProjects,
    pendingTasks             = pendingTasks,
    completedTasks           = completedTasks,
    totalComplaints          = totalComplaints,
    resolvedComplaints       = resolvedComplaints,
    pendingComplaints        = pendingComplaints,
    isActive                 = isActive,
    documentPath             = documentPath,
    permissions              = permissions,
    emergencyContactName     = emergencyContactName,
    emergencyContactPhone    = emergencyContactPhone,
    emergencyContactRelation = emergencyContactRelation,
    needsProfileCompletion   = needsProfileCompletion
)

// Extension: UserProfile → UserProfileData
// UserProfileData is now defined in MainViewModel.kt (same package: com.saini.ritik.main)
fun UserProfile.toUiModel() = UserProfileData(
    id                       = id,
    name                     = name,
    email                    = email,
    role                     = role,
    department               = department,
    designation              = designation,
    imageUrl                 = imageUrl,
    phoneNumber              = phoneNumber,
    companyName              = companyName,
    address                  = address,
    employeeId               = employeeId,
    reportingTo              = reportingTo,
    salary                   = salary,
    experience               = experience,
    completedProjects        = completedProjects,
    activeProjects           = activeProjects,
    pendingTasks             = pendingTasks,
    completedTasks           = completedTasks,
    totalComplaints          = totalComplaints,
    resolvedComplaints       = resolvedComplaints,
    pendingComplaints        = pendingComplaints,
    isActive                 = isActive,
    documentPath             = documentPath,
    permissions              = permissions,
    emergencyContactName     = emergencyContactName,
    emergencyContactPhone    = emergencyContactPhone,
    emergencyContactRelation = emergencyContactRelation,
    needsProfileCompletion   = needsProfileCompletion
)