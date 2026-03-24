package com.example.ritik_2.complaint.newcomplaintregistration.models

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.complaint.newcomplaintmodel.ComplaintLiveRepository
import com.example.ritik_2.complaint.newcomplaintmodel.CurrentUserSession
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * ComplaintFormViewModel
 *
 * Owns ALL business logic for the complaint registration screen:
 *   • Loading user profile + profile picture URL from Firebase
 *   • Managing form field state (via UI events)
 *   • File attachment validation
 *   • Building and saving the complaint to all 3 Firestore tiers
 *   • Notifying department heads via ComplaintLiveRepository
 *
 * The Activity/Screen only calls:
 *   • loadUser(uid)
 *   • onEvent(ComplaintFormEvent)
 *   • collect uiState
 *
 * No UI logic lives here. No Compose imports.
 */
class ComplaintFormViewModel : ViewModel() {

    private val auth     = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage  = FirebaseStorage.getInstance()
    private val repo     = ComplaintLiveRepository()

    private val _uiState = MutableStateFlow(ComplaintFormUiState())
    val uiState: StateFlow<ComplaintFormUiState> = _uiState.asStateFlow()

    // Holds the picked file URI — kept in ViewModel so it survives recompositions
    private var selectedFileUri: Uri? = null

    companion object {
        private const val TAG = "ComplaintFormVM"
        private const val MAX_FILE_BYTES = 2L * 1024 * 1024 // 2 MB
    }

    // ─────────────────────────────────────────────
    // LOAD USER
    // ─────────────────────────────────────────────

    fun loadUser(uid: String) {
        viewModelScope.launch {
            try {
                // 1. Load basic user data from user_access_control
                val doc  = firestore.collection("user_access_control").document(uid).get().await()
                val data = doc.data ?: return@launch

                val name                 = data["name"] as? String ?: ""
                val role                 = data["role"] as? String ?: ""
                val dept                 = data["department"] as? String ?: ""
                val sanitizedCompany     = data["sanitizedCompanyName"] as? String ?: ""
                val sanitizedDept        = data["sanitizedDepartment"] as? String ?: ""
                val docPath              = data["documentPath"] as? String ?: ""

                _uiState.update { it.copy(userName = name, userRole = role, userDept = dept) }

                // 2. Try to resolve the profile picture URL
                val picUrl = resolveProfilePictureUrl(
                    uid              = uid,
                    sanitizedCompany = sanitizedCompany,
                    sanitizedDept    = sanitizedDept,
                    role             = role,
                    docPath          = docPath
                )

                _uiState.update { it.copy(profilePictureUrl = picUrl, profileLoaded = true) }

            } catch (e: Exception) {
                Log.e(TAG, "loadUser failed", e)
                // Still mark as loaded so the UI shows the fallback avatar
                _uiState.update { it.copy(profileLoaded = true) }
            }
        }
    }

    /**
     * Tries several known Firebase Storage paths, then falls back to
     * the imageUrl stored in the Firestore user document.
     * Returns null if nothing is found.
     */
    private suspend fun resolveProfilePictureUrl(
        uid: String,
        sanitizedCompany: String,
        sanitizedDept: String,
        role: String,
        docPath: String
    ): String? {
        // Storage paths — ordered from most to least likely
        val storagePaths = listOf(
            "users/$sanitizedCompany/$sanitizedDept/$role/$uid/profile.jpg",
            "users/$sanitizedCompany/$sanitizedDept/$role/users/$uid/profile.jpg",
            "users/$sanitizedCompany/$role/$uid/profile.jpg",
            "users/$sanitizedCompany/$sanitizedDept/$role/${uid}_profile_image.jpg"
        )
        for (path in storagePaths) {
            try {
                return storage.reference.child(path).downloadUrl.await().toString()
            } catch (_: Exception) { /* try next */ }
        }

        // Firestore fallback: check nested profile.imageUrl or top-level imageUrl
        if (docPath.isNotBlank()) {
            try {
                val userDoc = firestore.document(docPath).get().await()
                val nested  = (userDoc.data?.get("profile") as? Map<*, *>)?.get("imageUrl") as? String
                val topLevel = userDoc.data?.get("imageUrl") as? String
                val url = nested?.takeIf { it.isNotBlank() } ?: topLevel?.takeIf { it.isNotBlank() }
                if (url != null) return url
            } catch (_: Exception) { }
        }
        return null
    }

    // ─────────────────────────────────────────────
    // UI EVENTS
    // ─────────────────────────────────────────────

