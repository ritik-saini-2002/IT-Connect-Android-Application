package com.example.ritik_2.complaint.viewcomplaint.ui.modern

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ritik_2.complaint.viewcomplaint.data.models.*
import com.example.ritik_2.complaint.viewcomplaint.ui.profile.ModernProfileAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernComplaintDetailsDialog(
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
    var showActionSheet by remember { mutableStateOf(false) }
    val creatorProfile = userProfiles[complaint.createdBy.userId]
    val assigneeProfile = complaint.assignedToUser?.let { userProfiles[it.userId] }

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
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with gradient background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Top bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Complaint Details",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = { showActionSheet = true },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                    )
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                                }

                                FilledTonalIconButton(
                                    onClick = onDismiss,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                    )
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        }

                        // Title and ID
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = complaint.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = "ID: ${complaint.id}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Status indicators
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                ModernStatusChip(
                                    text = complaint.status,
                                    type = ChipType.STATUS,
                                    animated = true
                                )
                            }

                            item {
                                ModernStatusChip(
                                    text = complaint.urgency,
                                    type = ChipType.URGENCY,
                                    animated = true
                                )
                            }

                            if (complaint.isGlobal) {
                                item {
                                    ModernStatusChip(
                                        text = "Global",
                                        type = ChipType.GLOBAL,
                                        icon = Icons.Default.Public,
                                        animated = true
                                    )
                                }
                            }

                            if (complaint.isOverdue()) {
                                item {
                                    ModernStatusChip(
                                        text = "Overdue",
                                        type = ChipType.ERROR,
                                        icon = Icons.Default.Warning,
                                        animated = true
                                    )
                                }
                            }

//                            if (complaint.hasAttachment) {
//                                item {
//                                    ModernStatusChip(
//                                        text = "${complaint.attachmentCount} Files",
//                                        type = ChipType.INFO,
//                                        icon = Icons.Default.Attachment,
//                                        animated = true
//                                    )
//                                }
//                            }
                        }
                    }
                }

                // Content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    // Description section
                    item {
                        ModernDetailSection(
                            title = "Description",
                            icon = Icons.Default.Description
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = complaint.description,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }

                    // People involved section
                    item {
                        ModernDetailSection(
                            title = "People Involved",
                            icon = Icons.Default.People
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Creator
                                ModernPersonCard(
                                    title = "Created by",
                                    user = complaint.createdBy,
                                    profile = creatorProfile,
                                    subtitle = complaint.getFormattedDateTime(),
                                    onClick = { onViewUserProfile(complaint.createdBy.userId) }
                                )

                                // Assignee
                                complaint.assignedToUser?.let { assignee ->
//                                    ModernPersonCard(
//                                        title = "Assigned to",
//                                        user = assignee,
//                                        profile = assigneeProfile,
//                                        subtitle = complaint.assignedToDepartment?.name ?: "Individual assignment",
//                                        onClick = { onViewUserProfile(assignee.userId) }
//                                    )
                                } ?: run {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Icon(
                                                        Icons.Default.PersonOff,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = "Not assigned",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "Available for assignment",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Details section
                    item {
                        ModernDetailSection(
                            title = "Details",
                            icon = Icons.Default.Info
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ModernDetailRow(
                                    icon = Icons.Default.Category,
                                    label = "Category",
                                    value = complaint.category
                                )

                                ModernDetailRow(
                                    icon = Icons.Default.Schedule,
                                    label = "Created",
                                    value = complaint.getFormattedDateTime()
                                )

                                ModernDetailRow(
                                    icon = Icons.Default.Update,
                                    label = "Last Updated",
                                    value = complaint.getFormattedUpdatedDate()
                                )

//                                complaint.assignedToDepartment?.let { dept ->
//                                    ModernDetailRow(
//                                        icon = Icons.Default.Business,
//                                        label = "Department",
//                                        value = dept.name
//                                    )
//                                }

                                if (!complaint.resolution.isNullOrBlank()) {
                                    ModernDetailRow(
                                        icon = Icons.Default.CheckCircle,
                                        label = "Resolution",
                                        value = complaint.resolution
                                    )
                                }

                                complaint.resolvedAt?.let {
                                    ModernDetailRow(
                                        icon = Icons.Default.Done,
                                        label = "Resolved At",
                                        value = complaint.getFormattedResolvedDate() ?: ""
                                    )
                                }
                            }
                        }
                    }

                    // Timeline section (if available)
                    if (complaint.timeline?.isNotEmpty() == true) {
                        item {
                            ModernDetailSection(
                                title = "Timeline",
                                icon = Icons.Default.Timeline
                            ) {
                                //ModernTimelineView(timeline = complaint.timeline)
                            }
                        }
                    }

                    // Quick actions at bottom
                    item {
                        ModernQuickActions(
                            complaint = complaint,
                            userPermissions = userPermissions,
                            onEdit = { onEdit(it) },
                            onAssign = { showActionSheet = true },
                            onClose = { onClose("Quick resolution") },
                            onReopen = onReopen
                        )
                    }
                }
            }
        }
    }

    // Action sheet
    if (showActionSheet) {
        ModernActionSheet(
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
            onDismiss = { showActionSheet = false }
        )
    }
}

@Composable
private fun ModernDetailSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        content()
    }
}

@Composable
private fun ModernPersonCard(
    title: String,
    user: UserInfo,
    profile: UserProfile?,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModernProfileAvatar(
                profilePictureUrl = profile?.profilePictureUrl,
                userName = user.name,
                size = 48.dp,
                showOnlineStatus = true,
                //isOnline = profile?.isOnline ?: false
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ModernDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ModernTimelineView(
    timeline: List<ComplaintTimelineItem>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        timeline.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = CircleShape,
                    color = getTimelineItemColor(item.type),
                    modifier = Modifier.size(8.dp)
                ) {}

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernQuickActions(
    complaint: ComplaintWithDetails,
    userPermissions: UserPermissions?,
    onEdit: (ComplaintUpdates) -> Unit,
    onAssign: () -> Unit,
    onClose: (String) -> Unit,
    onReopen: () -> Unit
) {
    val actions = buildList {
        if (complaint.canBeEdited(userPermissions, "")) {
            add(Triple(Icons.Default.Edit, "Edit", { }))
        }
        if (complaint.canBeAssigned(userPermissions)) {
            add(Triple(Icons.Default.PersonAdd, "Assign", onAssign))
        }
        if (complaint.canBeClosed(userPermissions)) {
            add(Triple(Icons.Default.CheckCircle, "Close") { onClose("Quick close") })
        }
        if (complaint.canBeReopened(userPermissions)) {
            add(Triple(Icons.Default.Refresh, "Reopen", onReopen))
        }
    }

    if (actions.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(actions) { (icon, title, action) ->
                FilledTonalButton(
                    onClick = action,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title)
                }
            }
        }
    }
}

@Composable
private fun getTimelineItemColor(type: String): Color {
    return when (type.lowercase()) {
        "created" -> Color(0xFF2196F3)
        "assigned" -> Color(0xFFFF9800)
        "updated" -> Color(0xFF9C27B0)
        "closed" -> Color(0xFF4CAF50)
        "reopened" -> Color(0xFFE91E63)
        else -> MaterialTheme.colorScheme.outline
    }
}

// Extension to support timeline items
data class ComplaintTimelineItem(
    val type: String,
    val description: String,
    val timestamp: String,
    val userId: String? = null
)

// Extension property for ComplaintWithDetails
val ComplaintWithDetails.timeline: List<ComplaintTimelineItem>?
    get() = null // This would be populated from your data source