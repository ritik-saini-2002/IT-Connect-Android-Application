package com.saini.ritik.complaint.managecomplaint

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageComplaintScreen(
    uiState           : ManageComplaintUiState,
    onFilterChange    : (String) -> Unit,
    onSearchChange    : (String) -> Unit,
    onStatusChange    : (String, String) -> Unit,
    onAssignUser      : (String, String, String) -> Unit,
    onDeleteComplaint : (String) -> Unit,
    onAddComment      : (String, String) -> Unit,
    onUpdateResolution: (String, String) -> Unit,
    onEditComplaint   : (String, String, String, String, String) -> Unit,
    onRefresh         : () -> Unit,
    onBack            : () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var selectedComplaint by remember { mutableStateOf<ComplaintItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Tickets", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search tickets…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Filter chips
            val filters = listOf("All", "Open", "In Progress", "Resolved", "Closed")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { f ->
                    FilterChip(
                        selected = uiState.filter == f,
                        onClick = { onFilterChange(f) },
                        label = { Text(f) },
                        leadingIcon = if (uiState.filter == f) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Stats row
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val all = uiState.complaints
                StatChip("Open", all.count { it.status == "Open" }, Color(0xFFFF9800), Modifier.weight(1f))
                StatChip("Active", all.count { it.status == "In Progress" }, Color(0xFF2196F3), Modifier.weight(1f))
                StatChip("Done", all.count { it.status == "Resolved" || it.status == "Closed" }, Color(0xFF4CAF50), Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            // Complaint list
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredComplaints.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Inbox, null, Modifier.size(64.dp), tint = cs.onSurfaceVariant.copy(0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("No tickets found", color = cs.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredComplaints, key = { it.id }) { complaint ->
                        ComplaintCard(
                            complaint = complaint,
                            canDelete = uiState.canDelete,
                            canAssign = uiState.canAssign,
                            onClick = { selectedComplaint = complaint }
                        )
                    }
                }
            }
        }

        // Detail dialog
        selectedComplaint?.let { complaint ->
            ComplaintDetailDialog(
                complaint = complaint,
                uiState = uiState,
                onDismiss = { selectedComplaint = null },
                onStatusChange = { status -> onStatusChange(complaint.id, status); selectedComplaint = null },
                onAssign = { userId, name -> onAssignUser(complaint.id, userId, name); selectedComplaint = null },
                onDelete = { onDeleteComplaint(complaint.id); selectedComplaint = null },
                onComment = { c -> onAddComment(complaint.id, c) },
                onResolve = { r -> onUpdateResolution(complaint.id, r); selectedComplaint = null },
                onEdit = { t, d, p, c -> onEditComplaint(complaint.id, t, d, p, c); selectedComplaint = null }
            )
        }
    }
}

@Composable
private fun StatChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(0.1f), modifier = modifier) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(0.8f))
        }
    }
}

