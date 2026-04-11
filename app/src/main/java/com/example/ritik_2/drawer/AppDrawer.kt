package com.example.ritik_2.drawer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.data.model.AuthSession
import com.example.ritik_2.data.model.UserProfile

// ── Drawer destination ────────────────────────────────────────────────────────

data class DrawerItem(
    val id                 : String,
    val label              : String,
    val icon               : ImageVector,
    val badge              : String?  = null,
    val isDestructive      : Boolean  = false,
    val requiredPermission : String   = "",
    val section            : String   = "main"   // "main", "pc_control", "admin"
)

// ── UNIFIED drawer items — works for both main app and PC Control ─────────────
// All activities are linked here. PC Control section shows for everyone.
// Admin section filtered by permissions.

val drawerItems = listOf(
    // ── Main section ──
    DrawerItem("home",         "Home",            Icons.Default.Home,            section = "main"),
    DrawerItem("profile",      "My Profile",      Icons.Default.Person,          section = "main"),
    DrawerItem("chat",         "Chat",            Icons.Default.Chat,            section = "main"),
    DrawerItem("contact",      "Contact",         Icons.Default.ContactPhone,    section = "main"),
    DrawerItem("notifications","Notifications",   Icons.Default.Notifications,   section = "main"),

    // ── PC Control section ── (visible to everyone)
    DrawerItem("pc_control",   "PC Control",      Icons.Default.Computer,        section = "pc_control"),
    DrawerItem("winshare",     "WinShare",        Icons.Default.Share,           section = "pc_control"),
    DrawerItem("macnet",       "MAC Net",         Icons.Default.Wifi,            section = "pc_control"),

    // ── Admin section ── (filtered by permissions)
    DrawerItem("admin_panel",  "Admin Panel",     Icons.Default.AdminPanelSettings,
        requiredPermission = "admin_panel", section = "admin"),
    DrawerItem("manage_users", "Manage Users",    Icons.Default.ManageAccounts,
        requiredPermission = "view_all_users", section = "admin"),
    DrawerItem("create_user",  "Create User",     Icons.Default.PersonAdd,
        requiredPermission = "create_user", section = "admin"),
    DrawerItem("roles",        "Role Management", Icons.Default.Security,
        requiredPermission = "manage_roles", section = "admin"),
    DrawerItem("departments",  "Departments",     Icons.Default.Groups,
        requiredPermission = "manage_departments", section = "admin"),
    DrawerItem("database",     "Database",        Icons.Default.Storage,
        requiredPermission = "database_manager", section = "admin"),
    DrawerItem("company",      "Company Settings",Icons.Default.Business,
        requiredPermission = "company_settings", section = "admin"),
    DrawerItem("reports",      "Reports",         Icons.Default.Assessment,
        requiredPermission = "view_reports", section = "admin"),

    // ── Settings & Logout ──
    DrawerItem("settings",     "Settings",        Icons.Default.Settings,        section = "main"),
    DrawerItem("logout",       "Logout",          Icons.Default.Logout,
        isDestructive = true, section = "main"),
)

// ── Swipeable drawer wrapper ──────────────────────────────────────────────────

@Composable
fun AppDrawerWrapper(
    session     : AuthSession?,
    profile     : UserProfile?,
    currentItem : String,
    permissions : List<String> = emptyList(),
    onNavigate  : (String) -> Unit,
    content     : @Composable () -> Unit
) {
    var isOpen by remember { mutableStateOf(false) }
    var dragX  by remember { mutableFloatStateOf(0f) }

    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val drawerWidth  = if (isLandscape) 260.dp else 300.dp
    val translateX   by animateDpAsState(
        targetValue   = if (isOpen) 0.dp else -drawerWidth,
        animationSpec = tween(280), label = "drawerX"
    )
    val scrimAlpha   by animateFloatAsState(
        targetValue   = if (isOpen) 0.45f else 0f,
        animationSpec = tween(280), label = "scrim"
    )
    val contentScale by animateFloatAsState(
        targetValue   = if (isOpen) 0.93f else 1f,
        animationSpec = tween(280), label = "contentScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart  = { offset -> dragX = offset.x },
                    onDragEnd    = {},
                    onDragCancel = {},
                    onHorizontalDrag = { _, delta ->
                        val newDragX = dragX + delta
                        if (!isOpen && dragX < 60f && delta > 0) {
                            if (newDragX > 80f) isOpen = true
                        }
                        if (isOpen && delta < -20f) isOpen = false
                        dragX = newDragX
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(contentScale)
                .clip(RoundedCornerShape(if (isOpen) 20.dp else 0.dp))
                .graphicsLayer { shadowElevation = if (isOpen) 24f else 0f }
        ) { content() }

        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable { isOpen = false }
            )
        }

        Box(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .offset(x = translateX)
                .zIndex(2f)
                .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            DrawerContent(
                session     = session,
                profile     = profile,
                currentItem = currentItem,
                permissions = permissions,
                onNavigate  = { id -> isOpen = false; onNavigate(id) },
                onClose     = { isOpen = false }
            )
        }
    }
}

