package com.saini.ritik.administrator.reports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.ConnectivityMonitor
import com.saini.ritik.core.SyncManager
import com.saini.ritik.data.source.AppDataSource
import com.saini.ritik.localdatabase.AppDatabase
import com.saini.ritik.localdatabase.UserEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ReportSection(
    val title : String,
    val rows  : List<Map<String, String>>
)

data class ReportsUiState(
    val isLoading      : Boolean              = true,
    val isDbAdmin      : Boolean              = false,
    val companyName    : String               = "",
    val sections       : List<ReportSection>  = emptyList(),
    val selectedSection: Int                  = 0,
    val totalUsers     : Int                  = 0,
    val activeUsers    : Int                  = 0,
    val totalDepts     : Int                  = 0,
    val totalRoles     : Int                  = 0,
    val error          : String?              = null,
    val isExporting    : Boolean              = false,
    val lastExportUri  : Uri?                 = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataSource    : AppDataSource,
    private val db            : AppDatabase,
    private val syncManager   : SyncManager,
    private val monitor       : ConnectivityMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    var pendingExport: ExportFormat? = null

    private var sanitizedCompany = ""
    private var users            = emptyList<UserEntity>()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val session  = authRepository.getSession() ?: error("Not logged in")
                val isDbAdmin = authRepository.isDbAdmin()
                val profile  = dataSource.getUserProfile(session.userId).getOrThrow()
                sanitizedCompany = profile.sanitizedCompany

                if (monitor.serverReachable.value) {
                    syncManager.refreshCompanyData(sanitizedCompany)
                }

                users = if (isDbAdmin) db.userDao().getAll()
                else db.userDao().getByCompany(sanitizedCompany)

                val depts = db.deptDao().getByCompany(sanitizedCompany)
                val roles = db.roleDao().getByCompany(sanitizedCompany)

                val sections = buildSections(users, depts.map { it.name }, roles.map { it.name })

                _state.update { it.copy(
                    isLoading    = false,
                    isDbAdmin    = isDbAdmin,
                    companyName  = profile.companyName,
                    sections     = sections,
                    totalUsers   = users.size,
                    activeUsers  = users.count { u -> u.isActive },
                    totalDepts   = depts.size,
                    totalRoles   = roles.size
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun selectSection(idx: Int) = _state.update { it.copy(selectedSection = idx) }

    // ── Export ────────────────────────────────────────────────────────────────

    fun export(format: ExportFormat, onDone: (String, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true) }
            try {
                val section  = _state.value.sections.getOrNull(_state.value.selectedSection)
                    ?: return@launch
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date())
                val filename  = "IT_Connect_${section.title.replace(" ", "_")}_$timestamp.${format.ext}"

                val content = when (format) {
                    ExportFormat.CSV  -> buildCsv(section)
                    ExportFormat.JSON -> buildJson(section)
                    ExportFormat.TXT  -> buildTxt(section)
                }

                _state.update { it.copy(isExporting = false) }
                onDone(content, filename)
            } catch (e: Exception) {
                _state.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }

    // ── Report builders ───────────────────────────────────────────────────────

    private fun buildSections(
        users: List<UserEntity>,
        depts: List<String>,
        roles: List<String>
    ): List<ReportSection> = listOf(

        // 1. User Directory
        ReportSection("User Directory", users.map { u -> mapOf(
            "Name"        to u.name,
            "Email"       to u.email,
            "Role"        to u.role,
            "Department"  to u.department,
            "Designation" to u.designation,
            "Company"     to u.companyName,
            "Status"      to if (u.isActive) "Active" else "Inactive",
            "Experience"  to if (u.experience > 0) "${u.experience} yrs" else "—",
            "Employee ID" to u.employeeId.ifBlank { "—" }
        ) }),

        // 2. Department Summary
        ReportSection("Department Summary", depts.map { dept ->
            val inDept  = users.filter { it.department == dept }
            mapOf(
                "Department"   to dept,
                "Total Users"  to inDept.size.toString(),
                "Active Users" to inDept.count { it.isActive }.toString(),
                "Inactive"     to inDept.count { !it.isActive }.toString(),
                "Roles Present" to inDept.map { it.role }.distinct().joinToString(", ")
            )
        }),

        // 3. Role Distribution
        ReportSection("Role Distribution", roles.map { role ->
            val inRole = users.filter { it.role == role }
            mapOf(
                "Role"        to role,
                "Count"       to inRole.size.toString(),
                "Active"      to inRole.count { it.isActive }.toString(),
                "Departments" to inRole.map { it.department }.distinct().joinToString(", ")
            )
        }),

        // 4. Activity Stats
        ReportSection("Activity Stats", users.map { u -> mapOf(
            "Name"              to u.name,
            "Role"              to u.role,
            "Completed Projects" to u.completedProjects.toString(),
            "Active Projects"    to u.activeProjects.toString(),
            "Pending Tasks"      to u.pendingTasks.toString(),
            "Completed Tasks"    to u.completedTasks.toString(),
            "Total Complaints"   to u.totalComplaints.toString(),
            "Resolved"           to u.resolvedComplaints.toString()
        ) }),

        // 5. Inactive Users
        ReportSection("Inactive Users", users.filter { !it.isActive }.map { u -> mapOf(
            "Name"        to u.name,
            "Email"       to u.email,
            "Role"        to u.role,
            "Department"  to u.department,
            "Company"     to u.companyName
        ) })
    )

    private fun buildCsv(section: ReportSection): String {
        if (section.rows.isEmpty()) return "No data"
        val headers = section.rows.first().keys
        val sb = StringBuilder()
        sb.appendLine(headers.joinToString(",") { "\"$it\"" })
        section.rows.forEach { row ->
            sb.appendLine(headers.joinToString(",") { k -> "\"${row[k]?.replace("\"", "\"\"") ?: ""}\"" })
        }
        return sb.toString()
    }

    private fun buildJson(section: ReportSection): String {
        val arr = JSONArray()
        section.rows.forEach { row ->
            arr.put(JSONObject(row as Map<*, *>))
        }
        return JSONObject().apply {
            put("report",    section.title)
            put("generated", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            put("count",     section.rows.size)
            put("data",      arr)
        }.toString(2)
    }

    private fun buildTxt(section: ReportSection): String {
        if (section.rows.isEmpty()) return "No data"
        val sb = StringBuilder()
        sb.appendLine("=" .repeat(60))
        sb.appendLine("Report: ${section.title}")
        sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("Records: ${section.rows.size}")
        sb.appendLine("=" .repeat(60))
        section.rows.forEachIndexed { i, row ->
            sb.appendLine("\n[${i + 1}]")
            row.forEach { (k, v) -> sb.appendLine("  $k: $v") }
        }
        return sb.toString()
    }
}