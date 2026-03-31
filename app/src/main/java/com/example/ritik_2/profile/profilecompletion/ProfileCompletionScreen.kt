package com.example.ritik_2.profile.profilecompletion

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.profile.profilecompletion.components.ProfileCompletionViewModel
import com.example.ritik_2.profile.profilecompletion.components.ProfileData
import java.text.SimpleDateFormat
import java.util.*

// ── Local permissions helper (replaces missing UserPermissions) ───────────────
private data class LocalPermissions(
    val canModifyUser: Boolean  = false,
    val canViewAllUsers: Boolean = false
) {
    companion object {
        fun fromRole(role: String) = LocalPermissions(
            canModifyUser   = role in listOf("Administrator", "Manager"),
            canViewAllUsers = role in listOf("Administrator", "Manager", "HR")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCompletionScreen(
    viewModel: ProfileCompletionViewModel,
    onImagePickClick: () -> Unit,
    onSaveProfile: (ProfileData) -> Unit,
    onNavigateBack: () -> Unit,
    isEditMode: Boolean = false,
    userId: String = ""
) {
    val scrollState = rememberLazyListState()
    LocalContext.current

    val uiState     by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val permissions = remember(currentUser?.role) {
        LocalPermissions.fromRole(currentUser?.role ?: "Employee")
    }

    var profileData            by remember { mutableStateOf(ProfileData()) }
    var showDatePicker         by remember { mutableStateOf<DatePickerType?>(null) }
    var passwordVisible        by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var password               by remember { mutableStateOf("") }
    var confirmPassword        by remember { mutableStateOf("") }

    // Populate form from ViewModel state
    LaunchedEffect(currentUser, uiState.currentProfile, uiState.currentWorkStats) {
        var updated = profileData

        currentUser?.let { user ->
            updated = updated.copy(
                name        = user.name,
                email       = user.email,
                role        = user.role,
                companyName = user.companyName,
                department  = user.department,
                designation = user.designation
            )
        }

        uiState.currentProfile?.let { profile ->
            updated = updated.copy(
                phoneNumber              = profile.phoneNumber,
                address                  = profile.address,
                dateOfBirth              = profile.dateOfBirth,
                joiningDate              = profile.joiningDate,
                employeeId               = profile.employeeId,
                reportingTo              = profile.reportingTo,
                salary                   = profile.salary,
                emergencyContactName     = profile.emergencyContactName,
                emergencyContactPhone    = profile.emergencyContactPhone,
                emergencyContactRelation = profile.emergencyContactRelation,
                imageUrl                 = profile.imageUrl
            )
        }

        uiState.currentWorkStats?.let { stats ->
            updated = updated.copy(experience = stats.experience)
        }

        profileData = updated
    }

    LaunchedEffect(uiState.selectedImageUri) {
        uiState.selectedImageUri?.let { uri ->
            profileData = profileData.copy(imageUri = uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEditMode || uiState.dataExists) "Edit Profile" else "Complete Profile",
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.dataExists && !isEditMode) {
                            Text(
                                text  = "Update your information",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditMode || uiState.dataExists) {
                        IconButton(
                            onClick  = { onSaveProfile(profileData) },
                            enabled  = !uiState.isLoading && isFormValid(profileData, password, confirmPassword, isEditMode || uiState.dataExists)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = "Save")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->

        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            if (uiState.isLoading && !uiState.isDataLoaded) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text  = "Loading profile data...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state           = scrollState,
                    modifier        = Modifier.fillMaxSize(),
                    contentPadding  = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    if (uiState.dataExists) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Profile Found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text("Your existing profile data has been loaded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        ProfileImageSection(
                            imageUrl    = profileData.imageUrl,
                            imageUri    = profileData.imageUri,
                            onImageClick = onImagePickClick,
                            canEdit     = permissions.canModifyUser || currentUser?.userId == userId,
                            userName    = profileData.name.ifEmpty { "User" }
                        )
                    }

                    item {
                        ProfileSectionCard(title = "Personal Information", icon = Icons.Default.Person) {
                            ProfileTextField(value = profileData.name, onValueChange = { profileData = profileData.copy(name = it) }, label = "Full Name", icon = Icons.Default.Person, enabled = canEditField("name", permissions.canModifyUser, currentUser?.role ?: ""))
                            ProfileTextField(value = profileData.email, onValueChange = { profileData = profileData.copy(email = it) }, label = "Email Address", icon = Icons.Default.Email, keyboardType = KeyboardType.Email, enabled = canEditField("email", permissions.canModifyUser, currentUser?.role ?: ""))
                            ProfileTextField(value = profileData.phoneNumber, onValueChange = { profileData = profileData.copy(phoneNumber = it) }, label = "Phone Number", icon = Icons.Default.Phone, keyboardType = KeyboardType.Phone)
                            ProfileTextField(value = profileData.address, onValueChange = { profileData = profileData.copy(address = it) }, label = "Address", icon = Icons.Default.LocationOn, maxLines = 3)
                            DatePickerField(value = profileData.dateOfBirth, onValueChange = { profileData = profileData.copy(dateOfBirth = it) }, label = "Date of Birth", onClick = { showDatePicker = DatePickerType.DATE_OF_BIRTH })
                        }
                    }

                    item {
                        ProfileSectionCard(title = "Work Information", icon = Icons.Default.Work) {
                            ProfileTextField(value = profileData.companyName, onValueChange = { profileData = profileData.copy(companyName = it) }, label = "Company Name", icon = Icons.Default.Business, enabled = canEditField("companyName", permissions.canModifyUser, currentUser?.role ?: ""))
                            ProfileTextField(value = profileData.department, onValueChange = { profileData = profileData.copy(department = it) }, label = "Department", icon = Icons.Default.Groups, enabled = canEditField("department", permissions.canModifyUser, currentUser?.role ?: ""))
                            ProfileTextField(value = profileData.designation, onValueChange = { profileData = profileData.copy(designation = it) }, label = "Designation", icon = Icons.Default.Badge, enabled = canEditField("designation", permissions.canModifyUser, currentUser?.role ?: ""))
                            RoleSelectionField(selectedRole = profileData.role, onRoleSelected = { profileData = profileData.copy(role = it) }, enabled = canEditField("role", permissions.canModifyUser, currentUser?.role ?: ""))
                            ProfileTextField(value = profileData.employeeId, onValueChange = { profileData = profileData.copy(employeeId = it) }, label = "Employee ID", icon = Icons.Default.Badge)
                            ProfileTextField(value = profileData.reportingTo, onValueChange = { profileData = profileData.copy(reportingTo = it) }, label = "Reporting To", icon = Icons.Default.SupervisorAccount)
                            DatePickerField(value = profileData.joiningDate, onValueChange = { profileData = profileData.copy(joiningDate = it) }, label = "Joining Date", onClick = { showDatePicker = DatePickerType.JOINING_DATE })
                            ProfileTextField(value = profileData.experience.toString(), onValueChange = { profileData = profileData.copy(experience = it.toIntOrNull() ?: 0) }, label = "Experience (Years)", icon = Icons.Default.Timeline, keyboardType = KeyboardType.Number)
                        }
                    }

                    if (permissions.canViewAllUsers && profileData.salary > 0) {
                        item {
                            ProfileSectionCard(title = "Confidential Information", icon = Icons.Default.Security) {
                                ProfileTextField(
                                    value = if (profileData.salary > 0) profileData.salary.toString() else "",
                                    onValueChange = { profileData = profileData.copy(salary = it.toDoubleOrNull() ?: 0.0) },
                                    label = "Salary", icon = Icons.Default.AttachMoney, keyboardType = KeyboardType.Number
                                )
                            }
                        }
                    }

                    item {
                        ProfileSectionCard(title = "Emergency Contact", icon = Icons.Default.ContactEmergency) {
                            ProfileTextField(value = profileData.emergencyContactName, onValueChange = { profileData = profileData.copy(emergencyContactName = it) }, label = "Contact Name", icon = Icons.Default.Person)
                            ProfileTextField(value = profileData.emergencyContactPhone, onValueChange = { profileData = profileData.copy(emergencyContactPhone = it) }, label = "Contact Phone", icon = Icons.Default.Phone, keyboardType = KeyboardType.Phone)
                            ProfileTextField(value = profileData.emergencyContactRelation, onValueChange = { profileData = profileData.copy(emergencyContactRelation = it) }, label = "Relationship", icon = Icons.Default.Person)
                        }
                    }

                    if (!isEditMode && !uiState.dataExists) {
                        item {
                            ProfileSectionCard(title = "Security", icon = Icons.Default.Security) {
                                ProfileTextField(
                                    value = password, onValueChange = { password = it }, label = "Password", icon = Icons.Default.Lock,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null) } }
                                )
                                ProfileTextField(
                                    value = confirmPassword, onValueChange = { confirmPassword = it }, label = "Confirm Password", icon = Icons.Default.Lock,
                                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = { IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) { Icon(if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null) } },
                                    isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                                    supportingText = if (confirmPassword.isNotEmpty() && password != confirmPassword) "Passwords do not match" else null
                                )
                            }
                        }
                    }

                    if (!isEditMode) {
                        item {
                            Button(
                                onClick  = { onSaveProfile(profileData) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled  = !uiState.isLoading && isFormValid(profileData, password, confirmPassword, isEditMode || uiState.dataExists),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = when {
                                        uiState.isLoading -> "Saving..."
                                        uiState.dataExists -> "Update Profile"
                                        else -> "Complete Profile"
                                    },
                                    fontSize = 16.sp, fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    uiState.error?.let { error ->
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(12.dp)) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        showDatePicker?.let { type ->
            DatePickerDialog(
                onDateSelected = { selectedDate ->
                    when (type) {
                        DatePickerType.DATE_OF_BIRTH -> profileData = profileData.copy(dateOfBirth = selectedDate)
                        DatePickerType.JOINING_DATE  -> profileData = profileData.copy(joiningDate = selectedDate)
                    }
                    showDatePicker = null
                },
                onDismiss = { showDatePicker = null }
            )
        }
    }
}

@Composable
private fun ProfileImageSection(imageUrl: String, imageUri: Uri?, onImageClick: () -> Unit, canEdit: Boolean, userName: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(enabled = canEdit) { onImageClick() }, contentAlignment = Alignment.Center) {
                when {
                    imageUri != null -> AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(imageUri).crossfade(true).build(), contentDescription = "Profile Image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    imageUrl.isNotEmpty() -> AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(), contentDescription = "Profile Image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else -> {
                        if (userName.isNotBlank()) {
                            val initials = userName.split(" ").take(2).map { it.firstOrNull()?.toString()?.uppercase() ?: "" }.joinToString("")
                            Text(text = initials, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (canEdit) {
                FloatingActionButton(onClick = onImageClick, modifier = Modifier.align(Alignment.BottomEnd).size(36.dp), containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Change Photo", modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (userName.isNotBlank()) {
            Text(text = userName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (canEdit) Text(text = "Tap to change photo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileSectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text, visualTransformation: VisualTransformation = VisualTransformation.None, trailingIcon: @Composable (() -> Unit)? = null, maxLines: Int = 1, isError: Boolean = false, supportingText: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline) },
        trailingIcon = trailingIcon,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation, maxLines = maxLines, isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline, disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleSelectionField(selectedRole: String, onRoleSelected: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    val roles = listOf("Administrator", "Manager", "Team Lead", "HR", "Employee", "Intern")
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded && enabled }) {
        OutlinedTextField(value = selectedRole, onValueChange = {}, readOnly = true, label = { Text("Role") },
            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(), enabled = enabled, shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline, disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            roles.forEach { role ->
                DropdownMenuItem(text = { Text(role) }, onClick = { onRoleSelected(role); expanded = false },
                    leadingIcon = { Icon(when (role) { "Administrator" -> Icons.Default.AdminPanelSettings; "Manager" -> Icons.Default.ManageAccounts; "Team Lead" -> Icons.Default.Groups; "HR" -> Icons.Default.People; "Intern" -> Icons.Default.School; else -> Icons.Default.Person }, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) })
            }
        }
    }
}

@Composable
private fun DatePickerField(value: Long?, onValueChange: (Long) -> Unit, label: String, onClick: () -> Unit) {
    val dateFormat   = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val displayValue = value?.let { dateFormat.format(Date(it)) } ?: ""
    OutlinedTextField(value = displayValue, onValueChange = {}, label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        enabled = false, readOnly = true, shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(onDateSelected: (Long) -> Unit, onDismiss: () -> Unit) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { onDateSelected(it) } }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = datePickerState) }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun canEditField(fieldName: String, canModify: Boolean, userRole: String): Boolean = when (fieldName) {
    "name", "email", "companyName", "role" -> userRole == "Administrator"
    "department", "designation"            -> userRole in listOf("Administrator", "Manager")
    else -> true
}

private fun isFormValid(profileData: ProfileData, password: String, confirmPassword: String, isEditMode: Boolean): Boolean {
    val basic = profileData.name.isNotBlank() && profileData.email.isNotBlank() && profileData.companyName.isNotBlank() && profileData.department.isNotBlank() && profileData.designation.isNotBlank() && profileData.phoneNumber.isNotBlank()
    return if (isEditMode) basic else basic && (password.isBlank() || password == confirmPassword)
}

private enum class DatePickerType { DATE_OF_BIRTH, JOINING_DATE }