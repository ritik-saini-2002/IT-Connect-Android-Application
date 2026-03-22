package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  PcControlTouchpadUI v2
//  - Tap → left click
//  - Tap then hold (within 300ms) → select + drag (hold left button)
//  - Double tap → double click
//  - Long press (600ms) → right click
//  - Two-finger → scroll
//  - Landscape layout supported
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlTouchpadUI(viewModel: PcControlViewModel) {

    val configuration   = LocalConfiguration.current
    val isLandscape     = configuration.screenWidthDp > configuration.screenHeightDp
    var sensitivity     by remember { mutableFloatStateOf(5f) }
    var feedback        by remember { mutableStateOf("Slide · Tap · Hold to drag") }
    var isDragging      by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🖱️ Touchpad", fontWeight = FontWeight.Bold)
                        if (isDragging) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    " DRAG ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.navigateTo(PcScreen.KEYBOARD) }) {
                        Icon(Icons.Default.Keyboard, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Keys", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (isLandscape) {
            // ── LANDSCAPE LAYOUT ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left: touchpad (larger in landscape)
                Column(
                    modifier = Modifier.weight(1.6f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TouchpadSurface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        sensitivity = sensitivity,
                        onFeedback = { feedback = it },
                        onDragStateChange = { isDragging = it },
                        viewModel = viewModel
                    )
                    Text(
                        feedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    MouseButtonsRow(viewModel = viewModel, onFeedback = { feedback = it })
                }

                // Right: controls panel
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SensitivityRow(sensitivity = sensitivity, onSensChange = { sensitivity = it })
                    ScrollButtons(viewModel = viewModel, onFeedback = { feedback = it })
                    QuickSystemButtons(viewModel = viewModel, onFeedback = { feedback = it })
                }
            }
        } else {
            // ── PORTRAIT LAYOUT ───────────────────────────────
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TouchpadSurface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    sensitivity = sensitivity,
                    onFeedback = { feedback = it },
                    onDragStateChange = { isDragging = it },
                    viewModel = viewModel
                )

                Text(
                    feedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                MouseButtonsRow(viewModel = viewModel, onFeedback = { feedback = it })
                SensitivityRow(sensitivity = sensitivity, onSensChange = { sensitivity = it })

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScrollButtons(
                        viewModel = viewModel,
                        onFeedback = { feedback = it },
                        modifier = Modifier.weight(1f)
                    )
                    QuickSystemButtons(
                        viewModel = viewModel,
                        onFeedback = { feedback = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  TOUCHPAD SURFACE — Core gesture detection
// ─────────────────────────────────────────────────────────────

@Composable
fun TouchpadSurface(
    modifier: Modifier = Modifier,
    sensitivity: Float,
    onFeedback: (String) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    viewModel: PcControlViewModel
) {
    val haptic      = LocalHapticFeedback.current
    val scope       = rememberCoroutineScope()
    var isActive    by remember { mutableStateOf(false) }
    var isDragging  by remember { mutableStateOf(false) }

    // Gesture state
    var lastX       by remember { mutableFloatStateOf(0f) }
    var lastY       by remember { mutableFloatStateOf(0f) }
    var isMoved     by remember { mutableStateOf(false) }
    var tapTime     by remember { mutableLongStateOf(0L) }
    var tapCount    by remember { mutableIntStateOf(0) }
    var holdJob     by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var dragJob     by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Tap-then-hold detection
    // Phase 1: finger down → start 600ms hold timer
    // Phase 2a: if finger up before 600ms AND within 300ms of last tap → TAP-HOLD drag
    // Phase 2b: if finger holds 600ms without moving → RIGHT CLICK
    // Phase 3: if finger moves after any state → MOVE / DRAG

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isDragging) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (isDragging) 2.dp else if (isActive) 1.5.dp else 1.dp,
                color = if (isDragging) MaterialTheme.colorScheme.error
                else if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .pointerInput(sensitivity) {
                awaitPointerEventScope {
                    while (true) {
                        val event   = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull() ?: continue
                        val touches = event.changes.size

                        when (event.type) {
                            PointerEventType.Press -> {
                                isActive = true
                                isMoved  = false
                                lastX    = pointer.position.x
                                lastY    = pointer.position.y

                                // Cancel any previous jobs
                                holdJob?.cancel()
                                dragJob?.cancel()

                                // Start hold timer → right click after 600ms
                                holdJob = scope.launch {
                                    delay(600)
                                    if (!isMoved) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.sendMouseClick("right")
                                        onFeedback("👆 Right Click")
                                    }
                                }
                            }

                            PointerEventType.Move -> {
                                if (!pointer.pressed) continue
                                val dx = (pointer.position.x - lastX) * sensitivity * 0.5f
                                val dy = (pointer.position.y - lastY) * sensitivity * 0.5f

                                if (kotlin.math.abs(dx) > 1.5f || kotlin.math.abs(dy) > 1.5f) {
                                    isMoved = true
                                    holdJob?.cancel() // cancel right-click hold

                                    if (touches == 2) {
                                        // Two-finger scroll
                                        val scrollAmount = if (dy > 0) -3 else 3
                                        viewModel.sendMouseScroll(scrollAmount)
                                        onFeedback(if (dy > 0) "↓ Scroll Down" else "↑ Scroll Up")
                                    } else {
                                        // Single finger move
                                        if (isDragging) {
                                            // During drag: send delta while button held
                                            viewModel.sendMouseDelta(dx, dy)
                                            onFeedback("✊ Dragging...")
                                        } else {
                                            viewModel.sendMouseDelta(dx, dy)
                                        }
                                    }
                                    lastX = pointer.position.x
                                    lastY = pointer.position.y
                                }
                            }

                            PointerEventType.Release -> {
                                isActive = false
                                holdJob?.cancel()

                                if (!isMoved) {
                                    val now = System.currentTimeMillis()

                                    if (isDragging) {
                                        // Release drag
                                        viewModel.sendMouseButtonUp()
                                        isDragging = false
                                        onDragStateChange(false)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onFeedback("✅ Drop!")
                                    } else {
                                        // Check if this is a tap-then-hold (for drag)
                                        val timeSinceLastTap = now - tapTime
                                        if (tapCount > 0 && timeSinceLastTap < 300) {
                                            // Tap-then-hold detected → start drag
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.sendMouseButtonDown("left")
                                            isDragging = true
                                            onDragStateChange(true)
                                            onFeedback("✊ Drag started! Move then tap to drop")
                                            tapCount = 0
                                        } else {
                                            // Regular tap
                                            tapCount++
                                            tapTime = now

                                            dragJob?.cancel()
                                            dragJob = scope.launch {
                                                delay(280) // wait to see if double tap
                                                when (tapCount) {
                                                    1 -> {
                                                        viewModel.sendMouseClick("left")
                                                        onFeedback("← Left Click")
                                                    }
                                                    2 -> {
                                                        viewModel.sendMouseClick("left", double = true)
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onFeedback("⚡ Double Click")
                                                    }
                                                }
                                                tapCount = 0
                                            }
                                        }
                                    }
                                } else if (isDragging) {
                                    // Was dragging and finger lifted — keep drag active
                                    // User must tap to drop
                                }
                                isMoved = false
                            }
                            else -> {}
                        }
                        pointer.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!isActive && !isDragging) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🖱️", fontSize = 36.sp)
                Text(
                    "TOUCHPAD",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp
                )
                Text(
                    "Slide=Move  Tap=Click  Tap+Hold=Drag",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else if (isDragging) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✊", fontSize = 36.sp)
                Text(
                    "DRAGGING",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    "Tap to drop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  MOUSE BUTTONS ROW
// ─────────────────────────────────────────────────────────────

@Composable
fun MouseButtonsRow(
    viewModel: PcControlViewModel,
    onFeedback: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        Button(
            onClick = { viewModel.sendMouseClick("left"); onFeedback("← Left Click") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) { Text("LEFT", fontWeight = FontWeight.Bold) }

        Box(
            modifier = Modifier
                .width(14.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .scrollable(
                    rememberScrollableState { delta ->
                        val amt = if (delta > 0) 3 else -3
                        viewModel.sendMouseScroll(amt)
                        onFeedback(if (delta > 0) "↑ Scroll Up" else "↓ Scroll Down")
                        delta
                    },
                    Orientation.Vertical
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("⋮", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        Button(
            onClick = { viewModel.sendMouseClick("right"); onFeedback("Right Click →") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) { Text("RIGHT", fontWeight = FontWeight.Bold) }
    }
}

// ─────────────────────────────────────────────────────────────
//  SENSITIVITY ROW
// ─────────────────────────────────────────────────────────────

@Composable
fun SensitivityRow(sensitivity: Float, onSensChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Speed, null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Speed", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = sensitivity,
            onValueChange = onSensChange,
            valueRange = 1f..12f,
            steps = 10,
            modifier = Modifier.weight(1f)
        )
        Text(
            sensitivity.toInt().toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(22.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  SCROLL BUTTONS
// ─────────────────────────────────────────────────────────────

@Composable
fun ScrollButtons(
    viewModel: PcControlViewModel,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedButton(
            onClick = { viewModel.sendMouseScroll(5); onFeedback("↑ Scroll Up") },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(4.dp),
            shape = RoundedCornerShape(10.dp)
        ) { Text("↑ Up", style = MaterialTheme.typography.labelSmall) }

        OutlinedButton(
            onClick = { viewModel.sendMouseScroll(-5); onFeedback("↓ Scroll Down") },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(4.dp),
            shape = RoundedCornerShape(10.dp)
        ) { Text("↓ Down", style = MaterialTheme.typography.labelSmall) }
    }
}

// ─────────────────────────────────────────────────────────────
//  QUICK SYSTEM BUTTONS
// ─────────────────────────────────────────────────────────────

@Composable
fun QuickSystemButtons(
    viewModel: PcControlViewModel,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val actions: List<Pair<String, () -> Unit>> = listOf(
            "⌨" to { viewModel.navigateTo(PcScreen.KEYBOARD); onFeedback("Keyboard") },
            "🖥" to { viewModel.sendKey("WIN+D"); onFeedback("WIN+D: Desktop") }
        )
        actions.forEach { (label, action) ->
            OutlinedButton(
                onClick = { action.invoke() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                shape = RoundedCornerShape(10.dp)
            ) { Text(label, style = MaterialTheme.typography.labelSmall) }
        }
    }
}