package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.BorderStroke
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
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus

/**
 * Shared top bar used by ALL PC-control screens.
 * Consistent height, color, connection chip and optional extra actions.
 * No system status bar is shown — activities hide it via hideSystemBars().
 *
 * IMPORTANT: Remove the duplicate PcScreenTopBar function from
 * PcControlPlansUI.kt to avoid "Conflicting overloads" build error.
 */
@Composable
fun PcScreenTopBar(
    title          : String,
    connectionStatus: PcConnectionStatus,
    onPing         : () -> Unit,
    navigationIcon : (@Composable () -> Unit)?        = null,
    extraActions   : (@Composable RowScope.() -> Unit)? = null
) {
    val (chipColor, chipLabel) = when (connectionStatus) {
        PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80) to "Online"
        PcConnectionStatus.OFFLINE  -> Color(0xFFFF6B6B) to "Offline"
        PcConnectionStatus.CHECKING -> Color(0xFFFBBF24) to "..."
        PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onPrimary.copy(0.55f) to "Ping"
    }

    Surface(
        color          = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
        modifier       = Modifier.fillMaxWidth()
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
                fontSize   = 15.sp,
                color      = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier   = Modifier.weight(1f),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )

            extraActions?.invoke(this)

            Surface(
                onClick = onPing,
                shape   = RoundedCornerShape(20.dp),
                color   = chipColor.copy(alpha = 0.18f),
                border  = BorderStroke(1.dp, chipColor.copy(alpha = 0.45f))
            ) {
                Text(
                    text       = "● $chipLabel",
                    modifier   = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = chipColor
                )
            }
        }
    }
}