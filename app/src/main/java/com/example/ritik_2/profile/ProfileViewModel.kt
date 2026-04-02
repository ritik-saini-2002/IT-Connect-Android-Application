package com.example.ritik_2.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.main.UserProfileData
import com.example.ritik_2.main.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading  : Boolean          = true,
    val profile    : UserProfileData? = null,
    val error      : String?          = null
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
                    _uiState.update { it.copy(isLoading = false, profile = profile.toUiModel()) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun updateField(userId: String, field: String, value: String) {
        viewModelScope.launch {
            dataSource.updateUserProfile(userId, mapOf(field to value))
                .onSuccess { loadProfile(userId) }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}