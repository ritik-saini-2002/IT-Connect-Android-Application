package com.saini.ritik.windowscontrol.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.rememberCoroutineScope
import com.saini.ritik.windowscontrol.data.PcStep
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saini.ritik.windowscontrol.data.*
import com.saini.ritik.windowscontrol.pcfilebrowser.PcControlFileBrowserActivity
import com.saini.ritik.windowscontrol.viewmodel.PcConnectionStatus
import com.saini.ritik.windowscontrol.viewmodel.PcControlViewModel
import com.saini.ritik.windowscontrol.viewmodel.PcUiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.saini.ritik.windowscontrol.data.PcStepSerializer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlPlansUI(viewModel: PcControlViewModel) {
    val plans            by viewModel.plans.observeAsState(emptyList())
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
    val context           = LocalContext.current
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is PcUiState.Success -> { android.widget.Toast.makeText(context, s.message, android.widget.Toast.LENGTH_SHORT).show(); viewModel.resetUiState() }
            is PcUiState.Error -> { android.widget.Toast.makeText(context, "Error: ${s.message}", android.widget.Toast.LENGTH_SHORT).show(); viewModel.resetUiState() }
            else -> {}
        }
    }

    // Pending plan for file-pick: "add" = save file step, "execute" = save + run
    var pendingPickPlan by remember { mutableStateOf<PcPlan?>(null) }
    var pendingPickMode by remember { mutableStateOf("") }

    val fileBrowserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val path = result.data?.getStringExtra(PcControlFileBrowserActivity.EXTRA_SELECTED_PATH)
            val plan = pendingPickPlan
            if (!path.isNullOrBlank() && plan != null) {
                val step        = PcStep("OPEN_FILE", path)
                val updatedPlan = plan.copy(stepsJson = PcStepSerializer.toJson(plan.steps + step))
                viewModel.savePlanDirectly(updatedPlan)
                if (pendingPickMode == "execute") viewModel.executePlan(updatedPlan)
            }
        }
        pendingPickPlan = null
        pendingPickMode = ""
    }

    fun launchFilePicker(plan: PcPlan, mode: String) {
        pendingPickPlan = plan
        pendingPickMode = mode
        val intent = Intent(context, PcControlFileBrowserActivity::class.java).apply {
            putExtra(PcControlFileBrowserActivity.EXTRA_PICK_MODE, true)
        }
        fileBrowserLauncher.launch(intent)
    }

    Scaffold(
        topBar = {
            PcScreenTopBar(
                title            = "Plans",
                connectionStatus = connectionStatus,
                onPing           = { viewModel.pingPc() },
                extraActions     = {
                    IconButton(onClick = { viewModel.executeQuickStep(PcStep("KEY_PRESS", "WIN+UP")) }) {
                        Icon(Icons.Default.OpenInFull, "Maximize active window")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startNewPlan() }, containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp), modifier = if (isLandscape) Modifier.size(48.dp) else Modifier) {
                Icon(Icons.Default.Add, "New Plan", tint = MaterialTheme.colorScheme.onPrimary, modifier = if (isLandscape) Modifier.size(20.dp) else Modifier)
            }
        }
    ) { padding ->
        if (plans.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Bolt, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No plans yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap + to create your first automation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), textAlign = TextAlign.Center)
                }
            }
        } else {
            if (isLandscape) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    item(span = { GridItemSpan(2) }) {
                        Text("← Swipe to execute • Long-press for options", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    items(plans, key = { it.planId }) { plan ->
                        SwipeablePlanCard(
                            plan = plan,
                            onExecute = { viewModel.executePlan(plan) },
                            onEdit = { viewModel.startEditPlan(plan) },
                            onDelete = { viewModel.deletePlan(plan) },
                            onAddFileAndExecute = { p -> launchFilePicker(p, "execute") },
                            onAddFileToPlan = { p -> launchFilePicker(p, "add") },
                            onExecuteFileStep = { step -> viewModel.executeQuickStep(step) },
                            compact = true
                        )
                    }
                    item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(60.dp)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Text("← Swipe right to execute  •  Long-press for more options", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    items(plans, key = { it.planId }) { plan ->
                        SwipeablePlanCard(
                            plan = plan,
                            onExecute = { viewModel.executePlan(plan) },
                            onEdit = { viewModel.startEditPlan(plan) },
                            onDelete = { viewModel.deletePlan(plan) },
                            onAddFileAndExecute = { p -> launchFilePicker(p, "execute") },
                            onAddFileToPlan = { p -> launchFilePicker(p, "add") },
                            onExecuteFileStep = { step -> viewModel.executeQuickStep(step) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeablePlanCard(
    plan: PcPlan, onExecute: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    onAddFileAndExecute: (PcPlan) -> Unit = {}, onAddFileToPlan: (PcPlan) -> Unit = {},
    onExecuteFileStep: (PcStep) -> Unit = {},
    compact: Boolean = false
) {
    val scope = rememberCoroutineScope(); val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current; val threshold = with(density) { if (compact) 80.dp.toPx() else 120.dp.toPx() }
    var showMenu by remember { mutableStateOf(false) }; var showConfirm by remember { mutableStateOf(false) }; var showLongPressMenu by remember { mutableStateOf(false) }
    val progress = (offsetX.value / threshold).coerceIn(0f, 1f)
    val cardBg = lerpColor(MaterialTheme.colorScheme.surface, Color(0xFF22C55E).copy(alpha = 0.15f), progress)

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).background(Color(0xFF22C55E).copy(alpha = (0.05f + 0.2f * progress).coerceIn(0f, 1f))), contentAlignment = Alignment.CenterStart) {
            AnimatedVisibility(visible = progress > 0.1f, enter = fadeIn() + slideInHorizontally { -it / 2 }, exit = fadeOut(), modifier = Modifier.padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                    if (progress > 0.35f) Text("Run", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> scope.launch { offsetX.snapTo((offsetX.value + delta).coerceAtLeast(0f)) } },
                    onDragStopped = { scope.launch { if (offsetX.value >= threshold) { offsetX.animateTo(threshold * 1.1f, tween(80)); offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)); onExecute() } else offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) } })
                .pointerInput(Unit) { detectTapGestures(onLongPress = { showLongPressMenu = true }) },
            shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = cardBg), elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(plan.planName, style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${plan.steps.size} step${if (plan.steps.size != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(if (compact) 28.dp else 36.dp)) {
                        Icon(Icons.Default.MoreVert, "Options", modifier = Modifier.size(if (compact) 14.dp else 18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { showMenu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; showConfirm = true })
                    }
                }
            }
            if (!compact) {
                val fileSteps = plan.steps.filter { it.hasFilePath() }
                if (fileSteps.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outline.copy(0.15f))
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        fileSteps.forEach { step ->
                            val name = step.filePathArg().substringAfterLast('/').substringAfterLast('\\').take(32)
                            val isMedia = step.fileExtension() in listOf("mp4","mkv","avi","mov","mp3","wav","flac","aac")
                            Surface(
                                onClick = { onExecuteFileStep(step) },
                                shape   = RoundedCornerShape(6.dp),
                                color   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(step.fileIconVector(), null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Surface(shape = RoundedCornerShape(4.dp), color = if (isMedia) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer) {
                                        if (isMedia) {
                                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp).size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        } else {
                                            Text("Open", Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLongPressMenu) {
        AlertDialog(onDismissRequest = { showLongPressMenu = false }, icon = { Icon(Icons.Default.Bolt, null) },
            title = { Text(plan.planName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple(Icons.Default.PlayArrow, MaterialTheme.colorScheme.primaryContainer, "Execute") to { showLongPressMenu = false; onExecute() },
                        Triple(Icons.Default.PlayCircle, MaterialTheme.colorScheme.secondaryContainer, "File + Execute") to { showLongPressMenu = false; onAddFileAndExecute(plan) },
                        Triple(Icons.Default.PlaylistAdd, MaterialTheme.colorScheme.tertiaryContainer, "Add File") to { showLongPressMenu = false; onAddFileToPlan(plan) },
                        Triple(Icons.Default.Edit, MaterialTheme.colorScheme.surfaceVariant, "Edit") to { showLongPressMenu = false; onEdit() },
                    ).forEach { (triple, action) ->
                        Surface(onClick = action, shape = RoundedCornerShape(10.dp), color = triple.second, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(triple.first, null, Modifier.size(18.dp)); Text(text = triple.third, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Surface(onClick = { showLongPressMenu = false; showConfirm = true }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Text("Delete", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }, confirmButton = {}, dismissButton = { TextButton(onClick = { showLongPressMenu = false }) { Text("Cancel") } })
    }

    if (showConfirm) {
        AlertDialog(onDismissRequest = { showConfirm = false }, title = { Text("Delete plan?") },
            text = { Text("\"${plan.planName}\" will be permanently deleted.") },
            confirmButton = { TextButton(onClick = { showConfirm = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } })
    }
}

@Composable
fun PcConnectionChip(status: PcConnectionStatus, onClick: () -> Unit) {
    val (color, label) = when (status) {
        PcConnectionStatus.ONLINE -> Color(0xFF22C55E) to "Online"
        PcConnectionStatus.OFFLINE -> MaterialTheme.colorScheme.error to "Offline"
        PcConnectionStatus.CHECKING -> Color(0xFFF59E0B) to "Checking"
        PcConnectionStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant to "Ping"
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = color.copy(0.12f)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

internal fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(red = start.red + (end.red - start.red) * f, green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f, alpha = start.alpha + (end.alpha - start.alpha) * f)
}