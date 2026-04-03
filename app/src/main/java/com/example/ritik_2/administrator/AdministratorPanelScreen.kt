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
import kotlinx.coroutines.delay

// ── Roles that can use the Administrator Panel ────────────────────────────────
private val PANEL_ROLES = setOf("Administrator", "Manager", "HR")

// ── Roles that can edit users (update profile via admin panel) ────────────────
private val CAN_EDIT_USERS = setOf("Administrator", "Manager", "HR")

data class AdminFunction(
    val id          : String,
    val title       : String,
    val description : String,
    val icon        : ImageVector,
    val color       : Color,
    val adminOnly   : Boolean = false  // true = Administrator only
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
    if (accessDeniedMsg != null) {
        AccessDeniedScreen(accessDeniedMsg); return
    }

    if (!hasAccess) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); visible = true }

    val role    = adminData?.role ?: ""
    val isAdmin = role == "Administrator"

    // ── Build function list based on role ─────────────────────────────────────
    // Database Manager is completely removed.
    // Role Management is Administrator-only.
    val allFunctions = remember(role) {
        buildList {
            add(AdminFunction(
                id          = "create_user",
                title       = "Create User",
                description = "Add new users to your organisation",
                icon        = Icons.Default.PersonAdd,
                color       = Color(0xFF4CAF50)
            ))
            add(AdminFunction(
                id          = "manage_users",
                title       = "Manage Users",
                description = "View, edit and manage existing users",
                icon        = Icons.Default.People,
                color       = Color(0xFF2196F3)
            ))
            if (isAdmin) add(AdminFunction(
                id          = "role_management",
                title       = "Role Management",
                description = "Change user roles and update permissions",
                icon        = Icons.Default.AdminPanelSettings,
                color       = Color(0xFFF44336),
                adminOnly   = true
            ))
            add(AdminFunction(
                id          = "company_settings",
                title       = "Company Settings",
                description = "Manage company information and branding",
                icon        = Icons.Default.Business,
                color       = Color(0xFFFF9800)
            ))
            add(AdminFunction(
                id          = "reports",
                title       = "Reports & Export",
                description = "Generate reports and export data",
                icon        = Icons.Default.Assessment,
                color       = Color(0xFF607D8B)
            ))
        }
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding      = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { -it }) {
                GradientHeader(adminData, isLoading)
            }
        }

        item {
            AnimatedVisibility(visible = visible, enter = fadeIn(tween(400, 100))) {
                if (organizationStats != null) {
                    StatsRow(
                        totalUsers = organizationStats.totalUsers,
                        totalDepts = organizationStats.totalDepartments,
                        totalRoles = organizationStats.totalRoles,
                        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
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
            Text("Quick Actions",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        itemsIndexed(allFunctions) { idx, fn ->
            var pressed by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue   = if (pressed) 0.96f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label         = "scale"
            )
            AnimatedVisibility(visible = visible,
                enter = fadeIn(tween(300, idx * 80)) +
                        slideInHorizontally(tween(350, idx * 80)) { it / 2 }) {
                FunctionCard(
                    fn       = fn,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).scale(scale),
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
    adminData : AdministratorPanelActivity.AdminData?,
    isLoading : Boolean
) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            )))
            .padding(top = 56.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
    ) {
        if (isLoading && adminData == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            adminData?.let { data ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
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
                            Text(data.name.take(2).uppercase(),
                                style      = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Welcome back,",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f))
                        Text(data.name,
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(data.role,
                                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style      = MaterialTheme.typography.labelSmall,
                                color      = Color.White,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("${data.companyName} · ${data.department}",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
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
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatPill(totalUsers, "Users",       Icons.Outlined.Groups,          Color(0xFF2196F3))
            VerticalDivider(Modifier.height(36.dp))
            StatPill(totalDepts, "Departments", Icons.Outlined.BusinessCenter,  Color(0xFF4CAF50))
            VerticalDivider(Modifier.height(36.dp))
            StatPill(totalRoles, "Roles",       Icons.Outlined.AccountTree,     Color(0xFFFF9800))
        }
    }
}

@Composable
private fun StatPill(value: Int, label: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(value.toString(),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface)
        Text(label,
            style = MaterialTheme.typography.labelSmall,
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
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(departments, key = { it.sanitized }) { dept ->
                Card(
                    modifier  = Modifier.width(130.dp),
                    shape     = RoundedCornerShape(12.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(dept.name,
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(dept.userCount.toString(),
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = Color(0xFF2196F3))
                        Text("users",
                            style = MaterialTheme.typography.labelSmall,
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
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(12.dp))
                    .background(fn.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(fn.icon, fn.title, tint = fn.color, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fn.title,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    if (fn.adminOnly) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFF44336).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Admin only",
                                modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style      = MaterialTheme.typography.labelSmall,
                                color      = Color(0xFFF44336),
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(fn.description,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
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
            modifier            = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.Lock, null,
                modifier = Modifier.size(72.dp),
                tint     = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text("Access Denied",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(message,
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
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