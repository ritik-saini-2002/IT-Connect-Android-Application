package com.example.ritik_2.complaint.viewcomplaint.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ritik_2.complaint.viewcomplaint.data.models.*
import com.example.ritik_2.complaint.viewcomplaint.utils.PermissionChecker
import com.example.ritik_2.complaint.viewcomplaint.ui.profile.ProfilePicture
import com.example.ritik_2.complaint.viewcomplaint.ui.profile.UserProfileDialog


@Composable
fun FilterDialog(
    currentFilter: String?,
    onFilterSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Complaints") },
        text = {
            Column {
                val filterOptions = listOf(
                    "All" to null,
                    "Technical" to "Technical",
                    "HR" to "HR",
                    "Administrative" to "Administrative",
                    "IT Support" to "IT Support",
                    "Finance" to "Finance",
                    "General" to "General"
                )

                filterOptions.forEach { (displayName, filterValue) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = currentFilter == filterValue,
                                onClick = { onFilterSelected(filterValue) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentFilter == filterValue,
                            onClick = { onFilterSelected(filterValue) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun SortDialog(
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort Complaints") },
        text = {
            Column {
                val sortOptions = listOf(
                    "Newest First" to SortOption.DATE_DESC,
                    "Oldest First" to SortOption.DATE_ASC,
                    "By Urgency" to SortOption.URGENCY,
                    "By Status" to SortOption.STATUS
                )

                sortOptions.forEach { (displayName, sortOption) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = currentSort == sortOption,
                                onClick = { onSortSelected(sortOption) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSort == sortOption,
                            onClick = { onSortSelected(sortOption) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun ViewModeDialog(
    currentViewMode: ViewMode,
    userRole: String,
    onViewModeSelected: (ViewMode) -> Unit,
    onDismiss: () -> Unit
) {
    val permissionChecker = remember { PermissionChecker() }
    val availableViewModes = remember(userRole) {
        permissionChecker.getAvailableViewModes(userRole)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select View Mode") },
        text = {
            Column {
                availableViewModes.forEach { viewMode ->
                    val displayName = permissionChecker.getViewModeDisplayName(viewMode)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = currentViewMode == viewMode,
                                onClick = { onViewModeSelected(viewMode) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentViewMode == viewMode,
                            onClick = { onViewModeSelected(viewMode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(displayName, fontWeight = FontWeight.Medium)
                            Text(
                                text = getViewModeDescription(viewMode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplaintDetailsDialog(
    complaint: ComplaintWithDetails,
    currentUser: UserData?,
    userPermissions: UserPermissions?,
    availableEmployees: List<UserData>,
    userProfiles: Map<String, UserProfile>,
    onViewUserProfile: (String) -> Unit,
    onDismiss: () -> Unit,
    onEdit: (ComplaintUpdates) -> Unit,
    onDelete: () -> Unit,
    onAssign: (String, String) -> Unit,
    onClose: (String) -> Unit,
    onReopen: () -> Unit,
    onChangeStatus: (String, String) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showUserProfileDialog by remember { mutableStateOf(false) }
    val creatorProfile = userProfiles[complaint.createdBy.userId]

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    // Show profile picture and name
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfilePicture(
                            profilePictureUrl = creatorProfile?.profilePictureUrl,
                            userName = complaint.createdBy.name,
                            size = 48.dp,
                            onClick = {
                                onViewUserProfile(complaint.createdBy.userId)
                                showUserProfileDialog = true
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = complaint.createdBy.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                item {
                    Divider()
                }

                // Complaint Information
                item {
                    ComplaintInfoSection(complaint = complaint)
                }

                item {
                    Divider()
                }

                // Action Buttons
                item {
                    ActionButtonsSection(
                        complaint = complaint,
                        currentUser = currentUser,
                        userPermissions = userPermissions,
                        onEdit = { showEditDialog = true },
                        onDelete = { showDeleteConfirmation = true },
                        onAssign = { showAssignDialog = true },
                        onClose = { showCloseDialog = true },
                        onReopen = onReopen,
                        onChangeStatus = { showStatusDialog = true }
                    )
                }
            }
        }
    }

    // Sub-dialogs
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

    if (showDeleteConfirmation) {
        DeleteConfirmationDialog(
            complaintTitle = complaint.title,
            onConfirm = {
                onDelete()
                showDeleteConfirmation = false
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}

@Composable
private fun ComplaintInfoSection(complaint: ComplaintWithDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow("Title", complaint.title)
        InfoRow("Description", complaint.description)
        InfoRow("Category", complaint.category)
        InfoRow("Urgency", complaint.urgency)
        InfoRow("Status", complaint.status)
        InfoRow("Created", complaint.getFormattedDateTime())
        InfoRow("Updated", complaint.getFormattedUpdatedDate())
        InfoRow("Created By", complaint.getCreatorText())

        if (complaint.assignedToUser != null) {
            InfoRow("Assigned To", complaint.getAssigneeText())
        }

        if (complaint.assignedToDepartment != null) {
            InfoRow("Department", complaint.getDepartmentText())
        }

        if (!complaint.resolution.isNullOrBlank()) {
            InfoRow("Resolution", complaint.resolution)
        }

        if (complaint.resolvedAt != null) {
            InfoRow("Resolved At", complaint.getFormattedResolvedDate() ?: "")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionButtonsSection(
    complaint: ComplaintWithDetails,
    currentUser: UserData?,
    userPermissions: UserPermissions?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAssign: () -> Unit,
    onClose: () -> Unit,
    onReopen: () -> Unit,
    onChangeStatus: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (complaint.canBeEdited(userPermissions, currentUser?.userId ?: "")) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
            }

            if (complaint.canBeAssigned(userPermissions)) {
                Button(
                    onClick = onAssign,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Assign")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (complaint.canBeClosed(userPermissions)) {
                Button(
                    onClick = onClose,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Close")
                }
            }

            if (complaint.canBeReopened(userPermissions)) {
                OutlinedButton(
                    onClick = onReopen,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reopen")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (userPermissions?.canEditComplaints == true) {
                OutlinedButton(
                    onClick = onChangeStatus,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Status")
                }
            }

            if (complaint.canBeDeleted(userPermissions, currentUser?.userId ?: "")) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditComplaintDialog(
    complaint: ComplaintWithDetails,
    onConfirm: (ComplaintUpdates) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(complaint.title) }
    var description by remember { mutableStateOf(complaint.description) }
    var category by remember { mutableStateOf(complaint.category) }
    var urgency by remember { mutableStateOf(complaint.urgency) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Complaint") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Category Dropdown
                var categoryExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        val categories = listOf("Technical", "HR", "Administrative", "IT Support", "Finance", "General")
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Urgency Dropdown
                var urgencyExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = urgencyExpanded,
                    onExpandedChange = { urgencyExpanded = !urgencyExpanded }
                ) {
                    OutlinedTextField(
                        value = urgency,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Urgency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = urgencyExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = urgencyExpanded,
                        onDismissRequest = { urgencyExpanded = false }
                    ) {
                        val urgencies = listOf("Low", "Medium", "High", "Critical")
                        urgencies.forEach { urg ->
                            DropdownMenuItem(
                                text = { Text(urg) },
                                onClick = {
                                    urgency = urg
                                    urgencyExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        ComplaintUpdates(
                            title = title,
                            description = description,
                            category = category,
                            urgency = urgency
                        )
                    )
                },
                enabled = title.isNotBlank() && description.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AssignComplaintDialog(
    availableEmployees: List<UserData>,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEmployee by remember { mutableStateOf<UserData?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredEmployees = remember(availableEmployees, searchQuery) {
        if (searchQuery.isBlank()) {
            availableEmployees
        } else {
            availableEmployees.filter { employee ->
                employee.name.contains(searchQuery, ignoreCase = true) ||
                        employee.email.contains(searchQuery, ignoreCase = true) ||
                        employee.department.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Complaint") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search employees") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredEmployees) { employee ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedEmployee == employee,
                                    onClick = { selectedEmployee = employee }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedEmployee == employee,
                                onClick = { selectedEmployee = employee }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = employee.name,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${employee.role} - ${employee.department}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (filteredEmployees.isEmpty()) {
                    Text(
                        text = "No employees found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedEmployee?.let { employee ->
                        onConfirm(employee.userId, employee.name)
                    }
                },
                enabled = selectedEmployee != null
            ) {
                Text("Assign")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CloseComplaintDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var resolution by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Close Complaint") },
        text = {
            Column {
                Text(
                    text = "Please provide a resolution summary:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = resolution,
                    onValueChange = { resolution = it },
                    label = { Text("Resolution") },
                    placeholder = { Text("Describe how the complaint was resolved...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(resolution) },
                enabled = resolution.isNotBlank()
            ) {
                Text("Close Complaint")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ChangeStatusDialog(
    currentStatus: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStatus by remember { mutableStateOf(currentStatus) }
    var reason by remember { mutableStateOf("") }

    val statusOptions = listOf("Open", "In Progress", "Pending", "On Hold", "Cancelled")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Status") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Current status: $currentStatus")

                Spacer(modifier = Modifier.height(8.dp))

                Text("Select new status:")
                statusOptions.forEach { status ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = status }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(status)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for change") },
                    placeholder = { Text("Optional: Explain why the status is being changed") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedStatus, reason.ifBlank { "Status changed" }) },
                enabled = selectedStatus != currentStatus
            ) {
                Text("Change Status")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    complaintTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Complaint") },
        text = {
            Text("Are you sure you want to delete the complaint \"$complaintTitle\"? This action cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getViewModeDescription(viewMode: ViewMode): String {
    return when (viewMode) {
        ViewMode.PERSONAL -> "View complaints you created"
        ViewMode.ASSIGNED_TO_ME -> "View complaints assigned to you"
        ViewMode.DEPARTMENT -> "View all department complaints"
        ViewMode.ALL_COMPANY -> "View all company complaints"
        ViewMode.GLOBAL -> "View company-wide complaints"
    }
}