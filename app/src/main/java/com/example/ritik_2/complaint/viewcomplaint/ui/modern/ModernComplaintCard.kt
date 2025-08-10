package com.example.ritik_2.complaint.viewcomplaint.ui.modern

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ritik_2.complaint.viewcomplaint.data.models.*
import com.example.ritik_2.complaint.viewcomplaint.ui.profile.ModernProfileAvatar
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernComplaintCard(
    complaint: ComplaintWithDetails,
    currentUser: UserData?,
    userPermissions: UserPermissions?,
    availableEmployees: List<UserData>,
    userProfile: UserProfile?,
    isLoading: Boolean = false,
    onEdit: (ComplaintUpdates) -> Unit,
    onDelete: () -> Unit,
    onAssign: (String, String) -> Unit,
    onClose: (String) -> Unit,
    onReopen: () -> Unit,
    onChangeStatus: (String, String) -> Unit,
    onViewDetails: () -> Unit,
    onViewUserProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }
    var showProgressAction by remember { mutableStateOf(false) }

    // Animation states
    val animatedProgress by animateFloatAsState(
        targetValue = if (showProgressAction) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "progress"
    )

    val cardElevation by animateDpAsState(
        targetValue = if (isExpanded) 12.dp else 4.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "elevation"
    )

    val cardScale by animateFloatAsState(
        targetValue = if (isExpanded) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onViewDetails()
            },
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        colors = CardDefaults.cardColors(
            containerColor = getCardBackgroundColor(complaint)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Progress indicator overlay
            if (isLoading || showProgressAction) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
            }

            // Main content
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with profile and actions
                ModernCardHeader(
                    complaint = complaint,
                    userProfile = userProfile,
                    isExpanded = isExpanded,
                    onToggleExpanded = {
                        isExpanded = !isExpanded
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onViewUserProfile = onViewUserProfile,
                    onShowActionMenu = { showActionMenu = true }
                )

                // Status and priority indicators
                ModernStatusSection(
                    complaint = complaint,
                    animated = true
                )

                // Description with smooth expand/collapse
//                AnimatedVisibility(
//                    visible = isExpanded || complaint.description.length < 100,
//                    enter = expandVertically(
//                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
//                    ) + fadeIn(),
//                    exit = shrinkVertically() + fadeOut()
//                ) {
//                    ModernDescriptionSection(
//                        description = complaint.description,
//                        isExpanded = isExpanded
//                    )
//                }
//
//                // Assignment info with icons
//                ModernAssignmentSection(complaint = complaint)
//
//                // Footer with timestamp and quick actions
//                ModernCardFooter(
//                    complaint = complaint,
//                    userPermissions = userPermissions,
//                    onQuickAssign = { showProgressAction = true },
//                    onQuickClose = {
//                        showProgressAction = true
//                        onClose("Quick resolution")
//                    }
//                )
            }
        }
    }

    // Action menu with modern design
    if (showActionMenu) {
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
            onDismiss = { showActionMenu = false }
        )
    }

    // Auto-reset progress animation
    LaunchedEffect(showProgressAction) {
        if (showProgressAction) {
            delay(2000)
            showProgressAction = false
        }
    }
}

@Composable
private fun getCardBackgroundColor(complaint: ComplaintWithDetails): Color {
    return when {
        complaint.isOverdue() -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        complaint.status.lowercase() == "closed" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f)
        complaint.urgency.lowercase() == "critical" -> MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
        else -> MaterialTheme.colorScheme.surface
    }
}

