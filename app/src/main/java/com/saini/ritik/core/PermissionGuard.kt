package com.saini.ritik.core

import com.saini.ritik.data.model.Permissions

/**
 * Single source of truth for all permission checks in the app.
 *
 * Role hierarchy (highest → lowest authority):
 *   System_Administrator  → super-role; full access across ALL companies;
 *                           only one who can edit another System_Administrator
 *   Administrator         → full access within own company; cannot touch DB manager
 *   Manager / HR          → can edit Employee, Intern, Team Lead only
 *   Everyone              → can always edit their OWN profile
 *
 * Permission-based checks:
 *   Actual access is driven by the user's permission list (stored in
 *   user_access_control), not just their role string.  Role gives the
 *   DEFAULT permission set; Administrator can grant/revoke extras per user.
 *   Only System_Administrator can grant/revoke any permission to any user.
 */
object PermissionGuard {

    // ── Role hierarchy ────────────────────────────────────────────────────────

    private val ROLE_HIERARCHY = listOf(
        Permissions.ROLE_SYSTEM_ADMIN,
        Permissions.ROLE_ADMIN,
        Permissions.ROLE_MANAGER,
        Permissions.ROLE_HR,
        Permissions.ROLE_TEAM_LEAD,
        Permissions.ROLE_EMPLOYEE,
        Permissions.ROLE_INTERN
    )

    /** Lower return value = higher authority. Unknown roles rank last. */
    fun rankOf(role: String): Int = ROLE_HIERARCHY.indexOf(role).let {
        if (it == -1) ROLE_HIERARCHY.size else it
    }

    fun isSystemAdmin(role: String): Boolean =
        role == Permissions.ROLE_SYSTEM_ADMIN

    // ── Profile editing ───────────────────────────────────────────────────────

    /**
     * Can [editorRole] edit the profile of a user with [targetRole]?
     *
     * Rules:
     *  - System_Administrator → can edit anyone including other System_Administrators
     *  - Administrator        → can edit anyone in own company EXCEPT System_Administrator
     *  - Manager/HR           → can edit Employee, Intern, Team Lead
     *  - Anyone               → can always edit their OWN profile
     */
    fun canEditProfile(
        editorRole: String,
        targetRole: String,
        editorId  : String,
        targetId  : String,
        isDbAdmin : Boolean = false
    ): Boolean {
        if (isDbAdmin || isSystemAdmin(editorRole)) return true
        if (editorId == targetId) return true
        // Nobody except System_Administrator can edit a System_Administrator
        if (isSystemAdmin(targetRole)) return false
        return when (editorRole) {
            Permissions.ROLE_ADMIN   -> true   // own company, non-system-admin targets
            Permissions.ROLE_MANAGER -> targetRole in setOf(
                Permissions.ROLE_EMPLOYEE, Permissions.ROLE_INTERN, Permissions.ROLE_TEAM_LEAD)
            Permissions.ROLE_HR      -> targetRole in setOf(
                Permissions.ROLE_EMPLOYEE, Permissions.ROLE_INTERN,
                Permissions.ROLE_TEAM_LEAD, Permissions.ROLE_MANAGER)
            else -> false
        }
    }

    /**
     * Which fields can [editorRole] write on [targetRole]'s profile?
     * Checks permission list first; falls back to role-based defaults.
     */
    fun editableFields(
        editorRole       : String,
        targetRole       : String,
        editorId         : String,
        targetId         : String,
        isDbAdmin        : Boolean       = false,
        editorPermissions: List<String>  = emptyList()
    ): Set<String> {
        if (isDbAdmin || isSystemAdmin(editorRole)) return ALL_FIELDS
        if (isSystemAdmin(targetRole) && !isSystemAdmin(editorRole)) return emptySet()

        // Permission-based override — if the user was explicitly granted full edit
        if (Permissions.PERM_ACCESS_ALL_DATA in editorPermissions) return ALL_FIELDS

        if (editorId == targetId) return SELF_EDITABLE_FIELDS

        return when (editorRole) {
            Permissions.ROLE_ADMIN   -> ALL_FIELDS
            Permissions.ROLE_MANAGER -> if (targetRole in setOf(
                    Permissions.ROLE_EMPLOYEE, Permissions.ROLE_INTERN, Permissions.ROLE_TEAM_LEAD))
                MANAGER_EDITABLE_FIELDS else emptySet()
            Permissions.ROLE_HR      -> if (targetRole in setOf(
                    Permissions.ROLE_EMPLOYEE, Permissions.ROLE_INTERN, Permissions.ROLE_TEAM_LEAD))
                MANAGER_EDITABLE_FIELDS else emptySet()
            else -> emptySet()
        }
    }

