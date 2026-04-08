package com.example.ritik_2.administrator.companysettings

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanySettingsScreen(
    viewModel   : CompanySettingsViewModel,
    onBack      : () -> Unit,
    onShowToast : (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snack  = remember { SnackbarHostState() }

    LaunchedEffect(state.successMsg) {
        state.successMsg?.let { snack.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snack.showSnackbar("⚠ $it"); viewModel.clearMessages() }
    }

    // ── Merge dialog ──────────────────────────────────────────────────────────
    if (state.showMergeDialog && state.mergeTargets.isNotEmpty()) {
        MergeDialog(
            current    = state.company,
            targets    = state.mergeTargets,
            isDbAdmin  = state.isDbAdmin,
            onRequest  = { viewModel.requestMerge(it) },
            onForce    = { src, tgt -> viewModel.forceApproveAndMerge(src, tgt) },
            onDismiss  = { viewModel.dismissMergeDialog() }
        )
    }

    Scaffold(
        topBar = {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(0.8f)
                    )))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Company Settings", fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, color = Color.White)
                        Text(state.company?.originalName ?: "Loading…",
                            fontSize = 11.sp, color = Color.White.copy(0.75f))
                    }
                    if (state.isOffline)
                        OfflineBadge()
                    if (!state.isEditing)
                        IconButton(onClick = { viewModel.setEditing(true) }) {
                            Icon(Icons.Default.Edit, "Edit", tint = Color.White)
                        }
                    else {
                        IconButton(onClick = { viewModel.setEditing(false) }) {
                            Icon(Icons.Default.Close, "Cancel", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        if (state.isLoading && state.company == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Stats header ──────────────────────────────────────────────────
            item {
                state.company?.let { c ->
                    CompanyStatsCard(c)
                }
            }

            // ── Rename section ────────────────────────────────────────────────
            item {
                RenameSection(
                    currentName = state.company?.originalName ?: "",
                    isEditing   = state.isEditing,
                    isLoading   = state.isLoading,
                    onRename    = { viewModel.renameCompany(it) }
                )
            }

            // ── Details section ───────────────────────────────────────────────
            item {
                DetailsSection(
                    company   = state.company,
                    isEditing = state.isEditing,
                    isLoading = state.isLoading,
                    onSave    = { web, addr, desc -> viewModel.updateDetails(web, addr, desc) }
                )
            }

            // ── Departments & Roles ───────────────────────────────────────────
            item {
                state.company?.let { c ->
                    InfoCard("Departments (${c.departments.size})", Icons.Default.AccountTree) {
                        if (c.departments.isEmpty()) {
                            Text("No departments yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            c.departments.forEach { dept ->
                                Row(Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ChevronRight, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(dept, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            item {
                state.company?.let { c ->
                    InfoCard("Roles (${c.availableRoles.size})", Icons.Default.AdminPanelSettings) {
                        if (c.availableRoles.isEmpty()) {
                            Text("No roles defined.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            c.availableRoles.forEach { role ->
                                Row(Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ChevronRight, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(role, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            // ── Merge section ─────────────────────────────────────────────────
            item {
                MergeSection(
                    company   = state.company,
                    isDbAdmin = state.isDbAdmin,
                    allCompanies = state.allCompanies,
                    pendingMerge = state.pendingMergeWith,
                    onStartMerge = { target ->
                        viewModel.requestMerge(target)
                    },
                    onForceApprove = { src, tgt ->
                        viewModel.forceApproveAndMerge(src, tgt)
                    }
                )
            }

            // ── DB admin: all companies ───────────────────────────────────────
            if (state.isDbAdmin && state.allCompanies.size > 1) {
                item {
                    Text("All Companies (DB Admin View)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp))
                }
                items(state.allCompanies) { c ->
                    AllCompanyCard(c)
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ── Company stats card ────────────────────────────────────────────────────────

@Composable
private fun CompanyStatsCard(c: CompanyInfo) {
    Card(
        Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Business, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(c.originalName,
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(c.sanitizedName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.6f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatPill("${c.totalUsers}",   "Total Users",  MaterialTheme.colorScheme.primary)
                StatPill("${c.activeUsers}",  "Active",       Color(0xFF4CAF50))
                StatPill("${c.departments.size}", "Depts",   Color(0xFF1976D2))
                StatPill("${c.availableRoles.size}", "Roles", Color(0xFFF57C00))
            }
        }
    }
}

@Composable
private fun StatPill(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Rename section ────────────────────────────────────────────────────────────

@Composable
private fun RenameSection(
    currentName: String, isEditing: Boolean, isLoading: Boolean,
    onRename: (String) -> Unit
) {
    var newName by remember(currentName) { mutableStateOf(currentName) }

    InfoCard("Company Name", Icons.Default.DriveFileRenameOutline) {
        if (isEditing) {
            OutlinedTextField(
                value         = newName,
                onValueChange = { newName = it },
                label         = { Text("Company Name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(10.dp)
            )
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null,
                        tint = Color(0xFFF57C00), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Renaming updates all users in this company.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE65100))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = { if (newName.isNotBlank() && newName != currentName) onRename(newName) },
                enabled  = !isLoading && newName.isNotBlank() && newName != currentName,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Default.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Apply Rename") }
            }
        } else {
            Text(currentName,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium)
        }
    }
}

// ── Details section ───────────────────────────────────────────────────────────

@Composable
private fun DetailsSection(
    company: CompanyInfo?, isEditing: Boolean, isLoading: Boolean,
    onSave: (String, String, String) -> Unit
) {
    var website     by remember(company) { mutableStateOf(company?.website     ?: "") }
    var address     by remember(company) { mutableStateOf(company?.address     ?: "") }
    var description by remember(company) { mutableStateOf(company?.description ?: "") }

    InfoCard("Company Details", Icons.Default.Info) {
        if (isEditing) {
            OutlinedTextField(website, { website = it }, label = { Text("Website") },
                leadingIcon = { Icon(Icons.Default.Language, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(address, { address = it }, label = { Text("Address") },
                leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(description, { description = it }, label = { Text("Description") },
                leadingIcon = { Icon(Icons.Default.Description, null) },
                modifier = Modifier.fillMaxWidth(), maxLines = 4,
                shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = { onSave(website, address, description) },
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Default.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Save Details") }
            }
        } else {
            if (company?.website?.isNotBlank() == true) InfoRow(Icons.Default.Language, "Website", company.website)
            if (company?.address?.isNotBlank() == true) InfoRow(Icons.Default.LocationOn, "Address", company.address)
            if (company?.description?.isNotBlank() == true) InfoRow(Icons.Default.Description, "Description", company.description)
            if (company?.website.isNullOrBlank() && company?.address.isNullOrBlank() && company?.description.isNullOrBlank()) {
                Text("No details added yet. Tap edit to add.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(0.7f),
            modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
        }
    }
}

// ── Merge section ─────────────────────────────────────────────────────────────

@Composable
private fun MergeSection(
    company     : CompanyInfo?,
    isDbAdmin   : Boolean,
    allCompanies: List<CompanyInfo>,
    pendingMerge: CompanyInfo?,
    onStartMerge  : (CompanyInfo) -> Unit,
    onForceApprove: (CompanyInfo, CompanyInfo) -> Unit
) {
    if (company == null) return

    InfoCard("Company Merge", Icons.Default.MergeType) {
        if (pendingMerge != null) {
            Card(colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF9C4)), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HourglassEmpty, null, tint = Color(0xFFF9A825))
                    Spacer(Modifier.width(8.dp))
                    Text("Merge request with ${pendingMerge.originalName} is pending admin approval.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            return@InfoCard
        }

        Text(
            "Merging combines all users from one company into another. " +
                    "Both companies' Administrators must approve.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isDbAdmin && allCompanies.size > 1) {
            Spacer(Modifier.height(8.dp))
            Text("Force Merge (DB Admin)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            val others = allCompanies.filter { it.sanitizedName != company.sanitizedName }
            others.forEach { other ->
                OutlinedButton(
                    onClick  = { onForceApprove(other, company) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.MergeType, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Merge ${other.originalName} → ${company.originalName}",
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ── Merge dialog ──────────────────────────────────────────────────────────────

@Composable
private fun MergeDialog(
    current  : CompanyInfo?,
    targets  : List<CompanyInfo>,
    isDbAdmin: Boolean,
    onRequest: (CompanyInfo) -> Unit,
    onForce  : (CompanyInfo, CompanyInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.MergeType, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Similar Company Found", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The following companies have similar names. Would you like to request a merge?")
                targets.forEach { t ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.originalName, fontWeight = FontWeight.SemiBold)
                            Text("${t.totalUsers} users · ${t.departments.size} departments",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick  = { onRequest(t) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Request Merge", fontSize = 11.sp) }
                                if (isDbAdmin && current != null) {
                                    Button(
                                        onClick  = { onForce(t, current) },
                                        modifier = Modifier.weight(1f),
                                        colors   = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Force Merge", fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── All company card (DB admin) ───────────────────────────────────────────────

@Composable
private fun AllCompanyCard(c: CompanyInfo) {
    Card(
        Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Business, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.originalName, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${c.totalUsers} users · ${c.departments.size} depts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = MaterialTheme.colorScheme.primary.copy(0.1f),
                shape = RoundedCornerShape(6.dp)) {
                Text("${c.activeUsers} active",
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun InfoCard(
    title  : String,
    icon   : ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun OfflineBadge() {
    Surface(color = Color.White.copy(0.15f), shape = RoundedCornerShape(8.dp)) {
        Text("Offline", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}