package com.example.ritik_2.administrator.departmentmanager

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
import com.example.ritik_2.data.model.Permissions

// ── Re-declare data classes here so they match this package ──────────────────
// (If DepartmentViewModel is also in departmentmanager package,
//  these are already defined there — remove duplicates accordingly)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartmentScreen(viewModel: DepartmentViewModel) {
    val state          by viewModel.state.collectAsState()
    val snack           = remember { SnackbarHostState() }
    var showCreate     by remember { mutableStateOf(false) }
    var deleteTarget   by remember { mutableStateOf<DeptInfo?>(null) }
    var moveUserTarget by remember { mutableStateOf<DeptUserInfo?>(null) }
    var moveMode       by remember { mutableStateOf<MoveMode?>(null) }

    BackHandler(state.selectedDept != null) { viewModel.selectDept(null) }

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
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )))                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.selectedDept != null) {
                        IconButton(onClick = { viewModel.selectDept(null) }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        val title = if (state.selectedDept == null) "Department Manager"
                        else state.departments.find { d -> d.sanitizedName == state.selectedDept }?.name ?: "Users"
                        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val sub = if (state.selectedDept == null) "${state.departments.size} departments"
                        else "${state.users.size} users"
                        Text(sub, fontSize = 11.sp, color = Color.White.copy(0.75f))
                    }
                    if (state.isOffline) {
                        Surface(color = Color.White.copy(0.15f), shape = RoundedCornerShape(8.dp)) {
                            Text("Offline",
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (state.selectedDept == null) {
                        IconButton(onClick = { showCreate = true }) {
                            Icon(Icons.Default.Add, "Add Department", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.selectedDept != null) {
                OutlinedTextField(
                    value         = state.searchQuery,
                    onValueChange = viewModel::search,
                    placeholder   = { Text("Search users in this department…") },
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
                targetState = state.selectedDept,
                transitionSpec = {
                    val entering = targetState != null
                    (if (entering) slideInHorizontally { it } + fadeIn()
                    else slideInHorizontally { -it } + fadeIn()) togetherWith
                            (if (entering) slideOutHorizontally { -it } + fadeOut()
                            else slideOutHorizontally { it } + fadeOut())
                },
                label = "deptNav"
            ) { selectedDept ->
                if (selectedDept == null) {
                    DepartmentsListView(
                        departments = state.departments,
                        isLoading   = state.isLoading,
                        onDeptClick = { dept -> viewModel.selectDept(dept.sanitizedName) },
                        onDelete    = { dept -> deleteTarget = dept }
                    )
                } else {
                    UsersInDeptView(
                        users            = state.users,
                        isLoading        = state.isLoading,
                        currentDeptName  = state.departments
                            .find { d -> d.sanitizedName == selectedDept }?.name ?: "",
                        onMoveUserToDept = { user -> moveUserTarget = user; moveMode = MoveMode.DEPT },
                        onMoveUserToRole = { user -> moveUserTarget = user; moveMode = MoveMode.ROLE }
                    )
                }
            }
        }
    }

    // Create dialog
    if (showCreate) {
        CreateDeptDialog(
            onDismiss = { showCreate = false },
            onCreate  = { name -> viewModel.createDepartment(name); showCreate = false }
        )
    }

    // Delete dialog
    deleteTarget?.let { dept ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon    = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Delete '${dept.name}'?") },
            text    = {
                if (dept.userCount > 0)
                    Text("${dept.userCount} users in this department. Move them first.",
                        color = MaterialTheme.colorScheme.error)
                else
                    Text("This department will be permanently removed.")
            },
            confirmButton = {
                Button(
                    onClick  = { viewModel.deleteDepartment(dept); deleteTarget = null },
                    enabled  = dept.userCount == 0,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Move user dialog
    moveUserTarget?.let { user ->
        when (moveMode) {
            MoveMode.DEPT -> MoveToDeptDialog(
                user        = user,
                departments = state.departments.filter { d -> d.sanitizedName != user.sanitizedDept },
                onDismiss   = { moveUserTarget = null; moveMode = null },
                onMove      = { dept -> viewModel.moveUserToDepartment(user, dept); moveUserTarget = null; moveMode = null }
            )
            MoveMode.ROLE -> MoveToRoleDialog(
                user      = user,
                onDismiss = { moveUserTarget = null; moveMode = null },
                onMove    = { role -> viewModel.moveUserToRole(user, role); moveUserTarget = null; moveMode = null }
            )
            null -> {}
        }
    }
}

private enum class MoveMode { DEPT, ROLE }

// ── Departments list ──────────────────────────────────────────────────────────

@Composable
private fun DepartmentsListView(
    departments : List<DeptInfo>,
    isLoading   : Boolean,
    onDeptClick : (DeptInfo) -> Unit,
    onDelete    : (DeptInfo) -> Unit
) {
    if (isLoading && departments.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (departments.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AccountTree, null, Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                Spacer(Modifier.height(12.dp))
                Text("No departments yet. Tap + to create one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items = departments, key = { dept -> dept.id }) { dept ->
            DeptCard(dept = dept, onClick = { onDeptClick(dept) }, onDelete = { onDelete(dept) })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DeptCard(dept: DeptInfo, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2E7D32).copy(0.12f)),
                Alignment.Center
            ) {
                Icon(Icons.Default.AccountTree, null,
                    tint = Color(0xFF2E7D32), modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(dept.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniChip("${dept.userCount} users",    Color(0xFF1976D2))
                    MiniChip("${dept.activeUsers} active", Color(0xFF2E7D32))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null,
                    tint     = MaterialTheme.colorScheme.error.copy(0.7f),
                    modifier = Modifier.size(20.dp))
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }
    }
}

// ── Users in department ───────────────────────────────────────────────────────

@Composable
private fun UsersInDeptView(
    users           : List<DeptUserInfo>,
    isLoading       : Boolean,
    currentDeptName : String,
    onMoveUserToDept: (DeptUserInfo) -> Unit,
    onMoveUserToRole: (DeptUserInfo) -> Unit
) {
    if (users.isEmpty() && !isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No users in $currentDeptName",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "${users.size} user${if (users.size != 1) "s" else ""} · ${users.count { u -> u.isActive }} active",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        items(items = users, key = { user -> user.id }) { user ->
            DeptUserCard(
                user             = user,
                onMoveUserToDept = { onMoveUserToDept(user) },
                onMoveUserToRole = { onMoveUserToRole(user) }
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun DeptUserCard(
    user            : DeptUserInfo,
    onMoveUserToDept: () -> Unit,
    onMoveUserToRole: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(
                        com.example.ritik_2.administrator.rolemanagement.roleColor(user.role).copy(0.15f)
                    ),
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
                    Text(
                        user.name.take(2).uppercase(),
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = com.example.ritik_2.administrator.rolemanagement.roleColor(user.role)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        user.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 14.sp,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, false)
                    )
                    Surface(
                        color = if (user.isActive) Color(0xFF4CAF50).copy(0.15f)
                        else Color(0xFFF44336).copy(0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            if (user.isActive) "Active" else "Inactive",
                            Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color      = if (user.isActive) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
                Text(user.email, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                com.example.ritik_2.administrator.rolemanagement.RolePill(
                    role  = user.role,
                    color = com.example.ritik_2.administrator.rolemanagement.roleColor(user.role)
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }, Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text        = { Text("Move to Department") },
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, null,
                            tint = Color(0xFF1976D2)) },
                        onClick     = { showMenu = false; onMoveUserToDept() }
                    )
                    DropdownMenuItem(
                        text        = { Text("Change Role") },
                        leadingIcon = { Icon(Icons.Default.SwapHoriz, null,
                            tint = Color(0xFFF57C00)) },
                        onClick     = { showMenu = false; onMoveUserToRole() }
                    )
                }
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun CreateDeptDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.CreateNewFolder, null,
            tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Create Department", fontWeight = FontWeight.Bold) },
        text  = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Department Name") },
                placeholder   = { Text("e.g. Engineering") },
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
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MoveToDeptDialog(
    user       : DeptUserInfo,
    departments: List<DeptInfo>,
    onDismiss  : () -> Unit,
    onMove     : (DeptInfo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${user.name}", fontWeight = FontWeight.Bold) },
        text  = {
            Column {
                Text("Select destination department:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(items = departments, key = { dept -> dept.id }) { dept ->
                        ListItem(
                            headlineContent   = { Text(dept.name) },
                            supportingContent = { Text("${dept.userCount} users") },
                            leadingContent    = {
                                Icon(Icons.Default.AccountTree, null, tint = Color(0xFF2E7D32))
                            },
                            modifier = Modifier.clickable { onMove(dept) }
                        )
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MoveToRoleDialog(
    user     : DeptUserInfo,
    onDismiss: () -> Unit,
    onMove   : (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Role for ${user.name}", fontWeight = FontWeight.Bold) },
        text  = {
            Column {
                Text("Current: ${user.role}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(items = Permissions.ALL_ROLES, key = { role -> role }) { role ->
                        val isCurrent = role == user.role
                        ListItem(
                            headlineContent = {
                                com.example.ritik_2.administrator.rolemanagement.RolePill(
                                    role  = role,
                                    color = com.example.ritik_2.administrator.rolemanagement.roleColor(role)
                                )
                            },
                            trailingContent = {
                                if (isCurrent)
                                    Icon(Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.clickable(enabled = !isCurrent) { onMove(role) }
                        )
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Helper ────────────────────────────────────────────────────────────────────

@Composable
private fun MiniChip(label: String, color: Color) {
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize   = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color      = color
        )
    }
}