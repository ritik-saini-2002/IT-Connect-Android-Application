package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.data.PcStepSerializer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcUiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────
//  PLANS SCREEN
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlPlansUI(viewModel: PcControlViewModel) {

    val plans            by viewModel.plans.observeAsState(emptyList())
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
    val context           = LocalContext.current

    // Toast on success / error
    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is PcUiState.Success -> {
                android.widget.Toast.makeText(context, "✅ ${s.message}",
                    android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetUiState()
            }
            is PcUiState.Error -> {
                android.widget.Toast.makeText(context, "❌ ${s.message}",
                    android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetUiState()
            }
            else -> {}
        }
    }

    // State for file picker dialog
    var pickFileForPlan      by remember { mutableStateOf<PcPlan?>(null) }
    var pickFileAndExecute   by remember { mutableStateOf<PcPlan?>(null) }
    var filePickerPath       by remember { mutableStateOf("C:/") }
    var filePickerItems      by remember { mutableStateOf<List<com.example.ritik_2.windowscontrol.data.PcFileItem>>(emptyList()) }
    var filePickerLoading    by remember { mutableStateOf(false) }
    val scope                = rememberCoroutineScope()

    // Load dir when path changes in picker
    LaunchedEffect(filePickerPath) {
        if (pickFileForPlan != null || pickFileAndExecute != null) {
            filePickerLoading = true
            filePickerItems   = viewModel.browsePickerDir(filePickerPath)
            filePickerLoading = false
        }
    }

    Scaffold(
        topBar = {
            PcScreenTopBar(
                title            = "Plans",
                connectionStatus = connectionStatus,
                onPing           = { viewModel.pingPc() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { viewModel.startNewPlan() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "New Plan", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        if (plans.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("⚡", fontSize = 48.sp)
                    Text("No plans yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap + to create your first automation",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                        textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(padding),
                contentPadding      = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "← Swipe right to execute  •  Long-press for more options",
                        style     = MaterialTheme.typography.labelSmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                        modifier  = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                items(plans, key = { it.planId }) { plan ->
                    SwipeablePlanCard(
                        plan      = plan,
                        onExecute = { viewModel.executePlan(plan) },
                        onEdit    = { viewModel.startEditPlan(plan) },
                        onDelete  = { viewModel.deletePlan(plan) },
                        onAddFileAndExecute = { p ->
                            filePickerPath = "C:/"
                            pickFileAndExecute = p
                        },
                        onAddFileToPlan = { p ->
                            filePickerPath = "C:/"
                            pickFileForPlan = p
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SHARED TOP BAR  — used by all screens
// ─────────────────────────────────────────────────────────────

@Composable
fun PcScreenTopBar(
    title            : String,
    connectionStatus : PcConnectionStatus,
    onPing           : () -> Unit,
    navigationIcon   : (@Composable () -> Unit)? = null,
    extraActions     : (@Composable RowScope.() -> Unit)? = null
) {
    val (chipColor, chipLabel) = when (connectionStatus) {
        PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80) to "Online"
        PcConnectionStatus.OFFLINE  -> Color(0xFFFF6B6B) to "Offline"
        PcConnectionStatus.CHECKING -> Color(0xFFFBBF24) to "..."
        PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onPrimary.copy(0.55f) to "Ping"
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            navigationIcon?.invoke()

            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                color      = MaterialTheme.colorScheme.onPrimary,
                modifier   = Modifier.weight(1f),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )

            extraActions?.invoke(this)

            Surface(
                onClick = onPing,
                shape   = RoundedCornerShape(20.dp),
                color   = chipColor.copy(0.18f),
                border  = BorderStroke(1.dp, chipColor.copy(0.45f))
            ) {
                Text(
                    "● $chipLabel",
                    modifier   = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = chipColor
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SWIPEABLE PLAN CARD  — swipe right = execute, long-press = menu
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeablePlanCard(
    plan                : PcPlan,
    onExecute           : () -> Unit,
    onEdit              : () -> Unit,
    onDelete            : () -> Unit,
    onAddFileAndExecute : (PcPlan) -> Unit = {},
    onAddFileToPlan     : (PcPlan) -> Unit = {},
) {
    val scope   = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val threshold = with(density) { 120.dp.toPx() }

    var showMenu          by remember { mutableStateOf(false) }
    var showConfirm       by remember { mutableStateOf(false) }
    var showLongPressMenu by remember { mutableStateOf(false) }

    val progress = (offsetX.value / threshold).coerceIn(0f, 1f)
    val cardBg   = lerpColor(
        MaterialTheme.colorScheme.surface,
        Color(0xFF22C55E).copy(alpha = 0.15f),
        progress
    )

    Box(modifier = Modifier.fillMaxWidth()) {

        // ── Swipe reveal background ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Color(0xFF22C55E).copy(alpha = (0.05f + 0.2f * progress).coerceIn(0f, 1f))
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            AnimatedVisibility(
                visible  = progress > 0.1f,
                enter    = fadeIn() + slideInHorizontally { -it / 2 },
                exit     = fadeOut(),
                modifier = Modifier.padding(start = 20.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null,
                        tint     = Color(0xFF22C55E),
                        modifier = Modifier.size(24.dp))
                    if (progress > 0.35f) {
                        Text("Execute",
                            color      = Color(0xFF22C55E),
                            fontWeight = FontWeight.Bold,
                            style      = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // ── Card ──────────────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceAtLeast(0f))
                        }
                    },
                    onDragStopped = { _ ->
                        scope.launch {
                            if (offsetX.value >= threshold) {
                                offsetX.animateTo(threshold * 1.1f, tween(80))
                                offsetX.animateTo(0f, spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness    = Spring.StiffnessMedium
                                ))
                                onExecute()
                            } else {
                                offsetX.animateTo(0f, spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness    = Spring.StiffnessMedium
                                ))
                            }
                        }
                    }
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { showLongPressMenu = true }
                    )
                },
            shape     = RoundedCornerShape(14.dp),
            colors    = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            // Title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(plan.planName,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis)
                    val cnt = plan.steps.size
                    Text("$cnt step${if (cnt != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // 3-dot menu
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, "Options", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded         = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text        = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick     = { showMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text        = { Text("Delete",
                                color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null,
                                tint = MaterialTheme.colorScheme.error) },
                            onClick     = { showMenu = false; showConfirm = true }
                        )
                    }
                }
            }

            // File step rows
            val fileSteps = plan.steps.filter { it.hasFilePath() }
            if (fileSteps.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                Column(
                    modifier            = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    fileSteps.forEach { step ->
                        val path    = step.filePathArg()
                        val name    = path.substringAfterLast('/').substringAfterLast('\\').take(32)
                        val isMedia = step.fileExtension() in
                                listOf("mp4","mkv","avi","mov","mp3","wav","flac","aac")
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(step.fileIcon(), fontSize = 13.sp)
                            Text(name,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isMedia) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    if (isMedia) "▶ Play" else "Open",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = if (isMedia) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Long-press context dialog ─────────────────────────────────────────────
    if (showLongPressMenu) {
        AlertDialog(
            onDismissRequest = { showLongPressMenu = false },
            icon    = { Text("⚡", fontSize = 24.sp) },
            title   = {
                Text(plan.planName,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What would you like to do?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // 1. Execute now
                    Surface(
                        onClick  = { showLongPressMenu = false; onExecute() },
                        shape    = RoundedCornerShape(10.dp),
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Execute Plan", fontWeight = FontWeight.SemiBold)
                                Text("Run all ${plan.steps.size} steps now",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // 2. Pick file + execute with plan
                    Surface(
                        onClick  = { showLongPressMenu = false; onAddFileAndExecute(plan) },
                        shape    = RoundedCornerShape(10.dp),
                        color    = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.PlayCircle, null,
                                tint     = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Pick File + Execute", fontWeight = FontWeight.SemiBold)
                                Text("Choose a file and run it with this plan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // 3. Add file step to saved plan
                    Surface(
                        onClick  = { showLongPressMenu = false; onAddFileToPlan(plan) },
                        shape    = RoundedCornerShape(10.dp),
                        color    = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.PlaylistAdd, null,
                                tint     = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Add File to Plan", fontWeight = FontWeight.SemiBold)
                                Text("Insert an OPEN_FILE step and save",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // 4. Edit
                    Surface(
                        onClick  = { showLongPressMenu = false; onEdit() },
                        shape    = RoundedCornerShape(10.dp),
                        color    = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Edit, null,
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp))
                            Text("Edit Plan",
                                fontWeight = FontWeight.SemiBold,
                                modifier   = Modifier.weight(1f))
                        }
                    }

                    // 5. Delete
                    Surface(
                        onClick  = { showLongPressMenu = false; showConfirm = true },
                        shape    = RoundedCornerShape(10.dp),
                        color    = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, null,
                                tint     = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp))
                            Text("Delete Plan",
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.error,
                                modifier   = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton  = {},
            dismissButton  = {
                TextButton(onClick = { showLongPressMenu = false }) { Text("Cancel") }
            }
        )
    }

    // ── Delete confirm ────────────────────────────────────────────────────────
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title            = { Text("Delete plan?") },
            text             = {
                Text("\"${plan.planName}\" will be permanently deleted.")
            },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  CONNECTION CHIP  (standalone, used by other screens)
// ─────────────────────────────────────────────────────────────

@Composable
fun PcConnectionChip(status: PcConnectionStatus, onClick: () -> Unit) {
    val (color, label) = when (status) {
        PcConnectionStatus.ONLINE   -> Color(0xFF22C55E) to "Online"
        PcConnectionStatus.OFFLINE  -> MaterialTheme.colorScheme.error to "Offline"
        PcConnectionStatus.CHECKING -> Color(0xFFF59E0B) to "Checking"
        PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onSurfaceVariant to "Ping"
    }
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(20.dp),
        color   = color.copy(0.12f),
        border  = BorderStroke(1.dp, color.copy(0.35f))
    ) {
        Text(
            "● $label",
            modifier   = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color      = color
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  COLOR LERP HELPER
// ─────────────────────────────────────────────────────────────

internal fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red   = start.red   + (end.red   - start.red)   * f,
        green = start.green + (end.green - start.green) * f,
        blue  = start.blue  + (end.blue  - start.blue)  * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}

// ─────────────────────────────────────────────────────────────
//  FILE PICKER HELPERS
// ─────────────────────────────────────────────────────────────

private fun fileIconForExt(ext: String): String = when (ext.lowercase()) {
    "mp4","mkv","avi","mov","wmv"         -> "🎬"
    "mp3","wav","flac","aac","m4a"         -> "🎵"
    "jpg","jpeg","png","gif","bmp","webp"  -> "🖼️"
    "pdf"                                  -> "📕"
    "doc","docx","rtf"                     -> "📘"
    "xls","xlsx","csv"                     -> "📗"
    "ppt","pptx"                           -> "📊"
    "txt","log","md"                       -> "📄"
    "zip","rar","7z","tar","gz"            -> "🗜️"
    "py","bat","ps1","sh","cmd"            -> "⚙️"
    "exe","msi"                            -> "🖥️"
    else                                    -> "📄"
}

private fun formatSizeKb(kb: Long): String = when {
    kb > 1024 * 1024 -> "${kb / (1024 * 1024)} GB"
    kb > 1024        -> "${kb / 1024} MB"
    else             -> "$kb KB"
}