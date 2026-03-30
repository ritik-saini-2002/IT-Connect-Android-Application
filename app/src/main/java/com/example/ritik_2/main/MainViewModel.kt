package com.example.ritik_2.main

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.authentication.AuthManager
import com.example.ritik_2.data.pocketbase.PocketBaseClient
import com.example.ritik_2.data.pocketbase.PocketBaseSessionManager
import com.example.ritik_2.registration.models.UserRecord
import io.github.agrevster.pocketbaseKotlin.models.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

// ── UI State ──────────────────────────────────────────────────
data class MainUiState(
    val isLoading: Boolean          = true,
    val userProfile: UserProfileData? = null,
    val error: String?              = null,
    val isRefreshing: Boolean       = false
)

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG            = "MainViewModel"
        private const val CACHE_TTL_MS   = 5 * 60 * 1000L // 5 min cache
        private const val RETRY_DELAY_MS = 2000L
        private const val MAX_RETRIES    = 3
    }

    private val pb          = PocketBaseClient.instance
    private val authManager = AuthManager.getInstance()

    // ── State ─────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // ── Cache ─────────────────────────────────────────────────
    private var cachedProfile: UserProfileData? = null
    private var cacheTimestamp: Long            = 0L
    private var loadJob: Job?                   = null

    // ── Load user profile ─────────────────────────────────────
    fun loadUserProfile(userId: String, forceRefresh: Boolean = false) {
        // Prevent duplicate concurrent loads
        if (loadJob?.isActive == true && !forceRefresh) return

        loadJob = viewModelScope.launch {
            try {
                // Serve from cache if fresh
                if (!forceRefresh && isCacheValid()) {
                    _uiState.update { it.copy(userProfile = cachedProfile, isLoading = false) }
                    Log.d(TAG, "Served from cache ✅")
                    return@launch
                }

                _uiState.update { it.copy(isLoading = true, error = null) }

                val profile = loadWithRetry(userId)

                if (profile != null) {
                    cachedProfile  = profile
                    cacheTimestamp = System.currentTimeMillis()
                    _uiState.update { it.copy(
                        userProfile = profile,
                        isLoading   = false,
                        error       = null
                    )}
                    Log.d(TAG, "Profile loaded: ${profile.name} ✅")
                } else {
                    _uiState.update { it.copy(
                        isLoading = false,
                        error     = "Profile not found. Please contact administrator."
                    )}
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadUserProfile failed: ${e.message}", e)
                _uiState.update { it.copy(
                    isLoading = false,
                    error     = "Failed to load profile: ${e.message}"
                )}
            }
        }
    }

    // ── Retry logic ───────────────────────────────────────────
    private suspend fun loadWithRetry(userId: String): UserProfileData? {
        repeat(MAX_RETRIES) { attempt ->
            try {
                return fetchProfile(userId)
            } catch (e: Exception) {
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY_MS)
            }
        }
        return null
    }

    // ── Fetch from PocketBase ─────────────────────────────────
    private suspend fun fetchProfile(userId: String): UserProfileData? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Get access control record (fastest — flat lookup)
                val accessResult = pb.records.getList<AccessRecord>(
                    "user_access_control", 1, 1,
                    "userId='$userId'"
                )

                if (accessResult.totalItems == 0) {
                    Log.w(TAG, "No access control record for: $userId")
                    return@withContext buildProfileFromSession()
                }

                val access = accessResult.items.first()

                if (!access.isActive) {
                    throw Exception("deactivated")
                }

                // 2. Get full user record from users collection
                val userRecord = pb.records.getOne<UserRecord>(
                    "users", userId
                )

                // 3. Parse nested JSON fields
                val profileJson  = parseJsonSafe(userRecord.profile)
                val workJson     = parseJsonSafe(userRecord.workStats)
                val issuesJson   = parseJsonSafe(userRecord.issues)

                val imageUrl = profileJson?.get("imageUrl")?.jsonPrimitive?.content ?: ""
                val imageUri = if (imageUrl.isNotBlank()) {
                    try { Uri.parse(imageUrl) } catch (_: Exception) { null }
                } else null

                UserProfileData(
                    id                 = userId,
                    name               = userRecord.name.ifBlank { access.name },
                    email              = access.email,
                    role               = access.role,
                    companyName        = userRecord.companyName,
                    designation        = userRecord.designation,
                    imageUrl           = imageUri,
                    phoneNumber        = profileJson?.get("phoneNumber")?.jsonPrimitive?.content ?: "",
                    experience         = workJson?.get("experience")?.jsonPrimitive?.intOrNull ?: 0,
                    completedProjects  = workJson?.get("completedProjects")?.jsonPrimitive?.intOrNull ?: 0,
                    activeProjects     = workJson?.get("activeProjects")?.jsonPrimitive?.intOrNull ?: 0,
                    pendingTasks       = workJson?.get("pendingTasks")?.jsonPrimitive?.intOrNull ?: 0,
                    completedTasks     = workJson?.get("completedTasks")?.jsonPrimitive?.intOrNull ?: 0,
                    totalComplaints    = issuesJson?.get("totalComplaints")?.jsonPrimitive?.intOrNull ?: 0,
                    resolvedComplaints = issuesJson?.get("resolvedComplaints")?.jsonPrimitive?.intOrNull ?: 0,
                    pendingComplaints  = issuesJson?.get("pendingComplaints")?.jsonPrimitive?.intOrNull ?: 0,
                    isActive           = access.isActive,
                    documentPath       = access.documentPath,
                    permissions        = parsePermissions(access.permissions)
                )

            } catch (e: Exception) {
                if (e.message?.contains("deactivated") == true) throw e
                Log.e(TAG, "fetchProfile error: ${e.message}", e)
                // Return session-based fallback
                buildProfileFromSession()
            }
        }
    }

    // ── Build minimal profile from saved session ──────────────
    private fun buildProfileFromSession(): UserProfileData? {
        val userId = PocketBaseSessionManager.getUserId() ?: return null
        return UserProfileData(
            id          = userId,
            name        = PocketBaseSessionManager.getName() ?: "User",
            email       = PocketBaseSessionManager.getEmail() ?: "",
            role        = PocketBaseSessionManager.getRole() ?: "",
            companyName = "",
            documentPath = PocketBaseSessionManager.getDocPath() ?: ""
        )
    }

    // ── Refresh (pull to refresh) ─────────────────────────────
    fun refresh() {
        val userId = PocketBaseSessionManager.getUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadUserProfile(userId, forceRefresh = true)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // ── Clear error ───────────────────────────────────────────
    fun clearError() = _uiState.update { it.copy(error = null) }

    // ── Cache helpers ─────────────────────────────────────────
    private fun isCacheValid(): Boolean =
        cachedProfile != null &&
                (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS

    // ── JSON helpers ──────────────────────────────────────────
    private fun parseJsonSafe(raw: String): JsonObject? {
        return try {
            Json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) { null }
    }

    private fun parsePermissions(raw: String): List<String> {
        return try {
            Json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) { emptyList() }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
    }
}

// ── Lightweight model for access control fetch ────────────────
@kotlinx.serialization.Serializable
data class AccessRecord(
    val userId: String       = "",
    val name: String         = "",
    val email: String        = "",
    val role: String         = "",
    val isActive: Boolean    = true,
    val documentPath: String = "",
    val permissions: String  = "[]"
) : Record()

// ── Full UI profile data ──────────────────────────────────────
data class UserProfileData(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val companyName: String,
    val designation: String         = "IT Professional",
    val imageUrl: Uri?              = null,
    val phoneNumber: String         = "",
    val skills: List<String>        = emptyList(),
    val experience: Int             = 0,
    val completedProjects: Int      = 0,
    val activeProjects: Int         = 0,
    val pendingTasks: Int           = 0,
    val completedTasks: Int         = 0,
    val totalComplaints: Int        = 0,
    val resolvedComplaints: Int     = 0,
    val pendingComplaints: Int      = 0,
    val isActive: Boolean           = true,
    val documentPath: String        = "",
    val permissions: List<String>   = emptyList(),

    val performanceScore: Double = if (completedProjects + activeProjects > 0)
        (completedProjects.toDouble() / (completedProjects + activeProjects)) * 100
    else 0.0,

    val complaintsRate: Double = if (totalComplaints > 0)
        (resolvedComplaints.toDouble() / totalComplaints) * 100
    else 100.0
)