package com.example.ritik_2.complaint.viewcomplaint.data

import android.util.Log
import com.example.ritik_2.complaint.viewcomplaint.data.models.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

class ComplaintRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val batchSize = 15

    companion object {
        private const val TAG = "ComplaintRepository"
    }

    suspend fun loadComplaints(
        userId: String,
        userData: UserData,
        viewMode: ViewMode,
        sortOption: SortOption,
        searchQuery: String,
        filterOption: String?
    ): List<ComplaintWithDetails> {
        return try {
            Log.d(TAG, "Loading complaints for viewMode: $viewMode")

            // Try multiple approaches with better error handling
            var documents = loadFromSearchIndex(userId, userData, viewMode, sortOption)

            if (documents.isEmpty()) {
                documents = loadFromFlatStructure(userId, userData, viewMode)
            }

            if (documents.isEmpty()) {
                documents = loadFromHierarchicalStructure(userId, userData, viewMode)
            }

            // Convert documents to complaint objects
            val complaints = documents.mapNotNull { doc ->
                convertToComplaintWithDetails(doc)
            }

            // Apply filters
            var filteredComplaints = complaints

            if (searchQuery.isNotBlank()) {
                filteredComplaints = filteredComplaints.filter { complaint ->
                    complaint.title.contains(searchQuery, ignoreCase = true) ||
                            complaint.description.contains(searchQuery, ignoreCase = true) ||
                            complaint.category.contains(searchQuery, ignoreCase = true)
                }
            }

            if (!filterOption.isNullOrBlank()) {
                filteredComplaints = filteredComplaints.filter { complaint ->
                    complaint.category.equals(filterOption, ignoreCase = true)
                }
            }

            // Apply sorting
            when (sortOption) {
                SortOption.DATE_DESC -> filteredComplaints.sortedByDescending { it.createdAt }
                SortOption.DATE_ASC -> filteredComplaints.sortedBy { it.createdAt }
                SortOption.URGENCY -> filteredComplaints.sortedByDescending {
                    getUrgencyPriority(it.urgency)
                }

                SortOption.STATUS -> TODO()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error loading complaints", e)
            throw e
        }
    }

    private suspend fun loadFromSearchIndex(
        userId: String,
        userData: UserData,
        viewMode: ViewMode,
        sortOption: SortOption
    ): List<DocumentSnapshot> {
        return try {
            val baseQuery = when (viewMode) {
                ViewMode.PERSONAL -> {
                    firestore.collection("complaint_search_index")
                        .whereEqualTo("createdBy.userId", userId)
                }
                ViewMode.DEPARTMENT -> {
                    firestore.collection("complaint_search_index")
                        .whereEqualTo("sanitizedCompanyName", userData.sanitizedCompanyName)
                        .whereEqualTo("sanitizedUserDepartment", userData.sanitizedDepartment)
                }
                ViewMode.ASSIGNED_TO_ME -> {
                    firestore.collection("complaint_search_index")
                        .whereEqualTo("assignedToUser.userId", userId)
                }
                ViewMode.ALL_COMPANY -> {
                    firestore.collection("complaint_search_index")
                        .whereEqualTo("sanitizedCompanyName", userData.sanitizedCompanyName)
                }
                ViewMode.GLOBAL -> {
                    firestore.collection("complaint_search_index")
                        .whereEqualTo("sanitizedCompanyName", userData.sanitizedCompanyName)
                        .whereEqualTo("isGlobal", true)
                }
            }

            val sortedQuery = baseQuery.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(batchSize.toLong())

            val result = sortedQuery.get().await()
            Log.d(TAG, "Search index returned ${result.documents.size} documents")
            result.documents

        } catch (e: Exception) {
            Log.w(TAG, "Search index query failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadFromFlatStructure(
        userId: String,
        userData: UserData,
        viewMode: ViewMode
    ): List<DocumentSnapshot> {
        return try {
            val baseQuery = when (viewMode) {
                ViewMode.PERSONAL -> {
                    firestore.collection("all_complaints")
                        .whereEqualTo("createdBy.userId", userId)
                }
                ViewMode.DEPARTMENT -> {
                    firestore.collection("all_complaints")
                        .whereEqualTo("sanitizedCompanyName", userData.sanitizedCompanyName)
                        .whereEqualTo("sanitizedUserDepartment", userData.sanitizedDepartment)
                }
                ViewMode.ASSIGNED_TO_ME -> {
                    firestore.collection("all_complaints")
                        .whereEqualTo("assignedToUser.userId", userId)
                }
                ViewMode.ALL_COMPANY -> {
                    firestore.collection("all_complaints")
                        .whereEqualTo("sanitizedCompanyName", userData.sanitizedCompanyName)
                }
                ViewMode.GLOBAL -> {
                    firestore.collection("all_complaints")
                        .whereEqualTo("sanitizedCompanyName", userData.sanitizedCompanyName)
                        .whereEqualTo("isGlobal", true)
                }
            }

            val result = baseQuery.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(batchSize.toLong())
                .get()
                .await()

            Log.d(TAG, "Flat structure returned ${result.documents.size} documents")
            result.documents

        } catch (e: Exception) {
            Log.w(TAG, "Flat structure query failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadFromHierarchicalStructure(
        userId: String,
        userData: UserData,
        viewMode: ViewMode
    ): List<DocumentSnapshot> {
        return try {
            val documents = mutableListOf<DocumentSnapshot>()
            val companyDoc = userData.sanitizedCompanyName

            when (viewMode) {
                ViewMode.PERSONAL -> {
                    val globalComplaints = firestore.collection("complaints")
                        .document(companyDoc)
                        .collection("global_complaints")
                        .whereEqualTo("createdBy.userId", userId)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(batchSize.toLong())
                        .get()
                        .await()

                    val deptComplaints = firestore.collection("complaints")
                        .document(companyDoc)
                        .collection("department_complaints")
                        .whereEqualTo("createdBy.userId", userId)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(batchSize.toLong())
                        .get()
                        .await()

                    documents.addAll(globalComplaints.documents)
                    documents.addAll(deptComplaints.documents)
                }

                ViewMode.ALL_COMPANY -> {
                    val globalComplaints = firestore.collection("complaints")
                        .document(companyDoc)
                        .collection("global_complaints")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(batchSize.toLong())
                        .get()
                        .await()

                    val deptComplaints = firestore.collection("complaints")
                        .document(companyDoc)
                        .collection("department_complaints")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(batchSize.toLong())
                        .get()
                        .await()

                    documents.addAll(globalComplaints.documents)
                    documents.addAll(deptComplaints.documents)
                }

                ViewMode.GLOBAL -> {
                    val globalComplaints = firestore.collection("complaints")
                        .document(companyDoc)
                        .collection("global_complaints")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(batchSize.toLong())
                        .get()
                        .await()

                    documents.addAll(globalComplaints.documents)
                }

                ViewMode.DEPARTMENT -> {
                    val deptComplaints = firestore.collection("complaints")
                        .document(companyDoc)
                        .collection("department_complaints")
                        .whereEqualTo("assignedToDepartment.sanitizedDepartmentName", userData.sanitizedDepartment)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(batchSize.toLong())
                        .get()
                        .await()

                    documents.addAll(deptComplaints.documents)
                }

                ViewMode.ASSIGNED_TO_ME -> {
                    val globalAssigned = firestore.collection("complaints")
                        .document(companyDoc)
                        .collection("global_complaints")
                        .whereEqualTo("assignedToUser.userId", userId)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(batchSize.toLong())
                        .get()
                        .await()

                    val deptAssigned = firestore.collection("complaints")
                        .document(companyDoc)
                        .collection("department_complaints")
                        .whereEqualTo("assignedToUser.userId", userId)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(batchSize.toLong())
                        .get()
                        .await()

                    documents.addAll(globalAssigned.documents)
                    documents.addAll(deptAssigned.documents)
                }
            }

            // Sort combined documents by timestamp and limit to batch size
            val sortedDocuments = documents.sortedByDescending {
                (it.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
            }.take(batchSize)

            Log.d(TAG, "Hierarchical structure returned ${sortedDocuments.size} documents")
            sortedDocuments

        } catch (e: Exception) {
            Log.w(TAG, "Hierarchical structure query failed: ${e.message}")
            emptyList()
        }
    }

    private fun convertToComplaintWithDetails(doc: DocumentSnapshot): ComplaintWithDetails? {
        return try {
            val data = doc.data ?: return null

            // Safe data extraction with proper null checks
            val createdByData = data["createdBy"]
            val createdBy = when (createdByData) {
                is Map<*, *> -> {
                    UserInfo(
                        userId = (createdByData["userId"] as? String) ?: "",
                        name = (createdByData["name"] as? String) ?: "Unknown User",
                        email = (createdByData["email"] as? String) ?: "",
                        department = (createdByData["department"] as? String) ?: ""
                    )
                }
                is String -> {
                    UserInfo(
                        userId = doc.id,
                        name = createdByData,
                        email = "",
                        department = ""
                    )
                }
                else -> {
                    UserInfo("", "Unknown User", "", "")
                }
            }

            val assignedToUserData = data["assignedToUser"]
            val assignedToUser = if (assignedToUserData is Map<*, *>) {
                UserInfo(
                    userId = (assignedToUserData["userId"] as? String) ?: "",
                    name = (assignedToUserData["name"] as? String) ?: "",
                    email = (assignedToUserData["email"] as? String) ?: "",
                    department = (assignedToUserData["department"] as? String) ?: ""
                )
            } else null

            val assignedToDepartmentData = data["assignedToDepartment"] ?: data["assignedDepartment"]
            val assignedToDepartment = if (assignedToDepartmentData is Map<*, *>) {
                DepartmentInfo(
                    departmentId = (assignedToDepartmentData["departmentId"] as? String) ?: "",
                    departmentName = (assignedToDepartmentData["departmentName"] as? String) ?: "",
                    companyName = (assignedToDepartmentData["companyName"] as? String) ?: "",
                    sanitizedName = (assignedToDepartmentData["sanitizedDepartmentName"] as? String) ?: "",
                    userCount = ((assignedToDepartmentData["userCount"] as? Number)?.toInt()) ?: 0,
                    availableRoles = (assignedToDepartmentData["availableRoles"] as? List<*>)?.mapNotNull {
                        it as? String
                    } ?: emptyList()
                )
            } else null

            // Safe status handling - handle both old and new format
            val statusData = data["status"]
            val status = when (statusData) {
                is Map<*, *> -> (statusData["current"] as? String) ?: "Open"
                is String -> statusData
                else -> "Open"
            }

            // Safe attachment handling
            val attachmentData = data["attachment"]
            val hasAttachment = if (attachmentData is Map<*, *>) {
                (attachmentData["hasFile"] as? Boolean) ?: false
            } else false

            val attachmentUrl = if (attachmentData is Map<*, *>) {
                attachmentData["url"] as? String
            } else null

            // Safe timestamp handling - prevent crashes from invalid timestamps
            val createdAtTimestamp = data["createdAt"]
            val createdAt = when (createdAtTimestamp) {
                is com.google.firebase.Timestamp -> createdAtTimestamp.toDate().time
                is Long -> createdAtTimestamp
                else -> System.currentTimeMillis()
            }

            val updatedAtTimestamp = data["updatedAt"]
            val updatedAt = when (updatedAtTimestamp) {
                is com.google.firebase.Timestamp -> updatedAtTimestamp.toDate().time
                is Long -> updatedAtTimestamp
                else -> createdAt
            }

            ComplaintWithDetails(
                id = doc.id,
                title = (data["title"] as? String) ?: "Untitled",
                description = (data["description"] as? String) ?: "",
                category = (data["department"] as? String) ?: (data["originalCategory"] as? String) ?: (data["category"] as? String) ?: "General",
                urgency = (data["urgency"] as? String) ?: "Medium",
                status = status,
                priority = ((data["priority"] as? Number)?.toInt()) ?: 1,
                createdAt = createdAt,
                updatedAt = updatedAt,
                documentPath = (data["documentPath"] as? String) ?: (data["hierarchicalPath"] as? String) ?: "",
                isGlobal = (data["isGlobal"] as? Boolean) ?: false,
                createdBy = createdBy,
                assignedToUser = assignedToUser,
                assignedToDepartment = assignedToDepartment,
                hasAttachment = hasAttachment,
                attachmentUrl = attachmentUrl,
                resolution = data["resolution"] as? String,
                estimatedResolutionTime = (data["estimatedResolutionTime"] as? String)
                    ?: (data["estimatedResponseTime"] as? String)
                    ?: "Not specified",
                resolvedAt = (data["resolvedAt"] as? Timestamp)?.toDate()?.time,
                resolvedBy = data["resolvedBy"] as? String
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting document ${doc.id} to ComplaintWithDetails", e)
            null
        }
    }

    suspend fun assignComplaintToUser(
        complaintId: String,
        assigneeId: String,
        assigneeName: String,
        assigneeData: UserData,
        currentUser: UserData
    ) {
        try {
            val batch = firestore.batch()
            val timestamp = Timestamp.now()

            // Find the complaint first to get its document path
            val complaint = findComplaintById(complaintId)
            if (complaint == null) {
                throw Exception("Complaint not found")
            }

            // Update main complaint document if path exists
            if (complaint.documentPath.isNotEmpty()) {
                try {
                    val complaintDocRef = firestore.document(complaint.documentPath)
                    val complaintUpdates = mapOf(
                        "assignedToUser" to mapOf(
                            "userId" to assigneeId,
                            "name" to assigneeName,
                            "email" to assigneeData.email,
                            "department" to assigneeData.department,
                            "assignedAt" to timestamp,
                            "assignedBy" to currentUser.userId
                        ),
                        "status" to mapOf(
                            "current" to "In Progress",
                            "history" to FieldValue.arrayUnion(
                                mapOf(
                                    "status" to "In Progress",
                                    "changedAt" to timestamp,
                                    "changedBy" to currentUser.userId,
                                    "reason" to "Assigned to $assigneeName"
                                )
                            )
                        ),
                        "updatedAt" to timestamp,
                        "lastModified" to timestamp
                    )
                    batch.update(complaintDocRef, complaintUpdates)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update main document, continuing with other updates", e)
                }
            }

            // Update search index
            try {
                val searchIndexRef = firestore.collection("complaint_search_index").document(complaintId)
                val searchUpdates = mapOf(
                    "assignedToUser" to mapOf(
                        "userId" to assigneeId,
                        "name" to assigneeName,
                        "email" to assigneeData.email,
                        "department" to assigneeData.department
                    ),
                    "status" to "in progress",
                    "updatedAt" to timestamp
                )
                batch.update(searchIndexRef, searchUpdates)
            } catch (e: Exception) {
                Log.w(TAG, "Could not update search index", e)
            }

            // Update flat structure
            try {
                val flatRef = firestore.collection("all_complaints").document(complaintId)
                val flatUpdates = mapOf(
                    "assignedToUser" to mapOf(
                        "userId" to assigneeId,
                        "name" to assigneeName
                    ),
                    "status" to "In Progress",
                    "updatedAt" to timestamp
                )
                batch.update(flatRef, flatUpdates)
            } catch (e: Exception) {
                Log.w(TAG, "Could not update flat structure", e)
            }

            batch.commit().await()
            Log.d(TAG, "Complaint $complaintId assigned to $assigneeName successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error assigning complaint", e)
            throw e
        }
    }

    suspend fun reopenComplaint(complaintId: String, currentUser: UserData) {
        try {
            val batch = firestore.batch()
            val timestamp = Timestamp.now()

            // Find the complaint first
            val complaint = findComplaintById(complaintId)
            if (complaint == null) {
                throw Exception("Complaint not found")
            }

            // Update main complaint document if path exists
            if (complaint.documentPath.isNotEmpty()) {
                try {
                    val complaintDocRef = firestore.document(complaint.documentPath)
                    val updates = mapOf(
                        "status" to mapOf(
                            "current" to "Open",
                            "history" to FieldValue.arrayUnion(
                                mapOf(
                                    "status" to "Open",
                                    "changedAt" to timestamp,
                                    "changedBy" to currentUser.userId,
                                    "reason" to "Complaint reopened by ${currentUser.name}"
                                )
                            )
                        ),
                        "updatedAt" to timestamp,
                        "lastModified" to timestamp,
                        "resolvedAt" to FieldValue.delete(),
                        "resolvedBy" to FieldValue.delete(),
                        "resolution" to FieldValue.delete()
                    )
                    batch.update(complaintDocRef, updates)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update main document", e)
                }
            }

            // Update search index
            try {
                val searchIndexRef = firestore.collection("complaint_search_index").document(complaintId)
                batch.update(searchIndexRef, mapOf(
                    "status" to "open",
                    "updatedAt" to timestamp
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Could not update search index", e)
            }

            // Update flat structure
            try {
                val flatRef = firestore.collection("all_complaints").document(complaintId)
                batch.update(flatRef, mapOf(
                    "status" to "Open",
                    "updatedAt" to timestamp
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Could not update flat structure", e)
            }

            // Update company statistics
            try {
                val companyStatsRef = firestore.collection("companies_metadata")
                    .document(currentUser.sanitizedCompanyName)
                batch.update(companyStatsRef, mapOf(
                    "openComplaints" to FieldValue.increment(1),
                    "closedComplaints" to FieldValue.increment(-1)
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Could not update statistics", e)
            }

            batch.commit().await()
            Log.d(TAG, "Complaint $complaintId reopened successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error reopening complaint", e)
            throw e
        }
    }

    suspend fun closeComplaint(complaintId: String, resolution: String, currentUser: UserData) {
        try {
            val batch = firestore.batch()
            val timestamp = Timestamp.now()

            // Find the complaint first
            val complaint = findComplaintById(complaintId)
            if (complaint == null) {
                throw Exception("Complaint not found")
            }

            // Update main complaint document if path exists
            if (complaint.documentPath.isNotEmpty()) {
                try {
                    val complaintDocRef = firestore.document(complaint.documentPath)
                    val updates = mapOf(
                        "status" to mapOf(
                            "current" to "Closed",
                            "history" to FieldValue.arrayUnion(
                                mapOf(
                                    "status" to "Closed",
                                    "changedAt" to timestamp,
                                    "changedBy" to currentUser.userId,
                                    "reason" to "Complaint resolved: $resolution"
                                )
                            )
                        ),
                        "resolution" to resolution,
                        "resolvedAt" to timestamp,
                        "resolvedBy" to currentUser.userId,
                        "updatedAt" to timestamp,
                        "lastModified" to timestamp
                    )
                    batch.update(complaintDocRef, updates)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update main document", e)
                }
            }

            // Update search index
            try {
                val searchIndexRef = firestore.collection("complaint_search_index").document(complaintId)
                batch.update(searchIndexRef, mapOf(
                    "status" to "closed",
                    "resolution" to resolution,
                    "updatedAt" to timestamp
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Could not update search index", e)
            }

            // Update flat structure
            try {
                val flatRef = firestore.collection("all_complaints").document(complaintId)
                batch.update(flatRef, mapOf(
                    "status" to "Closed",
                    "resolution" to resolution,
                    "updatedAt" to timestamp
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Could not update flat structure", e)
            }

            // Update company statistics
            try {
                val companyStatsRef = firestore.collection("companies_metadata")
                    .document(currentUser.sanitizedCompanyName)
                batch.update(companyStatsRef, mapOf(
                    "closedComplaints" to FieldValue.increment(1),
                    "openComplaints" to FieldValue.increment(-1)
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Could not update statistics", e)
            }

            batch.commit().await()
            Log.d(TAG, "Complaint $complaintId closed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error closing complaint", e)
            throw e
        }
    }

    suspend fun changeComplaintStatus(
        complaintId: String,
        newStatus: String,
        reason: String,
        currentUser: UserData
    ) {
        try {
            val batch = firestore.batch()
            val timestamp = Timestamp.now()

            // Find the complaint first
            val complaint = findComplaintById(complaintId)
            if (complaint == null) {
                throw Exception("Complaint not found")
            }

            // Update main complaint document if path exists
            if (complaint.documentPath.isNotEmpty()) {
                try {
                    val complaintDocRef = firestore.document(complaint.documentPath)
                    val updates = mapOf(
                        "status" to mapOf(
                            "current" to newStatus,
                            "history" to FieldValue.arrayUnion(
                                mapOf(
                                    "status" to newStatus,
                                    "changedAt" to timestamp,
                                    "changedBy" to currentUser.userId,
                                    "reason" to reason
                                )
                            )
                        ),
                        "updatedAt" to timestamp,
                        "lastModified" to timestamp
                    )
                    batch.update(complaintDocRef, updates)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update main document", e)
                }
            }

            // Update search index
            try {
                val searchIndexRef = firestore.collection("complaint_search_index").document(complaintId)
                batch.update(searchIndexRef, mapOf(
                    "status" to newStatus.lowercase(),
                    "updatedAt" to timestamp
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Could not update search index", e)
            }

            // Update flat structure
            try {
                val flatRef = firestore.collection("all_complaints").document(complaintId)
                batch.update(flatRef, mapOf(
                    "status" to newStatus,
                    "updatedAt" to timestamp
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Could not update flat structure", e)
            }

            batch.commit().await()
            Log.d(TAG, "Complaint $complaintId status changed to $newStatus successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error changing complaint status", e)
            throw e
        }
    }

    suspend fun updateComplaint(
        complaintId: String,
        updates: ComplaintUpdates,
        currentUser: UserData
    ) {
        try {
            val batch = firestore.batch()
            val timestamp = Timestamp.now()

            // Calculate urgency order for sorting
            val urgencyOrder = when(updates.urgency) {
                "Critical" -> 4
                "High" -> 3
                "Medium" -> 2
                "Low" -> 1
                else -> 1
            }

            val priority = calculatePriority(updates.urgency, updates.category)

            // Find the complaint first
            val complaint = findComplaintById(complaintId)
            if (complaint == null) {
                throw Exception("Complaint not found")
            }

            // Update main complaint document if path exists
            if (complaint.documentPath.isNotEmpty()) {
                try {
                    val complaintDocRef = firestore.document(complaint.documentPath)
                    val complaintUpdates = mapOf(
                        "title" to updates.title,
                        "description" to updates.description,
                        "department" to updates.category,
                        "originalCategory" to updates.category,
                        "urgency" to updates.urgency,
                        "priority" to priority,
                        "urgencyOrder" to urgencyOrder,
                        "updatedAt" to timestamp,
                        "lastModified" to timestamp,
                        "lastEditedBy" to currentUser.userId
                    )
                    batch.update(complaintDocRef, complaintUpdates)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update main document", e)
                }
            }

            // Update search index
            try {
                val searchIndexRef = firestore.collection("complaint_search_index").document(complaintId)
                val searchUpdates = mapOf(
                    "title" to updates.title.lowercase(),
                    "department" to updates.category.lowercase(),
                    "originalCategory" to updates.category.lowercase(),
                    "urgency" to updates.urgency.lowercase(),
                    "priority" to priority,
                    "updatedAt" to timestamp,
                    "searchTerms" to createSearchTerms(
                        updates.title,
                        updates.description,
                        updates.category,
                        updates.urgency
                    )
                )
                batch.update(searchIndexRef, searchUpdates)
            } catch (e: Exception) {
                Log.w(TAG, "Could not update search index", e)
            }

            // Update flat structure
            try {
                val flatRef = firestore.collection("all_complaints").document(complaintId)
                val flatUpdates = mapOf(
                    "title" to updates.title,
                    "department" to updates.category,
                    "originalCategory" to updates.category,
                    "urgency" to updates.urgency,
                    "updatedAt" to timestamp
                )
                batch.update(flatRef, flatUpdates)
            } catch (e: Exception) {
                Log.w(TAG, "Could not update flat structure", e)
            }

            batch.commit().await()
            Log.d(TAG, "Complaint $complaintId updated successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating complaint", e)
            throw e
        }
    }

    suspend fun deleteComplaint(complaintId: String, currentUser: UserData) {
        try {
            val batch = firestore.batch()

            // Find the complaint first
            val complaint = findComplaintById(complaintId)
            if (complaint == null) {
                throw Exception("Complaint not found")
            }

            // Delete main complaint document if path exists
            if (complaint.documentPath.isNotEmpty()) {
                try {
                    val complaintDocRef = firestore.document(complaint.documentPath)
                    batch.delete(complaintDocRef)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not delete main document", e)
                }
            }

            // Delete from search index
            try {
                val searchIndexRef = firestore.collection("complaint_search_index").document(complaintId)
                batch.delete(searchIndexRef)
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete from search index", e)
            }

            // Delete from flat structure
            try {
                val flatRef = firestore.collection("all_complaints").document(complaintId)
                batch.delete(flatRef)
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete from flat structure", e)
            }

            // Update statistics
            try {
                val companyStatsRef = firestore.collection("companies_metadata")
                    .document(currentUser.sanitizedCompanyName)
                val statsUpdate = mutableMapOf<String, Any>(
                    "totalComplaints" to FieldValue.increment(-1)
                )

                if (complaint.status.lowercase() == "open") {
                    statsUpdate["openComplaints"] = FieldValue.increment(-1)
                } else if (complaint.status.lowercase() == "closed") {
                    statsUpdate["closedComplaints"] = FieldValue.increment(-1)
                }

                batch.update(companyStatsRef, statsUpdate)
            } catch (e: Exception) {
                Log.w(TAG, "Could not update statistics", e)
            }

            batch.commit().await()
            Log.d(TAG, "Complaint $complaintId deleted successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error deleting complaint", e)
            throw e
        }
    }

    suspend fun getComplaintStatistics(sanitizedCompanyName: String): ComplaintStats? {
        return try {
            val companyDoc = firestore.collection("companies_metadata")
                .document(sanitizedCompanyName)
                .get()
                .await()

            if (companyDoc.exists()) {
                val data = companyDoc.data!!
                ComplaintStats(
                    totalComplaints = (data["totalComplaints"] as? Long)?.toInt() ?: 0,
                    openComplaints = (data["openComplaints"] as? Long)?.toInt() ?: 0,
                    closedComplaints = (data["closedComplaints"] as? Long)?.toInt() ?: 0,
                    inProgressComplaints = (data["inProgressComplaints"] as? Long)?.toInt() ?: 0,
                    averageResolutionTime = data["averageResolutionTime"] as? String ?: "N/A"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading complaint statistics", e)
            throw e
        }
    }

    private suspend fun findComplaintById(complaintId: String): ComplaintWithDetails? {
        return try {
            // Try search index first
            val searchDoc = firestore.collection("complaint_search_index")
                .document(complaintId)
                .get()
                .await()

            if (searchDoc.exists()) {
                val documentPath = searchDoc.getString("documentPath")
                if (!documentPath.isNullOrEmpty()) {
                    val complaintDoc = firestore.document(documentPath).get().await()
                    if (complaintDoc.exists()) {
                        return convertToComplaintWithDetails(complaintDoc)
                    }
                }
            }

            // Try flat structure
            val flatDoc = firestore.collection("all_complaints")
                .document(complaintId)
                .get()
                .await()

            if (flatDoc.exists()) {
                val hierarchicalPath = flatDoc.getString("hierarchicalPath")
                if (!hierarchicalPath.isNullOrEmpty()) {
                    val complaintDoc = firestore.document(hierarchicalPath).get().await()
                    if (complaintDoc.exists()) {
                        return convertToComplaintWithDetails(complaintDoc)
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding complaint by ID: $complaintId", e)
            null
        }
    }

    private fun getUrgencyPriority(urgency: String): Int {
        return when (urgency.lowercase()) {
            "critical" -> 4
            "high" -> 3
            "medium" -> 2
            "low" -> 1
            else -> 1
        }
    }

    private fun calculatePriority(urgency: String, category: String): Int {
        val urgencyScore = when (urgency) {
            "Critical" -> 4
            "High" -> 3
            "Medium" -> 2
            "Low" -> 1
            else -> 1
        }

        val categoryScore = when (category) {
            "Technical", "IT Support" -> 1
            "Administrative", "Finance" -> 0
            else -> 0
        }

        return urgencyScore + categoryScore
    }

    private fun createSearchTerms(
        title: String,
        description: String,
        category: String,
        urgency: String
    ): List<String> {
        return listOf(
            title.lowercase(),
            description.lowercase(),
            category.lowercase(),
            urgency.lowercase()
        ).flatMap { it.split("\\s+".toRegex()) }
            .filter { it.isNotEmpty() && it.length > 2 }
            .distinct()
    }
}