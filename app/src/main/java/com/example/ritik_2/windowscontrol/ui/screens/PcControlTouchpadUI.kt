package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcScreen
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────
//  PcControlTouchpadUI — Full touchpad + mouse control
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlTouchpadUI(viewModel: PcControlViewModel) {

    var sensitivity by remember { mutableFloatStateOf(5f) }
    var lastFeedback by remember { mutableStateOf("Slide to move • Tap to click") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🖱️ Touchpad", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { viewModel.navigateTo(PcScreen.KEYBOARD) }) {
                        Icon(Icons.Default.Keyboard, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Keyboard")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Touchpad Surface ──
            PcTouchpadSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                sensitivity = sensitivity,
                onFeedback = { lastFeedback = it },
                onMouseMove = { dx, dy -> viewModel.sendMouseDelta(dx, dy) },
                onClick = { viewModel.sendMouseClick("left") },
                onDoubleClick = { viewModel.sendMouseClick("left", double = true) },
                onRightClick = { viewModel.sendMouseClick("right") },
                onScroll = { amount -> viewModel.sendMouseScroll(amount) }
            )

            // ── Feedback text ──
            Text(
                lastFeedback,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // ── Mouse Buttons ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(16.dp))
            ) {
                // Left click
                Button(
                    onClick = {
                        viewModel.sendMouseClick("left")
                        lastFeedback = "← Left Click"
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("LEFT", fontWeight = FontWeight.Bold)
                }

                // Scroll zone
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .scrollable(
                            state = rememberScrollableState { delta ->
                                val amount = if (delta > 0) 3 else -3
                                viewModel.sendMouseScroll(amount)
                                lastFeedback = if (delta > 0) "↑ Scroll Up" else "↓ Scroll Down"
                                delta
                            },
                            orientation = Orientation.Vertical
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⋮", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }

                // Right click
                Button(
                    onClick = {
                        viewModel.sendMouseClick("right")
                        lastFeedback = "Right Click →"
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("RIGHT", fontWeight = FontWeight.Bold)
                }
            }

            // ── Sensitivity ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Speed", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    sensitivity.toInt().toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(20.dp)
                )
            }

            // ── Quick Actions ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Explicit typed list to avoid lambda ambiguity
                val quickActions: List<Pair<String, () -> Unit>> = listOf(
                    "↑ Scroll Up"   to ({ viewModel.sendMouseScroll(5) }),
                    "↓ Scroll Down" to ({ viewModel.sendMouseScroll(-5) }),
                    "⌨ Keyboard"   to ({ viewModel.navigateTo(PcScreen.KEYBOARD) })
                )
                quickActions.forEach { (label, action) ->
                    OutlinedButton(
                        onClick = {
                            action.invoke()
                            lastFeedback = label
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  TOUCHPAD SURFACE — gesture detection
// ─────────────────────────────────────────────────────────────

@Composable
fun PcTouchpadSurface(
    modifier: Modifier = Modifier,
    sensitivity: Float,
    onFeedback: (String) -> Unit,
    onMouseMove: (Float, Float) -> Unit,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onRightClick: () -> Unit,
    onScroll: (Int) -> Unit
) {
    var isActive by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(20.dp)
            )
            .pointerInput(sensitivity) {
                var lastX = 0f
                var lastY = 0f
                var isDragging = false
                var tapTime = 0L
                var tapCount = 0

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull() ?: continue

                        when (event.type) {
                            PointerEventType.Press -> {
                                isActive = true
                                lastX = pointer.position.x
                                lastY = pointer.position.y
                                isDragging = false
                            }
                            PointerEventType.Move -> {
                                if (pointer.pressed) {
                                    val dx = (pointer.position.x - lastX) * sensitivity * 0.5f
                                    val dy = (pointer.position.y - lastY) * sensitivity * 0.5f
                                    if (abs(dx) > 1 || abs(dy) > 1) {
                                        isDragging = true
                                        onMouseMove(dx, dy)
                                        onFeedback("Moving mouse...")
                                    }
                                    lastX = pointer.position.x
                                    lastY = pointer.position.y
                                }
                            }
                            PointerEventType.Release -> {
                                isActive = false
                                if (!isDragging) {
                                    val now = System.currentTimeMillis()
                                    if (now - tapTime < 300) {
                                        tapCount++
                                    } else {
                                        tapCount = 1
                                    }
                                    tapTime = now
                                    when (tapCount) {
                                        1 -> { onClick(); onFeedback("← Left Click") }
                                        2 -> { onDoubleClick(); onFeedback("⚡ Double Click"); tapCount = 0 }
                                    }
                                }
                                isDragging = false
                            }
                            else -> {}
                        }
                        pointer.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!isActive) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🖱️", fontSize = 40.sp, modifier = Modifier.padding(bottom = 4.dp))
                Text("TOUCHPAD", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = TextUnit(2f, TextUnitType.Sp)
                )
                Text("Slide · Tap · Long press = Right click",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}