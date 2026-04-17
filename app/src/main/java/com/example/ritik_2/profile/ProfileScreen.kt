package com.example.ritik_2.profile

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.data.model.Permissions

@Composable
fun ProfileScreen(
    imageUrl            : String?,
    name                : String,
    email               : String,
    phoneNumber         : String,
    designation         : String,
    companyName         : String,
    department          : String,
    role                : String,
    userId              : String,
    experience          : Int,
    completedProjects   : Int,
    activeProjects      : Int,
    pendingTasks        : Int,
    totalComplaints     : Int,
    resolvedComplaints  : Int,
    isLoading           : Boolean,
    canEdit             : Boolean          = false,
    permissions         : List<String>     = emptyList(),
    canManagePermissions: Boolean          = false,
    onSavePermissions   : (List<String>) -> Unit = {},
    onLogoutClick       : () -> Unit,
    onEditClick         : () -> Unit,
    onBackClick         : () -> Unit
) {
    var showPermissionsDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {

            // ── Hero gradient header ──────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().height(280.dp)
                    .background(Brush.verticalGradient(listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        MaterialTheme.colorScheme.background
                    )))
            ) {
                IconButton(
                    onClick  = onBackClick,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp).align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.ArrowBack, "Back",
                        tint = MaterialTheme.colorScheme.onPrimary)
                }

                Column(
                    modifier            = Modifier.align(Alignment.Center).padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(112.dp).background(
                            Brush.linearGradient(listOf(
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.primary
                            )), CircleShape
                        ).padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageUrl).crossfade(true).build(),
                                contentDescription = "Avatar",
                                modifier     = Modifier.size(106.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                Modifier.size(106.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val initials = name.split(" ").take(2)
                                    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                                    .joinToString("")
                                if (initials.isNotBlank())
                                    Text(initials,
                                        style      = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color      = MaterialTheme.colorScheme.onPrimary)
                                else
                                    Icon(Icons.Default.Person, null,
                                        modifier = Modifier.size(54.dp),
                                        tint     = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(name,
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimary)
                    Text(designation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                    ) {
                        Text(role,
                            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Stats card ────────────────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp).offset(y = (-20).dp),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStat(experience.toString(),        "Exp (yrs)", Icons.Outlined.Work,        MaterialTheme.colorScheme.primary)
                    VerticalDivider(Modifier.height(40.dp))
                    ProfileStat(completedProjects.toString(), "Done",      Icons.Outlined.CheckCircle, Color(0xFF4CAF50))
                    VerticalDivider(Modifier.height(40.dp))
                    ProfileStat(activeProjects.toString(),    "Active",    Icons.Outlined.Pending,     Color(0xFF2196F3))
                    VerticalDivider(Modifier.height(40.dp))
                    ProfileStat(pendingTasks.toString(),      "Tasks",     Icons.Outlined.Assignment,  Color(0xFFFF9800))
                }
            }

            // ── Info sections ─────────────────────────────────────────────────
            Column(
                modifier            = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoCard("Work Info", Icons.Outlined.Work) {
                    InfoRow(Icons.Outlined.Business,    "Company",     companyName)
                    InfoRow(Icons.Outlined.Groups,      "Department",  department)
                    InfoRow(Icons.Outlined.Badge,       "Designation", designation)
                    InfoRow(Icons.Outlined.WorkOutline, "Role",        role)
                }

                InfoCard("Contact Info", Icons.Outlined.ContactPage) {
                    InfoRow(Icons.Outlined.Email, "Email", email)
                    InfoRow(Icons.Outlined.Phone, "Phone", phoneNumber.ifBlank { "—" })
                }

                if (totalComplaints > 0) {
                    val rate = resolvedComplaints.toFloat() / totalComplaints
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ReportProblem, null,
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Complaint Resolution",
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                Text("${(rate * 100).toInt()}%",
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color(0xFF4CAF50))
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress   = { rate },
                                modifier   = Modifier.fillMaxWidth().height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color      = Color(0xFF4CAF50),
                                trackColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("$resolvedComplaints of $totalComplaints resolved",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                PermissionsCard(
                    granted              = permissions,
                    canManagePermissions = canManagePermissions,
                    onManageClick        = { showPermissionsDialog = true }
                )

                Spacer(Modifier.height(8.dp))

                // ── Action buttons ────────────────────────────────────────────
                // Edit button: only shown if canEdit = true (Administrator/Manager/HR)
                // Logout button: always shown (user can always log out)
                if (canEdit) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick  = onEditClick,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Edit Profile", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick  = onLogoutClick,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error),
                            border   = androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Logout, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Logout", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    // Non-admin roles: only logout, full width
                    OutlinedButton(
                        onClick  = onLogoutClick,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error),
                        border   = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Logout, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Logout", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        if (showPermissionsDialog) {
            PermissionsEditDialog(
                granted            = permissions,
                readOnly           = !canManagePermissions,
                onDismiss          = { showPermissionsDialog = false },
                onSave             = { updated ->
                    onSavePermissions(updated)
                    showPermissionsDialog = false
                }
            )
        }
    }
}

@Composable
private fun PermissionsCard(
    granted             : List<String>,
    canManagePermissions: Boolean,
    onManageClick       : () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Permissions",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${granted.size}/${Permissions.ALL_PERMISSIONS.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick  = onManageClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (canManagePermissions) Icons.Default.Edit else Icons.Default.Visibility,
                    null, Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (canManagePermissions) "Manage Permissions" else "View All Permissions")
            }
        }
    }
}

@Composable
private fun PermissionsEditDialog(
    granted  : List<String>,
    readOnly : Boolean,
    onDismiss: () -> Unit,
    onSave   : (List<String>) -> Unit
) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(granted) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (readOnly) "Permissions" else "Manage Permissions") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Permissions.ALL_PERMISSIONS.forEach { perm ->
                    val isGranted = selected.contains(perm)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked  = isGranted,
                            onCheckedChange = { checked ->
                                if (readOnly) return@Checkbox
                                if (checked) selected.add(perm) else selected.remove(perm)
                            },
                            enabled  = !readOnly
                        )
                        Text(
                            perm,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isGranted) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!readOnly) {
                TextButton(onClick = { onSave(selected.toList()) }) { Text("Save") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = if (!readOnly) {
            { TextButton(onClick = onDismiss) { Text("Cancel") } }
        } else null
    )
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun ProfileStat(value: String, label: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun InfoCard(
    title  : String,
    icon   : ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.padding(bottom = 10.dp)) {
                Icon(icon, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
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
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    if (value.isBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null,
            tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
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
}