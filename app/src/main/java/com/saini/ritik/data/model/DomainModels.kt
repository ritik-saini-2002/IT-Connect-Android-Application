package com.saini.ritik.data.model

data class UserProfile(
    val id                       : String,
    val name                     : String,
    val email                    : String,
    val role                     : String,
    val companyName              : String,
    val sanitizedCompany         : String        = "",
    val department               : String        = "",
    val sanitizedDept            : String        = "",
    val designation              : String        = "",   // blank until profile completion
    val imageUrl                 : String        = "",
    val phoneNumber              : String        = "",
    val address                  : String        = "",
    val employeeId               : String        = "",
    val reportingTo              : String        = "",
    val salary                   : Double        = 0.0,
    val experience               : Int           = 0,
    val completedProjects        : Int           = 0,
    val activeProjects           : Int           = 0,
    val pendingTasks             : Int           = 0,
    val completedTasks           : Int           = 0,
    val totalComplaints          : Int           = 0,
    val resolvedComplaints       : Int           = 0,
    val pendingComplaints        : Int           = 0,
    val isActive                 : Boolean       = true,
    val documentPath             : String        = "",
    val permissions              : List<String>  = emptyList(),
    val emergencyContactName     : String        = "",
    val emergencyContactPhone    : String        = "",
    val emergencyContactRelation : String        = "",
    val needsProfileCompletion   : Boolean       = true   // true until user completes profile
) {
    val performanceScore: Double
        get() = if (completedProjects + activeProjects > 0)
            completedProjects.toDouble() / (completedProjects + activeProjects) * 100.0
        else 0.0

    val complaintResolutionRate: Double
        get() = if (totalComplaints > 0)
            resolvedComplaints.toDouble() / totalComplaints * 100.0
        else 100.0

    /** True when the profile is genuinely incomplete */
    val isProfileIncomplete: Boolean
        get() = needsProfileCompletion || designation.isBlank()
}