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
                .onSuccess { _uiState.update { s -> s.copy(isLoading = false, profile = it.toUiModel()) } }
                .onFailure { _uiState.update { s -> s.copy(isLoading = false, error = it.message) } }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}