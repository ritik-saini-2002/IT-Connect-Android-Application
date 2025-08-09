package com.example.ritik_2.administrator.administratorpanel.usermanagement

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.*

// Complete Color Scheme
object ManageUserColors {
    val OffWhite = Color(0xFFF8F9FA)
    val LightGray = Color(0xFFE9ECEF)
    val MediumGray = Color(0xFF6C757D)
    val DarkGray = Color(0xFF343A40)
    val CharcoalGray = Color(0xFF212529)

    // Surface Colors
    val CardBackground = Color(0xFFFFFFFF)
    val SurfaceLight = Color(0xFFF1F3F4)
    val SurfaceMedium = Color(0xFFDEE2E6)
    val SurfaceDark = Color(0xFFADB5BD)

    // Accent Colors for Actions and Status
    val AccentBlue = Color(0xFF007BFF)
    val AccentGreen = Color(0xFF28A745)
    val AccentRed = Color(0xFFDC3545)
    val AccentOrange = Color(0xFFFD7E14)
    val AccentPurple = Color(0xFF6F42C1)
    val AccentYellow = Color(0xFFFFC107)
    val AccentTeal = Color(0xFF20C997)
    val AccentIndigo = Color(0xFF6610F2)
    val AccentPink = Color(0xFFE83E8C)

    // Text Colors
    val TextPrimary = Color(0xFF212529)
    val TextSecondary = Color(0xFF6C757D)
    val TextTertiary = Color(0xFFADB5BD)
    val TextOnAccent = Color(0xFFFFFFFF)

    // Status Colors
    val StatusActive = AccentGreen
    val StatusInactive = AccentRed
    val StatusPending = AccentOrange
    val StatusWarning = AccentYellow
    val StatusInfo = AccentBlue

    // Button Colors
    val ButtonPrimary = AccentBlue
    val ButtonSecondary = MediumGray
    val ButtonSuccess = AccentGreen
    val ButtonDanger = AccentRed
    val ButtonWarning = AccentOrange

    // Border Colors
    val BorderLight = Color(0xFFDEE2E6)
    val BorderMedium = Color(0xFFCED4DA)
    val BorderDark = Color(0xFFADB5BD)
}

// Fixed ManageUserScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUserScreen(
    viewModel: ManageUserViewModel = viewModel()
) {
    // Fixed: Using direct state access instead of collectAsState()
    val companies by viewModel.companies
    val departments by viewModel.departments
    val roles by viewModel.roles
    val users by viewModel.users
    val selectedCompany by viewModel.selectedCompany
    val selectedDepartment by viewModel.selectedDepartment
    val selectedRole by viewModel.selectedRole
    val isLoading by viewModel.isLoading
    val errorMessage by viewModel.errorMessage
    val currentUserRole by viewModel.currentUserRole

    var showUserDialog by remember { mutableStateOf<UserProfile?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf<UserProfile?>(null) }

    // Error message handling
    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotEmpty()) {
            // Show snackbar or toast
            // You can implement SnackbarHost here if needed
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 70.dp, bottom = 16.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(ManageUserColors.OffWhite, ManageUserColors.LightGray)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            ManageUserHeader(currentUserRole)

            Spacer(modifier = Modifier.height(20.dp))

            // Hierarchy Navigation
            HierarchyNavigation(
                companies = companies,
                departments = departments,
                roles = roles,
                selectedCompany = selectedCompany,
                selectedDepartment = selectedDepartment,
                selectedRole = selectedRole,
                onCompanySelected = viewModel::selectCompany,
                onDepartmentSelected = viewModel::selectDepartment,
                onRoleSelected = viewModel::selectRole
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Users List
            AnimatedVisibility(
                visible = users.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                UsersListSection(
                    users = users,
                    onUserClick = { showUserDialog = it },
                    onToggleUserStatus = viewModel::toggleUserActiveStatus,
                    onDeleteUser = { showDeleteConfirmation = it }
                )
            }

            // Empty state
            if (users.isEmpty() && selectedRole.isNotEmpty() && !isLoading) {
                EmptyUsersState()
            }
        }

        // Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = ManageUserColors.AccentBlue
                )
            }
        }
    }

    // User Detail Dialog
    showUserDialog?.let { user ->
        UserDetailDialog(
            user = user,
            onDismiss = { showUserDialog = null },
            onUpdateRole = { /* viewModel::updateUserRole */ } as (UserProfile, String) -> Unit,
            onToggleStatus = {
                viewModel.toggleUserActiveStatus(it)
                showUserDialog = null
            }
        )
    }

    // Delete Confirmation Dialog
    showDeleteConfirmation?.let { user ->
        DeleteUserConfirmationDialog(
            user = user,
            onConfirm = {
                viewModel.deleteUser(it)
                showDeleteConfirmation = null
            },
            onDismiss = { showDeleteConfirmation = null }
        )
    }
}

