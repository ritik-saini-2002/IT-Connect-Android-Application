package com.example.ritik_2.complaint.viewcomplaint.ui.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.ritik_2.complaint.viewcomplaint.data.models.UserProfile
import kotlin.math.absoluteValue

@Composable
fun ModernProfileAvatar(
    profilePictureUrl: String?,
    userName: String,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
    showOnlineStatus: Boolean = true,
    isOnline: Boolean = false
) {
    val initials = remember(userName) {
        userName.split(" ")
            .take(2)
            .joinToString("") { it.firstOrNull()?.uppercase() ?: "" }
    }

    val colors = remember(userName) {
        getAvatarColors(userName.hashCode())
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else Modifier
            )
    ) {
        if (!profilePictureUrl.isNullOrBlank()) {
            AsyncImage(
                model = profilePictureUrl,
                contentDescription = "Profile picture of $userName",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentScale = ContentScale.Crop
            )
        } else {
            // Gradient background for initials
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = colors.first,
                border = BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(colors.first, colors.second),
                                radius = size.value
                            )
                        )
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        fontSize = (size.value / 3).sp
                    )
                }
            }
        }

        // Online status indicator
        if (showOnlineStatus) {
            Surface(
                modifier = Modifier
                    .size(size * 0.3f)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp),
                shape = CircleShape,
                color = if (isOnline) Color(0xFF4CAF50) else Color.Gray,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
            ) {}
        }
    }
}

@Composable
fun UserProfileDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onSendMessage: () -> Unit,
    onViewAllComplaints: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Profile header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModernProfileAvatar(
                        profilePictureUrl = userProfile.profilePictureUrl,
                        userName = userProfile.name,
                        size = 80.dp,
                        showOnlineStatus = true,
                        //isOnline = userProfile.isOnline
                    )

                    Text(
                        text = userProfile.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "${userProfile.role} • ${userProfile.department}",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Stats section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStatItem(
                        value = userProfile.complaintsCreated.toString(),
                        label = "Created",
                        icon = Icons.Default.Add
                    )
//                    ProfileStatItem(
//                        value = userProfile.complaintsAssigned.toString(),
//                        label = "Assigned",
//                        icon = Icons.Default.Assignment
//                    )
                    ProfileStatItem(
                        value = userProfile.complaintsResolved.toString(),
                        label = "Resolved",
                        icon = Icons.Default.CheckCircle
                    )
                }

                // Contact info
//                if (!userProfile.email.isNullOrBlank() || !userProfile.phone.isNullOrBlank()) {
//                    Surface(
//                        modifier = Modifier.fillMaxWidth(),
//                        shape = RoundedCornerShape(16.dp),
//                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
//                    ) {
//                        Column(
//                            modifier = Modifier.padding(16.dp),
//                            verticalArrangement = Arrangement.spacedBy(8.dp)
//                        ) {
//                            if (!userProfile.email.isNullOrBlank()) {
//                                Row(
//                                    verticalAlignment = Alignment.CenterVertically,
//                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
//                                ) {
//                                    Icon(
//                                        Icons.Default.Email,
//                                        contentDescription = null,
//                                        modifier = Modifier.size(20.dp),
//                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
//                                    )
//                                    Text(
//                                        text = userProfile.email,
//                                        style = MaterialTheme.typography.bodyMedium
//                                    )
//                                }
//                            }
//
//                            if (!userProfile.phone.isNullOrBlank()) {
//                                Row(
//                                    verticalAlignment = Alignment.CenterVertically,
//                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
//                                ) {
//                                    Icon(
//                                        Icons.Default.Phone,
//                                        contentDescription = null,
//                                        modifier = Modifier.size(20.dp),
//                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
//                                    )
//                                    Text(
//                                        text = userProfile.phone,
//                                        style = MaterialTheme.typography.bodyMedium
//                                    )
//                                }
//                            }
//                        }
//                    }
//                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSendMessage,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Message,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Message")
                    }

                    Button(
                        onClick = onViewAllComplaints,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View All")
                    }
                }

                // Close button
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun ProfileStatItem(
    value: String,
    label: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getAvatarColors(seed: Int): Pair<Color, Color> {
    val colors = listOf(
        Color(0xFF6366F1) to Color(0xFF8B5CF6), // Indigo to Purple
        Color(0xFF10B981) to Color(0xFF059669), // Emerald
        Color(0xFFF59E0B) to Color(0xFFD97706), // Amber
        Color(0xFFEF4444) to Color(0xFFDC2626), // Red
        Color(0xFF3B82F6) to Color(0xFF2563EB), // Blue
        Color(0xFF8B5CF6) to Color(0xFF7C3AED), // Violet
        Color(0xFFF97316) to Color(0xFFEA580C), // Orange
        Color(0xFF14B8A6) to Color(0xFF0D9488), // Teal
    )

    return colors[seed.absoluteValue % colors.size]
}