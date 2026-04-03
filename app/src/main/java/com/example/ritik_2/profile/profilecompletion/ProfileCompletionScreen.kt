package com.example.ritik_2.profile.profilecompletion

import android.net.Uri
import androidx.compose.foundation.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

// ── Local form state — ONLY fields not collected at registration ───────────────
private data class FormState(
    val address                  : String = "",
    val employeeId               : String = "",
    val reportingTo              : String = "",
    val salary                   : String = "",
    val experience               : String = "",
    val emergencyContactName     : String = "",
    val emergencyContactPhone    : String = "",
    val emergencyContactRelation : String = "",
    val imageUri                 : Uri?   = null
)

private fun FormState.toSaveData(existingImageUrl: String) = ProfileSaveData(
    address                  = address,
    employeeId               = employeeId,
    reportingTo              = reportingTo,
    salary                   = salary.toDoubleOrNull() ?: 0.0,
    experience               = experience.toIntOrNull() ?: 0,
    emergencyContactName     = emergencyContactName,
    emergencyContactPhone    = emergencyContactPhone,
    emergencyContactRelation = emergencyContactRelation,
    existingImageUrl         = existingImageUrl
)

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCompletionScreen(
    viewModel        : ProfileCompletionViewModel,
    onImagePickClick : () -> Unit,
    onSaveProfile    : (ProfileSaveData) -> Unit,
    onNavigateBack   : () -> Unit,
    isEditMode       : Boolean = false,
    userId           : String  = ""
) {
    val uiState    by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()
    var form       by remember { mutableStateOf(FormState()) }

    // Pre-fill from existing profile when in edit mode
    LaunchedEffect(uiState.userProfile) {
        uiState.userProfile?.let { p ->
            form = FormState(
                address                  = p.address,
                employeeId               = p.employeeId,
                reportingTo              = p.reportingTo,
                salary                   = if (p.salary > 0) p.salary.toBigDecimal().stripTrailingZeros().toPlainString() else "",
                experience               = if (p.experience > 0) p.experience.toString() else "",
                emergencyContactName     = p.emergencyContactName,
                emergencyContactPhone    = p.emergencyContactPhone,
                emergencyContactRelation = p.emergencyContactRelation
            )
        }
    }

    // Sync newly picked image URI
    LaunchedEffect(uiState.selectedImageUri) {
        uiState.selectedImageUri?.let { form = form.copy(imageUri = it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditMode) "Edit Profile" else "Complete Your Profile",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (isEditMode) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    if (isEditMode) {
                        TextButton(
                            onClick = { onSaveProfile(form.toSaveData(uiState.userProfile?.imageUrl ?: "")) },
                            enabled = !uiState.isLoading
                        ) {
                            if (uiState.isLoading)
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else
                                Text("Save", fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            if (uiState.isLoading && uiState.userProfile == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(48.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(14.dp))
                        Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Box
            }

            LazyColumn(
                state               = scrollState,
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Welcome banner (first-time only) ─────────────────────────
                if (!isEditMode) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null,
                                    tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Almost there!",
                                        style      = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "Just a few more details to complete your profile.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.8f)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Avatar ───────────────────────────────────────────────────
                item {
                    AvatarSection(
                        imageUrl     = uiState.userProfile?.imageUrl ?: "",
                        imageUri     = form.imageUri,
                        userName     = uiState.userProfile?.name ?: "",
                        onImageClick = onImagePickClick
                    )
                }

                // ── Read-only: already filled at registration ─────────────────
                uiState.userProfile?.let { p ->
                    item {
                        PCCard("Your Account", Icons.Default.Lock) {
                            ReadOnlyRow("Full Name",   p.name,        Icons.Default.Person)
                            ReadOnlyRow("Email",       p.email,       Icons.Default.Email)
                            ReadOnlyRow("Phone",       p.phoneNumber, Icons.Default.Phone)
                            ReadOnlyRow("Designation", p.designation, Icons.Default.Badge)
                            ReadOnlyRow("Role",        p.role,        Icons.Default.ManageAccounts)
                            ReadOnlyRow("Company",     p.companyName, Icons.Default.Business)
                            ReadOnlyRow("Department",  p.department,  Icons.Default.Groups)
                        }
                    }
                }

                // ── Professional extras (editable, new) ───────────────────────
                item {
                    PCCard("Professional Details", Icons.Default.Work) {
                        PCField(form.experience,
                            { form = form.copy(experience = it) },
                            "Years of Experience", Icons.Default.Timeline,
                            keyboardType = KeyboardType.Number)
                        PCField(form.employeeId,
                            { form = form.copy(employeeId = it) },
                            "Employee ID", Icons.Default.Badge)
                        PCField(form.reportingTo,
                            { form = form.copy(reportingTo = it) },
                            "Reporting To", Icons.Default.SupervisorAccount)
                        PCField(form.salary,
                            { form = form.copy(salary = it) },
                            "Salary", Icons.Default.CurrencyRupee,
                            keyboardType = KeyboardType.Number)
                    }
                }

                // ── Personal extras (editable, new) ───────────────────────────
                item {
                    PCCard("Personal Details", Icons.Default.PersonPin) {
                        PCField(form.address,
                            { form = form.copy(address = it) },
                            "Address", Icons.Default.LocationOn,
                            maxLines = 3)
                    }
                }

                // ── Emergency contact (editable, new) ─────────────────────────
                item {
                    PCCard("Emergency Contact", Icons.Default.ContactEmergency) {
                        PCField(form.emergencyContactName,
                            { form = form.copy(emergencyContactName = it) },
                            "Contact Name", Icons.Default.Person)
                        PCField(form.emergencyContactPhone,
                            { form = form.copy(emergencyContactPhone = it) },
                            "Contact Phone", Icons.Default.Phone,
                            keyboardType = KeyboardType.Phone)
                        PCField(form.emergencyContactRelation,
                            { form = form.copy(emergencyContactRelation = it) },
                            "Relationship", Icons.Default.People)
                    }
                }

                // ── Error ─────────────────────────────────────────────────────
                uiState.error?.let { err ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape  = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(10.dp))
                                Text(err,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                // ── Save button (first-time mode) ─────────────────────────────
                if (!isEditMode) {
                    item {
                        Button(
                            onClick  = {
                                onSaveProfile(form.toSaveData(uiState.userProfile?.imageUrl ?: ""))
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled  = !uiState.isLoading,
                            shape    = RoundedCornerShape(14.dp)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Saving…", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            } else {
                                Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Complete Profile",
                                    fontSize   = 16.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helper composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AvatarSection(
    imageUrl     : String,
    imageUri     : Uri?,
    userName     : String,
    onImageClick : () -> Unit
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onImageClick() },
                contentAlignment = Alignment.Center
            ) {
                when {
                    imageUri != null      -> AsyncImage(
                        ImageRequest.Builder(LocalContext.current).data(imageUri).crossfade(true).build(),
                        "Avatar", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    imageUrl.isNotBlank() -> AsyncImage(
                        ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(),
                        "Avatar", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else -> {
                        val initials = userName.split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                            .joinToString("")
                        if (initials.isNotBlank())
                            Text(initials,
                                style      = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant)
                        else
                            Icon(Icons.Default.Person, null, Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FloatingActionButton(
                onClick        = onImageClick,
                modifier       = Modifier.align(Alignment.BottomEnd).size(34.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.CameraAlt, null, Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Tap to update photo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PCCard(
    title   : String,
    icon    : ImageVector,
    content : @Composable ColumnScope.() -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String, icon: ImageVector) {
    if (value.isBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null,
            tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
            modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
        }
    }
    HorizontalDivider(
        Modifier.padding(top = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

@Composable
private fun PCField(
    value         : String,
    onValueChange : (String) -> Unit,
    label         : String,
    icon          : ImageVector,
    keyboardType  : KeyboardType = KeyboardType.Text,
    maxLines      : Int          = 1
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        label           = { Text(label) },
        leadingIcon     = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier        = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        maxLines        = maxLines,
        singleLine      = maxLines == 1,
        shape           = RoundedCornerShape(10.dp),
        colors          = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline)
    )
}