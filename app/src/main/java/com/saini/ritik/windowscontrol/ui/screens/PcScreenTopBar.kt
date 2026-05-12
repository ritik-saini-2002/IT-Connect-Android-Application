package com.saini.ritik.windowscontrol.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saini.ritik.theme.ITConnectGlass
import com.saini.ritik.windowscontrol.viewmodel.PcConnectionStatus

/**
 * Shared glass-style header used by ALL PC-control screens.
 * Replaces the old TopAppBar with a glassmorphism box that matches
 * the File Browser header style.
 *
 * Features:
 * - Glass background with border (dark/light aware)
 * - Connection status chip (tap to ping)
 * - Optional navigation icon and extra actions
 * - Status bar padding built in
 */
@Composable
fun PcScreenTopBar(
    title            : String,
    connectionStatus : PcConnectionStatus,
    onPing           : () -> Unit,
    navigationIcon   : (@Composable () -> Unit)?        = null,
    extraActions     : (@Composable RowScope.() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val glassBg     = if (isDark) ITConnectGlass.darkGlassBg else ITConnectGlass.lightGlassBg
    val glassBorder = if (isDark) ITConnectGlass.darkGlassBorder else ITConnectGlass.lightGlassBorder
    val accent      = if (isDark) ITConnectGlass.darkAccentBlue else ITConnectGlass.lightAccentBlue

    val (chipColor, chipLabel) = when (connectionStatus) {
        PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80) to "Online"
        PcConnectionStatus.OFFLINE  -> Color(0xFFFF6B6B) to "Offline"
        PcConnectionStatus.CHECKING -> Color(0xFFFBBF24) to "..."
        PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onSurface.copy(0.4f) to "Ping"
    }

    Surface(
        color  = MaterialTheme.colorScheme.surfaceVariant,
        shape  = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            navigationIcon?.invoke()

            Text(
                text       = title,
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.weight(1f),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )

            extraActions?.invoke(this)

            Surface(
                onClick = onPing,
                shape   = RoundedCornerShape(12.dp),
                color   = chipColor.copy(alpha = 0.15f),
            ) {
                Text(
                    text       = "● $chipLabel",
                    modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = chipColor
                )
            }
        }
    }
}