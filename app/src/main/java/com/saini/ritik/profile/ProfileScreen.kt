package com.saini.ritik.profile

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
import com.saini.ritik.data.model.Permissions

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
//    permissions         : List<String>     = emptyList(),
//    canManagePermissions: Boolean          = false,
    // Permissions the EDITOR holds — used to scope the dialog checklist.
    // Only permissions within this list can be granted/revoked in the dialog.
    // Pass emptyList() for read-only viewers; pass ALL_PERMISSIONS for sysadmin.
    editorPermissions   : List<String>     = emptyList(),
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
//                IconButton(
//                    onClick  = onBackClick,
//                    modifier = Modifier.padding(top = 8.dp, start = 4.dp).align(Alignment.TopStart)
//                ) {
//                    Icon(Icons.Default.ArrowBack, "Back",
//                        tint = MaterialTheme.colorScheme.onPrimary)
//                }

                Column(
                    modifier            = Modifier.align(Alignment.Center).padding(top = 30.dp),
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
                        color      = MaterialTheme.colorScheme.inverseSurface)
                    Text(designation,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.85f))
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.2f)
                    ) {
                        Text(role,
                            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            style      = MaterialTheme.typography.bodyMedium,
                            color      = MaterialTheme.colorScheme.inverseOnSurface,
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

                // Permissions card — visible to everyone (read-only for most,
                // editable for admins/sysadmin via canManagePermissions).
                // The dialog only shows permissions the EDITOR holds, so a regular
                // admin can't grant permissions they don't have themselves.
//                PermissionsCard(
//                    granted              = permissions,
//                    canManagePermissions = canManagePermissions,
//                    onManageClick        = { showPermissionsDialog = true }
//                )

                Spacer(Modifier.height(8.dp))

                // ── Action buttons ────────────────────────────────────────────
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
                            Text("Explore and Edit Profile", fontWeight = FontWeight.SemiBold)
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

        // ── Permissions dialog ────────────────────────────────────────────────
        // Shows only when the user taps "Manage Permissions" on the card above.
        // The checklist is scoped to editorPermissions — a regular admin cannot
        // tick permissions they don't own themselves.
//        if (showPermissionsDialog) {
//            PermissionsEditDialog(
//                granted           = permissions,
//                readOnly          = !canManagePermissions,
//                editorPermissions = editorPermissions,
//                onDismiss         = { showPermissionsDialog = false },
//                onSave            = { updated ->
//                    onSavePermissions(updated)
//                    showPermissionsDialog = false
//                }
//            )
//        }
    }
}

// ── Permissions card (summary) ────────────────────────────────────────────────

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

// ── Permissions edit/view dialog ──────────────────────────────────────────────

//@Composable
//private fun PermissionsEditDialog(
//    granted          : List<String>,
//    readOnly         : Boolean,
//    // Permissions the editor holds. When not readOnly, only these can be toggled.
//    // Ignored when readOnly = true (viewer sees all, changes nothing).
//    editorPermissions: List<String> = emptyList(),
//    onDismiss        : () -> Unit,
//    onSave           : (List<String>) -> Unit
//) {
//    val selected = remember { mutableStateListOf<String>().apply { addAll(granted) } }
//
//    // Decide which permissions to show in the list:
//    // - Read-only view: show ALL permissions so the viewer can see the full picture
//    // - Edit mode: show only permissions the editor holds (can't grant what you don't have)
//    val displayList = if (readOnly) Permissions.ALL_PERMISSIONS
//    else editorPermissions.filter { it in Permissions.ALL_PERMISSIONS }
//
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        title = { Text(if (readOnly) "Permissions (view only)" else "Manage Permissions") },
//        text = {
//            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
//                if (!readOnly) {
//                    Text(
//                        "You can only grant permissions you hold yourself.",
//                        style  = MaterialTheme.typography.bodySmall,
//                        color  = MaterialTheme.colorScheme.onSurfaceVariant,
//                        modifier = Modifier.padding(bottom = 8.dp)
//                    )
//                }
//                displayList.forEach { perm ->
//                    val isGranted = selected.contains(perm)
//                    Row(
//                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Checkbox(
//                            checked  = isGranted,
//                            onCheckedChange = { checked ->
//                                if (readOnly) return@Checkbox
//                                if (checked) selected.add(perm) else selected.remove(perm)
//                            },
//                            enabled  = !readOnly
//                        )
//                        Text(
//                            perm.replace("_", " ").replaceFirstChar { it.uppercase() },
//                            style = MaterialTheme.typography.bodyMedium,
//                            color = if (isGranted) MaterialTheme.colorScheme.onSurface
//                            else MaterialTheme.colorScheme.onSurfaceVariant
//                        )
//                    }
//                }
//            }
//        },
//        confirmButton = {
//            if (!readOnly) {
//                TextButton(onClick = { onSave(selected.toList()) }) { Text("Save") }
//            } else {
//                TextButton(onClick = onDismiss) { Text("Close") }
//            }
//        },
//        dismissButton = if (!readOnly) {
//            { TextButton(onClick = onDismiss) { Text("Cancel") } }
//        } else null
//    )
//}

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