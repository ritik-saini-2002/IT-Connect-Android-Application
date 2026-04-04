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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.administrator.manageuser.models.*
import com.example.ritik_2.profile.profilecompletion.ProfileCompletionActivity

private sealed class ExplorerLevel {
    object Companies                                        : ExplorerLevel()
    data class Departments(val company: MUCompany)          : ExplorerLevel()
    data class Roles(val company: MUCompany,
                     val dept: MUDepartment)                : ExplorerLevel()
    data class Users(val company: MUCompany,
                     val dept: MUDepartment,
                     val role: MURoleInfo)                  : ExplorerLevel()
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
            snack.showSnackbar(it, duration = SnackbarDuration.Short); vm.clearMessages()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snack.showSnackbar("⚠ $it", duration = SnackbarDuration.Short); vm.clearMessages()
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
            // ── Unified gradient top bar matching DepartmentScreen / others ──
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
                        }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }

                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
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
                placeholder   = { Text("Search across all users…") },
                leadingIcon   = { Icon(Icons.Default.Search, null,
                    tint = MaterialTheme.colorScheme.primary) },
                trailingIcon  = {
                    if (state.searchQuery.isNotEmpty())
                        IconButton(onClick = { vm.search("") }) {
                            Icon(Icons.Default.Clear, null)
                        }
                },
                modifier   = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                shape      = RoundedCornerShape(14.dp),
                singleLine = true,
                colors     = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                )
            )

            if (level !is ExplorerLevel.Companies) BreadcrumbBar(level) { level = it }

            when {
                state.isLoading && state.users.isEmpty() -> LoadingView()
                state.searchQuery.isNotBlank() -> SearchResultsView(
                    users          = state.filteredUsers,
                    currentRole    = state.currentRole,
                    onUserClick    = { openProfile(it) },
                    onEditUser     = { openProfile(it) },
                    onToggleStatus = { vm.toggleUserStatus(it) },
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
                                isLoading      = state.isLoading,
                                onUserClick    = { openProfile(it) },
                                onEditUser     = { openProfile(it) },
                                onToggleStatus = { vm.toggleUserStatus(it) },
                                onDelete       = { deleteTarget = it }
                            )
                    }
                }
            }
        }
    }

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
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        crumbs.forEachIndexed { i, (label, target) ->
            val isLast = i == crumbs.lastIndex
            Text(
                label,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color      = if (isLast) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier   = Modifier.clickable(enabled = !isLast) { onNavigate(target) }
            )
            if (!isLast) Icon(Icons.Default.ChevronRight, null,
                Modifier.size(14.dp).padding(horizontal = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
        }
    }
}

// ── Companies view ────────────────────────────────────────────────────────────

