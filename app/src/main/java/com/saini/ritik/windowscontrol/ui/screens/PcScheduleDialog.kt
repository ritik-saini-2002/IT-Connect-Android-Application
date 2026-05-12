package com.saini.ritik.windowscontrol.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saini.ritik.windowscontrol.data.PcPlan
import com.saini.ritik.windowscontrol.data.PcSavedDevice
import com.saini.ritik.windowscontrol.data.PcSchedule
import com.saini.ritik.windowscontrol.scheduler.PcScheduleWorker
import com.saini.ritik.windowscontrol.viewmodel.PcControlViewModel

private val DAY_LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")
private val ACTIONS = listOf(
    PcScheduleWorker.ACTION_WOL           to "Wake on LAN",
    PcScheduleWorker.ACTION_SHUTDOWN      to "Shutdown",
    PcScheduleWorker.ACTION_SLEEP         to "Sleep",
    PcScheduleWorker.ACTION_LOCK          to "Lock",
    PcScheduleWorker.ACTION_EXECUTE_PLAN  to "Run plan"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcScheduleDialog(
    device    : PcSavedDevice,
    viewModel : PcControlViewModel,
    onDismiss : () -> Unit
) {
    val schedules by viewModel.schedulesFor(device.id).collectAsStateWithLifecycle()
    val plans     by viewModel.plans.observeAsState(emptyList())
    var adding    by remember { mutableStateOf(false) }
    var editing   by remember { mutableStateOf<PcSchedule?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon    = { Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary) },
        title   = { Text("Schedules — ${device.label.ifBlank { device.host }}") },
        text    = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                if (schedules.isEmpty()) {
                    Text("No schedules yet. Add one to automate power or plan actions.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(schedules, key = { it.id }) { s ->
                            ScheduleRow(
                                s = s,
                                planName = plans.firstOrNull { it.planId == s.planId }?.planName,
                                onToggle = { viewModel.toggleSchedule(s) },
                                onEdit   = { editing = s },
                                onDelete = { viewModel.deleteSchedule(s) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add schedule", fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )

    if (adding) {
        ScheduleEditor(
            seed     = PcSchedule(
                deviceId = device.id,
                action   = PcScheduleWorker.ACTION_WOL,
                hour     = 8, minute = 0, daysMask = 0x3E  // Mon-Fri
            ),
            plans    = plans,
            onCancel = { adding = false },
            onConfirm = { s -> viewModel.upsertSchedule(s); adding = false }
        )
    }
    editing?.let { s ->
        ScheduleEditor(
            seed     = s,
            plans    = plans,
            onCancel = { editing = null },
            onConfirm = { updated -> viewModel.upsertSchedule(updated); editing = null }
        )
    }
}

@Composable
private fun ScheduleRow(
    s: PcSchedule,
    planName: String?,
    onToggle: () -> Unit,
    onEdit:   () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color  = if (s.enabled) cs.surfaceVariant.copy(0.5f) else cs.surface,
        shape  = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, cs.outline.copy(0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "%02d:%02d  •  %s".format(s.hour, s.minute, actionLabel(s, planName)),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = cs.onSurface
                )
                Text(daysLabel(s.daysMask),
                    fontSize = 11.sp, color = cs.onSurfaceVariant)
            }
            Switch(checked = s.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(18.dp),
                    tint = cs.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Remove", modifier = Modifier.size(18.dp),
                    tint = Color(0xFFFF6B6B))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditor(
    seed     : PcSchedule,
    plans    : List<PcPlan>,
    onCancel : () -> Unit,
    onConfirm: (PcSchedule) -> Unit
) {
    var hour     by remember { mutableStateOf(seed.hour) }
    var minute   by remember { mutableStateOf(seed.minute) }
    var days     by remember { mutableStateOf(seed.daysMask) }
    var action   by remember { mutableStateOf(seed.action) }
    var planId   by remember { mutableStateOf(seed.planId) }
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onCancel,
        icon  = { Icon(Icons.Default.Schedule, null, tint = cs.primary) },
        title = { Text(if (seed.lastFiredAt > 0 || seed.id != seed.copy().id) "Edit schedule" else "New schedule") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Time
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hour.toString().padStart(2, '0'),
                        onValueChange = { v -> v.toIntOrNull()?.takeIf { it in 0..23 }?.let { hour = it } },
                        label = { Text("Hour") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minute.toString().padStart(2, '0'),
                        onValueChange = { v -> v.toIntOrNull()?.takeIf { it in 0..59 }?.let { minute = it } },
                        label = { Text("Minute") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Days of week
                Text("Days", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LETTERS.forEachIndexed { i, letter ->
                        val bit = 1 shl i
                        val on  = (days and bit) != 0
                        FilterChip(
                            selected = on,
                            onClick  = { days = if (on) days and bit.inv() else days or bit },
                            label    = { Text(letter, fontSize = 12.sp) },
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Action
                var actionMenuOpen by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = actionMenuOpen, onExpandedChange = { actionMenuOpen = it }) {
                    OutlinedTextField(
                        value = ACTIONS.firstOrNull { it.first == action }?.second ?: action,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Action") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(actionMenuOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = actionMenuOpen, onDismissRequest = { actionMenuOpen = false }) {
                        ACTIONS.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { action = value; actionMenuOpen = false }
                            )
                        }
                    }
                }

                // Plan selector when action is EXECUTE_PLAN
                if (action == PcScheduleWorker.ACTION_EXECUTE_PLAN) {
                    var planMenuOpen by remember { mutableStateOf(false) }
                    val planName = plans.firstOrNull { it.planId == planId }?.planName
                        ?: "Select plan…"
                    ExposedDropdownMenuBox(expanded = planMenuOpen, onExpandedChange = { planMenuOpen = it }) {
                        OutlinedTextField(
                            value = planName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Plan") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(planMenuOpen) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = planMenuOpen, onDismissRequest = { planMenuOpen = false }) {
                            plans.forEach { plan ->
                                DropdownMenuItem(
                                    text = { Text(plan.planName) },
                                    onClick = { planId = plan.planId; planMenuOpen = false }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val valid = days != 0 &&
                (action != PcScheduleWorker.ACTION_EXECUTE_PLAN || !planId.isNullOrBlank())
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(seed.copy(
                        hour = hour, minute = minute, daysMask = days,
                        action = action,
                        planId = if (action == PcScheduleWorker.ACTION_EXECUTE_PLAN) planId else null
                    ))
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

private fun actionLabel(s: PcSchedule, planName: String?): String =
    when (s.action) {
        PcScheduleWorker.ACTION_WOL          -> "Wake on LAN"
        PcScheduleWorker.ACTION_SHUTDOWN     -> "Shutdown"
        PcScheduleWorker.ACTION_SLEEP        -> "Sleep"
        PcScheduleWorker.ACTION_LOCK         -> "Lock"
        PcScheduleWorker.ACTION_EXECUTE_PLAN -> planName?.let { "Run: $it" } ?: "Run plan"
        else -> s.action
    }

private fun daysLabel(mask: Int): String {
    if (mask == 0x7F) return "Every day"
    if (mask == 0x3E) return "Weekdays"
    if (mask == 0x41) return "Weekends"
    return DAY_LETTERS.mapIndexedNotNull { i, letter ->
        letter.takeIf { (mask and (1 shl i)) != 0 }
    }.joinToString(" ")
}
