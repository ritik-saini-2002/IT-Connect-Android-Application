package com.example.ritik_2.complaint.viewcomplaint.utils

import android.util.Log
import com.example.ritik_2.complaint.viewcomplaint.data.models.UserPermissions
import com.example.ritik_2.complaint.viewcomplaint.data.models.ViewMode

class PermissionChecker {

    companion object {
        private const val TAG = "PermissionChecker"
    }

    fun getPermissionsForRole(role: String): UserPermissions {
        val userRole = role.lowercase().trim()
        Log.d(TAG, "Setting permissions for role: '$userRole'")

        return when {
            isAdministrator(userRole) -> {
                Log.d(TAG, "Setting ADMINISTRATOR permissions")
                UserPermissions(
                    canViewAllComplaints = true,
                    canViewDepartmentComplaints = true,
                    canAssignComplaints = true,
                    canReopenComplaints = true,
                    canCloseComplaints = true,
                    canDeleteComplaints = true,
                    canEditComplaints = true,
                    canViewStatistics = true,
                    canManageUsers = true
                )
            }
            isManager(userRole) -> {
                Log.d(TAG, "Setting MANAGER permissions")
                UserPermissions(
                    canViewAllComplaints = false,
                    canViewDepartmentComplaints = true,
                    canAssignComplaints = true,
                    canReopenComplaints = true,
                    canCloseComplaints = true,
                    canDeleteComplaints = false,
                    canEditComplaints = true,
                    canViewStatistics = true,
                    canManageUsers = false
                )
            }
            isTeamLeader(userRole) -> {
                Log.d(TAG, "Setting TEAM LEADER permissions")
                UserPermissions(
                    canViewAllComplaints = false,
                    canViewDepartmentComplaints = true,
                    canAssignComplaints = true,
                    canReopenComplaints = true,
                    canCloseComplaints = false,
                    canDeleteComplaints = false,
                    canEditComplaints = true,
                    canViewStatistics = false,
                    canManageUsers = false
                )
            }
            isSupervisor(userRole) -> {
                Log.d(TAG, "Setting SUPERVISOR permissions")
                UserPermissions(
                    canViewAllComplaints = false,
                    canViewDepartmentComplaints = true,
                    canAssignComplaints = true,
                    canReopenComplaints = false,
                    canCloseComplaints = false,
                    canDeleteComplaints = false,
                    canEditComplaints = false,
                    canViewStatistics = false,
                    canManageUsers = false
                )
            }
            else -> {
                Log.d(TAG, "Setting EMPLOYEE permissions")
                UserPermissions() // Default employee permissions (all false)
            }
        }
    }

    fun getInitialViewMode(role: String): ViewMode {
        val userRole = role.lowercase().trim()

        return when {
            isAdministrator(userRole) -> ViewMode.ALL_COMPANY
            isManager(userRole) || isTeamLeader(userRole) || isSupervisor(userRole) -> ViewMode.DEPARTMENT
            else -> ViewMode.PERSONAL
        }
    }

    fun canAccessViewMode(role: String, viewMode: ViewMode): Boolean {
        val userRole = role.lowercase().trim()

        return when (viewMode) {
            ViewMode.PERSONAL -> true // Everyone can view their own complaints

            ViewMode.ASSIGNED_TO_ME -> true // Everyone can view complaints assigned to them

            ViewMode.GLOBAL -> true // Everyone can view global complaints

            ViewMode.DEPARTMENT -> {
                // Only managers, team leaders, supervisors, and admins can view department complaints
                isManager(userRole) || isTeamLeader(userRole) ||
                        isSupervisor(userRole) || isAdministrator(userRole)
            }

            ViewMode.ALL_COMPANY -> {
                // Only administrators can view all company complaints
                isAdministrator(userRole)
            }
        }
    }

    fun canPerformAction(role: String, action: String): Boolean {
        val permissions = getPermissionsForRole(role)

        return when (action.lowercase()) {
            "assign" -> permissions.canAssignComplaints
            "close" -> permissions.canCloseComplaints
            "reopen" -> permissions.canReopenComplaints
            "edit" -> permissions.canEditComplaints
            "delete" -> permissions.canDeleteComplaints
            "view_all" -> permissions.canViewAllComplaints
            "view_department" -> permissions.canViewDepartmentComplaints
            "view_statistics" -> permissions.canViewStatistics
            "manage_users" -> permissions.canManageUsers
            else -> false
        }
    }

    fun getAvailableViewModes(role: String): List<ViewMode> {
        val userRole = role.lowercase().trim()
        val availableModes = mutableListOf<ViewMode>()

        // Everyone gets personal view
        availableModes.add(ViewMode.PERSONAL)

        // Everyone gets assigned to me view
        availableModes.add(ViewMode.ASSIGNED_TO_ME)

        // Everyone gets global view
        availableModes.add(ViewMode.GLOBAL)

        // Department view for managers and above
        if (isManager(userRole) || isTeamLeader(userRole) ||
            isSupervisor(userRole) || isAdministrator(userRole)) {
            availableModes.add(ViewMode.DEPARTMENT)
        }

        // All company view for admins only
        if (isAdministrator(userRole)) {
            availableModes.add(ViewMode.ALL_COMPANY)
        }

        return availableModes
    }

