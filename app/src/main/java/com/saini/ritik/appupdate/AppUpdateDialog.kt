package com.saini.ritik.appupdate

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties



/**
 * Shown to users when a newer app version is available on the server.
 *
 * [versionName]  — display string e.g. "1.3.0"
 * [releaseNotes] — optional change log text; hidden if blank
 * [downloadProgress] — null while idle, 0f..1f during download
 * [isForced]     — hides the "Later" button when min_version_code enforcement is needed
 * [onDownload]   — called when user taps "Update Now"
 * [onDismiss]    — called when user taps "Later" (only available when !isForced)
 */
@Composable
fun AppUpdateDialog(
    versionName      : String,
    releaseNotes     : String,
    downloadProgress : Float?     = null,
    isForced         : Boolean    = false,
    onDownload       : () -> Unit,
    onDismiss        : () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    // Pulse animation on the icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    Dialog(
        onDismissRequest = { if (!isForced && downloadProgress == null) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress    = !isForced,
            dismissOnClickOutside = !isForced
        )
    ) {
        Card(
            shape  = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Icon with gradient background ───────────────────────
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(iconScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(cs.primary.copy(0.3f), cs.tertiary.copy(0.1f)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SystemUpdateAlt,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // ── Text ────────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Update Available",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        color = cs.primary.copy(0.1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            "Version $versionName",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.primary
                        )
                    }
                }

                // ── Release notes ───────────────────────────────────────
                if (releaseNotes.isNotBlank()) {
                    Surface(
                        color  = cs.surfaceVariant,
                        shape  = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            releaseNotes,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            color = cs.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    Text(
                        "A new version of ITConnect is ready to install.",
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // ── Download progress bar ───────────────────────────────
                if (downloadProgress != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Downloading…", fontSize = 12.sp, color = cs.onSurfaceVariant)
                            Text("${(downloadProgress * 100).toInt()}%",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                        }
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = cs.primary,
                            trackColor = cs.primary.copy(0.15f)
                        )
                    }
                }

                // ── Buttons ─────────────────────────────────────────────
                if (downloadProgress == null) {
                    Button(
                        onClick  = onDownload,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = cs.primary)
                    ) {
                        Text("Update Now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    if (!isForced) {
                        TextButton(
                            onClick  = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Later", color = cs.onSurfaceVariant, fontSize = 13.sp)
                        }
                    } else {
                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "This update is required to continue using ITConnect.",
                                modifier = Modifier.padding(10.dp),
                                fontSize = 11.sp,
                                color = Color(0xFFE65100),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
