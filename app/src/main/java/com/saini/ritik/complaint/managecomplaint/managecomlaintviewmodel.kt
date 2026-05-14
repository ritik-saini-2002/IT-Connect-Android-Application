package com.saini.ritik.complaint.managecomplaint

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.AdminTokenProvider
import com.saini.ritik.core.AppConfig
import com.saini.ritik.core.PermissionGuard
import com.saini.ritik.data.model.Permissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

// ── Complaint data model ──────────────────────────────────────────────────────
data class ComplaintItem(
    val id              : String = "",
    val ticketId        : String = "",
    val title           : String = "",
    val description     : String = "",
    val category        : String = "",
    val priority        : String = "Medium",
    val status          : String = "Open",
    val raisedBy        : String = "",
    val raisedByName    : String = "",
    val raisedByEmail   : String = "",
    val raisedByRole    : String = "",
    val raisedByDepartment: String = "",
    val assignedTo      : String = "",
    val assignedToName  : String = "",
    val companyName     : String = "",
    val department      : String = "",
    val resolution      : String = "",
    val comments        : List<CommentItem> = emptyList(),
    val attachments     : List<String> = emptyList(),
    val created         : String = "",
    val updated         : String = "",
    val collectionId    : String = ""
)

data class CommentItem(
    val userId   : String = "",
    val userName : String = "",
    val comment  : String = "",
    val timestamp: String = ""
)

// ── Assignable user model ─────────────────────────────────────────────────────
data class AssignableUser(
    val id         : String = "",
    val name       : String = "",
    val email      : String = "",
    val role       : String = "",
    val department : String = ""
)

// ── UI state ──────────────────────────────────────────────────────────────────
data class ManageComplaintUiState(
    val complaints       : List<ComplaintItem> = emptyList(),
    val filteredComplaints: List<ComplaintItem> = emptyList(),
    val selectedComplaint: ComplaintItem? = null,
    val isLoading        : Boolean = true,
    val error            : String? = null,
    val filter           : String = "All",        // All, Open, In Progress, Resolved, Closed
    val searchQuery      : String = "",
    val assignableUsers  : List<AssignableUser> = emptyList(),

    // Current user permissions context
    val currentUserId    : String = "",
    val currentUserRole  : String = "",
    val currentPermissions: List<String> = emptyList(),
    val canRaise         : Boolean = false,
    val canDelete        : Boolean = false,
    val canAssign        : Boolean = false,
    val canResolve       : Boolean = false,
    val canEdit          : Boolean = false,
    val canViewAll       : Boolean = false
)

private const val COL_COMPLAINTS = "complaints"
private const val COL_USERS      = "users"
private const val COL_ACCESS_CTRL = "user_access_control"

