package com.example.ritik_2.complaint.viewcomplaint.ui.modern

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ritik_2.complaint.viewcomplaint.data.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernActionSheet(
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
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Bottom sheet-style dialog
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Complaint info
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = complaint.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ModernStatusChip(
                                text = complaint.status,
                                type = ChipType.STATUS,
                                animated = false
                            )
                            ModernStatusChip(
                                text = complaint.urgency,
                                type = ChipType.URGENCY,
                                animated = false
                            )
                        }
                    }
                }

                // Action buttons
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    // View Details
                    item {
                        ModernActionItem(
                            icon = Icons.Default.Visibility,
                            title = "View Details",
                            subtitle = "See complete information",
                            color = MaterialTheme.colorScheme.primary,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDismiss()
                            }
                        )
                    }

                    // Edit
                    if (complaint.canBeEdited(userPermissions, currentUser?.userId ?: "")) {
                        item {
                            ModernActionItem(
                                icon = Icons.Default.Edit,
                                title = "Edit Complaint",
                                subtitle = "Modify details",
                                color = MaterialTheme.colorScheme.tertiary,
                                onClick = {
                                    showEditDialog = true
                                    onDismiss()
                                }
                            )
                        }
                    }

                    // Assign
                    if (complaint.canBeAssigned(userPermissions)) {
                        item {
                            ModernActionItem(
                                icon = Icons.Default.PersonAdd,
                                title = "Assign",
                                subtitle = "Assign to team member",
                                color = Color(0xFF2196F3),
                                onClick = {
                                    showAssignDialog = true
                                    onDismiss()
                                }
                            )
                        }
                    }

                    // Close
                    if (complaint.canBeClosed(userPermissions)) {
                        item {
                            ModernActionItem(
                                icon = Icons.Default.CheckCircle,
                                title = "Close Complaint",
                                subtitle = "Mark as resolved",
                                color = Color(0xFF4CAF50),
                                onClick = {
                                    showCloseDialog = true
                                    onDismiss()
                                }
                            )
                        }
                    }

                    // Reopen
                    if (complaint.canBeReopened(userPermissions)) {
                        item {
                            ModernActionItem(
                                icon = Icons.Default.Refresh,
                                title = "Reopen",
                                subtitle = "Reactivate complaint",
                                color = Color(0xFFFF9800),
                                onClick = {
                                    onReopen()
                                    onDismiss()
                                }
                            )
                        }
                    }

                    // Change Status
                    if (userPermissions?.canEditComplaints == true) {
                        item {
                            ModernActionItem(
                                icon = Icons.Default.SwapHoriz,
                                title = "Change Status",
                                subtitle = "Update current status",
                                color = MaterialTheme.colorScheme.secondary,
                                onClick = {
                                    showStatusDialog = true
                                    onDismiss()
                                }
                            )
                        }
                    }

                    // Delete
                    if (complaint.canBeDeleted(userPermissions, currentUser?.userId ?: "")) {
                        item {
                            ModernActionItem(
                                icon = Icons.Default.Delete,
                                title = "Delete Complaint",
                                subtitle = "Remove permanently",
                                color = MaterialTheme.colorScheme.error,
                                onClick = {
                                    showDeleteConfirmation = true
                                    onDismiss()
                                },
                                isDestructive = true
                            )
                        }
                    }
                }
            }
        }
    }

    // Sub-dialogs with modern styling
    if (showEditDialog) {
        ModernEditComplaintDialog(
            complaint = complaint,
            onConfirm = { updates ->
                onEdit(updates)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showAssignDialog) {
        ModernAssignComplaintDialog(
            availableEmployees = availableEmployees,
            onConfirm = { assigneeId, assigneeName ->
                onAssign(assigneeId, assigneeName)
                showAssignDialog = false
            },
            onDismiss = { showAssignDialog = false }
        )
    }

    if (showCloseDialog) {
        ModernCloseComplaintDialog(
            onConfirm = { resolution ->
                onClose(resolution)
                showCloseDialog = false
            },
            onDismiss = { showCloseDialog = false }
        )
    }

    if (showStatusDialog) {
        ModernChangeStatusDialog(
            currentStatus = complaint.status,
            onConfirm = { newStatus, reason ->
                onChangeStatus(newStatus, reason)
                showStatusDialog = false
            },
            onDismiss = { showStatusDialog = false }
        )
    }

    if (showDeleteConfirmation) {
        ModernDeleteConfirmationDialog(
            complaintTitle = complaint.title,
            onConfirm = {
                onDelete()
                showDeleteConfirmation = false
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isDestructive)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        else color.copy(alpha = 0.1f),
        border = BorderStroke(
            1.dp,
            if (isDestructive)
                MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            else color.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isDestructive)
                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                else color.copy(alpha = 0.2f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDestructive) MaterialTheme.colorScheme.error else color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isDestructive)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernEditComplaintDialog(
    complaint: ComplaintWithDetails,
    onConfirm: (ComplaintUpdates) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(complaint.title) }
    var description by remember { mutableStateOf(complaint.description) }
    var category by remember { mutableStateOf(complaint.category) }
    var urgency by remember { mutableStateOf(complaint.urgency) }
    var isLoading by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header with progress
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Edit Complaint",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Form fields with modern styling
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ModernTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = "Title",
                            icon = Icons.Default.Title,
                            enabled = !isLoading
                        )
                    }

                    item {
                        ModernTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = "Description",
                            icon = Icons.Default.Description,
                            maxLines = 4,
                            enabled = !isLoading
                        )
                    }

                    item {
                        ModernDropdownField(
                            value = category,
                            onValueChange = { category = it },
                            label = "Category",
                            icon = Icons.Default.Category,
                            options = listOf("Technical", "HR", "Administrative", "IT Support", "Finance", "General"),
                            enabled = !isLoading
                        )
                    }

                    item {
                        ModernDropdownField(
                            value = urgency,
                            onValueChange = { urgency = it },
                            label = "Urgency",
                            icon = Icons.Default.PriorityHigh,
                            options = listOf("Low", "Medium", "High", "Critical"),
                            enabled = !isLoading
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            isLoading = true
                            onConfirm(
                                ComplaintUpdates(
                                    title = title,
                                    description = description,
                                    category = category,
                                    urgency = urgency
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && title.isNotBlank() && description.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save Changes")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    maxLines: Int = 1,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        maxLines = maxLines,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    options: List<String>,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && enabled }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

// Additional modern dialogs would follow similar patterns...
@Composable
fun ModernAssignComplaintDialog(
    availableEmployees: List<UserData>,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    // Similar modern styling for assign dialog
    // Implementation follows the same pattern as ModernEditComplaintDialog
}

@Composable
fun ModernCloseComplaintDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Modern close dialog implementation
}

@Composable
fun ModernChangeStatusDialog(
    currentStatus: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    // Modern status change dialog implementation
}

@Composable
fun ModernDeleteConfirmationDialog(
    complaintTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Modern delete confirmation dialog implementation
}