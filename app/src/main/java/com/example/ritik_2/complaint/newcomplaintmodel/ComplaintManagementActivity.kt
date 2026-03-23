package com.example.ritik_2.complaint.newcomplaintmodel

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.theme.ComplaintAppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ComplaintManagementActivity
 *
 * ROLE-BASED VISIBILITY RULES:
 *   Employee  → sees own complaints + complaints assigned to them ONLY
 *   Team Lead / Manager → sees all dept complaints (non-global) + global assigned to dept + assigned to them
 *   Admin → sees all company complaints
 *
 * CLOSE RULES:
 *   Complaint can be closed by:
 *   - The assigned employee
 *   - Admin (company-wide)
 *   - Manager / Team Leader of the same department
 *
 * DEPARTMENT ISOLATION:
 *   Non-global complaints from another department are never visible to members of a different dept.
 *   Global complaints are visible only when routed/assigned to that department.
 */
class ComplaintManagementActivity : ComponentActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val repo = ComplaintLiveRepository()

    // ── State flows ────────────────────────────────────────────────────────
    private val _myComplaints        = MutableStateFlow<List<LiveComplaint>>(emptyList())
    private val _deptComplaints      = MutableStateFlow<List<LiveComplaint>>(emptyList())
    private val _assignedComplaints  = MutableStateFlow<List<LiveComplaint>>(emptyList())
    private val _notifications       = MutableStateFlow<List<InAppNotification>>(emptyList())
    private val _unreadCount         = MutableStateFlow(0)
    private val _isLoading           = MutableStateFlow(true)
    private val _currentUserInfo     = MutableStateFlow<CurrentUserSession?>(null)
    private val _departmentMembers   = MutableStateFlow<List<DepartmentMember>>(emptyList())
    private val _errorMessage        = MutableStateFlow<String?>(null)
    private val _selectedComplaint   = MutableStateFlow<LiveComplaint?>(null)
    private val _selectedMemberProfile = MutableStateFlow<DepartmentMember?>(null)

    companion object {
        private const val TAG = "ComplaintMgmtActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uid = auth.currentUser?.uid ?: run { finish(); return }

        lifecycleScope.launch { initSession(uid) }

        setContent {
            ComplaintAppTheme {
                val myComplaints       by _myComplaints.collectAsState()
                val deptComplaints     by _deptComplaints.collectAsState()
                val assignedComplaints by _assignedComplaints.collectAsState()
                val notifications      by _notifications.collectAsState()
                val unreadCount        by _unreadCount.collectAsState()
                val isLoading          by _isLoading.collectAsState()
                val session            by _currentUserInfo.collectAsState()
                val deptMembers        by _departmentMembers.collectAsState()
                val error              by _errorMessage.collectAsState()
                val selectedComplaint  by _selectedComplaint.collectAsState()
                val selectedProfile    by _selectedMemberProfile.collectAsState()

                ComplaintManagementScreen(
                    myComplaints        = myComplaints,
                    deptComplaints      = deptComplaints,
                    assignedComplaints  = assignedComplaints,
                    notifications       = notifications,
                    unreadCount         = unreadCount,
                    isLoading           = isLoading,
                    session             = session,
                    departmentMembers   = deptMembers,
                    errorMessage        = error,
                    selectedComplaint   = selectedComplaint,
                    selectedMemberProfile = selectedProfile,
                    onSelectComplaint   = { _selectedComplaint.value = it },
                    onDismissComplaint  = { _selectedComplaint.value = null },
                    onAssignComplaint   = ::assignComplaint,
                    onUpdateStatus      = ::updateStatus,
                    onCloseComplaint    = ::closeComplaint,
                    onTapAssignee       = ::loadMemberProfile,
                    onDismissProfile    = { _selectedMemberProfile.value = null },
                    onNotificationTap   = ::onNotificationTap,
                    onMarkAllRead       = ::markAllRead,
                    onClearError        = { _errorMessage.value = null },
                    onRefresh           = {
                        session?.let { s -> lifecycleScope.launch { refreshData(s) } }
                    },
                    onBackClick         = { finish() }
                )
            }
        }
    }

    // ── Session init ───────────────────────────────────────────────────────

    private suspend fun initSession(uid: String) {
        try {
            _isLoading.value = true
            val doc = firestore.collection("user_access_control").document(uid).get().await()
            if (!doc.exists()) {
                _errorMessage.value = "User profile not found."
                _isLoading.value = false
                return
            }
            val data = doc.data!!
            val session = CurrentUserSession(
                userId               = data["userId"] as? String ?: uid,
                name                 = data["name"] as? String ?: "User",
                email                = data["email"] as? String ?: "",
                role                 = data["role"] as? String ?: "Employee",
                companyName          = data["companyName"] as? String ?: "",
                sanitizedCompanyName = data["sanitizedCompanyName"] as? String ?: "",
                department           = data["department"] as? String ?: "",
                sanitizedDepartment  = data["sanitizedDepartment"] as? String ?: ""
            )
            _currentUserInfo.value = session
            Log.d(TAG, "Session: ${session.name} / ${session.role} / dept=${session.department}")

            startStreams(session)
            if (session.isHeadRole) loadDepartmentMembers(session)

            _isLoading.value = false
        } catch (e: Exception) {
            Log.e(TAG, "initSession failed", e)
            _errorMessage.value = "Failed to load session: ${e.message}"
            _isLoading.value = false
        }
    }

    // ── Start all streams based on role ─────────────────────────────────--

    private fun startStreams(session: CurrentUserSession) {

        // Stream 1: My Complaints — visible to everyone (own complaints)
        lifecycleScope.launch {
            repo.observeMyComplaints(session.userId)
                .catch { e -> _errorMessage.value = "Stream error: ${e.message}" }
                .collect { _myComplaints.value = it }
        }

        // Stream 2: Assigned to Me — only complaints where this user is assignee
        // This ensures employees only see complaints they're explicitly assigned to
        lifecycleScope.launch {
            repo.observeAssignedToMe(session.userId)
                .catch { e -> Log.e(TAG, "assignedToMe stream error", e) }
                .collect { list ->
                    // Filter out complaints already in "My Complaints" to avoid duplicates
                    _assignedComplaints.value = list.filter {
                        it.createdBy.userId != session.userId
                    }
                }
        }

        // Stream 3: Department tab — head roles only
        when {
            session.isAdmin -> {
                // Admin sees all company complaints
                lifecycleScope.launch {
                    repo.observeAllCompanyComplaints(session.sanitizedCompanyName)
                        .catch { e -> Log.e(TAG, "admin stream error", e) }
                        .collect { _deptComplaints.value = it }
                }
            }
            session.isHeadRole -> {
                // Dept head sees:
                // - All complaints in their dept (non-global)
                // - Global complaints assigned/routed to their dept
                val deptFlow = repo.observeDepartmentComplaints(
                    session.sanitizedCompanyName, session.sanitizedDepartment
                )
                val globalFlow = repo.observeGlobalComplaintsForDept(
                    session.sanitizedCompanyName, session.sanitizedDepartment
                )
                lifecycleScope.launch {
                    deptFlow.combine(globalFlow) { dept, global ->
                        (dept + global).distinctBy { it.id }
                            .sortedByDescending { it.updatedAt }
                    }.catch { e -> Log.e(TAG, "dept stream error", e) }
                        .collect { _deptComplaints.value = it }
                }
            }
            else -> {
                // Regular employees: dept tab is hidden; leave deptComplaints empty
                _deptComplaints.value = emptyList()
            }
        }

        // Notifications (all roles)
        lifecycleScope.launch {
            repo.observeNotifications(session.userId)
                .catch { Log.e(TAG, "notification stream error", it) }
                .collect { _notifications.value = it }
        }
        lifecycleScope.launch {
            repo.observeUnreadCount(session.userId)
                .catch { Log.e(TAG, "unread count error", it) }
                .collect { _unreadCount.value = it }
        }
    }

    private suspend fun refreshData(session: CurrentUserSession) {
        _isLoading.value = true
        if (session.isHeadRole) loadDepartmentMembers(session)
        _isLoading.value = false
    }

    private suspend fun loadDepartmentMembers(session: CurrentUserSession) {
        val members = repo.getDepartmentMembers(
            session.sanitizedCompanyName, session.sanitizedDepartment
        )
        _departmentMembers.value = members
        Log.d(TAG, "Loaded ${members.size} department members")
    }

    // ── Actions ────────────────────────────────────────────────────────────

    private fun assignComplaint(
        complaint: LiveComplaint,
        member: DepartmentMember,
        note: String
    ) {
        val session = _currentUserInfo.value ?: return
        if (!session.isHeadRole) {
            showToast("You don't have permission to assign complaints.")
            return
        }
        lifecycleScope.launch {
            val result = repo.assignComplaint(
                AssignmentRequest(
                    complaintId       = complaint.id,
                    assigneeId        = member.userId,
                    assigneeName      = member.name,
                    assigneeData      = member,
                    assignedByUserId  = session.userId,
                    assignedByName    = session.name,
                    note              = note
                )
            )
            if (result.isSuccess) {
                repo.notifyDepartmentHeads(
                    sanitizedCompanyName = session.sanitizedCompanyName,
                    sanitizedDepartment  = session.sanitizedDepartment,
                    complaintId          = complaint.id,
                    complaintTitle       = complaint.title,
                    raisedByName         = complaint.createdBy.name,
                    raisedByUserId       = complaint.createdBy.userId,
                    urgency              = complaint.urgency
                )
                showToast("Assigned to ${member.name}")
                _selectedComplaint.value = null
            } else {
                _errorMessage.value = "Assignment failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    private fun updateStatus(
        complaint: LiveComplaint,
        newStatus: String,
        description: String,
        resolution: String? = null
    ) {
        val session = _currentUserInfo.value ?: return
        // Validate close permission
        if (newStatus.lowercase() in listOf("closed", "resolved")) {
            val canClose = session.isAdmin
                    || (session.isHeadRole && complaint.sanitizedDepartment == session.sanitizedDepartment)
                    || complaint.assignedToUser?.userId == session.userId
            if (!canClose) {
                _errorMessage.value = "You don't have permission to close this complaint."
                return
            }
        }
        lifecycleScope.launch {
            val result = repo.updateComplaintStatus(
                complaintId       = complaint.id,
                newStatus         = newStatus,
                description       = description,
                performedByUserId = session.userId,
                performedByName   = session.name,
                notifyUserId      = complaint.createdBy.userId,
                resolution        = resolution
            )
            if (result.isSuccess) {
                showToast("Status updated to $newStatus")
                _selectedComplaint.value = null
            } else {
                _errorMessage.value = "Status update failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    private fun closeComplaint(complaint: LiveComplaint, resolution: String) {
        val session = _currentUserInfo.value ?: return
        lifecycleScope.launch {
            val result = repo.closeComplaint(
                complaint         = complaint,
                performedByUserId = session.userId,
                performedByName   = session.name,
                resolution        = resolution,
                session           = session
            )
            if (result.isSuccess) {
                showToast("Complaint closed")
                _selectedComplaint.value = null
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Close failed"
            }
        }
    }

    private fun loadMemberProfile(userId: String) {
        lifecycleScope.launch {
            _selectedMemberProfile.value = repo.getUserContactDetails(userId)
        }
    }

    private fun onNotificationTap(notification: InAppNotification) {
        val session = _currentUserInfo.value ?: return
        lifecycleScope.launch { repo.markNotificationRead(session.userId, notification.id) }
        notification.complaintId?.let { cId ->
            val all = _myComplaints.value + _deptComplaints.value + _assignedComplaints.value
            all.distinctBy { it.id }.find { it.id == cId }?.let {
                _selectedComplaint.value = it
            }
        }
    }

    private fun markAllRead() {
        val session = _currentUserInfo.value ?: return
        lifecycleScope.launch { repo.markAllNotificationsRead(session.userId) }
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// ── Session model ──────────────────────────────────────────────────────────

data class CurrentUserSession(
    val userId: String,
    val name: String,
    val email: String,
    val role: String,
    val companyName: String,
    val sanitizedCompanyName: String,
    val department: String,
    val sanitizedDepartment: String
) {
    val isAdmin: Boolean       get() = role.contains("admin", ignoreCase = true)
    val isManager: Boolean     get() = role.contains("manager", ignoreCase = true)
    val isTeamLeader: Boolean  get() = role.contains("lead", ignoreCase = true)
    val isHeadRole: Boolean    get() = isAdmin || isManager || isTeamLeader

    fun canClosComplaint(complaint: LiveComplaint): Boolean = when {
        isAdmin -> true
        isHeadRole && complaint.sanitizedDepartment == sanitizedDepartment -> true
        complaint.assignedToUser?.userId == userId -> true
        else -> false
    }
}