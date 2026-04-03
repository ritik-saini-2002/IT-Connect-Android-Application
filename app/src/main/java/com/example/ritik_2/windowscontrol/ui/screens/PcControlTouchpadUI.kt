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

// Gesture constants
private const val TAP_MAX_PX = 12f
private const val HOLD_MS    = 580L
private const val DTAP_MS    = 260L

// ─────────────────────────────────────────────────────────────
//  ROOT — orientation dispatch
// ─────────────────────────────────────────────────────────────

@Composable
fun PcControlTouchpadUI(viewModel: PcControlViewModel) {
    val cfg              = LocalConfiguration.current
    val isLandscape      = cfg.screenWidthDp > cfg.screenHeightDp
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    var sensitivity      by remember { mutableFloatStateOf(3f) }
    var feedback         by remember { mutableStateOf("") }
    val scope            = rememberCoroutineScope()
    var feedbackJob      by remember { mutableStateOf<Job?>(null) }

    fun showFeedback(msg: String) {
        feedback = msg
        feedbackJob?.cancel()
        feedbackJob = scope.launch { delay(1200); feedback = "" }
    }

    AnimatedContent(
        targetState = isLandscape,
        transitionSpec = {
            fadeIn(tween(300)) togetherWith fadeOut(tween(200))
        },
        label = "orientation"
    ) { landscape ->
        if (landscape) {
            LandscapeControllerUI(
                viewModel        = viewModel,
                sensitivity      = sensitivity,
                feedback         = feedback,
                connectionStatus = connectionStatus,
                onFeedback       = { showFeedback(it) },
                onSensChange     = { sensitivity = it }
            )
        } else {
            PortraitTouchpadUI(
                viewModel        = viewModel,
                sensitivity      = sensitivity,
                feedback         = feedback,
                connectionStatus = connectionStatus,
                onFeedback       = { showFeedback(it) },
                onSensChange     = { sensitivity = it }
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
    feedback         : String,
    connectionStatus : PcConnectionStatus,
    onFeedback       : (String) -> Unit,
    onSensChange     : (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SmallTopBar(
            connectionStatus = connectionStatus,
            sensitivity      = sensitivity,
            onSensChange     = onSensChange,
            onPing           = { viewModel.pingPc() },
            onKeyboard       = { viewModel.navigateTo(PcScreen.KEYBOARD) }
        )
        // Touchpad takes majority of space
        TouchpadSurface(
            modifier     = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            sensitivity  = sensitivity,
            feedback     = feedback,
            onFeedback   = onFeedback,
            viewModel    = viewModel
        )
        // Mouse buttons — bigger for portrait
        MouseButtonRow(viewModel, onFeedback)
        // Action rows
        PortraitRow1(viewModel, onFeedback)
        PortraitRow2(viewModel, onFeedback)
        FKeyRow(viewModel, onFeedback)
        Spacer(Modifier.height(6.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
//  LANDSCAPE GAME CONTROLLER
// ═══════════════════════════════════════════════════════════════

@Composable
fun LandscapeControllerUI(
    viewModel        : PcControlViewModel,
    sensitivity      : Float,
    feedback         : String,
    connectionStatus : PcConnectionStatus,
    onFeedback       : (String) -> Unit,
    onSensChange     : (Float) -> Unit
) {
    val dotColor = connectionStatusColor(connectionStatus)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Status bar across the top
        Surface(
            color    = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                Text(
                    when (connectionStatus) {
                        PcConnectionStatus.ONLINE   -> "Connected"
                        PcConnectionStatus.OFFLINE  -> "Offline"
                        PcConnectionStatus.CHECKING -> "Checking…"
                        PcConnectionStatus.UNKNOWN  -> "Unknown"
                    },
                    fontSize = 11.sp, color = dotColor, fontWeight = FontWeight.SemiBold
                )
                if (feedback.isNotEmpty()) {
                    Text("•  $feedback", fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                Text("Spd", fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value          = sensitivity,
                    onValueChange  = onSensChange,
                    valueRange     = 1f..14f,
                    steps          = 12,
                    modifier       = Modifier.width(80.dp).height(20.dp)
                )
                Text("${sensitivity.toInt()}", fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.primary)
                IconButton(
                    onClick  = { viewModel.navigateTo(PcScreen.KEYBOARD) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Keyboard, null, Modifier.size(15.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 34.dp, start = 6.dp, end = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            // ── LEFT PANEL ────────────────────────────────
            Column(
                modifier = Modifier.width(120.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Shoulder buttons
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CtrlBtn("Right Click", Modifier.weight(1f),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendMouseClick("right"); onFeedback("Right Click") }
                    CtrlBtn("Left Click", Modifier.weight(1f),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer)
                    { viewModel.sendMouseClick("left"); onFeedback("Left Click") }
                }
                Spacer(Modifier.weight(1f))
                // D-pad
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    CtrlBtn("↑", Modifier.size(46.dp),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendKey("UP") }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        CtrlBtn("←", Modifier.size(46.dp),
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant)
                        { viewModel.sendKey("LEFT") }
                        Spacer(Modifier.size(46.dp))
                        CtrlBtn("→", Modifier.size(46.dp),
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant)
                        { viewModel.sendKey("RIGHT") }
                    }
                    CtrlBtn("↓", Modifier.size(46.dp),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendKey("DOWN") }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CtrlBtn("Esc", Modifier.weight(1f),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.error)
                    { viewModel.sendKey("ESC") }
                    CtrlBtn("Tab", Modifier.weight(1f),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendKey("TAB") }
                }
            }

            // ── CENTER PANEL ──────────────────────────────
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top media/system row
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        Triple("Alt+F4",  MaterialTheme.colorScheme.errorContainer,    MaterialTheme.colorScheme.error)     to { viewModel.sendKey("ALT+F4") },
                        Triple("Vol-",    MaterialTheme.colorScheme.surfaceVariant,     MaterialTheme.colorScheme.onSurfaceVariant) to { viewModel.executeQuickStep(PcStep("SYSTEM_CMD","VOLUME_DOWN")) },
                        Triple("Mute",    MaterialTheme.colorScheme.surfaceVariant,     MaterialTheme.colorScheme.onSurfaceVariant) to { viewModel.executeQuickStep(PcStep("SYSTEM_CMD","MUTE")) },
                        Triple("Vol+",    MaterialTheme.colorScheme.surfaceVariant,     MaterialTheme.colorScheme.onSurfaceVariant) to { viewModel.executeQuickStep(PcStep("SYSTEM_CMD","VOLUME_UP")) },
                        Triple("F5",      MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) to { viewModel.sendKey("F5") },
                        Triple("F11",     MaterialTheme.colorScheme.tertiaryContainer,  MaterialTheme.colorScheme.onTertiaryContainer) to { viewModel.sendKey("F11") },
                    ).forEach { (tripBg, action) ->
                        val (lbl, bg, fg) = tripBg
                        CtrlBtn(lbl, Modifier.weight(1f), bg, fg) { action(); onFeedback(lbl) }
                    }
                }

                // Round touchpad
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TouchpadSurface(
                        modifier     = Modifier
                            .fillMaxHeight(0.92f)
                            .aspectRatio(1.8f)
                            .clip(RoundedCornerShape(32.dp))
                            .shadow(6.dp, RoundedCornerShape(32.dp)),
                        sensitivity  = sensitivity,
                        feedback     = feedback,
                        onFeedback   = onFeedback,
                        viewModel    = viewModel
                    )
                }

                // Bottom center
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CtrlBtn("Space", Modifier.width(110.dp),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer)
                    { viewModel.sendKey("SPACE") }
                    CtrlBtn("Enter", Modifier.width(90.dp),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer)
                    { viewModel.sendKey("ENTER") }
                    CtrlBtn("⌫", Modifier.width(70.dp),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendKey("BACKSPACE") }
                }
            }

            // ── RIGHT PANEL ───────────────────────────────
            Column(
                modifier = Modifier.width(120.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CtrlBtn("Scroll↑", Modifier.weight(1f),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendMouseScroll(3); onFeedback("Scroll ↑") }
                    CtrlBtn("Scroll↓", Modifier.weight(1f),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendMouseScroll(-3); onFeedback("Scroll ↓") }
                }
                Spacer(Modifier.weight(1f))
                // Action diamond
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    CtrlBtn("Alt+Tab", Modifier.size(46.dp),
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer)
                    { viewModel.sendKey("ALT+TAB") }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        CtrlBtn("Win+D", Modifier.size(46.dp),
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer)
                        { viewModel.sendKey("WIN+D") }
                        Spacer(Modifier.size(46.dp))
                        CtrlBtn("Ctrl+C", Modifier.size(46.dp),
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer)
                        { viewModel.sendKey("CTRL+C") }
                    }
                    CtrlBtn("Ctrl+V", Modifier.size(46.dp),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer)
                    { viewModel.sendKey("CTRL+V") }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CtrlBtn("🔒 Lock", Modifier.weight(1f),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.error)
                    { viewModel.executeQuickStep(PcStep("SYSTEM_CMD","LOCK")) }
                    CtrlBtn("Ctrl+Z", Modifier.weight(1f),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    { viewModel.sendKey("CTRL+Z") }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  TOUCHPAD SURFACE — no drag support, improved gestures
// ─────────────────────────────────────────────────────────────

@Composable
fun TouchpadSurface(
    modifier    : Modifier,
    sensitivity : Float,
    feedback    : String,
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
    var scrollInd by remember { mutableStateOf("") }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    fun showScroll(d: String) {
        scrollInd = d
        scrollJob?.cancel()
        scrollJob = scope.launch { delay(700); scrollInd = "" }
    }

    Box(modifier = modifier) {
        // Scroll indicator
        AnimatedVisibility(
            visible  = scrollInd.isNotEmpty(),
            enter    = fadeIn() + slideInHorizontally { -it },
            exit     = fadeOut() + slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart).zIndex(10f).padding(start = 8.dp)
        ) {
            Surface(
                shape          = RoundedCornerShape(10.dp),
                color          = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp
            ) {
                Text(scrollInd,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color    = MaterialTheme.colorScheme.primary)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(0.3f)
                    else          MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    width = if (isActive) 1.5.dp else 1.dp,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else          MaterialTheme.colorScheme.outline.copy(0.35f),
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
                                    val dist = run {
                                        val ex = p.position.x - downX
                                        val ey = p.position.y - downY
                                        sqrt(ex * ex + ey * ey)
                                    }
                                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                                        if (dist > TAP_MAX_PX) {
                                            didMove = true
                                            holdJob?.cancel()
                                        }
                                        if (fingers >= 2) {
                                            val up = dy < 0
                                            viewModel.sendMouseScroll(if (up) 3 else -3)
                                            showScroll(if (up) "↑" else "↓")
                                            onFeedback(if (up) "Scroll ↑" else "Scroll ↓")
                                        } else if (didMove) {
                                            viewModel.sendMouseDelta(dx, dy)
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
                                        tapCount++
                                        tapTime = now
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
            if (!isActive) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🖱️", fontSize = 28.sp)
                    Text("Touchpad",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap · ×2 Double click · Hold = Right click · 2-finger Scroll",
                        fontSize  = 10.sp,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                        modifier  = Modifier.padding(horizontal = 20.dp))
                    if (feedback.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(feedback,
                                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style      = MaterialTheme.typography.labelSmall,
                                color      = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  PORTRAIT BUTTON ROWS — bigger, more spaced
// ─────────────────────────────────────────────────────────────

@Composable
fun SmallTopBar(
    connectionStatus : PcConnectionStatus,
    sensitivity      : Float,
    onSensChange     : (Float) -> Unit,
    onPing           : () -> Unit,
    onKeyboard       : () -> Unit
) {
    val dotColor = connectionStatusColor(connectionStatus)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                onClick = onPing,
                shape   = RoundedCornerShape(20.dp),
                color   = dotColor.copy(0.13f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                    Text(when (connectionStatus) {
                        PcConnectionStatus.ONLINE   -> "Online"
                        PcConnectionStatus.OFFLINE  -> "Offline"
                        PcConnectionStatus.CHECKING -> "Checking…"
                        PcConnectionStatus.UNKNOWN  -> "Tap to ping"
                    }, fontSize = 11.sp, color = dotColor, fontWeight = FontWeight.Bold)
                }
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Spd", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value         = sensitivity,
                    onValueChange = onSensChange,
                    valueRange    = 1f..14f,
                    steps         = 12,
                    modifier      = Modifier.width(90.dp).height(24.dp)
                )
                Text("${sensitivity.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(16.dp))
            }
            IconButton(onClick = onKeyboard, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Keyboard, "Keyboard",
                    modifier = Modifier.size(20.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MouseButtonRow(vm: PcControlViewModel, onFeedback: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Button(
            onClick  = { vm.sendMouseClick("left"); onFeedback("Left click") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape    = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer),
            contentPadding = PaddingValues(0.dp)
        ) { Text("L", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }

        // Scroll strip
        Box(
            modifier = Modifier
                .width(18.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .scrollable(
                    rememberScrollableState { d ->
                        vm.sendMouseScroll(if (d > 0) 3 else -3)
                        onFeedback(if (d > 0) "Scroll ↑" else "Scroll ↓"); d
                    }, Orientation.Vertical),
            contentAlignment = Alignment.Center
        ) {
            Text("⋮", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }

        Button(
            onClick  = { vm.sendMouseClick("right"); onFeedback("Right click") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape    = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer),
            contentPadding = PaddingValues(0.dp)
        ) { Text("R", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
    }
}

@Composable
fun PortraitRow1(vm: PcControlViewModel, onFeedback: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        QuickKey("Alt+F4", "Close",   MaterialTheme.colorScheme.errorContainer,      MaterialTheme.colorScheme.error,               Modifier.weight(1.2f)) { vm.sendKey("ALT+F4");  onFeedback("Alt+F4") }
        QuickKey("Enter",  "",         MaterialTheme.colorScheme.primaryContainer,     MaterialTheme.colorScheme.onPrimaryContainer,   Modifier.weight(1f))   { vm.sendKey("ENTER");   onFeedback("Enter") }
        QuickKey("Vol -",  "🔉",       MaterialTheme.colorScheme.surfaceVariant,       MaterialTheme.colorScheme.onSurfaceVariant,     Modifier.weight(0.9f)) { vm.executeQuickStep(PcStep("SYSTEM_CMD","VOLUME_DOWN")); onFeedback("Vol-") }
        QuickKey("Vol +",  "🔊",       MaterialTheme.colorScheme.surfaceVariant,       MaterialTheme.colorScheme.onSurfaceVariant,     Modifier.weight(0.9f)) { vm.executeQuickStep(PcStep("SYSTEM_CMD","VOLUME_UP"));   onFeedback("Vol+") }
        QuickKey("Mute",   "🔇",       MaterialTheme.colorScheme.surfaceVariant,       MaterialTheme.colorScheme.onSurfaceVariant,     Modifier.weight(0.9f)) { vm.executeQuickStep(PcStep("SYSTEM_CMD","MUTE"));        onFeedback("Mute") }
        QuickKey("F5",     "Refresh",  MaterialTheme.colorScheme.secondaryContainer,   MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(0.9f)) { vm.sendKey("F5");      onFeedback("F5") }
    }
}

@Composable
fun PortraitRow2(vm: PcControlViewModel, onFeedback: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        QuickKey("F11",      "Full",    MaterialTheme.colorScheme.tertiaryContainer,   MaterialTheme.colorScheme.onTertiaryContainer,  Modifier.weight(0.9f)) { vm.sendKey("F11");     onFeedback("F11") }
        QuickKey("Esc",      "",        MaterialTheme.colorScheme.surfaceVariant,       MaterialTheme.colorScheme.onSurfaceVariant,     Modifier.weight(0.8f)) { vm.sendKey("ESC");     onFeedback("Esc") }
        QuickKey("Space",    "Play",    MaterialTheme.colorScheme.primaryContainer,     MaterialTheme.colorScheme.onPrimaryContainer,   Modifier.weight(1.4f)) { vm.sendKey("SPACE");   onFeedback("Space") }
        QuickKey("Alt+Tab",  "Switch",  MaterialTheme.colorScheme.secondaryContainer,  MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1.2f)) { vm.sendKey("ALT+TAB"); onFeedback("Alt+Tab") }
        QuickKey("Win+D",    "Desktop", MaterialTheme.colorScheme.surfaceVariant,       MaterialTheme.colorScheme.onSurfaceVariant,     Modifier.weight(1.1f)) { vm.sendKey("WIN+D");   onFeedback("Win+D") }
    }
}

@Composable
fun FKeyRow(vm: PcControlViewModel, onFeedback: (String) -> Unit) {
    LazyRow(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
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
            QuickKey(fk, "", bg, fg, Modifier.width(50.dp)) { vm.sendKey(fk); onFeedback(fk) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  REUSABLE BUTTON COMPONENTS
// ─────────────────────────────────────────────────────────────

@Composable
fun CtrlBtn(
    label   : String,
    modifier: Modifier = Modifier,
    bg      : Color,
    fg      : Color,
    onClick : () -> Unit
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
        modifier = modifier.defaultMinSize(minHeight = 42.dp),
        shape    = RoundedCornerShape(10.dp),
        color    = if (pressed) fg.copy(0.25f) else bg,
        border   = BorderStroke(
            if (pressed) 1.5.dp else 1.dp,
            if (pressed) fg else fg.copy(0.2f)),
        tonalElevation = 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.fillMaxSize().padding(3.dp)
        ) {
            Text(
                label,
                fontSize   = if (label.length > 8) 8.sp else if (label.length > 5) 9.5.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color      = fg,
                textAlign  = TextAlign.Center,
                lineHeight = 12.sp
            )
        }
    }
}

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
        modifier = modifier.height(50.dp),
        shape    = RoundedCornerShape(10.dp),
        color    = if (pressed) fg.copy(0.25f) else bg,
        border   = BorderStroke(if (pressed) 1.5.dp else 1.dp, if (pressed) fg else fg.copy(0.2f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label,
                fontSize   = if (label.length > 6) 9.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color      = fg,
                textAlign  = TextAlign.Center,
                lineHeight = 12.sp)
            if (sub.isNotEmpty()) {
                Text(sub, fontSize = 8.sp, color = fg.copy(0.65f),
                    textAlign = TextAlign.Center, lineHeight = 9.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  HELPERS
// ─────────────────────────────────────────────────────────────

@Composable
private fun connectionStatusColor(status: PcConnectionStatus): Color = when (status) {
    PcConnectionStatus.ONLINE   -> Color(0xFF22C55E)
    PcConnectionStatus.OFFLINE  -> MaterialTheme.colorScheme.error
    PcConnectionStatus.CHECKING -> Color(0xFFF59E0B)
    PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
}