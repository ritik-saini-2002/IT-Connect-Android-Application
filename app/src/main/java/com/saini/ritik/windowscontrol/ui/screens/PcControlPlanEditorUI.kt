package com.saini.ritik.windowscontrol.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saini.ritik.windowscontrol.data.*
import com.saini.ritik.windowscontrol.viewmodel.PcControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlPlanEditorUI(viewModel: PcControlViewModel) {
    val plan by viewModel.editingPlan.collectAsStateWithLifecycle()
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val cs = MaterialTheme.colorScheme

    val context = LocalContext.current
    val addStepLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val stepJson = result.data?.getStringExtra(PcAddStepActivity.EXTRA_STEP_JSON)
            val step = stepJson?.let { PcStepSerializer.stepFromJson(it) }
            if (step != null) viewModel.addStep(step)
        }
    }

    if (plan == null) return
    val canSave = plan!!.planName.isNotBlank() && plan!!.steps.isNotEmpty()

    Scaffold(
        topBar = {
            if (isLandscape) {
                Surface(color = cs.surfaceVariant, tonalElevation = 2.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { viewModel.cancelEdit() }, Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Discard", Modifier.size(18.dp)) }
                        OutlinedTextField(value = plan!!.planName, onValueChange = { viewModel.updateEditingPlan(plan!!.copy(planName = it)) },
                            placeholder = { Text("Plan Name…", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f).height(44.dp), singleLine = true, shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("${plan!!.steps.size} steps", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Button(onClick = { viewModel.savePlan() }, enabled = canSave, shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            Icon(Icons.Default.Check, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Save", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { viewModel.cancelEdit() }) { Icon(Icons.Default.Close, "Discard") } },
                    title = { Text(plan!!.planName.ifBlank { "New Plan" }, fontWeight = FontWeight.Bold) },
                    actions = { Button(onClick = { viewModel.savePlan() }, enabled = canSave, shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(end = 8.dp)) { Icon(Icons.Default.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Save") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface))
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addStepLauncher.launch(Intent(context, PcAddStepActivity::class.java)) },
                containerColor = cs.primary, shape = RoundedCornerShape(16.dp),
                modifier = if (isLandscape) Modifier.size(48.dp) else Modifier
            ) {
                Icon(Icons.Default.Add, "Add Step", modifier = if (isLandscape) Modifier.size(20.dp) else Modifier)
            }
        }
    ) { padding ->
        if (isLandscape) {
            if (plan!!.steps.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("➕", fontSize = 32.sp); Spacer(Modifier.height(8.dp)); Text("Tap + to add steps", color = cs.onSurfaceVariant) }
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxSize().padding(padding)) {
                    itemsIndexed(plan!!.steps) { idx, step ->
                        PcStepCard(idx, step, onMoveUp = if (idx > 0) {{ viewModel.reorderSteps(idx, idx-1) }} else null,
                            onMoveDown = if (idx < plan!!.steps.lastIndex) {{ viewModel.reorderSteps(idx, idx+1) }} else null,
                            onRemove = { viewModel.removeStep(idx) }, compact = true)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(value = plan!!.planName, onValueChange = { viewModel.updateEditingPlan(plan!!.copy(planName = it)) },
                    label = { Text("Plan Name *") }, placeholder = { Text("e.g. Movie Night…") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, isError = plan!!.planName.isBlank(), shape = RoundedCornerShape(10.dp))
                HorizontalDivider(color = cs.outline.copy(0.15f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("STEPS (${plan!!.steps.size})", style = MaterialTheme.typography.labelSmall, color = cs.primary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    if (plan!!.steps.isEmpty()) Text("Add at least 1 step", style = MaterialTheme.typography.labelSmall, color = cs.error)
                }
                if (plan!!.steps.isEmpty()) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("➕", fontSize = 32.sp); Spacer(Modifier.height(8.dp)); Text("Tap + to add steps", color = cs.onSurfaceVariant) } }
                    }
                } else {
                    plan!!.steps.forEachIndexed { idx, step -> PcStepCard(idx, step, onMoveUp = if (idx > 0) {{ viewModel.reorderSteps(idx, idx-1) }} else null, onMoveDown = if (idx < plan!!.steps.lastIndex) {{ viewModel.reorderSteps(idx, idx+1) }} else null, onRemove = { viewModel.removeStep(idx) }) }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }

}

@Composable
fun PcStepCard(index: Int, step: PcStep, onMoveUp: (()->Unit)?, onMoveDown: (()->Unit)?, onRemove: ()->Unit, compact: Boolean = false) {
    val cs = MaterialTheme.colorScheme; val type = PcStepType.entries.find { it.name == step.type }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(if (compact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = cs.primaryContainer, modifier = Modifier.size(if (compact) 28.dp else 36.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("${index+1}", fontWeight = FontWeight.Bold, fontSize = if (compact) 10.sp else 13.sp) }
            }
            Icon(Icons.Default.Bolt, null, modifier = Modifier.size(if (compact) 16.dp else 20.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(type?.display ?: step.type, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val detail = buildStepDetail(step)
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
            if (!compact) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (onMoveUp != null) IconButton(onMoveUp, Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Up", Modifier.size(16.dp)) } else Spacer(Modifier.size(28.dp))
                    IconButton(onRemove, Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp), tint = cs.error) }
                    if (onMoveDown != null) IconButton(onMoveDown, Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Down", Modifier.size(16.dp)) } else Spacer(Modifier.size(28.dp))
                }
            } else { IconButton(onRemove, Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Remove", Modifier.size(12.dp), tint = cs.error) } }
        }
    }
}

fun buildStepDetail(step: PcStep): String = when (step.type) {
    "LAUNCH_APP" -> step.value.substringAfterLast("\\").substringAfterLast("/"); "KILL_APP" -> step.value; "KEY_PRESS" -> step.value
    "TYPE_TEXT" -> "\"${step.value.take(28)}${if (step.value.length > 28) "…" else ""}\""; "OPEN_FILE" -> step.value.substringAfterLast("\\").substringAfterLast("/").take(36)
    "MOUSE_CLICK" -> "(${step.x}, ${step.y})"; "FILE_OP" -> "${step.action}: ${step.from.substringAfterLast("\\")}"
    "SYSTEM_CMD" -> step.value + if (step.args.isNotEmpty()) " → ${step.args[0]}" else ""; "WAIT" -> "${step.ms}ms (${step.ms / 1000.0}s)"
    "RUN_SCRIPT" -> step.value.substringAfterLast("\\").substringAfterLast("/"); else -> step.value
}

