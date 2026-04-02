package com.example.ritik_2.auth

sealed class AuthState {
    object Idle            : AuthState()
    object Loading         : AuthState()
    object Success         : AuthState()
    data class Error(val message: String) : AuthState()
}