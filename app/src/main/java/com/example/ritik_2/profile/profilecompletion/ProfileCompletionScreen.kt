package com.example.ritik_2.profile.profilecompletion

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Enhanced ThemedColors for Ritik_2Theme
object ThemedColors {
    @Composable
    fun primary(isDark: Boolean) = if (isDark) Color(0xFF8B8CF6) else Color(0xFF6366F1)

    @Composable
    fun background(isDark: Boolean) = if (isDark) Color(0xFF121212) else Color(0xFFFAFAFA)

    @Composable
    fun surface(isDark: Boolean) = if (isDark) Color(0xFF1E1E1E) else Color.White

    @Composable
    fun surfaceVariant(isDark: Boolean) = if (isDark) Color(0xFF2D2D2D) else Color(0xFFF3F4F6)

    @Composable
    fun onSurface(isDark: Boolean) = if (isDark) Color(0xFFE0E0E0) else Color(0xFF374151)

    @Composable
    fun onSurfaceVariant(isDark: Boolean) = if (isDark) Color(0xFFB0B0B0) else Color(0xFF6B7280)

    @Composable
    fun error(isDark: Boolean) = if (isDark) Color(0xFFFF6B6B) else Color(0xFFEF4444)

    @Composable
    fun outline(isDark: Boolean) = if (isDark) Color(0xFF4A4A4A) else Color(0xFFE5E7EB)

    @Composable
    fun success(isDark: Boolean) = if (isDark) Color(0xFF4ADE80) else Color(0xFF10B981)

    @Composable
    fun warning(isDark: Boolean) = if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B)
}