@Composable
private fun ModernCardHeader(
    complaint: ComplaintWithDetails,
    userProfile: UserProfile?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onViewUserProfile: (String) -> Unit,
    onShowActionMenu: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Modern profile avatar with status indicator
        Box {
            ModernProfileAvatar(
                profilePictureUrl = userProfile?.profilePictureUrl,
                userName = complaint.createdBy.name,
                size = 48.dp,
                onClick = { onViewUserProfile(complaint.createdBy.userId) }
            )

            // Online status indicator
            Surface(
                modifier = Modifier
                    .size(14.dp)
                    .offset(x = 2.dp, y = 2.dp)
                    .align(Alignment.BottomEnd),
                shape = CircleShape,
                //color = if (userProfile?.isOnline == true) Color.Green else Color.Gray,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
            ) {}
        }

        // Title and metadata
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = complaint.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "ID: ${complaint.id.take(8)}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "• ${complaint.getTimeSinceCreation()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Expand/collapse button
            FilledTonalIconButton(
                onClick = onToggleExpanded,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp)
                )
            }

            // More actions button
            FilledTonalIconButton(
                onClick = onShowActionMenu,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ModernStatusSection(
    complaint: ComplaintWithDetails,
    animated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            ModernStatusChip(
                text = complaint.status,
                type = ChipType.STATUS,
                animated = animated,
                shimmerAlpha = if (complaint.status == "In Progress" && animated) shimmer else 0f
            )
        }

        item {
            ModernStatusChip(
                text = complaint.urgency,
                type = ChipType.URGENCY,
                animated = animated
            )
        }

        if (complaint.isGlobal) {
            item {
                ModernStatusChip(
                    text = "Global",
                    type = ChipType.GLOBAL,
                    icon = Icons.Default.Public,
                    animated = animated
                )
            }
        }

        if (complaint.isOverdue()) {
            item {
                ModernStatusChip(
                    text = "Overdue",
                    type = ChipType.ERROR,
                    icon = Icons.Default.Warning,
                    animated = animated,
                    shimmerAlpha = if (animated) shimmer else 0f
                )
            }
        }

        if (complaint.hasAttachment) {
            item {
                ModernStatusChip(
                    text = "Files",
                    type = ChipType.INFO,
                    icon = Icons.Default.Attachment,
                    animated = animated
                )
            }
        }
    }
}

enum class ChipType {
    STATUS, URGENCY, GLOBAL, ERROR, INFO, SUCCESS
}

@Composable
fun ModernStatusChip(
    text: String,
    type: ChipType,
    icon: ImageVector? = null,
    animated: Boolean = true,
    shimmerAlpha: Float = 0f,
    modifier: Modifier = Modifier
) {
    val colors = getChipColors(type, text)
    val animatedColors by animateColorAsState(
        targetValue = colors.first,
        animationSpec = tween(300),
        label = "chipColor"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (shimmerAlpha > 0f) {
                    Modifier.background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                colors.first,
                                colors.first.copy(alpha = 0.7f),
                                colors.first
                            )
                        )
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (shimmerAlpha == 0f) animatedColors.copy(alpha = 0.15f) else Color.Transparent,
        border = BorderStroke(1.dp, animatedColors.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = animatedColors
                )
            }

            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = animatedColors,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun getChipColors(type: ChipType, text: String): Pair<Color, Color> {
    return when (type) {
        ChipType.STATUS -> when (text.lowercase()) {
            "open" -> Color(0xFF2196F3) to Color.White
            "in progress", "assigned" -> Color(0xFFFF9800) to Color.White
            "closed", "resolved" -> Color(0xFF4CAF50) to Color.White
            "cancelled" -> Color(0xFF9E9E9E) to Color.White
            "reopened" -> Color(0xFFE91E63) to Color.White
            else -> MaterialTheme.colorScheme.outline to MaterialTheme.colorScheme.onSurface
        }
        ChipType.URGENCY -> when (text.lowercase()) {
            "critical" -> Color(0xFFFF0000) to Color.White
            "high" -> Color(0xFFFF6600) to Color.White
            "medium" -> Color(0xFFFFB300) to Color.White
            "low" -> Color(0xFF4CAF50) to Color.White
            else -> MaterialTheme.colorScheme.outline to MaterialTheme.colorScheme.onSurface
        }
        ChipType.GLOBAL -> MaterialTheme.colorScheme.primary to Color.White
        ChipType.ERROR -> MaterialTheme.colorScheme.error to Color.White
        ChipType.INFO -> MaterialTheme.colorScheme.tertiary to Color.White
        ChipType.SUCCESS -> Color(0xFF4CAF50) to Color.White
    }
}