@Composable
private fun ManageUserHeader(currentUserRole: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ManageUserColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Manage Users",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = ManageUserColors.DarkGray
                )
                Text(
                    text = "Role: $currentUserRole",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ManageUserColors.MediumGray
                )
            }

            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = "Admin Panel",
                tint = ManageUserColors.AccentBlue,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HierarchyNavigation(
    companies: List<Company>,
    departments: List<Department>,
    roles: List<RoleInfo>,
    selectedCompany: String,
    selectedDepartment: String,
    selectedRole: String,
    onCompanySelected: (String) -> Unit,
    onDepartmentSelected: (String) -> Unit,
    onRoleSelected: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Company Selection
        if (companies.size > 1) {
            SelectionCard(
                title = "Select Company",
                items = companies.map { it.name to it.sanitizedName },
                selectedValue = selectedCompany,
                onSelectionChanged = onCompanySelected,
                icon = Icons.Default.Business,
                color = ManageUserColors.AccentBlue
            )
        } else if (companies.isNotEmpty()) {
            InfoCard(
                title = "Company",
                value = companies.first().name,
                subtitle = "${companies.first().totalUsers} Total Users",
                icon = Icons.Default.Business,
                color = ManageUserColors.AccentBlue
            )
        }

        // Department Selection
        AnimatedVisibility(
            visible = departments.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            SelectionCard(
                title = "Select Department",
                items = departments.map { it.name to it.sanitizedName },
                selectedValue = selectedDepartment,
                onSelectionChanged = onDepartmentSelected,
                icon = Icons.Default.AccountTree,
                color = ManageUserColors.AccentGreen
            )
        }

        // Role Selection
        AnimatedVisibility(
            visible = roles.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            SelectionCard(
                title = "Select Role",
                items = roles.map { it.roleName to it.roleName },
                selectedValue = selectedRole,
                onSelectionChanged = onRoleSelected,
                icon = Icons.Default.Group,
                color = ManageUserColors.AccentPurple
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionCard(
    title: String,
    items: List<Pair<String, String>>,
    selectedValue: String,
    onSelectionChanged: (String) -> Unit,
    icon: ImageVector,
    color: Color
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ManageUserColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = ManageUserColors.DarkGray
                )
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = items.find { it.second == selectedValue }?.first ?: "Select $title",
                    onValueChange = { },
                    readOnly = true,
                    trailingIcon = {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = color,
                        unfocusedBorderColor = ManageUserColors.LightGray
                    )
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    items.forEach { (displayName, value) ->
                        DropdownMenuItem(
                            text = { Text(displayName) },
                            onClick = {
                                onSelectionChanged(value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ManageUserColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = ManageUserColors.MediumGray
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = ManageUserColors.DarkGray
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ManageUserColors.MediumGray
                )
            }
        }
    }
}

@Composable
private fun UsersListSection(
    users: List<UserProfile>,
    onUserClick: (UserProfile) -> Unit,
    onToggleUserStatus: (UserProfile) -> Unit,
    onDeleteUser: (UserProfile) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ManageUserColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Users (${users.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ManageUserColors.DarkGray
                )

                Row {
                    Text(
                        text = "Active: ${users.count { it.isActive }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ManageUserColors.AccentGreen
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Inactive: ${users.count { !it.isActive }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ManageUserColors.AccentRed
                    )
                }
            }

            HorizontalDivider(color = ManageUserColors.LightGray)

            // Users List
            LazyColumn {
                items(users) { user ->  // Fixed: Removed the package prefix
                    UserListItem(
                        user = user,
                        onClick = { onUserClick(user) },
                        onToggleStatus = { onToggleUserStatus(user) },
                        onDelete = { onDeleteUser(user) }
                    )

                    if (user != users.last()) {
                        HorizontalDivider(
                            color = ManageUserColors.LightGray.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserListItem(
    user: UserProfile,
    onClick: () -> Unit,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Image
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(user.imageUrl.ifEmpty { "https://via.placeholder.com/150" })
                .crossfade(true)
                .build(),
            contentDescription = "Profile Picture",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ManageUserColors.LightGray),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // User Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = ManageUserColors.DarkGray
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                Badge(
                    containerColor = if (user.isActive) ManageUserColors.AccentGreen else ManageUserColors.AccentRed
                ) {
                    Text(
                        text = if (user.isActive) "Active" else "Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = ManageUserColors.MediumGray
            )

            Text(
                text = "${user.designation} • ${user.experience} years exp",
                style = MaterialTheme.typography.bodySmall,
                color = ManageUserColors.MediumGray
            )

            // Stats Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                StatChip("${user.activeProjects} Active", ManageUserColors.AccentBlue)
                StatChip("${user.completedProjects} Completed", ManageUserColors.AccentGreen)
                if (user.totalComplaints > 0) {  // Fixed: Use > instead of compareTo
                    StatChip("${user.totalComplaints} Issues", ManageUserColors.AccentRed)
                }
            }
        }

        // Action Buttons
        Row {
            IconButton(
                onClick = onToggleStatus
            ) {
                Icon(
                    imageVector = if (user.isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (user.isActive) "Deactivate" else "Activate",
                    tint = if (user.isActive) ManageUserColors.AccentOrange else ManageUserColors.AccentGreen
                )
            }

            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete User",
                    tint = ManageUserColors.AccentRed
                )
            }
        }
    }
}

@Composable
private fun StatChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyUsersState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            tint = ManageUserColors.MediumGray,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Users Found",
            style = MaterialTheme.typography.titleMedium,
            color = ManageUserColors.MediumGray,
            textAlign = TextAlign.Center
        )

        Text(
            text = "No users are assigned to this role yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = ManageUserColors.MediumGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserDetailDialog(
    user: UserProfile,
    onDismiss: () -> Unit,
    onUpdateRole: (UserProfile, String) -> Unit,
    onToggleStatus: (UserProfile) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ManageUserColors.CardBackground)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "User Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ManageUserColors.DarkGray
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Profile Section
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(user.imageUrl.ifEmpty { "https://via.placeholder.com/150" })
                            .crossfade(true)
                            .build(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(ManageUserColors.LightGray),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ManageUserColors.DarkGray
                        )
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ManageUserColors.MediumGray
                        )
                        Text(
                            text = user.phoneNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ManageUserColors.MediumGray
                        )

                        // Status Badge
                        Badge(
                            containerColor = if (user.isActive) ManageUserColors.AccentGreen else ManageUserColors.AccentRed,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = if (user.isActive) "Active" else "Inactive",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Details Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(200.dp)
                ) {
                    item {
                        DetailCard("Role", user.role, ManageUserColors.AccentPurple)
                    }
                    item {
                        DetailCard("Department", user.department, ManageUserColors.AccentBlue)
                    }
                    item {
                        DetailCard("Designation", user.designation, ManageUserColors.AccentGreen)
                    }
                    item {
                        DetailCard("Experience", "${user.experience} years", ManageUserColors.AccentOrange)
                    }
                    item {
                        DetailCard("Active Projects", user.activeProjects.toString(), ManageUserColors.AccentBlue)
                    }
                    item {
                        DetailCard("Completed", user.completedProjects.toString(), ManageUserColors.AccentGreen)
                    }
                    if (user.totalComplaints > 0) {
                        item {
                            DetailCard("Issues", user.totalComplaints.toString(), ManageUserColors.AccentRed)
                        }
                    }
                    if (user.lastLogin != null) {
                        item {
                            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                            DetailCard("Last Login", dateFormat.format(user.lastLogin!!.toDate()), ManageUserColors.MediumGray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onToggleStatus(user) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (user.isActive) ManageUserColors.AccentOrange else ManageUserColors.AccentGreen
                        )
                    ) {
                        Icon(
                            imageVector = if (user.isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (user.isActive) "Deactivate" else "Activate")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, value: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                textAlign = TextAlign.Center
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = ManageUserColors.DarkGray,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DeleteUserConfirmationDialog(
    user: UserProfile,
    onConfirm: (UserProfile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete User",
                fontWeight = FontWeight.Bold,
                color = ManageUserColors.AccentRed
            )
        },
        text = {
            Column {
                Text("Are you sure you want to delete this user?")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${user.name} (${user.email})",
                    fontWeight = FontWeight.Medium,
                    color = ManageUserColors.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This action cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ManageUserColors.AccentRed
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(user) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ManageUserColors.AccentRed
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