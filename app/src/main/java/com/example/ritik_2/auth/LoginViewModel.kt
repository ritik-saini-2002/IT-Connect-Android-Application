package com.example.ritik_2.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<AuthState>(AuthState.Idle)
    val loginState: StateFlow<AuthState> = _loginState.asStateFlow()

    private val _resetState = MutableStateFlow<AuthState>(AuthState.Idle)
    val resetState: StateFlow<AuthState> = _resetState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = AuthState.Loading
            _loginState.value = authRepository.login(email, password)
                .fold(
                    onSuccess = { AuthState.Success },
                    onFailure = { AuthState.Error(mapError(it.message)) }
                )
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _resetState.value = AuthState.Loading
            _resetState.value = authRepository.sendPasswordReset(email)
                .fold(
                    onSuccess = { AuthState.Success },
                    onFailure = { AuthState.Error(it.message ?: "Failed to send reset link") }
                )
        }
    }

    fun resetLoginState() { _loginState.value = AuthState.Idle }
    fun resetResetState()  { _resetState.value = AuthState.Idle }

    private fun mapError(msg: String?): String = when {
        msg == null                                -> "Unknown error"
        msg.contains("400")                        -> "Invalid email or password"
        msg.contains("network", ignoreCase = true) -> "Network error — check Wi-Fi"
        msg.contains("timeout", ignoreCase = true) -> "Connection timed out"
        msg.contains("connect", ignoreCase = true) -> "Cannot reach server"
        else                                       -> "Login failed: $msg"
    }
}