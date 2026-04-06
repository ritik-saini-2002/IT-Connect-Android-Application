package com.example.ritik_2.windowscontrol.pctouchpad

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.windowscontrol.PcControlMain
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// ── Gesture thresholds ────────────────────────────────────────
private const val TAP_MAX_PX    = 10f
private const val HOLD_MS       = 550L
private const val DTAP_MS       = 230L
private const val SCROLL_MIN_PX = 4f
private const val DRAG_MIN_PX   = 6f

// ─────────────────────────────────────────────────────────────
//  ROOT
// ─────────────────────────────────────────────────────────────
@Composable
fun PcControlTouchpadUI(viewModel: PcControlViewModel) {
    val isLandscape      = LocalConfiguration.current.run { screenWidthDp > screenHeightDp }
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    var sensitivity      by remember { mutableFloatStateOf(2f) }
    var feedback         by remember { mutableStateOf("") }
    var liveScreenOn     by remember { mutableStateOf(false) }
    val scope            = rememberCoroutineScope()
    var fbJob            by remember { mutableStateOf<Job?>(null) }

    val showFeedback: (String) -> Unit = remember(scope) { { msg ->
        feedback = msg
        fbJob?.cancel()
        fbJob = scope.launch { delay(1_000); feedback = "" }
    }}

    AnimatedContent(
        targetState    = isLandscape,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
        label          = "orientation"
    ) { landscape ->
        if (landscape) {
            LandscapeLayout(
                vm = viewModel, sensitivity = sensitivity, feedback = feedback,
                connectionStatus = connectionStatus, liveScreenOn = liveScreenOn,
                onLiveToggle = { liveScreenOn = it }, onFeedback = showFeedback,
                onSensChange = { sensitivity = it }
            )
        } else {
            PortraitLayout(
                vm = viewModel, sensitivity = sensitivity, feedback = feedback,
                connectionStatus = connectionStatus, liveScreenOn = liveScreenOn,
                onLiveToggle = { liveScreenOn = it }, onFeedback = showFeedback,
                onSensChange = { sensitivity = it }
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════
//  LIVE SCREEN BACKGROUND
// ═════════════════════════════════════════════════════════════
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveScreenBackground(isOn: Boolean, modifier: Modifier = Modifier) {
    val settings  = remember { PcControlMain.getSettings() }
    val viewerUrl = remember(settings) {
        val base = settings.baseUrl.trimEnd('/')        // remove any trailing slash
        val key  = settings.secretKey.trim()            // remove any accidental whitespace
        "$base/screen/viewer?key=$key&q=12&w=720&fps=8"
    }

    // Keep WebView instance alive across recompositions so we can
    // start/stop the stream without recreating the view every time.
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // React to isOn changes: load or blank the stream
    LaunchedEffect(isOn) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (isOn) wv.loadUrl(viewerUrl)
        else      { wv.stopLoading(); wv.loadUrl("about:blank") }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                // Hide the view entirely when stream is off so it doesn't
                // sit on top of controls as an invisible touch-interceptor
                .alpha(if (isOn) 1f else 0f),
            factory = { ctx ->
                WebView(ctx).also { wv ->
                    wv.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView, request: WebResourceRequest
                        ) = true
                    }
                    with(wv.settings) {
                        javaScriptEnabled                = true
                        loadWithOverviewMode             = true
                        useWideViewPort                  = true
                        builtInZoomControls              = false
                        displayZoomControls              = false
                        mediaPlaybackRequiresUserGesture = false
                    }
                    wv.setBackgroundColor(android.graphics.Color.BLACK)
                    wv.alpha   = 0.22f
                    webViewRef = wv
                    android.util.Log.d("LiveScreen", "Viewer URL: $viewerUrl")
                    if (isOn) wv.loadUrl(viewerUrl)
                }
            },
            update = { wv -> webViewRef = wv }
        )
    }
}

