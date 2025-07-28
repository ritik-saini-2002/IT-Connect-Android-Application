package com.example.ritik_2.profile

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ProfileScreen(
    profileImageUrl: Uri?,
    name: String,
    email: String,
    phoneNumber: String,
    designation: String,
    companyName: String,
    role: String,
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

    // Animation for flip
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(
            durationMillis = 800,
            easing = FastOutSlowInEasing
        ),
        label = "cardFlip"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Instructions
        /*Text(
            text = "Tap the card to flip",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )*/

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
            colors = CardDefaults.cardColors(
                containerColor = if (rotation > 180f) Color.White else Color.White
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (rotation <= 90f) {
                    // Front side - Full profile information
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
                        onChangeProfilePic = onChangeProfilePic
                    )
                } else {
                    // Back side - Profile picture with name
                    ProfileBackSide(
                        profileImageUrl = profileImageUrl,
                        name = name,
                        designation = designation,
                        modifier = Modifier.graphicsLayer {
                            rotationY = 180f
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { isFlipped = !isFlipped },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Flip,
                    contentDescription = "Details",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Details")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = onLogoutClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = "Logout",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout")
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
    onChangeProfilePic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Picture
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.2f))
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
                    tint = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name and Designation
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = designation,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
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
                    color = Color.Blue
                )
                StatCard(
                    title = "Completed",
                    value = "$completedProjects",
                    color = Color.Green
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
                    color = Color.Blue
                )
                StatCard(
                    title = "Complaints",
                    value = "$complaints",
                    color = Color.Red
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
                .background(Color.Gray.copy(alpha = 0.2f)),
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
                    tint = Color.Gray
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
            color = Color.Gray,
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
                color = Color.Gray
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = "Edit",
            tint = Color.Gray,
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
        title = {
            Text(
                text = "Edit $field",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = newValue,
                onValueChange = { newValue = it },
                label = { Text("Enter new $field") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(newValue) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { onClose() }) {
                Text("Cancel")
            }
        }
    )
}