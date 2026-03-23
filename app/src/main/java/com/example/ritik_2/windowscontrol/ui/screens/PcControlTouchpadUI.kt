package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────
//  Layout (sketch):
//  ┌─────────────────────────────────────┐
//  │  Small top bar                      │
//  ├─────────────────────────────────────┤
//  │  TOUCHPAD  (largest, fills space)   │
//  ├──────────┬──────────────────────────┤
//  │    L     │           R              │
//  ├─────────────────────────────────────┤
//  │ Alt+F4 │ Enter │ Vol- │ Vol+ │ Mute │ F5
//  ├─────────────────────────────────────┤
//  │ F11 │ Esc │  Space  │ Alt+Tab │Win+D│
//  ├─────────────────────────────────────┤
//  │ ← F1 F2 F3 F4 F5 F6 F7 F8... →    │ scrollable
//  └─────────────────────────────────────┘
// ─────────────────────────────────────────────────────────────

private const val TAP_MAX_PX   = 12f
private const val HOLD_MS      = 580L
private const val DTAP_MS      = 260L
private const val DRAG_INIT_MS = 270L

// ═══════════════════════════════════════════════════════════════
//  MAIN SCREEN
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlTouchpadUI(viewModel: PcControlViewModel) {

    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    var sensitivity      by remember { mutableFloatStateOf(5f) }
    var feedback         by remember { mutableStateOf("") }
    var isDragging       by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var feedbackJob by remember { mutableStateOf<Job?>(null) }

    fun setFeedback(msg: String) {
        feedback = msg
        feedbackJob?.cancel()
        feedbackJob = scope.launch { delay(1500); feedback = "" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── TOP BAR ───────────────────────────────────────
        SmallTopBar(
            connectionStatus = connectionStatus,
            sensitivity      = sensitivity,
            isDragging       = isDragging,
            onSensChange     = { sensitivity = it },
            onPing           = { viewModel.pingPc() },
            onKeyboard       = { viewModel.navigateTo(PcScreen.KEYBOARD) }
        )

        // ── TOUCHPAD (takes all remaining space) ──────────
        TouchpadSurface(
            modifier     = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            sensitivity  = sensitivity,
            isDragging   = isDragging,
            feedback     = feedback,
            onDragChange = { isDragging = it },
            onFeedback   = { setFeedback(it) },
            viewModel    = viewModel
        )

        // ── L / R MOUSE BUTTONS ───────────────────────────
        MouseButtonRow(viewModel = viewModel, onFeedback = { setFeedback(it) })

        // ── ROW 1: Alt+F4 │ Enter │ Vol- │ Vol+ │ Mute │ F5
        ButtonRow1(viewModel = viewModel, onFeedback = { setFeedback(it) })

        // ── ROW 2: F11 │ Esc │ Space │ Alt+Tab │ Win+D ───
        ButtonRow2(viewModel = viewModel, onFeedback = { setFeedback(it) })

        // ── F-KEY ROW F1–F12 scrollable ───────────────────
        FKeyRow(viewModel = viewModel, onFeedback = { setFeedback(it) })

        Spacer(Modifier.height(4.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
//  SMALL TOP BAR
// ═══════════════════════════════════════════════════════════════

@Composable
fun SmallTopBar(
    connectionStatus : PcConnectionStatus,
    sensitivity      : Float,
    isDragging       : Boolean,
    onSensChange     : (Float) -> Unit,
    onPing           : () -> Unit,
    onKeyboard       : () -> Unit
) {
    val dotColor = when (connectionStatus) {
        PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80)
        PcConnectionStatus.OFFLINE  -> MaterialTheme.colorScheme.error
        PcConnectionStatus.CHECKING -> Color(0xFFF59E0B)
        PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
    }
    val statusLabel = when (connectionStatus) {
        PcConnectionStatus.ONLINE   -> "Online"
        PcConnectionStatus.OFFLINE  -> "Offline"
        PcConnectionStatus.CHECKING -> "Checking…"
        PcConnectionStatus.UNKNOWN  -> "Tap to ping"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Status pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    onClick = onPing,
                    shape = RoundedCornerShape(20.dp),
                    color = dotColor.copy(0.13f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            Modifier.size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            statusLabel,
                            fontSize = 10.sp,
                            color = dotColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (isDragging) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            "✊ Dragging",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Speed slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1.5f)
            ) {
                Text(
                    "Spd",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value         = sensitivity,
                    onValueChange = onSensChange,
                    valueRange    = 1f..14f,
                    steps         = 12,
                    modifier      = Modifier.weight(1f).height(24.dp)
                )
                Text(
                    "${sensitivity.toInt()}",
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                    modifier   = Modifier.width(16.dp)
                )
            }

            // Keyboard shortcut button
            IconButton(onClick = onKeyboard, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Keyboard, "Keyboard",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TOUCHPAD SURFACE
//  Gestures (matching sketch):
//  • Tap             → left click
//  • Double tap      → double left click
//  • Hold 580ms      → right click (haptic)
//  • Tap + tap-hold  → drag mode (hold left button)
//  • 2 fingers       → scroll (shows ↑↓ indicator on left)
//  • Slide           → move cursor
// ═══════════════════════════════════════════════════════════════

@Composable
fun TouchpadSurface(
    modifier     : Modifier,
    sensitivity  : Float,
    isDragging   : Boolean,
    feedback     : String,
    onDragChange : (Boolean) -> Unit,
    onFeedback   : (String) -> Unit,
    viewModel    : PcControlViewModel
) {
    val haptic = LocalHapticFeedback.current
    val scope  = rememberCoroutineScope()

    // Gesture state
    var isActive  by remember { mutableStateOf(false) }
    var lastX     by remember { mutableFloatStateOf(0f) }
    var lastY     by remember { mutableFloatStateOf(0f) }
    var downX     by remember { mutableFloatStateOf(0f) }
    var downY     by remember { mutableFloatStateOf(0f) }
    var didMove   by remember { mutableStateOf(false) }
    var tapTime   by remember { mutableLongStateOf(0L) }
    var tapCount  by remember { mutableIntStateOf(0) }
    var holdJob   by remember { mutableStateOf<Job?>(null) }
    var tapJob    by remember { mutableStateOf<Job?>(null) }

    // Scroll indicator (left edge popup)
    var scrollIndicator by remember { mutableStateOf("") }
    var scrollIndJob    by remember { mutableStateOf<Job?>(null) }
    fun showScroll(dir: String) {
        scrollIndicator = dir
        scrollIndJob?.cancel()
        scrollIndJob = scope.launch { delay(700); scrollIndicator = "" }
    }

    Box(modifier = modifier) {

        // ── Scroll indicator pill (left edge) ─────────────
        AnimatedVisibility(
            visible = scrollIndicator.isNotEmpty(),
            enter   = fadeIn() + slideInHorizontally { -it },
            exit    = fadeOut() + slideOutHorizontally { -it },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .zIndex(10f)
                .padding(start = 6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp
            ) {
                Text(
                    scrollIndicator,
                    modifier   = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ── Touchpad area ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        isDragging -> MaterialTheme.colorScheme.errorContainer.copy(0.2f)
                        isActive   -> MaterialTheme.colorScheme.primaryContainer.copy(0.3f)
                        else       -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .border(
                    width = if (isDragging) 2.dp else if (isActive) 1.5.dp else 1.dp,
                    color = when {
                        isDragging -> MaterialTheme.colorScheme.error
                        isActive   -> MaterialTheme.colorScheme.primary
                        else       -> MaterialTheme.colorScheme.outline.copy(0.35f)
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .pointerInput(sensitivity) {
                    awaitPointerEventScope {
                        while (true) {
                            val event   = awaitPointerEvent()
                            val p       = event.changes.firstOrNull() ?: continue
                            val fingers = event.changes.count { it.pressed }

                            when (event.type) {

                                PointerEventType.Press -> {
                                    isActive = true
                                    didMove  = false
                                    lastX = p.position.x; lastY = p.position.y
                                    downX = p.position.x; downY = p.position.y
                                    holdJob?.cancel()
                                    holdJob = scope.launch {
                                        delay(HOLD_MS)
                                        if (!didMove) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.sendMouseClick("right")
                                            onFeedback("Right click")
                                        }
                                    }
                                }

                                PointerEventType.Move -> {
                                    if (!p.pressed) continue
                                    val dx   = (p.position.x - lastX) * sensitivity * 0.45f
                                    val dy   = (p.position.y - lastY) * sensitivity * 0.45f
                                    val dist = sqrt(
                                        (p.position.x - downX).let { it * it } +
                                                (p.position.y - downY).let { it * it }
                                    )
                                    if (abs(dx) > 1f || abs(dy) > 1f) {
                                        if (dist > TAP_MAX_PX) {
                                            didMove = true
                                            holdJob?.cancel()
                                        }
                                        when {
                                            fingers >= 2 -> {
                                                val scrollUp = dy < 0
                                                viewModel.sendMouseScroll(if (scrollUp) 3 else -3)
                                                showScroll(if (scrollUp) "↑" else "↓")
                                                onFeedback(if (scrollUp) "Scroll ↑" else "Scroll ↓")
                                            }
                                            isDragging -> {
                                                viewModel.sendMouseDelta(dx, dy)
                                            }
                                            didMove -> {
                                                viewModel.sendMouseDelta(dx, dy)
                                            }
                                        }
                                        lastX = p.position.x
                                        lastY = p.position.y
                                    }
                                }

                                PointerEventType.Release -> {
                                    isActive = false
                                    holdJob?.cancel()
                                    if (!didMove) {
                                        val now = System.currentTimeMillis()
                                        when {
                                            isDragging -> {
                                                viewModel.sendMouseButtonUp()
                                                onDragChange(false)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onFeedback("Dropped")
                                            }
                                            tapCount > 0 && now - tapTime < DRAG_INIT_MS -> {
                                                // Tap then tap-hold = drag
                                                tapJob?.cancel()
                                                viewModel.sendMouseButtonDown("left")
                                                onDragChange(true)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onFeedback("Drag — move then tap to drop")
                                                tapCount = 0
                                            }
                                            else -> {
                                                tapCount++
                                                tapTime = now
                                                tapJob?.cancel()
                                                tapJob = scope.launch {
                                                    delay(DTAP_MS)
                                                    if (tapCount >= 2) {
                                                        // Double tap = double left click
                                                        viewModel.sendMouseClick("left", double = true)
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onFeedback("Double click")
                                                    } else {
                                                        viewModel.sendMouseClick("left")
                                                        onFeedback("Click")
                                                    }
                                                    tapCount = 0
                                                }
                                            }
                                        }
                                    }
                                    didMove = false
                                }

                                else -> {}
                            }
                            p.consume()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                isDragging -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("✊", fontSize = 28.sp)
                    Text(
                        "Dragging",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Tap to drop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(0.7f)
                    )
                }
                !isActive -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🖱️", fontSize = 30.sp)
                    Text(
                        "Touchpad",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Tap · ×2 Double-click · Hold Right-click\nTap+Hold Drag · 2-finger Scroll",
                        style     = MaterialTheme.typography.bodySmall,
                        fontSize  = 10.sp,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                        modifier  = Modifier.padding(horizontal = 20.dp)
                    )
                    if (feedback.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                feedback,
                                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style      = MaterialTheme.typography.labelSmall,
                                color      = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  MOUSE BUTTON ROW — L | scroll strip | R
// ═══════════════════════════════════════════════════════════════

@Composable
fun MouseButtonRow(viewModel: PcControlViewModel, onFeedback: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
    ) {
        Button(
            onClick  = { viewModel.sendMouseClick("left"); onFeedback("Left click") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape    = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("L", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }

        Box(
            modifier = Modifier
                .width(14.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .scrollable(
                    rememberScrollableState { delta ->
                        viewModel.sendMouseScroll(if (delta > 0) 3 else -3)
                        onFeedback(if (delta > 0) "Scroll ↑" else "Scroll ↓")
                        delta
                    },
                    Orientation.Vertical
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "⋮",
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        Button(
            onClick  = { viewModel.sendMouseClick("right"); onFeedback("Right click") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape    = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("R", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ROW 1 — Alt+F4 | Enter | Vol- | Vol+ | Mute | F5
// ═══════════════════════════════════════════════════════════════

@Composable
fun ButtonRow1(viewModel: PcControlViewModel, onFeedback: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        QuickKey(
            "Alt+F4", "Close",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            Modifier.weight(1.1f)
        ) { viewModel.sendKey("ALT+F4"); onFeedback("Alt+F4 — Close") }

        QuickKey(
            "Enter", "E Key",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Modifier.weight(1f)
        ) { viewModel.sendKey("ENTER"); onFeedback("Enter") }

        QuickKey(
            "Vol -", "🔉",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Modifier.weight(0.85f)
        ) { viewModel.executeQuickStep(PcStep("SYSTEM_CMD", "VOLUME_DOWN")); onFeedback("Vol Down") }

        QuickKey(
            "Vol +", "🔊",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Modifier.weight(0.85f)
        ) { viewModel.executeQuickStep(PcStep("SYSTEM_CMD", "VOLUME_UP")); onFeedback("Vol Up") }

        QuickKey(
            "Mute", "🔇",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Modifier.weight(0.85f)
        ) { viewModel.executeQuickStep(PcStep("SYSTEM_CMD", "MUTE")); onFeedback("Mute") }

        QuickKey(
            "F5", "Refresh",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Modifier.weight(0.85f)
        ) { viewModel.sendKey("F5"); onFeedback("F5") }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ROW 2 — F11 | Esc | Space | Alt+Tab | Win+D
// ═══════════════════════════════════════════════════════════════

@Composable
fun ButtonRow2(viewModel: PcControlViewModel, onFeedback: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        QuickKey(
            "F11", "Fullscr",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Modifier.weight(0.9f)
        ) { viewModel.sendKey("F11"); onFeedback("F11 — Fullscreen") }

        QuickKey(
            "Esc", "",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Modifier.weight(0.8f)
        ) { viewModel.sendKey("ESC"); onFeedback("Esc") }

        QuickKey(
            "Space", "Play/Pause",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Modifier.weight(1.4f)
        ) { viewModel.sendKey("SPACE"); onFeedback("Space — Play/Pause") }

        QuickKey(
            "Alt+Tab", "Switch",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Modifier.weight(1.1f)
        ) { viewModel.sendKey("ALT+TAB"); onFeedback("Alt+Tab") }

        QuickKey(
            "Win+D", "Desktop",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Modifier.weight(1f)
        ) { viewModel.sendKey("WIN+D"); onFeedback("Win+D — Desktop") }
    }
}

// ═══════════════════════════════════════════════════════════════
//  F-KEY ROW — F1 to F12, horizontally scrollable
// ═══════════════════════════════════════════════════════════════

@Composable
fun FKeyRow(viewModel: PcControlViewModel, onFeedback: (String) -> Unit) {
    val fkeys = (1..12).map { "F$it" }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(fkeys) { fk ->
            val isHighlighted = fk == "F5" || fk == "F11"
            val bg = when (fk) {
                "F5"  -> MaterialTheme.colorScheme.secondaryContainer
                "F11" -> MaterialTheme.colorScheme.tertiaryContainer
                else  -> MaterialTheme.colorScheme.surfaceVariant
            }
            val fg = when (fk) {
                "F5"  -> MaterialTheme.colorScheme.onSecondaryContainer
                "F11" -> MaterialTheme.colorScheme.onTertiaryContainer
                else  -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val sub = when (fk) {
                "F1"  -> "Help"
                "F5"  -> "Refresh"
                "F11" -> "Full"
                "F12" -> "Dev"
                else  -> ""
            }
            QuickKey(fk, sub, bg, fg, Modifier.width(46.dp))
            { viewModel.sendKey(fk); onFeedback(fk) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  QUICK KEY button — compact, clean, press animation
// ═══════════════════════════════════════════════════════════════

@Composable
fun QuickKey(
    label   : String,
    sub     : String,
    bg      : Color,
    fg      : Color,
    modifier: Modifier = Modifier,
    onClick : () -> Unit
) {
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { pressed = true; delay(90); pressed = false }
            onClick()
        },
        modifier       = modifier.height(46.dp),
        shape          = RoundedCornerShape(10.dp),
        color          = if (pressed) fg.copy(0.25f) else bg,
        border         = BorderStroke(
            width = if (pressed) 1.5.dp else 1.dp,
            color = if (pressed) fg else fg.copy(0.2f)
        ),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                fontSize   = if (label.length > 6) 8.5.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                color      = fg,
                textAlign  = TextAlign.Center,
                lineHeight = 11.sp
            )
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    fontSize  = 8.sp,
                    color     = fg.copy(0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 9.sp
                )
            }
        }
    }
}