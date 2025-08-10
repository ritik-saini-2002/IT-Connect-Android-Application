    package com.example.ritik_2.profile.profilecompletion.components

    import android.net.Uri
    import androidx.lifecycle.ViewModel
    import com.example.ritik_2.data.User
    import com.example.ritik_2.data.UserProfile
    import com.example.ritik_2.data.WorkStats
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.asStateFlow

    data class ProfileCompletionUiState(
        val isLoading: Boolean = false,
        val currentProfile: UserProfile? = null,
        val currentWorkStats: WorkStats? = null,
        val selectedImageUri: Uri? = null,
        val error: String? = null,
        val dataExists: Boolean = false,
        val isDataLoaded: Boolean = false,
        val isNewUser: Boolean = false
    )

    class ProfileCompletionViewModel : ViewModel() {

        private val _uiState = MutableStateFlow(ProfileCompletionUiState())
        val uiState: StateFlow<ProfileCompletionUiState> = _uiState.asStateFlow()

        private val _currentUser = MutableStateFlow<User?>(null)
        val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

        fun setLoading(isLoading: Boolean) {
            _uiState.value = _uiState.value.copy(isLoading = isLoading)
        }

        fun setCurrentUser(user: User) {
            _currentUser.value = user
            _uiState.value = _uiState.value.copy(isDataLoaded = true)
        }

        fun setCurrentProfile(profile: UserProfile) {
            _uiState.value = _uiState.value.copy(
                currentProfile = profile,
                isDataLoaded = true
            )
        }

        fun setCurrentWorkStats(stats: WorkStats) {
            _uiState.value = _uiState.value.copy(
                currentWorkStats = stats,
                isDataLoaded = true
            )
        }

        fun setSelectedImageUri(uri: Uri) {
            _uiState.value = _uiState.value.copy(selectedImageUri = uri)
        }

        fun setError(error: String?) {
            _uiState.value = _uiState.value.copy(error = error)
        }

        fun setDataExists(exists: Boolean) {
            _uiState.value = _uiState.value.copy(dataExists = exists)
        }

        fun setNewUser(isNew: Boolean) {
            _uiState.value = _uiState.value.copy(isNewUser = isNew)
        }

        fun clearSelectedImage() {
            _uiState.value = _uiState.value.copy(selectedImageUri = null)
        }

        fun initializeNewUser(userId: String, email: String, displayName: String? = null) {
            val newUser = User(
                userId = userId,
                email = email,
                name = displayName ?: "",
                role = "Employee",
                companyName = "",
                sanitizedCompanyName = "",
                department = "",
                sanitizedDepartment = "",
                designation = "",
                documentPath = "", // Will be set during save
                isActive = true,
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis(),
                createdBy = userId
            )

            _currentUser.value = newUser
            _uiState.value = _uiState.value.copy(
                isDataLoaded = true,
                dataExists = false,
                isNewUser = true
            )
        }
    }

    data class ProfileData(
        val name: String = "",
        val email: String = "",
        val role: String = "Employee",
        val companyName: String = "",
        val department: String = "",
        val designation: String = "",
        val phoneNumber: String = "",
        val address: String = "",
        val dateOfBirth: Long? = null,
        val joiningDate: Long? = null,
        val employeeId: String = "",
        val reportingTo: String = "",
        val salary: Double = 0.0,
        val emergencyContactName: String = "",
        val emergencyContactPhone: String = "",
        val emergencyContactRelation: String = "",
        val experience: Int = 0,
        val imageUrl: String = "",
        val imageUri: Uri? = null
    )