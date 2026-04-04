package com.example.ritik_2.administrator

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.administrator.databasemanager.DatabaseManagerActivity
import com.example.ritik_2.administrator.departmentmanager.DepartmentActivity
import com.example.ritik_2.administrator.manageuser.ManageUserActivity
import com.example.ritik_2.administrator.newusercreation.CreateUserActivity
import com.example.ritik_2.administrator.rolemanagement.RoleManagementActivity
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.ConnectivityMonitor
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.localdatabase.AppDatabase
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private val ALLOWED_ROLES = setOf("Administrator", "Manager", "HR")

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

    data class DepartmentData(val name: String, val sanitized: String,
                              val userCount: Int, val roles: List<String>)

    data class OrganizationStats(val totalUsers: Int, val totalDepartments: Int, val totalRoles: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ITConnectTheme {
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
            val session = authRepository.getSession()
            val userId  = session?.userId ?: run { deny("Session not found."); return }

            // ── Try local cache first — instant, no network needed ────────────
            val cachedUser = withContext(Dispatchers.IO) { db.userDao().getById(userId) }

            if (cachedUser != null) {
                // Show UI immediately from cache
                if (cachedUser.role !in ALLOWED_ROLES) {
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
                loadDashboardFromCache(cachedUser.sanitizedCompanyName)
            }

            // ── If server reachable, refresh in background ────────────────────
            if (monitor.serverReachable.value) {
                val profile = dataSource.getUserProfile(userId).getOrNull()
                if (profile != null) {
                    if (profile.role !in ALLOWED_ROLES) {
                        if (cachedUser == null) {
                            deny("Access denied.")
                            return
                        }
                        // Role changed on server — deny
                        deny("Access denied. Only Administrator, Manager and HR can access this panel.")
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
                    loadDashboardFromCache(profile.sanitizedCompany)
                } else if (cachedUser == null) {
                    deny("Could not load your profile.")
                    return
                }
            } else if (cachedUser == null) {
                // No cache, no network
                deny("Server unreachable and no cached data. Connect to the server first.")
                return
            }

        } catch (e: Exception) {
            Log.e("AdminPanel", "verifyAndLoad: ${e.message}", e)
            deny("Error loading panel: ${e.message}")
        }
    }

    private suspend fun loadDashboardFromCache(sanitizedCompany: String) {
        try {
            val depts = withContext(Dispatchers.IO) {
                db.deptDao().getByCompany(sanitizedCompany)
            }
            val users = withContext(Dispatchers.IO) {
                db.userDao().getByCompany(sanitizedCompany)
            }
            val roles = withContext(Dispatchers.IO) {
                db.roleDao().getByCompany(sanitizedCompany)
            }

            val deptData = depts.map { d ->
                DepartmentData(
                    name      = d.name,
                    sanitized = d.sanitizedName,
                    userCount = users.count { it.sanitizedDepartment == d.sanitizedName },
                    roles     = users.filter { it.sanitizedDepartment == d.sanitizedName }
                        .map { it.role }.distinct()
                )
            }
            departmentData.value    = deptData.sortedByDescending { it.userCount }
            organizationStats.value = OrganizationStats(users.size, depts.size, roles.size)
        } catch (e: Exception) {
            Log.e("AdminPanel", "loadDashboardFromCache: ${e.message}")
        } finally {
            isLoading.value = false
        }
    }

    private fun handleFunctionClick(id: String) {
        val ad = adminData.value ?: return
        when (id) {
            "create_user"      -> {
                if (ad.role in setOf("Administrator", "HR", "Manager"))
                    startActivity(Intent(this, CreateUserActivity::class.java))
                else toast("Only Administrator, HR and Manager can create users.")
            }
            "manage_users"     -> startActivity(Intent(this, ManageUserActivity::class.java))
            "department_mgr"   -> startActivity(Intent(this, DepartmentActivity::class.java))
            "role_management"  -> {
                if (ad.role == "Administrator")
                    startActivity(Intent(this, RoleManagementActivity::class.java))
                else toast("Only Administrator can manage roles.")
            }
            "database_manager" -> {
                if (ad.role == "Administrator")
                    startActivity(Intent(this, DatabaseManagerActivity::class.java))
                else toast("Only Administrator can access the database manager.")
            }
            else -> toast("Feature coming soon!")
        }
    }

    private fun deny(msg: String) {
        accessDeniedMsg.value = msg; isLoading.value = false
        lifecycleScope.launch { delay(2500); finish() }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}