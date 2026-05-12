package com.saini.ritik.auth

sealed class AuthState {
    object Idle    : AuthState()
    object Loading : AuthState()
    object OtpLoginSent : AuthState()  // OTP sent for login (separate from reset OTP)
    data class Success(val needsProfileCompletion: Boolean = false) : AuthState()
    data class Error(val message: String) : AuthState()
    object OtpSent : AuthState()
    object OtpVerified : AuthState()
}