package com.saini.ritik.appupdate

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Purple700 = Color(0xFF6200EA)
private val Purple300 = Color(0xFFB39DDB)

/**
 * Animated update-available banner shown at the top of the main screen.
 *
 * [versionName]  — new version string to display (e.g. "1.3.0")
 * [onTapToUpdate] — navigates to Help & Support / ContactActivity
 * [onDismiss]    — hides the banner for this session (does NOT permanently dismiss)
 */
@Composable
fun UpdateNoticeBanner(
    versionName  : String,
    onTapToUpdate: () -> Unit,
    onDismiss    : () -> Unit,
    modifier     : Modifier = Modifier
) {
    // Pulse animation on the icon dot
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        modifier  = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onTapToUpdate),
        shape     = RoundedCornerShape(14.dp),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        color     = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(Purple700, Color(0xFF9C27B0))
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pulsing icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SystemUpdateAlt,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        "Update Available — v$versionName",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp,
                        color      = Color.White
                    )
                    Text(
                        "Tap to open Help & Support to install",
                        fontSize = 11.sp,
                        color    = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Dismiss X
                IconButton(
                    onClick  = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint     = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}