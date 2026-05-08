package com.example.ritik_2.contact

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.appupdate.UpdateInfo  // ✅ FIX 1: use your own UpdateInfo, not androidx.security.state
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// Colors derived from MaterialTheme.colorScheme at each composable site

/**
 * UpdateCheckState — drives the update banner UI.
 * Produced by ContactActivity and passed down as state.
 */
sealed class UpdateCheckState {
    object Idle        : UpdateCheckState()
    object Checking    : UpdateCheckState()
    object UpToDate    : UpdateCheckState()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckState()
    data class Downloading(val progress: Float)      : UpdateCheckState()
    data class Error(val message: String)            : UpdateCheckState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(
    onEmailClick      : (String) -> Unit,
    onPhoneClick      : (String) -> Unit,
    onLocationClick   : (String) -> Unit,
    // ── Update-related parameters (all optional — safe for existing callers) ──
    currentVersionName: String           = "–",
    currentVersionCode: Int              = 0,
    updateCheckState  : UpdateCheckState = UpdateCheckState.Idle,
    onCheckForUpdate  : () -> Unit       = {},
    onInstallUpdate   : (UpdateInfo) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var isVisible by remember { mutableStateOf(false) }

    val developerInfo = DeveloperInfo(
        name    = "Ritik Saini",
        email   = "ritiksaini19757@gmail.com",
        phone   = "8279991971",
        address = "Roorkee",
        role    = "Developer & Administrator"
    )

    LaunchedEffect(Unit) { delay(300); isVisible = true }

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "bgOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                ))
            )
    ) {
        AnimatedBackgroundParticles(animatedOffset)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            // ✅ FIX 2: slideInVertically — args were swapped AND closing brace was missing
            AnimatedVisibility(
                visible = isVisible,
                enter   = slideInVertically(
                    animationSpec  = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                    initialOffsetY = { -it }
                ) + fadeIn(tween(800))
            ) { HeaderSection() }

            Spacer(Modifier.height(32.dp))

            // ✅ FIX 3: same swap corrected for all remaining slideInVertically calls
            AnimatedVisibility(
                visible = isVisible,
                enter   = slideInVertically(
                    animationSpec  = tween(durationMillis = 1000, delayMillis = 200, easing = FastOutSlowInEasing),
                    initialOffsetY = { it }
                ) + fadeIn(tween(1000, 200))
            ) { DeveloperProfileCard(developerInfo) }

            Spacer(Modifier.height(24.dp))

            // ── App Version & Update Section ──────────────────────────────────
            AnimatedVisibility(
                visible = isVisible,
                enter   = slideInVertically(
                    animationSpec  = tween(durationMillis = 1100, delayMillis = 300, easing = FastOutSlowInEasing),
                    initialOffsetY = { it }
                ) + fadeIn(tween(1100, 300))
            ) {
                AppVersionSection(
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    updateCheckState   = updateCheckState,
                    onCheckForUpdate   = onCheckForUpdate,
                    onInstallUpdate    = onInstallUpdate
                )
            }

            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter   = slideInVertically(
                    animationSpec  = tween(durationMillis = 1200, delayMillis = 400, easing = FastOutSlowInEasing),
                    initialOffsetY = { it }
                ) + fadeIn(tween(1200, 400))
            ) {
                ContactOptionsSection(developerInfo, onEmailClick, onPhoneClick, onLocationClick)
            }

            Spacer(Modifier.height(32.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter   = fadeIn(tween(1400, 600))
            ) { CopyrightSection(developerInfo.name) }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── App Version Section ───────────────────────────────────────────────────────

@Composable
private fun AppVersionSection(
    currentVersionName: String,
    currentVersionCode: Int,
    updateCheckState  : UpdateCheckState,
    onCheckForUpdate  : () -> Unit,
    onInstallUpdate   : (UpdateInfo) -> Unit
) {
    val cs = MaterialTheme.colorScheme

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Header row ────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color  = cs.primaryContainer,
                    shape  = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.SystemUpdateAlt, null,
                            tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("App Version", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("ITConnect Android", fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
                // Current version chip
                Surface(
                    color = cs.primaryContainer,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "v$currentVersionName",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize  = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color     = cs.onPrimaryContainer
                    )
                }
            }

            HorizontalDivider(color = cs.outlineVariant.copy(0.4f))

            // ── Status area ───────────────────────────────────────────────────
            when (updateCheckState) {

                UpdateCheckState.Idle -> {
                    UpdateStateRow(
                        icon    = Icons.Outlined.Info,
                        iconTint = cs.onSurfaceVariant,
                        text    = "Build $currentVersionCode  •  Tap to check for updates",
                        textColor = cs.onSurfaceVariant
                    )
                    Button(
                        onClick  = onCheckForUpdate,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = cs.primary)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Check for Updates", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                UpdateCheckState.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier  = Modifier.size(20.dp),
                            color     = cs.primary,
                            strokeWidth = 2.dp
                        )
                        Text("Checking for updates…", fontSize = 13.sp, color = cs.onSurfaceVariant)
                    }
                }

                UpdateCheckState.UpToDate -> {
                    UpdateStateRow(
                        icon    = Icons.Default.CheckCircle,
                        iconTint = cs.tertiary,
                        text    = "You're on the latest version  (build $currentVersionCode)",
                        textColor = cs.tertiary
                    )
                    OutlinedButton(
                        onClick  = onCheckForUpdate,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Re-check", fontSize = 13.sp)
                    }
                }

                is UpdateCheckState.UpdateAvailable -> {
                    val info = updateCheckState.info

                    Surface(
                        color = cs.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.NewReleases, null,
                                    tint = cs.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    "Version ${info.versionName} available",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = cs.onPrimaryContainer
                                )
                            }
                            if (info.releaseNotes.isNotBlank()) {
                                Text(
                                    info.releaseNotes,
                                    fontSize  = 12.sp,
                                    color     = cs.onSurfaceVariant,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    Button(
                        onClick  = { onInstallUpdate(info) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = cs.primary)
                    ) {
                        Icon(Icons.Default.SystemUpdateAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Install Update  →  v${info.versionName}",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                is UpdateCheckState.Downloading -> {
                    val progress = updateCheckState.progress
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier  = Modifier.size(16.dp),
                                    color     = cs.primary,
                                    strokeWidth = 2.dp
                                )
                                Text("Downloading update…", fontSize = 13.sp, color = cs.onSurfaceVariant)
                            }
                            Text(
                                "${(progress * 100).toInt()}%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = cs.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress    = { progress },
                            modifier    = Modifier.fillMaxWidth(),
                            color       = cs.primary,
                            trackColor  = cs.primaryContainer
                        )
                        Text(
                            "Do not close the app during download",
                            fontSize = 11.sp,
                            color = cs.onSurfaceVariant.copy(0.6f)
                        )
                    }
                }

                is UpdateCheckState.Error -> {
                    UpdateStateRow(
                        icon     = Icons.Default.ErrorOutline,
                        iconTint = cs.error,
                        text     = updateCheckState.message,
                        textColor = cs.error
                    )
                    OutlinedButton(
                        onClick  = onCheckForUpdate,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateStateRow(
    icon     : ImageVector,
    iconTint : Color,
    text     : String,
    textColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 12.sp, color = textColor, lineHeight = 16.sp)
    }
}

// ── Everything below is UNCHANGED from original ContactScreen ─────────────────

@Composable
private fun AnimatedBackgroundParticles(offset: Float) {
    val particleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        repeat(8) { index ->
            val angle  = (offset + index * 45f) * (Math.PI / 180f)
            val radius = size.minDimension * 0.3f + (index * 20f)
            val x = size.width / 2f + (radius * cos(angle)).toFloat()
            val y = size.height / 2f + (radius * sin(angle)).toFloat()
            drawCircle(particleColor, radius = 4f + (index * 2f), center = Offset(x, y))
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val scale by rememberInfiniteTransition(label = "hdr").animateFloat(
            1f, 1.1f,
            infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "hdrScale"
        )
        Card(
            modifier = Modifier.size(80.dp).scale(scale),
            shape    = CircleShape,
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Code, null, Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("About Developer", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Application Information", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeveloperProfileCard(developerInfo: DeveloperInfo) {
    var isHovered by remember { mutableStateOf(false) }
    Card(
        modifier  = Modifier.fillMaxWidth().clickable { isHovered = !isHovered },
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(if (isHovered) 16.dp else 8.dp)
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(Modifier.size(100.dp), CircleShape,
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Icon(Icons.Default.Person, null, Modifier.size(50.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(developerInfo.name, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(developerInfo.role, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Android", "Kotlin", "Jetpack Compose").forEach { skill ->
                    AssistChip({ }, { Text(skill, fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ContactOptionsSection(
    developerInfo  : DeveloperInfo,
    onEmailClick   : (String) -> Unit,
    onPhoneClick   : (String) -> Unit,
    onLocationClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Get in Touch", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary)
        ContactOptionItem(Icons.Default.Email,      "Email",    developerInfo.email)   { onEmailClick(developerInfo.email) }
        ContactOptionItem(Icons.Default.Phone,      "Phone",    developerInfo.phone)   { onPhoneClick(developerInfo.phone) }
        ContactOptionItem(Icons.Default.LocationOn, "Location", developerInfo.address) { onLocationClick(developerInfo.address) }
    }
}

@Composable
private fun ContactOptionItem(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    Card(
        modifier  = Modifier.fillMaxWidth().clickable { isPressed = !isPressed; onClick() },
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(Modifier.size(48.dp), CircleShape) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Icon(icon, title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, "Go", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CopyrightSection(developerName: String) {
    val heartScale by rememberInfiniteTransition(label = "heart").animateFloat(
        1f, 1.2f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heartScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Divider(Modifier.width(100.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
        Text("© 2024 $developerName", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text("All rights reserved", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f), textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Made with", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
            Icon(Icons.Default.Favorite, "Love", tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp).scale(heartScale))
            Text("by Ritik Saini", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
        }
    }
}

data class DeveloperInfo(
    val name   : String,
    val email  : String,
    val phone  : String,
    val address: String,
    val role   : String
)