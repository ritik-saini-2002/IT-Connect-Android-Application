package com.example.ritik_2.auth

sealed class AuthState {
    object Idle    : AuthState()
    object Loading : AuthState()
    data class Success(val needsProfileCompletion: Boolean = false) : AuthState()
    data class Error(val message: String) : AuthState()
}