@Composable
private fun CompaniesView(
    companies: List<MUCompany>, isLoading: Boolean, onClick: (MUCompany) -> Unit
) {
    if (isLoading && companies.isEmpty()) { LoadingView(); return }
    if (companies.isEmpty()) { EmptyView(Icons.Default.Business, "No companies found"); return }
    LazyColumn(
        contentPadding      = PaddingValues(14.dp),  // ← named param — fixes ClassCastException
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items = companies, key = { c -> c.sanitizedName }) { c ->
            ExplorerFolderCard(Icons.Default.Business, Color(0xFF1565C0),
                c.originalName, "${c.totalUsers} users", "${c.activeUsers} active") { onClick(c) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Departments view ──────────────────────────────────────────────────────────

@Composable
private fun DepartmentsView(
    company: MUCompany, depts: List<MUDepartment>, onClick: (MUDepartment) -> Unit
) {
    if (depts.isEmpty()) { EmptyView(Icons.Default.AccountTree, "No departments"); return }
    LazyColumn(
        contentPadding      = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("Departments in ${company.originalName}",
            "${depts.size} department${if (depts.size != 1) "s" else ""}") }
        items(items = depts, key = { d -> d.sanitizedName }) { d ->
            ExplorerFolderCard(Icons.Default.AccountTree, Color(0xFF2E7D32),
                d.departmentName, "${d.userCount} users", "${d.roles.size} roles") { onClick(d) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Roles view ────────────────────────────────────────────────────────────────

@Composable
private fun RolesView(
    company: MUCompany, dept: MUDepartment,
    roles: List<MURoleInfo>, onClick: (MURoleInfo) -> Unit
) {
    if (roles.isEmpty()) { EmptyView(Icons.Default.Group, "No roles in this department"); return }
    LazyColumn(
        contentPadding      = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader(dept.departmentName,
            "${roles.size} role${if (roles.size != 1) "s" else ""}") }
        items(items = roles, key = { r -> r.roleName }) { r ->
            ExplorerFolderCard(Icons.Default.Group, roleColor(r.roleName),
                r.roleName, "${r.userCount} users", "${r.activeUsers} active",
                badge = if (r.userCount > 0) r.userCount.toString() else null) { onClick(r) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Users view ────────────────────────────────────────────────────────────────

@Composable
private fun UsersView(
    users: List<MUUser>, currentRole: String, isLoading: Boolean,
    onUserClick: (MUUser) -> Unit, onEditUser: (MUUser) -> Unit,
    onToggleStatus: (MUUser) -> Unit, onDelete: (MUUser) -> Unit
) {
    if (users.isEmpty()) { EmptyView(Icons.Default.PersonSearch, "No users in this role"); return }
    LazyColumn(
        contentPadding      = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("${users.size} user${if (users.size != 1) "s" else ""}",
            "${users.count { it.isActive }} active · ${users.count { !it.isActive }} inactive") }
        items(items = users, key = { u -> u.id }) { user ->
            UserCard(user, canEdit(currentRole, user.role),
                onUserClick    = { onUserClick(user) },
                onEditUser     = { onEditUser(user) },
                onToggleStatus = { onToggleStatus(user) },
                onDelete       = { onDelete(user) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Search results ────────────────────────────────────────────────────────────

@Composable
private fun SearchResultsView(
    users: List<MUUser>, currentRole: String,
    onUserClick: (MUUser) -> Unit, onEditUser: (MUUser) -> Unit,
    onToggleStatus: (MUUser) -> Unit, onDelete: (MUUser) -> Unit
) {
    if (users.isEmpty()) { EmptyView(Icons.Default.SearchOff, "No results found"); return }
    LazyColumn(
        contentPadding      = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("${users.size} result${if (users.size != 1) "s" else ""}",
            "across all departments") }
        items(items = users, key = { u -> u.id }) { user ->
            UserCard(user, canEdit(currentRole, user.role), showContext = true,
                onUserClick    = { onUserClick(user) },
                onEditUser     = { onEditUser(user) },
                onToggleStatus = { onToggleStatus(user) },
                onDelete       = { onDelete(user) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── User card ─────────────────────────────────────────────────────────────────

@Composable
private fun UserCard(
    user: MUUser, canEdit: Boolean, showContext: Boolean = false,
    onUserClick: () -> Unit, onEditUser: () -> Unit,
    onToggleStatus: () -> Unit, onDelete: () -> Unit
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
                    Surface(
                        color = if (user.isActive) Color(0xFF4CAF50).copy(0.15f)
                        else Color(0xFFF44336).copy(0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(if (user.isActive) "Active" else "Inactive",
                            Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = if (user.isActive) Color(0xFF2E7D32) else Color(0xFFC62828))
                    }
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
            Box {
                IconButton(onClick = { showMenu = true }, Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (canEdit) DropdownMenuItem(
                        text        = { Text("Edit Profile") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color(0xFF1976D2)) },
                        onClick     = { showMenu = false; onEditUser() }
                    )
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
                    HorizontalDivider()
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

// ── Explorer folder card ──────────────────────────────────────────────────────

@Composable
private fun ExplorerFolderCard(
    icon: ImageVector, iconColor: Color, title: String,
    meta1: String, meta2: String, badge: String? = null, onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(iconColor.copy(0.12f)),
                Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniChip(meta1, iconColor); MiniChip(meta2, iconColor.copy(0.7f))
                }
            }
            if (badge != null)
                Box(Modifier.size(28.dp).clip(CircleShape).background(iconColor.copy(0.15f)),
                    Alignment.Center) {
                    Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = iconColor)
                }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

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

@Composable
private fun MiniChip(label: String, color: Color) {
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(6.dp)) {
        Text(label, Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

private fun canEdit(editorRole: String, targetRole: String) = when (editorRole) {
    "Administrator" -> true
    "Manager", "HR" -> targetRole in setOf("Employee", "Intern", "Team Lead")
    else            -> false
}

private fun roleColor(role: String): Color = when (role) {
    "Administrator" -> Color(0xFFD32F2F)
    "Manager"       -> Color(0xFF1976D2)
    "HR"            -> Color(0xFF388E3C)
    "Team Lead"     -> Color(0xFFF57C00)
    "Employee"      -> Color(0xFF7B1FA2)
    "Intern"        -> Color(0xFF455A64)
    else            -> Color(0xFF00796B)
}