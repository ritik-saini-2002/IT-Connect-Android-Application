package com.example.ritik_2.data.model

object Permissions {

    // ── Role string constants ─────────────────────────────────────────────────
    const val ROLE_SYSTEM_ADMIN = "System_Administrator"
    const val ROLE_ADMIN        = "Administrator"
    const val ROLE_MANAGER      = "Manager"
    const val ROLE_HR           = "HR"
    const val ROLE_TEAM_LEAD    = "Team Lead"
    const val ROLE_EMPLOYEE     = "Employee"
    const val ROLE_INTERN       = "Intern"

    /**
     * Default permissions used ONLY in SyncManager.seedRoleDefinitions() as a one-time bootstrap.
     * All other code must use DB lookup from RoleEntity.permissions instead.
     * @suppress kept for bootstrap use in SyncManager only
     */
    @Deprecated("Use DB lookup from RoleEntity.permissions. Only SyncManager.seedRoleDefinitions() may call this.")
    fun forRole(role: String): List<String> = when (role) {
        ROLE_SYSTEM_ADMIN -> ALL_PERMISSIONS
        ROLE_ADMIN -> listOf(
            "create_user", "delete_user", "modify_user", "view_all_users",
            "manage_roles", "view_analytics", "system_settings", "manage_companies",
            "access_all_data", "export_data", "manage_permissions", "access_admin_panel",
            "submit_complaints", "view_all_complaints", "resolve_complaints",
            // Feature access
            "access_server_connect", "access_windows_control",
            "access_nagios", "access_knowledge_base"
        )
        ROLE_MANAGER -> listOf(
            "view_team_users", "modify_team_user", "view_team_analytics",
            "assign_projects", "approve_requests", "view_reports",
            "submit_complaints", "view_department_complaints", "resolve_complaints",
            "access_admin_panel",
            // Feature access
            "access_server_connect", "access_nagios", "access_knowledge_base"
        )
        ROLE_HR -> listOf(
            "view_all_users", "modify_user", "view_hr_analytics",
            "manage_employees", "access_personal_data", "generate_reports",
            "submit_complaints", "view_all_complaints", "resolve_complaints",
            "access_admin_panel",
            // Feature access
            "access_server_connect", "access_knowledge_base"
        )
        ROLE_TEAM_LEAD -> listOf(
            "view_team_users", "assign_tasks", "view_team_performance",
            "approve_leave", "submit_complaints", "view_team_complaints",
            // Feature access
            "access_server_connect", "access_knowledge_base"
        )
        ROLE_EMPLOYEE -> listOf(
            "view_profile", "edit_profile", "view_assigned_projects",
            "submit_reports", "submit_complaints", "view_own_complaints",
            // Feature access
            "access_knowledge_base"
        )
        ROLE_INTERN -> listOf(
            "view_profile", "edit_basic_profile",
            "view_assigned_tasks", "submit_complaints"
        )
        else -> listOf("view_profile")
    }

    /**
     * All permissions that exist in the system.
     * Kept here as the canonical list so it can be referenced from any file.
     * System_Administrator always receives all of these regardless of server value.
     */
    val ALL_PERMISSIONS: List<String> = listOf(
        // User management
        "create_user", "delete_user", "modify_user", "view_all_users",
        // Role & company
        "manage_roles", "manage_companies", "manage_permissions",
        // Data & analytics
        "view_analytics", "view_reports", "export_data", "access_all_data",
        // Admin
        "system_settings", "access_admin_panel", "database_manager",
        // Team
        "view_team_users", "modify_team_user", "view_team_analytics",
        "assign_projects", "assign_tasks", "approve_requests",
        "view_team_performance", "approve_leave",
        // HR
        "manage_employees", "access_personal_data", "generate_reports",
        "view_hr_analytics",
        // Profile
        "view_profile", "edit_profile", "edit_basic_profile",
        // Projects & tasks
        "view_assigned_projects", "view_assigned_tasks",
        "submit_reports",
        // Complaints
        "submit_complaints", "view_own_complaints",
        "view_team_complaints", "view_department_complaints",
        "view_all_complaints", "resolve_complaints",
        // Super-role exclusives
        "view_all_companies", "manage_all_companies",
        "edit_system_administrator", "grant_revoke_any_permission",
        "manage_system_settings", "view_audit_logs",
        // Feature-level access gates (dashboard tiles)
        "access_server_connect",    // Windows SMB file share
        "access_windows_control",   // PC remote control / touchpad
        "access_nagios",            // Nagios server monitor
        "access_knowledge_base"     // MAC / network lookup tool
    )

    /**
     * All roles in hierarchy order — lower index = higher authority.
     * System_Administrator is the apex super-role.
     */
    val ALL_ROLES = listOf(
        ROLE_SYSTEM_ADMIN,
        ROLE_ADMIN,
        ROLE_MANAGER,
        ROLE_HR,
        ROLE_TEAM_LEAD,
        ROLE_EMPLOYEE,
        ROLE_INTERN
    )

    /**
     * Roles that are shown in the "Create User" / "Role Management" screens
     * when the editor is a regular Administrator (cannot create System_Administrator).
     */
    val ADMIN_ASSIGNABLE_ROLES = listOf(
        ROLE_ADMIN,
        ROLE_MANAGER,
        ROLE_HR,
        ROLE_TEAM_LEAD,
        ROLE_EMPLOYEE,
        ROLE_INTERN
    )

}