package com.example.ritik_2.main

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
// ✅ Import UserProfileData and MainUiState from MainViewModel
import com.example.ritik_2.main.UserProfileData
import com.example.ritik_2.main.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState       : MainUiState,
    onLogout      : () -> Unit,
    onCardClick   : (Int) -> Unit,
    onProfileClick: () -> Unit
) {
    val userProfile = uiState.userProfile
    val isLoading   = uiState.isLoading
    val userName    = userProfile?.name        ?: "IT Engineer"
    val jobTitle    = userProfile?.designation ?: "IT Professional"

    var showProfileDialog by remember { mutableStateOf(false) }

    Scaffold(
//        topBar = {
//            Box(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .background(
//                        Brush.horizontalGradient(listOf(
//                            MaterialTheme.colorScheme.primary,
//                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
//                        ))
//                    )
//                    .statusBarsPadding()
//                    .padding(horizontal = 16.dp, vertical = 12.dp)
//            ) {
//                Row(
//                    modifier              = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.SpaceBetween,
//                    verticalAlignment     = Alignment.CenterVertically
//                ) {
//                    Column {
//                        Text("IT Connect",
//                            style      = MaterialTheme.typography.titleLarge,
//                            fontWeight = FontWeight.Bold,
//                            color      = MaterialTheme.colorScheme.onPrimary)
//                        Text("Welcome, $userName",
//                            style = MaterialTheme.typography.bodySmall,
//                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
//                    }
//                    Row {
//                        IconButton(onClick = { showProfileDialog = true }) {
//                            Icon(Icons.Default.AccountCircle, "Profile",
//                                tint     = MaterialTheme.colorScheme.onPrimary,
//                                modifier = Modifier.size(28.dp))
//                        }
//                        IconButton(onClick = onLogout) {
//                            Icon(Icons.Default.Logout, "Logout",
//                                tint     = MaterialTheme.colorScheme.onPrimary,
//                                modifier = Modifier.size(24.dp))
//                        }
//                    }
//                }
//            }
//        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(56.dp),
                            color       = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("Loading your profile...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            AnimatedVisibility(
                visible = !isLoading,
                enter   = fadeIn(spring()) + slideInVertically(spring()) { it / 10 },
                exit    = fadeOut(spring())
            ) {
                val features = listOf(
                    FeatureItem(1, "Register Complaint", Icons.Outlined.ReportProblem,      Color(0xFFE53935)),
                    FeatureItem(2, "Manage Complaints",  Icons.Outlined.List,               Color(0xFF8E24AA)),
                    FeatureItem(3, "Admin Panel",        Icons.Outlined.AdminPanelSettings,  Color(0xFFFF6F00)),
                    FeatureItem(4, "Server Connect",     Icons.Outlined.Code,               Color(0xFF6200EA)),
                    FeatureItem(5, "Knowledge Base",     Icons.Outlined.MenuBook,           Color(0xFF00796B)),
                    FeatureItem(6, "Windows Control",    Icons.Outlined.Computer,           Color(0xFFC51162)),
                    FeatureItem(7, "Settings",           Icons.Outlined.Settings,           MaterialTheme.colorScheme.tertiary),
                    FeatureItem(8, "Help & Support",     Icons.Outlined.SupportAgent,       MaterialTheme.colorScheme.primary)
                )

                LazyVerticalGrid(
                    columns               = GridCells.Fixed(2),
                    contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement   = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier              = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(2) }) {
                        Spacer(Modifier.height(8.dp))
                        UserProfileCard(
                            userName          = userName,
                            jobTitle          = jobTitle,
                            // ✅ Pass String? directly — no Uri conversion needed
                            imageUrl          = userProfile?.imageUrl?.toString(),
                            experienceYears   = userProfile?.experience        ?: 0,
                            completedProjects = userProfile?.completedProjects ?: 0,
                            activeProjects    = userProfile?.activeProjects    ?: 0,
                            role              = userProfile?.role              ?: "",
                            companyName       = userProfile?.companyName       ?: "",
                            onProfileClick    = onProfileClick
                        )
                    }

                    item(span = { GridItemSpan(2) }) {
                        userProfile?.let { PerformanceSummaryCard(it) }
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
                        Spacer(Modifier.height(8.dp))
                        CopyrightSection(developerName = "Ritik Saini")
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        ProfileDialog(
            onDismiss    = { showProfileDialog = false },
            onSeeProfile = { onProfileClick(); showProfileDialog = false },
            onLogout     = { onLogout(); showProfileDialog = false }
        )
    }
}

// ── Profile Card — imageUrl is now String? ────────────────────
@Composable
fun UserProfileCard(
    userName         : String,
    jobTitle         : String,
    imageUrl         : String?,   // ✅ FIXED: was Uri?, now String?
    experienceYears  : Int,
    completedProjects: Int,
    activeProjects   : Int,
    role             : String = "",
    companyName      : String = "",
    onProfileClick   : () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clickable { onProfileClick() },
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth()
            ) {
                // Avatar with gradient ring
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(
                            Brush.linearGradient(listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )), CircleShape
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // ✅ Coil AsyncImage accepts String url directly — no Uri needed
                    if (!imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)   // ✅ String works fine here
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile",
                            modifier           = Modifier.size(70.dp).clip(CircleShape),
                            contentScale       = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, "Profile",
                                modifier = Modifier.size(40.dp),
                                tint     = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(userName,
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(jobTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    if (role.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(role,
                                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style      = MaterialTheme.typography.labelSmall,
                                color      = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                IconButton(onClick = onProfileClick) {
                    Icon(Icons.Default.Edit, "Edit",
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                }
            }

            if (companyName.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Business, null,
                        modifier = Modifier.size(14.dp),
                        tint     = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                    Text(companyName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(experienceYears,   "Experience", Icons.Outlined.Work)
                VerticalDivider(Modifier.height(40.dp).width(1.dp))
                StatItem(completedProjects, "Completed",  Icons.Outlined.CheckCircle)
                VerticalDivider(Modifier.height(40.dp).width(1.dp))
                StatItem(activeProjects,    "Active",     Icons.Outlined.Pending)
            }
        }
    }
}

// ── Performance Summary ───────────────────────────────────────
@Composable
fun PerformanceSummaryCard(profile: UserProfileData) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Performance Overview",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                ProgressMetric(Modifier.weight(1f), "Project Rate",
                    (profile.performanceScore / 100.0).toFloat().coerceIn(0f, 1f),
                    MaterialTheme.colorScheme.primary,
                    "${profile.performanceScore.toInt()}%")
                ProgressMetric(Modifier.weight(1f), "Resolution Rate",
                    (profile.complaintsRate / 100.0).toFloat().coerceIn(0f, 1f),
                    Color(0xFF00796B),
                    "${profile.complaintsRate.toInt()}%")
            }
        }
    }
}

