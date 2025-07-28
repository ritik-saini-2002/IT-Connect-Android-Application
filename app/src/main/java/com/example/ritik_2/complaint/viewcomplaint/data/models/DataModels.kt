package com.example.ritik_2.complaint.viewcomplaint.data.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Enums
enum class SortOption {
    DATE_DESC,
    DATE_ASC,
    URGENCY,
    STATUS
}

enum class ViewMode {
    PERSONAL,           // User's own complaints
    DEPARTMENT,         // Department complaints (Manager/Team Leader)
    ASSIGNED_TO_ME,     // Complaints assigned to current user
    ALL_COMPANY,        // All company complaints (Admin only)
    GLOBAL              // Global complaints visible to all
}

// Data Classes
data class ComplaintWithDetails(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val urgency: String,
    val status: String,
    val priority: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val documentPath: String,
    val isGlobal: Boolean,
    val createdBy: UserInfo,
    val assignedToUser: UserInfo?,
    val assignedToDepartment: DepartmentInfo?,
    val hasAttachment: Boolean,
    val attachmentUrl: String?,
    val resolution: String?,
    val estimatedResolutionTime: String,
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null
)

data class UserInfo(
    val userId: String,
    val name: String,
    val email: String,
    val department: String
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

data class DepartmentInfo(
    val departmentId: String,
    val departmentName: String,
    val companyName: String,
    val sanitizedName: String,
    val userCount: Int,
    val availableRoles: List<String>
)

data class UserPermissions(
    val canViewAllComplaints: Boolean = false,
    val canViewDepartmentComplaints: Boolean = false,
    val canAssignComplaints: Boolean = false,
    val canReopenComplaints: Boolean = false,
    val canCloseComplaints: Boolean = false,
    val canDeleteComplaints: Boolean = false,
    val canEditComplaints: Boolean = false,
    val canViewStatistics: Boolean = false,
    val canManageUsers: Boolean = false
)

data class ComplaintUpdates(
    val title: String,
    val description: String,
    val category: String,
    val urgency: String
)

data class ComplaintStats(
    val totalComplaints: Int,
    val openComplaints: Int,
    val closedComplaints: Int,
    val inProgressComplaints: Int,
    val averageResolutionTime: String
)

data class StatusHistoryItem(
    val status: String,
    val changedAt: Long,
    val changedBy: String,
    val reason: String
)

data class AssignmentInfo(
    val assignedToUserId: String,
    val assignedToUserName: String,
    val assignedAt: Long,
    val assignedBy: String
)

data class AttachmentInfo(
    val hasFile: Boolean,
    val url: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val uploadedAt: Long? = null
)

data class ComplaintFilter(
    val status: String? = null,
    val urgency: String? = null,
    val department: String? = null,
    val assignedToMe: Boolean = false,
    val createdByMe: Boolean = false,
    val dateRange: DateRange? = null
)

data class DateRange(
    val startDate: Long,
    val endDate: Long
)

data class ComplaintSearchResult(
    val complaints: List<ComplaintWithDetails>,
    val totalCount: Int,
    val hasMore: Boolean
)

data class NotificationData(
    val title: String,
    val message: String,
    val type: String,
    val complaintId: String? = null,
    val priority: String = "normal"
)

// Extension functions
fun ComplaintWithDetails.getFormattedDate(): String {
    val date = Date(this.createdAt)
    val format = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
    return format.format(date)
}

fun ComplaintWithDetails.getFormattedTime(): String {
    val date = Date(this.createdAt)
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(date)
}

fun ComplaintWithDetails.getFormattedDateTime(): String {
    val date = Date(this.createdAt)
    val format = SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault())
    return format.format(date)
}

fun ComplaintWithDetails.getFormattedUpdatedDate(): String {
    val date = Date(this.updatedAt)
    val format = SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault())
    return format.format(date)
}

fun ComplaintWithDetails.getFormattedResolvedDate(): String? {
    return resolvedAt?.let { timestamp ->
        val date = Date(timestamp)
        val format = SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault())
        format.format(date)
    }
}

fun ComplaintWithDetails.getTimeSinceCreation(): String {
    val now = System.currentTimeMillis()
    val diff = now - createdAt

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> getFormattedDate()
    }
}

