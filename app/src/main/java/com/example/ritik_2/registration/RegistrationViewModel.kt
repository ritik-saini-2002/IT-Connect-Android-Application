package com.example.ritik_2.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.data.model.RegistrationRequest
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.pocketbase.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RegistrationState {
    object Idle    : RegistrationState()
    object Loading : RegistrationState()
    data class Success(val userId: String) : RegistrationState()
    data class Error(val message: String)  : RegistrationState()
}

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val state: StateFlow<RegistrationState> = _state.asStateFlow()

    fun register(request: RegistrationRequest) {
        viewModelScope.launch {
            _state.value = RegistrationState.Loading
            dataSource.registerUser(request)
                .onSuccess { userId ->
                    // Save session after registration — get token by logging in
                    try {
                        val session = dataSource.login(request.email, request.password)
                        sessionManager.save(session)
                    } catch (_: Exception) {}
                    _state.value = RegistrationState.Success(userId)
                }
                .onFailure { _state.value = RegistrationState.Error(it.message ?: "Registration failed") }
        }
    }

    fun resetState() { _state.value = RegistrationState.Idle }
}