package com.example.ritik_2.administrator.manageuser.models

// ── Domain models ─────────────────────────────────────────────────────────────

data class MUCompany(
    val sanitizedName : String,
    val originalName  : String,
    val totalUsers    : Int,
    val activeUsers   : Int
)

data class MUDepartment(
    val sanitizedName  : String,
    val departmentName : String,
    val companyName    : String,   // sanitized company name
    val userCount      : Int,
    val activeUsers    : Int,
    val roles          : List<String>
)

data class MURoleInfo(
    val roleName    : String,
    val companyName : String,   // sanitized
    val deptName    : String,   // sanitized
    val userCount   : Int,
    val activeUsers : Int
)

data class MUUser(
    val id                : String,
    val name              : String,
    val email             : String,
    val role              : String,
    val companyName       : String,   // sanitized
    val deptName          : String,   // sanitized
    val designation       : String,
    val imageUrl          : String,
    val phoneNumber       : String,
    val experience        : Int,
    val activeProjects    : Int,
    val completedProjects : Int,
    val totalComplaints   : Int,
    val isActive          : Boolean,
    val documentPath      : String
)

// ── UI state ──────────────────────────────────────────────────────────────────

data class ManageUserUiState(
    val isLoading           : Boolean            = false,
    val currentRole         : String             = "",
    val companies           : List<MUCompany>    = emptyList(),
    val departments         : List<MUDepartment> = emptyList(),
    val roles               : List<MURoleInfo>   = emptyList(),
    val users               : List<MUUser>       = emptyList(),
    val expandedCompanies   : Set<String>        = emptySet(),
    val expandedDepartments : Set<String>        = emptySet(),
    val expandedRoles       : Set<String>        = emptySet(),
    val error               : String?            = null,
    val successMsg          : String?            = null
)