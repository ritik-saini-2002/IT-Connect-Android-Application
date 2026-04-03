package com.example.ritik_2.administrator.manageuser

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.administrator.manageuser.models.MUCompany
import com.example.ritik_2.administrator.manageuser.models.MUDepartment
import com.example.ritik_2.administrator.manageuser.models.MURoleInfo
import com.example.ritik_2.administrator.manageuser.models.MUUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUserScreen(vm: ManageUserViewModel) {
    val state by vm.state.collectAsState()
    val snack  = remember { SnackbarHostState() }

    var deleteTarget by remember { mutableStateOf<MUUser?>(null) }
    var detailTarget by remember { mutableStateOf<MUUser?>(null) }

    LaunchedEffect(state.successMsg) {
        state.successMsg?.let { snack.showSnackbar(it); vm.clearMessages() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snack.showSnackbar("⚠ $it"); vm.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Manage Users", fontWeight = FontWeight.Bold)
                        Text(
                            "${state.currentRole} · Folder View",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->

        // ── Loading ───────────────────────────────────────────────────────────
        if (state.isLoading && state.companies.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        // ── Empty ─────────────────────────────────────────────────────────────
        if (state.companies.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Business, null,
                        Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No companies found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        // ── Tree ──────────────────────────────────────────────────────────────
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items = state.companies,
                key   = { company: MUCompany -> company.sanitizedName }
            ) { company: MUCompany ->
                CompanyNode(
                    company        = company,
                    isExpanded     = state.expandedCompanies.contains(company.sanitizedName),
                    onToggle       = { vm.toggleCompany(company.sanitizedName) },
                    departments    = vm.getDepts(company.sanitizedName),
                    expandedDepts  = state.expandedDepartments,
                    expandedRoles  = state.expandedRoles,
                    onDeptToggle   = { sd: String ->
                        vm.toggleDepartment(company.sanitizedName, sd)
                    },
                    onRoleToggle   = { sd: String, role: String ->
                        vm.toggleRole(company.sanitizedName, sd, role)
                    },
                    getRoles       = { sd: String ->
                        vm.getRoles(company.sanitizedName, sd)
                    },
                    getUsers       = { sd: String, role: String ->
                        vm.getUsers(company.sanitizedName, sd, role)
                    },
                    onUserClick    = { user: MUUser -> detailTarget = user },
                    onToggleStatus = { user: MUUser -> vm.toggleUserStatus(user) },
                    onDelete       = { user: MUUser -> deleteTarget = user }
                )
            }
        }
    }

    // ── Delete confirm dialog ─────────────────────────────────────────────────
    deleteTarget?.let { user: MUUser ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon    = {
                Icon(Icons.Default.DeleteForever, null,
                    tint = MaterialTheme.colorScheme.error)
            },
            title   = { Text("Delete User?", fontWeight = FontWeight.Bold) },
            text    = { Text("Permanently delete ${user.name}? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { vm.deleteUser(user); deleteTarget = null },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    // ── Detail dialog ─────────────────────────────────────────────────────────
    detailTarget?.let { user: MUUser ->
        UserDetailDialog(
            user          = user,
            onDismiss     = { detailTarget = null },
            onToggleStatus = {
                vm.toggleUserStatus(user)
                detailTarget = null
            }
        )
    }
}

// ── Company node ──────────────────────────────────────────────────────────────

@Composable
private fun CompanyNode(
    company        : MUCompany,
    isExpanded     : Boolean,
    onToggle       : () -> Unit,
    departments    : List<MUDepartment>,
    expandedDepts  : Set<String>,
    expandedRoles  : Set<String>,
    onDeptToggle   : (String) -> Unit,
    onRoleToggle   : (String, String) -> Unit,
    getRoles       : (String) -> List<MURoleInfo>,
    getUsers       : (String, String) -> List<MUUser>,
    onUserClick    : (MUUser) -> Unit,
    onToggleStatus : (MUUser) -> Unit,
    onDelete       : (MUUser) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = Color(0xFF2196F3)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Business, null,
                    tint = Color(0xFF2196F3), modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(company.originalName, fontWeight = FontWeight.Bold)
                    Text(
                        "${company.totalUsers} users · ${company.activeUsers} active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Badge(containerColor = Color(0xFF2196F3)) {
                    Text(
                        "${departments.size} depts",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            // Departments
            AnimatedVisibility(
                visible = isExpanded,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Column(
                    Modifier.padding(start = 18.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    departments.forEach { dept: MUDepartment ->
                        val deptKey = "${company.sanitizedName}|${dept.sanitizedName}"
                        DeptNode(
                            dept           = dept,
                            companyKey     = company.sanitizedName,
                            isExpanded     = expandedDepts.contains(deptKey),
                            onToggle       = { onDeptToggle(dept.sanitizedName) },
                            roles          = getRoles(dept.sanitizedName),
                            expandedRoles  = expandedRoles,
                            onRoleToggle   = { role: String ->
                                onRoleToggle(dept.sanitizedName, role)
                            },
                            getUsers       = { role: String ->
                                getUsers(dept.sanitizedName, role)
                            },
                            onUserClick    = onUserClick,
                            onToggleStatus = onToggleStatus,
                            onDelete       = onDelete
                        )
                    }
                }
            }
        }
    }
}

// ── Department node ───────────────────────────────────────────────────────────

@Composable
private fun DeptNode(
    dept           : MUDepartment,
    companyKey     : String,
    isExpanded     : Boolean,
    onToggle       : () -> Unit,
    roles          : List<MURoleInfo>,
    expandedRoles  : Set<String>,
    onRoleToggle   : (String) -> Unit,
    getUsers       : (String) -> List<MUUser>,
    onUserClick    : (MUUser) -> Unit,
    onToggleStatus : (MUUser) -> Unit,
    onDelete       : (MUUser) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint     = Color(0xFF4CAF50),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.AccountTree, null,
                    tint     = Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        dept.departmentName,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${dept.userCount} users",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Badge(containerColor = Color(0xFF4CAF50)) {
                    Text(
                        "${roles.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Column(
                    Modifier.padding(start = 16.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    roles.forEach { role: MURoleInfo ->
                        val roleKey = "$companyKey|${dept.sanitizedName}|${role.roleName}"
                        RoleNode(
                            role           = role,
                            isExpanded     = expandedRoles.contains(roleKey),
                            onToggle       = { onRoleToggle(role.roleName) },
                            users          = getUsers(role.roleName),
                            onUserClick    = onUserClick,
                            onToggleStatus = onToggleStatus,
                            onDelete       = onDelete
                        )
                    }
                }
            }
        }
    }
}

// ── Role node ─────────────────────────────────────────────────────────────────

@Composable
private fun RoleNode(
    role           : MURoleInfo,
    isExpanded     : Boolean,
    onToggle       : () -> Unit,
    users          : List<MUUser>,
    onUserClick    : (MUUser) -> Unit,
    onToggleStatus : (MUUser) -> Unit,
    onDelete       : (MUUser) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint     = Color(0xFF9C27B0),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Group, null,
                    tint     = Color(0xFF9C27B0),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    role.roleName,
                    modifier   = Modifier.weight(1f),
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Badge(containerColor = Color(0xFF9C27B0)) {
                    Text(
                        "${users.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Column(
                    Modifier.padding(start = 12.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (users.isEmpty()) {
                        Text(
                            "No users in this role",
                            Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        users.forEach { user: MUUser ->
                            UserRow(
                                user          = user,
                                onUserClick   = { onUserClick(user) },
                                onToggleStatus = { onToggleStatus(user) },
                                onDelete      = { onDelete(user) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── User row ──────────────────────────────────────────────────────────────────

@Composable
private fun UserRow(
    user          : MUUser,
    onUserClick   : () -> Unit,
    onToggleStatus : () -> Unit,
    onDelete      : () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable { onUserClick() },
        shape  = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (user.isActive)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (user.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(user.imageUrl).crossfade(true).build(),
                        contentDescription = "avatar",
                        modifier     = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        user.name.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        style      = MaterialTheme.typography.labelSmall,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Info
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.name,
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        color = if (user.isActive)
                            Color(0xFF4CAF50).copy(alpha = 0.15f)
                        else
                            Color(0xFFF44336).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            if (user.isActive) "Active" else "Inactive",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = if (user.isActive) Color(0xFF4CAF50)
                            else Color(0xFFF44336)
                        )
                    }
                }
                Text(
                    "${user.designation} · ${user.experience}y exp",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Actions
            IconButton(onClick = onToggleStatus, modifier = Modifier.size(30.dp)) {
                Icon(
                    if (user.isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint     = if (user.isActive) Color(0xFFF57C00) else Color(0xFF4CAF50)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Delete, null,
                    modifier = Modifier.size(16.dp),
                    tint     = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ── User detail dialog ────────────────────────────────────────────────────────

@Composable
private fun UserDetailDialog(
    user          : MUUser,
    onDismiss     : () -> Unit,
    onToggleStatus : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (user.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(user.imageUrl).crossfade(true).build(),
                            contentDescription = "avatar",
                            modifier     = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            user.name.take(2).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(user.name, fontWeight = FontWeight.Bold)
                    Text(
                        user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine("Designation",     user.designation)
                DetailLine("Role",            user.role)
                DetailLine("Phone",           user.phoneNumber)
                DetailLine("Experience",      "${user.experience} years")
                DetailLine("Active Projects", user.activeProjects.toString())
                DetailLine("Completed",       user.completedProjects.toString())
                if (user.totalComplaints > 0)
                    DetailLine("Issues", user.totalComplaints.toString(), isRed = true)
                DetailLine("Status", if (user.isActive) "Active" else "Inactive")
                DetailLine("Path", user.documentPath)
            }
        },
        confirmButton = {
            Button(
                onClick = onToggleStatus,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = if (user.isActive) Color(0xFFF57C00)
                    else Color(0xFF4CAF50)
                )
            ) { Text(if (user.isActive) "Deactivate" else "Activate") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String, isRed: Boolean = false) {
    if (value.isBlank()) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color      = if (isRed) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            textAlign  = TextAlign.End,
            modifier   = Modifier.weight(1f).padding(start = 8.dp)
        )
    }
}