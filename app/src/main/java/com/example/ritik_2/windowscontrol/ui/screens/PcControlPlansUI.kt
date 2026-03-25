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
import com.example.ritik_2.windowscontrol.data.PcPlan
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

    // Single Toast — no duplicate snackbar
    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is PcUiState.Success -> {
                android.widget.Toast.makeText(context,
                    "✅ ${s.message}", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetUiState()
            }
            is PcUiState.Error -> {
                android.widget.Toast.makeText(context,
                    "❌ ${s.message}", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetUiState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Plans", fontWeight = FontWeight.Bold) },
                actions = {
                    PcConnectionChip(status = connectionStatus, onClick = { viewModel.pingPc() })
                    Spacer(Modifier.width(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.startNewPlan() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "New Plan", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        if (plans.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("⚡", fontSize = 48.sp)
                    Text("No plans yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap + to create your first plan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "Swipe right to execute  •  Hold + return left to cancel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
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
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SWIPEABLE PLAN CARD
//  Swipe right → execute
//  Hold + drag back left before releasing → cancel
// ─────────────────────────────────────────────────────────────

@Composable
fun SwipeablePlanCard(
    plan       : PcPlan,
    isExecuting: Boolean,
    onExecute  : () -> Unit,
    onEdit     : () -> Unit,
    onDelete   : () -> Unit
) {
    val scope     = rememberCoroutineScope()
    val offsetX   = remember { Animatable(0f) }
    var triggered by remember { mutableStateOf(false) }
    var showMenu  by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    val density   = LocalDensity.current
    val threshold = with(density) { 120.dp.toPx() }   // swipe distance to trigger
    val cancelThreshold = with(density) { -40.dp.toPx() } // drag back = cancel

    // Color lerp based on swipe distance
    val progress = (offsetX.value / threshold).coerceIn(0f, 1f)
    val cardColor = lerp(
        MaterialTheme.colorScheme.surface,
        Color(0xFF4ADE80).copy(0.25f),
        progress
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        // Background — shows Execute label
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF4ADE80).copy(0.15f * progress + 0.05f)),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null,
                    tint = Color(0xFF4ADE80).copy(progress),
                    modifier = Modifier.size(22.dp))
                Text("Execute",
                    color = Color(0xFF4ADE80).copy(progress),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge)
            }
        }

        // Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val newVal = (offsetX.value + delta).coerceAtLeast(0f)
                            offsetX.snapTo(newVal)
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            when {
                                // Dragged far enough right = execute
                                offsetX.value >= threshold && !triggered -> {
                                    triggered = true
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                    triggered = false
                                    onExecute()
                                }
                                // Dragged back to left = cancel
                                velocity < -500f || offsetX.value < cancelThreshold -> {
                                    offsetX.animateTo(0f, spring())
                                }
                                // Not enough = snap back
                                else -> {
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                }
                            }
                        }
                    }
                ),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Plan name + step count only (no step icons, no expansion)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        plan.planName,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val stepCount = plan.steps.size
                    Text(
                        "$stepCount step${if (stepCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Execute button (tap shortcut)
                if (isExecuting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    FilledIconButton(
                        onClick = onExecute,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, "Run",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                // Menu
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, "Options", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { showMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null,
                                    tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { showMenu = false; showConfirm = true }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete plan?") },
            text  = { Text("\"${plan.planName}\" will be permanently deleted.") },
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

private fun lerp(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red   = start.red   + (end.red   - start.red)   * f,
        green = start.green + (end.green - start.green) * f,
        blue  = start.blue  + (end.blue  - start.blue)  * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}

// ─────────────────────────────────────────────────────────────
//  CONNECTION CHIP — reused across screens
// ─────────────────────────────────────────────────────────────

@Composable
fun PcConnectionChip(status: PcConnectionStatus, onClick: () -> Unit) {
    val (color, label) = when (status) {
        PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80) to "Online"
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