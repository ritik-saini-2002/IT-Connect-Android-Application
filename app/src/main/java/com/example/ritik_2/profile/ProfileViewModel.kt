package com.example.ritik_2.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.data.model.UserProfile
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.main.UserProfileData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean          = true,
    val profile  : UserProfileData? = null,
    val error    : String?          = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            dataSource.getUserProfile(userId)
                .onSuccess { profile ->
                    _uiState.update { s ->
                        s.copy(isLoading = false, profile = profile.toUiModel())
                    }
                }
                .onFailure { e ->
                    _uiState.update { s ->
                        s.copy(isLoading = false, error = e.message)
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}

// Extension function that converts UserProfile → UserProfileData
// Defined here so both ProfileViewModel and MainViewModel can use it
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