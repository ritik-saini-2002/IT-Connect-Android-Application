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

    val CardBackground = Color(0xFFFFFFFF)
    val SurfaceLight = Color(0xFFF1F3F4)
    val SurfaceMedium = Color(0xFFDEE2E6)
    val SurfaceDark = Color(0xFFADB5BD)

    val AccentBlue = Color(0xFF007BFF)
    val AccentGreen = Color(0xFF28A745)
    val AccentRed = Color(0xFFDC3545)
    val AccentOrange = Color(0xFFFD7E14)
    val AccentPurple = Color(0xFF6F42C1)
    val AccentYellow = Color(0xFFFFC107)
    val AccentTeal = Color(0xFF20C997)
    val AccentIndigo = Color(0xFF6610F2)
    val AccentPink = Color(0xFFE83E8C)

    val TextPrimary = Color(0xFF212529)
    val TextSecondary = Color(0xFF6C757D)
    val TextTertiary = Color(0xFFADB5BD)
    val TextOnAccent = Color(0xFFFFFFFF)

    val StatusActive = AccentGreen
    val StatusInactive = AccentRed
    val StatusPending = AccentOrange
    val StatusWarning = AccentYellow
    val StatusInfo = AccentBlue

    val ButtonPrimary = AccentBlue
    val ButtonSecondary = MediumGray
    val ButtonSuccess = AccentGreen
    val ButtonDanger = AccentRed
    val ButtonWarning = AccentOrange

    val BorderLight = Color(0xFFDEE2E6)
    val BorderMedium = Color(0xFFCED4DA)
    val BorderDark = Color(0xFFADB5BD)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUserScreen(
    viewModel: ManageUserViewModel = viewModel()
) {
    val companies by viewModel.companies
    val departments by viewModel.departments
    val roles by viewModel.roles
    val users by viewModel.users
    val expandedCompanies by viewModel.expandedCompanies
    val expandedDepartments by viewModel.expandedDepartments
    val expandedRoles by viewModel.expandedRoles
    val isLoading by viewModel.isLoading
    val errorMessage by viewModel.errorMessage
    val currentUserRole by viewModel.currentUserRole

    var showUserDialog by remember { mutableStateOf<UserProfile?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf<UserProfile?>(null) }
    var snackbarHostState = remember { SnackbarHostState() }

    // Error message handling
    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(
                message = errorMessage,
                duration = SnackbarDuration.Short
            )
            viewModel.clearErrorMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
//            .background(
//                Brush.verticalGradient(
//                    colors = listOf(ManageUserColors.OffWhite, ManageUserColors.LightGray)
//                )
//            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 60.dp, bottom = 20.dp, start = 16.dp, end = 16.dp),
        ) {
            ManageUserHeader(currentUserRole)
            Spacer(modifier = Modifier.height(20.dp))

            // Fixed Folder-like hierarchy navigation
            FolderHierarchyNavigation(
                companies = companies,
                departments = departments,
                roles = roles,
                users = users,
                expandedCompanies = expandedCompanies,
                expandedDepartments = expandedDepartments,
                expandedRoles = expandedRoles,
                onCompanyToggle = { companyName ->
                    viewModel.toggleCompanyExpanded(companyName)
                },
                onDepartmentToggle = { companyName, departmentName ->
                    viewModel.toggleDepartmentExpanded(companyName, departmentName)
                },
                onRoleToggle = { companyName, departmentName, roleName ->
                    viewModel.toggleRoleExpanded(companyName, departmentName, roleName)
                },
                onUserClick = { showUserDialog = it },
                onToggleUserStatus = viewModel::toggleUserActiveStatus,
                onDeleteUser = { showDeleteConfirmation = it },
                getDepartmentsForCompany = { companyName ->
                    viewModel.getDepartmentsForCompany(companyName)
                },
                getRolesForDepartment = { companyName, departmentName ->
                    viewModel.getRolesForDepartment(companyName, departmentName)
                },
                getUsersForRole = { companyName, departmentName, roleName ->
                    viewModel.getUsersForRole(companyName, departmentName, roleName)
                }
            )

            // Empty state when no companies
            if (companies.isEmpty() && !isLoading) {
                EmptyCompaniesState()
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ManageUserColors.AccentBlue)
            }
        }

        // Snackbar for error messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // User Detail Dialog
    showUserDialog?.let { user ->
        UserDetailDialog(
            user = user,
            onDismiss = { showUserDialog = null },
            onUpdateRole = { userProfile, newRole ->
                viewModel.updateUserRole(userProfile, newRole)
            },
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
private fun FolderHierarchyNavigation(
    companies: List<Company>,
    departments: List<Department>,
    roles: List<RoleInfo>,
    users: List<UserProfile>,
    expandedCompanies: Set<String>,
    expandedDepartments: Set<String>,
    expandedRoles: Set<String>,
    onCompanyToggle: (String) -> Unit,
    onDepartmentToggle: (String, String) -> Unit,
    onRoleToggle: (String, String, String) -> Unit,
    onUserClick: (UserProfile) -> Unit,
    onToggleUserStatus: (UserProfile) -> Unit,
    onDeleteUser: (UserProfile) -> Unit,
    getDepartmentsForCompany: (String) -> List<Department>,
    getRolesForDepartment: (String, String) -> List<RoleInfo>,
    getUsersForRole: (String, String, String) -> List<UserProfile>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ManageUserColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        LazyColumn(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(companies) { company ->
                CompanyFolderItem(
                    company = company,
                    isExpanded = expandedCompanies.contains(company.sanitizedName),
                    onToggle = { onCompanyToggle(company.sanitizedName) },
                    departments = getDepartmentsForCompany(company.sanitizedName),
                    expandedDepartments = expandedDepartments,
                    expandedRoles = expandedRoles,
                    onDepartmentToggle = onDepartmentToggle,
                    onRoleToggle = onRoleToggle,
                    onUserClick = onUserClick,
                    onToggleUserStatus = onToggleUserStatus,
                    onDeleteUser = onDeleteUser,
                    getRolesForDepartment = getRolesForDepartment,
                    getUsersForRole = getUsersForRole
                )
            }
        }
    }
}

@Composable
private fun CompanyFolderItem(
    company: Company,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    departments: List<Department>,
    expandedDepartments: Set<String>,
    expandedRoles: Set<String>,
    onDepartmentToggle: (String, String) -> Unit,
    onRoleToggle: (String, String, String) -> Unit,
    onUserClick: (UserProfile) -> Unit,
    onToggleUserStatus: (UserProfile) -> Unit,
    onDeleteUser: (UserProfile) -> Unit,
    getRolesForDepartment: (String, String) -> List<RoleInfo>,
    getUsersForRole: (String, String, String) -> List<UserProfile>
) {
    Column {
        // Company Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            //colors = CardDefaults.cardColors(
                //containerColor = ManageUserColors.AccentBlue.copy(alpha = 0.1f)
            //),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = ManageUserColors.AccentBlue
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Business,
                    contentDescription = "Company",
                    tint = ManageUserColors.AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = company.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ManageUserColors.DarkGray
                    )
                    Text(
                        text = "${company.totalUsers} Total Users • ${company.activeUsers} Active",
                        style = MaterialTheme.typography.bodySmall,
                        color = ManageUserColors.MediumGray
                    )
                }
                Badge(
                    containerColor = ManageUserColors.AccentBlue
                ) {
                    Text(
                        text = "${company.departments.size} Depts",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        // Departments (shown when company is expanded)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 8.dp)
            ) {
                departments.forEach { department ->
                    DepartmentFolderItem(
                        company = company,
                        department = department,
                        isExpanded = expandedDepartments.contains("${company.sanitizedName}-${department.sanitizedName}"),
                        onToggle = { onDepartmentToggle(company.sanitizedName, department.sanitizedName) },
                        roles = getRolesForDepartment(company.sanitizedName, department.sanitizedName),
                        expandedRoles = expandedRoles,
                        onRoleToggle = onRoleToggle,
                        onUserClick = onUserClick,
                        onToggleUserStatus = onToggleUserStatus,
                        onDeleteUser = onDeleteUser,
                        getUsersForRole = getUsersForRole
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun DepartmentFolderItem(
    company: Company,
    department: Department,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    roles: List<RoleInfo>,
    expandedRoles: Set<String>,
    onRoleToggle: (String, String, String) -> Unit,
    onUserClick: (UserProfile) -> Unit,
    onToggleUserStatus: (UserProfile) -> Unit,
    onDeleteUser: (UserProfile) -> Unit,
    getUsersForRole: (String, String, String) -> List<UserProfile>
) {
    Column {
        // Department Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            colors = CardDefaults.cardColors(
                //containerColor = ManageUserColors.AccentGreen.copy(alpha = 0.1f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = ManageUserColors.AccentGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = "Department",
                    tint = ManageUserColors.AccentGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = department.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = ManageUserColors.DarkGray
                    )
                    Text(
                        text = "${department.userCount} Users • ${department.activeUsers} Active",
                        style = MaterialTheme.typography.bodySmall,
                        color = ManageUserColors.MediumGray
                    )
                }
                Badge(
                    containerColor = ManageUserColors.AccentGreen
                ) {
                    Text(
                        text = "${roles.size} Roles",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        // Roles (shown when department is expanded)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 8.dp)
            ) {
                roles.forEach { role ->
                    RoleFolderItem(
                        company = company,
                        department = department,
                        role = role,
                        isExpanded = expandedRoles.contains("${company.sanitizedName}-${department.sanitizedName}-${role.roleName}"),
                        onToggle = { onRoleToggle(company.sanitizedName, department.sanitizedName, role.roleName) },
                        users = getUsersForRole(company.sanitizedName, department.sanitizedName, role.roleName),
                        onUserClick = onUserClick,
                        onToggleUserStatus = onToggleUserStatus,
                        onDeleteUser = onDeleteUser
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun RoleFolderItem(
    company: Company,
    department: Department,
    role: RoleInfo,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    users: List<UserProfile>,
    onUserClick: (UserProfile) -> Unit,
    onToggleUserStatus: (UserProfile) -> Unit,
    onDeleteUser: (UserProfile) -> Unit
) {
    Column {
        // Role Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            colors = CardDefaults.cardColors(
                //containerColor = ManageUserColors.AccentPurple.copy(alpha = 0.1f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = ManageUserColors.AccentPurple,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "Role",
                    tint = ManageUserColors.AccentPurple,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = role.roleName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = ManageUserColors.DarkGray
                    )
                    Text(
                        text = "${role.userCount} Users • ${role.activeUsers} Active",
                        style = MaterialTheme.typography.bodySmall,
                        color = ManageUserColors.MediumGray
                    )
                }
                Badge(
                    containerColor = ManageUserColors.AccentPurple
                ) {
                    Text(
                        text = "${users.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        // Users (shown when role is expanded)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 8.dp)
            ) {
                if (users.isEmpty()) {
                    EmptyRoleUsersState(role.roleName)
                } else {
                    users.forEach { user ->
                        UserListItemCompact(
                            user = user,
                            onClick = { onUserClick(user) },
                            onToggleStatus = { onToggleUserStatus(user) },
                            onDelete = { onDeleteUser(user) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun UserListItemCompact(
    user: UserProfile,
    onClick: () -> Unit,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (user.isActive)
                ManageUserColors.CardBackground
            else
                ManageUserColors.LightGray.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ManageUserColors.LightGray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // User Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = ManageUserColors.DarkGray
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Badge(
                        containerColor = if (user.isActive) ManageUserColors.AccentGreen else ManageUserColors.AccentRed,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            text = if (user.isActive) "Active" else "Inactive",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = "${user.designation} • ${user.experience} yrs • ${user.activeProjects} active projects",
                    style = MaterialTheme.typography.bodySmall,
                    color = ManageUserColors.MediumGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (user.totalComplaints > 0) {
                    Text(
                        text = "${user.totalComplaints} issues reported",
                        style = MaterialTheme.typography.labelSmall,
                        color = ManageUserColors.AccentRed
                    )
                }
            }

            // Action Buttons
            Row {
                IconButton(
                    onClick = onToggleStatus,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (user.isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (user.isActive) "Deactivate" else "Activate",
                        tint = if (user.isActive) ManageUserColors.AccentOrange else ManageUserColors.AccentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete User",
                        tint = ManageUserColors.AccentRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRoleUsersState(roleName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            //containerColor = ManageUserColors.LightGray.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.PersonOff,
                contentDescription = null,
                tint = ManageUserColors.MediumGray,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No users in $roleName role",
                style = MaterialTheme.typography.bodySmall,
                color = ManageUserColors.MediumGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyCompaniesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Business,
            contentDescription = null,
            tint = ManageUserColors.MediumGray,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Companies Found",
            style = MaterialTheme.typography.titleMedium,
            color = ManageUserColors.MediumGray,
            textAlign = TextAlign.Center
        )

        Text(
            text = "No companies are available for management.",
            style = MaterialTheme.typography.bodyMedium,
            color = ManageUserColors.MediumGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
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
                    text = "Role: $currentUserRole • Folder View",
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
                .padding(16.dp)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ManageUserColors.CardBackground)
        ) {
            LazyColumn(
                modifier = Modifier.padding(24.dp)
            ) {
                item {
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
                }

                item {
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
                }

                item {
                    // Details Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.height(320.dp)
                    ) {
                        item {
                            DetailCard("Company", user.companyName, ManageUserColors.AccentBlue)
                        }
                        item {
                            DetailCard("Department", user.department, ManageUserColors.AccentGreen)
                        }
                        item {
                            DetailCard("Role", user.role, ManageUserColors.AccentPurple)
                        }
                        item {
                            DetailCard("Designation", user.designation, ManageUserColors.AccentOrange)
                        }
                        item {
                            DetailCard("Experience", "${user.experience} years", ManageUserColors.AccentTeal)
                        }
                        item {
                            DetailCard("Active Projects", user.activeProjects.toString(), ManageUserColors.AccentIndigo)
                        }
                        item {
                            DetailCard("Completed Projects", user.completedProjects.toString(), ManageUserColors.AccentGreen)
                        }
                        if (user.totalComplaints > 0) {
                            item {
                                DetailCard("Issues Reported", user.totalComplaints.toString(), ManageUserColors.AccentRed)
                            }
                        }
                        if (user.createdAt != null) {
                            item {
                                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                DetailCard("Joined", dateFormat.format(user.createdAt!!.toDate()), ManageUserColors.MediumGray)
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
                }

                item {
                    // Document Path Info
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = ManageUserColors.SurfaceLight
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Document Path",
                                style = MaterialTheme.typography.labelMedium,
                                color = ManageUserColors.MediumGray
                            )
                            Text(
                                text = user.documentPath.ifEmpty { "No document path available" },
                                style = MaterialTheme.typography.bodySmall,
                                color = ManageUserColors.DarkGray,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                item {
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = ManageUserColors.DarkGray,
                textAlign = TextAlign.Center,
                maxLines = 2,
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${user.companyName} • ${user.department} • ${user.role}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ManageUserColors.MediumGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This action cannot be undone and will remove all user data from the system.",
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

// Additional helper composables for better UX
@Composable
private fun StatisticsBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ManageUserColors.MediumGray
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            containerColor = Color.Transparent
        ),
        border = BorderStroke(1.dp, color)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}