    fun onEvent(event: ComplaintFormEvent) {
        when (event) {
            is ComplaintFormEvent.TitleChanged ->
                _uiState.update { it.copy(title = event.value.take(100)) }

            is ComplaintFormEvent.DescriptionChanged ->
                _uiState.update { it.copy(description = event.value.take(1000)) }

            is ComplaintFormEvent.CategoryChanged ->
                _uiState.update { it.copy(selectedCategory = event.value) }

            is ComplaintFormEvent.UrgencyChanged ->
                _uiState.update { it.copy(selectedUrgency = event.value) }

            is ComplaintFormEvent.ContactInfoChanged ->
                _uiState.update { it.copy(contactInfo = event.value) }

            is ComplaintFormEvent.GlobalToggled ->
                _uiState.update { it.copy(isGlobal = event.value) }

            is ComplaintFormEvent.FilePicked -> {
                selectedFileUri = event.uri
                _uiState.update { it.copy(hasAttachment = true) }
            }

            ComplaintFormEvent.FileRemoved -> {
                selectedFileUri = null
                _uiState.update { it.copy(hasAttachment = false) }
            }

            ComplaintFormEvent.ResetForm -> {
                selectedFileUri = null
                _uiState.update {
                    it.copy(
                        title            = "",
                        description      = "",
                        selectedCategory = "Technical",
                        selectedUrgency  = "Medium",
                        contactInfo      = "",
                        hasAttachment    = false,
                        isGlobal         = false,
                        submitError      = null
                    )
                }
            }

            ComplaintFormEvent.ClearError ->
                _uiState.update { it.copy(submitError = null) }
        }
    }

    // ─────────────────────────────────────────────
    // SUBMIT
    // ─────────────────────────────────────────────

