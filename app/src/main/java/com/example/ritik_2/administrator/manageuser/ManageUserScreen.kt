package com.example.ritik_2.administrator.manageuser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.administrator.manageuser.models.*
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.data.model.Permissions
import com.example.ritik_2.profile.profilecompletion.ProfileCompletionActivity

// ── Navigation levels ─────────────────────────────────────────────────────────

private sealed class ExplorerLevel {
    object Companies                                   : ExplorerLevel()
    data class Departments(val company: MUCompany)     : ExplorerLevel()
    data class Roles(val company: MUCompany,
                     val dept   : MUDepartment)        : ExplorerLevel()
    data class Users(val company: MUCompany,
                     val dept   : MUDepartment,
                     val role   : MURoleInfo)          : ExplorerLevel()
}

private val ExplorerLevel.depth: Int get() = when (this) {
    is ExplorerLevel.Companies   -> 0
    is ExplorerLevel.Departments -> 1
    is ExplorerLevel.Roles       -> 2
    is ExplorerLevel.Users       -> 3
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUserScreen(vm: ManageUserViewModel) {
    val state        by vm.state.collectAsState()
    val snack         = remember { SnackbarHostState() }
    val context       = LocalContext.current
    var level        by remember { mutableStateOf<ExplorerLevel>(ExplorerLevel.Companies) }
    var deleteTarget by remember { mutableStateOf<MUUser?>(null) }
    var roleTarget   by remember { mutableStateOf<MUUser?>(null) }
    var showCreate   by remember { mutableStateOf(false) }  // DB admin create dialog

    BackHandler(level !is ExplorerLevel.Companies) {
        level = when (val l = level) {
            is ExplorerLevel.Departments -> ExplorerLevel.Companies
            is ExplorerLevel.Roles       -> ExplorerLevel.Departments(l.company)
            is ExplorerLevel.Users       -> ExplorerLevel.Roles(l.company, l.dept)
            else                         -> ExplorerLevel.Companies
        }
    }

    LaunchedEffect(state.successMsg) {
        state.successMsg?.let {
            snack.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.clearMessages()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snack.showSnackbar("⚠ $it", duration = SnackbarDuration.Short)
            vm.clearMessages()
        }
    }

    fun openProfile(user: MUUser) {
        context.startActivity(
            ProfileCompletionActivity.createIntent(
                context        = context,
                userId         = user.id,
                isEditMode     = true,
                targetUserRole = user.role,
                editorRole     = state.currentRole
            )
        )
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
                    if (level !is ExplorerLevel.Companies) {
                        IconButton(onClick = {
                            level = when (val l = level) {
                                is ExplorerLevel.Departments -> ExplorerLevel.Companies
                                is ExplorerLevel.Roles       -> ExplorerLevel.Departments(l.company)
                                is ExplorerLevel.Users       -> ExplorerLevel.Roles(l.company, l.dept)
                                else                         -> ExplorerLevel.Companies
                            }
                        }) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }

                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(0.18f)),
                        Alignment.Center
                    ) {
                        Icon(
                            when (level) {
                                is ExplorerLevel.Companies   -> Icons.Default.Business
                                is ExplorerLevel.Departments -> Icons.Default.AccountTree
                                is ExplorerLevel.Roles       -> Icons.Default.Group
                                is ExplorerLevel.Users       -> Icons.Default.Person
                            },
                            null, tint = Color.White, modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(Modifier.weight(1f)) {
                        val title = when (level) {
                            is ExplorerLevel.Companies   -> "Manage Users"
                            is ExplorerLevel.Departments -> (level as ExplorerLevel.Departments).company.originalName
                            is ExplorerLevel.Roles       -> (level as ExplorerLevel.Roles).dept.departmentName
                            is ExplorerLevel.Users       -> (level as ExplorerLevel.Users).role.roleName
                        }
                        val subtitle = when (level) {
                            is ExplorerLevel.Companies   ->
                                "${state.users.size} users · ${state.users.count { it.isActive }} active"
                            is ExplorerLevel.Departments -> {
                                val c = (level as ExplorerLevel.Departments).company
                                "${c.totalUsers} users · ${c.activeUsers} active"
                            }
                            is ExplorerLevel.Roles -> {
                                val d = (level as ExplorerLevel.Roles).dept
                                "${d.userCount} users across ${d.roles.size} roles"
                            }
                            is ExplorerLevel.Users -> {
                                val r = (level as ExplorerLevel.Users).role
                                "${r.userCount} users · ${r.activeUsers} active"
                            }
                        }
                        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, fontSize = 11.sp, color = Color.White.copy(0.75f))
                    }

                    // DB admin badge
                    if (state.isDbAdmin) {
                        Surface(color = Color(0xFFFFD700).copy(0.25f),
                            shape = RoundedCornerShape(8.dp)) {
                            Text("DB Admin",
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 9.sp, color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    // DB admin: global add user button
                    if (state.isDbAdmin) {
                        IconButton(onClick = { showCreate = true }) {
                            Icon(Icons.Default.PersonAdd, "Add User", tint = Color.White)
                        }
                    }

                    if (state.isLoading)
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp).padding(end = 4.dp),
                            color       = Color.White,
                            strokeWidth = 2.dp
                        )
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            OutlinedTextField(
                value         = state.searchQuery,
                onValueChange = vm::search,
                placeholder   = { Text("Search users…") },
                leadingIcon   = { Icon(Icons.Default.Search, null,
                    tint = MaterialTheme.colorScheme.primary) },
                trailingIcon  = {
                    if (state.searchQuery.isNotEmpty())
                        IconButton(onClick = { vm.search("") }) {
                            Icon(Icons.Default.Clear, null)
                        }
                },
                modifier   = Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                shape      = RoundedCornerShape(14.dp),
                singleLine = true
            )

            if (level !is ExplorerLevel.Companies) BreadcrumbBar(level) { level = it }

            when {
                state.isLoading && state.users.isEmpty() -> LoadingView()

                state.searchQuery.isNotBlank() -> SearchResultsView(
                    users          = state.filteredUsers,
                    currentRole    = state.currentRole,
                    isDbAdmin      = state.isDbAdmin,
                    onUserClick    = { openProfile(it) },
                    onEditUser     = { openProfile(it) },
                    onToggleStatus = { vm.toggleUserStatus(it) },
                    onChangeRole   = { roleTarget = it },
                    onDelete       = { deleteTarget = it }
                )

                else -> AnimatedContent(
                    targetState = level,
                    transitionSpec = {
                        val entering = targetState.depth > initialState.depth
                        (if (entering) slideInHorizontally { it } + fadeIn()
                        else slideInHorizontally { -it } + fadeIn()) togetherWith
                                (if (entering) slideOutHorizontally { -it } + fadeOut()
                                else slideOutHorizontally { it } + fadeOut())
                    },
                    label = "explorer"
                ) { cur ->
                    when (cur) {
                        is ExplorerLevel.Companies ->
                            CompaniesView(vm.getFilteredCompanies(), state.isLoading) {
                                level = ExplorerLevel.Departments(it)
                            }
                        is ExplorerLevel.Departments ->
                            DepartmentsView(cur.company,
                                vm.getDepts(cur.company.sanitizedName)) {
                                level = ExplorerLevel.Roles(cur.company, it)
                            }
                        is ExplorerLevel.Roles ->
                            RolesView(cur.company, cur.dept,
                                vm.getRoles(cur.company.sanitizedName,
                                    cur.dept.sanitizedName)) {
                                level = ExplorerLevel.Users(cur.company, cur.dept, it)
                            }
                        is ExplorerLevel.Users ->
                            UsersView(
                                users          = vm.getUsers(
                                    cur.company.sanitizedName,
                                    cur.dept.sanitizedName,
                                    cur.role.roleName
                                ),
                                currentRole    = state.currentRole,
                                isDbAdmin      = state.isDbAdmin,
                                isLoading      = state.isLoading,
                                onUserClick    = { openProfile(it) },
                                onEditUser     = { openProfile(it) },
                                onToggleStatus = { vm.toggleUserStatus(it) },
                                onChangeRole   = { roleTarget = it },
                                onDelete       = { deleteTarget = it }
                            )
                    }
                }
            }
        }
    }

    // ── Delete confirm dialog ─────────────────────────────────────────────────
    deleteTarget?.let { user ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon    = { Icon(Icons.Default.DeleteForever, null,
                tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Delete User?", fontWeight = FontWeight.Bold) },
            text    = {
                Column {
                    Text("Permanently delete:")
                    Spacer(Modifier.height(6.dp))
                    Text("👤  ${user.name}", fontWeight = FontWeight.SemiBold)
                    Text(user.email, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.isDbAdmin) {
                        Spacer(Modifier.height(4.dp))
                        Text("Company: ${user.originalCompany}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("This cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            },
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

    // ── Role change dialog ────────────────────────────────────────────────────
    roleTarget?.let { user ->
        RoleChangeDialog(
            user      = user,
            isDbAdmin = state.isDbAdmin,
            onDismiss = { roleTarget = null },
            onConfirm = { newRole ->
                vm.changeRole(user, newRole)
                roleTarget = null
            }
        )
    }

    // ── DB admin: create user dialog ──────────────────────────────────────────
    if (showCreate && state.isDbAdmin) {
        DbAdminCreateUserDialog(
            onDismiss = { showCreate = false },
            onCreate  = { name, email, pw, role, dept, desig, company ->
                vm.createUserDirect(name, email, pw, role, dept, desig, company)
                showCreate = false
            }
        )
    }
}

// ── Breadcrumb ────────────────────────────────────────────────────────────────

@Composable
private fun BreadcrumbBar(level: ExplorerLevel, onNavigate: (ExplorerLevel) -> Unit) {
    val crumbs: List<Pair<String, ExplorerLevel>> = buildList {
        add("Companies" to ExplorerLevel.Companies)
        when (level) {
            is ExplorerLevel.Departments -> add(level.company.originalName to level)
            is ExplorerLevel.Roles -> {
                add(level.company.originalName to ExplorerLevel.Departments(level.company))
                add(level.dept.departmentName  to level)
            }
            is ExplorerLevel.Users -> {
                add(level.company.originalName to ExplorerLevel.Departments(level.company))
                add(level.dept.departmentName  to ExplorerLevel.Roles(level.company, level.dept))
                add(level.role.roleName        to level)
            }
            else -> {}
        }
    }
    Row(
        Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        crumbs.forEachIndexed { i, (label, target) ->
            val isLast = i == crumbs.lastIndex
            Text(label,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color      = if (isLast) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier   = Modifier.clickable(enabled = !isLast) { onNavigate(target) })
            if (!isLast) Icon(Icons.Default.ChevronRight, null,
                Modifier.size(14.dp).padding(horizontal = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
        }
    }
}

// ── Companies / Departments / Roles folder views ──────────────────────────────

@Composable
private fun CompaniesView(
    companies: List<MUCompany>, isLoading: Boolean, onClick: (MUCompany) -> Unit
) {
    if (isLoading && companies.isEmpty()) { LoadingView(); return }
    if (companies.isEmpty()) { EmptyView(Icons.Default.Business, "No companies found"); return }
    LazyColumn(contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(companies, key = { it.sanitizedName }) { c ->
            FolderCard(Icons.Default.Business, Color(0xFF1565C0),
                c.originalName, "${c.totalUsers} users", "${c.activeUsers} active") { onClick(c) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DepartmentsView(
    company: MUCompany, depts: List<MUDepartment>, onClick: (MUDepartment) -> Unit
) {
    if (depts.isEmpty()) { EmptyView(Icons.Default.AccountTree, "No departments"); return }
    LazyColumn(contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader("Departments in ${company.originalName}",
            "${depts.size} dept${if (depts.size != 1) "s" else ""}") }
        items(depts, key = { it.sanitizedName }) { d ->
            FolderCard(Icons.Default.AccountTree, Color(0xFF2E7D32),
                d.departmentName, "${d.userCount} users", "${d.roles.size} roles") { onClick(d) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun RolesView(
    company: MUCompany, dept: MUDepartment,
    roles: List<MURoleInfo>, onClick: (MURoleInfo) -> Unit
) {
    if (roles.isEmpty()) { EmptyView(Icons.Default.Group, "No roles in this dept"); return }
    LazyColumn(contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader(dept.departmentName,
            "${roles.size} role${if (roles.size != 1) "s" else ""}") }
        items(roles, key = { it.roleName }) { r ->
            FolderCard(Icons.Default.Group, roleColor(r.roleName),
                r.roleName, "${r.userCount} users", "${r.activeUsers} active",
                badge = if (r.userCount > 0) r.userCount.toString() else null) { onClick(r) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Users views ───────────────────────────────────────────────────────────────

@Composable
private fun UsersView(
    users         : List<MUUser>,
    currentRole   : String,
    isDbAdmin     : Boolean,
    isLoading     : Boolean,
    onUserClick   : (MUUser) -> Unit,
    onEditUser    : (MUUser) -> Unit,
    onToggleStatus: (MUUser) -> Unit,
    onChangeRole  : (MUUser) -> Unit,
    onDelete      : (MUUser) -> Unit
) {
    if (users.isEmpty()) { EmptyView(Icons.Default.PersonSearch, "No users in this role"); return }
    LazyColumn(contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader("${users.size} user${if (users.size != 1) "s" else ""}",
            "${users.count { it.isActive }} active · ${users.count { !it.isActive }} inactive") }
        items(users, key = { it.id }) { user ->
            UserCard(user, canEdit(currentRole, user.role, isDbAdmin),
                isDbAdmin      = isDbAdmin,
                onUserClick    = { onUserClick(user) },
                onEditUser     = { onEditUser(user) },
                onToggleStatus = { onToggleStatus(user) },
                onChangeRole   = { onChangeRole(user) },
                onDelete       = { onDelete(user) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SearchResultsView(
    users         : List<MUUser>,
    currentRole   : String,
    isDbAdmin     : Boolean,
    onUserClick   : (MUUser) -> Unit,
    onEditUser    : (MUUser) -> Unit,
    onToggleStatus: (MUUser) -> Unit,
    onChangeRole  : (MUUser) -> Unit,
    onDelete      : (MUUser) -> Unit
) {
    if (users.isEmpty()) { EmptyView(Icons.Default.SearchOff, "No results"); return }
    LazyColumn(contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader("${users.size} result${if (users.size != 1) "s" else ""}",
            "across all departments") }
        items(users, key = { it.id }) { user ->
            UserCard(user, canEdit(currentRole, user.role, isDbAdmin),
                isDbAdmin      = isDbAdmin,
                showContext    = true,
                onUserClick    = { onUserClick(user) },
                onEditUser     = { onEditUser(user) },
                onToggleStatus = { onToggleStatus(user) },
                onChangeRole   = { onChangeRole(user) },
                onDelete       = { onDelete(user) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── User card ─────────────────────────────────────────────────────────────────

@Composable
private fun UserCard(
    user          : MUUser,
    canEdit       : Boolean,
    isDbAdmin     : Boolean,
    showContext   : Boolean = false,
    onUserClick   : () -> Unit,
    onEditUser    : () -> Unit,
    onToggleStatus: () -> Unit,
    onChangeRole  : () -> Unit,
    onDelete      : () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { onUserClick() },
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (user.isActive) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(0.6f))
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(roleColor(user.role).copy(0.15f)),
                Alignment.Center
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
                    Text(user.name.take(2).uppercase(), fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, color = roleColor(user.role))
                }
            }

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(user.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, false))
                    StatusBadge(user.isActive)
                }
                Text(user.email, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (user.designation.isNotBlank() || user.experience > 0)
                    Text(buildString {
                        if (user.designation.isNotBlank()) append(user.designation)
                        if (user.experience > 0) append(" · ${user.experience}y exp")
                        if (user.activeProjects > 0) append(" · ${user.activeProjects} proj")
                    }, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showContext)
                    Text("${user.originalCompany} › ${user.originalDept}",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.primary.copy(0.7f))
            }

            // Role pill
            RolePill(user.role)

            // Action menu
            Box {
                IconButton(onClick = { showMenu = true }, Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    // Edit profile — available if canEdit OR isDbAdmin
                    if (canEdit || isDbAdmin) {
                        DropdownMenuItem(
                            text        = { Text("Edit Profile") },
                            leadingIcon = { Icon(Icons.Default.Edit, null,
                                tint = Color(0xFF1976D2)) },
                            onClick     = { showMenu = false; onEditUser() }
                        )
                    }
                    // Activate / Deactivate
                    DropdownMenuItem(
                        text = { Text(if (user.isActive) "Deactivate" else "Activate") },
                        leadingIcon = {
                            Icon(if (user.isActive) Icons.Default.Pause
                            else Icons.Default.PlayArrow, null,
                                tint = if (user.isActive) Color(0xFFF57C00)
                                else Color(0xFF4CAF50))
                        },
                        onClick = { showMenu = false; onToggleStatus() }
                    )
                    // Change role — DB admin or Administrator
                    if (isDbAdmin || canEdit) {
                        DropdownMenuItem(
                            text        = { Text("Change Role") },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null,
                                tint = Color(0xFF7B1FA2)) },
                            onClick     = { showMenu = false; onChangeRole() }
                        )
                    }
                    HorizontalDivider()
                    // Delete — always available for DB admin
                    DropdownMenuItem(
                        text        = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.error) },
                        onClick     = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

// ── Role change dialog ────────────────────────────────────────────────────────

@Composable
private fun RoleChangeDialog(
    user     : MUUser,
    isDbAdmin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selected by remember { mutableStateOf(user.role) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.SwapHoriz, null,
            tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Change Role — ${user.name}", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Current: ${user.role}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                // DB admin can assign any role; others limited by PermissionGuard
                val availableRoles = if (isDbAdmin) Permissions.ALL_ROLES
                else Permissions.ALL_ROLES.filter { r ->
                    r in setOf("Employee", "Intern", "Team Lead")
                }
                availableRoles.forEach { role ->
                    val isCurrent = role == user.role
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selected = role }
                            .background(
                                if (selected == role)
                                    MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = selected == role,
                            onClick  = { selected = role }
                        )
                        RolePill(role)
                        if (isCurrent)
                            Text("current",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (selected != user.role) onConfirm(selected) else onDismiss() },
                enabled  = selected.isNotBlank()
            ) { Text("Confirm") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── DB admin create user dialog ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DbAdminCreateUserDialog(
    onDismiss: () -> Unit,
    onCreate : (name: String, email: String, password: String, role: String,
                department: String, designation: String, company: String) -> Unit
) {
    var name     by remember { mutableStateOf("") }
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role     by remember { mutableStateOf("Employee") }
    var dept     by remember { mutableStateOf("") }
    var desig    by remember { mutableStateOf("") }
    var company  by remember { mutableStateOf("") }
    var roleExp  by remember { mutableStateOf(false) }

    val isValid = name.isNotBlank() && email.isNotBlank() &&
            password.length >= 6 && dept.isNotBlank() &&
            desig.isNotBlank() && company.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.PersonAdd, null,
            tint = MaterialTheme.colorScheme.primary) },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Create User", fontWeight = FontWeight.Bold)
                Surface(color = Color(0xFFFFD700).copy(0.2f), shape = RoundedCornerShape(6.dp)) {
                    Text("DB Admin",
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 9.sp, color = Color(0xFFB8860B), fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogField(name,    { name    = it }, "Full Name",   Icons.Default.Person)
                DialogField(email,   { email   = it }, "Email",       Icons.Default.Email,   KeyboardType.Email)
                DialogField(password,{ password= it }, "Password",    Icons.Default.Lock,    KeyboardType.Password)
                DialogField(company, { company  = it }, "Company Name",Icons.Default.Business)
                DialogField(dept,    { dept     = it }, "Department",  Icons.Default.Groups)
                DialogField(desig,   { desig    = it }, "Designation", Icons.Default.Badge)

                // Role selector
                Text("Role", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                ExposedDropdownMenuBox(expanded = roleExp,
                    onExpandedChange = { roleExp = it }) {
                    @OptIn(ExperimentalMaterial3Api::class)
                    OutlinedTextField(
                        value = role, onValueChange = {}, readOnly = true,
                        label = { Text("Role") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(roleExp)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape    = RoundedCornerShape(10.dp)
                    )
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenu(expanded = roleExp,
                        onDismissRequest = { roleExp = false }) {
                        Permissions.ALL_ROLES.forEach { r ->
                            DropdownMenuItem(
                                text    = { Text(r) },
                                onClick = { role = r; roleExp = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    if (isValid) onCreate(name, email, password, role, dept, desig, company)
                },
                enabled  = isValid
            ) { Text("Create") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogField(
    value        : String,
    onValueChange: (String) -> Unit,
    label        : String,
    icon         : ImageVector,
    keyboardType : KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        label           = { Text(label) },
        leadingIcon     = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier        = Modifier.fillMaxWidth(),
        singleLine      = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType),
        shape           = RoundedCornerShape(10.dp)
    )
}

// ── Shared small composables ──────────────────────────────────────────────────

@Composable
private fun FolderCard(
    icon: ImageVector, iconColor: Color, title: String,
    meta1: String, meta2: String, badge: String? = null, onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                .background(iconColor.copy(0.12f)), Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniChip(meta1, iconColor)
                    MiniChip(meta2, iconColor.copy(0.7f))
                }
            }
            if (badge != null) {
                Box(Modifier.size(28.dp).clip(CircleShape)
                    .background(iconColor.copy(0.15f)), Alignment.Center) {
                    Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = iconColor)
                }
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }
    }
}

@Composable
private fun StatusBadge(isActive: Boolean) {
    Surface(
        color = if (isActive) Color(0xFF4CAF50).copy(0.15f) else Color(0xFFF44336).copy(0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(if (isActive) "Active" else "Inactive",
            Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            fontSize = 9.sp, fontWeight = FontWeight.Bold,
            color = if (isActive) Color(0xFF2E7D32) else Color(0xFFC62828))
    }
}

@Composable
private fun RolePill(role: String) {
    val color = roleColor(role)
    Surface(color = color.copy(0.15f), shape = RoundedCornerShape(20.dp)) {
        Text(role, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniChip(label: String, color: Color) {
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(6.dp)) {
        Text(label, Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF1565C0))
            Spacer(Modifier.height(12.dp))
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyView(icon: ImageVector, message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Delegates entirely to PermissionGuard — no hardcoded role strings here. */
private fun canEdit(editorRole: String, targetRole: String, isDbAdmin: Boolean): Boolean =
    PermissionGuard.canEditProfile(
        editorRole = editorRole,
        targetRole = targetRole,
        editorId   = "",   // editing other user
        targetId   = "other",
        isDbAdmin  = isDbAdmin
    )

private fun roleColor(role: String): Color = when (role) {
    "Administrator" -> Color(0xFFD32F2F)
    "Manager"       -> Color(0xFF1976D2)
    "HR"            -> Color(0xFF388E3C)
    "Team Lead"     -> Color(0xFFF57C00)
    "Employee"      -> Color(0xFF7B1FA2)
    "Intern"        -> Color(0xFF455A64)
    else            -> Color(0xFF00796B)
}