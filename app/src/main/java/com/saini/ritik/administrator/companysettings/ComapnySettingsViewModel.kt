package com.saini.ritik.administrator.companysettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.AppConfig
import com.saini.ritik.core.ConnectivityMonitor
import com.saini.ritik.core.StringUtils
import com.saini.ritik.core.SyncManager
import com.saini.ritik.data.source.AppDataSource
import com.saini.ritik.localdatabase.AppDatabase
import com.saini.ritik.localdatabase.CompanyEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class CompanyInfo(
    val id              : String,
    val originalName    : String,
    val sanitizedName   : String,
    val totalUsers      : Int,
    val activeUsers     : Int,
    val availableRoles  : List<String>,
    val departments     : List<String>,
    val description     : String = "",
    val website         : String = "",
    val address         : String = "",
    val logoUrl         : String = ""
)

data class CompanySettingsUiState(
    val isLoading        : Boolean       = true,
    val company          : CompanyInfo?  = null,
    val allCompanies     : List<CompanyInfo> = emptyList(),  // DB admin only
    val isDbAdmin        : Boolean       = false,
    val isEditing        : Boolean       = false,
    val error            : String?       = null,
    val successMsg       : String?       = null,
    val isOffline        : Boolean       = false,
    // Merge flow
    val mergeTargets     : List<CompanyInfo> = emptyList(),
    val showMergeDialog  : Boolean       = false,
    val pendingMergeWith : CompanyInfo?  = null,
    val mergeApprovalRequired: Boolean   = false   // needs other company's admin approval
)

