package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalConfiguration
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

private const val TAP_MAX_PX   = 12f
private const val HOLD_MS      = 580L
private const val DTAP_MS      = 260L
private const val DRAG_INIT_MS = 270L

// ─────────────────────────────────────────────────────────────
//  MAIN — detects orientation and shows correct layout
// ─────────────────────────────────────────────────────────────

@Composable
fun PcControlTouchpadUI(viewModel: PcControlViewModel) {
    val configuration    = LocalConfiguration.current
    val isLandscape      = configuration.screenWidthDp > configuration.screenHeightDp
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    var sensitivity      by remember { mutableFloatStateOf(1f) }  // default 1 as requested
    var feedback         by remember { mutableStateOf("") }
    var isDragging       by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var feedbackJob by remember { mutableStateOf<Job?>(null) }

    fun setFeedback(msg: String) {
        feedback = msg
        feedbackJob?.cancel()
        feedbackJob = scope.launch { delay(1200); feedback = "" }
    }

    // Smooth animated transition portrait ↔ landscape
    AnimatedContent(
        targetState = isLandscape,
        transitionSpec = {
            fadeIn(tween(350)) + slideInHorizontally(tween(350)) togetherWith
                    fadeOut(tween(250)) + slideOutHorizontally(tween(250))
        },
        label = "orientation"
    ) { landscape ->
        if (landscape) {
            GameControllerUI(
                viewModel    = viewModel,
                sensitivity  = sensitivity,
                isDragging   = isDragging,
                feedback     = feedback,
                onDragChange = { isDragging = it },
                onFeedback   = { setFeedback(it) },
                onSensChange = { sensitivity = it },
                connectionStatus = connectionStatus
            )
        } else {
            PortraitTouchpadUI(
                viewModel    = viewModel,
                sensitivity  = sensitivity,
                isDragging   = isDragging,
                feedback     = feedback,
                connectionStatus = connectionStatus,
                onDragChange = { isDragging = it },
                onFeedback   = { setFeedback(it) },
                onSensChange = { sensitivity = it }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  PORTRAIT LAYOUT
// ═══════════════════════════════════════════════════════════════

@Composable
fun PortraitTouchpadUI(
    viewModel        : PcControlViewModel,
    sensitivity      : Float,
    isDragging       : Boolean,
    feedback         : String,
    connectionStatus : PcConnectionStatus,
    onDragChange     : (Boolean) -> Unit,
    onFeedback       : (String) -> Unit,
    onSensChange     : (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SmallTopBar(connectionStatus, sensitivity, isDragging, onSensChange,
            { viewModel.pingPc() }, { viewModel.navigateTo(PcScreen.KEYBOARD) })
        TouchpadSurface(
            modifier     = Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            sensitivity  = sensitivity,
            isDragging   = isDragging,
            feedback     = feedback,
            onDragChange = onDragChange,
            onFeedback   = onFeedback,
            viewModel    = viewModel
        )
        MouseButtonRow(viewModel, onFeedback)
        ButtonRow1(viewModel, onFeedback)
        ButtonRow2(viewModel, onFeedback)
        FKeyRow(viewModel, onFeedback)
        Spacer(Modifier.height(4.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
//  LANDSCAPE — GAME CONTROLLER LAYOUT
//
//  ┌──────┬──────────────────────────┬──────┐
//  │ TOP  │       TOP BUTTONS        │ TOP  │
//  │BTNS  │  (Alt+F4, Vol, F5, F11)  │BTNS  │
//  │ LEFT ├──────────────────────────┤ RIGHT│
//  │      │   ROUND TOUCHPAD CENTER  │      │
//  │ D-PAD│                          │A/B/X/Y│
//  │      │                          │      │
//  └──────┴──────────────────────────┴──────┘
// ═══════════════════════════════════════════════════════════════

@Composable
fun GameControllerUI(
    viewModel        : PcControlViewModel,
    sensitivity      : Float,
    isDragging       : Boolean,
    feedback         : String,
    connectionStatus : PcConnectionStatus,
    onDragChange     : (Boolean) -> Unit,
    onFeedback       : (String) -> Unit,
    onSensChange     : (Float) -> Unit
) {
    val dotColor = when (connectionStatus) {
        PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80)
        PcConnectionStatus.OFFLINE  -> MaterialTheme.colorScheme.error
        PcConnectionStatus.CHECKING -> Color(0xFFF59E0B)
        PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Status dot top-center
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
            Text(if (connectionStatus == PcConnectionStatus.ONLINE) "Connected" else "Offline",
                fontSize = 10.sp, color = dotColor)
            if (feedback.isNotEmpty()) {
                Text("• $feedback", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            // Speed control
            Text("Spd", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = sensitivity, onValueChange = onSensChange,
                valueRange = 1f..14f, steps = 12,
                modifier = Modifier.width(80.dp).height(20.dp))
            Text("${sensitivity.toInt()}", fontSize = 9.sp,
                color = MaterialTheme.colorScheme.primary)
            // Keyboard button
            IconButton(onClick = { viewModel.navigateTo(PcScreen.KEYBOARD) },
                modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Keyboard, null, Modifier.size(14.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── LEFT SIDE — D-Pad style ────────────────────
            Column(
                modifier = Modifier.width(130.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // L1/L2 shoulder buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CtrlBtn("L2", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendMouseClick("right"); onFeedback("Right Click") }
                    CtrlBtn("L1", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendMouseClick("left"); onFeedback("Left Click") }
                }
                Spacer(Modifier.weight(1f))
                // D-Pad arrows
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CtrlBtn("↑", Modifier.size(44.dp), MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendKey("UP") }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        CtrlBtn("←", Modifier.size(44.dp), MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant)
                        { viewModel.sendKey("LEFT") }
                        Spacer(Modifier.size(44.dp))
                        CtrlBtn("→", Modifier.size(44.dp), MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant)
                        { viewModel.sendKey("RIGHT") }
                    }
                    CtrlBtn("↓", Modifier.size(44.dp), MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendKey("DOWN") }
                }
                Spacer(Modifier.weight(1f))
                // Extra left buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CtrlBtn("Esc", Modifier.weight(1f), MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.error)
                    { viewModel.sendKey("ESC") }
                    CtrlBtn("Tab", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendKey("TAB") }
                }
            }

            // ── CENTER — Round Touchpad ────────────────────
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top action buttons row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CtrlBtn("Alt+F4", Modifier.weight(1f), MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.error)
                    { viewModel.sendKey("ALT+F4") }
                    CtrlBtn("Vol-", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.executeQuickStep(PcStep("SYSTEM_CMD","VOLUME_DOWN")) }
                    CtrlBtn("Mute", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.executeQuickStep(PcStep("SYSTEM_CMD","MUTE")) }
                    CtrlBtn("Vol+", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.executeQuickStep(PcStep("SYSTEM_CMD","VOLUME_UP")) }
                    CtrlBtn("F5", Modifier.weight(1f), MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer)
                    { viewModel.sendKey("F5") }
                    CtrlBtn("F11", Modifier.weight(1f), MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer)
                    { viewModel.sendKey("F11") }
                }

                // Round touchpad
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TouchpadSurface(
                        modifier     = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1.6f)
                            .clip(RoundedCornerShape(28.dp))
                            .shadow(6.dp, RoundedCornerShape(28.dp)),
                        sensitivity  = sensitivity,
                        isDragging   = isDragging,
                        feedback     = feedback,
                        onDragChange = onDragChange,
                        onFeedback   = onFeedback,
                        viewModel    = viewModel
                    )
                }

                // Bottom center buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CtrlBtn("Space", Modifier.width(100.dp),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer)
                    { viewModel.sendKey("SPACE") }
                    CtrlBtn("Enter", Modifier.width(80.dp),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer)
                    { viewModel.sendKey("ENTER") }
                    CtrlBtn("⌫", Modifier.width(60.dp),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendKey("BACKSPACE") }
                }
            }

            // ── RIGHT SIDE — Action buttons (A/B/X/Y style) ─
            Column(
                modifier = Modifier.width(130.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // R1/R2 shoulder
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CtrlBtn("R1", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendMouseScroll(3); onFeedback("Scroll ↑") }
                    CtrlBtn("R2", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendMouseScroll(-3); onFeedback("Scroll ↓") }
                }
                Spacer(Modifier.weight(1f))
                // A/B/X/Y action buttons — customizable
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Y (top)
                    CtrlBtn("Alt+Tab", Modifier.size(44.dp),
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer)
                    { viewModel.sendKey("ALT+TAB") }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        // X (left)
                        CtrlBtn("Win+D", Modifier.size(44.dp),
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer)
                        { viewModel.sendKey("WIN+D") }
                        Spacer(Modifier.size(44.dp))
                        // B (right)
                        CtrlBtn("Ctrl+C", Modifier.size(44.dp),
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer)
                        { viewModel.sendKey("CTRL+C") }
                    }
                    // A (bottom)
                    CtrlBtn("Ctrl+V", Modifier.size(44.dp),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer)
                    { viewModel.sendKey("CTRL+V") }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CtrlBtn("🔒Lock", Modifier.weight(1f), MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.error)
                    { viewModel.executeQuickStep(PcStep("SYSTEM_CMD","LOCK")) }
                    CtrlBtn("Ctrl+Z", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendKey("CTRL+Z") }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  GAME CONTROLLER BUTTON
// ─────────────────────────────────────────────────────────────

@Composable
fun CtrlBtn(
    label  : String,
    modifier: Modifier = Modifier,
    bg     : Color,
    fg     : Color,
    onClick: () -> Unit
) {
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { pressed = true; delay(80); pressed = false }
            onClick()
        },
        modifier = modifier.defaultMinSize(minHeight = 38.dp),
        shape    = RoundedCornerShape(10.dp),
        color    = if (pressed) fg.copy(0.25f) else bg,
        border   = BorderStroke(if (pressed) 1.5.dp else 1.dp,
            if (pressed) fg else fg.copy(0.2f)),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(3.dp)) {
            Text(label,
                fontSize   = if (label.length > 6) 8.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                color      = fg,
                textAlign  = TextAlign.Center,
                lineHeight = 11.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  PORTRAIT COMPONENTS (unchanged structure, sensitivity default 1)
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
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)) {
                Surface(onClick = onPing, shape = RoundedCornerShape(20.dp),
                    color = dotColor.copy(0.13f)) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                        Text(when (connectionStatus) {
                            PcConnectionStatus.ONLINE   -> "Online"
                            PcConnectionStatus.OFFLINE  -> "Offline"
                            PcConnectionStatus.CHECKING -> "Checking…"
                            PcConnectionStatus.UNKNOWN  -> "Tap to ping"
                        }, fontSize = 10.sp, color = dotColor, fontWeight = FontWeight.Bold)
                    }
                }
                if (isDragging) {
                    Surface(shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer) {
                        Text("✊ Dragging",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1.5f)) {
                Text("Spd", fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = sensitivity, onValueChange = onSensChange,
                    valueRange = 1f..14f, steps = 12,
                    modifier = Modifier.weight(1f).height(24.dp))
                Text("${sensitivity.toInt()}", fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(16.dp))
            }
            IconButton(onClick = onKeyboard, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Keyboard, "Keyboard",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TouchpadSurface(
    modifier    : Modifier,
    sensitivity : Float,
    isDragging  : Boolean,
    feedback    : String,
    onDragChange: (Boolean) -> Unit,
    onFeedback  : (String) -> Unit,
    viewModel   : PcControlViewModel
) {
    val haptic = LocalHapticFeedback.current
    val scope  = rememberCoroutineScope()
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
    var scrollIndicator by remember { mutableStateOf("") }
    var scrollIndJob    by remember { mutableStateOf<Job?>(null) }
    fun showScroll(d: String) {
        scrollIndicator = d
        scrollIndJob?.cancel()
        scrollIndJob = scope.launch { delay(700); scrollIndicator = "" }
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible  = scrollIndicator.isNotEmpty(),
            enter    = fadeIn() + slideInHorizontally { -it },
            exit     = fadeOut() + slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart).zIndex(10f).padding(start = 6.dp)
        ) {
            Surface(shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp) {
                Text(scrollIndicator,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(when {
                    isDragging -> MaterialTheme.colorScheme.errorContainer.copy(0.2f)
                    isActive   -> MaterialTheme.colorScheme.primaryContainer.copy(0.3f)
                    else       -> MaterialTheme.colorScheme.surfaceVariant
                })
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
                                    isActive = true; didMove = false
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
                                    val dist = sqrt((p.position.x-downX).let{it*it}+(p.position.y-downY).let{it*it})
                                    if (abs(dx) > 1f || abs(dy) > 1f) {
                                        if (dist > TAP_MAX_PX) { didMove = true; holdJob?.cancel() }
                                        when {
                                            fingers >= 2 -> {
                                                val up = dy < 0
                                                viewModel.sendMouseScroll(if (up) 3 else -3)
                                                showScroll(if (up) "↑" else "↓")
                                                onFeedback(if (up) "Scroll ↑" else "Scroll ↓")
                                            }
                                            isDragging -> viewModel.sendMouseDelta(dx, dy)
                                            didMove    -> viewModel.sendMouseDelta(dx, dy)
                                        }
                                        lastX = p.position.x; lastY = p.position.y
                                    }
                                }
                                PointerEventType.Release -> {
                                    isActive = false; holdJob?.cancel()
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
                                                tapJob?.cancel()
                                                viewModel.sendMouseButtonDown("left")
                                                onDragChange(true)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onFeedback("Drag — tap to drop")
                                                tapCount = 0
                                            }
                                            else -> {
                                                tapCount++; tapTime = now; tapJob?.cancel()
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
            when {
                isDragging -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("✊", fontSize = 28.sp)
                    Text("Dragging", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("Tap to drop", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(0.7f))
                }
                !isActive -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🖱️", fontSize = 28.sp)
                    Text("Touchpad", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap · ×2 Double · Hold Right · Tap+Hold Drag · 2-finger Scroll",
                        fontSize = 10.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                        modifier = Modifier.padding(horizontal = 20.dp))
                    if (feedback.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(feedback, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MouseButtonRow(vm: PcControlViewModel, onFeedback: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp)
        .clip(RoundedCornerShape(10.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))) {
        Button(onClick = { vm.sendMouseClick("left"); onFeedback("Left click") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
            contentPadding = PaddingValues(0.dp)) {
            Text("L", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        Box(modifier = Modifier.width(14.dp).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .scrollable(rememberScrollableState { d ->
                vm.sendMouseScroll(if (d > 0) 3 else -3)
                onFeedback(if (d > 0) "Scroll ↑" else "Scroll ↓"); d
            }, Orientation.Vertical), contentAlignment = Alignment.Center) {
            Text("⋮", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Button(onClick = { vm.sendMouseClick("right"); onFeedback("Right click") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
            contentPadding = PaddingValues(0.dp)) {
            Text("R", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun ButtonRow1(vm: PcControlViewModel, onFeedback: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        QuickKey("Alt+F4","Close", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, Modifier.weight(1.1f))
        { vm.sendKey("ALT+F4"); onFeedback("Alt+F4") }
        QuickKey("Enter","E Key", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f))
        { vm.sendKey("ENTER"); onFeedback("Enter") }
        QuickKey("Vol -","🔉", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(0.85f))
        { vm.executeQuickStep(PcStep("SYSTEM_CMD","VOLUME_DOWN")); onFeedback("Vol-") }
        QuickKey("Vol +","🔊", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(0.85f))
        { vm.executeQuickStep(PcStep("SYSTEM_CMD","VOLUME_UP")); onFeedback("Vol+") }
        QuickKey("Mute","🔇", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(0.85f))
        { vm.executeQuickStep(PcStep("SYSTEM_CMD","MUTE")); onFeedback("Mute") }
        QuickKey("F5","Refresh", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(0.85f))
        { vm.sendKey("F5"); onFeedback("F5") }
    }
}

@Composable
fun ButtonRow2(vm: PcControlViewModel, onFeedback: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        QuickKey("F11","Fullscr", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Modifier.weight(0.9f))
        { vm.sendKey("F11"); onFeedback("F11") }
        QuickKey("Esc","", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(0.8f))
        { vm.sendKey("ESC"); onFeedback("Esc") }
        QuickKey("Space","Play/Pause", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1.4f))
        { vm.sendKey("SPACE"); onFeedback("Space") }
        QuickKey("Alt+Tab","Switch", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1.1f))
        { vm.sendKey("ALT+TAB"); onFeedback("Alt+Tab") }
        QuickKey("Win+D","Desktop", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
        { vm.sendKey("WIN+D"); onFeedback("Win+D") }
    }
}

@Composable
fun FKeyRow(vm: PcControlViewModel, onFeedback: (String) -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        items((1..12).map { "F$it" }) { fk ->
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
            QuickKey(fk, "", bg, fg, Modifier.width(46.dp)) { vm.sendKey(fk); onFeedback(fk) }
        }
    }
}

@Composable
fun QuickKey(label: String, sub: String, bg: Color, fg: Color,
             modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { pressed = true; delay(90); pressed = false }
            onClick()
        },
        modifier = modifier.height(46.dp), shape = RoundedCornerShape(10.dp),
        color = if (pressed) fg.copy(0.25f) else bg,
        border = BorderStroke(if (pressed) 1.5.dp else 1.dp, if (pressed) fg else fg.copy(0.2f)),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(label, fontSize = if (label.length > 6) 8.5.sp else 10.sp,
                fontWeight = FontWeight.Bold, color = fg,
                textAlign = TextAlign.Center, lineHeight = 11.sp)
            if (sub.isNotEmpty()) Text(sub, fontSize = 8.sp, color = fg.copy(0.6f),
                textAlign = TextAlign.Center, lineHeight = 9.sp)
        }
    }
}