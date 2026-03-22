package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
//  PcControlTouchpadUI — Clean, classic design
//
//  Layout (top → bottom):
//  ┌──────────────────────────────────┐
//  │  Top bar (connection + nav)      │
//  ├──────────────────────────────────┤
//  │  Emergency button rows           │
//  │  Row 1: F11 │ F5 │ SPACE │ VOL- │ MUTE │ VOL+ │
//  │  Row 2: ALT+F4 │ ESC │ ENTER │ ← │ → │ ⌫   │
//  ├──────────────────────────────────┤
//  │                                  │
//  │         TOUCHPAD  (large)        │
//  │                                  │
//  ├──────────────────────────────────┤
//  │    LEFT CLICK │ scroll │ RIGHT   │
//  └──────────────────────────────────┘
// ─────────────────────────────────────────────────────────────

private const val TAP_MAX_PX    = 14f
private const val HOLD_MS       = 600L
private const val DTAP_MS       = 270L
private const val DRAG_INIT_MS  = 290L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlTouchpadUI(viewModel: PcControlViewModel) {

    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    var sensitivity      by remember { mutableFloatStateOf(5f) }
    var feedback         by remember { mutableStateOf("") }
    var isDragging       by remember { mutableStateOf(false) }
    var showSensitivity  by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Touchpad", fontWeight = FontWeight.Bold)
                        // Connection pill
                        val (pillColor, pillText) = when (connectionStatus) {
                            PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80) to "Online"
                            PcConnectionStatus.OFFLINE  -> MaterialTheme.colorScheme.error to "Offline"
                            PcConnectionStatus.CHECKING -> Color(0xFFF59E0B) to "Checking"
                            PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onSurfaceVariant to "Tap to check"
                        }
                        Surface(
                            onClick  = { viewModel.pingPc() },
                            shape    = RoundedCornerShape(20.dp),
                            color    = pillColor.copy(alpha = 0.15f),
                            border   = BorderStroke(1.dp, pillColor.copy(0.4f))
                        ) {
                            Text(
                                "● $pillText",
                                modifier  = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize  = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color     = pillColor
                            )
                        }
                        if (isDragging) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    "Dragging",
                                    modifier  = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize  = 11.sp,
                                    color     = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Sensitivity toggle
                    IconButton(onClick = { showSensitivity = !showSensitivity }) {
                        Icon(Icons.Default.Speed, "Sensitivity",
                            tint = if (showSensitivity) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Keyboard nav
                    IconButton(onClick = { viewModel.navigateTo(PcScreen.KEYBOARD) }) {
                        Icon(Icons.Default.Keyboard, "Keyboard")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ── Sensitivity slider ─────────────────────────
            if (showSensitivity) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.SlowMotionVideo, null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Speed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value         = sensitivity,
                        onValueChange = { sensitivity = it },
                        valueRange    = 1f..14f,
                        steps         = 12,
                        modifier      = Modifier.weight(1f)
                    )
                    Text(
                        "${sensitivity.toInt()}",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                        modifier   = Modifier.width(22.dp)
                    )
                }
            }

            // ── ROW 1: Media + Volume ──────────────────────
            EmergencyRow1(viewModel = viewModel)

            // ── ROW 2: App control + Nav ───────────────────
            EmergencyRow2(viewModel = viewModel)

            // ── Feedback label ─────────────────────────────
            if (feedback.isNotEmpty()) {
                Text(
                    feedback,
                    modifier  = Modifier.align(Alignment.CenterHorizontally),
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            // ── TOUCHPAD (fills remaining space) ──────────
            TouchpadSurface(
                modifier     = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                sensitivity  = sensitivity,
                isDragging   = isDragging,
                onDragChange = { isDragging = it },
                onFeedback   = { feedback = it },
                viewModel    = viewModel
            )

            // ── Mouse buttons ──────────────────────────────
            MouseButtons(viewModel = viewModel, onFeedback = { feedback = it })
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  ROW 1 — F11 (fullscreen) | F5 (slideshow) | SPACE (play/pause)
//         | VOL- | MUTE | VOL+
// ─────────────────────────────────────────────────────────────

@Composable
fun EmergencyRow1(viewModel: PcControlViewModel) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // F11 — fullscreen (VLC, browser, etc)
        EBtn(
            label    = "F11",
            subLabel = "Fullscreen",
            icon     = "⛶",
            modifier = Modifier.weight(1f),
            color    = MaterialTheme.colorScheme.primaryContainer,
            content  = MaterialTheme.colorScheme.onPrimaryContainer
        ) { viewModel.sendKey("F11") }

        // F5 — slideshow / refresh
        EBtn(
            label    = "F5",
            subLabel = "Slideshow",
            icon     = "▶",
            modifier = Modifier.weight(1f),
            color    = MaterialTheme.colorScheme.secondaryContainer,
            content  = MaterialTheme.colorScheme.onSecondaryContainer
        ) { viewModel.sendKey("F5") }

        // SPACE — play/pause
        EBtn(
            label    = "Space",
            subLabel = "Play/Pause",
            icon     = "⏯",
            modifier = Modifier.weight(1.2f),
            color    = MaterialTheme.colorScheme.tertiaryContainer,
            content  = MaterialTheme.colorScheme.onTertiaryContainer
        ) { viewModel.sendKey("SPACE") }

        // VOL-
        EBtn(
            label    = "Vol -",
            subLabel = "",
            icon     = "🔉",
            modifier = Modifier.weight(0.9f),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            content  = MaterialTheme.colorScheme.onSurfaceVariant
        ) { viewModel.executeQuickStep(PcStep("SYSTEM_CMD", "VOLUME_DOWN")) }

        // MUTE
        EBtn(
            label    = "Mute",
            subLabel = "",
            icon     = "🔇",
            modifier = Modifier.weight(0.9f),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            content  = MaterialTheme.colorScheme.onSurfaceVariant
        ) { viewModel.executeQuickStep(PcStep("SYSTEM_CMD", "MUTE")) }

        // VOL+
        EBtn(
            label    = "Vol +",
            subLabel = "",
            icon     = "🔊",
            modifier = Modifier.weight(0.9f),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            content  = MaterialTheme.colorScheme.onSurfaceVariant
        ) { viewModel.executeQuickStep(PcStep("SYSTEM_CMD", "VOLUME_UP")) }
    }
}