@HiltViewModel
class CompanySettingsViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val authRepository: AuthRepository,
    private val db            : AppDatabase,
    private val syncManager   : SyncManager,
    private val monitor       : ConnectivityMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(CompanySettingsUiState())
    val state: StateFlow<CompanySettingsUiState> = _state.asStateFlow()

    private var sanitizedCompany = ""

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val session  = authRepository.getSession() ?: error("Not logged in")
                val isDbAdmin = authRepository.isDbAdmin()
                val profile  = dataSource.getUserProfile(session.userId).getOrThrow()
                sanitizedCompany = profile.sanitizedCompany

                val offline = !monitor.serverReachable.value
                if (!offline) syncManager.refreshCompanyData(sanitizedCompany)

                // Load own company
                val own = loadCompanyFromCache(sanitizedCompany)

                // DB admin: load all companies
                val all = if (isDbAdmin) {
                    db.companyDao().getAll().map { it.toInfo() }
                } else emptyList()

                _state.update { it.copy(
                    isLoading    = false,
                    company      = own,
                    allCompanies = all,
                    isDbAdmin    = isDbAdmin,
                    isOffline    = offline
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadCompanyFromCache(sc: String): CompanyInfo? {
        val entity = db.companyDao().getByName(sc) ?: return null
        return entity.toInfo()
    }

    // ── Rename company ────────────────────────────────────────────────────────

    fun renameCompany(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val newSanitized = StringUtils.sanitize(newName)

                // Check for duplicates before renaming
                val similar = findSimilarCompanies(newSanitized)
                if (similar.isNotEmpty() && similar.any { it.sanitizedName != sanitizedCompany }) {
                    _state.update { it.copy(
                        isLoading      = false,
                        mergeTargets   = similar,
                        showMergeDialog = true
                    ) }
                    return@launch
                }

                applyRename(newName, newSanitized)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun applyRename(newName: String, newSanitized: String) {
        val token = syncManager.getAdminToken()

        // Fetch current company record
        val res  = syncManager.pbGet(
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records" +
                    "?filter=(sanitizedName='$sanitizedCompany')&perPage=1", token)
        val item = JSONObject(res).optJSONArray("items")?.optJSONObject(0)
            ?: error("Company not found")
        val cId  = item.optString("id")

        // Update companies_metadata
        syncManager.pbPatch(
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records/$cId",
            token,
            JSONObject().apply {
                put("originalName",   newName)
                put("sanitizedName",  newSanitized)
            }.toString()
        )

        // Update all users in this company
        val users = db.userDao().getByCompany(sanitizedCompany)
        users.forEach { u ->
            try {
                syncManager.pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/users/records/${u.id}",
                    token,
                    JSONObject().apply {
                        put("companyName",          newName)
                        put("sanitizedCompanyName", newSanitized)
                    }.toString()
                )
                db.userDao().upsert(u.copy(
                    companyName          = newName,
                    sanitizedCompanyName = newSanitized
                ))
            } catch (e: Exception) {
                android.util.Log.w("CompanyVM", "rename user ${u.id}: ${e.message}")
            }
        }

        // Update local company cache
        val cached = db.companyDao().getByName(sanitizedCompany)
        if (cached != null) {
            db.companyDao().upsert(cached.copy(
                sanitizedName = newSanitized,
                originalName  = newName
            ))
        }

        sanitizedCompany = newSanitized
        load()
        _state.update { it.copy(successMsg = "Company renamed to $newName") }
    }

    // ── Update company details ─────────────────────────────────────────────────

    fun updateDetails(website: String, address: String, description: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val token = syncManager.getAdminToken()
                val res   = syncManager.pbGet(
                    "${AppConfig.BASE_URL}/api/collections/companies_metadata/records" +
                            "?filter=(sanitizedName='$sanitizedCompany')&perPage=1", token)
                val item  = JSONObject(res).optJSONArray("items")?.optJSONObject(0)
                    ?: error("Company not found")
                val cId   = item.optString("id")

                syncManager.pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/companies_metadata/records/$cId",
                    token,
                    JSONObject().apply {
                        put("website",     website)
                        put("address",     address)
                        put("description", description)
                    }.toString()
                )
                load()
                _state.update { it.copy(successMsg = "Company details updated") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Merge company ─────────────────────────────────────────────────────────
    // Both companies' Administrators must approve the merge.
    // This records a merge request — actual merge happens when target admin approves.

    fun requestMerge(targetCompany: CompanyInfo) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val token   = syncManager.getAdminToken()
                val session = authRepository.getSession() ?: error("No session")

                // Record merge request in a merge_requests collection
                syncManager.pbPost(
                    "${AppConfig.BASE_URL}/api/collections/merge_requests/records",
                    token,
                    JSONObject().apply {
                        put("requestingCompany",  sanitizedCompany)
                        put("targetCompany",       targetCompany.sanitizedName)
                        put("requestedBy",         session.userId)
                        put("requestedByName",     session.name)
                        put("status",              "pending")
                        put("createdAt",           System.currentTimeMillis())
                    }.toString()
                )

                _state.update { it.copy(
                    isLoading             = false,
                    showMergeDialog       = false,
                    pendingMergeWith      = targetCompany,
                    mergeApprovalRequired = true,
                    successMsg            = "Merge request sent to ${targetCompany.originalName}'s admin. Awaiting approval."
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * DB admin can force-approve a merge without dual approval.
     * Moves all users from [sourceCompany] into [targetCompany].
     */
    fun forceApproveAndMerge(sourceCompany: CompanyInfo, targetCompany: CompanyInfo) {
        if (!_state.value.isDbAdmin) {
            _state.update { it.copy(error = "Only the database admin can force-merge companies") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val token = syncManager.getAdminToken()
                val users = db.userDao().getByCompany(sourceCompany.sanitizedName)

                users.forEach { u ->
                    val newPath = "users/${targetCompany.sanitizedName}/${u.sanitizedDepartment}/${u.role}/${u.id}"
                    syncManager.pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/users/records/${u.id}",
                        token,
                        JSONObject().apply {
                            put("companyName",          targetCompany.originalName)
                            put("sanitizedCompanyName", targetCompany.sanitizedName)
                            put("documentPath",         newPath)
                        }.toString()
                    )
                    db.userDao().upsert(u.copy(
                        companyName          = targetCompany.originalName,
                        sanitizedCompanyName = targetCompany.sanitizedName,
                        documentPath         = newPath
                    ))
                }

                // Delete source company record
                val res  = syncManager.pbGet(
                    "${AppConfig.BASE_URL}/api/collections/companies_metadata/records" +
                            "?filter=(sanitizedName='${sourceCompany.sanitizedName}')&perPage=1",
                    token)
                val cId  = JSONObject(res).optJSONArray("items")?.optJSONObject(0)?.optString("id")
                if (!cId.isNullOrEmpty()) {
                    syncManager.pbDelete(
                        "${AppConfig.BASE_URL}/api/collections/companies_metadata/records/$cId",
                        token)
                }

                load()
                _state.update { it.copy(
                    successMsg = "Merged ${sourceCompany.originalName} → ${targetCompany.originalName}"
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun dismissMergeDialog() = _state.update { it.copy(showMergeDialog = false, mergeTargets = emptyList()) }
    fun setEditing(v: Boolean) = _state.update { it.copy(isEditing = v) }
    fun clearMessages() = _state.update { it.copy(error = null, successMsg = null) }

    private suspend fun findSimilarCompanies(sc: String): List<CompanyInfo> {
        return db.companyDao().getAll()
            .filter { c ->
                c.sanitizedName == sc ||
                        c.sanitizedName.replace("_","").equals(sc.replace("_",""), ignoreCase = true)
            }
            .map { it.toInfo() }
    }

    private fun CompanyEntity.toInfo() = CompanyInfo(
        id             = sanitizedName,
        originalName   = originalName,
        sanitizedName  = sanitizedName,
        totalUsers     = totalUsers,
        activeUsers    = activeUsers,
        availableRoles = availableRoles,
        departments    = departments
    )
}