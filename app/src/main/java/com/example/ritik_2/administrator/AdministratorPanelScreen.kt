package com.example.ritik_2.administrator

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.data.model.Permissions
import kotlinx.coroutines.delay

private val PANEL_ROLES = setOf(
    Permissions.ROLE_SYSTEM_ADMIN,
    Permissions.ROLE_ADMIN,
    Permissions.ROLE_MANAGER,
    Permissions.ROLE_HR
)

data class AdminFunction(
    val id          : String,
    val title       : String,
    val description : String,
    val icon        : ImageVector,
    val color       : Color,
    val badgeLabel  : String  = "",   // e.g. "Super Admin", "Admin Only"
    val badgeColor  : Color   = Color(0xFFF44336)
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdministratorPanelScreen(
    adminData         : AdministratorPanelActivity.AdminData?,
    departmentData    : List<AdministratorPanelActivity.DepartmentData>,
    organizationStats : AdministratorPanelActivity.OrganizationStats?,
    isLoading         : Boolean,
    hasAccess         : Boolean,
    accessDeniedMsg   : String?,
    onFunctionClick   : (String) -> Unit
) {
    if (accessDeniedMsg != null) { AccessDeniedScreen(accessDeniedMsg); return }

    if (!hasAccess) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); visible = true }

    val role         = adminData?.role ?: ""
    val permissions  = adminData?.permissions ?: emptyList()
    val isSysAdmin   = PermissionGuard.isSystemAdmin(role)
    val isAdmin      = role == Permissions.ROLE_ADMIN || isSysAdmin
    val canAccessDb  = PermissionGuard.canAccessDatabaseManager(role, permissions)

    // Build function list entirely from permissions, not just role string
    val allFunctions = remember(role, permissions) {
        buildList {
            // Always available to panel roles
            add(AdminFunction("create_user",    "Create User",
                "Add new users to your organisation",
                Icons.Default.PersonAdd, Color(0xFF4CAF50)))
            add(AdminFunction("manage_users",   "Manage Users",
                "View, edit and manage existing users",
                Icons.Default.People,   Color(0xFF2196F3)))
            add(AdminFunction("department_mgr", "Department Manager",
                "Create, delete and move users across depts",
                Icons.Default.AccountTree, Color(0xFF00897B)))

            // Role management — Administrator and above
            if (isAdmin || "manage_roles" in permissions) {
                add(AdminFunction("role_management", "Role Management",
                    "Change user roles and update permissions",
                    Icons.Default.AdminPanelSettings, Color(0xFFF44336),
                    badgeLabel = if (isSysAdmin) "Super Admin" else "Admin Only"))
            }

            // Database Manager — System_Administrator OR explicit permission only
            if (canAccessDb) {
                add(AdminFunction("database_manager", "Database Manager",
                    "Manage collections, rules, indexes and data",
                    Icons.Default.Cloud, Color(0xFF9C27B0),
                    badgeLabel  = if (isSysAdmin) "Super Admin" else "Permitted",
                    badgeColor  = if (isSysAdmin) Color(0xFF6200EA) else Color(0xFF9C27B0)))
            }

            // Company settings — Administrator and above
            if (isAdmin || "manage_companies" in permissions) {
                add(AdminFunction("company_settings", "Company Settings",
                    "Manage company information and branding",
                    Icons.Default.Business, Color(0xFFFF9800),
                    badgeLabel = if (isSysAdmin) "Super Admin" else "Admin Only"))
            }

            // Reports — anyone with access_admin_panel
            add(AdminFunction("reports", "Reports & Export",
                "Generate reports and export data",
                Icons.Default.Assessment, Color(0xFF607D8B)))
        }
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding      = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { -it }) {
                GradientHeader(adminData, isSysAdmin, isLoading)
            }
        }
        item {
            AnimatedVisibility(visible = visible, enter = fadeIn(tween(400, 100))) {
                if (organizationStats != null) {
                    StatsRow(
                        organizationStats.totalUsers,
                        organizationStats.totalDepartments,
                        organizationStats.totalRoles,
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else if (isLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
        if (departmentData.isNotEmpty()) {
            item {
                AnimatedVisibility(visible = visible,
                    enter = fadeIn(tween(400, 200)) + slideInHorizontally { it }) {
                    DepartmentStrip(departmentData, Modifier.padding(bottom = 8.dp))
                }
            }
        }
        item {
            Text(
                "Quick Actions",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        itemsIndexed(allFunctions) { idx, fn ->
            var pressed by remember { mutableStateOf(false) }
            val sc by animateFloatAsState(
                targetValue   = if (pressed) 0.96f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "s"
            )
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(300, idx * 80)) + slideInHorizontally(tween(350, idx * 80)) { it / 2 }
            ) {
                FunctionCard(
                    fn       = fn,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).scale(sc),
                    onClick  = { onFunctionClick(fn.id) },
                    onPress  = { p -> pressed = p }
                )
            }
        }
    }
}

// ── Gradient header ───────────────────────────────────────────────────────────

@Composable
private fun GradientHeader(
    adminData  : AdministratorPanelActivity.AdminData?,
    isSysAdmin : Boolean,
    isLoading  : Boolean
) {
    val gradientColors = if (isSysAdmin)
        listOf(Color(0xFF4A148C), Color(0xFF7B1FA2))  // deep purple for System_Administrator
    else
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(0.75f))

    Box(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(gradientColors))
            .padding(top = 56.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
    ) {
        if (isLoading && adminData == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else adminData?.let { data ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(0.25f)),
                        Alignment.Center
                    ) {
                        if (data.imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(data.imageUrl).crossfade(true).build(),
                                contentDescription = "Avatar",
                                modifier     = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                data.name.take(2).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Welcome back,",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(0.8f))
                        Text(data.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Role badge
                            Surface(
                                color = if (isSysAdmin) Color(0xFFFFD600).copy(0.25f)
                                else Color.White.copy(0.2f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    data.role,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSysAdmin) Color(0xFFFFD600) else Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (isSysAdmin) {
                                Surface(
                                    color = Color(0xFFFFD600).copy(0.2f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(Icons.Default.Shield, null,
                                            Modifier.size(10.dp), tint = Color(0xFFFFD600))
                                        Text("Super Admin",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFFFD600),
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("${data.companyName} · ${data.department}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(0.75f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ── Stats row ─────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(
    totalUsers: Int, totalDepts: Int, totalRoles: Int,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            StatPill(totalUsers, "Users",       Icons.Outlined.Groups,         Color(0xFF2196F3))
            VerticalDivider(Modifier.height(36.dp))
            StatPill(totalDepts, "Departments", Icons.Outlined.BusinessCenter, Color(0xFF4CAF50))
            VerticalDivider(Modifier.height(36.dp))
            StatPill(totalRoles, "Roles",       Icons.Outlined.AccountTree,    Color(0xFFFF9800))
        }
    }
}

@Composable
private fun StatPill(value: Int, label: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(value.toString(), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Department strip ──────────────────────────────────────────────────────────

@Composable
private fun DepartmentStrip(
    departments: List<AdministratorPanelActivity.DepartmentData>,
    modifier   : Modifier = Modifier
) {
    Column(modifier.padding(horizontal = 16.dp)) {
        Text("Departments (${departments.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(departments, key = { it.sanitized }) { dept ->
                Card(Modifier.width(130.dp), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(dept.name, style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(dept.userCount.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                        Text("users", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ── Function card ─────────────────────────────────────────────────────────────

@Composable
private fun FunctionCard(
    fn      : AdminFunction,
    modifier: Modifier = Modifier,
    onClick : () -> Unit,
    onPress : (Boolean) -> Unit
) {
    Card(
        modifier  = modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication        = null,
            onClick           = onClick
        ),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(12.dp))
                    .background(fn.color.copy(0.12f)),
                Alignment.Center
            ) {
                Icon(fn.icon, fn.title, tint = fn.color, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fn.title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    if (fn.badgeLabel.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = fn.badgeColor.copy(0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(fn.badgeLabel,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = fn.badgeColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(fn.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                modifier = Modifier.size(20.dp))
        }
    }
}

// ── Access denied ─────────────────────────────────────────────────────────────

@Composable
private fun AccessDeniedScreen(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text("Access Denied",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth(0.5f))
            Spacer(Modifier.height(8.dp))
            Text("Redirecting…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}