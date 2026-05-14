package com.saini.ritik.administrator

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.saini.ritik.administrator.companysettings.CompanySettingsActivity
import com.saini.ritik.administrator.databasemanager.DatabaseManagerActivity
import com.saini.ritik.administrator.departmentmanager.DepartmentActivity
import com.saini.ritik.administrator.manageuser.ManageUserActivity
import com.saini.ritik.administrator.newusercreation.CreateUserActivity
import com.saini.ritik.administrator.reports.ReportsActivity
import com.saini.ritik.administrator.rolemanagement.RoleManagementActivity
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.ConnectivityMonitor
import com.saini.ritik.core.PermissionGuard
import com.saini.ritik.data.model.Permissions
import com.saini.ritik.data.source.AppDataSource
import com.saini.ritik.localdatabase.AppDatabase
import com.saini.ritik.theme.Ritik_2Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private val ALLOWED_ROLES = setOf(
    Permissions.ROLE_SYSTEM_ADMIN,
    Permissions.ROLE_ADMIN,
    Permissions.ROLE_MANAGER,
    Permissions.ROLE_HR
)

@AndroidEntryPoint
class AdministratorPanelActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var dataSource    : AppDataSource
    @Inject lateinit var db            : AppDatabase
    @Inject lateinit var monitor       : ConnectivityMonitor

    private val adminData         = mutableStateOf<AdminData?>(null)
    private val departmentData    = mutableStateOf<List<DepartmentData>>(emptyList())
    private val organizationStats = mutableStateOf<OrganizationStats?>(null)
    private val isLoading         = mutableStateOf(true)
    private val hasAccess         = mutableStateOf(false)
    private val accessDeniedMsg   = mutableStateOf<String?>(null)

    data class AdminData(
        val userId               : String,
        val name                 : String,
        val email                : String,
        val companyName          : String,
        val sanitizedCompanyName : String,
        val role                 : String,
        val department           : String,
        val permissions          : List<String>,
        val imageUrl             : String = ""
    )

    data class DepartmentData(
        val name     : String,
        val sanitized: String,
        val userCount: Int,
        val roles    : List<String>
    )

    data class OrganizationStats(
        val totalUsers      : Int,
        val totalDepartments: Int,
        val totalRoles      : Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Ritik_2Theme() {
                AdministratorPanelScreen(
                    adminData         = adminData.value,
                    departmentData    = departmentData.value,
                    organizationStats = organizationStats.value,
                    isLoading         = isLoading.value,
                    hasAccess         = hasAccess.value,
                    accessDeniedMsg   = accessDeniedMsg.value,
                    onFunctionClick   = ::handleFunctionClick
                )
            }
        }
        lifecycleScope.launch { verifyAndLoad() }
    }

    private suspend fun verifyAndLoad() {
        try {
            val session   = authRepository.getSession()
            val userId    = session?.userId ?: run { deny("Session not found."); return }
            val isDbAdmin = authRepository.isDbAdmin()

            val cachedUser = withContext(Dispatchers.IO) { db.userDao().getById(userId) }

            val hasRoleAccess = isDbAdmin ||
                    PermissionGuard.canAccessAdminPanel(cachedUser?.role ?: "", isDbAdmin)

            if (cachedUser != null) {
                if (!hasRoleAccess) {
                    deny("Access denied. Only Administrator, Manager and HR can access this panel.")
                    return
                }
                adminData.value = AdminData(
                    userId               = userId,
                    name                 = cachedUser.name,
                    email                = cachedUser.email,
                    companyName          = cachedUser.companyName,
                    sanitizedCompanyName = cachedUser.sanitizedCompanyName,
                    role                 = cachedUser.role,
                    department           = cachedUser.department,
                    permissions          = cachedUser.permissions,
                    imageUrl             = cachedUser.imageUrl
                )
                hasAccess.value = true
                loadDashboardFromCache(cachedUser.sanitizedCompanyName, isDbAdmin)
            }

            if (monitor.serverReachable.value) {
                val profile = dataSource.getUserProfile(userId).getOrNull()
                if (profile != null) {
                    val serverHasAccess = isDbAdmin ||
                            PermissionGuard.canAccessAdminPanel(profile.role, isDbAdmin)
                    if (!serverHasAccess) {
                        if (cachedUser == null) { deny("Access denied."); return }
                        deny("Access denied. Your role has changed.")
                        return
                    }
                    adminData.value = AdminData(
                        userId               = userId,
                        name                 = profile.name,
                        email                = profile.email,
                        companyName          = profile.companyName,
                        sanitizedCompanyName = profile.sanitizedCompany,
                        role                 = profile.role,
                        department           = profile.department,
                        permissions          = profile.permissions,
                        imageUrl             = profile.imageUrl
                    )
                    hasAccess.value = true
                    loadDashboardFromCache(profile.sanitizedCompany, isDbAdmin)
                } else if (cachedUser == null) {
                    deny("Could not load your profile."); return
                }
            } else if (cachedUser == null) {
                deny("Server unreachable and no cached data."); return
            }
        } catch (e: Exception) {
            Log.e("AdminPanel", "verifyAndLoad: ${e.message}", e)
            deny("Error loading panel: ${e.message}")
        }
    }

    private suspend fun loadDashboardFromCache(
        sanitizedCompany: String,
        isDbAdmin       : Boolean = false
    ) {
        try {
            val users = if (isDbAdmin) withContext(Dispatchers.IO) { db.userDao().getAll() }
            else withContext(Dispatchers.IO) { db.userDao().getByCompany(sanitizedCompany) }
            val depts = withContext(Dispatchers.IO) { db.deptDao().getByCompany(sanitizedCompany) }
            val roles = withContext(Dispatchers.IO) { db.roleDao().getByCompany(sanitizedCompany) }

            departmentData.value = depts.map { d ->
                DepartmentData(
                    name      = d.name,
                    sanitized = d.sanitizedName,
                    userCount = users.count { it.sanitizedDepartment == d.sanitizedName },
                    roles     = users.filter { it.sanitizedDepartment == d.sanitizedName }
                        .map { it.role }.distinct()
                )
            }.sortedByDescending { it.userCount }

            organizationStats.value = OrganizationStats(users.size, depts.size, roles.size)
        } catch (e: Exception) {
            Log.e("AdminPanel", "loadDashboardFromCache: ${e.message}")
        } finally {
            isLoading.value = false
        }
    }

    private fun handleFunctionClick(id: String) {
        val ad        = adminData.value ?: return
        val isDbAdmin = authRepository.isDbAdmin()
        val isSysAdmin = PermissionGuard.isSystemAdmin(ad.role)
        val perms     = ad.permissions

        when (id) {
            "create_user" -> {
                if (Permissions.PERM_CREATE_USER in perms || isDbAdmin)
                    startActivity(Intent(this, CreateUserActivity::class.java))
                else toast("You don't have permission to create users.")
            }
            "manage_users" -> {
                // Always available to panel roles (checked at entry)
                startActivity(Intent(this, ManageUserActivity::class.java))
            }
            "department_mgr" -> {
                startActivity(Intent(this, DepartmentActivity::class.java))
            }
            "role_management" -> {
                if (Permissions.PERM_MANAGE_ROLES in perms || isDbAdmin)
                    startActivity(Intent(this, RoleManagementActivity::class.java))
                else toast("You need the 'manage_roles' permission.")
            }
            "database_manager" -> {
                // Only System_Administrator or DB admin or explicit permission
                val canDb = PermissionGuard.canAccessDatabaseManager(ad.role, perms, isDbAdmin)
                if (canDb)
                    startActivity(Intent(this, DatabaseManagerActivity::class.java))
                else toast("Database Manager requires System_Administrator role or 'database_manager' permission.")
            }
            "company_settings" -> {
                if (Permissions.PERM_MANAGE_COMPANIES in perms || isDbAdmin)
                    startActivity(Intent(this, CompanySettingsActivity::class.java))
                else toast("You need the 'manage_companies' permission.")
            }
            "reports" -> startActivity(Intent(this, ReportsActivity::class.java))
            else      -> toast("Feature coming soon!")
        }
    }

    private fun deny(msg: String) {
        accessDeniedMsg.value = msg; isLoading.value = false
        lifecycleScope.launch {
            kotlinx.coroutines.delay(2500); finish()
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}