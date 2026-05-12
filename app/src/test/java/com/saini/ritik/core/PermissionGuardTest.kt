package com.saini.ritik.core

import com.saini.ritik.data.model.Permissions
import org.junit.Assert.*
import org.junit.Test

class PermissionGuardTest {

    // ── Role hierarchy ───────────────────────────────────────────

    @Test
    fun `system admin has highest rank`() {
        assertEquals(0, PermissionGuard.rankOf(Permissions.ROLE_SYSTEM_ADMIN))
    }

    @Test
    fun `admin outranks manager`() {
        assertTrue(PermissionGuard.rankOf(Permissions.ROLE_ADMIN) < PermissionGuard.rankOf(Permissions.ROLE_MANAGER))
    }

    @Test
    fun `unknown role ranks last`() {
        assertEquals(7, PermissionGuard.rankOf("UnknownRole"))
    }

    @Test
    fun `intern has lowest defined rank`() {
        assertEquals(6, PermissionGuard.rankOf(Permissions.ROLE_INTERN))
    }

    // ── canEditProfile ───────────────────────────────────────────

    @Test
    fun `system admin can edit anyone`() {
        assertTrue(PermissionGuard.canEditProfile(
            Permissions.ROLE_SYSTEM_ADMIN, Permissions.ROLE_SYSTEM_ADMIN, "u1", "u2"))
    }

    @Test
    fun `admin can edit employee in own company`() {
        assertTrue(PermissionGuard.canEditProfile(
            Permissions.ROLE_ADMIN, Permissions.ROLE_EMPLOYEE, "u1", "u2"))
    }

    @Test
    fun `admin cannot edit system admin`() {
        assertFalse(PermissionGuard.canEditProfile(
            Permissions.ROLE_ADMIN, Permissions.ROLE_SYSTEM_ADMIN, "u1", "u2"))
    }

    @Test
    fun `manager can edit employee`() {
        assertTrue(PermissionGuard.canEditProfile(
            Permissions.ROLE_MANAGER, Permissions.ROLE_EMPLOYEE, "u1", "u2"))
    }

    @Test
    fun `manager cannot edit admin`() {
        assertFalse(PermissionGuard.canEditProfile(
            Permissions.ROLE_MANAGER, Permissions.ROLE_ADMIN, "u1", "u2"))
    }

    @Test
    fun `employee can edit own profile`() {
        assertTrue(PermissionGuard.canEditProfile(
            Permissions.ROLE_EMPLOYEE, Permissions.ROLE_EMPLOYEE, "u1", "u1"))
    }

    @Test
    fun `employee cannot edit other employee`() {
        assertFalse(PermissionGuard.canEditProfile(
            Permissions.ROLE_EMPLOYEE, Permissions.ROLE_EMPLOYEE, "u1", "u2"))
    }

    @Test
    fun `HR can edit manager`() {
        assertTrue(PermissionGuard.canEditProfile(
            Permissions.ROLE_HR, Permissions.ROLE_MANAGER, "u1", "u2"))
    }

    @Test
    fun `db admin can edit anyone`() {
        assertTrue(PermissionGuard.canEditProfile(
            Permissions.ROLE_EMPLOYEE, Permissions.ROLE_SYSTEM_ADMIN, "u1", "u2", isDbAdmin = true))
    }

    // ── canChangeRole ────────────────────────────────────────────

    @Test
    fun `system admin can change any role`() {
        assertTrue(PermissionGuard.canChangeRole(
            Permissions.ROLE_SYSTEM_ADMIN, Permissions.ROLE_EMPLOYEE, Permissions.ROLE_ADMIN))
    }

    @Test
    fun `admin can change employee to team lead`() {
        assertTrue(PermissionGuard.canChangeRole(
            Permissions.ROLE_ADMIN, Permissions.ROLE_EMPLOYEE, Permissions.ROLE_TEAM_LEAD))
    }

    @Test
    fun `admin cannot assign system admin role`() {
        assertFalse(PermissionGuard.canChangeRole(
            Permissions.ROLE_ADMIN, Permissions.ROLE_EMPLOYEE, Permissions.ROLE_SYSTEM_ADMIN))
    }

    @Test
    fun `manager can change employee to intern`() {
        assertTrue(PermissionGuard.canChangeRole(
            Permissions.ROLE_MANAGER, Permissions.ROLE_EMPLOYEE, Permissions.ROLE_INTERN))
    }

    @Test
    fun `manager cannot promote employee to manager`() {
        assertFalse(PermissionGuard.canChangeRole(
            Permissions.ROLE_MANAGER, Permissions.ROLE_EMPLOYEE, Permissions.ROLE_MANAGER))
    }

    @Test
    fun `HR cannot change roles`() {
        assertFalse(PermissionGuard.canChangeRole(
            Permissions.ROLE_HR, Permissions.ROLE_EMPLOYEE, Permissions.ROLE_INTERN))
    }

    // ── canGrantOrRevokePermission ───────────────────────────────

    @Test
    fun `system admin can grant any permission`() {
        assertTrue(PermissionGuard.canGrantOrRevokePermission(
            Permissions.ROLE_SYSTEM_ADMIN, Permissions.ROLE_ADMIN, "database_manager"))
    }

    @Test
    fun `admin cannot grant system-only permission`() {
        assertFalse(PermissionGuard.canGrantOrRevokePermission(
            Permissions.ROLE_ADMIN, Permissions.ROLE_EMPLOYEE, "database_manager"))
    }

    @Test
    fun `admin can grant regular permission`() {
        assertTrue(PermissionGuard.canGrantOrRevokePermission(
            Permissions.ROLE_ADMIN, Permissions.ROLE_EMPLOYEE, "view_reports"))
    }

    @Test
    fun `manager cannot grant permissions`() {
        assertFalse(PermissionGuard.canGrantOrRevokePermission(
            Permissions.ROLE_MANAGER, Permissions.ROLE_EMPLOYEE, "view_reports"))
    }

    // ── Feature gates ────────────────────────────────────────────

    @Test
    fun `system admin can access database manager`() {
        assertTrue(PermissionGuard.canAccessDatabaseManager(Permissions.ROLE_SYSTEM_ADMIN, emptyList()))
    }

    @Test
    fun `employee with permission can access database manager`() {
        assertTrue(PermissionGuard.canAccessDatabaseManager(Permissions.ROLE_EMPLOYEE, listOf("database_manager")))
    }

    @Test
    fun `employee without permission cannot access database manager`() {
        assertFalse(PermissionGuard.canAccessDatabaseManager(Permissions.ROLE_EMPLOYEE, emptyList()))
    }

    @Test
    fun `admin can access admin panel`() {
        assertTrue(PermissionGuard.canAccessAdminPanel(Permissions.ROLE_ADMIN))
    }

    @Test
    fun `employee cannot access admin panel`() {
        assertFalse(PermissionGuard.canAccessAdminPanel(Permissions.ROLE_EMPLOYEE))
    }

    // ── canAccessCompany ─────────────────────────────────────────

    @Test
    fun `system admin can access any company`() {
        assertTrue(PermissionGuard.canAccessCompany(
            Permissions.ROLE_SYSTEM_ADMIN, "CompanyA", "CompanyB"))
    }

    @Test
    fun `employee can access own company only`() {
        assertTrue(PermissionGuard.canAccessCompany(
            Permissions.ROLE_EMPLOYEE, "CompanyA", "CompanyA"))
        assertFalse(PermissionGuard.canAccessCompany(
            Permissions.ROLE_EMPLOYEE, "CompanyA", "CompanyB"))
    }
}