    /**
     * @param contentResolver  needed to check file size; pass from Activity/Context
     * @param onSuccess        called on the main thread when Firestore writes succeed
     */
    fun submitComplaint(
        contentResolver: ContentResolver,
        onSuccess: () -> Unit
    ) {
        val state = _uiState.value
        val uid   = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(submitError = "Not authenticated. Please log in again.") }
            return
        }

        if (!state.isFormValid) {
            _uiState.update { it.copy(submitError = "Title and description are required.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null) }
            try {
                // Load fresh session from Firestore
                val userDoc = firestore.collection("user_access_control").document(uid).get().await()
                if (!userDoc.exists()) {
                    _uiState.update { it.copy(isSubmitting = false, submitError = "User profile not found.") }
                    return@launch
                }
                val uData = userDoc.data!!
                val session = CurrentUserSession(
                    userId = uData["userId"] as? String ?: uid,
                    name = uData["name"] as? String ?: "Unknown",
                    email = uData["email"] as? String ?: "",
                    role = uData["role"] as? String ?: "Employee",
                    companyName = uData["companyName"] as? String ?: "",
                    sanitizedCompanyName = uData["sanitizedCompanyName"] as? String ?: "",
                    department = uData["department"] as? String ?: "",
                    sanitizedDepartment = uData["sanitizedDepartment"] as? String ?: ""
                )

                // File size check
                if (state.hasAttachment && selectedFileUri != null) {
                    val fileSize = contentResolver
                        .openFileDescriptor(selectedFileUri!!, "r")
                        ?.use { it.statSize } ?: 0L
                    if (fileSize > MAX_FILE_BYTES) {
                        _uiState.update { it.copy(isSubmitting = false, submitError = "File must be under 2 MB.") }
                        return@launch
                    }
                }

                // Build IDs
                val sanitized   = sanitizeComplaintId(state.title)
                val complaintId = "${sanitized}_${UUID.randomUUID().toString().take(6)}"
                val now         = Timestamp.now()
                val nowMs       = System.currentTimeMillis()
                val deptName    = mapCategoryToDepartment(state.selectedCategory)
                val priority    = calculatePriority(state.selectedUrgency, deptName)
                val estTime     = estimatedResolutionTime(state.selectedUrgency)
                val isGlobal    = state.isGlobal

                // Upload attachment
                val (attachmentUrl, attachmentFileName, attachmentFileSize) = uploadAttachment(
                    session, complaintId, state.hasAttachment
                )

                // Firestore paths
                val complaintPath = if (isGlobal)
                    "complaints/${session.sanitizedCompanyName}/global_complaints/$complaintId"
                else
                    "complaints/${session.sanitizedCompanyName}/department_complaints/$complaintId"

                // Build maps
                val createdEvent = buildTimelineEvent(nowMs, "Open", "Complaint Created",
                    "Raised by ${session.name}", now, session.name, session.userId, "CREATED")

                val createdByMap = buildCreatedByMap(session, uData, state.contactInfo)

                val attachmentMap = if (state.hasAttachment && attachmentUrl != null) mapOf(
                    "hasFile"    to true,
                    "url"        to attachmentUrl,
                    "fileName"   to (attachmentFileName ?: ""),
                    "fileSize"   to (attachmentFileSize ?: 0L),
                    "uploadedAt" to now
                ) else mapOf("hasFile" to false)

                val tags         = generateTags(state.title, state.description, deptName)
                val searchTerms  = createSearchTerms(
                    state.title,
                    state.description,
                    deptName,
                    state.selectedUrgency
                )

                // Full hierarchical document
                val fullDoc = buildFullComplaintDoc(
                    complaintId, state, deptName, priority, estTime, complaintPath,
                    session, createdByMap, createdEvent, attachmentMap, tags, searchTerms, now
                )

                // Flat document for all_complaints
                val flatDoc = buildFlatComplaintDoc(
                    complaintId, state, deptName, complaintPath, session, createdEvent, attachmentMap, priority, now
                )

                // Search index document
                val searchDoc = buildSearchIndexDoc(
                    complaintId, state, deptName, complaintPath, session, searchTerms, now
                )

                // Batch write
                val batch = firestore.batch()
                batch.set(firestore.document(complaintPath), fullDoc)
                batch.set(firestore.collection("all_complaints").document(complaintId), flatDoc)
                batch.set(firestore.collection("complaint_search_index").document(complaintId), searchDoc)

                // Stats updates — wrapped in try/catch so they never fail the batch
                try {
                    batch.update(
                        firestore.collection("companies_metadata").document(session.sanitizedCompanyName),
                        mapOf(
                            "totalComplaints"    to FieldValue.increment(1),
                            "openComplaints"     to FieldValue.increment(1),
                            "lastComplaintDate"  to now,
                            "lastUpdated"        to now
                        )
                    )
                } catch (_: Exception) { }

                try {
                    batch.update(
                        firestore.collection("user_access_control").document(session.userId),
                        mapOf(
                            "complaintStats.totalSubmitted"     to FieldValue.increment(1),
                            "complaintStats.lastSubmissionDate" to now
                        )
                    )
                } catch (_: Exception) { }

                batch.commit().await()

                // Notify heads (fire and forget — failure doesn't affect UX)
                viewModelScope.launch {
                    try {
                        repo.notifyDepartmentHeads(
                            sanitizedCompanyName = session.sanitizedCompanyName,
                            sanitizedDepartment  = session.sanitizedDepartment,
                            complaintId          = complaintId,
                            complaintTitle       = state.title,
                            raisedByName         = session.name,
                            raisedByUserId       = session.userId,
                            urgency              = state.selectedUrgency
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "notifyDepartmentHeads failed (non-critical)", e)
                    }
                }

                // Reset and notify
                selectedFileUri = null
                _uiState.update { it.copy(isSubmitting = false, submitSuccess = true) }
                onSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "submitComplaint failed", e)
                _uiState.update {
                    it.copy(isSubmitting = false, submitError = "Submission failed: ${e.message}")
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────

    /** Uploads the selected file to Firebase Storage. Returns (url, fileName, fileSize). */
    private suspend fun uploadAttachment(
        session: CurrentUserSession,
        complaintId: String,
        hasAttachment: Boolean
    ): Triple<String?, String?, Long?> {
        if (!hasAttachment || selectedFileUri == null) return Triple(null, null, null)
        return try {
            val ref = storage.reference
                .child("complaints/${session.sanitizedCompanyName}/$complaintId/attachment")
            val result   = ref.putFile(selectedFileUri!!).await()
            val url      = ref.downloadUrl.await().toString()
            val fileName = result.metadata?.name
            val fileSize = result.metadata?.sizeBytes
            Triple(url, fileName, fileSize)
        } catch (e: Exception) {
            Log.w(TAG, "Attachment upload failed (non-critical)", e)
            Triple(null, null, null)
        }
    }

    private fun buildTimelineEvent(
        nowMs: Long, status: String, title: String, description: String,
        now: Timestamp, performedBy: String, performedByUserId: String, type: String
    ): Map<String, Any> = mapOf(
        "eventId"           to "evt_$nowMs",
        "status"            to status,
        "title"             to title,
        "description"       to description,
        "timestamp"         to now,
        "performedBy"       to performedBy,
        "performedByUserId" to performedByUserId,
        "type"              to type
    )

    private fun buildCreatedByMap(
        session: CurrentUserSession,
        uData: Map<String, Any>,
        contactInfo: String
    ): Map<String, Any> = mapOf(
        "userId"               to session.userId,
        "name"                 to session.name,
        "email"                to session.email,
        "department"           to session.department,
        "role"                 to session.role,
        "designation"          to (uData["designation"] as? String ?: ""),
        "contactInfo"          to contactInfo,
        "companyName"          to session.companyName,
        "sanitizedCompanyName" to session.sanitizedCompanyName,
        "userDocumentPath"     to (uData["documentPath"] as? String ?: "")
    )

    private fun buildFullComplaintDoc(
        complaintId: String,
        state: ComplaintFormUiState,
        deptName: String,
        priority: Int,
        estTime: String,
        complaintPath: String,
        session: CurrentUserSession,
        createdByMap: Map<String, Any>,
        createdEvent: Map<String, Any>,
        attachmentMap: Map<String, Any>,
        tags: List<String>,
        searchTerms: List<String>,
        now: Timestamp
    ): HashMap<String, Any> = hashMapOf(
        "complaintId"             to complaintId,
        "originalTitle"           to state.title,
        "sanitizedTitle"          to complaintId,
        "title"                   to state.title,
        "description"             to state.description,
        "department"              to deptName,
        "originalCategory"        to state.selectedCategory,
        "urgency"                 to state.selectedUrgency,
        "priority"                to priority,
        "estimatedResolutionTime" to estTime,
        "documentPath"            to complaintPath,
        "companyName"             to session.companyName,
        "sanitizedCompanyName"    to session.sanitizedCompanyName,
        "userDepartment"          to session.department,
        "sanitizedUserDepartment" to session.sanitizedDepartment,
        "isGlobal"                to state.isGlobal,
        "createdBy"               to createdByMap,
        "status"                  to mapOf(
            "current" to "Open",
            "history" to listOf(createdEvent)
        ),
        "timeline"                to listOf(createdEvent),
        "attachment"              to attachmentMap,
        "createdAt"               to now,
        "updatedAt"               to now,
        "lastModified"            to now,
        "tags"                    to tags,
        "searchTerms"             to searchTerms
    )

    private fun buildFlatComplaintDoc(
        complaintId: String,
        state: ComplaintFormUiState,
        deptName: String,
        complaintPath: String,
        session: CurrentUserSession,
        createdEvent: Map<String, Any>,
        attachmentMap: Map<String, Any>,
        priority: Int,
        now: Timestamp
    ): Map<String, Any> = mapOf(
        "complaintId"             to complaintId,
        "originalTitle"           to state.title,
        "title"                   to state.title,
        "department"              to deptName,
        "originalCategory"        to state.selectedCategory,
        "urgency"                 to state.selectedUrgency,
        "status"                  to "Open",
        "companyName"             to session.companyName,
        "sanitizedCompanyName"    to session.sanitizedCompanyName,
        "userDepartment"          to session.department,
        "sanitizedUserDepartment" to session.sanitizedDepartment,
        "createdBy"               to mapOf(
            "userId"     to session.userId,
            "name"       to session.name,
            "email"      to session.email,
            "department" to session.department,
            "role"       to session.role
        ),
        "isGlobal"         to state.isGlobal,
        "hierarchicalPath" to complaintPath,
        "documentPath"     to complaintPath,
        "timeline"         to listOf(createdEvent),
        "attachment"       to attachmentMap,
        "priority"         to priority,
        "createdAt"        to now,
        "updatedAt"        to now
    )

    private fun buildSearchIndexDoc(
        complaintId: String,
        state: ComplaintFormUiState,
        deptName: String,
        complaintPath: String,
        session: CurrentUserSession,
        searchTerms: List<String>,
        now: Timestamp
    ): Map<String, Any> = mapOf(
        "complaintId"             to complaintId,
        "title"                   to state.title.lowercase(),
        "originalTitle"           to state.title,
        "department"              to deptName.lowercase(),
        "originalCategory"        to state.selectedCategory.lowercase(),
        "urgency"                 to state.selectedUrgency.lowercase(),
        "status"                  to "open",
        "companyName"             to session.companyName,
        "sanitizedCompanyName"    to session.sanitizedCompanyName,
        "userDepartment"          to session.department,
        "sanitizedUserDepartment" to session.sanitizedDepartment,
        "createdBy"               to session.name.lowercase(),
        "isGlobal"                to state.isGlobal,
        "documentPath"            to complaintPath,
        "createdAt"               to now,
        "searchTerms"             to searchTerms
    )
}

// ─────────────────────────────────────────────
// EVENTS
// ─────────────────────────────────────────────

/**
 * All possible actions the UI can trigger.
 * The ViewModel processes each one and updates uiState.
 */
sealed class ComplaintFormEvent {
    data class TitleChanged(val value: String)        : ComplaintFormEvent()
    data class DescriptionChanged(val value: String)  : ComplaintFormEvent()
    data class CategoryChanged(val value: String)     : ComplaintFormEvent()
    data class UrgencyChanged(val value: String)      : ComplaintFormEvent()
    data class ContactInfoChanged(val value: String)  : ComplaintFormEvent()
    data class GlobalToggled(val value: Boolean)      : ComplaintFormEvent()
    data class FilePicked(val uri: Uri)               : ComplaintFormEvent()
    object FileRemoved                                : ComplaintFormEvent()
    object ResetForm                                  : ComplaintFormEvent()
    object ClearError                                 : ComplaintFormEvent()
}
