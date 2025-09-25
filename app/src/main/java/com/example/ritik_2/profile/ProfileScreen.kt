package com.example.ritik_2.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.profile.profilecompletion.ProfileCompletionActivity
import com.example.ritik_2.theme.Ritik_2Theme

@Composable
fun ProfileScreen(
    profileImageUrl: Uri?,
    name: String,
    email: String,
    phoneNumber: String,
    designation: String,
    companyName: String,
    role: String,
    userId: String,
    complaints: Int = 0,
    experience: Int,
    completedProjects: Int,
    activeProjects: Int,
    isLoading: Boolean,
    onLogoutClick: () -> Unit,
    onEditClick: (String, String) -> Unit,
    onChangeProfilePic: () -> Unit,
    onBackClick: () -> Unit
) {
    var isFlipped by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedField by remember { mutableStateOf("") }
    var selectedValue by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Animation for flip
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "cardFlip"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Flip Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .clickable { isFlipped = !isFlipped },
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Front side (visible when rotation <= 90f)
                if (rotation <= 90f) {
                    ProfileBackSide(
                        profileImageUrl = profileImageUrl,
                        name = name,
                        designation = designation,
                        modifier = Modifier.graphicsLayer { rotationY = 0f }
                    )
                } else {
                    // Back side (visible when rotation > 90f) - needs 180° rotation to appear correctly
                    ProfileFrontSide(
                        profileImageUrl = profileImageUrl,
                        name = name,
                        email = email,
                        phoneNumber = phoneNumber,
                        designation = designation,
                        companyName = companyName,
                        experience = experience,
                        completedProjects = completedProjects,
                        activeProjects = activeProjects,
                        complaints = complaints,
                        onEditClick = { field, value ->
                            selectedField = field
                            selectedValue = value
                            showEditDialog = true
                        },
                        onChangeProfilePic = onChangeProfilePic,
                        modifier = Modifier.graphicsLayer { rotationY = 180f }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    val intent = Intent(context, ProfileCompletionActivity::class.java)
                    intent.putExtra("userId", userId)
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Update Profile",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Update Profile",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = onLogoutClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = "Logout",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onError
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Logout",
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }

    // Edit Dialog
    if (showEditDialog) {
        EditDialog(
            field = selectedField,
            value = selectedValue,
            onSave = { newValue ->
                onEditClick(selectedField, newValue)
                showEditDialog = false
            },
            onClose = { showEditDialog = false }
        )
    }
}

@Composable
fun ProfileFrontSide(
    profileImageUrl: Uri?,
    name: String,
    email: String,
    phoneNumber: String,
    designation: String,
    companyName: String,
    experience: Int,
    completedProjects: Int,
    activeProjects: Int,
    complaints: Int,
    onEditClick: (String, String) -> Unit,
    onChangeProfilePic: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Picture
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onChangeProfilePic() },
            contentAlignment = Alignment.Center
        ) {
            if (profileImageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(profileImageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Default Profile",
                    modifier = Modifier.size(50.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name and Designation
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = designation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Personal Information Section
        ProfileSection(title = "Personal Information") {
            ProfileInfoItem(
                icon = Icons.Filled.Email,
                label = "Email",
                value = email,
                onClick = { onEditClick("Email", email) }
            )
            ProfileInfoItem(
                icon = Icons.Filled.Phone,
                label = "Phone",
                value = phoneNumber,
                onClick = { onEditClick("Phone", phoneNumber) }
            )
            ProfileInfoItem(
                icon = Icons.Filled.Business,
                label = "Company",
                value = companyName,
                onClick = { onEditClick("Company", companyName) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Professional Statistics
        ProfileSection(title = "Professional Stats") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(
                    title = "Experience",
                    value = "$experience years",
                    color = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    title = "Completed",
                    value = "$completedProjects",
                    color = Color(0xFF4CAF50) // Green
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(
                    title = "Active",
                    value = "$activeProjects",
                    color = Color(0xFF2196F3) // Blue
                )
                StatCard(
                    title = "Complaints",
                    value = "$complaints",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ProfileBackSide(
    profileImageUrl: Uri?,
    name: String,
    designation: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Large Profile Picture
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (profileImageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(profileImageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Default Profile",
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Name and Designation centered at bottom
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = designation,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ProfileSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
fun ProfileInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = "Edit",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color
) {
    Card(
        modifier = Modifier
            .width(80.dp)
            .height(60.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontSize = 10.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDialog(
    field: String,
    value: String,
    onSave: (String) -> Unit,
    onClose: () -> Unit
) {
    var newValue by remember { mutableStateOf(value) }

    AlertDialog(
        onDismissRequest = { onClose() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Edit $field",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            OutlinedTextField(
                value = newValue,
                onValueChange = { newValue = it },
                label = {
                    Text(
                        "Enter new $field",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(newValue) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Save",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onClose() },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewProfileScreen() {
    Ritik_2Theme {
        ProfileScreen(
            profileImageUrl = null,
            name = "John Doe",
            email = "john.doe@company.com",
            phoneNumber = "+1234567890",
            designation = "Senior Developer",
            companyName = "Tech Corp",
            role = "Administrator",
            userId = "user123",
            complaints = 2,
            experience = 5,
            completedProjects = 15,
            activeProjects = 3,
            isLoading = false,
            onLogoutClick = { },
            onEditClick = { _, _ -> },
            onChangeProfilePic = { },
            onBackClick = { }
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewProfileScreenDark() {
    Ritik_2Theme {
        ProfileScreen(
            profileImageUrl = null,
            name = "John Doe",
            email = "john.doe@company.com",
            phoneNumber = "+1234567890",
            designation = "Senior Developer",
            companyName = "Tech Corp",
            role = "Administrator",
            userId = "user123",
            complaints = 2,
            experience = 5,
            completedProjects = 15,
            activeProjects = 3,
            isLoading = false,
            onLogoutClick = { },
            onEditClick = { _, _ -> },
            onChangeProfilePic = { },
            onBackClick = { }
        )
    }
}