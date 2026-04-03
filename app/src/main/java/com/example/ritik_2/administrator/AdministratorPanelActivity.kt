package com.example.ritik_2.administrator

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.administrator.manageuser.ManageUserActivity
import com.example.ritik_2.administrator.newusercreation.CreateUserActivity
import com.example.ritik_2.administrator.rolemanagement.RoleManagementActivity
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

// Roles allowed to enter Administrator Panel
private val ALLOWED_ROLES = setOf("Administrator", "Manager", "HR")

@AndroidEntryPoint
class AdministratorPanelActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var dataSource    : AppDataSource
    @Inject lateinit var http          : OkHttpClient

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
        val name      : String,
        val sanitized : String,
        val userCount : Int,
        val roles     : List<String>
    )

    data class OrganizationStats(
        val totalUsers       : Int,
        val totalDepartments : Int,
        val totalRoles       : Int
    )

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
            val userId  = session?.userId ?: run { deny("Session not found. Please log in again."); return }
            val profile = dataSource.getUserProfile(userId).getOrNull()
                ?: run { deny("Could not load your profile."); return }

            // ── Role gate ─────────────────────────────────────────────────────
            if (profile.role !in ALLOWED_ROLES) {
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
            loadDashboard(profile.role, profile.sanitizedCompany)

        } catch (e: Exception) {
            deny("Error loading panel: ${e.message}")
        }
    }

    private suspend fun loadDashboard(role: String, sanitizedCompany: String) {
        try {
            val token  = authRepository.getSession()?.token ?: return
            // Administrator sees all companies; Manager/HR see only their own
            val filter = if (role == "Administrator") ""
            else "?filter=(sanitizedCompanyName='$sanitizedCompany')"

            val deptsRes  = withContext(Dispatchers.IO) {
                pbGet("${AppConfig.BASE_URL}/api/collections/departments_metadata/records$filter", token)
            }
            val deptsJson = JSONObject(deptsRes).optJSONArray("items")

            var totalUsers = 0
            val allRoles   = mutableSetOf<String>()
            val depts      = mutableListOf<DepartmentData>()

            for (i in 0 until (deptsJson?.length() ?: 0)) {
                val d     = deptsJson!!.getJSONObject(i)
                val roles = d.optJSONArray("availableRoles")
                    ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
                allRoles.addAll(roles)
                val uc = d.optInt("userCount", 0)
                totalUsers += uc
                depts.add(DepartmentData(
                    name      = d.optString("departmentName"),
                    sanitized = d.optString("sanitizedName"),
                    userCount = uc,
                    roles     = roles
                ))
            }

            departmentData.value    = depts.sortedByDescending { it.userCount }
            organizationStats.value = OrganizationStats(totalUsers, depts.size, allRoles.size)

        } catch (e: Exception) {
            Log.e("AdminPanel", "loadDashboard: ${e.message}", e)
        } finally {
            isLoading.value = false
        }
    }

    private fun handleFunctionClick(id: String) {
        val ad = adminData.value ?: return

        when (id) {
            "create_user" -> {
                // Administrator and HR can create users
                if (ad.role in setOf("Administrator", "HR")) {
                    startActivity(Intent(this, CreateUserActivity::class.java))
                } else {
                    toast("Only Administrator and HR can create users.")
                }
            }

            "manage_users" -> {
                // All three allowed roles can manage users
                startActivity(Intent(this, ManageUserActivity::class.java))
            }

            "role_management" -> {
                // Only Administrator can change roles
                if (ad.role == "Administrator") {
                    startActivity(Intent(this, RoleManagementActivity::class.java))
                } else {
                    toast("Only Administrator can manage roles.")
                }
            }

            // database_manager intentionally removed

            else -> toast("Feature coming soon!")
        }
    }

    private fun deny(msg: String) {
        accessDeniedMsg.value = msg
        isLoading.value       = false
        lifecycleScope.launch { delay(2500); finish() }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun pbGet(url: String, token: String): String {
        val res = http.newCall(
            Request.Builder().url(url).get()
                .addHeader("Authorization", "Bearer $token").build()
        ).execute()
        val b = res.body?.string() ?: "{}"; res.close(); return b
    }
}