// ═════════════════════════════════════════════════════════════
//  LANDSCAPE
// ═════════════════════════════════════════════════════════════
@Composable
fun LandscapeLayout(
    vm               : PcControlViewModel,
    sensitivity      : Float,
    feedback         : String,
    connectionStatus : PcConnectionStatus,
    liveScreenOn     : Boolean,
    onLiveToggle     : (Boolean) -> Unit,
    onFeedback       : (String) -> Unit,
    onSensChange     : (Float) -> Unit,
) {
    val dotColor = connectionStatus.toColor()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val sideW  = maxWidth  * 0.10f
        val sideWL = maxWidth  * 0.20f
        val gap     = 4.dp
        val padV    = 25.dp
        val topRowH = 28.dp
        val midRowH = 36.dp
        val botRowH = 40.dp
        val tpH     = maxHeight - (padV * 2 + gap * 3 + topRowH + midRowH + botRowH)

        LiveScreenBackground(isOn = liveScreenOn, modifier = Modifier.fillMaxSize())

        Row(
            modifier              = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = padV),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {

            // ── LEFT PANEL ────────────────────────────────
            Column(
                modifier            = Modifier.width(sideWL).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                Row(Modifier.fillMaxWidth().height(topRowH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    TBtn("L", Modifier.weight(1f).fillMaxHeight(),
                        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    { vm.sendMouseClick("left"); onFeedback("L Click") }
                    TBtn("R", Modifier.weight(1f).fillMaxHeight(),
                        MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    { vm.sendMouseClick("right"); onFeedback("R Click") }
                }
                Row(Modifier.fillMaxWidth().height(midRowH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    TBtn("Scroll↑", Modifier.weight(1f).fillMaxHeight(),
                        MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    { vm.sendMouseScroll(3); onFeedback("Scroll ↑") }
                    TBtn("Scroll↓", Modifier.weight(1f).fillMaxHeight(),
                        MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    { vm.sendMouseScroll(-3); onFeedback("Scroll ↓") }
                }

                Box(Modifier.fillMaxWidth().height(tpH), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        val arrowMod = Modifier.width(sideWL * 0.45f).height(36.dp)
                        val arrowBg  = MaterialTheme.colorScheme.surfaceVariant
                        val arrowFg  = MaterialTheme.colorScheme.onSurfaceVariant
                        TBtn("▲", arrowMod, arrowBg, arrowFg) { vm.sendKey("UP") }
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            TBtn("◀", arrowMod, arrowBg, arrowFg) { vm.sendKey("LEFT") }
                            Spacer(Modifier.width(sideWL * 0.08f))
                            TBtn("▶", arrowMod, arrowBg, arrowFg) { vm.sendKey("RIGHT") }
                        }
                        TBtn("▼", arrowMod, arrowBg, arrowFg) { vm.sendKey("DOWN") }
                        Spacer(Modifier.height(4.dp))
                        LiveToggleButton(
                            isOn = liveScreenOn, onToggle = onLiveToggle,
                            modifier = Modifier.fillMaxWidth().height(32.dp)
                        )
                    }
                }

                Row(Modifier.fillMaxWidth().height(botRowH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    TBtn("Esc", Modifier.weight(1f).fillMaxHeight(),
                        MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
                    { vm.sendKey("ESC") }
                    TBtn("Tab", Modifier.weight(1f).fillMaxHeight(),
                        MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    { vm.sendKey("TAB") }
                }
            }

            // ── CENTRE PANEL ──────────────────────────────
            Column(
                modifier            = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                StatusBar(
                    modifier         = Modifier.fillMaxWidth().height(topRowH),
                    connectionStatus = connectionStatus,
                    dotColor         = dotColor,
                    liveScreenOn     = liveScreenOn,
                    feedback         = feedback,
                    sensitivity      = sensitivity,
                    onSensChange     = onSensChange
                )

                // Media strip
                Row(Modifier.fillMaxWidth().height(midRowH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    MediaButtons(vm = vm, onFeedback = onFeedback)
                }

                LaptopTouchpad(
                    modifier        = Modifier.fillMaxWidth().height(tpH),
                    sensitivity     = sensitivity,
                    feedback        = feedback,
                    onFeedback      = onFeedback,
                    vm              = vm,
                    semiTransparent = liveScreenOn
                )

                Row(Modifier.fillMaxWidth().height(botRowH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    TBtn("Space", Modifier.weight(2f).fillMaxHeight(),
                        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    { vm.sendKey("SPACE"); onFeedback("Space") }
                    TBtn("Enter", Modifier.weight(1.5f).fillMaxHeight(),
                        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    { vm.sendKey("ENTER"); onFeedback("Enter") }
                    TBtn("Backspace", Modifier.weight(1f).fillMaxHeight(),
                        MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    { vm.sendKey("BACKSPACE"); onFeedback("Backspace") }
                }
            }

            // ── RIGHT PANEL ───────────────────────────────
            Column(
                modifier            = Modifier.width(sideW).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                TBtn("Alt+Tab", Modifier.fillMaxWidth().height(topRowH),
                    MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                { vm.sendKey("ALT+TAB"); onFeedback("Alt+Tab") }
                Row(Modifier.fillMaxWidth().height(midRowH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    TBtn("Copy", Modifier.weight(1f).fillMaxHeight(),
                        MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    { vm.sendKey("CTRL+C"); onFeedback("Copy") }
                    TBtn("Paste", Modifier.weight(1f).fillMaxHeight(),
                        MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    { vm.sendKey("CTRL+V"); onFeedback("Paste") }
                }
                Column(Modifier.fillMaxWidth().height(tpH), verticalArrangement = Arrangement.spacedBy(gap)) {
                    TBtn("Win+D", Modifier.fillMaxWidth().weight(1f),
                        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    { vm.sendKey("WIN+D"); onFeedback("Win+D") }
                    TBtn("Undo", Modifier.fillMaxWidth().weight(1f),
                        MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    { vm.sendKey("CTRL+Z"); onFeedback("Undo") }
                    TBtn("Keys", Modifier.fillMaxWidth().weight(1f),
                        MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    { vm.navigateTo(PcScreen.KEYBOARD) }
                    TBtn("Screenshot", Modifier.fillMaxWidth().weight(1f),
                        MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    { vm.executeQuickStep(PcStep("SYSTEM_CMD", "SCREENSHOT")); onFeedback("Screenshot") }
                }
                TBtn("Lock", Modifier.fillMaxWidth().height(botRowH),
                    MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
                { vm.executeQuickStep(PcStep("SYSTEM_CMD", "LOCK")) }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
//  PORTRAIT
// ═════════════════════════════════════════════════════════════
@Composable
fun PortraitLayout(
    vm               : PcControlViewModel,
    sensitivity      : Float,
    feedback         : String,
    connectionStatus : PcConnectionStatus,
    liveScreenOn     : Boolean,
    onLiveToggle     : (Boolean) -> Unit,
    onFeedback       : (String) -> Unit,
    onSensChange     : (Float) -> Unit,
) {
    val dotColor = connectionStatus.toColor()
    val gap      = 4.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LiveScreenBackground(isOn = liveScreenOn, modifier = Modifier.fillMaxSize())

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            // ── Status bar ────────────────────────────────
            Surface(
                color    = MaterialTheme.colorScheme.surfaceVariant.copy(if (liveScreenOn) 0.80f else 1f),
                shape    = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(30.dp)
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { vm.pingPc() },
                        shape   = RoundedCornerShape(20.dp),
                        color   = dotColor.copy(0.15f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                            Text(
                                connectionStatus.label,
                                fontSize = 10.sp, color = dotColor, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (liveScreenOn) LiveBadge()
                    Spacer(Modifier.weight(1f))
                    Text("Spd", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = sensitivity, onValueChange = onSensChange,
                        valueRange = 1f..12f, steps = 10,
                        modifier   = Modifier.width(180.dp).height(20.dp)
                    )
                    Text(
                        "${sensitivity.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color    = MaterialTheme.colorScheme.primary, modifier = Modifier.width(14.dp)
                    )
                    TextButton(
                        onClick        = { vm.navigateTo(PcScreen.KEYBOARD) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) { Text("Keys", fontSize = 10.sp) }
                }
            }

            LaptopTouchpad(
                modifier        = Modifier.fillMaxWidth().weight(1f),
                sensitivity     = sensitivity,
                feedback        = feedback,
                onFeedback      = onFeedback,
                vm              = vm,
                semiTransparent = liveScreenOn
            )

            // ── Mouse buttons row ─────────────────────────
            Row(Modifier.fillMaxWidth().height(55.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                TBtn("L", Modifier.weight(1f).fillMaxHeight(),
                    MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                { vm.sendMouseClick("left"); onFeedback("L Click") }
                ScrollWheel(vm = vm, onFeedback = onFeedback)
                TBtn("R", Modifier.weight(1f).fillMaxHeight(),
                    MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                { vm.sendMouseClick("right"); onFeedback("R Click") }
            }

            // ── Action row 1 ──────────────────────────────
            Row(Modifier.fillMaxWidth().height(55.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                LiveToggleButton(isOn = liveScreenOn, onToggle = onLiveToggle, modifier = Modifier.width(58.dp).fillMaxHeight())
                TBtn("Alt+F4", Modifier.weight(1.2f).fillMaxHeight(), MaterialTheme.colorScheme.errorContainer,     MaterialTheme.colorScheme.error)                 { vm.sendKey("ALT+F4");  onFeedback("Alt+F4") }
                TBtn("Enter",  Modifier.weight(1f).fillMaxHeight(),   MaterialTheme.colorScheme.primaryContainer,   MaterialTheme.colorScheme.onPrimaryContainer)    { vm.sendKey("ENTER");   onFeedback("Enter") }
                TBtn("Vol -",  Modifier.weight(0.9f).fillMaxHeight(), MaterialTheme.colorScheme.surfaceVariant,     MaterialTheme.colorScheme.onSurfaceVariant)      { vm.executeQuickStep(PcStep("SYSTEM_CMD","VOLUME_DOWN")); onFeedback("Vol-") }
                TBtn("Vol +",  Modifier.weight(0.9f).fillMaxHeight(), MaterialTheme.colorScheme.surfaceVariant,     MaterialTheme.colorScheme.onSurfaceVariant)      { vm.executeQuickStep(PcStep("SYSTEM_CMD","VOLUME_UP"));   onFeedback("Vol+") }
                TBtn("Mute",   Modifier.weight(0.9f).fillMaxHeight(), MaterialTheme.colorScheme.surfaceVariant,     MaterialTheme.colorScheme.onSurfaceVariant)      { vm.executeQuickStep(PcStep("SYSTEM_CMD","MUTE"));        onFeedback("Mute") }
                TBtn("F5",     Modifier.weight(0.9f).fillMaxHeight(), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)  { vm.sendKey("F5");      onFeedback("F5") }
            }

            // ── Action row 2 ──────────────────────────────
            Row(Modifier.fillMaxWidth().height(55.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                TBtn("F11",     Modifier.weight(0.9f).fillMaxHeight(), MaterialTheme.colorScheme.tertiaryContainer,  MaterialTheme.colorScheme.onTertiaryContainer)  { vm.sendKey("F11");     onFeedback("F11") }
                TBtn("Esc",     Modifier.weight(0.8f).fillMaxHeight(), MaterialTheme.colorScheme.surfaceVariant,     MaterialTheme.colorScheme.onSurfaceVariant)     { vm.sendKey("ESC");     onFeedback("Esc") }
                TBtn("Space",   Modifier.weight(1.4f).fillMaxHeight(), MaterialTheme.colorScheme.primaryContainer,   MaterialTheme.colorScheme.onPrimaryContainer)   { vm.sendKey("SPACE");   onFeedback("Space") }
                TBtn("Alt+Tab", Modifier.weight(1.2f).fillMaxHeight(), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) { vm.sendKey("ALT+TAB"); onFeedback("Alt+Tab") }
                TBtn("Win+D",   Modifier.weight(1.1f).fillMaxHeight(), MaterialTheme.colorScheme.surfaceVariant,     MaterialTheme.colorScheme.onSurfaceVariant)     { vm.sendKey("WIN+D");   onFeedback("Win+D") }
            }

            // ── F-key scrollable row ──────────────────────
            Row(
                Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                repeat(12) { i ->
                    val fk = "F${i + 1}"
                    val bg = when (fk) { "F5"  -> MaterialTheme.colorScheme.secondaryContainer; "F11" -> MaterialTheme.colorScheme.tertiaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant }
                    val fg = when (fk) { "F5"  -> MaterialTheme.colorScheme.onSecondaryContainer; "F11" -> MaterialTheme.colorScheme.onTertiaryContainer; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                    TBtn(fk, Modifier.width(48.dp).fillMaxHeight(), bg, fg) { vm.sendKey(fk); onFeedback(fk) }
                }
            }

            Spacer(Modifier.height(2.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════
//  SHARED SMALL COMPOSABLES
// ═════════════════════════════════════════════════════════════

/** "● LIVE" red badge */
@Composable
private fun LiveBadge() {
    Text("● LIVE", fontSize = 9.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
}

/** Reusable status bar for landscape mode */
@Composable
private fun StatusBar(
    modifier         : Modifier,
    connectionStatus : PcConnectionStatus,
    dotColor         : Color,
    liveScreenOn     : Boolean,
    feedback         : String,
    sensitivity      : Float,
    onSensChange     : (Float) -> Unit,
) {
    Surface(
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(if (liveScreenOn) 0.85f else 1f),
        shape    = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
            Text(connectionStatus.label, fontSize = 9.sp, color = dotColor, fontWeight = FontWeight.Bold)
            if (liveScreenOn) LiveBadge()
            if (feedback.isNotEmpty())
                Text("· $feedback", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f), maxLines = 1)
            else Spacer(Modifier.weight(1f))
            Text("Spd", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = sensitivity, onValueChange = onSensChange, valueRange = 1f..12f, steps = 10,
                modifier = Modifier.width(180.dp).height(20.dp))
            Text("${sensitivity.toInt()}", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(12.dp))
        }
    }
}

/** 6-button media strip used inside the landscape centre panel */
@Composable
private fun RowScope.MediaButtons(vm: PcControlViewModel, onFeedback: (String) -> Unit) {
    data class MediaBtn(val label: String, val action: () -> Unit)
    val buttons = listOf(
        MediaBtn("Alt+F4") { vm.sendKey("ALT+F4");                                              onFeedback("Alt+F4") },
        MediaBtn("Vol -")  { vm.executeQuickStep(PcStep("SYSTEM_CMD","VOLUME_DOWN"));            onFeedback("Vol-")   },
        MediaBtn("Mute")   { vm.executeQuickStep(PcStep("SYSTEM_CMD","MUTE"));                   onFeedback("Mute")   },
        MediaBtn("Vol +")  { vm.executeQuickStep(PcStep("SYSTEM_CMD","VOLUME_UP"));              onFeedback("Vol+")   },
        MediaBtn("F5")     { vm.sendKey("F5");                                                   onFeedback("F5")     },
        MediaBtn("F11")    { vm.sendKey("F11");                                                  onFeedback("F11")    },
    )
    buttons.forEachIndexed { i, btn ->
        val bg = when (i) {
            0    -> MaterialTheme.colorScheme.errorContainer
            4    -> MaterialTheme.colorScheme.secondaryContainer
            5    -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val fg = when (i) {
            0    -> MaterialTheme.colorScheme.error
            4    -> MaterialTheme.colorScheme.onSecondaryContainer
            5    -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        TBtn(btn.label, Modifier.weight(1f).fillMaxHeight(), bg, fg, btn.action)
    }
}

/** Vertical scroll wheel strip */
@Composable
private fun ScrollWheel(vm: PcControlViewModel, onFeedback: (String) -> Unit) {
    Box(
        Modifier
            .width(20.dp).fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .scrollable(
                rememberScrollableState { d ->
                    vm.sendMouseScroll(if (d > 0) 3 else -3)
                    onFeedback(if (d > 0) "Scroll ↑" else "Scroll ↓")
                    d
                },
                Orientation.Vertical
            ),
        contentAlignment = Alignment.Center
    ) {
        Text("⋮", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

// ═════════════════════════════════════════════════════════════
//  LIVE TOGGLE BUTTON
// ═════════════════════════════════════════════════════════════
@Composable
fun LiveToggleButton(isOn: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptic     = LocalHapticFeedback.current
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue  = 0.4f, targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "pulseAlpha"
    )

    Surface(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggle(!isOn) },
        modifier       = modifier,
        shape          = RoundedCornerShape(8.dp),
        color          = if (isOn) Color(0xFFEF4444).copy(0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        border         = BorderStroke(
            if (isOn) 1.5.dp else 0.8.dp,
            if (isOn) Color(0xFFEF4444).copy(pulseAlpha) else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.15f)
        ),
        tonalElevation = 1.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (isOn) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444).copy(pulseAlpha)))
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text       = if (isOn) "LIVE\nOFF" else "🖥\nLIVE",
                    fontSize   = 9.sp, fontWeight = FontWeight.Bold,
                    color      = if (isOn) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign  = TextAlign.Center, lineHeight = 11.sp
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
//  LAPTOP TOUCHPAD — full gesture engine
// ═════════════════════════════════════════════════════════════
private enum class TouchState { IDLE, ONE_FINGER, TWO_FINGER, THREE_FINGER, DRAGGING }

@Composable
fun LaptopTouchpad(
    modifier        : Modifier,
    sensitivity     : Float,
    feedback        : String,
    onFeedback      : (String) -> Unit,
    vm              : PcControlViewModel,
    semiTransparent : Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val scope  = rememberCoroutineScope()

    var state      by remember { mutableStateOf(TouchState.IDLE) }
    var isActive   by remember { mutableStateOf(false) }
    var scrollBuf  by remember { mutableStateOf("") }

    var p1x by remember { mutableFloatStateOf(0f) }
    var p1y by remember { mutableFloatStateOf(0f) }
    var p2x by remember { mutableFloatStateOf(0f) }
    var p2y by remember { mutableFloatStateOf(0f) }
    var totalMoveX by remember { mutableFloatStateOf(0f) }
    var totalMoveY by remember { mutableFloatStateOf(0f) }

    var holdJob      by remember { mutableStateOf<Job?>(null) }
    var tapJob       by remember { mutableStateOf<Job?>(null) }
    var tapCount     by remember { mutableIntStateOf(0) }
    var scrollBufJob by remember { mutableStateOf<Job?>(null) }
    var scrollAccY   by remember { mutableFloatStateOf(0f) }
    var scrollAccX   by remember { mutableFloatStateOf(0f) }

    val showScroll: (String) -> Unit = { dir ->
        scrollBuf = dir
        scrollBufJob?.cancel()
        scrollBufJob = scope.launch { delay(600); scrollBuf = "" }
    }

    fun resetAll() {
        state = TouchState.IDLE; isActive = false
        totalMoveX = 0f; totalMoveY = 0f
        scrollAccX = 0f; scrollAccY = 0f
        holdJob?.cancel(); holdJob = null
    }

    val surfaceAlpha = if (semiTransparent) 0.55f else 1f

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible  = scrollBuf.isNotEmpty(),
            enter    = fadeIn(tween(80)), exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(scrollBuf, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        if (state == TouchState.DRAGGING) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                shape    = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary
            ) {
                Text("DRAG", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        state == TouchState.DRAGGING -> MaterialTheme.colorScheme.primary.copy(0.12f * surfaceAlpha)
                        isActive                     -> MaterialTheme.colorScheme.primaryContainer.copy(0.25f * surfaceAlpha)
                        else                         -> MaterialTheme.colorScheme.surfaceVariant.copy(surfaceAlpha)
                    }
                )
                .border(
                    width = if (isActive || state == TouchState.DRAGGING) 1.5.dp else 1.dp,
                    color = when {
                        state == TouchState.DRAGGING -> MaterialTheme.colorScheme.primary
                        isActive                     -> MaterialTheme.colorScheme.primary.copy(0.6f)
                        else                         -> MaterialTheme.colorScheme.outline.copy(0.3f)
                    },
                    shape = RoundedCornerShape(14.dp)
                )
                .pointerInput(sensitivity) {
                    awaitPointerEventScope {
                        while (true) {
                            val event   = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            val count   = pressed.size
                            val first   = pressed.firstOrNull()
                                ?: event.changes.firstOrNull() ?: continue

                            when (event.type) {
                                PointerEventType.Press -> {
                                    isActive = true; totalMoveX = 0f; totalMoveY = 0f
                                    when (count) {
                                        1 -> {
                                            p1x = first.position.x; p1y = first.position.y
                                            state = TouchState.ONE_FINGER
                                            holdJob?.cancel()
                                            holdJob = scope.launch {
                                                delay(HOLD_MS)
                                                val moved = abs(totalMoveX) > DRAG_MIN_PX || abs(totalMoveY) > DRAG_MIN_PX
                                                if (!moved && state == TouchState.ONE_FINGER) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    vm.mouseButtonDown("left")
                                                    state = TouchState.DRAGGING
                                                    onFeedback("Drag mode")
                                                }
                                            }
                                        }
                                        2 -> {
                                            holdJob?.cancel(); state = TouchState.TWO_FINGER
                                            p1x = pressed[0].position.x; p1y = pressed[0].position.y
                                            p2x = pressed[1].position.x; p2y = pressed[1].position.y
                                            scrollAccX = 0f; scrollAccY = 0f
                                        }
                                        else -> { holdJob?.cancel(); state = TouchState.THREE_FINGER }
                                    }
                                }

                                PointerEventType.Move -> {
                                    if (count == 0) continue
                                    when (state) {
                                        TouchState.ONE_FINGER, TouchState.DRAGGING -> {
                                            val dx = (first.position.x - p1x) * sensitivity * 0.5f
                                            val dy = (first.position.y - p1y) * sensitivity * 0.5f
                                            totalMoveX += abs(first.position.x - p1x)
                                            totalMoveY += abs(first.position.y - p1y)
                                            val moved = totalMoveX > DRAG_MIN_PX || totalMoveY > DRAG_MIN_PX
                                            if (moved && state == TouchState.ONE_FINGER) holdJob?.cancel()
                                            if ((abs(dx) > 0.3f || abs(dy) > 0.3f) && moved) vm.sendMouseDelta(dx, dy)
                                            p1x = first.position.x; p1y = first.position.y
                                        }
                                        TouchState.TWO_FINGER -> {
                                            if (pressed.size < 2) { state = TouchState.ONE_FINGER } else {
                                                val np1y = pressed[0].position.y; val np2y = pressed[1].position.y
                                                val np1x = pressed[0].position.x; val np2x = pressed[1].position.x
                                                val avgDy = ((np1y - p1y) + (np2y - p2y)) / 2f
                                                val avgDx = ((np1x - p1x) + (np2x - p2x)) / 2f
                                                scrollAccY += avgDy; scrollAccX += avgDx
                                                if (abs(scrollAccY) > SCROLL_MIN_PX) {
                                                    val ticks = (scrollAccY / SCROLL_MIN_PX).toInt()
                                                    vm.sendMouseScroll(-ticks)
                                                    showScroll(if (ticks < 0) "↓" else "↑")
                                                    onFeedback(if (ticks < 0) "Scroll ↓" else "Scroll ↑")
                                                    scrollAccY -= ticks * SCROLL_MIN_PX
                                                }
                                                if (abs(scrollAccX) > SCROLL_MIN_PX * 2 && abs(scrollAccX) > abs(scrollAccY)) {
                                                    val hTicks = (scrollAccX / (SCROLL_MIN_PX * 2)).toInt()
                                                    if (hTicks != 0) {
                                                        vm.sendMouseScroll(hTicks, horizontal = true)
                                                        scrollAccX -= hTicks * SCROLL_MIN_PX * 2
                                                    }
                                                }
                                                p1x = np1x; p1y = np1y; p2x = np2x; p2y = np2y
                                            }
                                        }
                                        else -> {}
                                    }
                                    pressed.forEach { it.consume() }
                                }

                                PointerEventType.Release -> {
                                    val movedFar = abs(totalMoveX) > TAP_MAX_PX || abs(totalMoveY) > TAP_MAX_PX
                                    when (state) {
                                        TouchState.DRAGGING -> {
                                            vm.mouseButtonUp("left"); onFeedback("Drop")
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            resetAll()
                                        }
                                        TouchState.ONE_FINGER -> {
                                            holdJob?.cancel()
                                            if (!movedFar) {
                                                tapCount++; tapJob?.cancel()
                                                tapJob = scope.launch {
                                                    delay(DTAP_MS)
                                                    if (tapCount >= 2) {
                                                        vm.sendMouseClick("left", double = true)
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onFeedback("Double click")
                                                    } else {
                                                        vm.sendMouseClick("left"); onFeedback("Click")
                                                    }
                                                    tapCount = 0
                                                }
                                            }
                                            resetAll()
                                        }
                                        TouchState.TWO_FINGER -> {
                                            if (!movedFar) {
                                                vm.sendMouseClick("right")
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onFeedback("Right click")
                                            }
                                            resetAll()
                                        }
                                        TouchState.THREE_FINGER -> {
                                            if (!movedFar) {
                                                vm.sendMouseClick("middle")
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onFeedback("Middle click")
                                            }
                                            resetAll()
                                        }
                                        else -> resetAll()
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (!isActive && state == TouchState.IDLE) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Touchpad", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "1-finger: move/tap  •  2-finger: scroll/right-click\ndouble-tap: dbl-click  •  hold: drag",
                        fontSize = 9.sp, textAlign = TextAlign.Center,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    if (feedback.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(feedback, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  TBtn  — universal button
// ─────────────────────────────────────────────────────────────
@Composable
fun TBtn(label: String, modifier: Modifier, bg: Color, fg: Color, onClick: () -> Unit) {
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { pressed = true; delay(80); pressed = false }
            onClick()
        },
        modifier       = modifier,
        shape          = RoundedCornerShape(8.dp),
        color          = if (pressed) fg.copy(0.22f) else bg,
        border         = BorderStroke(if (pressed) 1.5.dp else 0.8.dp, if (pressed) fg else fg.copy(0.15f)),
        tonalElevation = 1.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = when {
                    label.length > 8 -> 7.5.sp
                    label.length > 5 -> 9.sp
                    label.length > 3 -> 10.sp
                    else             -> 12.sp
                },
                fontWeight = FontWeight.Bold, color = fg,
                textAlign  = TextAlign.Center, lineHeight = 12.sp,
                modifier   = Modifier.padding(horizontal = 1.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────

/** Extension to get display label without a when-chain everywhere */
private val PcConnectionStatus.label: String get() = when (this) {
    PcConnectionStatus.ONLINE   -> "Online"
    PcConnectionStatus.OFFLINE  -> "Offline"
    PcConnectionStatus.CHECKING -> "Checking"
    PcConnectionStatus.UNKNOWN  -> "Ping"
}

@Composable
private fun PcConnectionStatus.toColor(): Color = when (this) {
    PcConnectionStatus.ONLINE   -> Color(0xFF22C55E)
    PcConnectionStatus.OFFLINE  -> MaterialTheme.colorScheme.error
    PcConnectionStatus.CHECKING -> Color(0xFFF59E0B)
    PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
}