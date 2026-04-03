package com.example.ritik_2.administrator.databasemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.administrator.databasemanager.models.DBRecord
import com.example.ritik_2.administrator.databasemanager.models.DBTab
import com.example.ritik_2.administrator.databasemanager.models.DBUiState
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.data.source.AppDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class DatabaseManagerViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val authRepository: AuthRepository,
    private val http          : OkHttpClient
) : ViewModel() {

    private val _state = MutableStateFlow(DBUiState())
    val state: StateFlow<DBUiState> = _state.asStateFlow()

    private var allRecords  : List<DBRecord> = emptyList()
    private var adminToken  : String         = ""
    private var currentRole : String         = ""
    private var adminCompany: String         = ""

    init { loadCurrentUser() }

    // ── Bootstrap ─────────────────────────────────────────────────────────────

    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                val session = authRepository.getSession() ?: return@launch
                val profile = dataSource.getUserProfile(session.userId).getOrNull()
                    ?: return@launch
                currentRole  = profile.role
                adminCompany = profile.sanitizedCompany

                // Get fresh admin token for collections endpoint
                adminToken = getAdminToken() ?: session.token ?: ""

                _state.update { it.copy(adminCompany = profile.companyName) }
                loadTab(DBTab.USERS)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    // Fresh admin token — needed for /api/collections endpoint
    private suspend fun getAdminToken(): String? = withContext(Dispatchers.IO) {
        try {
            val body = org.json.JSONObject().apply {
                put("identity", AppConfig.ADMIN_EMAIL)
                put("password", AppConfig.ADMIN_PASS)
            }.toString().toRequestBody("application/json".toMediaType())
            val res     = http.newCall(
                Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/_superusers/auth-with-password")
                    .post(body).build()
            ).execute()
            val resBody = res.body?.string() ?: ""
            res.close()
            if (res.isSuccessful) JSONObject(resBody).optString("token").ifEmpty { null }
            else null
        } catch (_: Exception) { null }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun switchTab(tab: DBTab) {
        _state.update { it.copy(currentTab = tab, searchQuery = "") }
        loadTab(tab)
    }

    fun search(q: String) {
        _state.update { it.copy(searchQuery = q) }
        val filtered = if (q.isBlank()) allRecords
        else allRecords.filter { rec ->
            rec.title.contains(q, ignoreCase = true) ||
                    rec.sub1.contains(q,  ignoreCase = true) ||
                    rec.sub2.contains(q,  ignoreCase = true)
        }
        _state.update { it.copy(records = filtered) }
    }

    fun deleteRecord(record: DBRecord) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val col = when (_state.value.currentTab) {
                    DBTab.USERS       -> "users"
                    DBTab.DEPARTMENTS -> "companies_metadata"  // no dept collection, map to companies
                    DBTab.COMPANIES   -> "companies_metadata"
                    DBTab.COLLECTIONS -> return@launch
                }
                pbDelete(
                    "${AppConfig.BASE_URL}/api/collections/$col/records/${record.id}",
                    adminToken
                )
                _state.update { it.copy(successMsg = "${record.title} deleted") }
                loadTab(_state.value.currentTab)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refresh() = loadTab(_state.value.currentTab)
    fun clearMessages() = _state.update { it.copy(error = null, successMsg = null) }

    // ── Private: load per tab ─────────────────────────────────────────────────

    private fun loadTab(tab: DBTab) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val records: List<DBRecord> = when (tab) {
                    DBTab.USERS       -> fetchUsers()
                    DBTab.DEPARTMENTS -> fetchDepartments()
                    DBTab.COMPANIES   -> fetchCompanies()
                    DBTab.COLLECTIONS -> fetchCollections()
                }
                allRecords = records
                _state.update {
                    it.copy(
                        isLoading  = false,
                        records    = records,
                        totalCount = records.size,
                        currentTab = tab
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Fetch helpers ─────────────────────────────────────────────────────────

    private suspend fun fetchUsers(): List<DBRecord> {
        // Administrator sees all users, Manager/HR see only their company
        val filter = if (currentRole == "Administrator") ""
        else "?filter=(sanitizedCompanyName='$adminCompany')&perPage=200&sort=-created"

        val url = if (filter.isEmpty())
            "${AppConfig.BASE_URL}/api/collections/users/records?perPage=200&sort=-created"
        else
            "${AppConfig.BASE_URL}/api/collections/users/records$filter"

        val res   = pbGet(url, adminToken)
        val items = JSONObject(res).optJSONArray("items") ?: return emptyList()

        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            DBRecord(
                id    = o.optString("id"),
                title = o.optString("name").ifBlank { o.optString("email") },
                sub1  = o.optString("email"),
                sub2  = "${o.optString("role")} · ${o.optString("department")}",
                badge = if (o.optBoolean("isActive", true)) "Active" else "Inactive",
                extra = mapOf(
                    "Company"     to o.optString("companyName"),
                    "Designation" to o.optString("designation"),
                    "ID"          to o.optString("id")
                )
            )
        }
    }

    private suspend fun fetchDepartments(): List<DBRecord> {
        // Read departments array from companies_metadata
        val url = if (currentRole == "Administrator")
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records?perPage=100"
        else
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records" +
                    "?filter=(sanitizedName='$adminCompany')&perPage=100"

        val res   = pbGet(url, adminToken)
        val items = JSONObject(res).optJSONArray("items") ?: return emptyList()

        val result = mutableListOf<DBRecord>()
        for (i in 0 until items.length()) {
            val company     = items.getJSONObject(i)
            val companyName = company.optString("originalName")
            val sc          = company.optString("sanitizedName")
            val depts = try {
                org.json.JSONArray(company.optString("departments", "[]"))
            } catch (_: Exception) { org.json.JSONArray() }

            for (j in 0 until depts.length()) {
                val dept = depts.optString(j)
                // Count users in this dept
                val usersRes   = pbGet(
                    "${AppConfig.BASE_URL}/api/collections/users/records" +
                            "?filter=(sanitizedCompanyName='$sc'&&department='$dept')&perPage=1",
                    adminToken
                )
                val totalUsers = JSONObject(usersRes).optInt("totalItems", 0)

                result.add(DBRecord(
                    id    = "${sc}_$dept",
                    title = dept,
                    sub1  = companyName,
                    sub2  = "$totalUsers users",
                    badge = sc,
                    extra = mapOf("Company" to companyName)
                ))
            }
        }
        return result
    }

    private suspend fun fetchCompanies(): List<DBRecord> {
        val res   = pbGet(
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records?perPage=100",
            adminToken
        )
        val items = JSONObject(res).optJSONArray("items") ?: return emptyList()

        return (0 until items.length()).map { i ->
            val o     = items.getJSONObject(i)
            val roles = try {
                org.json.JSONArray(o.optString("availableRoles", "[]"))
                    .let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                    .joinToString(", ")
            } catch (_: Exception) { "" }

            DBRecord(
                id    = o.optString("id"),
                title = o.optString("originalName"),
                sub1  = "${o.optInt("totalUsers")} total · ${o.optInt("activeUsers")} active",
                sub2  = o.optString("sanitizedName"),
                badge = "${o.optInt("totalUsers")} users",
                extra = mapOf(
                    "Roles"          to roles,
                    "Sanitized Name" to o.optString("sanitizedName")
                )
            )
        }
    }

    private suspend fun fetchCollections(): List<DBRecord> {
        // Needs admin token — /api/collections lists all collections
        val res   = pbGet("${AppConfig.BASE_URL}/api/collections?perPage=100", adminToken)
        val items = JSONObject(res).optJSONArray("items") ?: return emptyList()

        return (0 until items.length()).map { i ->
            val o      = items.getJSONObject(i)
            val schema = o.optJSONArray("schema")
            val fields = schema?.length() ?: 0
            DBRecord(
                id    = o.optString("id"),
                title = o.optString("name"),
                sub1  = "Type: ${o.optString("type")}",
                sub2  = "$fields fields",
                badge = o.optString("type"),
                extra = mapOf(
                    "Fields"      to fields.toString(),
                    "List Rule"   to (o.optString("listRule").ifBlank { "public" }),
                    "Create Rule" to (o.optString("createRule").ifBlank { "public" }),
                    "Full ID"     to o.optString("id")
                )
            )
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private suspend fun pbGet(url: String, token: String): String =
        withContext(Dispatchers.IO) {
            val res  = http.newCall(
                Request.Builder().url(url).get()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            ).execute()
            val body = res.body?.string() ?: "{}"
            res.close()
            body
        }

    private suspend fun pbDelete(url: String, token: String) =
        withContext(Dispatchers.IO) {
            http.newCall(
                Request.Builder().url(url).delete()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            ).execute().close()
        }
}