package com.example.ritik_2.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState                  : MainUiState,
    onLogout                 : () -> Unit,
    onCardClick              : (Int) -> Unit,
    onProfileClick           : () -> Unit,
    showCompleteProfileBanner: Boolean = false
) {
    val userProfile = uiState.userProfile
    val isLoading   = uiState.isLoading

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()
    var totalDragX  by remember { mutableFloatStateOf(0f) }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        gesturesEnabled = false,
        scrimColor    = Color.Black.copy(alpha = 0.45f),
        drawerContent = {
            ModalDrawerSheet(
                drawerShape          = RectangleShape,
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                windowInsets         = WindowInsets(0)
            ) {
                AppSidebar(
                    profile        = userProfile,
                    onProfileClick = { scope.launch { drawerState.close() }; onProfileClick() },
                    onCardClick    = { id -> scope.launch { drawerState.close() }; onCardClick(id) },
                    onLogout       = { scope.launch { drawerState.close() }; onLogout() },
                    onClose        = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(drawerState.isClosed) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd   = {
                            if (totalDragX > 80f && drawerState.isClosed)
                                scope.launch { drawerState.open() }
                            else if (totalDragX < -80f && drawerState.isOpen)
                                scope.launch { drawerState.close() }
                            totalDragX = 0f
                        },
                        onHorizontalDrag = { _, d -> totalDragX += d }
                    )
                }
        ) {
            Scaffold { paddingValues ->
                Box(Modifier.fillMaxSize().padding(paddingValues)) {

                    AnimatedVisibility(
                        visible = isLoading && userProfile == null,
                        enter = fadeIn(), exit = fadeOut()
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(Modifier.size(56.dp),
                                    color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
                                Spacer(Modifier.height(16.dp))
                                Text("Loading your profile...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = userProfile != null || !isLoading,
                        enter   = fadeIn(spring()) + slideInVertically(spring()) { it / 10 },
                        exit    = fadeOut(spring())
                    ) {
                        val features = listOf(
                            FeatureItem(1, "Register Complaint", Icons.Outlined.ReportProblem,     Color(0xFFE53935)),
                            FeatureItem(2, "Manage Complaints",  Icons.Outlined.List,              Color(0xFF8E24AA)),
                            FeatureItem(3, "Admin Panel",        Icons.Outlined.AdminPanelSettings, Color(0xFFFF6F00)),
                            FeatureItem(4, "Server Connect",     Icons.Outlined.Code,              Color(0xFF6200EA)),
                            FeatureItem(5, "Knowledge Base",     Icons.Outlined.MenuBook,          Color(0xFF00796B)),
                            FeatureItem(6, "Windows Control",    Icons.Outlined.Computer,          Color(0xFFC51162)),
                            FeatureItem(7, "Chats",           Icons.Outlined.Settings,          MaterialTheme.colorScheme.tertiary),
                            FeatureItem(8, "Help & Support",     Icons.Outlined.SupportAgent,      MaterialTheme.colorScheme.primary)
                        )

                        LazyVerticalGrid(
                            columns               = GridCells.Fixed(2),
                            contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                            verticalArrangement   = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier              = Modifier.fillMaxSize()
                        ) {
                            if (showCompleteProfileBanner) {
                                item(span = { GridItemSpan(2) }) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        shape    = RoundedCornerShape(14.dp),
                                        colors   = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer)
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().clickable { onProfileClick() }.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Warning, null,
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(22.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text("Profile Incomplete",
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer)
                                                Text("Tap to complete your profile",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(0.8f))
                                            }
                                            Icon(Icons.Default.ChevronRight, null,
                                                tint = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }
                                }
                            }

                            item(span = { GridItemSpan(2) }) {
                                Column {
                                    Spacer(Modifier.height(8.dp))
                                    UserProfileCard(
                                        profile        = userProfile,
                                        onProfileClick = onProfileClick,
                                        onLogout       = onLogout
                                    )
                                }
                            }

                            item(span = { GridItemSpan(2) }) {
                                Text("Dashboard",
                                    style      = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier   = Modifier.padding(vertical = 4.dp))
                            }

                            items(features) { feature ->
                                FeatureCard(
                                    title   = feature.title,
                                    icon    = feature.icon,
                                    color   = feature.color,
                                    onClick = { onCardClick(feature.id) }
                                )
                            }

                            item(span = { GridItemSpan(2) }) {
                                Column {
                                    Spacer(Modifier.height(8.dp))
                                    CopyrightSection(developerName = "Ritik Saini")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Profile Card ──────────────────────────────────────────────────────────────

@Composable
fun UserProfileCard(
    profile       : UserProfileData?,
    onProfileClick: () -> Unit,
    onLogout      : () -> Unit
) {
    val name        = profile?.name        ?: "Loading..."
    val email       = profile?.email       ?: ""
    val role        = profile?.role        ?: ""
    val department  = profile?.department  ?: ""
    val designation = profile?.designation ?: ""
    val imageUrl    = profile?.imageUrl

    Card(
        modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp))
            .clickable { onProfileClick() },
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(88.dp).background(
                    Brush.linearGradient(listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )), CircleShape
                ).padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl).crossfade(true).build(),
                        contentDescription = "Profile",
                        modifier     = Modifier.size(82.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop)
                } else {
                    Box(
                        Modifier.size(82.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = name.split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                            .joinToString("")
                        if (initials.isNotBlank())
                            Text(initials,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        else
                            Icon(Icons.Default.Person, "Profile",
                                Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(name, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center)

            if (designation.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(designation, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.75f),
                    textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically) {
                if (email.isNotBlank()) InfoChip(Icons.Outlined.Email, email)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically) {
                if (role.isNotBlank()) {
                    InfoChip(Icons.Outlined.ManageAccounts, role)
                    Spacer(Modifier.width(8.dp))
                }
                if (department.isNotBlank()) InfoChip(Icons.Outlined.Groups, department)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(0.10f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun FeatureCard(title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "cardScale")
    Card(
        modifier = Modifier.fillMaxWidth().height(130.dp).scale(scale)
            .shadow(4.dp, RoundedCornerShape(16.dp)).clickable { pressed = true; onClick() },
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier            = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                .background(color.copy(0.15f)), Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
        }
    }
    LaunchedEffect(pressed) {
        if (pressed) { kotlinx.coroutines.delay(150); pressed = false }
    }
}

// ── Sidebar ───────────────────────────────────────────────────────────────────

@Composable
fun AppSidebar(
    profile       : UserProfileData?,
    onProfileClick: () -> Unit,
    onCardClick   : (Int) -> Unit,
    onLogout      : () -> Unit,
    onClose       : () -> Unit
) {
    val sidebarItems = listOf(
        FeatureItem(1, "Register Complaint", Icons.Outlined.ReportProblem,      Color(0xFFE53935)),
        FeatureItem(2, "Manage Complaints",  Icons.Outlined.List,               Color(0xFF8E24AA)),
        FeatureItem(3, "Admin Panel",        Icons.Outlined.AdminPanelSettings,  Color(0xFFFF6F00)),
        FeatureItem(4, "Server Connect",     Icons.Outlined.Code,               Color(0xFF6200EA)),
        FeatureItem(5, "Knowledge Base",     Icons.Outlined.MenuBook,           Color(0xFF00796B)),
        FeatureItem(6, "Windows Control",    Icons.Outlined.Computer,           Color(0xFFC51162)),
        FeatureItem(7, "Settings",           Icons.Outlined.Settings,           Color(0xFF546E7A)),
        FeatureItem(8, "Help & Support",     Icons.Outlined.SupportAgent,       Color(0xFF1976D2))
    )

    Column(Modifier.fillMaxHeight().width(300.dp).background(MaterialTheme.colorScheme.surface)) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primary.copy(0.85f)
                )))
                .clickable { onProfileClick() }
                .padding(top = 52.dp, bottom = 20.dp, start = 20.dp, end = 20.dp)
        ) {
            Column {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(0.25f)),
                    Alignment.Center) {
                    if (!profile?.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(profile!!.imageUrl).crossfade(true).build(),
                            contentDescription = "Avatar",
                            modifier     = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop)
                    } else {
                        val initials = (profile?.name ?: "").split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                            .joinToString("")
                        if (initials.isNotBlank())
                            Text(initials, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = Color.White)
                        else
                            Icon(Icons.Default.Person, null, Modifier.size(34.dp), tint = Color.White)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(profile?.name ?: "Loading...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                if (!profile?.designation.isNullOrBlank())
                    Text(profile!!.designation,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.85f), maxLines = 1)
                if (!profile?.role.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(color = Color.White.copy(0.2f), shape = RoundedCornerShape(20.dp)) {
                        Text(profile!!.role,
                            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            sidebarItems.forEach { item ->
                SidebarNavItem(item.icon, item.title, item.color) { onCardClick(item.id) }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))
        SidebarNavItem(Icons.Default.Logout, "Logout", MaterialTheme.colorScheme.error) { onLogout() }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SidebarNavItem(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { pressed = true; onClick() }
            .background(if (pressed) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(0.12f)),
            Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
    LaunchedEffect(pressed) {
        if (pressed) { kotlinx.coroutines.delay(120); pressed = false }
    }
}

@Composable
fun CopyrightSection(developerName: String) {
    val heartScale by rememberInfiniteTransition(label = "heart").animateFloat(
        1f, 1.3f,
        infiniteRepeatable(androidx.compose.animation.core.tween(800,
            easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heartScale")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier            = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    ) {
        HorizontalDivider(Modifier.width(80.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
        Spacer(Modifier.height(4.dp))
        Text("© 2025 $developerName", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text("All rights reserved", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Made with", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
            Icon(Icons.Default.Favorite, "Love",
                tint = Color.Red, modifier = Modifier.size(14.dp).scale(heartScale))
            Text("by $developerName", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
        }
    }
}

@Composable
fun StatItem(value: Int, label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(value.toString(), fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.6f))
    }
}

data class FeatureItem(val id: Int, val title: String, val icon: ImageVector, val color: Color)