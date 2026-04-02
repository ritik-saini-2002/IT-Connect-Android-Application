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
import java.text.SimpleDateFormat
import java.util.*

// ── ProfileData — local form state (replaces old ProfileData) ─
data class ProfileData(
    val name                    : String = "",
    val email                   : String = "",
    val role                    : String = "Employee",
    val companyName             : String = "",
    val department              : String = "",
    val designation             : String = "",
    val phoneNumber             : String = "",
    val address                 : String = "",
    val dateOfBirth             : Long?  = null,
    val joiningDate             : Long?  = null,
    val employeeId              : String = "",
    val reportingTo             : String = "",
    val salary                  : Double = 0.0,
    val emergencyContactName    : String = "",
    val emergencyContactPhone   : String = "",
    val emergencyContactRelation: String = "",
    val experience              : Int    = 0,
    val imageUrl                : String = "",
    val imageUri                : Uri?   = null
)

// Convert ProfileData → ProfileSaveData for ViewModel
fun ProfileData.toSaveData() = ProfileSaveData(
    name                    = name,
    designation             = designation,
    phoneNumber             = phoneNumber,
    address                 = address,
    employeeId              = employeeId,
    reportingTo             = reportingTo,
    salary                  = salary,
    experience              = experience,
    emergencyContactName    = emergencyContactName,
    emergencyContactPhone   = emergencyContactPhone,
    emergencyContactRelation = emergencyContactRelation,
    existingImageUrl        = imageUrl
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCompletionScreen(
    viewModel       : ProfileCompletionViewModel,
    onImagePickClick: () -> Unit,
    onSaveProfile   : (ProfileSaveData) -> Unit,   // ✅ uses ProfileSaveData
    onNavigateBack  : () -> Unit,
    isEditMode      : Boolean = false,
    userId          : String  = ""
) {
    val scrollState = rememberLazyListState()
    val uiState     by viewModel.uiState.collectAsState()

    // ✅ Single local form state — no references to old ViewModel state fields
    var profileData by remember { mutableStateOf(ProfileData()) }
    var showDatePicker by remember { mutableStateOf<DatePickerType?>(null) }

    // Populate form when profile loads from ViewModel
    LaunchedEffect(uiState.userProfile) {
        uiState.userProfile?.let { profile ->
            profileData = ProfileData(
                name                    = profile.name,
                email                   = profile.email,
                role                    = profile.role,
                companyName             = profile.companyName,
                department              = profile.designation, // use designation as dept fallback
                designation             = profile.designation,
                phoneNumber             = profile.phoneNumber,
                imageUrl                = profile.imageUrl ?: ""
            )
        }
    }

    // Update imageUri when user picks an image
    LaunchedEffect(uiState.selectedImageUri) {
        uiState.selectedImageUri?.let { uri ->
            profileData = profileData.copy(imageUri = uri)
        }
    }

    val isDataExists = uiState.userProfile != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEditMode || isDataExists) "Edit Profile" else "Complete Profile",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isEditMode || isDataExists) {
                        IconButton(
                            onClick  = { onSaveProfile(profileData.toSaveData()) },
                            enabled  = !uiState.isLoading && isFormValid(profileData)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, "Save")
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

            if (uiState.isLoading && uiState.userProfile == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("Loading profile...", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    state               = scrollState,
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    if (isDataExists) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Profile Found", style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text("Your existing profile data has been loaded.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        ProfileImageSection(
                            imageUrl     = profileData.imageUrl,
                            imageUri     = profileData.imageUri,
                            onImageClick = onImagePickClick,
                            userName     = profileData.name.ifEmpty { "User" }
                        )
                    }

                    item {
                        ProfileSectionCard("Personal Information", Icons.Default.Person) {
                            PCTextField(profileData.name,        { profileData = profileData.copy(name = it) },        "Full Name",    Icons.Default.Person)
                            PCTextField(profileData.email,       { profileData = profileData.copy(email = it) },       "Email",        Icons.Default.Email, KeyboardType.Email)
                            PCTextField(profileData.phoneNumber, { profileData = profileData.copy(phoneNumber = it) }, "Phone",        Icons.Default.Phone, KeyboardType.Phone)
                            PCTextField(profileData.address,     { profileData = profileData.copy(address = it) },     "Address",      Icons.Default.LocationOn, maxLines = 3)
                            PCDateField(profileData.dateOfBirth, { profileData = profileData.copy(dateOfBirth = it) }, "Date of Birth") { showDatePicker = DatePickerType.DATE_OF_BIRTH }
                        }
                    }

                    item {
                        ProfileSectionCard("Work Information", Icons.Default.Work) {
                            PCTextField(profileData.companyName,  { profileData = profileData.copy(companyName = it) },  "Company",     Icons.Default.Business)
                            PCTextField(profileData.department,   { profileData = profileData.copy(department = it) },   "Department",  Icons.Default.Groups)
                            PCTextField(profileData.designation,  { profileData = profileData.copy(designation = it) },  "Designation", Icons.Default.Badge)
                            PCRoleField(profileData.role)         { profileData = profileData.copy(role = it) }
                            PCTextField(profileData.employeeId,   { profileData = profileData.copy(employeeId = it) },   "Employee ID", Icons.Default.Badge)
                            PCTextField(profileData.reportingTo,  { profileData = profileData.copy(reportingTo = it) },  "Reporting To",Icons.Default.SupervisorAccount)
                            PCDateField(profileData.joiningDate,  { profileData = profileData.copy(joiningDate = it) },  "Joining Date") { showDatePicker = DatePickerType.JOINING_DATE }
                            PCTextField(profileData.experience.toString(), { profileData = profileData.copy(experience = it.toIntOrNull() ?: 0) }, "Experience (Years)", Icons.Default.Timeline, KeyboardType.Number)
                        }
                    }

                    item {
                        ProfileSectionCard("Emergency Contact", Icons.Default.ContactEmergency) {
                            PCTextField(profileData.emergencyContactName,     { profileData = profileData.copy(emergencyContactName = it) },     "Contact Name",  Icons.Default.Person)
                            PCTextField(profileData.emergencyContactPhone,    { profileData = profileData.copy(emergencyContactPhone = it) },    "Contact Phone", Icons.Default.Phone, KeyboardType.Phone)
                            PCTextField(profileData.emergencyContactRelation, { profileData = profileData.copy(emergencyContactRelation = it) }, "Relationship",  Icons.Default.People)
                        }
                    }

                    if (!isEditMode) {
                        item {
                            Button(
                                onClick  = { onSaveProfile(profileData.toSaveData()) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled  = !uiState.isLoading && isFormValid(profileData),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (uiState.isLoading) "Saving..." else if (isDataExists) "Update Profile" else "Complete Profile",
                                    fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    uiState.error?.let { error ->
                        item {
                            Card(modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape  = RoundedCornerShape(12.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(error, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }

        showDatePicker?.let { type ->
            PCDatePickerDialog(
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

// ── Helper Composables ────────────────────────────────────────

@Composable
private fun ProfileImageSection(imageUrl: String, imageUri: Uri?, onImageClick: () -> Unit, userName: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                modifier = Modifier.size(120.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onImageClick() },
                contentAlignment = Alignment.Center
            ) {
                when {
                    imageUri != null ->
                        AsyncImage(ImageRequest.Builder(LocalContext.current).data(imageUri).crossfade(true).build(),
                            "Profile", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    imageUrl.isNotEmpty() ->
                        AsyncImage(ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(),
                            "Profile", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else -> {
                        val initials = userName.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }.joinToString("")
                        if (initials.isNotBlank()) Text(initials, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Icon(Icons.Default.Person, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FloatingActionButton(onClick = onImageClick, modifier = Modifier.align(Alignment.BottomEnd).size(36.dp),
                containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.CameraAlt, "Change Photo", modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        if (userName.isNotBlank() && userName != "User")
            Text(userName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Tap to change photo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileSectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun PCTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector,
                        keyboardType: KeyboardType = KeyboardType.Text, maxLines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        maxLines = maxLines, singleLine = maxLines == 1,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PCRoleField(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val roles = listOf("Administrator","Manager","Team Lead","HR","Employee","Intern")
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(value = selected, onValueChange = {}, readOnly = true, label = { Text("Role") },
            leadingIcon = { Icon(Icons.Default.Badge, null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            roles.forEach { r -> DropdownMenuItem(text = { Text(r) }, onClick = { onSelected(r); expanded = false }) }
        }
    }
}

@Composable
private fun PCDateField(value: Long?, onValueChange: (Long) -> Unit, label: String, onClick: () -> Unit) {
    val fmt          = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val displayValue = value?.let { fmt.format(Date(it)) } ?: ""
    OutlinedTextField(value = displayValue, onValueChange = {}, label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        enabled = false, readOnly = true, shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PCDatePickerDialog(onDateSelected: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton    = { TextButton(onClick = { state.selectedDateMillis?.let { onDateSelected(it) } }) { Text("OK") } },
        dismissButton    = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}

private fun isFormValid(d: ProfileData): Boolean =
    d.name.isNotBlank() && d.designation.isNotBlank()

private enum class DatePickerType { DATE_OF_BIRTH, JOINING_DATE }