    fun getViewModeDisplayName(viewMode: ViewMode): String {
        return when (viewMode) {
            ViewMode.PERSONAL -> "My Complaints"
            ViewMode.ASSIGNED_TO_ME -> "Assigned to Me"
            ViewMode.DEPARTMENT -> "Department"
            ViewMode.ALL_COMPANY -> "All Company"
            ViewMode.GLOBAL -> "Global"
        }
    }

    fun getRoleDisplayName(role: String): String {
        val userRole = role.lowercase().trim()

        return when {
            isAdministrator(userRole) -> "Administrator"
            isManager(userRole) -> "Manager"
            isTeamLeader(userRole) -> "Team Leader"
            isSupervisor(userRole) -> "Supervisor"
            else -> "Employee"
        }
    }

    fun getRoleDescription(role: String): String {
        val userRole = role.lowercase().trim()

        return when {
            isAdministrator(userRole) -> "Full access to all complaints and user management"
            isManager(userRole) -> "Can manage department complaints and team members"
            isTeamLeader(userRole) -> "Can assign and manage team complaints"
            isSupervisor(userRole) -> "Can oversee and assign department complaints"
            else -> "Can create and view own complaints"
        }
    }

    fun getPermissionsSummary(role: String): List<String> {
        val permissions = getPermissionsForRole(role)
        val summary = mutableListOf<String>()

        if (permissions.canViewAllComplaints) {
            summary.add("View all company complaints")
        } else if (permissions.canViewDepartmentComplaints) {
            summary.add("View department complaints")
        } else {
            summary.add("View own complaints")
        }

        if (permissions.canAssignComplaints) {
            summary.add("Assign complaints to team members")
        }

        if (permissions.canCloseComplaints) {
            summary.add("Close and resolve complaints")
        }

        if (permissions.canReopenComplaints) {
            summary.add("Reopen closed complaints")
        }

        if (permissions.canEditComplaints) {
            summary.add("Edit complaint details")
        }

        if (permissions.canDeleteComplaints) {
            summary.add("Delete complaints")
        }

        if (permissions.canViewStatistics) {
            summary.add("View complaint statistics")
        }

        if (permissions.canManageUsers) {
            summary.add("Manage user accounts")
        }

        return summary
    }

    fun validateUserAction(
        userRole: String,
        action: String,
        targetUserId: String? = null,
        currentUserId: String
    ): Boolean {
        val permissions = getPermissionsForRole(userRole)

        return when (action.lowercase()) {
            "assign_complaint" -> {
                permissions.canAssignComplaints
            }
            "close_complaint" -> {
                permissions.canCloseComplaints
            }
            "reopen_complaint" -> {
                permissions.canReopenComplaints
            }
            "edit_complaint" -> {
                // Users can edit their own complaints if they're open, or if they have edit permissions
                permissions.canEditComplaints ||
                        (targetUserId == currentUserId)
            }
            "delete_complaint" -> {
                // Users can delete their own open complaints, or if they have delete permissions
                permissions.canDeleteComplaints ||
                        (targetUserId == currentUserId)
            }
            "view_user_profile" -> {
                // Managers and above can view profiles, users can view their own
                permissions.canManageUsers ||
                        isManager(userRole.lowercase()) ||
                        (targetUserId == currentUserId)
            }
            "edit_user_profile" -> {
                // Only admins can edit other profiles, users can edit their own
                permissions.canManageUsers || (targetUserId == currentUserId)
            }
            else -> false
        }
    }

    fun getRestrictedActions(role: String): List<String> {
        val permissions = getPermissionsForRole(role)
        val restrictedActions = mutableListOf<String>()

        if (!permissions.canAssignComplaints) {
            restrictedActions.add("assign_complaints")
        }

        if (!permissions.canCloseComplaints) {
            restrictedActions.add("close_complaints")
        }

        if (!permissions.canReopenComplaints) {
            restrictedActions.add("reopen_complaints")
        }

        if (!permissions.canEditComplaints) {
            restrictedActions.add("edit_complaints")
        }

        if (!permissions.canDeleteComplaints) {
            restrictedActions.add("delete_complaints")
        }

        if (!permissions.canViewAllComplaints) {
            restrictedActions.add("view_all_complaints")
        }

        if (!permissions.canViewStatistics) {
            restrictedActions.add("view_statistics")
        }

        if (!permissions.canManageUsers) {
            restrictedActions.add("manage_users")
        }

        return restrictedActions
    }

    // Helper methods for role checking
    private fun isAdministrator(role: String): Boolean {
        return role.contains("admin") || role == "administrator"
    }

    private fun isManager(role: String): Boolean {
        return role.contains("manager")
    }

    private fun isTeamLeader(role: String): Boolean {
        return role.contains("team leader") ||
                role.contains("teamleader") ||
                role.contains("lead") ||
                role.contains("team_leader")
    }

    private fun isSupervisor(role: String): Boolean {
        return role.contains("supervisor") ||
                role.contains("senior") ||
                role.contains("head")
    }

