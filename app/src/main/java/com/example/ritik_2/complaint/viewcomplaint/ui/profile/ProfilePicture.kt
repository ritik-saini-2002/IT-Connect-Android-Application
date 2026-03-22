package com.example.ritik_2.complaint.viewcomplaint.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.complaint.viewcomplaint.data.models.UserProfile
import com.example.ritik_2.complaint.viewcomplaint.utils.ProfilePictureManager

@Composable
fun ProfilePicture(
    profilePictureUrl: String?,
    userName: String,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profilePictureManager = remember(context) { ProfilePictureManager(context) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(profilePictureUrl ?: profilePictureManager.getDefaultProfilePictureUrl(userName))
                .crossfade(true)
                .build(),
            contentDescription = "Profile picture of $userName",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onSendMessage: (() -> Unit)? = null,
    onViewAllComplaints: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with close button
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "User Profile",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                // Profile Picture and Basic Info
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ProfilePicture(
                            profilePictureUrl = userProfile.profilePictureUrl,
                            userName = userProfile.name,
                            size = 100.dp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = userProfile.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = userProfile.designation,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (userProfile.isActive) {
                                Color(0xFF4CAF50).copy(alpha = 0.1f)
                            } else {
                                Color(0xFF9E9E9E).copy(alpha = 0.1f)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (userProfile.isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                            CircleShape
                                        )
                                )
                                Text(
                                    text = if (userProfile.isActive) "Active" else "Inactive",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (userProfile.isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Contact Information
                item {
                    ProfileSection(title = "Contact Information") {
                        ProfileInfoItem(
                            icon = Icons.Default.Email,
                            label = "Email",
                            value = userProfile.email
                        )

                        ProfileInfoItem(
                            icon = Icons.Default.Phone,
                            label = "Phone",
                            value = userProfile.phoneNumber
                        )
                    }
                }

                // Work Information
                item {
                    ProfileSection(title = "Work Information") {
                        ProfileInfoItem(
                            icon = Icons.Default.Business,
                            label = "Company",
                            value = userProfile.companyName
                        )

                        ProfileInfoItem(
                            icon = Icons.Default.Group,
                            label = "Department",
                            value = userProfile.department
                        )

                        ProfileInfoItem(
                            icon = Icons.Default.Work,
                            label = "Role",
                            value = userProfile.role
                        )
                    }
                }

                // Statistics
                item {
                    ProfileSection(title = "Complaint Statistics") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatisticCard(
                                title = "Created",
                                value = userProfile.complaintsCreated.toString(),
                                icon = Icons.Default.Add,
                                color = MaterialTheme.colorScheme.primary
                            )

                            StatisticCard(
                                title = "Resolved",
                                value = userProfile.complaintsResolved.toString(),
                                icon = Icons.Default.CheckCircle,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }

                // Skills (if available)
                if (userProfile.skills.isNotEmpty()) {
                    item {
                        ProfileSection(title = "Skills") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(userProfile.skills) { skill ->
                                    SkillChip(skill = skill)
                                }
                            }
                        }
                    }
                }

                // Bio (if available)
                if (!userProfile.bio.isNullOrBlank()) {
                    item {
                        ProfileSection(title = "About") {
                            Text(
                                text = userProfile.bio,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Action Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (onSendMessage != null) {
                            OutlinedButton(
                                onClick = onSendMessage,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Message,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Message")
                            }
                        }

                        if (onViewAllComplaints != null) {
                            Button(
                                onClick = onViewAllComplaints,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("View Complaints")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ProfileInfoItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatisticCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier.width(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SkillChip(skill: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = skill,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}