    // ── Role management ───────────────────────────────────────────────────────

    /**
     * Can [editorRole] change a user's role from [targetRole] to [newRole]?
     *
     * Rules:
     *  - System_Administrator → can change any role to any role
     *  - Administrator        → can change roles within own company,
     *                           but CANNOT assign or revoke System_Administrator
     *  - Manager              → can change Employee / Intern / Team Lead only,
     *                           cannot promote to Manager or above
     *  - HR                   → cannot change roles
     */
    fun canChangeRole(
        editorRole: String,
        targetRole: String,
        newRole   : String,
        isDbAdmin : Boolean = false
    ): Boolean {
        if (isDbAdmin || isSystemAdmin(editorRole)) return true
        // Nobody else can touch System_Administrator role assignment
        if (isSystemAdmin(targetRole) || isSystemAdmin(newRole)) return false
        return when (editorRole) {
            Permissions.ROLE_ADMIN   -> true
            Permissions.ROLE_MANAGER -> targetRole in setOf(
                Permissions.ROLE_EMPLOYEE, Permissions.ROLE_INTERN, Permissions.ROLE_TEAM_LEAD) &&
                    newRole in setOf(
                Permissions.ROLE_EMPLOYEE, Permissions.ROLE_INTERN, Permissions.ROLE_TEAM_LEAD)
            else -> false
        }
    }

    // ── Permission grant / revoke ─────────────────────────────────────────────

    /**
     * Can [editorRole] grant or revoke [permission] for a user with [targetRole]?
     *
     *  - System_Administrator → can grant/revoke ANY permission to ANY user
     *  - Administrator        → can grant/revoke permissions within their company
     *                           EXCEPT system-level ones and "edit_system_administrator"
     *  - Others               → cannot modify permissions
     */
    fun canGrantOrRevokePermission(
        editorRole : String,
        targetRole : String,
        permission : String,
        isDbAdmin  : Boolean = false
    ): Boolean {
        if (isDbAdmin || isSystemAdmin(editorRole)) return true
        if (isSystemAdmin(targetRole)) return false
        if (editorRole != Permissions.ROLE_ADMIN) return false
        // Administrator cannot grant system-level permissions
        return permission !in SYSTEM_ONLY_PERMISSIONS
    }

    // ── Department movement ───────────────────────────────────────────────────

    fun canMoveDepartment(
        editorRole: String,
        targetRole: String,
        isDbAdmin : Boolean = false
    ): Boolean {
        if (isDbAdmin || isSystemAdmin(editorRole)) return true
        if (isSystemAdmin(targetRole)) return false
        return when (editorRole) {
            Permissions.ROLE_ADMIN   -> true
            Permissions.ROLE_MANAGER -> targetRole in setOf(
                Permissions.ROLE_EMPLOYEE, Permissions.ROLE_INTERN, Permissions.ROLE_TEAM_LEAD)
            else -> false
        }
    }

    // ── Company scope ─────────────────────────────────────────────────────────

    /**
     * Can the user see/manage [targetCompany]?
     * System_Administrator and DB admin can see all companies.
     */
    fun canAccessCompany(
        editorRole    : String,
        userCompany   : String,
        targetCompany : String,
        isDbAdmin     : Boolean = false
    ): Boolean = isDbAdmin || isSystemAdmin(editorRole) || userCompany == targetCompany

    // ── Feature gates ─────────────────────────────────────────────────────────

    /**
     * Database Manager is only accessible to System_Administrator or DB admin,
     * OR a user who was explicitly granted "database_manager" permission.
     */
    fun canAccessDatabaseManager(
        role       : String,
        permissions: List<String>,
        isDbAdmin  : Boolean = false
    ): Boolean = isDbAdmin || isSystemAdmin(role) || Permissions.PERM_DATABASE_MANAGER in permissions

    fun canAccessAdminPanel(
        role    : String,
        isDbAdmin: Boolean = false
    ): Boolean = isDbAdmin || role in setOf(
        Permissions.ROLE_SYSTEM_ADMIN,
        Permissions.ROLE_ADMIN,
        Permissions.ROLE_MANAGER,
        Permissions.ROLE_HR
    )

