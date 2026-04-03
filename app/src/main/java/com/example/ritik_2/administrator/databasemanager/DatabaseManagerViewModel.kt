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
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private var allRecords: List<DBRecord> = emptyList()

    private fun token(): String = authRepository.getSession()?.token ?: ""

    init { loadCurrentUser() }

    // ── Bootstrap ─────────────────────────────────────────────────────────────

    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                val session = authRepository.getSession() ?: return@launch
                val profile = dataSource.getUserProfile(session.userId).getOrNull() ?: return@launch
                _state.update { it.copy(adminCompany = profile.sanitizedCompany) }
                loadTab(DBTab.USERS)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun switchTab(tab: DBTab) {
        _state.update { it.copy(currentTab = tab, searchQuery = "") }
        loadTab(tab)
    }

    fun search(q: String) {
        _state.update { it.copy(searchQuery = q) }
        val filtered: List<DBRecord> = if (q.isBlank()) allRecords
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
                    DBTab.DEPARTMENTS -> "departments_metadata"
                    DBTab.COMPANIES   -> "companies_metadata"
                    DBTab.COLLECTIONS -> return@launch   // read-only tab
                }
                pbDelete(
                    "${AppConfig.BASE_URL}/api/collections/$col/records/${record.id}",
                    token()
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
                _state.update { it.copy(
                    isLoading  = false,
                    records    = records,
                    totalCount = records.size,
                    currentTab = tab
                )}
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Fetch helpers ─────────────────────────────────────────────────────────

    private suspend fun fetchUsers(): List<DBRecord> {
        val sc    = _state.value.adminCompany
        val res   = pbGet(
            "${AppConfig.BASE_URL}/api/collections/users/records" +
                    "?filter=(sanitizedCompanyName='$sc')&perPage=200&sort=-created",
            token()
        )
        val items = JSONObject(res).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            DBRecord(
                id = o.optString("id"),
                title = o.optString("name"),
                sub1 = o.optString("email"),
                sub2 = "${o.optString("role")} · ${o.optString("department")}",
                badge = if (o.optBoolean("isActive", true)) "Active" else "Inactive",
                extra = mapOf(
                    "Designation" to o.optString("designation"),
                    "NeedsCompletion" to o.optBoolean("needsProfileCompletion").toString()
                )
            )
        }
    }

    private suspend fun fetchDepartments(): List<DBRecord> {
        val sc    = _state.value.adminCompany
        val res   = pbGet(
            "${AppConfig.BASE_URL}/api/collections/departments_metadata/records" +
                    "?filter=(sanitizedCompanyName='$sc')&perPage=100",
            token()
        )
        val items = JSONObject(res).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            DBRecord(
                id = o.optString("id"),
                title = o.optString("departmentName"),
                sub1 = "${o.optInt("userCount")} users",
                sub2 = "${o.optInt("activeUsers")} active",
                badge = o.optString("sanitizedName")
            )
        }
    }

    private suspend fun fetchCompanies(): List<DBRecord> {
        val res   = pbGet(
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records?perPage=100",
            token()
        )
        val items = JSONObject(res).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            DBRecord(
                id = o.optString("id"),
                title = o.optString("originalName"),
                sub1 = "${o.optInt("totalUsers")} total users",
                sub2 = "${o.optInt("activeUsers")} active",
                badge = o.optString("sanitizedName")
            )
        }
    }

    private suspend fun fetchCollections(): List<DBRecord> {
        val res   = pbGet("${AppConfig.BASE_URL}/api/collections", token())
        val items = JSONObject(res).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            DBRecord(
                id = o.optString("id"),
                title = o.optString("name"),
                sub1 = "type: ${o.optString("type")}",
                sub2 = "${o.optJSONArray("schema")?.length() ?: 0} fields",
                badge = o.optString("id").take(8)
            )
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private suspend fun pbGet(url: String, token: String): String =
        withContext(Dispatchers.IO) {
            val res = http.newCall(
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