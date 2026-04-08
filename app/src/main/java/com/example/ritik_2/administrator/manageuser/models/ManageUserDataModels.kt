package com.example.ritik_2.administrator.manageuser.models

data class MUCompany(
    val sanitizedName : String,
    val originalName  : String,
    val totalUsers    : Int,
    val activeUsers   : Int
)

data class MUDepartment(
    val sanitizedName  : String,
    val departmentName : String,
    val companyName    : String,
    val userCount      : Int,
    val activeUsers    : Int,
    val roles          : List<String>
)

data class MURoleInfo(
    val roleName    : String,
    val companyName : String,
    val deptName    : String,
    val userCount   : Int,
    val activeUsers : Int
)

data class MUUser(
    val id                : String,
    val name              : String,
    val email             : String,
    val role              : String,
    val companyName       : String,
    val deptName          : String,
    val designation       : String,
    val imageUrl          : String,
    val phoneNumber       : String,
    val experience        : Int,
    val activeProjects    : Int,
    val completedProjects : Int,
    val totalComplaints   : Int,
    val isActive          : Boolean,
    val documentPath      : String,
    val originalCompany   : String = "",
    val originalDept      : String = ""
)

data class ManageUserUiState(
    val isLoading           : Boolean         = false,
    val isDbAdmin           : Boolean         = false,   // ← NEW
    val currentRole         : String          = "",
    val companies           : List<MUCompany> = emptyList(),
    val users               : List<MUUser>    = emptyList(),
    val filteredUsers       : List<MUUser>    = emptyList(),
    val searchQuery         : String          = "",
    val expandedCompanies   : Set<String>     = emptySet(),
    val expandedDepartments : Set<String>     = emptySet(),
    val expandedRoles       : Set<String>     = emptySet(),
    val error               : String?         = null,
    val successMsg          : String?         = null
)