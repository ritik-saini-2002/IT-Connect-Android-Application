package com.example.ritik_2.administrator.rolemanagement

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.data.model.UserProfile

// ── Screen level ──────────────────────────────────────────────────────────────
private sealed class RoleLevel {
    object Roles                              : RoleLevel()
    data class Users(val role: RoleInfo)      : RoleLevel()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleManagementScreen(
    viewModel    : RoleManagementViewModel,
    onRoleChanged: (String, String, String) -> Unit
) {
    val state          by viewModel.state.collectAsState()
    val snack           = remember { SnackbarHostState() }
    var level          by remember { mutableStateOf<RoleLevel>(RoleLevel.Roles) }
    var showCreate     by remember { mutableStateOf(false) }
    var deleteTarget   by remember { mutableStateOf<RoleInfo?>(null) }
    var confirmDialog  by remember { mutableStateOf<Triple<UserProfile, String, String>?>(null) }

    BackHandler(level is RoleLevel.Users) { level = RoleLevel.Roles }

    LaunchedEffect(state.successMsg) {
        state.successMsg?.let {
            snack.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snack.showSnackbar("⚠ $it", duration = SnackbarDuration.Short)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (level is RoleLevel.Users) {
                        IconButton(onClick = { level = RoleLevel.Roles }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }

                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(0.18f)),
                        Alignment.Center
                    ) {
                        Icon(
                            if (level is RoleLevel.Users) Icons.Default.People
                            else Icons.Default.AdminPanelSettings,
                            null, tint = Color.White, modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(Modifier.weight(1f)) {
                        val title = when (val l = level) {
                            is RoleLevel.Roles -> "Role Management"
                            is RoleLevel.Users -> l.role.name
                        }
                        val subtitle = when (val l = level) {
                            is RoleLevel.Roles ->
                                "${state.roles.size} roles · ${state.users.size} users"
                            is RoleLevel.Users ->
                                "${l.role.userCount} user${if (l.role.userCount != 1) "s" else ""}"
                        }
                        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, fontSize = 11.sp, color = Color.White.copy(0.75f))
                    }

                    if (state.isOffline)
                        Surface(color = Color.White.copy(0.15f), shape = RoundedCornerShape(8.dp)) {
                            Text("Offline",
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                    if (level is RoleLevel.Roles) {
                        IconButton(onClick = { showCreate = true }) {
                            Icon(Icons.Default.Add, "Create Role", tint = Color.White)
                        }
                    }

                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Search bar (shown when viewing users in a role)
            if (level is RoleLevel.Users) {
                OutlinedTextField(
                    value         = state.searchQuery,
                    onValueChange = viewModel::search,
                    placeholder   = { Text("Search users in this role…") },
                    leadingIcon   = { Icon(Icons.Default.Search, null,
                        tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon  = {
                        if (state.searchQuery.isNotEmpty())
                            IconButton(onClick = { viewModel.search("") }) {
                                Icon(Icons.Default.Clear, null)
                            }
                    },
                    modifier   = Modifier.fillMaxWidth().padding(12.dp),
                    singleLine = true,
                    shape      = RoundedCornerShape(12.dp)
                )
            }

            AnimatedContent(
                targetState = level,
                transitionSpec = {
                    val entering = targetState is RoleLevel.Users
                    (if (entering) slideInHorizontally { it } + fadeIn()
                    else slideInHorizontally { -it } + fadeIn()) togetherWith
                            (if (entering) slideOutHorizontally { -it } + fadeOut()
                            else slideOutHorizontally { it } + fadeOut())
                },
                label = "roleNav"
            ) { cur ->
                when (cur) {
                    is RoleLevel.Roles ->
                        RolesListView(
                            roles     = state.roles,
                            isLoading = state.isLoading,
                            onClick   = { role -> level = RoleLevel.Users(role) },
                            onDelete  = { role -> deleteTarget = role }
                        )
                    is RoleLevel.Users ->
                        UsersInRoleView(
                            users          = state.filteredUsers.filter { it.role == cur.role.name },
                            isLoading      = state.isLoading,
                            availableRoles = state.roles.map { it.name },
                            roleName       = cur.role.name,
                            onRoleSelected = { user, newRole ->
                                confirmDialog = Triple(user, user.role, newRole)
                            }
                        )
                }
            }
        }
    }

    // Create role dialog
    if (showCreate) {
        CreateRoleDialog(
            onDismiss = { showCreate = false },
            onCreate  = { name -> viewModel.createRole(name); showCreate = false }
        )
    }

    // Delete role confirm
    deleteTarget?.let { role ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon  = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete '${role.name}'?", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (role.isBuiltIn && !role.isCustom)
                        Text("This is a built-in role and cannot be deleted.",
                            color = MaterialTheme.colorScheme.error)
                    else if (role.userCount > 0)
                        Text("${role.userCount} users have this role. Move them first.",
                            color = MaterialTheme.colorScheme.error)
                    else
                        Text("This action cannot be undone.")
                }
            },
            confirmButton = {
                Button(
                    onClick  = { viewModel.deleteRole(role); deleteTarget = null },
                    enabled  = !role.isBuiltIn && role.userCount == 0,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Confirm role change
    confirmDialog?.let { (user, oldRole, newRole) ->
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            icon  = { Icon(Icons.Default.SwapHoriz, null,
                tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Change Role?", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("👤  ${user.name}", fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RolePill(oldRole, roleColor(oldRole))
                        Icon(Icons.Default.ArrowForward, null,
                            modifier = Modifier.size(14.dp))
                        RolePill(newRole, roleColor(newRole))
                    }
                    Text("Permissions will be updated automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.changeUserRole(user, newRole, onRoleChanged)
                    confirmDialog = null
                }) { Text("Confirm") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDialog = null }) { Text("Cancel") }
            }
        )
    }
}

// ── Roles list view ───────────────────────────────────────────────────────────

@Composable
private fun RolesListView(
    roles    : List<RoleInfo>,
    isLoading: Boolean,
    onClick  : (RoleInfo) -> Unit,
    onDelete : (RoleInfo) -> Unit
) {
    if (isLoading && roles.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (roles.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                Spacer(Modifier.height(12.dp))
                Text("No roles yet. Tap + to create one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("${roles.size} role${if (roles.size != 1) "s" else ""}",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp))
        }
        items(items = roles, key = { r -> r.id }) { role ->
            RoleCard(role = role,
                onClick  = { onClick(role) },
                onDelete = { onDelete(role) })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun RoleCard(role: RoleInfo, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(roleColor(role.name).copy(0.12f)),
                Alignment.Center
            ) {
                Icon(Icons.Default.AdminPanelSettings, null,
                    tint = roleColor(role.name), modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(role.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (role.isCustom)
                        RoleTag("Custom", Color(0xFF1976D2))
                    if (role.isBuiltIn && !role.isCustom)
                        RoleTag("Built-in", Color(0xFF388E3C))
                }
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniChip("${role.userCount} user${if (role.userCount != 1) "s" else ""}",
                        roleColor(role.name))
                }
            }
            IconButton(
                onClick  = onDelete,
                enabled  = !role.isBuiltIn || role.isCustom
            ) {
                Icon(Icons.Default.Delete, null,
                    tint = if (!role.isBuiltIn || role.isCustom)
                        MaterialTheme.colorScheme.error.copy(0.7f)
                    else MaterialTheme.colorScheme.onSurface.copy(0.2f),
                    modifier = Modifier.size(20.dp))
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }
    }
}

// ── Users in role view ────────────────────────────────────────────────────────

@Composable
private fun UsersInRoleView(
    users         : List<UserProfile>,
    isLoading     : Boolean,
    availableRoles: List<String>,
    roleName      : String,
    onRoleSelected: (UserProfile, String) -> Unit
) {
    if (users.isEmpty() && !isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PersonSearch, null, Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                Spacer(Modifier.height(12.dp))
                Text("No users with role '$roleName'",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("${users.size} user${if (users.size != 1) "s" else ""}",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp))
        }
        items(items = users, key = { u -> u.id }) { user ->
            UserRoleCard(
                user           = user,
                availableRoles = availableRoles,
                isUpdating     = isLoading,
                onRoleSelected = { newRole -> onRoleSelected(user, newRole) }
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── User role card ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserRoleCard(
    user          : UserProfile,
    availableRoles: List<String>,
    isUpdating    : Boolean,
    onRoleSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            // User info row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(roleColor(user.role).copy(0.15f)),
                    Alignment.Center
                ) {
                    if (user.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model              = ImageRequest.Builder(LocalContext.current)
                                .data(user.imageUrl).crossfade(true).build(),
                            contentDescription = "avatar",
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop
                        )
                    } else {
                        Text(user.name.take(2).uppercase(), fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, color = roleColor(user.role))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.name, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(user.email, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (user.designation.isNotBlank())
                        Text(user.designation,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.65f))
                }
                Spacer(Modifier.width(8.dp))
                RolePill(user.role, roleColor(user.role))
            }

            Spacer(Modifier.height(10.dp))

            // Role change dropdown
            ExposedDropdownMenuBox(
                expanded         = expanded && !isUpdating,
                onExpandedChange = { if (!isUpdating) expanded = it }
            ) {
                OutlinedTextField(
                    value         = "Change role…",
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Select new role") },
                    trailingIcon  = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    enabled  = !isUpdating,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary)
                )
                ExposedDropdownMenu(
                    expanded         = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableRoles.forEach { role ->
                        val isCurrent = role == user.role
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RolePill(role, roleColor(role))
                                    if (isCurrent)
                                        Text("current",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = { expanded = false; if (!isCurrent) onRoleSelected(role) },
                            enabled = !isCurrent,
                            trailingIcon = {
                                if (isCurrent) Icon(Icons.Default.Check, null,
                                    Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun CreateRoleDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Create New Role", fontWeight = FontWeight.Bold) },
        text  = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Role Name") },
                placeholder   = { Text("e.g. Senior Developer") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick  = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled  = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
fun RolePill(role: String, color: Color) {
    Surface(color = color.copy(0.15f), shape = RoundedCornerShape(20.dp)) {
        Text(role,
            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoleTag(label: String, color: Color) {
    Surface(color = color.copy(0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(label,
            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            fontSize   = 9.sp,
            color      = color,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniChip(label: String, color: Color) {
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(6.dp)) {
        Text(label,
            modifier   = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize   = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color      = color)
    }
}

fun roleColor(role: String): Color = when (role) {
    "Administrator" -> Color(0xFFD32F2F)
    "Manager"       -> Color(0xFF1976D2)
    "HR"            -> Color(0xFF388E3C)
    "Team Lead"     -> Color(0xFFF57C00)
    "Employee"      -> Color(0xFF7B1FA2)
    "Intern"        -> Color(0xFF455A64)
    else            -> Color(0xFF00796B)
}