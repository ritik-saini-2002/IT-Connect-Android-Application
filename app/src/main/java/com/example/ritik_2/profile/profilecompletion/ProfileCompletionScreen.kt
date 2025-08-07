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

// System-aware Color Scheme
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
        imageUri = uri
    }

    // Check if user can modify core data (Administrator or Manager only)
    val canModifyCoreData = userRole in listOf("Administrator", "Manager")

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
                    // Fallback: try direct users collection
                    val userDoc = FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId)
                        .get()
                        .await()

                    if (userDoc.exists()) {
                        val data = userDoc.data ?: emptyMap()
                        email = data["email"]?.toString() ?: ""
                        name = data["name"]?.toString() ?: ""
                        role = data["role"]?.toString() ?: userRole
                        designation = data["designation"]?.toString() ?: ""
                        companyName = data["companyName"]?.toString() ?: ""
                        phoneNumber = data["phoneNumber"]?.toString() ?: ""
                        address = data["address"]?.toString() ?: ""
                        experience = data["experience"]?.toString() ?: "0"
                        completedProjects = data["completedProjects"]?.toString() ?: "0"
                        activeProjects = data["activeProjects"]?.toString() ?: "0"
                        skills = (data["skills"] as? List<String>)?.joinToString(", ") ?: ""
                        department = data["department"]?.toString() ?: ""
                        salary = data["salary"]?.toString() ?: ""
                        dateOfBirth = data["dateOfBirth"]?.toString() ?: ""
                        currentImageUrl = data["imageUrl"]?.toString() ?: ""
                    }
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

    // Form validation
    val isFormValid = phoneNumber.isNotBlank() &&
            (newPassword.isEmpty() || (newPassword.length >= 6 && newPassword == confirmNewPassword)) &&
            (if (canModifyCoreData) {
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
        // Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemedColors.primary(isDarkTheme)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Complete Your Profile",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (canModifyCoreData) {
                        "You have administrator privileges - you can modify all fields"
                    } else {
                        "Update your information or skip to continue"
                    },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemedColors.surface(isDarkTheme)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {

                // Profile Image Section
                ProfileImageSection(
                    imageUri = imageUri,
                    currentImageUrl = currentImageUrl,
                    onImagePick = { imagePickerLauncher.launch("image/*") },
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Core Information (editable by Admin/Manager only)
                SectionHeader("Core Information", Icons.Filled.AdminPanelSettings, isDarkTheme)

                if (!canModifyCoreData) {
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

                if (canModifyCoreData) {
                    ThemedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Full Name *",
                        isError = showErrors && name.isBlank(),
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ThemedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email Address *",
                        keyboardType = KeyboardType.Email,
                        isError = showErrors && email.isBlank(),
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    RoleSelectionDropdown(
                        selectedRole = role,
                        onRoleSelected = { role = it },
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ThemedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = "Company/Organization *",
                        isError = showErrors && companyName.isBlank(),
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ThemedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = "Department *",
                        isError = showErrors && department.isBlank(),
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ThemedTextField(
                        value = designation,
                        onValueChange = { designation = it },
                        label = "Designation",
                        isDarkTheme = isDarkTheme
                    )
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
                    ThemedReadOnlyField(designation, "Designation", isDarkTheme)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Contact Information
                SectionHeader("Contact Information", Icons.Filled.ContactPhone, isDarkTheme)
                Spacer(modifier = Modifier.height(12.dp))

                ThemedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = "Phone Number *",
                    keyboardType = KeyboardType.Phone,
                    isError = showErrors && phoneNumber.isBlank(),
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(12.dp))

                ThemedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "Address",
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Professional Information
                SectionHeader("Professional Information", Icons.Filled.Work, isDarkTheme)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    ThemedTextField(
                        value = employeeId,
                        onValueChange = { employeeId = it },
                        label = "Employee ID",
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ThemedTextField(
                        value = salary,
                        onValueChange = { salary = it },
                        label = "Salary",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    ThemedTextField(
                        value = joiningDate,
                        onValueChange = { joiningDate = it },
                        label = "Joining Date (YYYY-MM-DD)",
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ThemedTextField(
                        value = reportingTo,
                        onValueChange = { reportingTo = it },
                        label = "Reporting To",
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                ThemedTextField(
                    value = dateOfBirth,
                    onValueChange = { dateOfBirth = it },
                    label = "Date of Birth (YYYY-MM-DD)",
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(12.dp))

                ThemedTextField(
                    value = skills,
                    onValueChange = { skills = it },
                    label = "Skills (comma-separated)",
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Work Statistics
                SectionHeader("Work Statistics", Icons.Filled.Analytics, isDarkTheme)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    ThemedTextField(
                        value = experience,
                        onValueChange = { experience = it },
                        label = "Experience (years)",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ThemedTextField(
                        value = totalWorkingHours,
                        onValueChange = { totalWorkingHours = it },
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
                        onValueChange = { completedProjects = it },
                        label = "Completed Projects",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ThemedTextField(
                        value = activeProjects,
                        onValueChange = { activeProjects = it },
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
                        onValueChange = { pendingTasks = it },
                        label = "Pending Tasks",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ThemedTextField(
                        value = completedTasks,
                        onValueChange = { completedTasks = it },
                        label = "Completed Tasks",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Emergency Contact
                SectionHeader("Emergency Contact", Icons.Filled.EmergencyShare, isDarkTheme)
                Spacer(modifier = Modifier.height(12.dp))

                ThemedTextField(
                    value = emergencyContactName,
                    onValueChange = { emergencyContactName = it },
                    label = "Emergency Contact Name",
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    ThemedTextField(
                        value = emergencyContactPhone,
                        onValueChange = { emergencyContactPhone = it },
                        label = "Emergency Contact Phone",
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ThemedTextField(
                        value = emergencyContactRelation,
                        onValueChange = { emergencyContactRelation = it },
                        label = "Relation",
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Password Change
                SectionHeader("Change Password (Optional)", Icons.Filled.Lock, isDarkTheme)
                Spacer(modifier = Modifier.height(12.dp))

                ThemedPasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "New Password (Optional)",
                    isPasswordVisible = isPasswordVisible,
                    onPasswordVisibilityChange = { isPasswordVisible = it },
                    isError = showErrors && newPassword.isNotEmpty() && newPassword.length < 6,
                    isDarkTheme = isDarkTheme
                )

                if (newPassword.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ThemedPasswordField(
                        value = confirmNewPassword,
                        onValueChange = { confirmNewPassword = it },
                        label = "Confirm New Password",
                        isPasswordVisible = isConfirmPasswordVisible,
                        onPasswordVisibilityChange = { isConfirmPasswordVisible = it },
                        isError = showErrors && newPassword != confirmNewPassword,
                        isDarkTheme = isDarkTheme
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                Button(
                    onClick = {
                        if (isFormValid) {
                            isUpdating = true

                            // Prepare updated data
                            val updatedData = mutableMapOf<String, Any>()

                            // Core data (only if user can modify)
                            if (canModifyCoreData) {
                                updatedData["name"] = name
                                updatedData["email"] = email
                                updatedData["role"] = role
                                updatedData["companyName"] = companyName
                                updatedData["department"] = department
                                updatedData["designation"] = designation
                            }

                            // Contact information
                            updatedData["phoneNumber"] = phoneNumber
                            updatedData["address"] = address

                            // Professional information
                            if (employeeId.isNotEmpty()) updatedData["employeeId"] = employeeId
                            if (salary.isNotEmpty()) updatedData["salary"] = salary.toIntOrNull() ?: 0
                            if (joiningDate.isNotEmpty()) updatedData["joiningDate"] = joiningDate
                            if (reportingTo.isNotEmpty()) updatedData["reportingTo"] = reportingTo
                            if (dateOfBirth.isNotEmpty()) updatedData["dateOfBirth"] = dateOfBirth

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

                            val passwordToUpdate = if (newPassword.isNotEmpty()) newPassword else null
                            onProfileUpdateClick(updatedData, passwordToUpdate, imageUri)
                        } else {
                            showErrors = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isUpdating,
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

//                // Skip Button
//                OutlinedButton(
//                    onClick = onSkipClick,
//                    modifier = Modifier.fillMaxWidth(),
//                    shape = RoundedCornerShape(12.dp),
//                    colors = ButtonDefaults.outlinedButtonColors(
//                        contentColor = ThemedColors.onSurface(isDarkTheme)
//                    ),
//                    border = BorderStroke(1.dp, ThemedColors.outline(isDarkTheme))
//                ) {
//                    Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
//                    Spacer(modifier = Modifier.width(8.dp))
//                    Text("Skip for Now", fontWeight = FontWeight.Medium)
//                }
//
//                Spacer(modifier = Modifier.height(12.dp))

                // Logout Button
//                OutlinedButton(
//                    onClick = onLogoutClick,
//                    modifier = Modifier.fillMaxWidth(),
//                    shape = RoundedCornerShape(12.dp),
//                    colors = ButtonDefaults.outlinedButtonColors(
//                        contentColor = ThemedColors.error(isDarkTheme)
//                    ),
//                    border = BorderStroke(1.dp, ThemedColors.error(isDarkTheme).copy(alpha = 0.5f))
//                ) {
//                    Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
//                    Spacer(modifier = Modifier.width(8.dp))
//                    Text("Logout", fontWeight = FontWeight.Medium)
//                }

                // Validation error messages
                if (showErrors) {
                    Spacer(modifier = Modifier.height(16.dp))
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

                                    if (canModifyCoreData) {
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
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

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
fun ThemedReadOnlyField(value: String, label: String, isDarkTheme: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label, color = ThemedColors.onSurfaceVariant(isDarkTheme)) },
        modifier = Modifier.fillMaxWidth(),
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

@Composable
fun ProfileImageSection(
    imageUri: Uri?,
    currentImageUrl: String,
    onImagePick: () -> Unit,
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
                    ThemedColors.primary(isDarkTheme).copy(alpha = 0.3f),
                    CircleShape
                )
                .clickable { onImagePick() },
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
                            Icons.Filled.CameraAlt,
                            contentDescription = "Add Photo",
                            tint = ThemedColors.primary(isDarkTheme),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Add Photo",
                            color = ThemedColors.onSurfaceVariant(isDarkTheme),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (imageUri != null || currentImageUrl.isNotEmpty())
                "Tap to change photo"
            else
                "Tap to add profile photo",
            color = ThemedColors.primary(isDarkTheme),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        if (imageUri == null && currentImageUrl.isEmpty()) {
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