    /**
     * Master dashboard-tile gate. Called in two places:
     *   1. MainScreen — to decide whether to SHOW the tile at all.
     *   2. handleCardClick — as a hard guard before launching an Activity.
     *
     * Feature ID → Access rule:
     *  1  Register Complaint  → everyone with submit_complaints (all roles)
     *  2  Manage Complaints   → anyone who can view any complaint feed
     *  3  Admin Panel         → access_admin_panel permission OR qualifying role
     *  4  Server Connect      → access_server_connect permission
     *  5  Knowledge Base      → access_knowledge_base permission
     *  6  Windows Control     → access_windows_control permission
     *  7  Chats               → all users (no restriction)
     *  8  Help & Support      → all users (no restriction)
     *  9  Nagios Monitor      → access_nagios permission
     */
    fun canAccessFeature(
        featureId  : Int,
        role       : String,
        permissions: List<String>,
        isDbAdmin  : Boolean = false
    ): Boolean {
        // System_Administrator and DB admin bypass every gate
        if (isDbAdmin || isSystemAdmin(role)) return true

        return when (featureId) {
            1 -> Permissions.PERM_SUBMIT_COMPLAINTS in permissions
            2 -> permissions.any { it in listOf(
                    Permissions.PERM_VIEW_ALL_COMPLAINTS,
                    Permissions.PERM_VIEW_DEPARTMENT_COMPLAINTS,
                    Permissions.PERM_VIEW_TEAM_COMPLAINTS
                )}
            3 -> canAccessAdminPanel(role, isDbAdmin) || Permissions.PERM_ACCESS_ADMIN_PANEL in permissions
            4 -> Permissions.PERM_ACCESS_SERVER_CONNECT  in permissions
            5 -> Permissions.PERM_ACCESS_KNOWLEDGE_BASE  in permissions
            6 -> Permissions.PERM_ACCESS_WINDOWS_CONTROL in permissions
            7 -> true   // Chats — all users
            8 -> true   // Help & Support — all users
            9 -> Permissions.PERM_ACCESS_NAGIOS in permissions
            10 -> isDbAdmin || isSystemAdmin(role) || Permissions.PERM_MANAGE_APP_UPDATES in permissions
            else -> false
        }
    }

    /**
     * Gate for Windows Control sub-features (Touchpad, File Browser, etc.).
     * Requires both the parent gate AND the specific sub-feature permission.
     * System_Administrator and DB admin bypass unconditionally.
     */
    fun canAccessWindowsControlSub(
        subPermission: String,
        role         : String,
        permissions  : List<String>,
        isDbAdmin    : Boolean = false
    ): Boolean {
        if (isDbAdmin || isSystemAdmin(role)) return true
        return Permissions.PERM_ACCESS_WINDOWS_CONTROL in permissions && subPermission in permissions
    }

    /**
     * Human-readable name for each feature tile — used in "Access Denied" messages.
     */
    fun featureName(featureId: Int): String = when (featureId) {
        1 -> "Register Complaint"
        2 -> "Manage Complaints"
        3 -> "Admin Panel"
        4 -> "Server Connect"
        5 -> "Knowledge Base"
        6 -> "Windows Control"
        7 -> "Chats"
        8 -> "Help & Support"
        9 -> "Nagios Monitor"
        10 -> "App Updates"
        else -> "this feature"
    }

    // ── Field sets ────────────────────────────────────────────────────────────

    val ALL_FIELDS: Set<String> = setOf(
        "name", "phoneNumber", "designation", "role", "department", "companyName",
        "address", "employeeId", "reportingTo", "salary", "experience",
        "emergencyContactName", "emergencyContactPhone", "emergencyContactRelation",
        "imageUrl"
    )

    val SELF_EDITABLE_FIELDS: Set<String> = setOf(
        "address", "experience",
        "emergencyContactName", "emergencyContactPhone", "emergencyContactRelation",
        "imageUrl", "phoneNumber"
    )

    val MANAGER_EDITABLE_FIELDS: Set<String> = setOf(
        "designation", "department", "experience",
        "emergencyContactName", "emergencyContactPhone", "emergencyContactRelation"
    )

    /** Permissions that only System_Administrator can grant/revoke. */
    private val SYSTEM_ONLY_PERMISSIONS: Set<String> = setOf(
        Permissions.PERM_DATABASE_MANAGER,
        Permissions.PERM_VIEW_ALL_COMPANIES, Permissions.PERM_MANAGE_ALL_COMPANIES,
        Permissions.PERM_EDIT_SYSTEM_ADMINISTRATOR, Permissions.PERM_GRANT_REVOKE_ANY_PERMISSION,
        Permissions.PERM_MANAGE_SYSTEM_SETTINGS, Permissions.PERM_VIEW_AUDIT_LOGS
    )
}