// ── Drawer content ────────────────────────────────────────────────────────────

@Composable
private fun DrawerContent(
    session     : AuthSession?,
    profile     : UserProfile?,
    currentItem : String,
    permissions : List<String> = emptyList(),
    onNavigate  : (String) -> Unit,
    onClose     : () -> Unit
) {
    val role = session?.role ?: ""

    Column(Modifier.fillMaxSize()) {

        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1565C0), Color(0xFF1E88E5))
                    )
                )
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape)
                            .background(Color.White.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!profile?.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(profile!!.imageUrl).crossfade(true).build(),
                                contentDescription = "avatar",
                                modifier     = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            val initials = (session?.name ?: "")
                                .split(" ").take(2)
                                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                .joinToString("")
                            Text(initials.ifBlank { "?" },
                                fontSize   = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White)
                        }
                    }

                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Close",
                            tint = Color.White.copy(0.8f), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(session?.name ?: "User", fontWeight = FontWeight.Bold,
                    fontSize = 17.sp, color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(session?.email ?: "", fontSize = 12.sp,
                    color = Color.White.copy(0.75f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Surface(color = Color.White.copy(0.2f), shape = RoundedCornerShape(20.dp)) {
                    Text(role.ifBlank { "User" },
                        modifier   = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize   = 11.sp, fontWeight = FontWeight.SemiBold,
                        color      = Color.White)
                }
            }
        }

        // Nav items — grouped by section
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            val filteredItems = drawerItems.filter { item ->
                item.requiredPermission.isEmpty() ||
                        permissions.contains(item.requiredPermission) ||
                        PermissionGuard.isSystemAdmin(role)
            }

            // Main section
            val mainItems = filteredItems.filter { it.section == "main" && !it.isDestructive }
            if (mainItems.isNotEmpty()) {
                SectionLabel("NAVIGATE")
                mainItems.forEach { item ->
                    DrawerNavItem(item = item, isActive = item.id == currentItem,
                        onClick = { onNavigate(item.id) })
                }
            }

            // PC Control section
            val pcItems = filteredItems.filter { it.section == "pc_control" }
            if (pcItems.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                SectionLabel("REMOTE CONTROL")
                pcItems.forEach { item ->
                    DrawerNavItem(item = item, isActive = item.id == currentItem,
                        onClick = { onNavigate(item.id) })
                }
            }

            // Admin section
            val adminItems = filteredItems.filter { it.section == "admin" }
            if (adminItems.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                SectionLabel("ADMINISTRATION")
                adminItems.forEach { item ->
                    DrawerNavItem(item = item, isActive = item.id == currentItem,
                        onClick = { onNavigate(item.id) })
                }
            }

            // Destructive items (logout etc)
            val destructive = filteredItems.filter { it.isDestructive }
            if (destructive.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                destructive.forEach { item ->
                    DrawerNavItem(item = item, isActive = false,
                        onClick = { onNavigate(item.id) })
                }
            }
        }

        // Footer
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("IT Connect v3.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier   = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        style      = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp
    )
}

// ── Drawer nav item ───────────────────────────────────────────────────────────

@Composable
private fun DrawerNavItem(
    item    : DrawerItem,
    isActive: Boolean,
    onClick : () -> Unit
) {
    val bgColor  = if (isActive) MaterialTheme.colorScheme.primary.copy(0.1f)
    else Color.Transparent
    val iconTint = when {
        item.isDestructive -> MaterialTheme.colorScheme.error
        isActive           -> MaterialTheme.colorScheme.primary
        else               -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textColor = when {
        item.isDestructive -> MaterialTheme.colorScheme.error
        isActive           -> MaterialTheme.colorScheme.primary
        else               -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp).height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent)
        )

        Icon(item.icon, null, modifier = Modifier.size(20.dp), tint = iconTint)

        Text(item.label, modifier = Modifier.weight(1f),
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp, color = textColor)

        if (item.badge != null) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                Text(item.badge,
                    modifier   = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    fontSize   = 10.sp, fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onPrimary)
            }
        }

        if (isActive) {
            Icon(Icons.Default.ChevronRight, null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary.copy(0.5f))
        }
    }
}