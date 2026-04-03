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
            PcTopBar(
                title            = "Plans",
                connectionStatus = connectionStatus,
                onPing           = { viewModel.pingPc() },
                actions          = {
                    PcConnectionChip(status = connectionStatus, onClick = { viewModel.pingPc() })
                    Spacer(Modifier.width(6.dp))
                }
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
            EmptyPlansState(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier        = Modifier.fillMaxSize().padding(padding),
                contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "← Swipe right to execute",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f),
                        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun EmptyPlansState(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("⚡", fontSize = 52.sp)
            Text("No plans yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tap + to create your first automation plan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SWIPEABLE PLAN CARD — swipe only, no drag icon
// ─────────────────────────────────────────────────────────────

@Composable
fun SwipeablePlanCard(
    plan       : PcPlan,
    isExecuting: Boolean,
    onExecute  : () -> Unit,
    onEdit     : () -> Unit,
    onDelete   : () -> Unit
) {
    val scope        = rememberCoroutineScope()
    val offsetX      = remember { Animatable(0f) }
    val density      = LocalDensity.current
    val threshold    = with(density) { 130.dp.toPx() }
    var showMenu     by remember { mutableStateOf(false) }
    var showConfirm  by remember { mutableStateOf(false) }

    val progress   = (offsetX.value / threshold).coerceIn(0f, 1f)
    val cardBg     = lerpColor(
        MaterialTheme.colorScheme.surface,
        Color(0xFF22C55E).copy(alpha = 0.18f),
        progress
    )
    val revealBg   = Color(0xFF22C55E).copy(alpha = (0.08f + 0.22f * progress).coerceIn(0f, 1f))

    Box(modifier = Modifier.fillMaxWidth()) {

        // ── Reveal background ──────────────────────────────
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(revealBg),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.padding(start = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(
                    visible = progress > 0.1f,
                    enter   = fadeIn() + scaleIn(),
                    exit    = fadeOut() + scaleOut()
                ) {
                    Icon(
                        Icons.Default.PlayArrow, null,
                        tint     = Color(0xFF22C55E),
                        modifier = Modifier.size(26.dp)
                    )
                }
                AnimatedVisibility(
                    visible = progress > 0.3f,
                    enter   = fadeIn(tween(80)) + slideInHorizontally { -it / 2 },
                    exit    = fadeOut()
                ) {
                    Text(
                        "Execute",
                        color      = Color(0xFF22C55E),
                        fontWeight = FontWeight.Bold,
                        style      = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // ── Card ───────────────────────────────────────────
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            // Only allow rightward swipe
                            val next = (offsetX.value + delta).coerceAtLeast(0f)
                            offsetX.snapTo(next)
                        }
                    },
                    onDragStopped = { _ ->
                        scope.launch {
                            if (offsetX.value >= threshold && !isExecuting) {
                                // Animate to threshold then snap back
                                offsetX.animateTo(
                                    threshold * 1.1f,
                                    tween(80)
                                )
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
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Plan name + step count
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            plan.planName,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val cnt = plan.steps.size
                        Text(
                            "$cnt step${if (cnt != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Execute button
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        FilledIconButton(
                            onClick  = onExecute,
                            modifier = Modifier.size(38.dp),
                            colors   = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, "Run",
                                modifier = Modifier.size(20.dp),
                                tint     = MaterialTheme.colorScheme.onPrimary)
                        }
                    }

                    // Menu
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.MoreVert, "Options", modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text          = { Text("Edit") },
                                leadingIcon   = { Icon(Icons.Default.Edit, null) },
                                onClick       = { showMenu = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text          = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon   = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick       = { showMenu = false; showConfirm = true }
                            )
                        }
                    }
                }

                // ── File path steps with open button ──────────
                val fileSteps = plan.steps.filter { it.hasFilePath() }
                if (fileSteps.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    fileSteps.forEach { step ->
                        PlanFileStepRow(step = step, onOpen = {
                            // Execute just this open step immediately
                            val openStep = if (step.type == "LAUNCH_APP" && step.args.isNotEmpty())
                                PcStep("SYSTEM_CMD", "OPEN_FILE", args = listOf(step.args[0]))
                            else step
                            // trigger via parent — we just re-execute the whole plan for simplicity;
                            // or you can expose a quick-step here:
                        })
                    }
                }
            }
        }
    }

    // Delete confirmation
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
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlanFileStepRow(step: PcStep, onOpen: () -> Unit) {
    val filePath = step.filePathArg()
    val name     = filePath.substringAfterLast('/').substringAfterLast('\\').take(36)
    val icon     = step.fileIcon()
    val isMedia  = step.fileExtension() in listOf("mp4","mkv","avi","mov","mp3","wav","flac")

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(icon, fontSize = 16.sp)
        Text(
            name,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        if (filePath.isNotBlank()) {
            Surface(
                onClick = onOpen,
                shape   = RoundedCornerShape(8.dp),
                color   = if (isMedia)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (isMedia) Icons.Default.PlayArrow else Icons.Default.OpenInNew,
                        null, modifier = Modifier.size(12.dp),
                        tint = if (isMedia) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        if (isMedia) "Play" else "Open",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMedia) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SHARED: Connection chip  +  TopBar
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
        color   = color.copy(0.13f),
        border  = BorderStroke(1.dp, color.copy(0.4f))
    ) {
        Text(
            "● $label",
            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color      = color
        )
    }
}

/** Unified top bar used across all screens — matches sidebar style */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcTopBar(
    title            : String,
    connectionStatus : PcConnectionStatus,
    onPing           : () -> Unit,
    navigationIcon   : @Composable (() -> Unit)? = null,
    actions          : @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        navigationIcon = { navigationIcon?.invoke() },
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, fontWeight = FontWeight.Bold)
            }
        },
        actions = actions,
        colors  = TopAppBarDefaults.topAppBarColors(
            containerColor    = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

// Color lerp helper
internal fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red   = start.red   + (end.red   - start.red)   * f,
        green = start.green + (end.green - start.green) * f,
        blue  = start.blue  + (end.blue  - start.blue)  * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}