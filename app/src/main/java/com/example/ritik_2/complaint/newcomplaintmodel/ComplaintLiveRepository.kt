package com.example.ritik_2.complaint.newcomplaintmodel

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────

data class LiveComplaint(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val urgency: String = "",
    val status: String = "Open",
    val priority: Int = 1,
    val isGlobal: Boolean = false,
    val companyName: String = "",
    val sanitizedCompanyName: String = "",
    val sanitizedDepartment: String = "",        // ← NEW: department scope field
    val documentPath: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val createdBy: LiveUserInfo = LiveUserInfo(),
    val assignedToUser: LiveUserInfo? = null,
    val assignedToDepartment: LiveDepartmentInfo? = null,
    val timeline: List<TimelineEvent> = emptyList(),
    val resolution: String? = null,
    val resolvedAt: Long? = null,
    val hasAttachment: Boolean = false,
    val attachmentUrl: String? = null,
    val estimatedResolutionTime: String = "",
    val tags: List<String> = emptyList()
) {
    val isClosed: Boolean get() = status.lowercase() in listOf("closed", "resolved")
    val isAssigned: Boolean get() = assignedToUser != null
}

data class LiveUserInfo(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val department: String = "",
    val role: String = "",
    val designation: String = "",
    val profilePictureUrl: String? = null,
    val phoneNumber: String = "",
    val companyName: String = ""
)

data class LiveDepartmentInfo(
    val departmentId: String = "",
    val departmentName: String = "",
    val sanitizedName: String = "",
    val companyName: String = "",
    val userCount: Int = 0,
    val availableRoles: List<String> = emptyList()
)

data class TimelineEvent(
    val eventId: String = "",
    val status: String = "",
    val title: String = "",
    val description: String = "",
    val timestamp: Long = 0L,
    val performedBy: String = "",
    val performedByUserId: String = "",
    val type: TimelineEventType = TimelineEventType.STATUS_CHANGE
)

enum class TimelineEventType {
    CREATED, ASSIGNED, STATUS_CHANGE, COMMENT, RESOLVED, REOPENED, ESCALATED
}

data class InAppNotification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val type: NotificationType = NotificationType.GENERAL,
    val complaintId: String? = null,
    val complaintTitle: String? = null,
    val fromUserId: String = "",
    val fromUserName: String = "",
    val isRead: Boolean = false,
    val createdAt: Long = 0L,
    val actionData: Map<String, String> = emptyMap()
)

enum class NotificationType {
    COMPLAINT_CREATED,
    COMPLAINT_ASSIGNED,
    STATUS_UPDATE,
    COMPLAINT_RESOLVED,
    COMPLAINT_REOPENED,
    NEW_COMMENT,
    GENERAL
}

data class DepartmentMember(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val designation: String = "",
    val department: String = "",
    val phoneNumber: String = "",
    val profilePictureUrl: String? = null,
    val isActive: Boolean = true
)

data class AssignmentRequest(
    val complaintId: String,
    val assigneeId: String,
    val assigneeName: String,
    val assigneeData: DepartmentMember,
    val assignedByUserId: String,
    val assignedByName: String,
    val note: String = ""
)

// ─────────────────────────────────────────────
// LIVE REPOSITORY
// ─────────────────────────────────────────────