@HiltViewModel
class ManageComplaintViewModel @Inject constructor(
    private val http              : OkHttpClient,
    private val authRepository    : AuthRepository,
    private val adminTokenProvider: AdminTokenProvider
) : ViewModel() {

    companion object { private const val TAG = "ManageComplaintVM" }

    private val _uiState = MutableStateFlow(ManageComplaintUiState())
    val uiState: StateFlow<ManageComplaintUiState> = _uiState.asStateFlow()

    init {
        computePermissions()
        loadComplaints()
        loadAssignableUsers()
    }

    fun reload() = loadComplaints()

    fun onFilterChange(filter: String) {
        _uiState.value = _uiState.value.copy(filter = filter)
        applyFilters()
    }

    fun onSearchChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    // ── Permission computation ────────────────────────────────────────────────
    private fun computePermissions() {
        val session = authRepository.getSession() ?: return
        val role   = session.role
        val perms  = session.permissions
        val isSA   = PermissionGuard.isSystemAdmin(role)
        val isAdmin = role == Permissions.ROLE_ADMIN
        val isManager = role == Permissions.ROLE_MANAGER
        val isHR = role == Permissions.ROLE_HR

        _uiState.value = _uiState.value.copy(
            currentUserId    = session.userId,
            currentUserRole  = role,
            currentPermissions = perms,
            canRaise   = isSA || Permissions.PERM_SUBMIT_COMPLAINTS in perms,
            canDelete  = isSA || isAdmin,
            canAssign  = isSA || isAdmin || isManager,
            canResolve = isSA || isAdmin || isManager || isHR || Permissions.PERM_RESOLVE_COMPLAINTS in perms,
            canEdit    = isSA || isAdmin || isManager,
            canViewAll = isSA || isAdmin || isHR || Permissions.PERM_VIEW_ALL_COMPLAINTS in perms
        )
    }

    // ── Load complaints ───────────────────────────────────────────────────────
    private fun loadComplaints() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val session = authRepository.getSession() ?: run {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "No session")
                    return@launch
                }
                val token = try { adminTokenProvider.getAdminTokenSync() } catch (_: Exception) { session.token }
                val sc    = session.documentPath.split("/").getOrNull(1) ?: ""

                // Build filter based on permissions
                val filter = buildComplaintFilter(session.userId, session.role, session.permissions, sc)
                val url = "${AppConfig.BASE_URL}/api/collections/$COL_COMPLAINTS/records" +
                        "?filter=$filter&sort=-created&perPage=200"

                val res = http.newCall(
                    Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $token")
                        .get().build()
                ).execute()
                val body = res.body?.string() ?: ""
                val code = res.code
                val ok   = res.isSuccessful
                res.close()

                if (ok) {
                    val json  = JSONObject(body)
                    val items = json.optJSONArray("items") ?: JSONArray()
                    val list  = (0 until items.length()).map { i ->
                        parseComplaint(items.getJSONObject(i))
                    }
                    _uiState.value = _uiState.value.copy(
                        complaints = list,
                        isLoading  = false,
                        error      = null
                    )
                    applyFilters()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error     = "Failed to load: HTTP $code"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadComplaints: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private fun buildComplaintFilter(userId: String, role: String, perms: List<String>, company: String): String {
        if (PermissionGuard.isSystemAdmin(role)) return ""
        if (role == Permissions.ROLE_ADMIN || Permissions.PERM_VIEW_ALL_COMPLAINTS in perms)
            return "(companyName='$company')"

        val dept = authRepository.getSession()?.documentPath?.split("/")?.getOrNull(2) ?: ""
        if (Permissions.PERM_VIEW_DEPARTMENT_COMPLAINTS in perms)
            return "(companyName='$company'%26%26department='$dept')"
        if (Permissions.PERM_VIEW_TEAM_COMPLAINTS in perms)
            return "(companyName='$company'%26%26(raisedByDepartment='$dept'||assignedTo='$userId'))"

        // Default: own complaints only
        return "(raisedBy='$userId'||assignedTo='$userId')"
    }

    private fun applyFilters() {
        val state = _uiState.value
        var list = state.complaints

        if (state.filter != "All") {
            list = list.filter { it.status.equals(state.filter, ignoreCase = true) }
        }

        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                it.ticketId.lowercase().contains(q) ||
                it.raisedByName.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                it.description.lowercase().contains(q)
            }
        }

        _uiState.value = state.copy(filteredComplaints = list)
    }

    // ── Update status ─────────────────────────────────────────────────────────
    fun updateStatus(complaintId: String, newStatus: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val token = getToken()
                withContext(Dispatchers.IO) {
                    patchComplaint(complaintId, token, JSONObject().apply {
                        put("status", newStatus)
                    })
                }
                loadComplaints()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Update failed") }
            }
        }
    }

    // ── Assign user ───────────────────────────────────────────────────────────
    fun assignUser(complaintId: String, userId: String, userName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val token = getToken()
                withContext(Dispatchers.IO) {
                    patchComplaint(complaintId, token, JSONObject().apply {
                        put("assignedTo", userId)
                        put("assignedToName", userName)
                        if (_uiState.value.complaints.find { it.id == complaintId }?.status == "Open")
                            put("status", "In Progress")
                    })
                }
                loadComplaints()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Assignment failed") }
            }
        }
    }

    // ── Delete complaint ──────────────────────────────────────────────────────
    fun deleteComplaint(complaintId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val state = _uiState.value
        if (!state.canDelete) { onError("Access denied"); return }

        viewModelScope.launch {
            try {
                val token = getToken()
                withContext(Dispatchers.IO) {
                    val res = http.newCall(
                        Request.Builder()
                            .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPLAINTS/records/$complaintId")
                            .addHeader("Authorization", "Bearer $token")
                            .delete()
                            .build()
                    ).execute()
                    res.close()
                    if (!res.isSuccessful) error("Delete failed: HTTP ${res.code}")
                }
                loadComplaints()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Delete failed") }
            }
        }
    }

    // ── Add comment ───────────────────────────────────────────────────────────
    fun addComment(complaintId: String, comment: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (comment.isBlank()) return
        viewModelScope.launch {
            try {
                val session = authRepository.getSession() ?: run { onError("No session"); return@launch }
                val token   = getToken()

                // Get current comments
                val existing = _uiState.value.complaints.find { it.id == complaintId }?.comments ?: emptyList()
                val newComment = CommentItem(
                    userId    = session.userId,
                    userName  = session.name,
                    comment   = comment,
                    timestamp = java.time.Instant.now().toString()
                )
                val allComments = existing + newComment
                val commentsJson = JSONArray().apply {
                    allComments.forEach { c ->
                        put(JSONObject().apply {
                            put("userId", c.userId)
                            put("userName", c.userName)
                            put("comment", c.comment)
                            put("timestamp", c.timestamp)
                        })
                    }
                }

                withContext(Dispatchers.IO) {
                    patchComplaint(complaintId, token, JSONObject().apply {
                        put("comments", commentsJson.toString())
                    })
                }
                loadComplaints()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Comment failed") }
            }
        }
    }

    // ── Update resolution ─────────────────────────────────────────────────────
    fun updateResolution(complaintId: String, resolution: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val token = getToken()
                withContext(Dispatchers.IO) {
                    patchComplaint(complaintId, token, JSONObject().apply {
                        put("resolution", resolution)
                        put("status", "Resolved")
                    })
                }
                loadComplaints()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Resolution failed") }
            }
        }
    }

    // ── Edit complaint ────────────────────────────────────────────────────────
    fun editComplaint(
        complaintId: String, title: String, desc: String,
        priority: String, category: String,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = getToken()
                withContext(Dispatchers.IO) {
                    patchComplaint(complaintId, token, JSONObject().apply {
                        put("title", title)
                        put("description", desc)
                        put("priority", priority)
                        put("category", category)
                    })
                }
                loadComplaints()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Edit failed") }
            }
        }
    }

    // ── Load assignable users (same department / company) ─────────────────────
    private fun loadAssignableUsers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = authRepository.getSession() ?: return@launch
                val token   = try { adminTokenProvider.getAdminTokenSync() } catch (_: Exception) { session.token }
                val sc      = session.documentPath.split("/").getOrNull(1) ?: return@launch

                val res = http.newCall(
                    Request.Builder()
                        .url("${AppConfig.BASE_URL}/api/collections/$COL_ACCESS_CTRL/records" +
                                "?filter=(sanitizedCompanyName='$sc'%26%26isActive=true)&perPage=200")
                        .addHeader("Authorization", "Bearer $token")
                        .get().build()
                ).execute()
                val body = res.body?.string() ?: ""
                val ok   = res.isSuccessful
                res.close()

                if (ok) {
                    val items = JSONObject(body).optJSONArray("items") ?: return@launch
                    val users = (0 until items.length()).map { i ->
                        val item = items.getJSONObject(i)
                        AssignableUser(
                            id         = item.optString("userId"),
                            name       = item.optString("name"),
                            email      = item.optString("email"),
                            role       = item.optString("role"),
                            department = item.optString("department")
                        )
                    }
                    _uiState.value = _uiState.value.copy(assignableUsers = users)
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadAssignableUsers: ${e.message}")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun getToken(): String {
        val session = authRepository.getSession() ?: error("No session")
        return try { adminTokenProvider.getAdminTokenSync() } catch (_: Exception) { session.token }
    }

    private fun patchComplaint(id: String, token: String, data: JSONObject) {
        val res = http.newCall(
            Request.Builder()
                .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPLAINTS/records/$id")
                .addHeader("Authorization", "Bearer $token")
                .patch(data.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        val code = res.code; res.close()
        if (code !in 200..299) error("PATCH failed: HTTP $code")
    }

    private fun parseComplaint(json: JSONObject): ComplaintItem {
        val commentsRaw = json.optString("comments", "[]")
        val comments = try {
            val arr = JSONArray(commentsRaw)
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                CommentItem(
                    userId    = c.optString("userId"),
                    userName  = c.optString("userName"),
                    comment   = c.optString("comment"),
                    timestamp = c.optString("timestamp")
                )
            }
        } catch (_: Exception) { emptyList() }

        val attachmentFiles = mutableListOf<String>()
        val attArr = json.optJSONArray("attachments")
        if (attArr != null) {
            for (i in 0 until attArr.length()) {
                val fname = attArr.optString(i)
                if (fname.isNotBlank()) {
                    val collectionId = json.optString("collectionId")
                    val recordId = json.optString("id")
                    attachmentFiles.add(
                        AppConfig.fileUrl(collectionId, recordId, fname) ?: fname
                    )
                }
            }
        }

        return ComplaintItem(
            id                 = json.optString("id"),
            ticketId           = json.optString("ticketId"),
            title              = json.optString("title"),
            description        = json.optString("description"),
            category           = json.optString("category"),
            priority           = json.optString("priority"),
            status             = json.optString("status"),
            raisedBy           = json.optString("raisedBy"),
            raisedByName       = json.optString("raisedByName"),
            raisedByEmail      = json.optString("raisedByEmail"),
            raisedByRole       = json.optString("raisedByRole"),
            raisedByDepartment = json.optString("raisedByDepartment"),
            assignedTo         = json.optString("assignedTo"),
            assignedToName     = json.optString("assignedToName"),
            companyName        = json.optString("companyName"),
            department         = json.optString("department"),
            resolution         = json.optString("resolution"),
            comments           = comments,
            attachments        = attachmentFiles,
            created            = json.optString("created"),
            updated            = json.optString("updated"),
            collectionId       = json.optString("collectionId")
        )
    }
}
