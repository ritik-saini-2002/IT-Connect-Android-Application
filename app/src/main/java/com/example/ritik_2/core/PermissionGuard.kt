package com.example.ritik_2.core

/**
 * Single source of truth for all permission checks in the app.
 *
 * Rules enforced:
 * ─ Administrator  → full access to own company; DB admin can see ALL companies
 * ─ Manager / HR   → can edit Employee, Intern, Team Lead only (not Admin/Manager/HR)
 *                    Manager CANNOT change department roles or edit Admin profiles
 *                    HR     CAN  manage Manager profiles (view only, no role change)
 * ─ Everyone       → can always edit their OWN profile
 * ─ Database admin → determined by matching AppConfig.ADMIN_EMAIL at login time
 */
object PermissionGuard {

    // ── Role hierarchy (lower index = higher authority) ───────────────────────
    private val ROLE_HIERARCHY = listOf(
        "Administrator", "Manager", "HR", "Team Lead", "Employee", "Intern"
    )

    private fun rankOf(role: String) = ROLE_HIERARCHY.indexOf(role).let {
        if (it == -1) ROLE_HIERARCHY.size else it
    }

    // ── Profile editing ───────────────────────────────────────────────────────

    /**
     * Can [editorRole] edit the profile of a user with [targetRole]?
     * [editorId] == [targetId] always returns true (own profile).
     */
    fun canEditProfile(
        editorRole: String,
        targetRole: String,
        editorId  : String,
        targetId  : String,
        isDbAdmin : Boolean = false
    ): Boolean {
        if (isDbAdmin) return true
        if (editorId == targetId) return true
        return when (editorRole) {
            "Administrator" -> true
            "Manager"       -> targetRole in setOf("Employee", "Intern", "Team Lead")
            // HR can view Manager profiles but cannot edit them (handled in screen)
            "HR"            -> targetRole in setOf("Employee", "Intern", "Team Lead", "Manager")
            else            -> false
        }
    }

    /**
     * Which fields can [editorRole] actually write when editing [targetRole]'s profile?
     * Returns a set of allowed field keys matching ProfileSaveData property names.
     */
    fun editableFields(
        editorRole: String,
        targetRole: String,
        editorId  : String,
        targetId  : String,
        isDbAdmin : Boolean = false
    ): Set<String> {
        if (isDbAdmin) return ALL_FIELDS

        // Own profile (non-admin)
        if (editorId == targetId) return SELF_EDITABLE_FIELDS

        return when (editorRole) {
            "Administrator" -> ALL_FIELDS
            "Manager"       -> if (targetRole in setOf("Employee", "Intern", "Team Lead"))
                MANAGER_EDITABLE_FIELDS else emptySet()
            // HR can only view Manager profiles — no field edits
            "HR"            -> if (targetRole in setOf("Employee", "Intern", "Team Lead"))
                MANAGER_EDITABLE_FIELDS else emptySet()
            else            -> emptySet()
        }
    }

    // ── Role management ───────────────────────────────────────────────────────

    /**
     * Can [editorRole] change the role of a user currently in [targetRole]?
     * Managers cannot promote anyone to Manager or above.
     * HR cannot change roles at all.
     */
    fun canChangeRole(
        editorRole: String,
        targetRole: String,
        newRole   : String,
        isDbAdmin : Boolean = false
    ): Boolean {
        if (isDbAdmin) return true
        return when (editorRole) {
            "Administrator" -> true
            "Manager"       -> targetRole in setOf("Employee", "Intern", "Team Lead") &&
                    newRole    in setOf("Employee", "Intern", "Team Lead")
            else            -> false
        }
    }

    /**
     * Can [editorRole] move a user from [sourceDept] to [targetDept]?
     * Managers can move users within their own department hierarchy.
     * HR cannot move users across departments.
     */
    fun canMoveDepartment(
        editorRole: String,
        targetRole: String,
        isDbAdmin : Boolean = false
    ): Boolean {
        if (isDbAdmin) return true
        return when (editorRole) {
            "Administrator" -> true
            "Manager"       -> targetRole in setOf("Employee", "Intern", "Team Lead")
            else            -> false
        }
    }

    // ── Company scope ─────────────────────────────────────────────────────────

    /**
     * Can [userId] with [role] see/manage users in [targetCompany]?
     * DB admin can see all companies. Others only see their own.
     */
    fun canAccessCompany(
        userCompany   : String,
        targetCompany : String,
        isDbAdmin     : Boolean = false
    ): Boolean = isDbAdmin || userCompany == targetCompany

    // ── Database Manager access ───────────────────────────────────────────────

    /**
     * Only the PocketBase DB admin (matched by email at login) OR users
     * with "database_manager" permission can access the Database Manager.
     */
    fun canAccessDatabaseManager(
        permissions: List<String>,
        isDbAdmin  : Boolean = false
    ): Boolean = isDbAdmin || "database_manager" in permissions

    // ── Admin panel access ────────────────────────────────────────────────────

    fun canAccessAdminPanel(role: String, isDbAdmin: Boolean = false): Boolean =
        isDbAdmin || role in setOf("Administrator", "Manager", "HR")

    // ── Field sets ────────────────────────────────────────────────────────────

    val ALL_FIELDS = setOf(
        "name", "phoneNumber", "designation", "role", "department", "companyName",
        "address", "employeeId", "reportingTo", "salary", "experience",
        "emergencyContactName", "emergencyContactPhone", "emergencyContactRelation",
        "imageUrl"
    )

    /** Fields a user can edit on their OWN profile */
    val SELF_EDITABLE_FIELDS = setOf(
        "address", "experience",
        "emergencyContactName", "emergencyContactPhone", "emergencyContactRelation",
        "imageUrl", "phoneNumber"
    )

    /** Fields Manager/HR can edit on subordinate profiles */
    val MANAGER_EDITABLE_FIELDS = setOf(
        "designation", "department", "experience",
        "emergencyContactName", "emergencyContactPhone", "emergencyContactRelation"
    )
}