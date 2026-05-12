package com.saini.ritik.profile.profilecompletion

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.saini.ritik.core.PermissionGuard

private data class FormState(
    val address                  : String = "",
    val employeeId               : String = "",
    val reportingTo              : String = "",
    val salary                   : String = "",
    val experience               : String = "",
    val emergencyContactName     : String = "",
    val emergencyContactPhone    : String = "",
    val emergencyContactRelation : String = "",
    val imageUri                 : Uri?   = null,
    // Admin editable
    val name                     : String = "",
    val phoneNumber              : String = "",
    val designation              : String = "",
    val role                     : String = "",
    val department               : String = "",
    val companyName              : String = ""
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
    existingImageUrl         = existingImageUrl,
    name                     = name,
    phoneNumber              = phoneNumber,
    designation              = designation,
    role                     = role,
    department               = department,
    companyName              = companyName
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileCompletionScreen(
    viewModel            : ProfileCompletionViewModel,
    onImagePickClick     : () -> Unit,
    onSaveProfile        : (ProfileSaveData) -> Unit,
    onSavePermissions    : (List<String>) -> Unit = {},
    onNavigateBack       : () -> Unit,
    isEditMode           : Boolean    = false,
    isAdmin              : Boolean    = false,
    isManager            : Boolean    = false,   // Manager or HR editing another user
    isSelfEdit           : Boolean    = false,   // true when editor == target
    userId               : String     = "",
    editableFields       : Set<String> = PermissionGuard.ALL_FIELDS,
    canManagePermissions : Boolean    = false,
    editorPermissions    : List<String> = emptyList(),
    /**
     * Permission-based per-field gate. Only fields whose key is in this set
     * are exposed for editing in the admin branch — even if [isAdmin] is true.
     * Computed by the launching Activity from [PermissionGuard.editableFields].
     * Defaults to ALL_FIELDS so legacy callers keep their previous behaviour.
     */
) {
    val uiState    by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()
    var form       by remember { mutableStateOf(FormState()) }
    val isEditing   = uiState.isEditing

    // Permissions panel state — only initialised when canManagePermissions is true
    var showPermissionsPanel by remember { mutableStateOf(false) }
    var pendingPerms by remember(uiState.userProfile) {
        mutableStateOf(uiState.userProfile?.permissions ?: emptyList())
    }
    // The set of permissions this editor is allowed to grant — their own perms
    // minus system-only ones (already enforced server-side, but guard in UI too)
    val grantablePerms = remember(editorPermissions) {
        com.saini.ritik.data.model.Permissions.ALL_PERMISSIONS.filter { perm ->
            // Sysadmin / grant_revoke_any sees all; others see only their own perms
            com.saini.ritik.data.model.Permissions.PERM_GRANT_REVOKE_ANY_PERMISSION in editorPermissions ||
                    perm in editorPermissions
        }
    }

    // Determine screen title
    val title = when {
        !isEditMode -> "Complete Your Profile"
        isAdmin     -> "Edit User Profile (Admin)"
        isManager   -> "Edit User Profile"
        else        -> "My Profile"
    }

    // Pre-fill form from loaded profile
    LaunchedEffect(uiState.userProfile) {
        uiState.userProfile?.let { p ->
            form = FormState(
                address                  = p.address,
                employeeId               = p.employeeId,
                reportingTo              = p.reportingTo,
                salary                   = if (p.salary > 0) p.salary.toBigDecimal()
                    .stripTrailingZeros().toPlainString() else "",
                experience               = if (p.experience > 0) p.experience.toString() else "",
                emergencyContactName     = p.emergencyContactName,
                emergencyContactPhone    = p.emergencyContactPhone,
                emergencyContactRelation = p.emergencyContactRelation,
                name                     = p.name,
                phoneNumber              = p.phoneNumber,
                designation              = p.designation,
                role                     = p.role,
                department               = p.department,
                companyName              = p.companyName
            )
        }
    }

    LaunchedEffect(uiState.selectedImageUri) {
        uiState.selectedImageUri?.let { form = form.copy(imageUri = it) }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, fontWeight = FontWeight.Bold,
                                color = Color.White, fontSize = 16.sp)
                            uiState.userProfile?.let { p ->
                                if (isEditMode)
                                    Text("${p.name} · ${p.role}",
                                        fontSize = 12.sp,
                                        color    = Color.White.copy(0.8f))
                            }
                        }
                    },
                    navigationIcon = {
                        if (isEditMode) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                            }
                        }
                    },
                    actions = {
                        if (isEditMode) {
                            if (isEditing) {
                                TextButton(
                                    onClick = {
                                        onSaveProfile(
                                            form.toSaveData(uiState.userProfile?.imageUrl ?: "")
                                        )
                                    },
                                    enabled = !uiState.isLoading
                                ) {
                                    if (uiState.isLoading)
                                        CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            color       = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    else
                                        Text("Save",
                                            fontWeight = FontWeight.Bold,
                                            color      = Color.White)
                                }
                                IconButton(onClick = { viewModel.setEditing(false) }) {
                                    Icon(Icons.Default.Close, "Cancel", tint = Color.White)
                                }
                            } else {
                                IconButton(onClick = { viewModel.toggleEditing() }) {
                                    Icon(Icons.Default.Edit, "Edit", tint = Color.White)
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            if (uiState.isLoading && uiState.userProfile == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(48.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(14.dp))
                        Text("Loading…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Box
            }

            LazyColumn(
                state               = scrollState,
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // Welcome banner (first-time only)
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
                                    Text("Fill in the details below to complete your profile.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                            .copy(0.8f))
                                }
                            }
                        }
                    }
                }

                // Permission banner for Manager/HR
                if (isManager && isEditMode) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF57C00).copy(0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null,
                                    tint     = Color(0xFFF57C00),
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "You can update designation, department, " +
                                            "experience and emergency contact.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
                    }
                }

                // Avatar
                item {
                    AvatarSection(
                        imageUrl     = uiState.userProfile?.imageUrl ?: "",
                        imageUri     = form.imageUri,
                        userName     = uiState.userProfile?.name ?: "",
                        isEditing    = isEditing,
                        onImageClick = { if (isEditing) onImagePickClick() }
                    )
                }

                // ── Account Info ─────────────────────────────────────────────
                item {
                    PCCard("Account Info", Icons.Default.Person) {
                        when {
                            isAdmin && isEditing -> {
                                // Admin branch — each field gated by editableFields.
                                // company/dept/role are LOCKED when editing own profile
                                // (isSelfEdit) to prevent self-escalation.
                                if ("name" in editableFields)
                                    PCField(form.name, { form = form.copy(name = it) },
                                        "Full Name", Icons.Default.Person)
                                if ("phoneNumber" in editableFields)
                                    PCField(form.phoneNumber, { form = form.copy(phoneNumber = it) },
                                        "Phone", Icons.Default.Phone, KeyboardType.Phone)
                                if ("designation" in editableFields)
                                    PCField(form.designation, { form = form.copy(designation = it) },
                                        "Designation", Icons.Default.Badge)
                                // role, companyName, department locked for own-profile
                                if ("role" in editableFields && !isSelfEdit)
                                    PCField(form.role, { form = form.copy(role = it) },
                                        "Role", Icons.Default.ManageAccounts)
                                else ReadOnlyRow("Role", form.role, Icons.Default.ManageAccounts)
                                if ("companyName" in editableFields && !isSelfEdit)
                                    PCField(form.companyName, { form = form.copy(companyName = it) },
                                        "Company", Icons.Default.Business)
                                else ReadOnlyRow("Company", form.companyName, Icons.Default.Business)
                                if ("department" in editableFields && !isSelfEdit)
                                    PCField(form.department, { form = form.copy(department = it) },
                                        "Department", Icons.Default.Groups)
                                else ReadOnlyRow("Department", form.department, Icons.Default.Groups)
                            }
                            isManager && isEditing -> {
                                uiState.userProfile?.let { p ->
                                    ReadOnlyRow("Full Name",   p.name,        Icons.Default.Person)
                                    ReadOnlyRow("Email",       p.email,       Icons.Default.Email)
                                    ReadOnlyRow("Phone",       p.phoneNumber, Icons.Default.Phone)
                                    ReadOnlyRow("Role",        p.role,        Icons.Default.ManageAccounts)
                                    ReadOnlyRow("Company",     p.companyName, Icons.Default.Business)
                                }
                                PCField(form.designation, { form = form.copy(designation = it) },
                                    "Designation", Icons.Default.Badge)
                                PCField(form.department, { form = form.copy(department = it) },
                                    "Department", Icons.Default.Groups)
                            }
                            isSelfEdit && isEditing -> {
                                // Own-profile: phone is editable; name/role/company/dept/designation locked
                                uiState.userProfile?.let { p ->
                                    ReadOnlyRow("Full Name",   p.name,        Icons.Default.Person)
                                    ReadOnlyRow("Email",       p.email,       Icons.Default.Email)
                                    ReadOnlyRow("Designation", p.designation, Icons.Default.Badge)
                                    ReadOnlyRow("Role",        p.role,        Icons.Default.ManageAccounts)
                                    ReadOnlyRow("Company",     p.companyName, Icons.Default.Business)
                                    ReadOnlyRow("Department",  p.department,  Icons.Default.Groups)
                                }
                                if ("phoneNumber" in editableFields)
                                    PCField(form.phoneNumber, { form = form.copy(phoneNumber = it) },
                                        "Phone", Icons.Default.Phone, KeyboardType.Phone)
                            }
                            else -> {
                                uiState.userProfile?.let { p ->
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
                    }
                }

                // ── Professional Details ─────────────────────────────────────
                item {
                    PCCard("Professional Details", Icons.Default.Work) {
                        when {
                            isAdmin && isEditing -> {
                                if ("experience" in editableFields)
                                    PCField(form.experience,  { form = form.copy(experience = it) },
                                        "Years of Experience", Icons.Default.Timeline, KeyboardType.Number)
                                if ("employeeId" in editableFields)
                                    PCField(form.employeeId,  { form = form.copy(employeeId = it) },
                                        "Employee ID",  Icons.Default.Badge)
                                if ("reportingTo" in editableFields)
                                    PCField(form.reportingTo, { form = form.copy(reportingTo = it) },
                                        "Reporting To", Icons.Default.SupervisorAccount)
                                if ("salary" in editableFields)
                                    PCField(form.salary,      { form = form.copy(salary = it) },
                                        "Salary",       Icons.Default.CurrencyRupee, KeyboardType.Number)
                            }
                            isEditing -> {
                                // Manager, HR or own profile — only experience is editable
                                PCField(form.experience, { form = form.copy(experience = it) },
                                    "Years of Experience", Icons.Default.Timeline, KeyboardType.Number)
                                uiState.userProfile?.let { p ->
                                    ReadOnlyRow("Employee ID",  p.employeeId,  Icons.Default.Badge)
                                    ReadOnlyRow("Reporting To", p.reportingTo, Icons.Default.SupervisorAccount)
                                    if (isAdmin)
                                        ReadOnlyRow("Salary",
                                            if (p.salary > 0) p.salary.toString() else "—",
                                            Icons.Default.CurrencyRupee)
                                }
                            }
                            else -> {
                                uiState.userProfile?.let { p ->
                                    ReadOnlyRow("Experience",
                                        if (p.experience > 0) "${p.experience} yrs" else "—",
                                        Icons.Default.Timeline)
                                    ReadOnlyRow("Employee ID",  p.employeeId,  Icons.Default.Badge)
                                    ReadOnlyRow("Reporting To", p.reportingTo, Icons.Default.SupervisorAccount)
                                    ReadOnlyRow("Salary",
                                        if (p.salary > 0) p.salary.toString() else "—",
                                        Icons.Default.CurrencyRupee)
                                }
                            }
                        }
                    }
                }

                // ── Work Stats (read-only for everyone) ──────────────────────
                item {
                    PCCard("Work Stats", Icons.Default.BarChart) {
                        uiState.userProfile?.let { p ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatBox("${p.completedProjects}", "Completed",  Color(0xFF2E7D32))
                                StatBox("${p.activeProjects}",    "Active",     Color(0xFF1565C0))
                                StatBox("${p.pendingTasks}",      "Pending",    Color(0xFFF57C00))
                                StatBox("${p.completedTasks}",    "Tasks Done", Color(0xFF6A1B9A))
                            }
                            if (p.totalComplaints > 0) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StatBox("${p.totalComplaints}",    "Complaints",  Color(0xFFC62828))
                                    StatBox("${p.resolvedComplaints}", "Resolved",    Color(0xFF2E7D32))
                                    StatBox("${p.pendingComplaints}",  "Pending",     Color(0xFFF57C00))
                                }
                            }
                        }
                    }
                }

                // ── Personal Details ─────────────────────────────────────────
                item {
                    PCCard("Personal Details", Icons.Default.PersonPin) {
                        if (isEditing) {
                            PCField(form.address, { form = form.copy(address = it) },
                                "Address", Icons.Default.LocationOn, maxLines = 3)
                        } else {
                            ReadOnlyRow("Address",
                                uiState.userProfile?.address?.ifBlank { "—" } ?: "—",
                                Icons.Default.LocationOn)
                        }
                    }
                }

                // ── Emergency Contact ────────────────────────────────────────
                item {
                    PCCard("Emergency Contact", Icons.Default.ContactEmergency) {
                        if (isEditing) {
                            PCField(form.emergencyContactName,
                                { form = form.copy(emergencyContactName = it) },
                                "Contact Name",  Icons.Default.Person)
                            PCField(form.emergencyContactPhone,
                                { form = form.copy(emergencyContactPhone = it) },
                                "Contact Phone", Icons.Default.Phone, KeyboardType.Phone)
                            PCField(form.emergencyContactRelation,
                                { form = form.copy(emergencyContactRelation = it) },
                                "Relationship",  Icons.Default.People)
                        } else {
                            uiState.userProfile?.let { p ->
                                ReadOnlyRow("Name",
                                    p.emergencyContactName.ifBlank { "—" },
                                    Icons.Default.Person)
                                ReadOnlyRow("Phone",
                                    p.emergencyContactPhone.ifBlank { "—" },
                                    Icons.Default.Phone)
                                ReadOnlyRow("Relationship",
                                    p.emergencyContactRelation.ifBlank { "—" },
                                    Icons.Default.People)
                            }
                        }
                    }
                }

                // ── Permissions Panel ────────────────────────────────────────
                // Visible only to users who can grant/revoke permissions on this target.
                // Shows only permissions within the editor's own permission set.
                if (canManagePermissions && isEditMode) {
                    item {
                        Card(
                            modifier  = Modifier.fillMaxWidth(),
                            shape     = RoundedCornerShape(14.dp),
                            colors    = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                // Header row with toggle
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Security, null,
                                            tint     = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Permissions",
                                            style      = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (showPermissionsPanel) {
                                            TextButton(onClick = {
                                                onSavePermissions(pendingPerms)
                                                showPermissionsPanel = false
                                            }) {
                                                Text("Save", fontWeight = FontWeight.Bold)
                                            }
                                            IconButton(onClick = {
                                                // Reset to original on cancel
                                                pendingPerms = uiState.userProfile?.permissions ?: emptyList()
                                                showPermissionsPanel = false
                                            }) {
                                                Icon(Icons.Default.Close, "Cancel",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        } else {
                                            IconButton(onClick = { showPermissionsPanel = true }) {
                                                Icon(Icons.Default.Edit, "Edit permissions",
                                                    tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }

                                if (showPermissionsPanel) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "You can only grant permissions you hold yourself.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    // Group permissions into readable categories
                                    val grouped = grantablePerms.groupBy { perm ->
                                        when {
                                            perm.startsWith("windows_control_") -> "Windows Control"
                                            perm.startsWith("access_")          -> "Feature Access"
                                            perm.startsWith("view_")            -> "View"
                                            perm.startsWith("manage_")          -> "Manage"
                                            perm.startsWith("edit_")            -> "Edit"
                                            perm.startsWith("submit_")          -> "Submit"
                                            perm.startsWith("resolve_")         -> "Resolve"
                                            perm.startsWith("approve_")         -> "Approve"
                                            perm.startsWith("generate_")        -> "Generate"
                                            perm.startsWith("assign_")          -> "Assign"
                                            perm.startsWith("grant_")           -> "Grant"
                                            perm.startsWith("export_")          -> "Export"
                                            else                                 -> "Other"
                                        }
                                    }
                                    grouped.forEach { (category, perms) ->
                                        Text(category,
                                            style      = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = MaterialTheme.colorScheme.primary,
                                            modifier   = Modifier.padding(top = 8.dp, bottom = 4.dp))
                                        perms.forEach { perm ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked         = perm in pendingPerms,
                                                    onCheckedChange = { checked ->
                                                        pendingPerms = if (checked)
                                                            (pendingPerms + perm).distinct()
                                                        else
                                                            pendingPerms - perm
                                                    }
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    perm.replace("_", " ")
                                                        .replaceFirstChar { it.uppercase() },
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Collapsed — show current permissions as chips
                                    val currentPerms = uiState.userProfile?.permissions ?: emptyList()
                                    if (currentPerms.isEmpty()) {
                                        Text("No permissions assigned",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 6.dp))
                                    } else {
                                        Spacer(Modifier.height(6.dp))
                                        androidx.compose.foundation.layout.FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement   = Arrangement.spacedBy(6.dp)
                                        ) {
                                            currentPerms.forEach { perm ->
                                                SuggestionChip(
                                                    onClick = {},
                                                    label   = {
                                                        Text(
                                                            perm.replace("_", " "),
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Error
                // Error
                uiState.error?.let { err ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,          // ← softer icon than Error
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        err,
                                        color      = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                    // Only show the "contact admin" sub-line for permission errors
                                    if (err.contains("administrator", ignoreCase = true)) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "Contact your Administrator to update this information.",
                                            color    = MaterialTheme.colorScheme.onErrorContainer,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Save button (first-time only)
                if (!isEditMode) {
                    item {
                        Button(
                            onClick  = {
                                onSaveProfile(
                                    form.toSaveData(uiState.userProfile?.imageUrl ?: "")
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled  = !uiState.isLoading,
                            shape    = RoundedCornerShape(14.dp)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    color       = MaterialTheme.colorScheme.onPrimary,
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

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun AvatarSection(
    imageUrl    : String,
    imageUri    : Uri?,
    userName    : String,
    isEditing   : Boolean,
    onImageClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (isEditing) Modifier.clickable { onImageClick() } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                when {
                    imageUri != null -> AsyncImage(
                        ImageRequest.Builder(LocalContext.current)
                            .data(imageUri).crossfade(true).build(),
                        "Avatar", Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop)
                    imageUrl.isNotBlank() -> AsyncImage(
                        ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl).crossfade(true).build(),
                        "Avatar", Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop)
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
            if (isEditing) {
                FloatingActionButton(
                    onClick        = onImageClick,
                    modifier       = Modifier.align(Alignment.BottomEnd).size(34.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(16.dp))
                }
            }
        }
        if (isEditing) {
            Spacer(Modifier.height(6.dp))
            Text("Tap to update photo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PCCard(
    title  : String,
    icon   : ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun StatBox(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String, icon: ImageVector) {
    if (value.isBlank() || value == "—") return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null,
            tint     = MaterialTheme.colorScheme.primary.copy(0.65f),
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
        color = MaterialTheme.colorScheme.outlineVariant.copy(0.35f))
}

@Composable
private fun PCField(
    value        : String,
    onValueChange: (String) -> Unit,
    label        : String,
    icon         : ImageVector,
    keyboardType : KeyboardType = KeyboardType.Text,
    maxLines     : Int          = 1
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
        shape           = RoundedCornerShape(10.dp)
    )
}