@Composable
private fun ComplaintCard(complaint: ComplaintItem, canDelete: Boolean, canAssign: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val statusColor = when (complaint.status) {
        "Open" -> Color(0xFFFF9800); "In Progress" -> Color(0xFF2196F3)
        "Resolved" -> Color(0xFF4CAF50); "Closed" -> Color(0xFF9E9E9E)
        else -> cs.primary
    }
    val priorityColor = when (complaint.priority) {
        "Low" -> Color(0xFF4CAF50); "Medium" -> Color(0xFFFF9800)
        "High" -> Color(0xFFFF5722); "Critical" -> Color(0xFFD32F2F)
        else -> cs.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(14.dp)).clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Priority indicator
                Box(Modifier.size(8.dp).clip(CircleShape).background(priorityColor))
                Spacer(Modifier.width(8.dp))
                Text(complaint.ticketId, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(0.15f)) {
                    Text(complaint.status, Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = statusColor)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(complaint.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(complaint.description, style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(complaint.raisedByName.ifBlank { "Unknown" }, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Outlined.Category, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(complaint.category, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(20.dp), color = priorityColor.copy(0.1f)) {
                    Text(complaint.priority, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = priorityColor)
                }
            }

            if (complaint.assignedToName.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AssignmentInd, null, Modifier.size(14.dp), tint = cs.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Assigned: ${complaint.assignedToName}", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                }
            }

            if (complaint.attachments.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AttachFile, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("${complaint.attachments.size} attachment(s)", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComplaintDetailDialog(
    complaint: ComplaintItem,
    uiState: ManageComplaintUiState,
    onDismiss: () -> Unit,
    onStatusChange: (String) -> Unit,
    onAssign: (String, String) -> Unit,
    onDelete: () -> Unit,
    onComment: (String) -> Unit,
    onResolve: (String) -> Unit,
    onEdit: (String, String, String, String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var commentText by remember { mutableStateOf("") }
    var resolutionText by remember { mutableStateOf(complaint.resolution) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Box(
                    Modifier.fillMaxWidth().background(
                        Brush.horizontalGradient(listOf(cs.primary, cs.tertiary))
                    ).padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(complaint.ticketId, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.8f))
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }
                        }
                        Text(complaint.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status & Priority
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val statusColor = when (complaint.status) {
                            "Open" -> Color(0xFFFF9800); "In Progress" -> Color(0xFF2196F3)
                            "Resolved" -> Color(0xFF4CAF50); else -> Color(0xFF9E9E9E)
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(0.15f)) {
                            Text(complaint.status, Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                fontWeight = FontWeight.SemiBold, color = statusColor, style = MaterialTheme.typography.labelMedium)
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = cs.secondaryContainer) {
                            Text(complaint.priority, Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer)
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = cs.tertiaryContainer) {
                            Text(complaint.category, Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium, color = cs.onTertiaryContainer)
                        }
                    }

                    // Description
                    Text("Description", fontWeight = FontWeight.SemiBold)
                    Text(complaint.description, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)

                    // Raised by
                    HorizontalDivider()
                    Text("Raised By", fontWeight = FontWeight.SemiBold)
                    Text("${complaint.raisedByName} (${complaint.raisedByRole})", style = MaterialTheme.typography.bodySmall)
                    Text(complaint.raisedByEmail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)

                    // Assigned
                    if (complaint.assignedToName.isNotBlank()) {
                        Text("Assigned To", fontWeight = FontWeight.SemiBold)
                        Text(complaint.assignedToName, style = MaterialTheme.typography.bodySmall)
                    }

                    // Attachments
                    if (complaint.attachments.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Attachments (${complaint.attachments.size})", fontWeight = FontWeight.SemiBold)
                        complaint.attachments.forEach { url ->
                            Surface(
                                shape = RoundedCornerShape(8.dp), color = cs.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    try { uriHandler.openUri(url) } catch (_: Exception) {}
                                }
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (url.endsWith(".pdf")) Icons.Outlined.PictureAsPdf else Icons.Outlined.Image,
                                        null, Modifier.size(20.dp), tint = cs.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(url.substringAfterLast("/").take(30), style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Resolution
                    if (uiState.canResolve && complaint.status != "Closed") {
                        HorizontalDivider()
                        Text("Resolution", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = resolutionText, onValueChange = { resolutionText = it },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                            placeholder = { Text("Enter resolution…") }, minLines = 2
                        )
                        Button(
                            onClick = { onResolve(resolutionText) },
                            shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(),
                            enabled = resolutionText.isNotBlank()
                        ) { Text("Mark Resolved") }
                    } else if (complaint.resolution.isNotBlank()) {
                        HorizontalDivider()
                        Text("Resolution", fontWeight = FontWeight.SemiBold)
                        Text(complaint.resolution, style = MaterialTheme.typography.bodySmall)
                    }

                    // Comments
                    HorizontalDivider()
                    Text("Comments (${complaint.comments.size})", fontWeight = FontWeight.SemiBold)
                    complaint.comments.forEach { c ->
                        Surface(shape = RoundedCornerShape(8.dp), color = cs.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Row { Text(c.userName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.weight(1f))
                                    Text(c.timestamp.take(10), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
                                Spacer(Modifier.height(4.dp))
                                Text(c.comment, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = commentText, onValueChange = { commentText = it },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            placeholder = { Text("Add comment…") }, singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { if (commentText.isNotBlank()) { onComment(commentText); commentText = "" } }) {
                            Icon(Icons.Default.Send, null, tint = cs.primary)
                        }
                    }
                }

                // Action buttons
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.canAssign) {
                        OutlinedButton(onClick = { showAssignDialog = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.PersonAdd, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Assign")
                        }
                    }
                    if (uiState.canEdit) {
                        OutlinedButton(onClick = { showEditDialog = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Edit")
                        }
                    }
                    if (uiState.canDelete) {
                        OutlinedButton(onClick = { showDeleteConfirm = true }, shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp))
                        }
                    }
                }

                // Status change row
                if (uiState.canResolve) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Open", "In Progress", "Resolved", "Closed").forEach { s ->
                            if (s != complaint.status) {
                                FilledTonalButton(onClick = { onStatusChange(s) }, shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                                    Text(s, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Assign dialog
    if (showAssignDialog) {
        AssignUserDialog(
            users = uiState.assignableUsers,
            department = complaint.department,
            onAssign = { u -> onAssign(u.id, u.name); showAssignDialog = false },
            onDismiss = { showAssignDialog = false }
        )
    }

    // Edit dialog
    if (showEditDialog) {
        EditComplaintDialog(
            complaint = complaint,
            onSave = { t, d, p, c -> onEdit(t, d, p, c); showEditDialog = false },
            onDismiss = { showEditDialog = false }
        )
    }

    // Delete confirm
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = { TextButton(onClick = { onDelete(); showDeleteConfirm = false }) { Text("Delete", color = cs.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
            title = { Text("Delete Ticket?") },
            text = { Text("This action cannot be undone. The complaint '${complaint.ticketId}' will be permanently deleted.") },
            icon = { Icon(Icons.Default.Warning, null, tint = cs.error) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignUserDialog(users: List<AssignableUser>, department: String, onAssign: (AssignableUser) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var filterDept by remember { mutableStateOf(true) }
    val filtered = users.filter { u ->
        val matchSearch = search.isBlank() || u.name.contains(search, true) || u.email.contains(search, true)
        val matchDept = !filterDept || u.department.equals(department, true)
        matchSearch && matchDept
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
            Column(Modifier.padding(16.dp)) {
                Text("Assign User", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search…") }, shape = RoundedCornerShape(10.dp), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = filterDept, onCheckedChange = { filterDept = it })
                    Text("Same department only", style = MaterialTheme.typography.labelMedium)
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered) { user ->
                        ListItem(
                            headlineContent = { Text(user.name) },
                            supportingContent = { Text("${user.role} • ${user.department}", style = MaterialTheme.typography.labelSmall) },
                            leadingContent = { Icon(Icons.Outlined.Person, null) },
                            modifier = Modifier.clickable { onAssign(user) }
                        )
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun EditComplaintDialog(complaint: ComplaintItem, onSave: (String, String, String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(complaint.title) }
    var desc by remember { mutableStateOf(complaint.description) }
    var priority by remember { mutableStateOf(complaint.priority) }
    var category by remember { mutableStateOf(complaint.category) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Edit Ticket", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Low", "Medium", "High", "Critical").forEach { p ->
                        FilterChip(selected = p == priority, onClick = { priority = p }, label = { Text(p) }, modifier = Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(title, desc, priority, category) }, shape = RoundedCornerShape(10.dp)) { Text("Save") }
                }
            }
        }
    }
}
