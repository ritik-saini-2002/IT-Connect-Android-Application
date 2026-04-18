package com.example.ritik_2.windowscontrol.pctouchpad

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
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
import kotlin.math.roundToInt
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

// ── Gesture thresholds ─────────────────────────────────────
private const val TAP_MAX_PX    = 10f
private const val HOLD_MS       = 550L
private const val DTAP_MS       = 230L
private const val SCROLL_MIN_PX = 4f
private const val DRAG_MIN_PX   = 6f

// ── Theme-aware glass colors ───────────────────────────────
data class GlassColors(
    val bg: Color, val bgGradientEnd: Color,
    val glassBg: Color, val glassBorder: Color,
    val surface: Color, val accent: Color, val accentSecondary: Color, val danger: Color,
    val textPrimary: Color, val textSecondary: Color, val textTertiary: Color,
    val buttonBg: Color, val buttonBorder: Color,
    val touchpadBg: Color
)

@Composable
private fun glassColors(): GlassColors {
    val cs = MaterialTheme.colorScheme
    return GlassColors(
        bg = cs.surface, bgGradientEnd = cs.surface,
        glassBg = cs.surfaceVariant,
        glassBorder = cs.outline.copy(alpha = 0.25f),
        surface = cs.surface, accent = cs.primary, accentSecondary = cs.secondary,
        danger = cs.error, textPrimary = cs.onSurface, textSecondary = cs.onSurfaceVariant,
        textTertiary = cs.onSurfaceVariant.copy(0.5f),
        buttonBg = cs.surfaceVariant,
        buttonBorder = cs.outline.copy(alpha = 0.25f),
        touchpadBg = cs.surfaceVariant
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  SHORTCUT MODIFIER DATA — for landscape left panel
// ─────────────────────────────────────────────────────────────────────────────
private data class ShortcutItem(val combo: String, val label: String)
private data class ModifierGroup(val key: String, val display: String, val shortcuts: List<ShortcutItem>)

private val SHORTCUT_GROUPS = listOf(
    ModifierGroup("Win", "⊞ Win", listOf(
        ShortcutItem("WIN+L","Lock"), ShortcutItem("WIN+D","Desktop"), ShortcutItem("WIN+E","Explorer"),
        ShortcutItem("WIN+I","Settings"), ShortcutItem("WIN+R","Run"), ShortcutItem("WIN+S","Search"),
        ShortcutItem("WIN+TAB","Tasks"), ShortcutItem("WIN+P","Project"), ShortcutItem("WIN+X","Quick"),
        ShortcutItem("WIN+V","Clipboard"), ShortcutItem("WIN+M","Min All"), ShortcutItem("WIN+UP","Max"),
        ShortcutItem("WIN+DOWN","Min"), ShortcutItem("WIN+LEFT","Left"), ShortcutItem("WIN+RIGHT","Right"),
        ShortcutItem("WIN+.","Emoji"), ShortcutItem("WIN+SHIFT+S","Snip"), ShortcutItem("WIN+G","Game"),
    )),
    ModifierGroup("Ctrl", "Ctrl", listOf(
        ShortcutItem("CTRL+C","Copy"), ShortcutItem("CTRL+V","Paste"), ShortcutItem("CTRL+X","Cut"),
        ShortcutItem("CTRL+Z","Undo"), ShortcutItem("CTRL+Y","Redo"), ShortcutItem("CTRL+A","Sel All"),
        ShortcutItem("CTRL+S","Save"), ShortcutItem("CTRL+F","Find"), ShortcutItem("CTRL+N","New"),
        ShortcutItem("CTRL+O","Open"), ShortcutItem("CTRL+P","Print"), ShortcutItem("CTRL+W","Close Tab"),
        ShortcutItem("CTRL+T","New Tab"), ShortcutItem("CTRL+R","Refresh"), ShortcutItem("CTRL+L","Address"),
    )),
    ModifierGroup("Alt", "Alt", listOf(
        ShortcutItem("ALT+TAB","Switch"), ShortcutItem("ALT+F4","Close"), ShortcutItem("ALT+ENTER","Props"),
        ShortcutItem("ALT+SPACE","Menu"), ShortcutItem("ALT+LEFT","Back"), ShortcutItem("ALT+RIGHT","Fwd"),
        ShortcutItem("ALT+UP","Parent"), ShortcutItem("ALT+D","Address"),
    )),
    ModifierGroup("Shift", "⇧", listOf(
        ShortcutItem("SHIFT+DELETE","Perm Del"), ShortcutItem("SHIFT+TAB","Prev"),
        ShortcutItem("SHIFT+F10","Menu"), ShortcutItem("SHIFT+HOME","Sel←"),
        ShortcutItem("SHIFT+END","Sel→"), ShortcutItem("SHIFT+INSERT","Paste"),
    )),
    ModifierGroup("Ctrl+Shift", "C+⇧", listOf(
        ShortcutItem("CTRL+SHIFT+ESC","TaskMgr"), ShortcutItem("CTRL+SHIFT+N","New Dir"),
        ShortcutItem("CTRL+SHIFT+T","Reopen"), ShortcutItem("CTRL+SHIFT+V","Plain"),
        ShortcutItem("CTRL+SHIFT+DELETE","Clear"), ShortcutItem("CTRL+SHIFT+S","SaveAs"),
    )),
)

// ─────────────────────────────────────────────────────────────────────────────
//  ROOT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PcControlTouchpadUI(viewModel: PcControlViewModel) {
    val cfg         = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()

    var sensitivity     by remember { mutableFloatStateOf(2f) }
    var feedback        by remember { mutableStateOf("") }
    var liveScreenOn    by remember { mutableStateOf(false) }
    var zoomLocked      by remember { mutableStateOf(true) }
    var touchpadLocked  by remember { mutableStateOf(false) }
    val scope        = rememberCoroutineScope()
    var fbJob        by remember { mutableStateOf<Job?>(null) }
    var showUrlBar   by remember { mutableStateOf(false) }
    var urlText      by remember { mutableStateOf("") }
    var shortcutGroup by remember { mutableStateOf<ModifierGroup?>(null) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var showBrightnessSlider by remember { mutableStateOf(false) }
    val volumeLevel    = viewModel.volumeLevel.collectAsStateWithLifecycle(initialValue = -1)
    val brightnessLevel = viewModel.brightnessLevel.collectAsStateWithLifecycle(initialValue = -1)

    val showFeedback: (String) -> Unit = remember(scope) { { msg ->
        feedback = msg; fbJob?.cancel(); fbJob = scope.launch { delay(1_000); feedback = "" }
    }}
    val openUrl: (String) -> Unit = remember(scope) { { url ->
        val finalUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        viewModel.executeQuickStep(PcStep("SYSTEM_CMD", "OPEN_URL", args = listOf(finalUrl)))
        showFeedback("Opening: $url"); urlText = ""; showUrlBar = false
    }}

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = isLandscape,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "orientation"
        ) { landscape ->
            if (landscape) {
                LandscapeLayout(vm = viewModel, sensitivity = sensitivity, feedback = feedback,
                    connectionStatus = connectionStatus, liveScreenOn = liveScreenOn,
                    showUrlBar = showUrlBar, urlText = urlText,
                    zoomLocked = zoomLocked, touchpadLocked = touchpadLocked,
                    onLiveToggle = { liveScreenOn = it }, onFeedback = showFeedback,
                    onSensChange = { sensitivity = it }, onToggleUrlBar = { showUrlBar = !showUrlBar },
                    onUrlChange = { urlText = it }, onOpenUrl = openUrl,
                    onZoomLockToggle = { zoomLocked = !zoomLocked },
                    onTouchpadLockToggle = { touchpadLocked = !touchpadLocked },
                    shortcutGroup = shortcutGroup, onShortcutGroupChange = { shortcutGroup = it },
                    onShowVolumeSlider = { viewModel.fetchVolume(); showVolumeSlider = true },
                    onShowBrightnessSlider = { viewModel.fetchBrightness(); showBrightnessSlider = true })
            } else {
                PortraitLayout(vm = viewModel, sensitivity = sensitivity, feedback = feedback,
                    connectionStatus = connectionStatus, showUrlBar = showUrlBar, urlText = urlText,
                    zoomLocked = zoomLocked, onZoomLockToggle = { zoomLocked = !zoomLocked },
                    touchpadLocked = touchpadLocked, onTouchpadLockToggle = { touchpadLocked = !touchpadLocked },
                    onFeedback = showFeedback, onSensChange = { sensitivity = it },
                    onToggleUrlBar = { showUrlBar = !showUrlBar },
                    onUrlChange = { urlText = it }, onOpenUrl = openUrl,
                    onShowVolumeSlider = { viewModel.fetchVolume(); showVolumeSlider = true },
                    onShowBrightnessSlider = { viewModel.fetchBrightness(); showBrightnessSlider = true })
            }
        }

        // ── Volume Slider Popup ──────────────────────────────────────────────
        if (showVolumeSlider) {
            SystemSliderPopup(
                title = "🔊 Volume",
                value = volumeLevel.value.coerceIn(0, 100),
                onValueChange = { viewModel.setVolume(it) },
                onDismiss = { showVolumeSlider = false }
            )
        }

        // ── Brightness Slider Popup ──────────────────────────────────────────
        if (showBrightnessSlider) {
            SystemSliderPopup(
                title = "🔆 Brightness",
                value = if (brightnessLevel.value < 0) 50 else brightnessLevel.value.coerceIn(0, 100),
                onValueChange = { viewModel.setBrightness(it) },
                onDismiss = { showBrightnessSlider = false },
                unavailable = brightnessLevel.value < 0
            )
        }

        // ── Full-screen activity lock overlay ────────────────────────────────
        // Placed last in Box so it sits on top of everything.
        // pointerInput on the background consumes all touches except the Unlock button.
        if (touchpadLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                    Text(
                        "Activity Locked",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "All controls are disabled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { touchpadLocked = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock Activity")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LIVE SCREEN — JPEG snapshot poller.
//
//  History: v1 tried WebView + <img src="/screen/stream"> (MJPEG). That
//  fails on Android WebView because the image loader cannot consume
//  `multipart/x-mixed-replace`; the first frame arrives, parsing bails with
//  `net::ERR_FAILED`, and the viewer just reconnects forever behind a black
//  background. See Logcat repro: `LiveScreen viewer err -1 net::ERR_FAILED
//  on /screen/stream`.
//
//  v2 (current): poll `/screen/capture` ~5 fps through OkHttp, decode each
//  JPEG with BitmapFactory, push into a Compose `Image`. Benefits:
//    • Works on every Android version — no WebView quirks.
//    • Reuses the existing authenticated OkHttp client (header + query key,
//      cert pinning, LAN-only interceptor) → both secret-key and master-key
//      devices stream identically.
//    • Downscaled JPEGs (`s=4`, `q=20`) keep per-frame payloads under ~40KB,
//      so 5 fps is ~200KB/s over LAN.
//    • Failures (network, 401, decode) just skip a frame and the UI holds
//      the previous bitmap instead of flashing to black.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun LiveScreenBackground(isOn: Boolean, modifier: Modifier = Modifier) {
    var frame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var errorTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isOn) {
        if (!isOn) {
            frame = null
            errorTag = null
            return@LaunchedEffect
        }
        val api = PcControlMain.apiClient
        if (api == null) {
            errorTag = "not-connected"
            android.util.Log.w("LiveScreen", "apiClient null — PcControlMain not initialized")
            return@LaunchedEffect
        }
        errorTag = null
        var consecutiveFails = 0
        while (true) {
            val jpeg = api.fetchScreenFrame(quality = 20, scale = 4)
            if (jpeg != null && jpeg.isNotEmpty()) {
                val bmp = try {
                    android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                } catch (e: Throwable) {
                    android.util.Log.w("LiveScreen", "decode failed: ${e.message}")
                    null
                }
                if (bmp != null) {
                    frame = bmp.asImageBitmap()
                    consecutiveFails = 0
                    errorTag = null
                }
            } else {
                consecutiveFails++
                if (consecutiveFails == 1 || consecutiveFails % 10 == 0) {
                    android.util.Log.w(
                        "LiveScreen",
                        "fetch failed (#$consecutiveFails) — check agent /screen/capture & key"
                    )
                }
                if (consecutiveFails >= 3) errorTag = "fetch-failed"
            }
            // ~5 fps. Background visual doesn't need faster; cheap on LAN.
            kotlinx.coroutines.delay(200)
        }
    }

    Box(modifier = modifier) {
        val bmp = frame
        if (isOn && bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.28f),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        // When enabled but no frame yet, a subtle shimmer tells the user the
        // stream is trying; completely silent failures are bad UX.
        if (isOn && bmp == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.08f))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  URL BAR — slim single-line
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun UrlBarRow(c: GlassColors, visible: Boolean, urlText: String, onUrlChange: (String) -> Unit, onOpenUrl: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(), modifier = modifier) {
        Surface(
            color = c.glassBg, shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, c.glassBorder),
            modifier = Modifier.fillMaxWidth().height(32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Language, null, tint = c.accent, modifier = Modifier.size(14.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = urlText,
                    onValueChange = onUrlChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = c.textPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (urlText.isNotBlank()) onOpenUrl(urlText) }),
                    modifier = Modifier.weight(1f),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accentSecondary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (urlText.isEmpty()) Text("URL or search…", fontSize = 11.sp, color = c.textTertiary)
                            inner()
                        }
                    }
                )
                Surface(
                    onClick = { if (urlText.isNotBlank()) onOpenUrl(urlText) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (urlText.isNotBlank()) c.accent.copy(0.15f) else Color.Transparent,
                    modifier = Modifier.height(22.dp)
                ) {
                    Text("Go", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (urlText.isNotBlank()) c.accent else c.textTertiary,
                        modifier = Modifier.padding(horizontal = 8.dp))
                }
                Box(Modifier.size(20.dp).clip(CircleShape).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, "Close", tint = c.textTertiary, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LANDSCAPE LAYOUT — scroll slider is INSIDE touchpad now
// ─────────────────────────────────────────────────────────────────────────────
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun LandscapeLayout(
    vm: PcControlViewModel, sensitivity: Float, feedback: String,
    connectionStatus: PcConnectionStatus, liveScreenOn: Boolean,
    showUrlBar: Boolean, urlText: String,
    zoomLocked: Boolean = false, touchpadLocked: Boolean = false,
    onLiveToggle: (Boolean) -> Unit, onFeedback: (String) -> Unit,
    onSensChange: (Float) -> Unit, onToggleUrlBar: () -> Unit,
    onUrlChange: (String) -> Unit, onOpenUrl: (String) -> Unit,
    onZoomLockToggle: () -> Unit = {}, onTouchpadLockToggle: () -> Unit = {},
    shortcutGroup: ModifierGroup? = null, onShortcutGroupChange: (ModifierGroup?) -> Unit = {},
    onShowVolumeSlider: () -> Unit = {}, onShowBrightnessSlider: () -> Unit = {},
) {
    val c = glassColors()
    val dotColor = connectionStatus.toGlassColor(c)
    val btnAlpha = if (liveScreenOn) 0.6f else 1f
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(c.surface)) {
        val sideWL = maxWidth * 0.195f
        val sideWR = maxWidth * 0.10f
        val gap = 4.dp

        LiveScreenBackground(isOn = liveScreenOn, modifier = Modifier.fillMaxSize())

        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {

            // ── FAR LEFT: Modifier keys column (ALWAYS visible in landscape) ──
            ShortcutModifierColumn(c = c, selected = shortcutGroup, onSelect = { onShortcutGroupChange(it) }, modifier = Modifier.width(52.dp).fillMaxHeight())

            if (shortcutGroup != null) {
                // ── SHORTCUTS MODE: grid replaces the entire touchpad centre ──
                ShortcutsGrid(c = c, group = shortcutGroup, vm = vm, onFeedback = onFeedback, modifier = Modifier.weight(1f).fillMaxHeight())
            } else {
                // ── LEFT PANEL ──
                Column(modifier = Modifier.width(sideWL).fillMaxHeight().alpha(btnAlpha), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        GlassButton(c, "L", Modifier.weight(1f).fillMaxHeight(), c.accent) { vm.sendMouseClick("left"); onFeedback("L Click") }
                        GlassButton(c, "R", Modifier.weight(1f).fillMaxHeight()) { vm.sendMouseClick("right"); onFeedback("R Click") }
                    }
                    Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        GlassButton(c, "↑", Modifier.weight(1f).fillMaxHeight()) { vm.sendMouseScroll(3); onFeedback("Scroll ↑") }
                        GlassButton(c, "↓", Modifier.weight(1f).fillMaxHeight()) { vm.sendMouseScroll(-3); onFeedback("Scroll ↓") }
                    }
                    Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        GlassButton(c, if (zoomLocked) "🔒 Zoom" else "🔓 Zoom", Modifier.weight(1f).fillMaxHeight(),
                            tintColor = if (zoomLocked) c.danger else null) {
                            onZoomLockToggle(); onFeedback(if (zoomLocked) "Zoom unlocked" else "Zoom locked")
                        }
                        GlassButton(c, if (touchpadLocked) "🔒 Pad" else "🔓 Pad", Modifier.weight(1f).fillMaxHeight(),
                            tintColor = if (touchpadLocked) c.danger else null) {
                            onTouchpadLockToggle(); onFeedback(if (touchpadLocked) "Pad unlocked" else "Pad locked")
                        }
                    }
                    UrlBarRow(c = c, visible = showUrlBar, urlText = urlText, onUrlChange = onUrlChange, onOpenUrl = onOpenUrl, onDismiss = onToggleUrlBar, modifier = Modifier.fillMaxWidth())
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
                            val arrowMod = Modifier.width(sideWL * 0.44f).height(36.dp)
                            GlassButton(c, "▲", arrowMod) { vm.sendKey("UP") }
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                GlassButton(c, "◀", arrowMod) { vm.sendKey("LEFT") }
                                Spacer(Modifier.width(sideWL * 0.06f))
                                GlassButton(c, "▶", arrowMod) { vm.sendKey("RIGHT") }
                            }
                            GlassButton(c, "▼", arrowMod) { vm.sendKey("DOWN") }
                            Spacer(Modifier.height(2.dp))
                            GlassButton(c, if (showUrlBar) "× URL" else "🌐 URL", Modifier.fillMaxWidth().height(28.dp), if (showUrlBar) c.accent else null, onClick = onToggleUrlBar)
                            LiveToggleButton(c = c, isOn = liveScreenOn, onToggle = onLiveToggle, modifier = Modifier.fillMaxWidth().height(28.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        GlassButton(c, "Esc", Modifier.weight(1f).fillMaxHeight(), c.danger) { vm.sendKey("ESC") }
                        GlassButton(c, "Tab", Modifier.weight(1f).fillMaxHeight()) { vm.sendKey("TAB") }
                    }
                }

                // ── CENTRE PANEL — touchpad has built-in scroll slider ──
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    GlassStatusBar(c = c, connectionStatus = connectionStatus, dotColor = dotColor, liveScreenOn = liveScreenOn, feedback = feedback, sensitivity = sensitivity, onSensChange = onSensChange, modifier = Modifier.fillMaxWidth().height(26.dp).alpha(btnAlpha))
                    Row(Modifier.fillMaxWidth().height(32.dp).alpha(btnAlpha), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        GlassMediaButtons(c = c, vm = vm, onFeedback = onFeedback, onShowVolumeSlider = onShowVolumeSlider, onShowBrightnessSlider = onShowBrightnessSlider)
                    }
                    // Touchpad with integrated scroll slider on right
                    LaptopTouchpad(c = c, modifier = Modifier.fillMaxWidth().weight(1f), sensitivity = sensitivity, feedback = feedback, onFeedback = onFeedback, vm = vm, semiTransparent = liveScreenOn, showScrollSlider = true, zoomLocked = zoomLocked, touchpadLocked = touchpadLocked)
                    Row(Modifier.fillMaxWidth().height(42.dp).alpha(btnAlpha), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        GlassButton(c, "Space", Modifier.weight(2f).fillMaxHeight(), c.accent) { vm.sendKey("SPACE"); onFeedback("Space") }
                        GlassButton(c, "Enter", Modifier.weight(1.5f).fillMaxHeight(), c.accent) { vm.sendKey("ENTER"); onFeedback("Enter") }
                        GlassButton(c, "Backspace", Modifier.weight(1.2f).fillMaxHeight()) { vm.sendKey("BACKSPACE"); onFeedback("Backspace") }
                    }
                }

                // ── RIGHT PANEL ──
                Column(modifier = Modifier.width(sideWR).fillMaxHeight().alpha(btnAlpha), verticalArrangement = Arrangement.spacedBy(gap)) {
                    GlassButton(c, "Alt+Tab", Modifier.fillMaxWidth().height(26.dp), c.accentSecondary) { vm.sendKey("ALT+TAB"); onFeedback("Alt+Tab") }
                    Row(Modifier.fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        GlassButton(c, "Copy", Modifier.weight(1f).fillMaxHeight()) { vm.sendKey("CTRL+C"); onFeedback("Copy") }
                        GlassButton(c, "Paste", Modifier.weight(1f).fillMaxHeight()) { vm.sendKey("CTRL+V"); onFeedback("Paste") }
                    }
                    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                        GlassButton(c, "Win+D", Modifier.fillMaxWidth().weight(1f), c.accent) { vm.sendKey("WIN+D"); onFeedback("Win+D") }
                        GlassButton(c, "Undo", Modifier.fillMaxWidth().weight(1f)) { vm.sendKey("CTRL+Z"); onFeedback("Undo") }
                        GlassButton(c, "⌨", Modifier.fillMaxWidth().weight(1f), if (shortcutGroup != null) c.accent else null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onShortcutGroupChange(if (shortcutGroup == null) SHORTCUT_GROUPS.first() else null)
                        }
                        GlassButton(c, "SS", Modifier.fillMaxWidth().weight(1f)) { vm.executeQuickStep(PcStep("SYSTEM_CMD", "SCREENSHOT")); onFeedback("Screenshot") }
                    }
                    GlassButton(c, "Lock", Modifier.fillMaxWidth().height(42.dp), c.danger) { vm.executeQuickStep(PcStep("SYSTEM_CMD", "LOCK")) }
                }
            } // end else (no shortcut panel)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PORTRAIT LAYOUT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PortraitLayout(
    vm: PcControlViewModel, sensitivity: Float, feedback: String,
    connectionStatus: PcConnectionStatus, showUrlBar: Boolean, urlText: String,
    zoomLocked: Boolean = false, onZoomLockToggle: () -> Unit = {},
    touchpadLocked: Boolean = false, onTouchpadLockToggle: () -> Unit = {},
    onFeedback: (String) -> Unit, onSensChange: (Float) -> Unit,
    onToggleUrlBar: () -> Unit, onUrlChange: (String) -> Unit, onOpenUrl: (String) -> Unit,
    onShowVolumeSlider: () -> Unit = {}, onShowBrightnessSlider: () -> Unit = {},
) {
    val c = glassColors()
    val dotColor = connectionStatus.toGlassColor(c)
    val gap = 4.dp

    Box(modifier = Modifier.fillMaxSize().background(c.surface)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            Surface(color = c.glassBg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, c.glassBorder), modifier = Modifier.fillMaxWidth().height(30.dp)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(onClick = { vm.pingPc() }, shape = RoundedCornerShape(20.dp), color = dotColor.copy(0.15f)) {
                        Row(Modifier.padding(horizontal = 7.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                            Text(connectionStatus.label, fontSize = 9.sp, color = dotColor, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Spd", fontSize = 10.sp, color = c.textTertiary)
                    Slider(value = sensitivity, onValueChange = onSensChange, valueRange = 1f..12f, steps = 10, modifier = Modifier.width(140.dp).height(20.dp), colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent))
                    Text("${sensitivity.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.accent, modifier = Modifier.width(14.dp))
                    TextButton(onClick = { vm.navigateTo(PcScreen.KEYBOARD) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Keys", fontSize = 10.sp, color = c.accent) }
                }
            }
            UrlBarRow(c = c, visible = showUrlBar, urlText = urlText, onUrlChange = onUrlChange, onOpenUrl = onOpenUrl, onDismiss = onToggleUrlBar, modifier = Modifier.fillMaxWidth())
            LaptopTouchpad(c = c, modifier = Modifier.fillMaxWidth().weight(1f), sensitivity = sensitivity, feedback = feedback, onFeedback = onFeedback, vm = vm, semiTransparent = false, showScrollSlider = false, zoomLocked = zoomLocked, touchpadLocked = touchpadLocked)
            Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                GlassButton(c, "L", Modifier.weight(1f).fillMaxHeight(), c.accent) { vm.sendMouseClick("left"); onFeedback("L Click") }
                GlassScrollWheel(c = c, vm = vm, onFeedback = onFeedback)
                GlassButton(c, "R", Modifier.weight(1f).fillMaxHeight()) { vm.sendMouseClick("right"); onFeedback("R Click") }
                GlassButton(c, if (zoomLocked) "🔒Z" else "Z", Modifier.width(42.dp).fillMaxHeight(),
                    tintColor = if (zoomLocked) c.danger else null) {
                    onZoomLockToggle(); onFeedback(if (zoomLocked) "Zoom unlocked" else "Zoom locked")
                }
                GlassButton(c, if (touchpadLocked) "🔒P" else "P", Modifier.width(42.dp).fillMaxHeight(),
                    tintColor = if (touchpadLocked) c.danger else null) {
                    onTouchpadLockToggle(); onFeedback(if (touchpadLocked) "Pad unlocked" else "Pad locked")
                }
            }
            Row(Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                GlassButton(c, "🌐", Modifier.width(46.dp).fillMaxHeight(), if (showUrlBar) c.accent else null, onClick = onToggleUrlBar)
                GlassButton(c, "Alt+F4", Modifier.weight(1.2f).fillMaxHeight(), c.danger) { vm.sendKey("ALT+F4"); onFeedback("Alt+F4") }
                GlassButton(c, "Enter", Modifier.weight(1f).fillMaxHeight(), c.accent) { vm.sendKey("ENTER"); onFeedback("Enter") }
                GlassButton(c, "Volume", Modifier.weight(0.85f).fillMaxHeight()) { onShowVolumeSlider() }
                GlassButton(c, "Brightness", Modifier.weight(0.85f).fillMaxHeight()) { onShowBrightnessSlider() }
                GlassButton(c, "Mute", Modifier.weight(0.85f).fillMaxHeight()) { vm.executeQuickStep(PcStep("SYSTEM_CMD", "MUTE")); onFeedback("Mute") }
            }
            Row(Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                GlassButton(c, "F11", Modifier.weight(0.9f).fillMaxHeight(), c.accentSecondary) { vm.sendKey("F11"); onFeedback("F11") }
                GlassButton(c, "Esc", Modifier.weight(0.8f).fillMaxHeight()) { vm.sendKey("ESC"); onFeedback("Esc") }
                GlassButton(c, "Space", Modifier.weight(1.4f).fillMaxHeight(), c.accent) { vm.sendKey("SPACE"); onFeedback("Space") }
                GlassButton(c, "Alt+Tab", Modifier.weight(1.2f).fillMaxHeight()) { vm.sendKey("ALT+TAB"); onFeedback("Alt+Tab") }
                GlassButton(c, "Win+D", Modifier.weight(1.1f).fillMaxHeight()) { vm.sendKey("WIN+D"); onFeedback("Win+D") }
            }
            Row(Modifier.fillMaxWidth().height(42.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(12) { i ->
                    val fk = "F${i + 1}"; val tint = when (fk) { "F5" -> c.accentSecondary; "F11" -> c.accent; else -> null }
                    GlassButton(c, fk, Modifier.width(46.dp).fillMaxHeight(), tint) { vm.sendKey(fk); onFeedback(fk) }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GLASS STATUS BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GlassStatusBar(c: GlassColors, connectionStatus: PcConnectionStatus, dotColor: Color, liveScreenOn: Boolean, feedback: String, sensitivity: Float, onSensChange: (Float) -> Unit, modifier: Modifier) {
    Surface(color = c.glassBg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, c.glassBorder), modifier = modifier) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
            Text(connectionStatus.label, fontSize = 9.sp, color = dotColor, fontWeight = FontWeight.Bold)
            if (liveScreenOn) Text("● LIVE", fontSize = 9.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            if (feedback.isNotEmpty()) Text("· $feedback", fontSize = 9.sp, color = c.accent, modifier = Modifier.weight(1f), maxLines = 1) else Spacer(Modifier.weight(1f))
            Text("Spd", fontSize = 8.sp, color = c.textTertiary)
            Slider(value = sensitivity, onValueChange = onSensChange, valueRange = 1f..12f, steps = 10, modifier = Modifier.width(140.dp).height(18.dp), colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent))
            Text("${sensitivity.toInt()}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = c.accent, modifier = Modifier.width(12.dp))
        }
    }
}

@Composable
private fun RowScope.GlassMediaButtons(c: GlassColors, vm: PcControlViewModel, onFeedback: (String) -> Unit, onShowVolumeSlider: () -> Unit = {}, onShowBrightnessSlider: () -> Unit = {}) {
    data class Btn(val label: String, val tint: Color?, val action: () -> Unit)
    listOf(
        Btn("Alt+F4", c.danger) { vm.sendKey("ALT+F4"); onFeedback("Alt+F4") },
        Btn("Volume", null) { onShowVolumeSlider() },
        Btn("Mute", null) { vm.executeQuickStep(PcStep("SYSTEM_CMD", "MUTE")); onFeedback("Mute") },
        Btn("Brightness", null) { onShowBrightnessSlider() },
        Btn("F5", c.accentSecondary) { vm.sendKey("F5"); onFeedback("F5") },
        Btn("F11", c.accent) { vm.sendKey("F11"); onFeedback("F11") },
    ).forEach { btn -> GlassButton(c, btn.label, Modifier.weight(1f).fillMaxHeight(), btn.tint, btn.action) }
}

@Composable
private fun GlassScrollWheel(c: GlassColors, vm: PcControlViewModel, onFeedback: (String) -> Unit) {
    Box(Modifier.width(20.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(c.glassBg).border(1.dp, c.glassBorder, RoundedCornerShape(6.dp))
        .scrollable(rememberScrollableState { d -> vm.sendMouseScroll(if (d > 0) 3 else -3); onFeedback(if (d > 0) "Scroll ↑" else "Scroll ↓"); d }, Orientation.Vertical),
        contentAlignment = Alignment.Center) { Text("⋮", color = c.textSecondary, fontSize = 12.sp) }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LIVE TOGGLE (landscape only)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun LiveToggleButton(c: GlassColors, isOn: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val pulseAlpha by rememberInfiniteTransition(label = "p").animateFloat(0.4f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), "pa")
    Surface(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggle(!isOn) }, modifier = modifier, shape = RoundedCornerShape(10.dp),
        color = if (isOn) Color(0xFFEF4444).copy(0.18f) else c.glassBg,
        border = BorderStroke(if (isOn) 1.5.dp else 1.dp, if (isOn) Color(0xFFEF4444).copy(pulseAlpha) else c.glassBorder)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (isOn) { Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEF4444).copy(pulseAlpha))); Spacer(Modifier.height(1.dp)) }
                Text(if (isOn) "LIVE\nOFF" else "🖥\nLIVE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (isOn) Color(0xFFEF4444) else c.textSecondary, textAlign = TextAlign.Center, lineHeight = 10.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LAPTOP TOUCHPAD — with optional scroll slider (circle dot) on right edge
// ─────────────────────────────────────────────────────────────────────────────
private enum class TouchState { IDLE, ONE_FINGER, TWO_FINGER, THREE_FINGER, DRAGGING }

@Composable
fun LaptopTouchpad(
    c: GlassColors, modifier: Modifier, sensitivity: Float,
    feedback: String, onFeedback: (String) -> Unit,
    vm: PcControlViewModel, semiTransparent: Boolean = false,
    showScrollSlider: Boolean = false, zoomLocked: Boolean = false,
    touchpadLocked: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var state by remember { mutableStateOf(TouchState.IDLE) }
    var isActive by remember { mutableStateOf(false) }
    var scrollBuf by remember { mutableStateOf("") }

    var p1x by remember { mutableFloatStateOf(0f) }; var p1y by remember { mutableFloatStateOf(0f) }
    var p2x by remember { mutableFloatStateOf(0f) }; var p2y by remember { mutableFloatStateOf(0f) }
    var totalMoveX by remember { mutableFloatStateOf(0f) }; var totalMoveY by remember { mutableFloatStateOf(0f) }

    var holdJob by remember { mutableStateOf<Job?>(null) }
    var tapJob by remember { mutableStateOf<Job?>(null) }
    var tapCount by remember { mutableIntStateOf(0) }
    var scrollBufJob by remember { mutableStateOf<Job?>(null) }
    var scrollAccY by remember { mutableFloatStateOf(0f) }; var scrollAccX by remember { mutableFloatStateOf(0f) }
    var didScroll by remember { mutableStateOf(false) }
    var pinchStartDist by remember { mutableFloatStateOf(0f) }  // distance between fingers at pinch start
    var pinchAccum by remember { mutableFloatStateOf(0f) }      // accumulated pinch delta


    // Scroll slider state — spring-back to center on release
    var sliderHeightPx by remember { mutableIntStateOf(0) }
    val sliderDotY = remember { Animatable(0f) }
    var sliderCenterY by remember { mutableFloatStateOf(0f) }

    val showScroll: (String) -> Unit = { dir -> scrollBuf = dir; scrollBufJob?.cancel(); scrollBufJob = scope.launch { delay(600); scrollBuf = "" } }
    fun resetAll() { state = TouchState.IDLE; isActive = false; totalMoveX = 0f; totalMoveY = 0f; scrollAccX = 0f; scrollAccY = 0f; didScroll = false; pinchStartDist = 0f; pinchAccum = 0f; holdJob?.cancel(); holdJob = null }

    // Use rememberUpdatedState so the gesture handler always reads the latest lock values
    // without needing to restart (which caused the flicker on every toggle).
    val touchpadLockedState = rememberUpdatedState(touchpadLocked)
    val zoomLockedState     = rememberUpdatedState(zoomLocked)

    val surfaceAlpha = if (semiTransparent) 0.45f else 1f
    val sliderWidthDp = 26.dp
    val dotSizeDp = 20.dp

    Box(modifier = modifier) {
        // Scroll indicator overlay
        AnimatedVisibility(visible = scrollBuf.isNotEmpty(), enter = fadeIn(tween(80)), exit = fadeOut(tween(300)), modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = c.accent.copy(0.2f)) { Text(scrollBuf, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.accent) }
        }
        if (state == TouchState.DRAGGING) {
            Surface(modifier = Modifier.align(Alignment.TopEnd).padding(if (showScrollSlider) (sliderWidthDp + 10.dp) else 6.dp, 6.dp), shape = RoundedCornerShape(6.dp), color = c.accent) {
                Text("DRAG", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.fillMaxSize()) {
            // ── Main touchpad area ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = if (showScrollSlider) 0.dp else 14.dp, bottomEnd = if (showScrollSlider) 0.dp else 14.dp))
                    .background(
                        when {
                            state == TouchState.DRAGGING -> c.accent.copy(0.12f * surfaceAlpha)
                            isActive -> c.accent.copy(0.06f * surfaceAlpha)
                            else -> c.touchpadBg.copy(alpha = surfaceAlpha)
                        }
                    )
                    .border(
                        width = if (isActive || state == TouchState.DRAGGING) 1.5.dp else 1.dp,
                        color = when {
                            state == TouchState.DRAGGING -> c.accent
                            isActive -> c.accent.copy(0.5f)
                            else -> c.glassBorder
                        },
                        shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = if (showScrollSlider) 0.dp else 14.dp, bottomEnd = if (showScrollSlider) 0.dp else 14.dp)
                    )
                    .pointerInput(sensitivity) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                // When touchpad is locked, consume all events and do nothing
                                if (touchpadLockedState.value) { event.changes.forEach { it.consume() }; continue }
                                val pressed = event.changes.filter { it.pressed }
                                val count = pressed.size
                                val first = pressed.firstOrNull() ?: event.changes.firstOrNull() ?: continue

                                when (event.type) {
                                    PointerEventType.Press -> {
                                        isActive = true; totalMoveX = 0f; totalMoveY = 0f
                                        when (count) {
                                            1 -> {
                                                p1x = first.position.x; p1y = first.position.y; state = TouchState.ONE_FINGER
                                                holdJob?.cancel(); holdJob = scope.launch {
                                                    delay(HOLD_MS)
                                                    val moved = abs(totalMoveX) > DRAG_MIN_PX || abs(totalMoveY) > DRAG_MIN_PX
                                                    if (!moved && state == TouchState.ONE_FINGER) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        vm.mouseButtonDown("left"); state = TouchState.DRAGGING; onFeedback("Drag mode")
                                                    }
                                                }
                                            }
                                            2 -> { holdJob?.cancel(); state = TouchState.TWO_FINGER; p1x = pressed[0].position.x; p1y = pressed[0].position.y; p2x = pressed[1].position.x; p2y = pressed[1].position.y; scrollAccX = 0f; scrollAccY = 0f; pinchStartDist = kotlin.math.hypot((p2x - p1x).toDouble(), (p2y - p1y).toDouble()).toFloat(); pinchAccum = 0f }
                                            else -> { holdJob?.cancel(); state = TouchState.THREE_FINGER }
                                        }
                                    }
                                    PointerEventType.Move -> {
                                        if (count == 0) continue
                                        when (state) {
                                            TouchState.ONE_FINGER, TouchState.DRAGGING -> {
                                                val dx = (first.position.x - p1x) * sensitivity * 0.5f; val dy = (first.position.y - p1y) * sensitivity * 0.5f
                                                totalMoveX += abs(first.position.x - p1x); totalMoveY += abs(first.position.y - p1y)
                                                val moved = totalMoveX > DRAG_MIN_PX || totalMoveY > DRAG_MIN_PX
                                                if (moved && state == TouchState.ONE_FINGER) holdJob?.cancel()
                                                if ((abs(dx) > 0.3f || abs(dy) > 0.3f) && moved) vm.sendMouseDelta(dx, dy)
                                                p1x = first.position.x; p1y = first.position.y
                                            }
                                            TouchState.TWO_FINGER -> {
                                                if (pressed.size < 2) { state = TouchState.ONE_FINGER } else {
                                                    val np1y = pressed[0].position.y; val np2y = pressed[1].position.y; val np1x = pressed[0].position.x; val np2x = pressed[1].position.x
                                                    val avgDy = ((np1y - p1y) + (np2y - p2y)) / 2f; val avgDx = ((np1x - p1x) + (np2x - p2x)) / 2f

                                                    // ── PINCH DETECTION — zoom in/out ──
                                                    val curDist = kotlin.math.hypot((np2x - np1x).toDouble(), (np2y - np1y).toDouble()).toFloat()
                                                    val pinchDelta = curDist - pinchStartDist
                                                    val pinchThreshold = 30f  // px before triggering zoom

                                                    if (!zoomLockedState.value && abs(pinchDelta) > pinchThreshold && abs(pinchDelta) > abs(avgDy) * 2) {
                                                        // Pinch gesture detected — send CTRL+Plus or CTRL+Minus
                                                        pinchAccum += (curDist - kotlin.math.hypot((p2x - p1x).toDouble(), (p2y - p1y).toDouble()).toFloat())
                                                        val pinchStep = 25f
                                                        if (abs(pinchAccum) > pinchStep) {
                                                            if (pinchAccum > 0) {
                                                                vm.sendKey("CTRL+PLUS"); onFeedback("Zoom +")
                                                            } else {
                                                                vm.sendKey("CTRL+MINUS"); onFeedback("Zoom −")
                                                            }
                                                            showScroll(if (pinchAccum > 0) "+" else "−")
                                                            pinchAccum = 0f; didScroll = true
                                                        }
                                                    } else {
                                                        // Normal two-finger scroll
                                                        scrollAccY += avgDy; scrollAccX += avgDx
                                                        if (abs(scrollAccY) > SCROLL_MIN_PX) {
                                                            val ticks = (scrollAccY / SCROLL_MIN_PX).toInt(); vm.sendMouseScroll(-ticks)
                                                            showScroll(if (ticks < 0) "↓" else "↑"); onFeedback(if (ticks < 0) "Scroll ↓" else "Scroll ↑")
                                                            scrollAccY -= ticks * SCROLL_MIN_PX
                                                            didScroll = true
                                                        }
                                                        if (abs(scrollAccX) > SCROLL_MIN_PX * 2 && abs(scrollAccX) > abs(scrollAccY)) {
                                                            val hTicks = (scrollAccX / (SCROLL_MIN_PX * 2)).toInt()
                                                            if (hTicks != 0) { vm.sendMouseScroll(hTicks, horizontal = true); scrollAccX -= hTicks * SCROLL_MIN_PX * 2; didScroll = true }
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
                                            TouchState.DRAGGING -> { vm.mouseButtonUp("left"); onFeedback("Drop"); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); resetAll() }
                                            TouchState.ONE_FINGER -> {
                                                holdJob?.cancel()
                                                if (!movedFar) { tapCount++; tapJob?.cancel(); tapJob = scope.launch { delay(DTAP_MS); if (tapCount >= 2) { vm.sendMouseClick("left", double = true); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onFeedback("Double click") } else { vm.sendMouseClick("left"); onFeedback("Click") }; tapCount = 0 } }
                                                resetAll()
                                            }
                                            TouchState.TWO_FINGER -> { if (!movedFar && !didScroll) { vm.sendMouseClick("right"); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onFeedback("Right click") }; resetAll() }
                                            TouchState.THREE_FINGER -> { if (!movedFar) { vm.sendMouseClick("middle"); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onFeedback("Middle click") }; resetAll() }
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
                if (touchpadLocked) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Touch Lock", fontSize = 28.sp)
                        Text("Touchpad Locked", style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold, color = c.danger)
                        Text("Tap P / TouchPad to unlock",
                            fontSize = 9.sp, textAlign = TextAlign.Center, color = c.textTertiary,
                            modifier = Modifier.padding(horizontal = 20.dp))
                    }
                } else if (!isActive && state == TouchState.IDLE) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Touchpad", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                        Text("1-finger: move/tap  •  2-finger: scroll/right-click\ndouble-tap: dbl-click  •  hold: drag",
                            fontSize = 9.sp, textAlign = TextAlign.Center, color = c.textTertiary, modifier = Modifier.padding(horizontal = 20.dp))
                        if (feedback.isNotEmpty()) {
                            Surface(shape = RoundedCornerShape(6.dp), color = c.accent.copy(0.15f)) {
                                Text(feedback, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = c.accent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── Scroll slider — spring-back circle dot ──
            if (showScrollSlider) {
                val dotSizePx = with(density) { dotSizeDp.toPx() }

                Box(
                    modifier = Modifier
                        .width(sliderWidthDp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
                        .background(c.touchpadBg.copy(alpha = surfaceAlpha * 0.9f))
                        .border(1.dp, c.glassBorder, RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
                        .onSizeChanged { size ->
                            sliderHeightPx = size.height
                            val center = (size.height / 2f) - (dotSizePx / 2f)
                            sliderCenterY = center
                            // Initialize dot to center
                            scope.launch { sliderDotY.snapTo(center) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    // Spring back to center
                                    scope.launch {
                                        sliderDotY.animateTo(
                                            targetValue = sliderCenterY,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        sliderDotY.animateTo(
                                            sliderCenterY,
                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                        )
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val maxY = sliderHeightPx - dotSizePx
                                    val newY = (sliderDotY.value + dragAmount.y).coerceIn(0f, maxY)
                                    scope.launch { sliderDotY.snapTo(newY) }

                                    // Scroll speed proportional to distance from center
                                    val distFromCenter = newY - sliderCenterY
                                    val threshold = dotSizePx * 0.5f
                                    if (abs(distFromCenter) > threshold) {
                                        val speed = (abs(distFromCenter) / (sliderCenterY.coerceAtLeast(1f)) * 4).toInt().coerceIn(1, 6)
                                        val ticks = if (distFromCenter > 0) -speed else speed
                                        vm.sendMouseScroll(ticks)
                                        showScroll(if (ticks > 0) "↑" else "↓")
                                        onFeedback(if (ticks > 0) "Scroll ↑" else "Scroll ↓")
                                    }
                                }
                            )
                        }
                ) {
                    // Track line
                    Box(
                        Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .padding(vertical = 16.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(c.textTertiary.copy(0.3f))
                    )
                    // Center mark
                    Box(
                        Modifier
                            .width(10.dp)
                            .height(1.5.dp)
                            .align(Alignment.Center)
                            .background(c.textTertiary.copy(0.25f))
                    )
                    Text("▲", fontSize = 8.sp, color = c.textTertiary, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
                    Text("▼", fontSize = 8.sp, color = c.textTertiary, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
                    // Draggable circle dot — animated position
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(0, sliderDotY.value.roundToInt()) }
                            .size(dotSizeDp)
                            .align(Alignment.TopCenter)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(c.accent.copy(0.9f), c.accent.copy(0.5f))
                                )
                            )
                            .border(1.dp, c.accent.copy(0.7f), CircleShape)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SYSTEM SLIDER POPUP — reusable for volume and brightness
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SystemSliderPopup(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    unavailable: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(10.dp), color = cs.primaryContainer) {
                    Text("${sliderValue.toInt()}%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = cs.onPrimaryContainer)
                }
            }
        },
        text = {
            if (unavailable) {
                Text("Not available on this PC (desktop PCs don't support brightness control)",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { onValueChange(sliderValue.toInt()) },
                        valueRange = 0f..100f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = cs.primary,
                            activeTrackColor = cs.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Text("100", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                    // Quick preset buttons
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 25, 50, 75, 100).forEach { preset ->
                            Surface(
                                onClick = { sliderValue = preset.toFloat(); onValueChange(preset) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (sliderValue.toInt() == preset) cs.primary.copy(0.2f) else cs.surfaceVariant,
                                border = BorderStroke(1.dp, if (sliderValue.toInt() == preset) cs.primary.copy(0.5f) else cs.outline.copy(0.2f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("$preset", modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sliderValue.toInt() == preset) cs.primary else cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  SHORTCUT MODIFIER COLUMN — thin left strip (landscape only)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ShortcutModifierColumn(c: GlassColors, selected: ModifierGroup?, onSelect: (ModifierGroup?) -> Unit, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SHORTCUT_GROUPS.forEach { group ->
            val isSel = selected?.key == group.key
            Surface(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(if (isSel) null else group) },
                color = if (isSel) c.accent.copy(0.2f) else c.buttonBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(if (isSel) 1.5.dp else 1.dp, if (isSel) c.accent.copy(0.4f) else c.buttonBorder),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(group.display, fontSize = if (group.key.length > 5) 7.sp else 9.sp,
                            fontWeight = FontWeight.Bold, color = if (isSel) c.accent else c.textSecondary,
                            textAlign = TextAlign.Center, lineHeight = 10.sp)
                        if (isSel) { Spacer(Modifier.height(2.dp)); Box(Modifier.width(14.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(c.accent)) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SHORTCUTS GRID — all shortcuts for selected modifier (landscape only)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ShortcutsGrid(c: GlassColors, group: ModifierGroup, vm: PcControlViewModel, onFeedback: (String) -> Unit, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(color = c.accent.copy(0.1f), shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, c.accent.copy(0.25f)),
            modifier = Modifier.fillMaxWidth().height(32.dp)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(group.display, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.accent)
                Spacer(Modifier.width(8.dp))
                Text("Shortcuts • ${group.shortcuts.size}", fontSize = 10.sp, color = c.textSecondary)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
            items(group.shortcuts.chunked(4)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { sc ->
                        Surface(
                            onClick = { vm.sendKey(sc.combo); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onFeedback(sc.combo) },
                            color = c.buttonBg, shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, c.buttonBorder),
                            tonalElevation = 2.dp,
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Column(Modifier.fillMaxSize().padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(sc.combo, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = c.accent, textAlign = TextAlign.Center, lineHeight = 10.sp, maxLines = 2)
                                Text(sc.label, fontSize = 7.sp, color = c.textTertiary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GlassButton — hold to repeat (volume, scroll, arrows, etc.)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GlassButton(c: GlassColors, label: String, modifier: Modifier, tintColor: Color? = null, onClick: () -> Unit = {}) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    val cs = MaterialTheme.colorScheme

    // Map tint → Material3 container/content tokens (same pattern as keyboard KbBtn)
    val containerColor = when (tintColor) {
        c.accent          -> cs.primaryContainer
        c.accentSecondary -> cs.secondaryContainer
        c.danger          -> cs.errorContainer
        else              -> cs.surfaceVariant
    }
    val contentColor = when (tintColor) {
        c.accent          -> cs.onPrimaryContainer
        c.accentSecondary -> cs.onSecondaryContainer
        c.danger          -> cs.error
        else              -> cs.onSurfaceVariant
    }
    val borderColor = when (tintColor) {
        c.accent          -> cs.primary.copy(alpha = 0.35f)
        c.accentSecondary -> cs.secondary.copy(alpha = 0.35f)
        c.danger          -> cs.error.copy(alpha = 0.35f)
        else              -> cs.outline.copy(alpha = 0.25f)
    }

    Surface(
        modifier = modifier
            .pointerInput(onClick) {
                awaitPointerEventScope {
                    while (true) {
                        // Wait for press
                        awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()

                        // Start repeat after 400ms hold, then every 100ms
                        repeatJob = scope.launch {
                            delay(400)
                            while (true) {
                                onClick()
                                delay(100)
                            }
                        }

                        // Wait for release
                        waitForUpOrCancellation()
                        repeatJob?.cancel()
                        repeatJob = null
                        pressed = false
                    }
                }
            },
        shape = RoundedCornerShape(10.dp),
        color = if (pressed) containerColor.copy(alpha = 0.75f) else containerColor,
        border = BorderStroke(if (pressed) 1.5.dp else 1.dp, if (pressed) contentColor.copy(0.4f) else borderColor),
        tonalElevation = 2.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = when { label.length > 8 -> 7.5.sp; label.length > 5 -> 9.sp; label.length > 3 -> 10.sp; else -> 12.sp },
                fontWeight = FontWeight.Bold, color = contentColor, textAlign = TextAlign.Center, lineHeight = 12.sp, modifier = Modifier.padding(horizontal = 1.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────
private val PcConnectionStatus.label: String
    get() = when (this) {
        PcConnectionStatus.ONLINE -> "Online"; PcConnectionStatus.OFFLINE -> "Offline"
        PcConnectionStatus.CHECKING -> "Checking"; PcConnectionStatus.UNKNOWN -> "Ping"
    }

@Composable
private fun PcConnectionStatus.toGlassColor(c: GlassColors): Color = when (this) {
    PcConnectionStatus.ONLINE -> Color(0xFF22C55E)
    PcConnectionStatus.OFFLINE -> c.danger
    PcConnectionStatus.CHECKING -> Color(0xFFF59E0B)
    PcConnectionStatus.UNKNOWN -> c.textTertiary
}