fun ComplaintWithDetails.getTimeSinceUpdate(): String {
    val now = System.currentTimeMillis()
    val diff = now - updatedAt

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> getFormattedUpdatedDate()
    }
}

fun ComplaintWithDetails.getResolutionTime(): String? {
    return if (resolvedAt != null && status.lowercase() == "closed") {
        val resolutionTime = resolvedAt - createdAt
        when {
            resolutionTime < 3600_000 -> "${resolutionTime / 60_000}m"
            resolutionTime < 86400_000 -> "${resolutionTime / 3600_000}h"
            else -> "${resolutionTime / 86400_000}d"
        }
    } else null
}

fun ComplaintWithDetails.isOverdue(): Boolean {
    if (status.lowercase() in listOf("closed", "cancelled")) {
        return false
    }

    val now = System.currentTimeMillis()
    val creationTime = createdAt

    // Define overdue thresholds based on urgency
    val overdueThreshold = when (urgency.lowercase()) {
        "critical" -> 4 * 3600_000L // 4 hours
        "high" -> 24 * 3600_000L // 24 hours
        "medium" -> 3 * 24 * 3600_000L // 3 days
        "low" -> 7 * 24 * 3600_000L // 1 week
        else -> 7 * 24 * 3600_000L
    }

    return (now - creationTime) > overdueThreshold
}

fun ComplaintWithDetails.getUrgencyColor(): String {
    return when (urgency.lowercase()) {
        "critical" -> "#FF0000" // Red
        "high" -> "#FF6600" // Orange
        "medium" -> "#FFB300" // Amber
        "low" -> "#4CAF50" // Green
        else -> "#9E9E9E" // Gray
    }
}

fun ComplaintWithDetails.getStatusColor(): String {
    return when (status.lowercase()) {
        "open" -> "#2196F3" // Blue
        "in progress", "assigned" -> "#FF9800" // Orange
        "closed", "resolved" -> "#4CAF50" // Green
        "cancelled" -> "#9E9E9E" // Gray
        "reopened" -> "#E91E63" // Pink
        else -> "#9E9E9E" // Gray
    }
}

fun ComplaintWithDetails.canBeAssigned(userPermissions: UserPermissions?): Boolean {
    return userPermissions?.canAssignComplaints == true &&
            status.lowercase() in listOf("open", "reopened")
}

fun ComplaintWithDetails.canBeClosed(userPermissions: UserPermissions?): Boolean {
    return userPermissions?.canCloseComplaints == true &&
            status.lowercase() !in listOf("closed", "cancelled")
}

fun ComplaintWithDetails.canBeReopened(userPermissions: UserPermissions?): Boolean {
    return userPermissions?.canReopenComplaints == true &&
            status.lowercase() == "closed"
}

fun ComplaintWithDetails.canBeEdited(userPermissions: UserPermissions?, currentUserId: String): Boolean {
    return userPermissions?.canEditComplaints == true ||
            (createdBy.userId == currentUserId && status.lowercase() == "open")
}

fun ComplaintWithDetails.canBeDeleted(userPermissions: UserPermissions?, currentUserId: String): Boolean {
    return userPermissions?.canDeleteComplaints == true ||
            (createdBy.userId == currentUserId && status.lowercase() == "open")
}

fun ComplaintWithDetails.getVisibilityText(): String {
    return if (isGlobal) {
        "Visible to all company members"
    } else {
        assignedToDepartment?.departmentName?.let {
            "Visible to $it department"
        } ?: "Department specific"
    }
}

fun ComplaintWithDetails.getDepartmentText(): String {
    return assignedToDepartment?.departmentName ?: category
}

fun ComplaintWithDetails.getAssigneeText(): String {
    return assignedToUser?.name ?: "Unassigned"
}

fun ComplaintWithDetails.getCreatorText(): String {
    val creatorName = createdBy.name.ifBlank { "Unknown User" }
    val department = createdBy.department.ifBlank { "Unknown Department" }
    return "$creatorName ($department)"
}

fun ComplaintWithDetails.isAssignedToUser(userId: String): Boolean {
    return assignedToUser?.userId == userId
}

