package com.example.ritik_2.complaint.viewcomplaint.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.complaint.viewcomplaint.data.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplaintCard(
    complaint: ComplaintWithDetails,
    currentUser: UserData?,
    userPermissions: UserPermissions?,
    availableEmployees: List<UserData>,
    onEdit: (ComplaintUpdates) -> Unit,
    onDelete: () -> Unit,
    onAssign: (String, String) -> Unit,
    onClose: (String) -> Unit,
    onReopen: () -> Unit,
    onChangeStatus: (String, String) -> Unit,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onViewDetails() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (complaint.isOverdue()) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title and ID
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = complaint.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID: ${complaint.id.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Menu Button
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        ComplaintMenuItems(
                            complaint = complaint,
                            currentUser = currentUser,
                            userPermissions = userPermissions,
                            availableEmployees = availableEmployees,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onAssign = onAssign,
                            onClose = onClose,
                            onReopen = onReopen,
                            onChangeStatus = onChangeStatus,
                            onViewDetails = onViewDetails,
                            onDismissMenu = { showMenu = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status and Priority Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Chip
                StatusChip(
                    status = complaint.status,
                    modifier = Modifier
                )

                // Urgency Chip
                UrgencyChip(
                    urgency = complaint.urgency,
                    modifier = Modifier
                )

                // Global Badge
                if (complaint.isGlobal) {
                    GlobalBadge()
                }

                // Overdue Badge
                if (complaint.isOverdue()) {
                    OverdueBadge()
                }

                Spacer(modifier = Modifier.weight(1f))

                // Attachment Icon
                if (complaint.hasAttachment) {
                    Icon(
                        Icons.Default.Attachment,
                        contentDescription = "Has attachment",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            if (complaint.description.isNotBlank()) {
                Text(
                    text = complaint.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Assignment and Department Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Department
                InfoItem(
                    icon = Icons.Default.Business,
                    label = "Department",
                    value = complaint.getDepartmentText(),
                    modifier = Modifier.weight(1f)
                )

                // Assigned To
                InfoItem(
                    icon = Icons.Default.Person,
                    label = "Assigned to",
                    value = complaint.getAssigneeText(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Created info
                Column {
                    Text(
                        text = "Created by ${complaint.createdBy.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = complaint.getTimeSinceCreation(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Quick Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Quick assign button for managers
                    if (complaint.canBeAssigned(userPermissions) && complaint.assignedToUser == null) {
                        IconButton(
                            onClick = { /* Quick assign to first available employee */ },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = "Quick assign",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Quick close button for resolvers
                    if (complaint.canBeClosed(userPermissions)) {
                        IconButton(
                            onClick = { onClose("Quick resolution") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Quick close",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComplaintMenuItems(
    complaint: ComplaintWithDetails,
    currentUser: UserData?,
    userPermissions: UserPermissions?,
    availableEmployees: List<UserData>,
    onEdit: (ComplaintUpdates) -> Unit,
    onDelete: () -> Unit,
    onAssign: (String, String) -> Unit,
    onClose: (String) -> Unit,
    onReopen: () -> Unit,
    onChangeStatus: (String, String) -> Unit,
    onViewDetails: () -> Unit,
    onDismissMenu: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }

    // View Details
    DropdownMenuItem(
        text = { Text("View Details") },
        onClick = {
            onViewDetails()
            onDismissMenu()
        },
        leadingIcon = {
            Icon(Icons.Default.Visibility, contentDescription = null)
        }
    )

    // Edit
    if (complaint.canBeEdited(userPermissions, currentUser?.userId ?: "")) {
        DropdownMenuItem(
            text = { Text("Edit") },
            onClick = {
                showEditDialog = true
                onDismissMenu()
            },
            leadingIcon = {
                Icon(Icons.Default.Edit, contentDescription = null)
            }
        )
    }

    // Assign
    if (complaint.canBeAssigned(userPermissions)) {
        DropdownMenuItem(
            text = { Text("Assign") },
            onClick = {
                showAssignDialog = true
                onDismissMenu()
            },
            leadingIcon = {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
            }
        )
    }

    // Close
    if (complaint.canBeClosed(userPermissions)) {
        DropdownMenuItem(
            text = { Text("Close") },
            onClick = {
                showCloseDialog = true
                onDismissMenu()
            },
            leadingIcon = {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
            }
        )
    }

    // Reopen
    if (complaint.canBeReopened(userPermissions)) {
        DropdownMenuItem(
            text = { Text("Reopen") },
            onClick = {
                onReopen()
                onDismissMenu()
            },
            leadingIcon = {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
        )
    }

    // Change Status
    if (userPermissions?.canEditComplaints == true) {
        DropdownMenuItem(
            text = { Text("Change Status") },
            onClick = {
                showStatusDialog = true
                onDismissMenu()
            },
            leadingIcon = {
                Icon(Icons.Default.SwapHoriz, contentDescription = null)
            }
        )
    }

    // Delete
    if (complaint.canBeDeleted(userPermissions, currentUser?.userId ?: "")) {
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = {
                onDelete()
                onDismissMenu()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        )
    }

    // Handle dialogs
    if (showEditDialog) {
        EditComplaintDialog(
            complaint = complaint,
            onConfirm = { updates ->
                onEdit(updates)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showAssignDialog) {
        AssignComplaintDialog(
            availableEmployees = availableEmployees,
            onConfirm = { assigneeId, assigneeName ->
                onAssign(assigneeId, assigneeName)
                showAssignDialog = false
            },
            onDismiss = { showAssignDialog = false }
        )
    }

    if (showCloseDialog) {
        CloseComplaintDialog(
            onConfirm = { resolution ->
                onClose(resolution)
                showCloseDialog = false
            },
            onDismiss = { showCloseDialog = false }
        )
    }

    if (showStatusDialog) {
        ChangeStatusDialog(
            currentStatus = complaint.status,
            onConfirm = { newStatus, reason ->
                onChangeStatus(newStatus, reason)
                showStatusDialog = false
            },
            onDismiss = { showStatusDialog = false }
        )
    }
}

@Composable
private fun StatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (status.lowercase()) {
        "open" -> Color(0xFF2196F3)
        "in progress", "assigned" -> Color(0xFFFF9800)
        "closed", "resolved" -> Color(0xFF4CAF50)
        "cancelled" -> Color(0xFF9E9E9E)
        "reopened" -> Color(0xFFE91E63)
        else -> Color(0xFF9E9E9E)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor.copy(alpha = 0.1f)
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = backgroundColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun UrgencyChip(
    urgency: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (urgency.lowercase()) {
        "critical" -> Color(0xFFFF0000)
        "high" -> Color(0xFFFF6600)
        "medium" -> Color(0xFFFFB300)
        "low" -> Color(0xFF4CAF50)
        else -> Color(0xFF9E9E9E)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor.copy(alpha = 0.1f)
    ) {
        Text(
            text = urgency,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = backgroundColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GlobalBadge() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                Icons.Default.Public,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Global",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun OverdueBadge() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Overdue",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun InfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}