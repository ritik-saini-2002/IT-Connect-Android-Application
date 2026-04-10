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

    fun forRole(role: String): List<String> = when (role) {

        // ── Super-role: full access across ALL companies ───────────────────────
        ROLE_SYSTEM_ADMIN -> listOf(
            // Everything Administrator has, plus cross-company and DB powers
            "create_user", "delete_user", "modify_user", "view_all_users",
            "manage_roles", "view_analytics", "system_settings", "manage_companies",
            "access_all_data", "export_data", "manage_permissions", "access_admin_panel",
            "submit_complaints", "view_all_complaints", "resolve_complaints",
            // Super-role exclusives
            "database_manager", "view_all_companies", "manage_all_companies",
            "edit_system_administrator", "grant_revoke_any_permission",
            "manage_system_settings", "view_audit_logs"
        )

        // ── Company-scoped admin ───────────────────────────────────────────────
        ROLE_ADMIN -> listOf(
            "create_user", "delete_user", "modify_user", "view_all_users",
            "manage_roles", "view_analytics", "system_settings", "manage_companies",
            "access_all_data", "export_data", "manage_permissions", "access_admin_panel",
            "submit_complaints", "view_all_complaints", "resolve_complaints"
            // NOTE: "database_manager" is NOT included by default — only System_Administrator
            //       or an explicitly granted permission gives this.
        )

        ROLE_MANAGER -> listOf(
            "view_team_users", "modify_team_user", "view_team_analytics",
            "assign_projects", "approve_requests", "view_reports",
            "submit_complaints", "view_department_complaints", "resolve_complaints",
            "access_admin_panel"
        )

        ROLE_HR -> listOf(
            "view_all_users", "modify_user", "view_hr_analytics",
            "manage_employees", "access_personal_data", "generate_reports",
            "submit_complaints", "view_all_complaints", "resolve_complaints",
            "access_admin_panel"
        )

        ROLE_TEAM_LEAD -> listOf(
            "view_team_users", "assign_tasks", "view_team_performance",
            "approve_leave", "submit_complaints", "view_team_complaints"
        )

        ROLE_EMPLOYEE -> listOf(
            "view_profile", "edit_profile", "view_assigned_projects",
            "submit_reports", "submit_complaints", "view_own_complaints"
        )

        ROLE_INTERN -> listOf(
            "view_profile", "edit_basic_profile",
            "view_assigned_tasks", "submit_complaints"
        )

        else -> listOf("view_profile")
    }

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

    /**
     * Returns true if [role] has the given [permission] by default.
     * Individual overrides are stored per-user in user_access_control.
     */
    fun hasPermission(role: String, permission: String): Boolean =
        permission in forRole(role)
}