// ─────────────────────────────────────────────────────────────
//  ROW 2 — ALT+F4 | ESC | ENTER | ← | → | ⌫
// ─────────────────────────────────────────────────────────────

@Composable
fun EmergencyRow2(viewModel: PcControlViewModel) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ALT+F4 — kill task
        EBtn(
            label    = "Alt+F4",
            subLabel = "Close",
            icon     = "✕",
            modifier = Modifier.weight(1.1f),
            color    = MaterialTheme.colorScheme.errorContainer,
            content  = MaterialTheme.colorScheme.error
        ) { viewModel.sendKey("ALT+F4") }

        // ESC
        EBtn(
            label    = "Esc",
            subLabel = "",
            icon     = "⎋",
            modifier = Modifier.weight(0.9f),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            content  = MaterialTheme.colorScheme.onSurfaceVariant
        ) { viewModel.sendKey("ESC") }

        // ENTER
        EBtn(
            label    = "Enter",
            subLabel = "",
            icon     = "↵",
            modifier = Modifier.weight(0.9f),
            color    = MaterialTheme.colorScheme.primaryContainer,
            content  = MaterialTheme.colorScheme.onPrimaryContainer
        ) { viewModel.sendKey("ENTER") }

        // Arrow Left
        EBtn(
            label    = "◀",
            subLabel = "Prev",
            icon     = "",
            modifier = Modifier.weight(0.8f),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            content  = MaterialTheme.colorScheme.onSurfaceVariant
        ) { viewModel.sendKey("LEFT") }

        // Arrow Right
        EBtn(
            label    = "▶",
            subLabel = "Next",
            icon     = "",
            modifier = Modifier.weight(0.8f),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            content  = MaterialTheme.colorScheme.onSurfaceVariant
        ) { viewModel.sendKey("RIGHT") }

        // Backspace
        EBtn(
            label    = "⌫",
            subLabel = "",
            icon     = "",
            modifier = Modifier.weight(0.8f),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            content  = MaterialTheme.colorScheme.onSurfaceVariant
        ) { viewModel.sendKey("BACKSPACE") }
    }
}

// ─────────────────────────────────────────────────────────────
//  EMERGENCY BUTTON component — clean, card-style
// ─────────────────────────────────────────────────────────────

