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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore

// System-aware Color Scheme (same as before)
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
    fun readOnlyBackground(isDark: Boolean) = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF8F9FA)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCompletionScreen(
    userId: String,
    isDarkTheme: Boolean,
    onProfileUpdateClick: (Map<String, Any>, String?, Uri?) -> Unit,
    onSkipClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    // Core user data states (from hierarchical structure)
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var createdBy by remember { mutableStateOf("") }

    // Profile data states (user can edit these)
    var phoneNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var employeeId by remember { mutableStateOf("") }
    var reportingTo by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var emergencyContactName by remember { mutableStateOf("") }
    var emergencyContactPhone by remember { mutableStateOf("") }
    var emergencyContactRelation by remember { mutableStateOf("") }
    var currentImageUrl by remember { mutableStateOf("") }

    // Work Stats (user can edit some of these)
    var experience by remember { mutableStateOf("0") }
    var completedProjects by remember { mutableStateOf("0") }
    var activeProjects by remember { mutableStateOf("0") }
    var pendingTasks by remember { mutableStateOf("0") }
    var completedTasks by remember { mutableStateOf("0") }

    // Admin restrictions
    var isAdminCreated by remember { mutableStateOf(false) }
    var canEditBasicInfo by remember { mutableStateOf(true) }

    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }

    // UI states
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var isDataLoaded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    // Load hierarchical user data
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            // First get access control to find the document path
            FirebaseFirestore.getInstance().collection("user_access_control").document(userId).get()
                .addOnSuccessListener { accessDoc ->
                    if (accessDoc.exists()) {
                        val documentPath = accessDoc.getString("documentPath") ?: ""
                        val sanitizedCompany = accessDoc.getString("sanitizedCompany") ?: ""
                        val sanitizedDepartment = accessDoc.getString("sanitizedDepartment") ?: ""
                        val userRole = accessDoc.getString("role") ?: ""

                        // Set access control data
                        email = accessDoc.getString("email") ?: ""
                        name = accessDoc.getString("name") ?: ""
                        role = userRole
                        companyName = accessDoc.getString("companyName") ?: ""
                        department = accessDoc.getString("department") ?: ""

                        // Now get the full user data from hierarchical structure
                        if (documentPath.isNotEmpty()) {
                            val userDocRef = FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(sanitizedCompany)
                                .collection(sanitizedDepartment)
                                .document(userRole)
                                .collection("users")
                                .document(userId)

                            userDocRef.get()
                                .addOnSuccessListener { userDoc ->
                                    if (userDoc.exists()) {
                                        val data = userDoc.data ?: emptyMap()

                                        // Core info (mostly read-only)
                                        designation = data["designation"]?.toString() ?: ""
                                        createdBy = data["createdBy"]?.toString() ?: ""

                                        // Check if admin created (restrictions apply)
                                        isAdminCreated = createdBy != "self_registration"
                                        canEditBasicInfo = !isAdminCreated

                                        // Profile data (user can edit)
                                        val profile = data["profile"] as? Map<*, *>
                                        if (profile != null) {
                                            currentImageUrl = profile["imageUrl"]?.toString() ?: ""
                                            phoneNumber = profile["phoneNumber"]?.toString() ?: ""
                                            address = profile["address"]?.toString() ?: ""
                                            dateOfBirth = profile["dateOfBirth"]?.toString() ?: ""
                                            employeeId = profile["employeeId"]?.toString() ?: ""
                                            reportingTo = profile["reportingTo"]?.toString() ?: ""
                                            salary = profile["salary"]?.toString() ?: ""

                                            val emergencyContact = profile["emergencyContact"] as? Map<*, *>
                                            if (emergencyContact != null) {
                                                emergencyContactName = emergencyContact["name"]?.toString() ?: ""
                                                emergencyContactPhone = emergencyContact["phone"]?.toString() ?: ""
                                                emergencyContactRelation = emergencyContact["relation"]?.toString() ?: ""
                                            }
                                        }

                                        // Work stats
                                        val workStats = data["workStats"] as? Map<*, *>
                                        if (workStats != null) {
                                            experience = workStats["experience"]?.toString() ?: "0"
                                            completedProjects = workStats["completedProjects"]?.toString() ?: "0"
                                            activeProjects = workStats["activeProjects"]?.toString() ?: "0"
                                            pendingTasks = workStats["pendingTasks"]?.toString() ?: "0"
                                            completedTasks = workStats["completedTasks"]?.toString() ?: "0"
                                        }
                                    }
                                    isDataLoaded = true
                                }
                                .addOnFailureListener {
                                    isDataLoaded = true
                                }
                        } else {
                            isDataLoaded = true
                        }
                    } else {
                        isDataLoaded = true
                    }
                }
                .addOnFailureListener {
                    isDataLoaded = true
                }
        }
    }

    // Validation - phone number is required
    val isFormValid = phoneNumber.isNotBlank() &&
            (newPassword.isEmpty() || (newPassword.length >= 6 && newPassword == confirmNewPassword))

    if (!isDataLoaded) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = ThemedColors.primary(isDarkTheme))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Loading your profile...",
                    color = ThemedColors.onSurfaceVariant(isDarkTheme)
                )
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
        // Header with restrictions info
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
                    text = if (isAdminCreated) {
                        "Your account was created by an administrator.\nSome fields are protected and cannot be modified."
                    } else {
                        "Update your information or skip to continue"
                    },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )

                if (isAdminCreated) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Created by: Administrator",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
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
                // Profile Image
                ProfileImageSection(
                    imageUri = imageUri,
                    currentImageUrl = currentImageUrl,
                    onImagePick = { imagePickerLauncher.launch("image/*") },
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Basic Information (Protected for admin-created users)
                SectionHeader(
                    title = "Basic Information",
                    icon = Icons.Filled.Person,
                    isDarkTheme = isDarkTheme,
                    isProtected = isAdminCreated
                )
                Spacer(modifier = Modifier.height(12.dp))

                ThemedReadOnlyField(name, "Full Name", isDarkTheme, isProtected = true)
                Spacer(modifier = Modifier.height(8.dp))
                ThemedReadOnlyField(email, "Email Address", isDarkTheme, isProtected = true)
                Spacer(modifier = Modifier.height(8.dp))
                ThemedReadOnlyField(role, "Role", isDarkTheme, isProtected = true)
                Spacer(modifier = Modifier.height(8.dp))
                ThemedReadOnlyField(designation, "Designation", isDarkTheme, isProtected = isAdminCreated)
                Spacer(modifier = Modifier.height(8.dp))
                ThemedReadOnlyField(companyName, "Company/Organization", isDarkTheme, isProtected = true)
                Spacer(modifier = Modifier.height(8.dp))
                ThemedReadOnlyField(department, "Department", isDarkTheme, isProtected = isAdminCreated)

                Spacer(modifier = Modifier.height(20.dp))

                // Contact Information (User can edit)
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

                // Personal Details
                SectionHeader("Personal Details", Icons.Filled.Person, isDarkTheme)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemedTextField(
                        value = dateOfBirth,
                        onValueChange = { dateOfBirth = it },
                        label = "Date of Birth",
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                    ThemedTextField(
                        value = employeeId,
                        onValueChange = { employeeId = it },
                        label = "Employee ID",
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemedTextField(
                        value = reportingTo,
                        onValueChange = { reportingTo = it },
                        label = "Reporting To",
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                    ThemedTextField(
                        value = salary,
                        onValueChange = { salary = it },
                        label = "Salary",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Emergency Contact
                SectionHeader("Emergency Contact", Icons.Filled.ContactEmergency, isDarkTheme)
                Spacer(modifier = Modifier.height(12.dp))

                ThemedTextField(
                    value = emergencyContactName,
                    onValueChange = { emergencyContactName = it },
                    label = "Emergency Contact Name",
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemedTextField(
                        value = emergencyContactPhone,
                        onValueChange = { emergencyContactPhone = it },
                        label = "Emergency Phone",
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                    ThemedTextField(
                        value = emergencyContactRelation,
                        onValueChange = { emergencyContactRelation = it },
                        label = "Relation",
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

// Work Statistics (Some restrictions for admin-created users)
                SectionHeader(
                    title = "Work Statistics",
                    icon = Icons.Filled.Work,
                    isDarkTheme = isDarkTheme,
                    isProtected = isAdminCreated && (experience != "0" || completedProjects != "0")
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isAdminCreated && experience != "0") {
                        ThemedReadOnlyField(
                            value = experience,
                            label = "Experience (Years)",
                            isDarkTheme = isDarkTheme,
                            isProtected = true,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        ThemedNumberField(
                            value = experience,
                            onValueChange = { experience = it },
                            label = "Experience (Years)",
                            modifier = Modifier.weight(1f),
                            isDarkTheme = isDarkTheme
                        )
                    }

                    if (isAdminCreated && completedProjects != "0") {
                        ThemedReadOnlyField(
                            value = completedProjects,
                            label = "Completed Projects",
                            isDarkTheme = isDarkTheme,
                            isProtected = true,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        ThemedNumberField(
                            value = completedProjects,
                            onValueChange = { completedProjects = it },
                            label = "Completed Projects",
                            modifier = Modifier.weight(1f),
                            isDarkTheme = isDarkTheme
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemedNumberField(
                        value = activeProjects,
                        onValueChange = { activeProjects = it },
                        label = "Active Projects",
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                    ThemedNumberField(
                        value = pendingTasks,
                        onValueChange = { pendingTasks = it },
                        label = "Pending Tasks",
                        modifier = Modifier.weight(1f),
                        isDarkTheme = isDarkTheme
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                ThemedNumberField(
                    value = completedTasks,
                    onValueChange = { completedTasks = it },
                    label = "Completed Tasks",
                    modifier = Modifier.fillMaxWidth(),
                    isDarkTheme = isDarkTheme
                )

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

                // Restriction Notice for Admin-Created Users
                if (isAdminCreated) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = ThemedColors.surfaceVariant(isDarkTheme)
                        ),
                        border = BorderStroke(1.dp, ThemedColors.primary(isDarkTheme).copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = ThemedColors.primary(isDarkTheme),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Admin Restrictions",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = ThemedColors.primary(isDarkTheme)
                                )
                                Text(
                                    "Basic information and pre-filled work statistics are protected by administrator settings.",
                                    fontSize = 12.sp,
                                    color = ThemedColors.onSurfaceVariant(isDarkTheme),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                Button(
                    onClick = {
                        if (isFormValid) {
                            isLoading = true

                            // Build update data properly
                            val updatedData = mutableMapOf<String, Any>()

                            // Profile updates
                            updatedData["profile.phoneNumber"] = phoneNumber
                            updatedData["profile.address"] = address
                            updatedData["profile.dateOfBirth"] = dateOfBirth
                            updatedData["profile.employeeId"] = employeeId
                            updatedData["profile.reportingTo"] = reportingTo
                            updatedData["profile.salary"] = salary.toIntOrNull() ?: 0
                            updatedData["profile.emergencyContact"] = mapOf(
                                "name" to emergencyContactName,
                                "phone" to emergencyContactPhone,
                                "relation" to emergencyContactRelation
                            )

                            // Work stats updates (only if not admin-restricted)
                            if (!isAdminCreated || experience == "0") {
                                updatedData["workStats.experience"] = experience.toIntOrNull() ?: 0
                            }
                            if (!isAdminCreated || completedProjects == "0") {
                                updatedData["workStats.completedProjects"] = completedProjects.toIntOrNull() ?: 0
                            }
                            updatedData["workStats.activeProjects"] = activeProjects.toIntOrNull() ?: 0
                            updatedData["workStats.pendingTasks"] = pendingTasks.toIntOrNull() ?: 0
                            updatedData["workStats.completedTasks"] = completedTasks.toIntOrNull() ?: 0
                            updatedData["isProfileComplete"] = true

                            val passwordToUpdate = if (newPassword.isNotEmpty()) newPassword else null
                            onProfileUpdateClick(updatedData.toMap(), passwordToUpdate, imageUri)
                        } else {
                            showErrors = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = ThemedColors.primary(isDarkTheme)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
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

                // Skip Button
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
                    Text("Skip for Now", fontWeight = FontWeight.Medium)
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

                if (showErrors && phoneNumber.isBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Phone number is required",
                        color = ThemedColors.error(isDarkTheme),
                        fontSize = 12.sp
                    )
                }

                if (showErrors && newPassword.isNotEmpty() && newPassword.length < 6) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Password must be at least 6 characters",
                        color = ThemedColors.error(isDarkTheme),
                        fontSize = 12.sp
                    )
                }

                if (showErrors && newPassword.isNotEmpty() && newPassword != confirmNewPassword) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Passwords do not match",
                        color = ThemedColors.error(isDarkTheme),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    isDarkTheme: Boolean,
    isProtected: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isProtected) ThemedColors.onSurfaceVariant(isDarkTheme) else ThemedColors.primary(isDarkTheme),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = if (isProtected) ThemedColors.onSurfaceVariant(isDarkTheme) else ThemedColors.primary(isDarkTheme)
        )
        if (isProtected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Protected by administrator",
                tint = ThemedColors.onSurfaceVariant(isDarkTheme),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ThemedReadOnlyField(
    value: String,
    label: String,
    isDarkTheme: Boolean,
    isProtected: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    color = ThemedColors.onSurfaceVariant(isDarkTheme).copy(alpha = 0.7f)
                )
                if (isProtected) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = ThemedColors.onSurfaceVariant(isDarkTheme).copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        enabled = false,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = ThemedColors.onSurface(isDarkTheme).copy(alpha = 0.7f),
            disabledBorderColor = if (isProtected)
                ThemedColors.error(isDarkTheme).copy(alpha = 0.3f)
            else
                ThemedColors.outline(isDarkTheme).copy(alpha = 0.5f),
            disabledLabelColor = ThemedColors.onSurfaceVariant(isDarkTheme).copy(alpha = 0.6f),
            disabledContainerColor = if (isProtected)
                ThemedColors.error(isDarkTheme).copy(alpha = 0.05f)
            else
                ThemedColors.readOnlyBackground(isDarkTheme)
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
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
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
fun ThemedNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    isDarkTheme: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all { c -> c.isDigit() }) onValueChange(it) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ThemedColors.primary(isDarkTheme),
            unfocusedBorderColor = ThemedColors.outline(isDarkTheme),
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