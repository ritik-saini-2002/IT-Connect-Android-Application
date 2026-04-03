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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.data.model.AuthSession
import com.example.ritik_2.data.model.UserProfile

// ── Drawer destination ────────────────────────────────────────────────────────

data class DrawerItem(
    val id         : String,
    val label      : String,
    val icon       : ImageVector,
    val badge      : String?  = null,
    val isDestructive: Boolean = false,
    val requiredRoles: List<String> = emptyList() // empty = visible to all
)

val drawerItems = listOf(
    DrawerItem("home",         "Home",            Icons.Default.Home),
    DrawerItem("profile",      "My Profile",      Icons.Default.Person),
    DrawerItem("manage_users", "Manage Users",    Icons.Default.ManageAccounts,
        requiredRoles = listOf("Administrator", "Manager", "HR")),
    DrawerItem("create_user",  "Create User",     Icons.Default.PersonAdd,
        requiredRoles = listOf("Administrator", "Manager")),
    DrawerItem("roles",        "Role Management", Icons.Default.AdminPanelSettings,
        requiredRoles = listOf("Administrator")),
    DrawerItem("database",     "Database",        Icons.Default.Storage,
        requiredRoles = listOf("Administrator")),
    DrawerItem("pc_control",   "PC Control",      Icons.Default.Computer),
    DrawerItem("settings",     "Settings",        Icons.Default.Settings),
    DrawerItem("logout",       "Logout",          Icons.Default.Logout,
        isDestructive = true),
)

// ── Swipeable drawer wrapper ──────────────────────────────────────────────────

/**
 * Wrap any screen content with this to get the global swipeable sidebar.
 *
 * Usage:
 *   AppDrawerWrapper(
 *       session     = session,
 *       profile     = profile,
 *       currentItem = "home",
 *       onNavigate  = { id -> ... }
 *   ) {
 *       YourScreenContent()
 *   }
 *
 * Swipe right-to-left (from left edge) opens, left-to-right closes.
 */
@Composable
fun AppDrawerWrapper(
    session    : AuthSession?,
    profile    : UserProfile?,
    currentItem: String,
    onNavigate : (String) -> Unit,
    content    : @Composable () -> Unit
) {
    var isOpen by remember { mutableStateOf(false) }
    var dragX  by remember { mutableFloatStateOf(0f) }

    val drawerWidth  = 300.dp
    val translateX   by animateDpAsState(
        targetValue   = if (isOpen) 0.dp else -drawerWidth,
        animationSpec = tween(280),
        label         = "drawerX"
    )
    val scrimAlpha   by animateFloatAsState(
        targetValue   = if (isOpen) 0.45f else 0f,
        animationSpec = tween(280),
        label         = "scrim"
    )
    val contentScale by animateFloatAsState(
        targetValue   = if (isOpen) 0.93f else 1f,
        animationSpec = tween(280),
        label         = "contentScale"
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
                        // Open: drag starting from left edge going right
                        if (!isOpen && dragX < 60f && delta > 0) {
                            if (newDragX > 80f) isOpen = true
                        }
                        // Close: drag going left while open
                        if (isOpen && delta < -20f) {
                            isOpen = false
                        }
                        dragX = newDragX
                    }
                )
            }
    ) {
        // Main content — scales down slightly when drawer is open
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(contentScale)
                .clip(RoundedCornerShape(if (isOpen) 20.dp else 0.dp))
                .graphicsLayer { shadowElevation = if (isOpen) 24f else 0f }
        ) {
            content()
        }

        // Scrim — tap to close
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable { isOpen = false }
            )
        }

        // Drawer panel
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
                onNavigate  = { id ->
                    isOpen = false
                    onNavigate(id)
                },
                onClose     = { isOpen = false }
            )
        }
    }
}

// ── Drawer content ────────────────────────────────────────────────────────────

@Composable
private fun DrawerContent(
    session    : AuthSession?,
    profile    : UserProfile?,
    currentItem: String,
    onNavigate : (String) -> Unit,
    onClose    : () -> Unit
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
                    modifier          = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Avatar
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
                Text(
                    session?.name ?: "User",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 17.sp,
                    color      = Color.White,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    session?.email ?: "",
                    fontSize = 12.sp,
                    color    = Color.White.copy(0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                // Role badge
                Surface(
                    color = Color.White.copy(0.2f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        role.ifBlank { "User" },
                        modifier   = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                }
            }
        }

        // Nav items
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            drawerItems
                .filter { item ->
                    item.requiredRoles.isEmpty() || role in item.requiredRoles
                }
                .forEachIndexed { index, item ->
                    if (index > 0 && item.isDestructive) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color    = MaterialTheme.colorScheme.outlineVariant.copy(0.5f)
                        )
                    }
                    DrawerNavItem(
                        item      = item,
                        isActive  = item.id == currentItem,
                        onClick   = { onNavigate(item.id) }
                    )
                }
        }

        // Footer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "IT Connect v2.0",
                style  = MaterialTheme.typography.labelSmall,
                color  = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
            )
        }
    }
}

// ── Drawer nav item ───────────────────────────────────────────────────────────

@Composable
private fun DrawerNavItem(
    item    : DrawerItem,
    isActive: Boolean,
    onClick : () -> Unit
) {
    val bgColor  = if (isActive) MaterialTheme.colorScheme.primary.copy(0.1f)
    else          Color.Transparent
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
        // Active indicator bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent)
        )

        Icon(item.icon, null,
            modifier = Modifier.size(20.dp),
            tint     = iconTint)

        Text(
            item.label,
            modifier   = Modifier.weight(1f),
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            fontSize   = 14.sp,
            color      = textColor
        )

        if (item.badge != null) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Text(item.badge,
                    modifier   = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onPrimary)
            }
        }

        if (isActive) {
            Icon(Icons.Default.ChevronRight, null,
                modifier = Modifier.size(14.dp),
                tint     = MaterialTheme.colorScheme.primary.copy(0.5f))
        }
    }
}