fun ComplaintWithDetails.isCreatedByUser(userId: String): Boolean {
    return createdBy.userId == userId
}

fun ComplaintWithDetails.matchesSearchQuery(query: String): Boolean {
    if (query.isBlank()) return true

    val searchQuery = query.lowercase()
    return title.lowercase().contains(searchQuery) ||
            description.lowercase().contains(searchQuery) ||
            category.lowercase().contains(searchQuery) ||
            urgency.lowercase().contains(searchQuery) ||
            status.lowercase().contains(searchQuery) ||
            createdBy.name.lowercase().contains(searchQuery) ||
            (assignedToUser?.name?.lowercase()?.contains(searchQuery) == true) ||
            (assignedToDepartment?.departmentName?.lowercase()?.contains(searchQuery) == true)
}

// Utility functions for permissions
fun UserPermissions.hasAnyEditPermission(): Boolean {
    return canEditComplaints || canAssignComplaints || canCloseComplaints || canReopenComplaints
}

fun UserPermissions.hasAnyViewPermission(): Boolean {
    return canViewAllComplaints || canViewDepartmentComplaints
}

fun UserPermissions.hasManagementPermissions(): Boolean {
    return canAssignComplaints || canCloseComplaints || canDeleteComplaints || canManageUsers
}



// Sorting utilities
//fun List<ComplaintWithDetails>.sortBy(sortOption: SortOption): List<ComplaintWithDetails> {
//    return when (sortOption) {
//        SortOption.DATE_DESC -> this.sortedByDescending { it.createdAt }
//        SortOption.DATE_ASC -> this.sortedBy { it.createdAt }
//        SortOption.URGENCY -> this.sortedByDescending {
//            getUrgencyPriority(it.urgency)
//        }.thenByDescending { it.createdAt }
//        SortOption.STATUS -> this.sortedBy { it.status }
//            .thenByDescending { it.createdAt }
//    }
//}

private fun getUrgencyPriority(urgency: String): Int {
    return when (urgency.lowercase()) {
        "critical" -> 4
        "high" -> 3
        "medium" -> 2
        "low" -> 1
        else -> 1
    }
}

// Filter utilities
fun List<ComplaintWithDetails>.filterBy(filter: ComplaintFilter): List<ComplaintWithDetails> {
    return this.filter { complaint ->
        // Status filter
        if (filter.status != null && !complaint.status.equals(filter.status, ignoreCase = true)) {
            return@filter false
        }

        // Urgency filter
        if (filter.urgency != null && !complaint.urgency.equals(filter.urgency, ignoreCase = true)) {
            return@filter false
        }

        // Department filter
        if (filter.department != null && !complaint.category.equals(filter.department, ignoreCase = true)) {
            return@filter false
        }

        // Date range filter
        if (filter.dateRange != null) {
            if (complaint.createdAt < filter.dateRange.startDate ||
                complaint.createdAt > filter.dateRange.endDate) {
                return@filter false
            }
        }

        true
    }
}

// Statistics utilities
fun List<ComplaintWithDetails>.getComplaintStatistics(): ComplaintStats {
    val total = this.size
    val open = this.count { it.status.lowercase() == "open" }
    val closed = this.count { it.status.lowercase() == "closed" }
    val inProgress = this.count { it.status.lowercase() in listOf("in progress", "assigned") }

    // Calculate average resolution time for closed complaints
    val closedComplaints = this.filter {
        it.status.lowercase() == "closed" && it.resolvedAt != null
    }

    val averageResolutionTime = if (closedComplaints.isNotEmpty()) {
        val totalResolutionTime = closedComplaints.sumOf {
            (it.resolvedAt!! - it.createdAt)
        }
        val averageMs = totalResolutionTime / closedComplaints.size

        when {
            averageMs < 3600_000 -> "${averageMs / 60_000}m"
            averageMs < 86400_000 -> "${averageMs / 3600_000}h"
            else -> "${averageMs / 86400_000}d"
        }
    } else {
        "N/A"
    }

    return ComplaintStats(
        totalComplaints = total,
        openComplaints = open,
        closedComplaints = closed,
        inProgressComplaints = inProgress,
        averageResolutionTime = averageResolutionTime
    )
}