package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.data.PcPlan
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlPlansUI(viewModel: PcControlViewModel) {

    val plans            by viewModel.plans.observeAsState(emptyList())
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
    val isExecuting       = uiState is PcUiState.Loading
    val snackbarState     = remember { SnackbarHostState() }

    // Show feedback snackbar
    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is PcUiState.Success -> {
                snackbarState.showSnackbar(s.message)
                viewModel.resetUiState()
            }
            is PcUiState.Error -> {
                snackbarState.showSnackbar(s.message)
                viewModel.resetUiState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = { Text("⚡ My Plans", fontWeight = FontWeight.Bold) },
                actions = {
                    PcConnectionChip(
                        status  = connectionStatus,
                        onClick = { viewModel.pingPc() }
                    )
                    Spacer(Modifier.width(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.startNewPlan() },
                icon    = { Icon(Icons.Default.Add, null) },
                text    = { Text("New Plan") }
            )
        }
    ) { padding ->
        if (plans.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("🎯", fontSize = 64.sp)
                    Text(
                        "No Plans Yet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Create a plan to automate anything\non your PC with one tap",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { viewModel.startNewPlan() }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create First Plan")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(plans, key = { it.planId }) { plan ->
                    PcPlanCard(
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
//  PLAN CARD
// ─────────────────────────────────────────────────────────────

@Composable
fun PcPlanCard(
    plan: PcPlan,
    isExecuting: Boolean,
    onExecute: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu    by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Text(plan.icon, fontSize = 34.sp)

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    plan.planName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val stepCount = plan.steps.size
                Text(
                    "$stepCount step${if (stepCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Show step types as preview chips
                if (plan.steps.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        plan.steps.take(3).forEach { step ->
                            val icon = when (step.type) {
                                "LAUNCH_APP"  -> "▶"
                                "KEY_PRESS"   -> "⌨"
                                "WAIT"        -> "⏱"
                                "SYSTEM_CMD"  -> "⚙"
                                "TYPE_TEXT"   -> "📝"
                                "MOUSE_CLICK" -> "🖱"
                                else          -> "•"
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    icon,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (plan.steps.size > 3) {
                            Text(
                                "+${plan.steps.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Options")
                }
                DropdownMenu(
                    expanded        = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text         = { Text("Edit") },
                        leadingIcon  = { Icon(Icons.Default.Edit, null) },
                        onClick      = { showMenu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text         = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon  = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { showMenu = false; showConfirm = true }
                    )
                }
            }

            // Run button
            Button(
                onClick   = onExecute,
                enabled   = !isExecuting,
                shape     = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title   = { Text("Delete Plan?") },
            text    = { Text("\"${plan.planName}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = { showConfirm = false; onDelete() },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  CONNECTION CHIP
// ─────────────────────────────────────────────────────────────

@Composable
fun PcConnectionChip(status: PcConnectionStatus, onClick: () -> Unit) {
    val (color, label) = when (status) {
        PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80) to "● Online"
        PcConnectionStatus.OFFLINE  -> MaterialTheme.colorScheme.error to "● Offline"
        PcConnectionStatus.CHECKING -> Color(0xFFFBBF24) to "● Checking"
        PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onSurfaceVariant to "● Tap to check"
    }
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(20.dp),
        color   = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style    = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color    = color
        )
    }
}