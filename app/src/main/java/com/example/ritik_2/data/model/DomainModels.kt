// ─── data/model/DomainModels.kt ───────────────────────────────
package com.example.ritik_2.data.model

/** Domain model used across all UI layers */
data class UserProfile(
    val id                : String,
    val name              : String,
    val email             : String,
    val role              : String,
    val companyName       : String,
    val sanitizedCompany  : String        = "",
    val department        : String        = "",
    val sanitizedDept     : String        = "",
    val designation       : String        = "IT Professional",
    val imageUrl          : String        = "",
    val phoneNumber       : String        = "",
    val address           : String        = "",
    val employeeId        : String        = "",
    val reportingTo       : String        = "",
    val salary            : Double        = 0.0,
    val experience        : Int           = 0,
    val completedProjects : Int           = 0,
    val activeProjects    : Int           = 0,
    val pendingTasks      : Int           = 0,
    val completedTasks    : Int           = 0,
    val totalComplaints   : Int           = 0,
    val resolvedComplaints: Int           = 0,
    val pendingComplaints : Int           = 0,
    val isActive          : Boolean       = true,
    val documentPath      : String        = "",
    val permissions       : List<String>  = emptyList(),
    val emergencyContactName    : String  = "",
    val emergencyContactPhone   : String  = "",
    val emergencyContactRelation: String  = ""
) {
    val performanceScore: Double
        get() = if (completedProjects + activeProjects > 0)
            completedProjects.toDouble() / (completedProjects + activeProjects) * 100 else 0.0

    val complaintResolutionRate: Double
        get() = if (totalComplaints > 0)
            resolvedComplaints.toDouble() / totalComplaints * 100 else 100.0
}

data class Company(
    val id            : String = "",
    val originalName  : String,
    val sanitizedName : String,
    val totalUsers    : Int           = 1,
    val activeUsers   : Int           = 1,
    val availableRoles: List<String>  = emptyList(),
    val departments   : List<String>  = emptyList()
)


// ─── data/model/AuthModels.kt ─────────────────────────────────
