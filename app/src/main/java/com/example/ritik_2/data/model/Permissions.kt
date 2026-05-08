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

    // ── Permission key constants ──────────────────────────────────────────────
    // Use these instead of bare string literals at every call site. A typo
    // against a const fails to compile, whereas a typo in a raw string like
    // `"acces_admin_panel"` silently breaks the gate.

    // User management
    const val PERM_CREATE_USER                  = "create_user"
    const val PERM_DELETE_USER                  = "delete_user"
    const val PERM_MODIFY_USER                  = "modify_user"
    const val PERM_VIEW_ALL_USERS               = "view_all_users"
    // Role & company
    const val PERM_MANAGE_ROLES                 = "manage_roles"
    const val PERM_MANAGE_COMPANIES             = "manage_companies"
    const val PERM_MANAGE_PERMISSIONS           = "manage_permissions"
    // Data & analytics
    const val PERM_VIEW_ANALYTICS               = "view_analytics"
    const val PERM_VIEW_REPORTS                 = "view_reports"
    const val PERM_EXPORT_DATA                  = "export_data"
    const val PERM_ACCESS_ALL_DATA              = "access_all_data"
    // Admin
    const val PERM_SYSTEM_SETTINGS              = "system_settings"
    const val PERM_ACCESS_ADMIN_PANEL           = "access_admin_panel"
    const val PERM_DATABASE_MANAGER             = "database_manager"
    // Team
    const val PERM_VIEW_TEAM_USERS              = "view_team_users"
    const val PERM_MODIFY_TEAM_USER             = "modify_team_user"
    const val PERM_VIEW_TEAM_ANALYTICS          = "view_team_analytics"
    const val PERM_ASSIGN_PROJECTS              = "assign_projects"
    const val PERM_ASSIGN_TASKS                 = "assign_tasks"
    const val PERM_APPROVE_REQUESTS             = "approve_requests"
    const val PERM_VIEW_TEAM_PERFORMANCE        = "view_team_performance"
    const val PERM_APPROVE_LEAVE                = "approve_leave"
    // HR
    const val PERM_MANAGE_EMPLOYEES             = "manage_employees"
    const val PERM_ACCESS_PERSONAL_DATA         = "access_personal_data"
    const val PERM_GENERATE_REPORTS             = "generate_reports"
    const val PERM_VIEW_HR_ANALYTICS            = "view_hr_analytics"
    // Profile
    const val PERM_VIEW_PROFILE                 = "view_profile"
    const val PERM_EDIT_PROFILE                 = "edit_profile"
    const val PERM_EDIT_BASIC_PROFILE           = "edit_basic_profile"
    // Projects & tasks
    const val PERM_VIEW_ASSIGNED_PROJECTS       = "view_assigned_projects"
    const val PERM_VIEW_ASSIGNED_TASKS          = "view_assigned_tasks"
    const val PERM_SUBMIT_REPORTS               = "submit_reports"
    // Complaints
    const val PERM_SUBMIT_COMPLAINTS            = "submit_complaints"
    const val PERM_VIEW_OWN_COMPLAINTS          = "view_own_complaints"
    const val PERM_VIEW_TEAM_COMPLAINTS         = "view_team_complaints"
    const val PERM_VIEW_DEPARTMENT_COMPLAINTS   = "view_department_complaints"
    const val PERM_VIEW_ALL_COMPLAINTS          = "view_all_complaints"
    const val PERM_RESOLVE_COMPLAINTS           = "resolve_complaints"
    // Super-role exclusives
    const val PERM_VIEW_ALL_COMPANIES           = "view_all_companies"
    const val PERM_MANAGE_ALL_COMPANIES         = "manage_all_companies"
    const val PERM_EDIT_SYSTEM_ADMINISTRATOR    = "edit_system_administrator"
    const val PERM_GRANT_REVOKE_ANY_PERMISSION  = "grant_revoke_any_permission"
    const val PERM_MANAGE_SYSTEM_SETTINGS       = "manage_system_settings"
    const val PERM_VIEW_AUDIT_LOGS              = "view_audit_logs"
    // Feature-level access gates (dashboard tiles)
    const val PERM_ACCESS_SERVER_CONNECT        = "access_server_connect"
    const val PERM_ACCESS_WINDOWS_CONTROL       = "access_windows_control"
    const val PERM_ACCESS_NAGIOS                = "access_nagios"
    const val PERM_ACCESS_KNOWLEDGE_BASE        = "access_knowledge_base"
    // Windows Control sub-feature gates
    const val PERM_WINDOWS_CONTROL_TOUCHPAD         = "windows_control_touchpad"
    const val PERM_WINDOWS_CONTROL_FILE_BROWSER     = "windows_control_file_browser"
    const val PERM_WINDOWS_CONTROL_APP_DIRECTORY    = "windows_control_app_directory"
    const val PERM_WINDOWS_CONTROL_ADMIN_SETTINGS   = "windows_control_admin_settings"
    const val PERM_WINDOWS_CONTROL_ADD_STEP         = "windows_control_add_step"
    const val PERM_MANAGE_APP_UPDATES = "manage_app_updates"


    /**
     * Default permissions used ONLY in SyncManager.seedRoleDefinitions() as a one-time bootstrap.
     * All other code must use DB lookup from RoleEntity.permissions instead.
     * @suppress kept for bootstrap use in SyncManager only
     */
    @Deprecated("Use DB lookup from RoleEntity.permissions. Only SyncManager.seedRoleDefinitions() may call this.")
    fun forRole(role: String): List<String> = when (role) {
        ROLE_SYSTEM_ADMIN -> ALL_PERMISSIONS
        ROLE_ADMIN -> listOf(
            PERM_CREATE_USER, PERM_DELETE_USER, PERM_MODIFY_USER, PERM_VIEW_ALL_USERS,
            PERM_MANAGE_ROLES, PERM_VIEW_ANALYTICS, PERM_SYSTEM_SETTINGS, PERM_MANAGE_COMPANIES,
            PERM_ACCESS_ALL_DATA, PERM_EXPORT_DATA, PERM_MANAGE_PERMISSIONS, PERM_ACCESS_ADMIN_PANEL,
            PERM_SUBMIT_COMPLAINTS, PERM_VIEW_ALL_COMPLAINTS, PERM_RESOLVE_COMPLAINTS,
            // Feature access
            PERM_ACCESS_SERVER_CONNECT, PERM_ACCESS_WINDOWS_CONTROL,
            PERM_ACCESS_NAGIOS, PERM_ACCESS_KNOWLEDGE_BASE,
            // Windows Control sub-features
            PERM_WINDOWS_CONTROL_TOUCHPAD, PERM_WINDOWS_CONTROL_FILE_BROWSER,
            PERM_WINDOWS_CONTROL_APP_DIRECTORY, PERM_WINDOWS_CONTROL_ADMIN_SETTINGS,
            PERM_WINDOWS_CONTROL_ADD_STEP,

            PERM_MANAGE_APP_UPDATES
        )
        ROLE_MANAGER -> listOf(
            PERM_VIEW_TEAM_USERS, PERM_MODIFY_TEAM_USER, PERM_VIEW_TEAM_ANALYTICS,
            PERM_ASSIGN_PROJECTS, PERM_APPROVE_REQUESTS, PERM_VIEW_REPORTS,
            PERM_SUBMIT_COMPLAINTS, PERM_VIEW_DEPARTMENT_COMPLAINTS, PERM_RESOLVE_COMPLAINTS,
            PERM_ACCESS_ADMIN_PANEL,
            // Feature access
            PERM_ACCESS_SERVER_CONNECT, PERM_ACCESS_NAGIOS, PERM_ACCESS_KNOWLEDGE_BASE,

            PERM_MANAGE_APP_UPDATES
        )
        ROLE_HR -> listOf(
            PERM_VIEW_ALL_USERS, PERM_MODIFY_USER, PERM_VIEW_HR_ANALYTICS,
            PERM_MANAGE_EMPLOYEES, PERM_ACCESS_PERSONAL_DATA, PERM_GENERATE_REPORTS,
            PERM_SUBMIT_COMPLAINTS, PERM_VIEW_ALL_COMPLAINTS, PERM_RESOLVE_COMPLAINTS,
            PERM_ACCESS_ADMIN_PANEL,
            // Feature access
            PERM_ACCESS_SERVER_CONNECT, PERM_ACCESS_KNOWLEDGE_BASE,

            PERM_MANAGE_APP_UPDATES
        )
        ROLE_TEAM_LEAD -> listOf(
            PERM_VIEW_TEAM_USERS, PERM_ASSIGN_TASKS, PERM_VIEW_TEAM_PERFORMANCE,
            PERM_APPROVE_LEAVE, PERM_SUBMIT_COMPLAINTS, PERM_VIEW_TEAM_COMPLAINTS,
            // Feature access
            PERM_ACCESS_SERVER_CONNECT, PERM_ACCESS_KNOWLEDGE_BASE,

            PERM_MANAGE_APP_UPDATES
        )
        ROLE_EMPLOYEE -> listOf(
            PERM_VIEW_PROFILE, PERM_EDIT_PROFILE, PERM_VIEW_ASSIGNED_PROJECTS,
            PERM_SUBMIT_REPORTS, PERM_SUBMIT_COMPLAINTS, PERM_VIEW_OWN_COMPLAINTS,
            // Feature access
            PERM_ACCESS_KNOWLEDGE_BASE,

            PERM_MANAGE_APP_UPDATES
        )
        ROLE_INTERN -> listOf(
            PERM_VIEW_PROFILE, PERM_EDIT_BASIC_PROFILE,
            PERM_VIEW_ASSIGNED_TASKS, PERM_SUBMIT_COMPLAINTS,

            PERM_MANAGE_APP_UPDATES
        )
        else -> listOf(PERM_VIEW_PROFILE)
    }

    /**
     * All permissions that exist in the system.
     * Kept here as the canonical list so it can be referenced from any file.
     * System_Administrator always receives all of these regardless of server value.
     */
    val ALL_PERMISSIONS: List<String> = listOf(
        // User management
        PERM_CREATE_USER, PERM_DELETE_USER, PERM_MODIFY_USER, PERM_VIEW_ALL_USERS,
        // Role & company
        PERM_MANAGE_ROLES, PERM_MANAGE_COMPANIES, PERM_MANAGE_PERMISSIONS,
        // Data & analytics
        PERM_VIEW_ANALYTICS, PERM_VIEW_REPORTS, PERM_EXPORT_DATA, PERM_ACCESS_ALL_DATA,
        // Admin
        PERM_SYSTEM_SETTINGS, PERM_ACCESS_ADMIN_PANEL, PERM_DATABASE_MANAGER,
        // Team
        PERM_VIEW_TEAM_USERS, PERM_MODIFY_TEAM_USER, PERM_VIEW_TEAM_ANALYTICS,
        PERM_ASSIGN_PROJECTS, PERM_ASSIGN_TASKS, PERM_APPROVE_REQUESTS,
        PERM_VIEW_TEAM_PERFORMANCE, PERM_APPROVE_LEAVE,
        // HR
        PERM_MANAGE_EMPLOYEES, PERM_ACCESS_PERSONAL_DATA, PERM_GENERATE_REPORTS,
        PERM_VIEW_HR_ANALYTICS,
        // Profile
        PERM_VIEW_PROFILE, PERM_EDIT_PROFILE, PERM_EDIT_BASIC_PROFILE,
        // Projects & tasks
        PERM_VIEW_ASSIGNED_PROJECTS, PERM_VIEW_ASSIGNED_TASKS,
        PERM_SUBMIT_REPORTS,
        // Complaints
        PERM_SUBMIT_COMPLAINTS, PERM_VIEW_OWN_COMPLAINTS,
        PERM_VIEW_TEAM_COMPLAINTS, PERM_VIEW_DEPARTMENT_COMPLAINTS,
        PERM_VIEW_ALL_COMPLAINTS, PERM_RESOLVE_COMPLAINTS,
        // Super-role exclusives
        PERM_VIEW_ALL_COMPANIES, PERM_MANAGE_ALL_COMPANIES,
        PERM_EDIT_SYSTEM_ADMINISTRATOR, PERM_GRANT_REVOKE_ANY_PERMISSION,
        PERM_MANAGE_SYSTEM_SETTINGS, PERM_VIEW_AUDIT_LOGS,
        // Feature-level access gates (dashboard tiles)
        PERM_ACCESS_SERVER_CONNECT,    // Windows SMB file share
        PERM_ACCESS_WINDOWS_CONTROL,   // PC remote control / touchpad
        PERM_ACCESS_NAGIOS,            // Nagios server monitor
        PERM_ACCESS_KNOWLEDGE_BASE,    // MAC / network lookup tool
        // Windows Control sub-feature gates (require access_windows_control + sub-key)
        PERM_WINDOWS_CONTROL_TOUCHPAD,
        PERM_WINDOWS_CONTROL_FILE_BROWSER,
        PERM_WINDOWS_CONTROL_APP_DIRECTORY,
        PERM_WINDOWS_CONTROL_ADMIN_SETTINGS,
        PERM_WINDOWS_CONTROL_ADD_STEP,

        PERM_MANAGE_APP_UPDATES
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
