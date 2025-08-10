// DataCenter.kt
package com.example.ritik_2.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import java.util.Date

// ================================
// USER RELATED DATA CLASSES
// ================================

@Entity(tableName = "users")
data class User(
    @PrimaryKey val userId: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "Employee",
    @ColumnInfo(name = "company_name") val companyName: String = "",
    @ColumnInfo(name = "sanitized_company") val sanitizedCompanyName: String = "",
    val department: String = "",
    @ColumnInfo(name = "sanitized_department") val sanitizedDepartment: String = "",
    val designation: String = "",
    @ColumnInfo(name = "document_path") val documentPath: String = "",
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_by") val createdBy: String = "",
    @ColumnInfo(name = "last_login") val lastLogin: Long? = null
) {
    // Firebase requires no-argument constructor
    constructor() : this("", "", "", "Employee", "", "", "", "", "", "", true, 0L, 0L, "", null)

    // Convert to Firebase document
    fun toMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "name" to name,
        "email" to email,
        "role" to role,
        "companyName" to companyName,
        "sanitizedCompanyName" to sanitizedCompanyName,
        "department" to department,
        "sanitizedDepartment" to sanitizedDepartment,
        "designation" to designation,
        "documentPath" to documentPath,
        "isActive" to isActive,
        "createdAt" to Timestamp(Date(createdAt)),
        "lastUpdated" to Timestamp(Date(lastUpdated)),
        "createdBy" to createdBy,
        "lastLogin" to lastLogin?.let { Timestamp(Date(it)) }
    ) as Map<String, Any>

    companion object {
        fun fromMap(map: Map<String, Any>): User = User(
            userId = map["userId"] as? String ?: "",
            name = map["name"] as? String ?: "",
            email = map["email"] as? String ?: "",
            role = map["role"] as? String ?: "Employee",
            companyName = map["companyName"] as? String ?: "",
            sanitizedCompanyName = map["sanitizedCompanyName"] as? String ?: "",
            department = map["department"] as? String ?: "",
            sanitizedDepartment = map["sanitizedDepartment"] as? String ?: "",
            designation = map["designation"] as? String ?: "",
            documentPath = map["documentPath"] as? String ?: "",
            isActive = map["isActive"] as? Boolean ?: true,
            createdAt = (map["createdAt"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
            lastUpdated = (map["lastUpdated"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
            createdBy = map["createdBy"] as? String ?: "",
            lastLogin = (map["lastLogin"] as? Timestamp)?.toDate()?.time
        )
    }
}

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val userId: String = "",
    @ColumnInfo(name = "image_url") val imageUrl: String = "",
    @ColumnInfo(name = "phone_number") val phoneNumber: String = "",
    val address: String = "",
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: Long? = null,
    @ColumnInfo(name = "joining_date") val joiningDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "employee_id") val employeeId: String = "",
    @ColumnInfo(name = "reporting_to") val reportingTo: String = "",
    val salary: Double = 0.0,
    @ColumnInfo(name = "emergency_contact_name") val emergencyContactName: String = "",
    @ColumnInfo(name = "emergency_contact_phone") val emergencyContactPhone: String = "",
    @ColumnInfo(name = "emergency_contact_relation") val emergencyContactRelation: String = ""
) {
    constructor() : this("", "", "", "", null, 0L, "", "", 0.0, "", "", "")

    fun toMap(): Map<String, Any> = mapOf(
        "imageUrl" to imageUrl,
        "phoneNumber" to phoneNumber,
        "address" to address,
        "dateOfBirth" to (dateOfBirth?.let { Timestamp(Date(it)) }),
        "joiningDate" to Timestamp(Date(joiningDate)),
        "employeeId" to employeeId,
        "reportingTo" to reportingTo,
        "salary" to salary,
        "emergencyContact" to mapOf(
            "name" to emergencyContactName,
            "phone" to emergencyContactPhone,
            "relation" to emergencyContactRelation
        )
    ) as Map<String, Any>

    companion object {
        fun fromMap(userId: String, map: Map<String, Any>): UserProfile {
            val emergencyContact = map["emergencyContact"] as? Map<*, *>
            return UserProfile(
                userId = userId,
                imageUrl = map["imageUrl"] as? String ?: "",
                phoneNumber = map["phoneNumber"] as? String ?: "",
                address = map["address"] as? String ?: "",
                dateOfBirth = (map["dateOfBirth"] as? Timestamp)?.toDate()?.time,
                joiningDate = (map["joiningDate"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                employeeId = map["employeeId"] as? String ?: "",
                reportingTo = map["reportingTo"] as? String ?: "",
                salary = (map["salary"] as? Number)?.toDouble() ?: 0.0,
                emergencyContactName = emergencyContact?.get("name") as? String ?: "",
                emergencyContactPhone = emergencyContact?.get("phone") as? String ?: "",
                emergencyContactRelation = emergencyContact?.get("relation") as? String ?: ""
            )
        }
    }
}

@Entity(tableName = "work_stats")
data class WorkStats(
    @PrimaryKey val userId: String = "",
    val experience: Int = 0,
    @ColumnInfo(name = "completed_projects") val completedProjects: Int = 0,
    @ColumnInfo(name = "active_projects") val activeProjects: Int = 0,
    @ColumnInfo(name = "pending_tasks") val pendingTasks: Int = 0,
    @ColumnInfo(name = "completed_tasks") val completedTasks: Int = 0,
    @ColumnInfo(name = "total_working_hours") val totalWorkingHours: Int = 0,
    @ColumnInfo(name = "avg_performance_rating") val avgPerformanceRating: Double = 0.0
) {
    constructor() : this("", 0, 0, 0, 0, 0, 0, 0.0)

    fun toMap(): Map<String, Any> = mapOf(
        "experience" to experience,
        "completedProjects" to completedProjects,
        "activeProjects" to activeProjects,
        "pendingTasks" to pendingTasks,
        "completedTasks" to completedTasks,
        "totalWorkingHours" to totalWorkingHours,
        "avgPerformanceRating" to avgPerformanceRating
    )

    companion object {
        fun fromMap(userId: String, map: Map<String, Any>): WorkStats = WorkStats(
            userId = userId,
            experience = (map["experience"] as? Number)?.toInt() ?: 0,
            completedProjects = (map["completedProjects"] as? Number)?.toInt() ?: 0,
            activeProjects = (map["activeProjects"] as? Number)?.toInt() ?: 0,
            pendingTasks = (map["pendingTasks"] as? Number)?.toInt() ?: 0,
            completedTasks = (map["completedTasks"] as? Number)?.toInt() ?: 0,
            totalWorkingHours = (map["totalWorkingHours"] as? Number)?.toInt() ?: 0,
            avgPerformanceRating = (map["avgPerformanceRating"] as? Number)?.toDouble() ?: 0.0
        )
    }
}

// ================================
// COMPLAINT RELATED DATA CLASSES
// ================================

@Entity(tableName = "complaints")
data class Complaint(
    @PrimaryKey val complaintId: String = "",
    val title: String = "",
    val description: String = "",
    val department: String = "",
    @ColumnInfo(name = "original_category") val originalCategory: String = "",
    val urgency: String = "",
    val status: String = "Open",
    @ColumnInfo(name = "company_name") val companyName: String = "",
    @ColumnInfo(name = "sanitized_company_name") val sanitizedCompanyName: String = "",
    @ColumnInfo(name = "user_department") val userDepartment: String = "",
    @ColumnInfo(name = "sanitized_user_department") val sanitizedUserDepartment: String = "",
    @ColumnInfo(name = "created_by_user_id") val createdByUserId: String = "",
    @ColumnInfo(name = "created_by_name") val createdByName: String = "",
    @ColumnInfo(name = "created_by_email") val createdByEmail: String = "",
    @ColumnInfo(name = "contact_info") val contactInfo: String = "",
    @ColumnInfo(name = "is_global") val isGlobal: Boolean = false,
    @ColumnInfo(name = "has_attachment") val hasAttachment: Boolean = false,
    @ColumnInfo(name = "attachment_url") val attachmentUrl: String = "",
    @ColumnInfo(name = "attachment_file_name") val attachmentFileName: String = "",
    @ColumnInfo(name = "attachment_file_size") val attachmentFileSize: Long = 0L,
    @ColumnInfo(name = "document_path") val documentPath: String = "",
    val priority: Int = 1,
    @ColumnInfo(name = "estimated_resolution_time") val estimatedResolutionTime: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "", "", "", "Open", "", "", "", "", "", "", "", "", false, false, "", "", 0L, "", 1, "", 0L, 0L)

    fun toMap(): Map<String, Any> = mapOf(
        "complaintId" to complaintId,
        "title" to title,
        "description" to description,
        "department" to department,
        "originalCategory" to originalCategory,
        "urgency" to urgency,
        "status" to mapOf(
            "current" to status,
            "history" to listOf<Map<String, Any>>()
        ),
        "companyName" to companyName,
        "sanitizedCompanyName" to sanitizedCompanyName,
        "userDepartment" to userDepartment,
        "sanitizedUserDepartment" to sanitizedUserDepartment,
        "isGlobal" to isGlobal,
        "documentPath" to documentPath,
        "createdBy" to mapOf(
            "userId" to createdByUserId,
            "name" to createdByName,
            "email" to createdByEmail,
            "contactInfo" to contactInfo
        ),
        "attachment" to mapOf(
            "hasFile" to hasAttachment,
            "url" to attachmentUrl,
            "fileName" to attachmentFileName,
            "fileSize" to attachmentFileSize
        ),
        "priority" to priority,
        "estimatedResolutionTime" to estimatedResolutionTime,
        "createdAt" to Timestamp(Date(createdAt)),
        "updatedAt" to Timestamp(Date(updatedAt))
    )

    companion object {
        fun fromMap(map: Map<String, Any>): Complaint {
            val createdBy = map["createdBy"] as? Map<*, *>
            val attachment = map["attachment"] as? Map<*, *>
            val statusMap = map["status"] as? Map<*, *>

            return Complaint(
                complaintId = map["complaintId"] as? String ?: "",
                title = map["title"] as? String ?: "",
                description = map["description"] as? String ?: "",
                department = map["department"] as? String ?: "",
                originalCategory = map["originalCategory"] as? String ?: "",
                urgency = map["urgency"] as? String ?: "",
                status = statusMap?.get("current") as? String ?: "Open",
                companyName = map["companyName"] as? String ?: "",
                sanitizedCompanyName = map["sanitizedCompanyName"] as? String ?: "",
                userDepartment = map["userDepartment"] as? String ?: "",
                sanitizedUserDepartment = map["sanitizedUserDepartment"] as? String ?: "",
                createdByUserId = createdBy?.get("userId") as? String ?: "",
                createdByName = createdBy?.get("name") as? String ?: "",
                createdByEmail = createdBy?.get("email") as? String ?: "",
                contactInfo = createdBy?.get("contactInfo") as? String ?: "",
                isGlobal = map["isGlobal"] as? Boolean ?: false,
                hasAttachment = attachment?.get("hasFile") as? Boolean ?: false,
                attachmentUrl = attachment?.get("url") as? String ?: "",
                attachmentFileName = attachment?.get("fileName") as? String ?: "",
                attachmentFileSize = (attachment?.get("fileSize") as? Number)?.toLong() ?: 0L,
                documentPath = map["documentPath"] as? String ?: "",
                priority = (map["priority"] as? Number)?.toInt() ?: 1,
                estimatedResolutionTime = map["estimatedResolutionTime"] as? String ?: "",
                createdAt = (map["createdAt"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
            )
        }
    }
}

// ================================
// ORGANIZATION STRUCTURE DATA CLASSES
// ================================

@Entity(tableName = "companies")
data class Company(
    @PrimaryKey val sanitizedName: String = "",
    @ColumnInfo(name = "original_name") val originalName: String = "",
    @ColumnInfo(name = "total_users") val totalUsers: Int = 0,
    @ColumnInfo(name = "active_users") val activeUsers: Int = 0,
    @ColumnInfo(name = "total_complaints") val totalComplaints: Int = 0,
    @ColumnInfo(name = "open_complaints") val openComplaints: Int = 0,
    @ColumnInfo(name = "available_roles") val availableRoles: String = "", // JSON string
    @ColumnInfo(name = "departments") val departments: String = "", // JSON string
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_complaint_date") val lastComplaintDate: Long? = null
) {
    constructor() : this("", "", 0, 0, 0, 0, "", "", 0L, 0L, null)

    fun toMap(): Map<String, Any> = mapOf(
        "originalName" to originalName,
        "sanitizedName" to sanitizedName,
        "totalUsers" to totalUsers,
        "activeUsers" to activeUsers,
        "totalComplaints" to totalComplaints,
        "openComplaints" to openComplaints,
        "availableRoles" to availableRoles.split(",").filter { it.isNotEmpty() },
        "departments" to departments.split(",").filter { it.isNotEmpty() },
        "createdAt" to Timestamp(Date(createdAt)),
        "lastUpdated" to Timestamp(Date(lastUpdated)),
        "lastComplaintDate" to lastComplaintDate?.let { Timestamp(Date(it)) }
    ) as Map<String, Any>

    companion object {
        fun fromMap(map: Map<String, Any>): Company {
            val roles = (map["availableRoles"] as? List<*>)?.mapNotNull { it as? String }?.joinToString(",") ?: ""
            val depts = (map["departments"] as? List<*>)?.mapNotNull { it as? String }?.joinToString(",") ?: ""

            return Company(
                sanitizedName = map["sanitizedName"] as? String ?: "",
                originalName = map["originalName"] as? String ?: "",
                totalUsers = (map["totalUsers"] as? Number)?.toInt() ?: 0,
                activeUsers = (map["activeUsers"] as? Number)?.toInt() ?: 0,
                totalComplaints = (map["totalComplaints"] as? Number)?.toInt() ?: 0,
                openComplaints = (map["openComplaints"] as? Number)?.toInt() ?: 0,
                availableRoles = roles,
                departments = depts,
                createdAt = (map["createdAt"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                lastUpdated = (map["lastUpdated"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                lastComplaintDate = (map["lastComplaintDate"] as? Timestamp)?.toDate()?.time
            )
        }
    }
}

@Entity(tableName = "departments")
data class Department(
    @PrimaryKey val id: String = "", // combination of companyId_departmentName
    @ColumnInfo(name = "department_name") val departmentName: String = "",
    @ColumnInfo(name = "company_name") val companyName: String = "",
    @ColumnInfo(name = "sanitized_company_name") val sanitizedCompanyName: String = "",
    @ColumnInfo(name = "sanitized_name") val sanitizedName: String = "",
    @ColumnInfo(name = "user_count") val userCount: Int = 0,
    @ColumnInfo(name = "active_users") val activeUsers: Int = 0,
    @ColumnInfo(name = "total_complaints") val totalComplaints: Int = 0,
    @ColumnInfo(name = "open_complaints") val openComplaints: Int = 0,
    @ColumnInfo(name = "available_roles") val availableRoles: String = "", // JSON string
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_complaint_date") val lastComplaintDate: Long? = null
) {
    constructor() : this("", "", "", "", "", 0, 0, 0, 0, "", 0L, 0L, null)

    fun toMap(): Map<String, Any> = mapOf(
        "departmentName" to departmentName,
        "companyName" to companyName,
        "sanitizedCompanyName" to sanitizedCompanyName,
        "sanitizedName" to sanitizedName,
        "userCount" to userCount,
        "activeUsers" to activeUsers,
        "totalComplaints" to totalComplaints,
        "openComplaints" to openComplaints,
        "availableRoles" to availableRoles.split(",").filter { it.isNotEmpty() },
        "createdAt" to Timestamp(Date(createdAt)),
        "lastUpdated" to Timestamp(Date(lastUpdated)),
        "lastComplaintDate" to lastComplaintDate?.let { Timestamp(Date(it)) }
    ) as Map<String, Any>

    companion object {
        fun fromMap(companyName: String, map: Map<String, Any>): Department {
            val roles = (map["availableRoles"] as? List<*>)?.mapNotNull { it as? String }?.joinToString(",") ?: ""
            val deptName = map["departmentName"] as? String ?: ""

            return Department(
                id = "${companyName}_${deptName}",
                departmentName = deptName,
                companyName = map["companyName"] as? String ?: "",
                sanitizedCompanyName = map["sanitizedCompanyName"] as? String ?: "",
                sanitizedName = map["sanitizedName"] as? String ?: "",
                userCount = (map["userCount"] as? Number)?.toInt() ?: 0,
                activeUsers = (map["activeUsers"] as? Number)?.toInt() ?: 0,
                totalComplaints = (map["totalComplaints"] as? Number)?.toInt() ?: 0,
                openComplaints = (map["openComplaints"] as? Number)?.toInt() ?: 0,
                availableRoles = roles,
                createdAt = (map["createdAt"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                lastUpdated = (map["lastUpdated"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                lastComplaintDate = (map["lastComplaintDate"] as? Timestamp)?.toDate()?.time
            )
        }
    }
}

// ================================
// SIMPLE DATA CLASSES FOR UI
// ================================

data class ComplaintData(
    val title: String,
    val description: String,
    val department: String,
    val urgency: String,
    val contactInfo: String = "",
    val hasAttachment: Boolean = false,
    val isGlobal: Boolean = false
)

data class ComplaintWithId(
    val id: String,
    val title: String,
    val description: String,
    val department: String,
    val urgency: String,
    val status: String,
    val timestamp: Long,
    val contactInfo: String,
    val hasAttachment: Boolean
)

data class DepartmentInfo(
    val departmentId: String,
    val departmentName: String,
    val companyName: String,
    val sanitizedName: String,
    val userCount: Int,
    val availableRoles: List<String>
)

data class UserData(
    val userId: String,
    val name: String,
    val email: String,
    val companyName: String,
    val sanitizedCompanyName: String,
    val department: String,
    val sanitizedDepartment: String,
    val role: String,
    val documentPath: String,
    val phoneNumber: String,
    val designation: String
)

// ================================
// UTILITY CLASSES
// ================================

data class UserPermissions(
    val canCreateUser: Boolean = false,
    val canDeleteUser: Boolean = false,
    val canModifyUser: Boolean = false,
    val canViewAllUsers: Boolean = false,
    val canManageRoles: Boolean = false,
    val canViewAnalytics: Boolean = false,
    val canAccessSystemSettings: Boolean = false,
    val canManageCompanies: Boolean = false,
    val canAccessAllData: Boolean = false,
    val canExportData: Boolean = false,
    val canManagePermissions: Boolean = false
) {
    companion object {
        fun fromRole(role: String): UserPermissions {
            return when (role) {
                "Administrator" -> UserPermissions(
                    canCreateUser = true,
                    canDeleteUser = true,
                    canModifyUser = true,
                    canViewAllUsers = true,
                    canManageRoles = true,
                    canViewAnalytics = true,
                    canAccessSystemSettings = true,
                    canManageCompanies = true,
                    canAccessAllData = true,
                    canExportData = true,
                    canManagePermissions = true
                )
                "Manager" -> UserPermissions(
                    canModifyUser = true,
                    canViewAllUsers = true,
                    canViewAnalytics = true
                )
                "HR" -> UserPermissions(
                    canViewAllUsers = true,
                    canModifyUser = true,
                    canViewAnalytics = true
                )
                else -> UserPermissions()
            }
        }

        fun fromPermissionList(permissions: List<String>): UserPermissions {
            return UserPermissions(
                canCreateUser = permissions.contains("create_user"),
                canDeleteUser = permissions.contains("delete_user"),
                canModifyUser = permissions.contains("modify_user"),
                canViewAllUsers = permissions.contains("view_all_users"),
                canManageRoles = permissions.contains("manage_roles"),
                canViewAnalytics = permissions.contains("view_analytics"),
                canAccessSystemSettings = permissions.contains("system_settings"),
                canManageCompanies = permissions.contains("manage_companies"),
                canAccessAllData = permissions.contains("access_all_data"),
                canExportData = permissions.contains("export_data"),
                canManagePermissions = permissions.contains("manage_permissions")
            )
        }
    }
}

// ================================
// FIRESTORE PATH BUILDERS
// ================================

object FirestorePaths {
    // User paths: users/{company}/{department}/{role}/users/{userId}
    fun getUserPath(sanitizedCompany: String, sanitizedDepartment: String, role: String, userId: String): String {
        return "users/$sanitizedCompany/$sanitizedDepartment/$role/users/$userId"
    }

    // Complaint paths
    fun getComplaintPath(sanitizedCompany: String, isGlobal: Boolean, complaintId: String): String {
        return if (isGlobal) {
            "complaints/$sanitizedCompany/global_complaints/$complaintId"
        } else {
            "complaints/$sanitizedCompany/department_complaints/$complaintId"
        }
    }

    // Company metadata path
    fun getCompanyPath(sanitizedCompany: String): String {
        return "companies_metadata/$sanitizedCompany"
    }

    // Department metadata path
    fun getDepartmentPath(sanitizedCompany: String, sanitizedDepartment: String): String {
        return "companies_metadata/$sanitizedCompany/departments_metadata/$sanitizedDepartment"
    }

    // Role metadata path
    fun getRolePath(sanitizedCompany: String, sanitizedDepartment: String, role: String): String {
        return "companies_metadata/$sanitizedCompany/departments_metadata/$sanitizedDepartment/roles_metadata/$role"
    }

    // Access control collections
    const val USER_ACCESS_CONTROL = "user_access_control"
    const val USER_SEARCH_INDEX = "user_search_index"
    const val COMPLAINT_SEARCH_INDEX = "complaint_search_index"
    const val ALL_COMPLAINTS = "all_complaints"
}

// ================================
// UTILITY FUNCTIONS
// ================================

object DataUtils {
    fun sanitizeDocumentId(input: String): String {
        return input
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(100)
    }

    fun getRolePermissions(role: String): List<String> {
        return when (role) {
            "Administrator" -> listOf(
                "create_user", "delete_user", "modify_user", "view_all_users",
                "manage_roles", "view_analytics", "system_settings", "manage_companies",
                "access_all_data", "export_data", "manage_permissions"
            )
            "Manager" -> listOf(
                "view_team_users", "modify_team_user", "view_team_analytics",
                "assign_projects", "approve_requests", "view_reports"
            )
            "HR" -> listOf(
                "view_all_users", "modify_user", "view_hr_analytics", "manage_employees",
                "access_personal_data", "generate_reports"
            )
            "Team Lead" -> listOf(
                "view_team_users", "assign_tasks", "view_team_performance", "approve_leave"
            )
            "Employee" -> listOf(
                "view_profile", "edit_profile", "view_assigned_projects", "submit_reports"
            )
            "Intern" -> listOf(
                "view_profile", "edit_basic_profile", "view_assigned_tasks"
            )
            else -> listOf("view_profile", "edit_basic_profile")
        }
    }

    fun calculateComplaintPriority(urgency: String, department: String): Int {
        val urgencyScore = when (urgency) {
            "Critical" -> 4
            "High" -> 3
            "Medium" -> 2
            "Low" -> 1
            else -> 1
        }

        val departmentScore = when (department) {
            "Technical", "IT Support" -> 1
            "Administrative", "Finance" -> 0
            else -> 0
        }

        return urgencyScore + departmentScore
    }

    fun getEstimatedResolutionTime(urgency: String): String {
        return when (urgency) {
            "Critical" -> "4 hours"
            "High" -> "24 hours"
            "Medium" -> "3 days"
            "Low" -> "1 week"
            else -> "1 week"
        }
    }
}