package com.example.ritik_2.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.SyncManager
import com.example.ritik_2.data.model.UserProfile
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.main.UserProfileData
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
    private val syncManager   : SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            dataSource.getUserProfile(userId)
                .onSuccess { profile ->
                    _uiState.update { it.copy(isLoading = false, profile = profile.toUiModel()) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
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

// Extension: UserProfile → UserProfileData
// UserProfileData is now defined in MainViewModel.kt (same package: com.example.ritik_2.main)
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