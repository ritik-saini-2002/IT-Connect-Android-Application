package com.example.ritik_2.windowscontrol.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.data.PcPlan
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcUiState

// ─────────────────────────────────────────────────────────────
//  PcControlPlansUI — Plans list with one-tap execution
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlPlansUI(viewModel: PcControlViewModel) {

    val plans by viewModel.plans.observeAsState(emptyList())
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isExecuting = uiState is PcUiState.Loading

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("🖥️ PC Plans", fontWeight = FontWeight.Bold)
                },
                actions = {
                    PcConnectionChip(
                        status = connectionStatus,
                        onClick = { viewModel.pingPc() }
                    )
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.startNewPlan() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Plan") }
            )
        }
    ) { padding ->
        if (plans.isEmpty()) {
            PcEmptyPlans(
                modifier = Modifier.padding(padding),
                onCreatePlan = { viewModel.startNewPlan() }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(plans, key = { it.planId }) { plan ->
                    PcPlanCard(
                        plan = plan,
                        isExecuting = isExecuting,
                        onExecute = { viewModel.executePlan(plan) },
                        onEdit = { viewModel.startEditPlan(plan) },
                        onDelete = { viewModel.deletePlan(plan) }
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
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(plan.icon, fontSize = 34.sp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    plan.planName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${plan.steps.size} step${if (plan.steps.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }

            Button(
                onClick = onExecute,
                enabled = !isExecuting,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  CONNECTION CHIP
// ─────────────────────────────────────────────────────────────

@Composable
fun PcConnectionChip(status: PcConnectionStatus, onClick: () -> Unit) {
    val (containerColor, label) = when (status) {
        PcConnectionStatus.ONLINE   -> MaterialTheme.colorScheme.tertiaryContainer to "● Online"
        PcConnectionStatus.OFFLINE  -> MaterialTheme.colorScheme.errorContainer to "● Offline"
        PcConnectionStatus.CHECKING -> MaterialTheme.colorScheme.secondaryContainer to "● ..."
        PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.surfaceVariant to "● ?"
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  EMPTY STATE
// ─────────────────────────────────────────────────────────────

@Composable
fun PcEmptyPlans(modifier: Modifier = Modifier, onCreatePlan: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("🎯", fontSize = 64.sp)
            Text("No Plans Yet", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(
                "Create a plan to automate anything\non your PC with one tap",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onCreatePlan) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Create First Plan")
            }
        }
    }
}