// User Permissions Data Class
data class UserPermissions(
    val role: String,
    val permissions: List<String>,
    val canModifyAll: Boolean,
    val canModifyPersonal: Boolean,
    val canModifyPhoto: Boolean
) {
    companion object {
        fun fromRole(role: String): UserPermissions {
            return when (role) {
                "Administrator" -> UserPermissions(
                    role = role,
                    permissions = listOf("modify_all_data", "create_user", "delete_user", "manage_roles"),
                    canModifyAll = true,
                    canModifyPersonal = true,
                    canModifyPhoto = true
                )
                "Manager" -> UserPermissions(
                    role = role,
                    permissions = listOf("modify_personal_data", "view_team_analytics"),
                    canModifyAll = false,
                    canModifyPersonal = true,
                    canModifyPhoto = true
                )
                "HR" -> UserPermissions(
                    role = role,
                    permissions = listOf("modify_personal_data", "view_all_users"),
                    canModifyAll = false,
                    canModifyPersonal = true,
                    canModifyPhoto = true
                )
                "Team Lead" -> UserPermissions(
                    role = role,
                    permissions = listOf("modify_personal_data", "view_team_users"),
                    canModifyAll = false,
                    canModifyPersonal = true,
                    canModifyPhoto = true
                )
                else -> UserPermissions(
                    role = role,
                    permissions = listOf("modify_personal_data"),
                    canModifyAll = false,
                    canModifyPersonal = true,
                    canModifyPhoto = false
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCompletionScreen(
    userId: String,
    userRole: String,
    isDarkTheme: Boolean,
    onProfileUpdateClick: (Map<String, Any>, String?, Uri?) -> Unit,
    onSkipClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val userPermissions = remember(userRole) { UserPermissions.fromRole(userRole) }

    // User data states
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var employeeId by remember { mutableStateOf("") }
    var reportingTo by remember { mutableStateOf("") }
    var joiningDate by remember { mutableStateOf("") }

    // Work stats
    var experience by remember { mutableStateOf("0") }
    var completedProjects by remember { mutableStateOf("0") }
    var activeProjects by remember { mutableStateOf("0") }
    var pendingTasks by remember { mutableStateOf("0") }
    var completedTasks by remember { mutableStateOf("0") }
    var totalWorkingHours by remember { mutableStateOf("0") }

    // Emergency contact
    var emergencyContactName by remember { mutableStateOf("") }
    var emergencyContactPhone by remember { mutableStateOf("") }
    var emergencyContactRelation by remember { mutableStateOf("") }

    // Image and password
    var currentImageUrl by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }

    // UI states
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var isDataLoaded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (userPermissions.canModifyPhoto) {
            imageUri = uri
        }
    }

    // Load existing user data
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            isLoading = true
            try {
                // First try to get access control data
                val accessControlDoc = FirebaseFirestore.getInstance()
                    .collection("user_access_control")
                    .document(userId)
                    .get()
                    .await()

                if (accessControlDoc.exists()) {
                    val accessData = accessControlDoc.data ?: emptyMap()

                    // Load basic data from access control
                    email = accessData["email"]?.toString() ?: ""
                    name = accessData["name"]?.toString() ?: ""
                    role = accessData["role"]?.toString() ?: userRole
                    companyName = accessData["companyName"]?.toString() ?: ""
                    department = accessData["department"]?.toString() ?: ""

                    // Try to get detailed data from hierarchical structure
                    val documentPath = accessData["documentPath"]?.toString()
                    if (!documentPath.isNullOrEmpty()) {
                        try {
                            val userDoc = FirebaseFirestore.getInstance()
                                .document(documentPath)
                                .get()
                                .await()

                            if (userDoc.exists()) {
                                val userData = userDoc.data ?: emptyMap()

                                // Basic info
                                designation = userData["designation"]?.toString() ?: ""

                                // Profile data
                                val profile = userData["profile"] as? Map<String, Any> ?: emptyMap()
                                phoneNumber = profile["phoneNumber"]?.toString() ?: ""
                                address = profile["address"]?.toString() ?: ""
                                dateOfBirth = profile["dateOfBirth"]?.toString() ?: ""
                                currentImageUrl = profile["imageUrl"]?.toString() ?: ""
                                salary = profile["salary"]?.toString() ?: ""
                                employeeId = profile["employeeId"]?.toString() ?: ""
                                reportingTo = profile["reportingTo"]?.toString() ?: ""
                                joiningDate = profile["joiningDate"]?.toString() ?: ""

                                // Emergency contact
                                val emergencyContact = profile["emergencyContact"] as? Map<String, Any> ?: emptyMap()
                                emergencyContactName = emergencyContact["name"]?.toString() ?: ""
                                emergencyContactPhone = emergencyContact["phone"]?.toString() ?: ""
                                emergencyContactRelation = emergencyContact["relation"]?.toString() ?: ""

                                // Work stats
                                val workStats = userData["workStats"] as? Map<String, Any> ?: emptyMap()
                                experience = (workStats["experience"] as? Number)?.toString() ?: "0"
                                completedProjects = (workStats["completedProjects"] as? Number)?.toString() ?: "0"
                                activeProjects = (workStats["activeProjects"] as? Number)?.toString() ?: "0"
                                pendingTasks = (workStats["pendingTasks"] as? Number)?.toString() ?: "0"
                                completedTasks = (workStats["completedTasks"] as? Number)?.toString() ?: "0"
                                totalWorkingHours = (workStats["totalWorkingHours"] as? Number)?.toString() ?: "0"

                                // Skills
                                val skillsList = userData["skills"] as? List<String> ?: emptyList()
                                skills = skillsList.joinToString(", ")
                            }
                        } catch (e: Exception) {
                            println("Error loading detailed user data: $e")
                        }
                    }
                } else {
                    // User doesn't exist - set default role
                    role = userRole
                }

                isDataLoaded = true

            } catch (e: Exception) {
                println("Exception loading user data: $e")
                errorMessage = "Error loading profile data: ${e.message}"
                isDataLoaded = true
            } finally {
                isLoading = false
            }
        }
    }

    // Form validation based on permissions
    val isFormValid = phoneNumber.isNotBlank() &&
            (newPassword.isEmpty() || (newPassword.length >= 6 && newPassword == confirmNewPassword)) &&
            (if (userPermissions.canModifyAll) {
                name.isNotBlank() && companyName.isNotBlank() && department.isNotBlank()
            } else true)

    // Loading screen
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = ThemedColors.primary(isDarkTheme),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Loading your profile...",
                    color = ThemedColors.onSurfaceVariant(isDarkTheme),
                    fontSize = 16.sp
                )
            }
        }
        return
    }

    // Error display
    errorMessage?.let { message ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = ThemedColors.error(isDarkTheme),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                message,
                color = ThemedColors.error(isDarkTheme),
                textAlign = TextAlign.Center,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { errorMessage = null },
                colors = ButtonDefaults.buttonColors(containerColor = ThemedColors.primary(isDarkTheme))
            ) {
                Text("Retry")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemedColors.background(isDarkTheme))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header Card with Ritik_2 Theme
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemedColors.primary(isDarkTheme)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ritik_2 Profile Setup",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append("Role: ${userPermissions.role}")
                        when {
                            userPermissions.canModifyAll -> append(" • Full Access")
                            userPermissions.canModifyPersonal -> append(" • Personal Access")
                            else -> append(" • View Only")
                        }
                    },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Permission Info Card
        PermissionInfoCard(userPermissions, isDarkTheme)

        Spacer(modifier = Modifier.height(16.dp))

        // Main Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemedColors.surface(isDarkTheme)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {

                // Profile Image Section
                ProfileImageSection(
                    imageUri = imageUri,
                    currentImageUrl = currentImageUrl,
                    onImagePick = {
                        if (userPermissions.canModifyPhoto) {
                            imagePickerLauncher.launch("image/*")
                        }
                    },
                    canModify = userPermissions.canModifyPhoto,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Core Information Section
                CoreInformationSection(
                    name = name,
                    onNameChange = { if (userPermissions.canModifyAll) name = it },
                    email = email,
                    onEmailChange = { if (userPermissions.canModifyAll) email = it },
                    role = role,
                    onRoleChange = { if (userPermissions.canModifyAll) role = it },
                    companyName = companyName,
                    onCompanyNameChange = { if (userPermissions.canModifyAll) companyName = it },
                    department = department,
                    onDepartmentChange = { if (userPermissions.canModifyAll) department = it },
                    designation = designation,
                    onDesignationChange = { if (userPermissions.canModifyAll) designation = it },
                    employeeId = employeeId,
                    onEmployeeIdChange = { if (userPermissions.canModifyAll) employeeId = it },
                    canModify = userPermissions.canModifyAll,
                    showErrors = showErrors,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Personal Information Section
                PersonalInformationSection(
                    phoneNumber = phoneNumber,
                    onPhoneNumberChange = { if (userPermissions.canModifyPersonal) phoneNumber = it },
                    address = address,
                    onAddressChange = { if (userPermissions.canModifyPersonal) address = it },
                    dateOfBirth = dateOfBirth,
                    onDateOfBirthChange = { if (userPermissions.canModifyPersonal) dateOfBirth = it },
                    canModify = userPermissions.canModifyPersonal,
                    showErrors = showErrors,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Professional Information Section
                ProfessionalInformationSection(
                    salary = salary,
                    onSalaryChange = { if (userPermissions.canModifyPersonal) salary = it },
                    joiningDate = joiningDate,
                    onJoiningDateChange = { if (userPermissions.canModifyPersonal) joiningDate = it },
                    reportingTo = reportingTo,
                    onReportingToChange = { if (userPermissions.canModifyPersonal) reportingTo = it },
                    skills = skills,
                    onSkillsChange = { if (userPermissions.canModifyPersonal) skills = it },
                    canModify = userPermissions.canModifyPersonal,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Work Statistics Section
                WorkStatisticsSection(
                    experience = experience,
                    onExperienceChange = { if (userPermissions.canModifyPersonal) experience = it },
                    completedProjects = completedProjects,
                    onCompletedProjectsChange = { if (userPermissions.canModifyPersonal) completedProjects = it },
                    activeProjects = activeProjects,
                    onActiveProjectsChange = { if (userPermissions.canModifyPersonal) activeProjects = it },
                    pendingTasks = pendingTasks,
                    onPendingTasksChange = { if (userPermissions.canModifyPersonal) pendingTasks = it },
                    completedTasks = completedTasks,
                    onCompletedTasksChange = { if (userPermissions.canModifyPersonal) completedTasks = it },
                    totalWorkingHours = totalWorkingHours,
                    onTotalWorkingHoursChange = { if (userPermissions.canModifyPersonal) totalWorkingHours = it },
                    canModify = userPermissions.canModifyPersonal,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Emergency Contact Section
                EmergencyContactSection(
                    emergencyContactName = emergencyContactName,
                    onEmergencyContactNameChange = { if (userPermissions.canModifyPersonal) emergencyContactName = it },
                    emergencyContactPhone = emergencyContactPhone,
                    onEmergencyContactPhoneChange = { if (userPermissions.canModifyPersonal) emergencyContactPhone = it },
                    emergencyContactRelation = emergencyContactRelation,
                    onEmergencyContactRelationChange = { if (userPermissions.canModifyPersonal) emergencyContactRelation = it },
                    canModify = userPermissions.canModifyPersonal,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Password Change Section
                PasswordChangeSection(
                    newPassword = newPassword,
                    onNewPasswordChange = { if (userPermissions.canModifyPersonal) newPassword = it },
                    confirmNewPassword = confirmNewPassword,
                    onConfirmNewPasswordChange = { if (userPermissions.canModifyPersonal) confirmNewPassword = it },
                    isPasswordVisible = isPasswordVisible,
                    onPasswordVisibilityChange = { isPasswordVisible = it },
                    isConfirmPasswordVisible = isConfirmPasswordVisible,
                    onConfirmPasswordVisibilityChange = { isConfirmPasswordVisible = it },
                    canModify = userPermissions.canModifyPersonal,
                    showErrors = showErrors,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                ActionButtonsSection(
                    isFormValid = isFormValid,
                    isUpdating = isUpdating,
                    canModify = userPermissions.canModifyPersonal || userPermissions.canModifyAll,
                    onUpdateClick = {
                        if (isFormValid) {
                            isUpdating = true

                            // Prepare updated data based on permissions
                            val updatedData = mutableMapOf<String, Any>()

                            // Core data (only if user can modify)
                            if (userPermissions.canModifyAll) {
                                updatedData["name"] = name
                                updatedData["email"] = email
                                updatedData["role"] = role
                                updatedData["companyName"] = companyName
                                updatedData["department"] = department
                                updatedData["designation"] = designation
                                updatedData["employeeId"] = employeeId
                            }

                            // Personal information (if user can modify personal data)
                            if (userPermissions.canModifyPersonal) {
                                updatedData["phoneNumber"] = phoneNumber
                                updatedData["address"] = address
                                if (dateOfBirth.isNotEmpty()) updatedData["dateOfBirth"] = dateOfBirth
                                if (salary.isNotEmpty()) updatedData["salary"] = salary.toIntOrNull() ?: 0
                                if (joiningDate.isNotEmpty()) updatedData["joiningDate"] = joiningDate
                                if (reportingTo.isNotEmpty()) updatedData["reportingTo"] = reportingTo

                                // Skills
                                if (skills.isNotEmpty()) {
                                    updatedData["skills"] = skills.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                }

                                // Work statistics
                                updatedData["experience"] = experience.toIntOrNull() ?: 0
                                updatedData["completedProjects"] = completedProjects.toIntOrNull() ?: 0
                                updatedData["activeProjects"] = activeProjects.toIntOrNull() ?: 0
                                updatedData["pendingTasks"] = pendingTasks.toIntOrNull() ?: 0
                                updatedData["completedTasks"] = completedTasks.toIntOrNull() ?: 0
                                updatedData["totalWorkingHours"] = totalWorkingHours.toIntOrNull() ?: 0

                                // Emergency contact
                                updatedData["emergencyContactName"] = emergencyContactName
                                updatedData["emergencyContactPhone"] = emergencyContactPhone
                                updatedData["emergencyContactRelation"] = emergencyContactRelation
                            }

                            val passwordToUpdate = if (newPassword.isNotEmpty() && userPermissions.canModifyPersonal) newPassword else null
                            val imageToUpdate = if (userPermissions.canModifyPhoto) imageUri else null

                            onProfileUpdateClick(updatedData, passwordToUpdate, imageToUpdate)
                        } else {
                            showErrors = true
                        }
                    },
                    onSkipClick = onSkipClick,
                    onLogoutClick = onLogoutClick,
                    isDarkTheme = isDarkTheme
                )

                // Validation error messages
                if (showErrors) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ValidationErrorCard(
                        phoneNumber = phoneNumber,
                        name = name,
                        companyName = companyName,
                        department = department,
                        newPassword = newPassword,
                        confirmNewPassword = confirmNewPassword,
                        canModifyAll = userPermissions.canModifyAll,
                        isDarkTheme = isDarkTheme
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// Individual Section Components

@Composable
fun PermissionInfoCard(userPermissions: UserPermissions, isDarkTheme: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                userPermissions.canModifyAll -> ThemedColors.success(isDarkTheme).copy(alpha = 0.1f)
                userPermissions.canModifyPersonal -> ThemedColors.warning(isDarkTheme).copy(alpha = 0.1f)
                else -> ThemedColors.error(isDarkTheme).copy(alpha = 0.1f)
            }
        ),
        border = BorderStroke(
            1.dp,
            when {
                userPermissions.canModifyAll -> ThemedColors.success(isDarkTheme).copy(alpha = 0.3f)
                userPermissions.canModifyPersonal -> ThemedColors.warning(isDarkTheme).copy(alpha = 0.3f)
                else -> ThemedColors.error(isDarkTheme).copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when {
                    userPermissions.canModifyAll -> Icons.Filled.AdminPanelSettings
                    userPermissions.canModifyPersonal -> Icons.Filled.Edit
                    else -> Icons.Filled.Lock
                },
                contentDescription = null,
                tint = when {
                    userPermissions.canModifyAll -> ThemedColors.success(isDarkTheme)
                    userPermissions.canModifyPersonal -> ThemedColors.warning(isDarkTheme)
                    else -> ThemedColors.error(isDarkTheme)
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "Access Level: ${userPermissions.role}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ThemedColors.onSurface(isDarkTheme)
                )
                Text(
                    when {
                        userPermissions.canModifyAll -> "You can modify all profile fields including core information"
                        userPermissions.canModifyPersonal -> "You can modify personal details only"
                        else -> "You have read-only access to this profile"
                    },
                    fontSize = 12.sp,
                    color = ThemedColors.onSurfaceVariant(isDarkTheme)
                )
            }
        }
    }
}

@Composable
fun CoreInformationSection(
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    role: String,
    onRoleChange: (String) -> Unit,
    companyName: String,
    onCompanyNameChange: (String) -> Unit,
    department: String,
    onDepartmentChange: (String) -> Unit,
    designation: String,
    onDesignationChange: (String) -> Unit,
    employeeId: String,
    onEmployeeIdChange: (String) -> Unit,
    canModify: Boolean,
    showErrors: Boolean,
    isDarkTheme: Boolean
) {
    SectionHeader("Core Information", Icons.Filled.AdminPanelSettings, isDarkTheme)

    if (!canModify) {
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemedColors.warning(isDarkTheme).copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, ThemedColors.warning(isDarkTheme).copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = ThemedColors.warning(isDarkTheme),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Core information is read-only. Contact Administrator to modify.",
                    fontSize = 12.sp,
                    color = ThemedColors.warning(isDarkTheme)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (canModify) {
        ThemedTextField(
            value = name,
            onValueChange = onNameChange,
            label = "Full Name *",
            isError = showErrors && name.isBlank(),
            isDarkTheme = isDarkTheme
        )
        Spacer(modifier = Modifier.height(12.dp))

        ThemedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email Address *",
            keyboardType = KeyboardType.Email,
            isError = showErrors && email.isBlank(),
            isDarkTheme = isDarkTheme
        )
        Spacer(modifier = Modifier.height(12.dp))

        RoleSelectionDropdown(
            selectedRole = role,
            onRoleSelected = onRoleChange,
            isDarkTheme = isDarkTheme
        )
        Spacer(modifier = Modifier.height(12.dp))

        ThemedTextField(
            value = companyName,
            onValueChange = onCompanyNameChange,
            label = "Company/Organization *",
            isError = showErrors && companyName.isBlank(),
            isDarkTheme = isDarkTheme
        )
        Spacer(modifier = Modifier.height(12.dp))

        ThemedTextField(
            value = department,
            onValueChange = onDepartmentChange,
            label = "Department *",
            isError = showErrors && department.isBlank(),
            isDarkTheme = isDarkTheme
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ThemedTextField(
                value = designation,
                onValueChange = onDesignationChange,
                label = "Designation",
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme
            )
            Spacer(modifier = Modifier.width(12.dp))
            ThemedTextField(
                value = employeeId,
                onValueChange = onEmployeeIdChange,
                label = "Employee ID",
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme
            )
        }
    } else {
        ThemedReadOnlyField(name, "Full Name", isDarkTheme)
        Spacer(modifier = Modifier.height(8.dp))
        ThemedReadOnlyField(email, "Email Address", isDarkTheme)
        Spacer(modifier = Modifier.height(8.dp))
        ThemedReadOnlyField(role, "Role", isDarkTheme)
        Spacer(modifier = Modifier.height(8.dp))
        ThemedReadOnlyField(companyName, "Company/Organization", isDarkTheme)
        Spacer(modifier = Modifier.height(8.dp))
        ThemedReadOnlyField(department, "Department", isDarkTheme)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ThemedReadOnlyField(designation, "Designation", isDarkTheme, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            ThemedReadOnlyField(employeeId, "Employee ID", isDarkTheme, Modifier.weight(1f))
        }
    }
}

@Composable
fun PersonalInformationSection(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    dateOfBirth: String,
    onDateOfBirthChange: (String) -> Unit,
    canModify: Boolean,
    showErrors: Boolean,
    isDarkTheme: Boolean
) {
    SectionHeader("Personal Information", Icons.Filled.ContactPhone, isDarkTheme)
    Spacer(modifier = Modifier.height(12.dp))

    if (canModify) {
        ThemedTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = "Phone Number *",
            keyboardType = KeyboardType.Phone,
            isError = showErrors && phoneNumber.isBlank(),
            isDarkTheme = isDarkTheme
        )
        Spacer(modifier = Modifier.height(12.dp))

        ThemedTextField(
            value = address,
            onValueChange = onAddressChange,
            label = "Address",
            isDarkTheme = isDarkTheme
        )
        Spacer(modifier = Modifier.height(12.dp))

        ThemedTextField(
            value = dateOfBirth,
            onValueChange = onDateOfBirthChange,
            label = "Date of Birth (YYYY-MM-DD)",
            isDarkTheme = isDarkTheme
        )
    } else {
        ThemedReadOnlyField(phoneNumber, "Phone Number", isDarkTheme)
        Spacer(modifier = Modifier.height(8.dp))
        ThemedReadOnlyField(address, "Address", isDarkTheme)
        Spacer(modifier = Modifier.height(8.dp))
        ThemedReadOnlyField(dateOfBirth, "Date of Birth", isDarkTheme)
    }
}

@Composable
fun ProfessionalInformationSection(
    salary: String,
    onSalaryChange: (String) -> Unit,
    joiningDate: String,
    onJoiningDateChange: (String) -> Unit,
    reportingTo: String,
    onReportingToChange: (String) -> Unit,
    skills: String,
    onSkillsChange: (String) -> Unit,
    canModify: Boolean,
    isDarkTheme: Boolean
) {
    SectionHeader("Professional Information", Icons.Filled.Work, isDarkTheme)
    Spacer(modifier = Modifier.height(12.dp))

    if (canModify) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ThemedTextField(
                value = salary,
                onValueChange = onSalaryChange,
                label = "Salary",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme
            )
            Spacer(modifier = Modifier.width(12.dp))
            ThemedTextField(
                value = joiningDate,
                onValueChange = onJoiningDateChange,
                label = "Joining Date (YYYY-MM-DD)",
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        ThemedTextField(
            value = reportingTo,
            onValueChange = onReportingToChange,
            label = "Reporting To",
            isDarkTheme = isDarkTheme
        )
        Spacer(modifier = Modifier.height(12.dp))

        ThemedTextField(
            value = skills,
            onValueChange = onSkillsChange,
            label = "Skills (comma-separated)",
            isDarkTheme = isDarkTheme
        )
    } else {
        Row(modifier = Modifier.fillMaxWidth()) {
            ThemedReadOnlyField(salary, "Salary", isDarkTheme, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            ThemedReadOnlyField(joiningDate, "Joining Date", isDarkTheme, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        ThemedReadOnlyField(reportingTo, "Reporting To", isDarkTheme)
        Spacer(modifier = Modifier.height(8.dp))
        ThemedReadOnlyField(skills, "Skills", isDarkTheme)
    }
}

@Composable
fun WorkStatisticsSection(
    experience: String,
    onExperienceChange: (String) -> Unit,
    completedProjects: String,
    onCompletedProjectsChange: (String) -> Unit,
    activeProjects: String,
    onActiveProjectsChange: (String) -> Unit,
    pendingTasks: String,
    onPendingTasksChange: (String) -> Unit,
    completedTasks: String,
    onCompletedTasksChange: (String) -> Unit,
    totalWorkingHours: String,
    onTotalWorkingHoursChange: (String) -> Unit,
    canModify: Boolean,
    isDarkTheme: Boolean
) {
    SectionHeader("Work Statistics", Icons.Filled.Analytics, isDarkTheme)
    Spacer(modifier = Modifier.height(12.dp))

    if (canModify) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ThemedTextField(
                value = experience,
                onValueChange = onExperienceChange,
                label = "Experience (years)",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme
            )
            Spacer(modifier = Modifier.width(12.dp))
            ThemedTextField(
                value = totalWorkingHours,
                onValueChange = onTotalWorkingHoursChange,
                label = "Total Working Hours",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ThemedTextField(
                value = completedProjects,
                onValueChange = onCompletedProjectsChange,
                label = "Completed Projects",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme
            )
            Spacer(modifier = Modifier.width(12.dp))
            ThemedTextField(
                value = activeProjects,
                onValueChange = onActiveProjectsChange,
                label = "Active Projects",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ThemedTextField(
                value = pendingTasks,
                onValueChange = onPendingTasksChange,
                label = "Pending Tasks",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme
            )
            Spacer(modifier = Modifier.width(12.dp))
            ThemedTextField(
                value = completedTasks,
                onValueChange = onCompletedTasksChange,
                label = "Completed Tasks",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme
            )
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth()) {
            ThemedReadOnlyField(experience, "Experience (years)", isDarkTheme, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            ThemedReadOnlyField(totalWorkingHours, "Total Working Hours", isDarkTheme, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ThemedReadOnlyField(completedProjects, "Completed Projects", isDarkTheme, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            ThemedReadOnlyField(activeProjects, "Active Projects", isDarkTheme, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ThemedReadOnlyField(pendingTasks, "Pending Tasks", isDarkTheme, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            ThemedReadOnlyField(completedTasks, "Completed Tasks", isDarkTheme, Modifier.weight(1f))
        }
    }
}

@Composable
fun EmergencyContactSection(
    emergencyContactName: String,
    onEmergencyContactNameChange: (String) -> Unit,
    emergencyContactPhone: String,
    onEmergencyContactPhoneChange: (String) -> Unit,
    emergencyContactRelation: String,
    onEmergencyContactRelationChange: (String) -> Unit,
    canModify: Boolean,
    isDarkTheme: Boolean
) {
    SectionHeader("Emergency Contact", Icons.Filled.EmergencyShare, isDarkTheme)
    Spacer(modifier = Modifier.height(12.dp))

    if (canModify) {
        ThemedTextField(
            value = emergencyContactName,
            onValueChange = onEmergencyContactNameChange,
            label = "Emergency Contact Name",
            isDarkTheme = isDarkTheme
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ThemedTextField(
                value = emergencyContactPhone,
                onValueChange = onEmergencyContactPhoneChange,
                label = "Emergency Contact Phone",
                keyboardType = KeyboardType.Phone,
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme
            )
            Spacer(modifier = Modifier.width(12.dp))
            ThemedTextField(
                value = emergencyContactRelation,
                onValueChange = onEmergencyContactRelationChange,
                label = "Relation",
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme
            )
        }
    } else {
        ThemedReadOnlyField(emergencyContactName, "Emergency Contact Name", isDarkTheme)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ThemedReadOnlyField(emergencyContactPhone, "Emergency Contact Phone", isDarkTheme, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            ThemedReadOnlyField(emergencyContactRelation, "Relation", isDarkTheme, Modifier.weight(1f))
        }
    }
}

@Composable
fun PasswordChangeSection(
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    confirmNewPassword: String,
    onConfirmNewPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    isConfirmPasswordVisible: Boolean,
    onConfirmPasswordVisibilityChange: (Boolean) -> Unit,
    canModify: Boolean,
    showErrors: Boolean,
    isDarkTheme: Boolean
) {
    SectionHeader("Change Password (Optional)", Icons.Filled.Lock, isDarkTheme)

    if (!canModify) {
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemedColors.warning(isDarkTheme).copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, ThemedColors.warning(isDarkTheme).copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = ThemedColors.warning(isDarkTheme),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Password change not available for your role.",
                    fontSize = 12.sp,
                    color = ThemedColors.warning(isDarkTheme)
                )
            }
        }
    } else {
        Spacer(modifier = Modifier.height(12.dp))

        ThemedPasswordField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = "New Password (Optional)",
            isPasswordVisible = isPasswordVisible,
            onPasswordVisibilityChange = onPasswordVisibilityChange,
            isError = showErrors && newPassword.isNotEmpty() && newPassword.length < 6,
            isDarkTheme = isDarkTheme
        )

        if (newPassword.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            ThemedPasswordField(
                value = confirmNewPassword,
                onValueChange = onConfirmNewPasswordChange,
                label = "Confirm New Password",
                isPasswordVisible = isConfirmPasswordVisible,
                onPasswordVisibilityChange = onConfirmPasswordVisibilityChange,
                isError = showErrors && newPassword != confirmNewPassword,
                isDarkTheme = isDarkTheme
            )
        }
    }
}

@Composable
fun ActionButtonsSection(
    isFormValid: Boolean,
    isUpdating: Boolean,
    canModify: Boolean,
    onUpdateClick: () -> Unit,
    onSkipClick: () -> Unit,
    onLogoutClick: () -> Unit,
    isDarkTheme: Boolean
) {
    if (canModify) {
        Button(
            onClick = onUpdateClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isUpdating && isFormValid,
            colors = ButtonDefaults.buttonColors(containerColor = ThemedColors.primary(isDarkTheme)),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isUpdating) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Updating...")
            } else {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Update Profile",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    } else {
        // Read-only view - show info message
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemedColors.surfaceVariant(isDarkTheme)),
            border = BorderStroke(1.dp, ThemedColors.outline(isDarkTheme).copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = ThemedColors.onSurfaceVariant(isDarkTheme),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Profile View Mode",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = ThemedColors.onSurface(isDarkTheme)
                )
                Text(
                    "Contact your administrator to make changes",
                    fontSize = 12.sp,
                    color = ThemedColors.onSurfaceVariant(isDarkTheme),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // Skip Button (always available)
    OutlinedButton(
        onClick = onSkipClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ThemedColors.onSurface(isDarkTheme)
        ),
        border = BorderStroke(1.dp, ThemedColors.outline(isDarkTheme))
    ) {
        Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Continue to App", fontWeight = FontWeight.Medium)
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Logout Button
    OutlinedButton(
        onClick = onLogoutClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ThemedColors.error(isDarkTheme)
        ),
        border = BorderStroke(1.dp, ThemedColors.error(isDarkTheme).copy(alpha = 0.5f))
    ) {
        Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Logout", fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ValidationErrorCard(
    phoneNumber: String,
    name: String,
    companyName: String,
    department: String,
    newPassword: String,
    confirmNewPassword: String,
    canModifyAll: Boolean,
    isDarkTheme: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ThemedColors.error(isDarkTheme).copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, ThemedColors.error(isDarkTheme).copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    tint = ThemedColors.error(isDarkTheme),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "Please fix the following errors:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = ThemedColors.error(isDarkTheme)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (phoneNumber.isBlank()) {
                        Text(
                            "• Phone number is required",
                            fontSize = 11.sp,
                            color = ThemedColors.error(isDarkTheme)
                        )
                    }

                    if (canModifyAll) {
                        if (name.isBlank()) {
                            Text(
                                "• Full name is required",
                                fontSize = 11.sp,
                                color = ThemedColors.error(isDarkTheme)
                            )
                        }
                        if (companyName.isBlank()) {
                            Text(
                                "• Company name is required",
                                fontSize = 11.sp,
                                color = ThemedColors.error(isDarkTheme)
                            )
                        }
                        if (department.isBlank()) {
                            Text(
                                "• Department is required",
                                fontSize = 11.sp,
                                color = ThemedColors.error(isDarkTheme)
                            )
                        }
                    }

                    if (newPassword.isNotEmpty() && newPassword.length < 6) {
                        Text(
                            "• Password must be at least 6 characters",
                            fontSize = 11.sp,
                            color = ThemedColors.error(isDarkTheme)
                        )
                    }

                    if (newPassword.isNotEmpty() && newPassword != confirmNewPassword) {
                        Text(
                            "• Passwords do not match",
                            fontSize = 11.sp,
                            color = ThemedColors.error(isDarkTheme)
                        )
                    }
                }
            }
        }
    }
}

// Enhanced ProfileImageSection with permission handling
@Composable
fun ProfileImageSection(
    imageUri: Uri?,
    currentImageUrl: String,
    onImagePick: () -> Unit,
    canModify: Boolean,
    isDarkTheme: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(ThemedColors.surfaceVariant(isDarkTheme))
                .border(
                    2.dp,
                    if (canModify) ThemedColors.primary(isDarkTheme).copy(alpha = 0.3f) else ThemedColors.outline(isDarkTheme).copy(alpha = 0.3f),
                    CircleShape
                )
                .then(
                    if (canModify) Modifier.clickable { onImagePick() } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                imageUri != null -> {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                currentImageUrl.isNotEmpty() -> {
                    AsyncImage(
                        model = currentImageUrl,
                        contentDescription = "Current Profile Picture",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (canModify) Icons.Filled.CameraAlt else Icons.Filled.AccountCircle,
                            contentDescription = if (canModify) "Add Photo" else "Profile Picture",
                            tint = if (canModify) ThemedColors.primary(isDarkTheme) else ThemedColors.onSurfaceVariant(isDarkTheme),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (canModify) "Add Photo" else "No Photo",
                            color = ThemedColors.onSurfaceVariant(isDarkTheme),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Show overlay for non-modifiable images
            if (!canModify && (imageUri != null || currentImageUrl.isNotEmpty())) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Locked",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = when {
                !canModify -> "Photo cannot be changed"
                imageUri != null || currentImageUrl.isNotEmpty() -> "Tap to change photo"
                else -> "Tap to add profile photo"
            },
            color = if (canModify) ThemedColors.primary(isDarkTheme) else ThemedColors.onSurfaceVariant(isDarkTheme),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        if (canModify && imageUri == null && currentImageUrl.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "JPG, PNG up to 5MB",
                color = ThemedColors.onSurfaceVariant(isDarkTheme),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Enhanced utility composables
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleSelectionDropdown(
    selectedRole: String,
    onRoleSelected: (String) -> Unit,
    isDarkTheme: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val roles = listOf("Administrator", "Manager", "HR", "Team Lead", "Employee", "Intern")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedRole,
            onValueChange = {},
            readOnly = true,
            label = { Text("Role *") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ThemedColors.primary(isDarkTheme),
                unfocusedBorderColor = ThemedColors.outline(isDarkTheme),
                focusedLabelColor = ThemedColors.primary(isDarkTheme),
                unfocusedLabelColor = ThemedColors.onSurfaceVariant(isDarkTheme),
                focusedTextColor = ThemedColors.onSurface(isDarkTheme),
                unfocusedTextColor = ThemedColors.onSurface(isDarkTheme)
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            roles.forEach { role ->
                DropdownMenuItem(
                    text = { Text(role) },
                    onClick = {
                        onRoleSelected(role)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector, isDarkTheme: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = ThemedColors.primary(isDarkTheme),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = ThemedColors.primary(isDarkTheme)
        )
    }
}

@Composable
fun ThemedReadOnlyField(
    value: String,
    label: String,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label, color = ThemedColors.onSurfaceVariant(isDarkTheme)) },
        modifier = modifier.fillMaxWidth(),
        enabled = false,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = ThemedColors.onSurface(isDarkTheme).copy(alpha = 0.7f),
            disabledBorderColor = ThemedColors.outline(isDarkTheme).copy(alpha = 0.5f),
            disabledLabelColor = ThemedColors.onSurfaceVariant(isDarkTheme).copy(alpha = 0.6f)
        )
    )
}

@Composable
fun ThemedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        isError = isError,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ThemedColors.primary(isDarkTheme),
            unfocusedBorderColor = ThemedColors.outline(isDarkTheme),
            errorBorderColor = ThemedColors.error(isDarkTheme),
            focusedLabelColor = ThemedColors.primary(isDarkTheme),
            unfocusedLabelColor = ThemedColors.onSurfaceVariant(isDarkTheme),
            cursorColor = ThemedColors.primary(isDarkTheme),
            focusedTextColor = ThemedColors.onSurface(isDarkTheme),
            unfocusedTextColor = ThemedColors.onSurface(isDarkTheme)
        )
    )
}

@Composable
fun ThemedPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPasswordVisible: Boolean,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    isError: Boolean = false,
    isDarkTheme: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { onPasswordVisibilityChange(!isPasswordVisible) }) {
                Icon(
                    imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = ThemedColors.onSurfaceVariant(isDarkTheme)
                )
            }
        },
        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        isError = isError,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ThemedColors.primary(isDarkTheme),
            unfocusedBorderColor = ThemedColors.outline(isDarkTheme),
            errorBorderColor = ThemedColors.error(isDarkTheme),
            focusedLabelColor = ThemedColors.primary(isDarkTheme),
            unfocusedLabelColor = ThemedColors.onSurfaceVariant(isDarkTheme),
            cursorColor = ThemedColors.primary(isDarkTheme),
            focusedTextColor = ThemedColors.onSurface(isDarkTheme),
            unfocusedTextColor = ThemedColors.onSurface(isDarkTheme)
        )
    )
}