package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlPlansUI(viewModel: PcControlViewModel) {

    val plans            by viewModel.plans.observeAsState(emptyList())
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
    val isExecuting       = uiState is PcUiState.Loading
    val context           = LocalContext.current

    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is PcUiState.Success -> {
                android.widget.Toast.makeText(context, "✅ ${s.message}", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetUiState()
            }
            is PcUiState.Error -> {
                android.widget.Toast.makeText(context, "❌ ${s.message}", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetUiState()
            }
            else -> {}
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
                        "← Swipe right to execute",
                        style     = MaterialTheme.typography.labelSmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                        modifier  = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                items(plans, key = { it.planId }) { plan ->
                    SwipeablePlanCard(
                        plan        = plan,
                        isExecuting = isExecuting,
                        onExecute   = { viewModel.executePlan(plan) },
                        onEdit      = { viewModel.startEditPlan(plan) },
                        onDelete    = { viewModel.deletePlan(plan) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SHARED TOP BAR — used by ALL screens
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcScreenTopBar(
    title            : String,
    connectionStatus : PcConnectionStatus,
    onPing           : () -> Unit,
    navigationIcon   : (@Composable () -> Unit)? = null,
    extraActions     : (@Composable RowScope.() -> Unit)? = null
) {
    TopAppBar(
        navigationIcon = { navigationIcon?.invoke() },
        title = {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                style      = MaterialTheme.typography.titleMedium,
                color      = MaterialTheme.colorScheme.onPrimary
            )
        },
        actions = {
            extraActions?.invoke(this)
            // Connection chip in top bar — same style as sidebar
            val (color, label) = when (connectionStatus) {
                PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80) to "Online"
                PcConnectionStatus.OFFLINE  -> Color(0xFFFF6B6B) to "Offline"
                PcConnectionStatus.CHECKING -> Color(0xFFFBBF24) to "Checking"
                PcConnectionStatus.UNKNOWN  -> Color.White.copy(0.6f) to "Ping"
            }
            Surface(
                onClick = onPing,
                shape   = RoundedCornerShape(20.dp),
                color   = color.copy(0.18f),
                border  = BorderStroke(1.dp, color.copy(0.5f)),
                modifier = Modifier.padding(end = 10.dp)
            ) {
                Text(
                    "● $label",
                    modifier   = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = color
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor    = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor     = MaterialTheme.colorScheme.onPrimary
        )
    )
}

// ─────────────────────────────────────────────────────────────
//  SWIPEABLE PLAN CARD
// ─────────────────────────────────────────────────────────────

@Composable
fun SwipeablePlanCard(
    plan       : PcPlan,
    isExecuting: Boolean,
    onExecute  : () -> Unit,
    onEdit     : () -> Unit,
    onDelete   : () -> Unit
) {
    val scope       = rememberCoroutineScope()
    val offsetX     = remember { Animatable(0f) }
    val density     = LocalDensity.current
    val threshold   = with(density) { 120.dp.toPx() }
    var showMenu    by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val progress  = (offsetX.value / threshold).coerceIn(0f, 1f)
    val cardBg    = lerpColor(
        MaterialTheme.colorScheme.surface,
        Color(0xFF22C55E).copy(alpha = 0.15f),
        progress
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        // Reveal background
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF22C55E).copy(alpha = (0.05f + 0.2f * progress).coerceIn(0f, 1f))),
            contentAlignment = Alignment.CenterStart
        ) {
            AnimatedVisibility(
                visible = progress > 0.1f,
                enter   = fadeIn() + slideInHorizontally { -it / 2 },
                exit    = fadeOut(),
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
                        Text("Execute", color = Color(0xFF22C55E),
                            fontWeight = FontWeight.Bold,
                            style      = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val next = (offsetX.value + delta).coerceAtLeast(0f)
                            offsetX.snapTo(next)
                        }
                    },
                    onDragStopped = { _ ->
                        scope.launch {
                            if (offsetX.value >= threshold && !isExecuting) {
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
                ),
            shape     = RoundedCornerShape(14.dp),
            colors    = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
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

                if (isExecuting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    FilledIconButton(
                        onClick  = onExecute,
                        modifier = Modifier.size(36.dp),
                        colors   = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, "Run",
                            modifier = Modifier.size(18.dp),
                            tint     = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, "Options", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text        = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick     = { showMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text        = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick     = { showMenu = false; showConfirm = true }
                        )
                    }
                }
            }

            // File step rows
            val fileSteps = plan.steps.filter { it.hasFilePath() }
            if (fileSteps.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    fileSteps.forEach { step ->
                        val path = step.filePathArg()
                        val name = path.substringAfterLast('/').substringAfterLast('\\').take(32)
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

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title            = { Text("Delete plan?") },
            text             = { Text("\"${plan.planName}\" will be permanently deleted.") },
            confirmButton    = {
                TextButton(onClick = { showConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  CONNECTION CHIP
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

internal fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red   = start.red   + (end.red   - start.red)   * f,
        green = start.green + (end.green - start.green) * f,
        blue  = start.blue  + (end.blue  - start.blue)  * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}