package com.example.ritik_2.data.model

data class AuthCredentials(
    val email    : String,
    val password : String
)

data class AuthSession(
    val userId      : String,
    val token       : String,
    val email       : String,
    val name        : String,
    val role        : String,
    val documentPath: String
)

data class RegistrationRequest(
    val email             : String,
    val password          : String,
    val name              : String,
    val phoneNumber       : String   = "",
    val designation       : String,
    val companyName       : String,
    val department        : String,
    val role              : String,
    val experience        : Int      = 0,
    val completedProjects : Int      = 0,
    val activeProjects    : Int      = 0,
    val complaints        : Int      = 0,
    val imageBytes        : ByteArray? = null
)