@Composable
fun EBtn(
    label    : String,
    subLabel : String,
    icon     : String,
    modifier : Modifier = Modifier,
    color    : Color,
    content  : Color,
    onClick  : () -> Unit
) {
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { pressed = true; delay(100); pressed = false }
            onClick()
        },
        modifier  = modifier.height(52.dp),
        shape     = RoundedCornerShape(12.dp),
        color     = if (pressed) content.copy(alpha = 0.2f) else color,
        border    = if (pressed) BorderStroke(1.5.dp, content)
        else BorderStroke(1.dp, content.copy(alpha = 0.2f)),
        tonalElevation = if (pressed) 0.dp else 2.dp
    ) {
        Column(
            modifier              = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.Center
        ) {
            if (icon.isNotEmpty()) {
                Text(icon, fontSize = 13.sp, lineHeight = 14.sp)
            }
            Text(
                label,
                fontSize   = if (label.length > 5) 9.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color      = if (pressed) content else content,
                textAlign  = TextAlign.Center,
                lineHeight = 12.sp
            )
            if (subLabel.isNotEmpty()) {
                Text(
                    subLabel,
                    fontSize  = 8.sp,
                    color     = content.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    lineHeight = 9.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  TOUCHPAD SURFACE — laptop-style gestures
//  • Tap       → left click
//  • Double tap → double click
//  • Hold 600ms → right click
//  • Tap+Hold   → drag
//  • 2 fingers  → scroll
// ─────────────────────────────────────────────────────────────

@Composable
fun TouchpadSurface(
    modifier     : Modifier,
    sensitivity  : Float,
    isDragging   : Boolean,
    onDragChange : (Boolean) -> Unit,
    onFeedback   : (String) -> Unit,
    viewModel    : PcControlViewModel
) {
    val haptic   = LocalHapticFeedback.current
    val scope    = rememberCoroutineScope()

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

    val surfaceColor = when {
        isDragging -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        isActive   -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else       -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = when {
        isDragging -> MaterialTheme.colorScheme.error
        isActive   -> MaterialTheme.colorScheme.primary
        else       -> MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceColor)
            .border(
                width = if (isActive || isDragging) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
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
                                val dx = (p.position.x - lastX) * sensitivity * 0.45f
                                val dy = (p.position.y - lastY) * sensitivity * 0.45f
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
                                            viewModel.sendMouseScroll(if (dy > 0) -3 else 3)
                                            onFeedback(if (dy > 0) "Scroll ↓" else "Scroll ↑")
                                        }
                                        isDragging -> {
                                            viewModel.sendMouseDelta(dx, dy)
                                            onFeedback("Dragging…")
                                        }
                                        didMove -> viewModel.sendMouseDelta(dx, dy)
                                    }
                                    lastX = p.position.x; lastY = p.position.y
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
                                            // Tap + tap-hold = drag
                                            tapJob?.cancel()
                                            viewModel.sendMouseButtonDown("left")
                                            onDragChange(true)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onFeedback("Drag — move then tap to drop")
                                            tapCount = 0
                                        }
                                        else -> {
                                            tapCount++; tapTime = now
                                            tapJob?.cancel()
                                            tapJob = scope.launch {
                                                delay(DTAP_MS)
                                                if (tapCount >= 2) {
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
        if (!isActive && !isDragging) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🖱️", fontSize = 32.sp)
                Text(
                    "Touchpad",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Tap · Double-tap · Hold · Tap+Hold to drag · 2-finger scroll",
                    style     = MaterialTheme.typography.bodySmall,
                    fontSize  = 10.sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else if (isDragging) {
            Column(
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
                    "Move to drag • Tap to drop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(0.7f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  MOUSE BUTTONS BAR — Left | Scroll | Right
// ─────────────────────────────────────────────────────────────

@Composable
fun MouseButtons(viewModel: PcControlViewModel, onFeedback: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        // Left click
        Button(
            onClick = { viewModel.sendMouseClick("left"); onFeedback("Left click") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape    = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text("LEFT", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium)
        }

        // Scroll wheel (drag up/down on this strip)
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .scrollable(
                    rememberScrollableState { delta ->
                        viewModel.sendMouseScroll(if (delta > 0) 3 else -3)
                        onFeedback(if (delta > 0) "Scroll ↑" else "Scroll ↓")
                        delta
                    }, Orientation.Vertical
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("⋮", color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp, lineHeight = 14.sp)
        }

        // Right click
        Button(
            onClick = { viewModel.sendMouseClick("right"); onFeedback("Right click") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape    = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text("RIGHT", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}