class ComplaintLiveRepository {

    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "ComplaintLiveRepo"
        private const val BATCH_SIZE = 25L
    }

    // ── [RULE] Complaints created by a user (My Complaints tab) ───────────
    // Employee sees their own complaints regardless of assignment status
    fun observeMyComplaints(userId: String): Flow<List<LiveComplaint>> = callbackFlow {
        val reg = firestore.collection("all_complaints")
            .whereEqualTo("createdBy.userId", userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(BATCH_SIZE)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    parseComplaintDoc(it.id, it.data ?: return@mapNotNull null)
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ── [RULE] Complaints assigned TO this user (Assigned to Me tab) ──────
    // Only shows when actually assigned — non-assigned employees cannot see other complaints
    fun observeAssignedToMe(userId: String): Flow<List<LiveComplaint>> = callbackFlow {
        val reg = firestore.collection("all_complaints")
            .whereEqualTo("assignedToUser.userId", userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(BATCH_SIZE)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    parseComplaintDoc(it.id, it.data ?: return@mapNotNull null)
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ── [RULE] Department complaints for Head roles (Manager/TeamLeader) ───
    // Only shows complaints belonging to their department (non-global by dept field,
    // OR global complaints that have been assigned to this dept)
    fun observeDepartmentComplaints(
        sanitizedCompanyName: String,
        sanitizedDepartment: String
    ): Flow<List<LiveComplaint>> = callbackFlow {
        // Stream 1: dept-specific complaints
        val reg = firestore.collection("all_complaints")
            .whereEqualTo("sanitizedCompanyName", sanitizedCompanyName)
            .whereEqualTo("sanitizedUserDepartment", sanitizedDepartment)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(BATCH_SIZE)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    parseComplaintDoc(it.id, it.data ?: return@mapNotNull null)
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ── [RULE] Global complaints routed to a dept (visible to that dept head) ──
    fun observeGlobalComplaintsForDept(
        sanitizedCompanyName: String,
        sanitizedDepartment: String
    ): Flow<List<LiveComplaint>> = callbackFlow {
        val reg = firestore.collection("all_complaints")
            .whereEqualTo("sanitizedCompanyName", sanitizedCompanyName)
            .whereEqualTo("isGlobal", true)
            .whereEqualTo("assignedToDepartment.sanitizedName", sanitizedDepartment)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(BATCH_SIZE)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    parseComplaintDoc(it.id, it.data ?: return@mapNotNull null)
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ── [RULE] Admin sees ALL company complaints ────────────────────────────
    fun observeAllCompanyComplaints(sanitizedCompanyName: String): Flow<List<LiveComplaint>> = callbackFlow {
        val reg = firestore.collection("all_complaints")
            .whereEqualTo("sanitizedCompanyName", sanitizedCompanyName)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(BATCH_SIZE)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    parseComplaintDoc(it.id, it.data ?: return@mapNotNull null)
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ── Single complaint live stream ───────────────────────────────────────
    fun observeSingleComplaint(complaintId: String): Flow<LiveComplaint?> = callbackFlow {
        var mainReg: ListenerRegistration? = null
        val indexReg = firestore.collection("complaint_search_index")
            .document(complaintId)
            .addSnapshotListener { indexDoc, _ ->
                val docPath = indexDoc?.getString("documentPath")
                if (!docPath.isNullOrEmpty()) {
                    mainReg?.remove()
                    mainReg = firestore.document(docPath)
                        .addSnapshotListener { doc, err ->
                            if (err != null) return@addSnapshotListener
                            trySend(doc?.data?.let { parseComplaintDoc(doc.id, it) })
                        }
                } else {
                    firestore.collection("all_complaints").document(complaintId)
                        .addSnapshotListener { doc, err ->
                            if (err != null) return@addSnapshotListener
                            trySend(doc?.data?.let { parseComplaintDoc(doc.id, it) })
                        }
                }
            }
        awaitClose { indexReg.remove(); mainReg?.remove() }
    }

    // ── Notifications ──────────────────────────────────────────────────────
    fun observeNotifications(userId: String): Flow<List<InAppNotification>> = callbackFlow {
        val reg = firestore.collection("user_notifications")
            .document(userId)
            .collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    parseNotification(it.id, it.data ?: return@mapNotNull null)
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    fun observeUnreadCount(userId: String): Flow<Int> = callbackFlow {
        val reg = firestore.collection("user_notifications")
            .document(userId)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                trySend(snap?.size() ?: 0)
            }
        awaitClose { reg.remove() }
    }

    // ── Department members ─────────────────────────────────────────────────
    suspend fun getDepartmentMembers(
        sanitizedCompanyName: String,
        sanitizedDepartment: String
    ): List<DepartmentMember> {
        return try {
            val snap = firestore.collection("user_access_control")
                .whereEqualTo("sanitizedCompanyName", sanitizedCompanyName)
                .whereEqualTo("sanitizedDepartment", sanitizedDepartment)
                .whereEqualTo("isActive", true)
                .get().await()
            snap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                DepartmentMember(
                    userId = d["userId"] as? String ?: doc.id,
                    name = d["name"] as? String ?: "",
                    email = d["email"] as? String ?: "",
                    role = d["role"] as? String ?: "Employee",
                    designation = d["designation"] as? String ?: "",
                    department = d["department"] as? String ?: "",
                    phoneNumber = d["phoneNumber"] as? String ?: "",
                    isActive = d["isActive"] as? Boolean ?: true
                )
            }.sortedWith(compareBy({ roleOrder(it.role) }, { it.name }))
        } catch (e: Exception) {
            Log.e(TAG, "getDepartmentMembers failed", e)
            emptyList()
        }
    }

    private fun roleOrder(role: String) = when {
        role.contains("admin", ignoreCase = true) -> 0
        role.contains("manager", ignoreCase = true) -> 1
        role.contains("lead", ignoreCase = true) -> 2
        else -> 3
    }

    // ── [RULE] Close complaint — only assignee, or head role of that dept ──
    suspend fun closeComplaint(
        complaint: LiveComplaint,
        performedByUserId: String,
        performedByName: String,
        resolution: String,
        session: CurrentUserSession
    ): Result<Unit> {
        val canClose = when {
            session.isAdmin -> true
            session.isHeadRole && complaint.sanitizedDepartment == session.sanitizedDepartment -> true
            complaint.assignedToUser?.userId == performedByUserId -> true
            else -> false
        }
        if (!canClose) return Result.failure(Exception("You don't have permission to close this complaint."))

        return updateComplaintStatus(
            complaintId = complaint.id,
            newStatus = "Closed",
            description = "Complaint closed by $performedByName",
            performedByUserId = performedByUserId,
            performedByName = performedByName,
            notifyUserId = complaint.createdBy.userId,
            resolution = resolution.ifBlank { "Resolved and closed." }
        )
    }

    // ── Assign complaint ───────────────────────────────────────────────────
    suspend fun assignComplaint(request: AssignmentRequest): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val ts = Timestamp.now()
            val now = System.currentTimeMillis()

            val timelineEvent = mapOf(
                "eventId" to "evt_$now",
                "status" to "Assigned",
                "title" to "Complaint Assigned",
                "description" to buildString {
                    append("Assigned to ${request.assigneeName} by ${request.assignedByName}")
                    if (request.note.isNotBlank()) append(" — Note: ${request.note}")
                },
                "timestamp" to ts,
                "performedBy" to request.assignedByName,
                "performedByUserId" to request.assignedByUserId,
                "type" to "ASSIGNED"
            )
            val assignedUserMap = mapOf(
                "userId" to request.assigneeId,
                "name" to request.assigneeName,
                "email" to request.assigneeData.email,
                "department" to request.assigneeData.department,
                "role" to request.assigneeData.role,
                "designation" to request.assigneeData.designation,
                "phoneNumber" to request.assigneeData.phoneNumber
            )

            val flatRef = firestore.collection("all_complaints").document(request.complaintId)
            batch.update(flatRef, mapOf(
                "assignedToUser" to assignedUserMap,
                "status" to "In Progress",
                "updatedAt" to ts,
                "timeline" to FieldValue.arrayUnion(timelineEvent)
            ))

            val searchRef = firestore.collection("complaint_search_index").document(request.complaintId)
            batch.update(searchRef, mapOf(
                "assignedToUser" to assignedUserMap,
                "status" to "in progress",
                "updatedAt" to ts
            ))

            // Try hierarchical path
            val searchDoc = firestore.collection("complaint_search_index")
                .document(request.complaintId).get().await()
            searchDoc.getString("documentPath")?.takeIf { it.isNotEmpty() }?.let { path ->
                batch.update(firestore.document(path), mapOf(
                    "assignedToUser" to assignedUserMap,
                    "status" to mapOf("current" to "In Progress", "history" to FieldValue.arrayUnion(timelineEvent)),
                    "timeline" to FieldValue.arrayUnion(timelineEvent),
                    "updatedAt" to ts
                ))
            }

            batch.commit().await()

            // Notify assignee
            sendNotificationToUser(
                userId = request.assigneeId,
                notification = InAppNotification(
                    title = "New Complaint Assigned",
                    message = "\"${request.complaintId}\" assigned to you by ${request.assignedByName}",
                    type = NotificationType.COMPLAINT_ASSIGNED,
                    complaintId = request.complaintId,
                    fromUserId = request.assignedByUserId,
                    fromUserName = request.assignedByName,
                    createdAt = now
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "assignComplaint failed", e)
            Result.failure(e)
        }
    }

    // ── Update complaint status ────────────────────────────────────────────
    suspend fun updateComplaintStatus(
        complaintId: String,
        newStatus: String,
        description: String,
        performedByUserId: String,
        performedByName: String,
        notifyUserId: String,
        resolution: String? = null
    ): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val ts = Timestamp.now()
            val now = System.currentTimeMillis()

            val eventType = when (newStatus.lowercase()) {
                "resolved", "closed" -> "RESOLVED"
                "reopened" -> "REOPENED"
                else -> "STATUS_CHANGE"
            }
            val timelineEvent = mapOf(
                "eventId" to "evt_$now",
                "status" to newStatus,
                "title" to "Status → $newStatus",
                "description" to description,
                "timestamp" to ts,
                "performedBy" to performedByName,
                "performedByUserId" to performedByUserId,
                "type" to eventType
            )

            val flatUpdate = mutableMapOf<String, Any>(
                "status" to newStatus,
                "updatedAt" to ts,
                "timeline" to FieldValue.arrayUnion(timelineEvent)
            )
            if (resolution != null) flatUpdate["resolution"] = resolution

            val flatRef = firestore.collection("all_complaints").document(complaintId)
            batch.update(flatRef, flatUpdate)

            val searchRef = firestore.collection("complaint_search_index").document(complaintId)
            batch.update(searchRef, mapOf("status" to newStatus.lowercase(), "updatedAt" to ts))

            val searchDoc = firestore.collection("complaint_search_index")
                .document(complaintId).get().await()
            searchDoc.getString("documentPath")?.takeIf { it.isNotEmpty() }?.let { path ->
                val mainUpdate = mutableMapOf<String, Any>(
                    "status" to mapOf("current" to newStatus, "history" to FieldValue.arrayUnion(timelineEvent)),
                    "timeline" to FieldValue.arrayUnion(timelineEvent),
                    "updatedAt" to ts
                )
                if (resolution != null) {
                    mainUpdate["resolution"] = resolution
                    mainUpdate["resolvedAt"] = ts
                    mainUpdate["resolvedBy"] = performedByUserId
                }
                firestore.document(path).update(mainUpdate)
            }

            batch.commit().await()

            sendNotificationToUser(
                userId = notifyUserId,
                notification = InAppNotification(
                    title = "Complaint Status Updated",
                    message = "Your complaint status changed to \"$newStatus\". $description",
                    type = when (newStatus.lowercase()) {
                        "resolved", "closed" -> NotificationType.COMPLAINT_RESOLVED
                        "reopened" -> NotificationType.COMPLAINT_REOPENED
                        else -> NotificationType.STATUS_UPDATE
                    },
                    complaintId = complaintId,
                    fromUserId = performedByUserId,
                    fromUserName = performedByName,
                    createdAt = now
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateComplaintStatus failed", e)
            Result.failure(e)
        }
    }

    // ── Notify department heads ────────────────────────────────────────────
    suspend fun notifyDepartmentHeads(
        sanitizedCompanyName: String,
        sanitizedDepartment: String,
        complaintId: String,
        complaintTitle: String,
        raisedByName: String,
        raisedByUserId: String,
        urgency: String
    ) {
        try {
            val headRoles = listOf("Administrator", "Manager", "Team Leader", "TeamLeader", "Team Lead")
            val snap = firestore.collection("user_access_control")
                .whereEqualTo("sanitizedCompanyName", sanitizedCompanyName)
                .whereEqualTo("sanitizedDepartment", sanitizedDepartment)
                .whereEqualTo("isActive", true)
                .get().await()
            val adminSnap = firestore.collection("user_access_control")
                .whereEqualTo("sanitizedCompanyName", sanitizedCompanyName)
                .whereEqualTo("role", "Administrator")
                .whereEqualTo("isActive", true)
                .get().await()

            val heads = (snap.documents.filter { doc ->
                val role = doc.getString("role") ?: ""
                headRoles.any { role.contains(it, ignoreCase = true) }
            } + adminSnap.documents).distinctBy { it.id }
                .filter { it.getString("userId") != raisedByUserId }

            heads.forEach { doc ->
                val headId = doc.getString("userId") ?: doc.id
                sendNotificationToUser(
                    userId = headId,
                    notification = InAppNotification(
                        title = "New Complaint — $urgency Priority",
                        message = "\"$complaintTitle\" raised by $raisedByName needs your attention.",
                        type = NotificationType.COMPLAINT_CREATED,
                        complaintId = complaintId,
                        complaintTitle = complaintTitle,
                        fromUserId = raisedByUserId,
                        fromUserName = raisedByName,
                        createdAt = System.currentTimeMillis(),
                        actionData = mapOf("action" to "assign", "complaintId" to complaintId)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "notifyDepartmentHeads failed", e)
        }
    }

    suspend fun markNotificationRead(userId: String, notificationId: String) {
        try {
            firestore.collection("user_notifications")
                .document(userId)
                .collection("notifications")
                .document(notificationId)
                .update(mapOf("isRead" to true, "readAt" to Timestamp.now()))
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "markNotificationRead failed", e)
        }
    }

    suspend fun markAllNotificationsRead(userId: String) {
        try {
            val unread = firestore.collection("user_notifications")
                .document(userId)
                .collection("notifications")
                .whereEqualTo("isRead", false)
                .get().await()
            val batch = firestore.batch()
            unread.documents.forEach { doc ->
                batch.update(doc.reference, mapOf("isRead" to true, "readAt" to Timestamp.now()))
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(TAG, "markAllNotificationsRead failed", e)
        }
    }

    suspend fun getUserContactDetails(userId: String): DepartmentMember? {
        return try {
            val doc = firestore.collection("user_access_control").document(userId).get().await()
            val d = doc.data ?: return null
            DepartmentMember(
                userId = d["userId"] as? String ?: userId,
                name = d["name"] as? String ?: "",
                email = d["email"] as? String ?: "",
                role = d["role"] as? String ?: "",
                designation = d["designation"] as? String ?: "",
                department = d["department"] as? String ?: "",
                phoneNumber = d["phoneNumber"] as? String ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "getUserContactDetails failed", e)
            null
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────
    private suspend fun sendNotificationToUser(userId: String, notification: InAppNotification) {
        try {
            firestore.collection("user_notifications")
                .document(userId)
                .collection("notifications")
                .add(mapOf(
                    "title" to notification.title,
                    "message" to notification.message,
                    "type" to notification.type.name,
                    "complaintId" to (notification.complaintId ?: ""),
                    "complaintTitle" to (notification.complaintTitle ?: ""),
                    "fromUserId" to notification.fromUserId,
                    "fromUserName" to notification.fromUserName,
                    "isRead" to false,
                    "createdAt" to Timestamp.now(),
                    "actionData" to notification.actionData
                )).await()
        } catch (e: Exception) {
            Log.e(TAG, "sendNotificationToUser failed", e)
        }
    }

    private fun parseComplaintDoc(id: String, data: Map<String, Any>): LiveComplaint {
        val createdByData = data["createdBy"] as? Map<*, *>
        val assignedUserData = data["assignedToUser"] as? Map<*, *>
        val assignedDeptData = data["assignedToDepartment"] as? Map<*, *>
        val currentStatus = when (val s = data["status"]) {
            is Map<*, *> -> s["current"] as? String ?: "Open"
            is String -> s
            else -> "Open"
        }
        val timelineRaw = data["timeline"] as? List<*> ?: emptyList<Any>()
        val timeline = timelineRaw.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            TimelineEvent(
                eventId = m["eventId"] as? String ?: "",
                status = m["status"] as? String ?: "",
                title = m["title"] as? String ?: "",
                description = m["description"] as? String ?: "",
                timestamp = (m["timestamp"] as? Timestamp)?.toDate()?.time ?: 0L,
                performedBy = m["performedBy"] as? String ?: "",
                performedByUserId = m["performedByUserId"] as? String ?: "",
                type = runCatching {
                    TimelineEventType.valueOf(m["type"] as? String ?: "STATUS_CHANGE")
                }.getOrDefault(TimelineEventType.STATUS_CHANGE)
            )
        }.sortedBy { it.timestamp }

        val createdAt = when (val ts = data["createdAt"]) {
            is Timestamp -> ts.toDate().time
            is Long -> ts
            else -> 0L
        }
        val updatedAt = when (val ts = data["updatedAt"]) {
            is Timestamp -> ts.toDate().time
            is Long -> ts
            else -> createdAt
        }
        val attachmentData = data["attachment"] as? Map<*, *>

        return LiveComplaint(
            id = id,
            title = data["title"] as? String ?: data["originalTitle"] as? String ?: "Untitled",
            description = data["description"] as? String ?: "",
            category = data["department"] as? String ?: data["originalCategory"] as? String ?: "General",
            urgency = data["urgency"] as? String ?: "Medium",
            status = currentStatus,
            priority = (data["priority"] as? Number)?.toInt() ?: 1,
            isGlobal = data["isGlobal"] as? Boolean ?: false,
            companyName = data["companyName"] as? String ?: "",
            sanitizedCompanyName = data["sanitizedCompanyName"] as? String ?: "",
            sanitizedDepartment = data["sanitizedUserDepartment"] as? String
                ?: data["sanitizedDepartment"] as? String ?: "",
            documentPath = data["documentPath"] as? String ?: data["hierarchicalPath"] as? String ?: "",
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = LiveUserInfo(
                userId = createdByData?.get("userId") as? String ?: "",
                name = createdByData?.get("name") as? String ?: "Unknown",
                email = createdByData?.get("email") as? String ?: "",
                department = createdByData?.get("department") as? String ?: "",
                role = createdByData?.get("role") as? String ?: "",
                designation = createdByData?.get("designation") as? String ?: "",
                phoneNumber = createdByData?.get("contactInfo") as? String ?: ""
            ),
            assignedToUser = assignedUserData?.let {
                LiveUserInfo(
                    userId = it["userId"] as? String ?: "",
                    name = it["name"] as? String ?: "",
                    email = it["email"] as? String ?: "",
                    department = it["department"] as? String ?: "",
                    role = it["role"] as? String ?: "",
                    designation = it["designation"] as? String ?: "",
                    phoneNumber = it["phoneNumber"] as? String ?: ""
                )
            },
            assignedToDepartment = assignedDeptData?.let {
                LiveDepartmentInfo(
                    departmentId = it["departmentId"] as? String ?: "",
                    departmentName = it["departmentName"] as? String ?: "",
                    sanitizedName = it["sanitizedDepartmentName"] as? String ?: "",
                    companyName = it["companyName"] as? String ?: "",
                    userCount = (it["userCount"] as? Number)?.toInt() ?: 0
                )
            },
            timeline = timeline,
            resolution = data["resolution"] as? String,
            resolvedAt = (data["resolvedAt"] as? Timestamp)?.toDate()?.time,
            hasAttachment = attachmentData?.get("hasFile") as? Boolean ?: false,
            attachmentUrl = attachmentData?.get("url") as? String,
            estimatedResolutionTime = data["estimatedResolutionTime"] as? String ?: "",
            tags = (data["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        )
    }

    private fun parseNotification(id: String, data: Map<String, Any>): InAppNotification {
        val createdAt = when (val ts = data["createdAt"]) {
            is Timestamp -> ts.toDate().time
            is Long -> ts
            else -> 0L
        }
        return InAppNotification(
            id = id,
            title = data["title"] as? String ?: "",
            message = data["message"] as? String ?: "",
            type = runCatching {
                NotificationType.valueOf(data["type"] as? String ?: "GENERAL")
            }.getOrDefault(NotificationType.GENERAL),
            complaintId = data["complaintId"] as? String,
            complaintTitle = data["complaintTitle"] as? String,
            fromUserId = data["fromUserId"] as? String ?: "",
            fromUserName = data["fromUserName"] as? String ?: "",
            isRead = data["isRead"] as? Boolean ?: false,
            createdAt = createdAt,
            actionData = (data["actionData"] as? Map<*, *>)
                ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
                ?.toMap() ?: emptyMap()
        )
    }
}