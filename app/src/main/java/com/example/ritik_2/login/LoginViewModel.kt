package com.example.ritik_2.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.auth.AuthState
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

    /**
     * Fast login — only blocks on auth-with-password.
     * isActive and profile enrichment happen in background (see AuthRepository).
     * Typical response time: ~200–400ms (just one network call).
     */
    fun login(email: String, password: String) {
        if (_loginState.value == AuthState.Loading) return  // debounce double-tap
        viewModelScope.launch {
            _loginState.value = AuthState.Loading
            _loginState.value = authRepository.login(email.trim(), password)
                .fold(
                    onSuccess = { AuthState.Success() },
                    onFailure = { AuthState.Error(mapError(it.message)) }
                )
        }
    }

    private val _otpLoginState = MutableStateFlow<AuthState>(AuthState.Idle)
    val otpLoginState: StateFlow<AuthState> = _otpLoginState.asStateFlow()

    fun sendLoginOtp(email: String) {
        viewModelScope.launch {
            _otpLoginState.value = AuthState.Loading
            _otpLoginState.value = authRepository.sendLoginOtp(email.trim())
                .fold(
                    onSuccess = { AuthState.OtpLoginSent },
                    onFailure = { AuthState.Error(it.message ?: "Failed to send OTP") }
                )
        }
    }

    fun loginWithOtp(email: String, otp: String) {
        viewModelScope.launch {
            _otpLoginState.value = AuthState.Loading
            _otpLoginState.value = authRepository.loginWithOtp(email.trim(), otp.trim())
                .fold(
                    onSuccess = { AuthState.Success() },
                    onFailure = { AuthState.Error(it.message ?: "OTP login failed") }
                )
        }
    }

    fun resetOtpLoginState() { _otpLoginState.value = AuthState.Idle }

//    fun sendPasswordReset(email: String) {
//        viewModelScope.launch {
//            _resetState.value = AuthState.Loading
//            _resetState.value = authRepository.sendPasswordReset(email.trim())
//                .fold(
//                    onSuccess = { AuthState.Success() },
//                    onFailure = { AuthState.Error(it.message ?: "Failed to send reset link") }
//                )
//        }
//    }

    // LoginViewModel.kt — add these below sendPasswordReset()

    fun sendOtp(email: String) {
        viewModelScope.launch {
            _resetState.value = AuthState.Loading
            _resetState.value = authRepository.sendOtp(email.trim())
                .fold(
                    onSuccess = { AuthState.OtpSent },
                    onFailure = { AuthState.Error(it.message ?: "Failed to send OTP") }
                )
        }
    }

    fun verifyOtp(email: String, otp: String) {
        viewModelScope.launch {
            _resetState.value = AuthState.Loading
            _resetState.value = authRepository.verifyOtp(email, otp)
                .fold(
                    onSuccess = { AuthState.OtpVerified },
                    onFailure = { AuthState.Error(it.message ?: "Invalid OTP") }
                )
        }
    }

    fun verifyOtpAndResetPassword(email: String, otp: String, newPassword: String) {
        viewModelScope.launch {
            _resetState.value = AuthState.Loading
            _resetState.value = authRepository.verifyOtpAndResetPassword(email.trim(), otp.trim(), newPassword)
                .fold(
                    onSuccess = { AuthState.Success() },
                    onFailure = { AuthState.Error(it.message ?: "Reset failed") }
                )
        }
    }

    fun resetPassword(email: String, otp: String, newPassword: String) {
        viewModelScope.launch {
            _resetState.value = AuthState.Loading
            _resetState.value = authRepository.verifyOtpAndResetPassword(email.trim(), otp.trim(), newPassword)
                .fold(
                    onSuccess = { AuthState.Success() },
                    onFailure = { AuthState.Error(it.message ?: "Reset failed") }
                )
        }
    }

    fun resetLoginState() { _loginState.value = AuthState.Idle }
    fun resetResetState()  { _resetState.value = AuthState.Idle }

    private fun mapError(msg: String?): String = when {
        msg == null                                    -> "Unknown error"
        msg.contains("disabled", ignoreCase = true)   -> "Account disabled. Contact your administrator."
        msg.contains("400")                            -> "Invalid email or password"
        msg.contains("network",  ignoreCase = true)   -> "Network error — check Wi-Fi"
        msg.contains("timeout",  ignoreCase = true)   -> "Connection timed out"
        msg.contains("connect",  ignoreCase = true)   -> "Cannot reach server"
        else                                           -> "Login failed: $msg"
    }
}