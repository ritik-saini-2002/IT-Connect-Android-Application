package com.example.ritik_2.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.profile.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// Maps UserProfile fields to what MainScreen / AppSidebar / ProfileActivity expect
data class UserProfileData(
    val id                   : String = "",
    val name                 : String = "",
    val email                : String = "",
    val role                 : String = "",
    val department           : String = "",
    val designation          : String = "",
    val imageUrl             : String = "",
    // Extended fields used by ProfileActivity
    val phoneNumber          : String = "",
    val companyName          : String = "",
    val address              : String = "",
    val employeeId           : String = "",
    val reportingTo          : String = "",
    val salary               : Double = 0.0,
    val experience           : Int    = 0,
    val completedProjects    : Int    = 0,
    val activeProjects       : Int    = 0,
    val pendingTasks         : Int    = 0,
    val completedTasks       : Int    = 0,
    val totalComplaints      : Int    = 0,
    val resolvedComplaints   : Int    = 0,
    val pendingComplaints    : Int    = 0,
    val isActive             : Boolean = true,
    val documentPath         : String = "",
    val permissions          : List<String> = emptyList(),
    val emergencyContactName     : String = "",
    val emergencyContactPhone    : String = "",
    val emergencyContactRelation : String = "",
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
    private val authRepository: AuthRepository
) : ViewModel() {

    var uiState by mutableStateOf(MainUiState())
        private set

    init { load() }

    fun reload() {
        // Only reload if not already loading
        if (!uiState.isLoading) load()
    }

    private fun load() {
        val userId = authRepository.getSession()?.userId ?: run {
            uiState = uiState.copy(isLoading = false, error = "No session")
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            dataSource.getUserProfile(userId)
                .onSuccess { profile ->
                    uiState = uiState.copy(
                        isLoading   = false,
                        userProfile = profile.toUiModel()
                    )
                }
                .onFailure { e ->
                    uiState = uiState.copy(isLoading = false, error = e.message)
                }
        }
    }
}