package com.example.ritik_2.administrator.administratorpanel.databasemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ritik_2.data.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseManagerScreen(
    users: List<User>,
    complaints: List<Complaint>,
    companies: List<Company>,
    departments: List<Department>,
    currentTab: DatabaseTab,
    isLoading: Boolean,
    databaseMode: DatabaseMode,
    searchQuery: String,
    adminUserData: User?,
    onTabChanged: (DatabaseTab) -> Unit,
    onDatabaseModeChanged: (DatabaseMode) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSyncData: () -> Unit,
    onDeleteRecord: (String, RecordType) -> Unit,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    onClearLocalDatabase: () -> Unit,
    onRefreshData: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var showDeleteDialog by remember { mutableStateOf<Pair<String, RecordType>?>(null) }
    var showDetailDialog by remember { mutableStateOf<Any?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Database Manager",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    adminUserData?.let {
                        Text(
                            text = "${it.name} • ${it.companyName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            actions = {
                // Database Mode Toggle
                IconButton(
                    onClick = {
                        val newMode = if (databaseMode == DatabaseMode.FIREBASE) DatabaseMode.LOCAL else DatabaseMode.FIREBASE
                        onDatabaseModeChanged(newMode)
                    }
                ) {
                    Icon(
                        imageVector = if (databaseMode == DatabaseMode.FIREBASE) Icons.Filled.Cloud else Icons.Filled.Storage,
                        contentDescription = "Switch Database Mode",
                        tint = if (databaseMode == DatabaseMode.FIREBASE) Color(0xFF4285F4) else MaterialTheme.colorScheme.primary
                    )
                }

                // More options menu
                var showMenu by remember { mutableStateOf(false) }

                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options"
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sync Data") },
                        onClick = {
                            showMenu = false
                            onSyncData()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Sync, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export to Local") },
                        onClick = {
                            showMenu = false
                            onExportData()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Download, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Import from Local") },
                        onClick = {
                            showMenu = false
                            onImportData()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Upload, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Local DB") },
                        onClick = {
                            showMenu = false
                            onClearLocalDatabase()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        onClick = {
                            showMenu = false
                            onRefreshData()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Database Mode Indicator
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (databaseMode == DatabaseMode.FIREBASE)
                    Color(0xFF4285F4).copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (databaseMode == DatabaseMode.FIREBASE) Icons.Filled.Cloud else Icons.Filled.Storage,
                        contentDescription = null,
                        tint = if (databaseMode == DatabaseMode.FIREBASE) Color(0xFF4285F4) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Current Source: ${if (databaseMode == DatabaseMode.FIREBASE) "Firebase Cloud" else "Local Database"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        // Search Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Search ${currentTab.name.lowercase()}...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
        }

        // Tab Row
        if (isTablet) {
            // Horizontal tabs for tablets
            ScrollableTabRow(
                selectedTabIndex = currentTab.ordinal,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                DatabaseTab.values().forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = { onTabChanged(tab) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = getTabIcon(tab),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${tab.name} (${getTabCount(tab, users, complaints, companies, departments)})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    )
                }
            }
        } else {
            // Vertical navigation for phones
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DatabaseTab.values()) { tab ->
                    FilterChip(
                        onClick = { onTabChanged(tab) },
                        label = {
                            Text(
                                text = "${tab.name} (${getTabCount(tab, users, complaints, companies, departments)})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        selected = currentTab == tab,
                        leadingIcon = {
                            Icon(
                                imageVector = getTabIcon(tab),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when (currentTab) {
                DatabaseTab.USERS -> UsersList(
                    users = users,
                    isLoading = isLoading,
                    onDeleteUser = { userId -> onDeleteRecord(userId, RecordType.USER) },
                    onShowDetails = { user -> showDetailDialog = user },
                    isTablet = isTablet
                )
                DatabaseTab.COMPLAINTS -> ComplaintsList(
                    complaints = complaints,
                    isLoading = isLoading,
                    onDeleteComplaint = { complaintId -> onDeleteRecord(complaintId, RecordType.COMPLAINT) },
                    onShowDetails = { complaint -> showDetailDialog = complaint },
                    isTablet = isTablet
                )
                DatabaseTab.COMPANIES -> CompaniesList(
                    companies = companies,
                    isLoading = isLoading,
                    onDeleteCompany = { companyId -> onDeleteRecord(companyId, RecordType.COMPANY) },
                    onShowDetails = { company -> showDetailDialog = company },
                    isTablet = isTablet
                )
                DatabaseTab.DEPARTMENTS -> DepartmentsList(
                    departments = departments,
                    isLoading = isLoading,
                    onDeleteDepartment = { departmentId -> onDeleteRecord(departmentId, RecordType.DEPARTMENT) },
                    onShowDetails = { department -> showDetailDialog = department },
                    isTablet = isTablet
                )
            }

            if (isLoading && (users.isEmpty() && complaints.isEmpty() && companies.isEmpty() && departments.isEmpty())) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading ${currentTab.name.lowercase()}...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { (recordId, recordType) ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete this ${recordType.name.lowercase()}? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecord(recordId, recordType)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Detail Dialog
    showDetailDialog?.let { item ->
        Dialog(onDismissRequest = { showDetailDialog = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                when (item) {
                    is User -> UserDetailContent(user = item)
                    is Complaint -> ComplaintDetailContent(complaint = item)
                    is Company -> CompanyDetailContent(company = item)
                    is Department -> DepartmentDetailContent(department = item)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showDetailDialog = null }) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun UsersList(
    users: List<User>,
    isLoading: Boolean,
    onDeleteUser: (String) -> Unit,
    onShowDetails: (User) -> Unit,
    isTablet: Boolean
) {
    if (users.isEmpty() && !isLoading) {
        EmptyState(
            icon = Icons.Outlined.Person,
            title = "No Users Found",
            description = "No users match your current search criteria."
        )
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(users, key = { it.userId }) { user ->
            UserCard(
                user = user,
                onDelete = { onDeleteUser(user.userId) },
                onShowDetails = { onShowDetails(user) },
                isTablet = isTablet
            )
        }
    }
}

@Composable
fun UserCard(
    user: User,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit,
    isTablet: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowDetails() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar
            Box(
                modifier = Modifier
                    .size(if (isTablet) 56.dp else 48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.take(2).uppercase(),
                    style = if (isTablet) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // User Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Chip(
                        label = user.role,
                        color = getRoleColor(user.role)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = user.department,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Status Indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (user.isActive) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Delete Button
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete User",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ComplaintsList(
    complaints: List<Complaint>,
    isLoading: Boolean,
    onDeleteComplaint: (String) -> Unit,
    onShowDetails: (Complaint) -> Unit,
    isTablet: Boolean
) {
    if (complaints.isEmpty() && !isLoading) {
        EmptyState(
            icon = Icons.Outlined.ReportProblem,
            title = "No Complaints Found",
            description = "No complaints match your current search criteria."
        )
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(complaints, key = { it.complaintId }) { complaint ->
            ComplaintCard(
                complaint = complaint,
                onDelete = { onDeleteComplaint(complaint.complaintId) },
                onShowDetails = { onShowDetails(complaint) },
                isTablet = isTablet
            )
        }
    }
}

@Composable
fun ComplaintCard(
    complaint: Complaint,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit,
    isTablet: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowDetails() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = complaint.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "By: ${complaint.createdByName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatDate(complaint.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Complaint",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Chip(
                    label = complaint.department,
                    color = MaterialTheme.colorScheme.secondaryContainer
                )
                Chip(
                    label = complaint.urgency,
                    color = getUrgencyColor(complaint.urgency)
                )
                Chip(
                    label = complaint.status,
                    color = getStatusColor(complaint.status)
                )
                if (complaint.isGlobal) {
                    Chip(
                        label = "Global",
                        color = Color(0xFF9C27B0).copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
fun CompaniesList(
    companies: List<Company>,
    isLoading: Boolean,
    onDeleteCompany: (String) -> Unit,
    onShowDetails: (Company) -> Unit,
    isTablet: Boolean
) {
    if (companies.isEmpty() && !isLoading) {
        EmptyState(
            icon = Icons.Outlined.Business,
            title = "No Companies Found",
            description = "No companies match your current search criteria."
        )
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(companies, key = { it.sanitizedName }) { company ->
            CompanyCard(
                company = company,
                onDelete = { onDeleteCompany(company.sanitizedName) },
                onShowDetails = { onShowDetails(company) },
                isTablet = isTablet
            )
        }
    }
}

@Composable
fun CompanyCard(
    company: Company,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit,
    isTablet: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowDetails() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = company.originalName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Created: ${formatDate(company.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Company",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Users",
                    value = company.activeUsers.toString(),
                    icon = Icons.Default.Person
                )
                StatItem(
                    label = "Complaints",
                    value = company.openComplaints.toString(),
                    icon = Icons.Default.ReportProblem
                )
                StatItem(
                    label = "Departments",
                    value = company.departments.split(",").filter { it.isNotEmpty() }.size.toString(),
                    icon = Icons.Default.Business
                )
            }
        }
    }
}

@Composable
fun DepartmentsList(
    departments: List<Department>,
    isLoading: Boolean,
    onDeleteDepartment: (String) -> Unit,
    onShowDetails: (Department) -> Unit,
    isTablet: Boolean
) {
    if (departments.isEmpty() && !isLoading) {
        EmptyState(
            icon = Icons.Outlined.AccountTree,
            title = "No Departments Found",
            description = "No departments match your current search criteria."
        )
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(departments, key = { it.id }) { department ->
            DepartmentCard(
                department = department,
                onDelete = { onDeleteDepartment(department.id) },
                onShowDetails = { onShowDetails(department) },
                isTablet = isTablet
            )
        }
    }
}

@Composable
fun DepartmentCard(
    department: Department,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit,
    isTablet: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowDetails() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Department Icon
            Box(
                modifier = Modifier
                    .size(if (isTablet) 48.dp else 40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getDepartmentIcon(department.departmentName),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(if (isTablet) 24.dp else 20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Department Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = department.departmentName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${department.activeUsers} active users",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (department.openComplaints > 0) {
                    Text(
                        text = "${department.openComplaints} open complaints",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF44336)
                    )
                }
            }

            // Delete Button
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Department",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Detail Content Composables
@Composable
fun UserDetailContent(user: User) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "User Details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        DetailRow("Name", user.name)
        DetailRow("Email", user.email)
        DetailRow("Role", user.role)
        DetailRow("Company", user.companyName)
        DetailRow("Department", user.department)
        DetailRow("Designation", user.designation)
        DetailRow("Status", if (user.isActive) "Active" else "Inactive")
        DetailRow("Created", formatDate(user.createdAt))
        DetailRow("Last Updated", formatDate(user.lastUpdated))
        if (user.lastLogin != null) {
            DetailRow("Last Login", formatDate(user.lastLogin))
        }
    }
}

@Composable
fun ComplaintDetailContent(complaint: Complaint) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Complaint Details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        DetailRow("Title", complaint.title)
        DetailRow("Description", complaint.description)
        DetailRow("Department", complaint.department)
        DetailRow("Urgency", complaint.urgency)
        DetailRow("Status", complaint.status)
        DetailRow("Created By", complaint.createdByName)
        DetailRow("Contact", complaint.contactInfo)
        DetailRow("Global", if (complaint.isGlobal) "Yes" else "No")
        DetailRow("Has Attachment", if (complaint.hasAttachment) "Yes" else "No")
        DetailRow("Priority", complaint.priority.toString())
        DetailRow("Created", formatDate(complaint.createdAt))
        DetailRow("Updated", formatDate(complaint.updatedAt))
    }
}

@Composable
fun CompanyDetailContent(company: Company) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Company Details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        DetailRow("Name", company.originalName)
        DetailRow("Total Users", company.totalUsers.toString())
        DetailRow("Active Users", company.activeUsers.toString())
        DetailRow("Total Complaints", company.totalComplaints.toString())
        DetailRow("Open Complaints", company.openComplaints.toString())
        DetailRow("Departments", company.departments.replace(",", ", "))
        DetailRow("Available Roles", company.availableRoles.replace(",", ", "))
        DetailRow("Created", formatDate(company.createdAt))
        DetailRow("Last Updated", formatDate(company.lastUpdated))
    }
}

@Composable
fun DepartmentDetailContent(department: Department) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Department Details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        DetailRow("Name", department.departmentName)
        DetailRow("Company", department.companyName)
        DetailRow("User Count", department.userCount.toString())
        DetailRow("Active Users", department.activeUsers.toString())
        DetailRow("Total Complaints", department.totalComplaints.toString())
        DetailRow("Open Complaints", department.openComplaints.toString())
        DetailRow("Available Roles", department.availableRoles.replace(",", ", "))
        DetailRow("Created", formatDate(department.createdAt))
        DetailRow("Last Updated", formatDate(department.lastUpdated))
    }
}

// Utility Composables
@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun Chip(
    label: String,
    color: Color
) {
    Surface(
        color = color,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(0.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

// Utility Functions
fun getTabIcon(tab: DatabaseTab): ImageVector {
    return when (tab) {
        DatabaseTab.USERS -> Icons.Default.People
        DatabaseTab.COMPLAINTS -> Icons.Default.ReportProblem
        DatabaseTab.COMPANIES -> Icons.Default.Business
        DatabaseTab.DEPARTMENTS -> Icons.Default.AccountTree
    }
}

fun getTabCount(
    tab: DatabaseTab,
    users: List<User>,
    complaints: List<Complaint>,
    companies: List<Company>,
    departments: List<Department>
): Int {
    return when (tab) {
        DatabaseTab.USERS -> users.size
        DatabaseTab.COMPLAINTS -> complaints.size
        DatabaseTab.COMPANIES -> companies.size
        DatabaseTab.DEPARTMENTS -> departments.size
    }
}

fun getRoleColor(role: String): Color {
    return when (role) {
        "Administrator" -> Color(0xFFD32F2F).copy(alpha = 0.2f)
        "Manager" -> Color(0xFF1976D2).copy(alpha = 0.2f)
        "HR" -> Color(0xFF388E3C).copy(alpha = 0.2f)
        "Team Lead" -> Color(0xFFF57C00).copy(alpha = 0.2f)
        "Employee" -> Color(0xFF7B1FA2).copy(alpha = 0.2f)
        "Intern" -> Color(0xFF455A64).copy(alpha = 0.2f)
        else -> Color(0xFF616161).copy(alpha = 0.2f)
    }
}

fun getUrgencyColor(urgency: String): Color {
    return when (urgency) {
        "Critical" -> Color(0xFFD32F2F).copy(alpha = 0.2f)
        "High" -> Color(0xFFF57C00).copy(alpha = 0.2f)
        "Medium" -> Color(0xFFFBC02D).copy(alpha = 0.2f)
        "Low" -> Color(0xFF388E3C).copy(alpha = 0.2f)
        else -> Color(0xFF616161).copy(alpha = 0.2f)
    }
}

fun getStatusColor(status: String): Color {
    return when (status.lowercase()) {
        "open" -> Color(0xFF1976D2).copy(alpha = 0.2f)
        "in progress" -> Color(0xFFF57C00).copy(alpha = 0.2f)
        "resolved" -> Color(0xFF388E3C).copy(alpha = 0.2f)
        "closed" -> Color(0xFF616161).copy(alpha = 0.2f)
        else -> Color(0xFF7B1FA2).copy(alpha = 0.2f)
    }
}

fun getDepartmentIcon(departmentName: String): ImageVector {
    return when (departmentName.lowercase()) {
        "it", "technical", "it support" -> Icons.Default.Computer
        "hr", "human resources" -> Icons.Default.People
        "finance", "accounting" -> Icons.Default.AccountBalance
        "administration", "admin" -> Icons.Default.AdminPanelSettings
        "marketing" -> Icons.Default.Campaign
        "sales" -> Icons.Default.TrendingUp
        "operations" -> Icons.Default.Settings
        "legal" -> Icons.Default.Gavel
        "research", "r&d" -> Icons.Default.Science
        else -> Icons.Default.Business
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}