@Composable
fun ProgressMetric(modifier: Modifier, label: String, value: Float, color: Color, percent: String) {
    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label,   style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(percent, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress   = { value },
            modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color      = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}

// ── Stat Item ─────────────────────────────────────────────────
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
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
    }
}

// ── Feature Card ──────────────────────────────────────────────
@Composable
fun FeatureCard(title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "cardScale"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth().height(130.dp)
            .scale(scale)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clickable { pressed = true; onClick() },
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier            = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Box(
                modifier = Modifier.size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
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

// ── Copyright Section ─────────────────────────────────────────
@Composable
fun CopyrightSection(developerName: String) {
    val heartScale by rememberInfiniteTransition(label = "heart").animateFloat(
        initialValue  = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heartScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier            = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    ) {
        HorizontalDivider(modifier = Modifier.width(80.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        Spacer(Modifier.height(4.dp))
        Text("© 2025 $developerName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Text("All rights reserved",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Made with", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Icon(Icons.Default.Favorite, "Love",
                tint = Color.Red, modifier = Modifier.size(14.dp).scale(heartScale))
            Text("by $developerName", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

// ── Profile Dialog ────────────────────────────────────────────
@Composable
fun ProfileDialog(onDismiss: () -> Unit, onSeeProfile: () -> Unit, onLogout: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Profile Options", fontWeight = FontWeight.Bold) },
        text  = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSeeProfile() }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text("View Profile", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onLogout() }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(16.dp))
                    Text("Logout", fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

data class FeatureItem(val id: Int, val title: String, val icon: ImageVector, val color: Color)