package com.example.ritik_2.registration.models

import io.github.agrevster.pocketbaseKotlin.models.Record
import kotlinx.serialization.Serializable

// ── Main user auth record (maps to "users" auth collection) ───
@Serializable
data class UserRecord(
    val userId: String               = "",
    val name: String                 = "",
    val role: String                 = "",
    val companyName: String          = "",
    val sanitizedCompanyName: String = "",
    val department: String           = "",
    val sanitizedDepartment: String  = "",
    val designation: String          = "",
    val isActive: Boolean            = true,
    val documentPath: String         = "",
    val permissions: String          = "[]",  // JSON string
    val profile: String              = "{}",  // JSON string
    val workStats: String            = "{}",  // JSON string
    val issues: String               = "{}"   // JSON string
) : Record()

// ── Company metadata record ────────────────────────────────────
@Serializable
data class CompanyRecord(
    val originalName: String   = "",
    val sanitizedName: String  = "",
    val totalUsers: Int        = 0,
    val activeUsers: Int       = 0,
    val availableRoles: String = "[]",  // JSON string
    val departments: String    = "[]"   // JSON string
) : Record()

// ── User access control record ────────────────────────────────
@Serializable
data class AccessControlRecord(
    val userId: String               = "",
    val name: String                 = "",
    val email: String                = "",
    val companyName: String          = "",
    val sanitizedCompanyName: String = "",
    val department: String           = "",
    val sanitizedDepartment: String  = "",
    val role: String                 = "",
    val permissions: String          = "[]",  // JSON string
    val isActive: Boolean            = true,
    val documentPath: String         = ""
) : Record()

// ── User search index record ──────────────────────────────────
@Serializable
data class SearchIndexRecord(
    val userId: String               = "",
    val name: String                 = "",
    val email: String                = "",
    val companyName: String          = "",
    val sanitizedCompanyName: String = "",
    val department: String           = "",
    val sanitizedDepartment: String  = "",
    val role: String                 = "",
    val designation: String          = "",
    val isActive: Boolean            = true,
    val searchTerms: String          = "[]",  // JSON string
    val documentPath: String         = ""
) : Record()

// ─────────────────────────────────────────────────────────────
// Non-Record data classes — used only for JSON encoding
// ─────────────────────────────────────────────────────────────

@Serializable
data class ProfileData(
    val imageUrl: String    = "",
    val phoneNumber: String = "",
    val address: String     = "",
    val employeeId: String  = "",
    val reportingTo: String = "",
    val salary: Double      = 0.0,
    val emergencyContactName: String     = "",
    val emergencyContactPhone: String    = "",
    val emergencyContactRelation: String = ""
)

@Serializable
data class WorkStats(
    val experience: Int              = 0,
    val completedProjects: Int       = 0,
    val activeProjects: Int          = 0,
    val pendingTasks: Int            = 0,
    val completedTasks: Int          = 0,
    val totalWorkingHours: Int       = 0,
    val avgPerformanceRating: Double = 0.0
)

@Serializable
data class IssuesData(
    val totalComplaints: Int    = 0,
    val resolvedComplaints: Int = 0,
    val pendingComplaints: Int  = 0
)