    fun canAssignToUser(assignerRole: String, targetUserRole: String): Boolean {
        val assignerRoleLower = assignerRole.lowercase().trim()
        val targetRoleLower = targetUserRole.lowercase().trim()

        return when {
            // Admins can assign to anyone
            isAdministrator(assignerRoleLower) -> true

            // Managers can assign to team leaders, supervisors, and employees
            isManager(assignerRoleLower) -> {
                !isAdministrator(targetRoleLower) && !isManager(targetRoleLower)
            }

            // Team leaders can assign to supervisors and employees
            isTeamLeader(assignerRoleLower) -> {
                !isAdministrator(targetRoleLower) &&
                        !isManager(targetRoleLower) &&
                        !isTeamLeader(targetRoleLower)
            }

            // Supervisors can assign to employees only
            isSupervisor(assignerRoleLower) -> {
                !isAdministrator(targetRoleLower) &&
                        !isManager(targetRoleLower) &&
                        !isTeamLeader(targetRoleLower) &&
                        !isSupervisor(targetRoleLower)
            }

            else -> false
        }
    }

    fun getMaxAssignableRoles(assignerRole: String): List<String> {
        val assignerRoleLower = assignerRole.lowercase().trim()

        return when {
            isAdministrator(assignerRoleLower) -> {
                listOf("Employee", "Supervisor", "Team Leader", "Manager")
            }
            isManager(assignerRoleLower) -> {
                listOf("Employee", "Supervisor", "Team Leader")
            }
            isTeamLeader(assignerRoleLower) -> {
                listOf("Employee", "Supervisor")
            }
            isSupervisor(assignerRoleLower) -> {
                listOf("Employee")
            }
            else -> emptyList()
        }
    }

    fun getRoleHierarchyLevel(role: String): Int {
        val roleLower = role.lowercase().trim()

        return when {
            isAdministrator(roleLower) -> 5
            isManager(roleLower) -> 4
            isTeamLeader(roleLower) -> 3
            isSupervisor(roleLower) -> 2
            else -> 1 // Employee
        }
    }

    fun canEscalateToRole(currentRole: String, targetRole: String): Boolean {
        val currentLevel = getRoleHierarchyLevel(currentRole)
        val targetLevel = getRoleHierarchyLevel(targetRole)

        // Can only escalate to higher levels
        return targetLevel > currentLevel
    }

    fun getEscalationTargets(currentRole: String): List<String> {
        val currentLevel = getRoleHierarchyLevel(currentRole)
        val targets = mutableListOf<String>()

        if (currentLevel < 2) targets.add("Supervisor")
        if (currentLevel < 3) targets.add("Team Leader")
        if (currentLevel < 4) targets.add("Manager")
        if (currentLevel < 5) targets.add("Administrator")

        return targets
    }

    fun getPermissionRestrictionMessage(action: String, role: String): String {
        return when (action.lowercase()) {
            "assign" -> "Your role ($role) does not have permission to assign complaints to other users."
            "close" -> "Your role ($role) does not have permission to close complaints."
            "reopen" -> "Your role ($role) does not have permission to reopen closed complaints."
            "edit" -> "Your role ($role) does not have permission to edit this complaint."
            "delete" -> "Your role ($role) does not have permission to delete complaints."
            "view_all" -> "Your role ($role) does not have permission to view all company complaints."
            "view_department" -> "Your role ($role) does not have permission to view department complaints."
            "view_statistics" -> "Your role ($role) does not have permission to view complaint statistics."
            "manage_users" -> "Your role ($role) does not have permission to manage user accounts."
            else -> "Your role ($role) does not have permission to perform this action."
        }
    }

    fun isActionAllowedForComplaint(
        userRole: String,
        userId: String,
        action: String,
        complaintCreatorId: String,
        complaintStatus: String,
        isAssignedToUser: Boolean = false
    ): Boolean {
        val permissions = getPermissionsForRole(userRole)
        val isOwner = userId == complaintCreatorId
        val isOpen = complaintStatus.lowercase() == "open"
        val isClosed = complaintStatus.lowercase() == "closed"

        return when (action.lowercase()) {
            "edit" -> {
                permissions.canEditComplaints || (isOwner && isOpen)
            }
            "delete" -> {
                permissions.canDeleteComplaints || (isOwner && isOpen)
            }
            "assign" -> {
                permissions.canAssignComplaints && !isClosed
            }
            "close" -> {
                permissions.canCloseComplaints && !isClosed
            }
            "reopen" -> {
                permissions.canReopenComplaints && isClosed
            }
            "view_details" -> {
                // Users can view details of their own complaints, assigned complaints,
                // or if they have appropriate permissions
                isOwner || isAssignedToUser ||
                        permissions.canViewAllComplaints ||
                        permissions.canViewDepartmentComplaints
            }
            "add_comment" -> {
                // Users can comment on their own complaints, assigned complaints,
                // or if they have appropriate permissions
                isOwner || isAssignedToUser ||
                        permissions.canEditComplaints ||
                        permissions.canViewDepartmentComplaints
            }
            else -> false
        }
    }
}