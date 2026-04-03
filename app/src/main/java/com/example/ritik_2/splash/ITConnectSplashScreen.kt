package com.example.ritik_2.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*

// onSplashComplete is called when the animation finishes.
// The Activity decides where to navigate — the composable does NOT check login state.
@Composable
fun ITConnectSplashScreen(
    onSplashComplete: (Boolean) -> Unit = {}
) {
    var animationState by remember { mutableStateOf(SplashAnimationState.INITIAL) }
    val configuration  = LocalConfiguration.current
    val screenWidth    = configuration.screenWidthDp.dp
    val isDarkTheme    = isSystemInDarkTheme()

    val logoSize    = remember(screenWidth) { minOf(screenWidth * 0.3f, 300.dp) }
    val themeColors = remember(isDarkTheme) { SplashThemeColors.create(isDarkTheme) }

    LaunchedEffect(Unit) {
        listOf(
            300L  to SplashAnimationState.LOGO_APPEAR,
            800L  to SplashAnimationState.CONNECTIONS_ANIMATE,
            1200L to SplashAnimationState.NODES_APPEAR,
            1000L to SplashAnimationState.TEXT_APPEAR,
            1000L to SplashAnimationState.COMPLETE
        ).forEach { (duration, state) ->
            delay(duration)
            animationState = state
        }
        // Notify the Activity that animation is done.
        // The Boolean arg is unused here — routing is handled in SplashActivity.route().
        onSplashComplete(true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = themeColors.backgroundColors,
                    radius = screenWidth.value * 0.8f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        SplashContent(
            logoSize       = logoSize,
            animationState = animationState,
            themeColors    = themeColors,
            screenWidth    = screenWidth
        )
    }
}

@Composable
private fun SplashContent(
    logoSize       : Dp,
    animationState : SplashAnimationState,
    themeColors    : SplashThemeColors,
    screenWidth    : Dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ITConnectLogo(size = logoSize, animationState = animationState, themeColors = themeColors)

        Spacer(Modifier.height(32.dp))

        AnimatedVisibility(
            visible = animationState.ordinal >= SplashAnimationState.TEXT_APPEAR.ordinal,
            enter   = fadeIn(tween(800)) + slideInVertically(tween(800)) { it / 2 }
        ) {
            SplashText(themeColors = themeColors, screenWidth = screenWidth)
        }

        Spacer(Modifier.height(48.dp))

        AnimatedVisibility(
            visible = animationState.ordinal >= SplashAnimationState.TEXT_APPEAR.ordinal,
            enter   = fadeIn(tween(600, delayMillis = 400))
        ) {
            PulsingDots(themeColors = themeColors)
        }
    }
}

@Composable
private fun SplashText(themeColors: SplashThemeColors, screenWidth: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text          = "IT Connect",
            fontSize      = (screenWidth.value * 0.06f).sp,
            fontWeight    = FontWeight.Bold,
            color         = themeColors.primaryTextColor,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text       = "Network Solutions",
            fontSize   = (screenWidth.value * 0.035f).sp,
            color      = themeColors.secondaryTextColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ITConnectLogo(size: Dp, animationState: SplashAnimationState, themeColors: SplashThemeColors) {
    val density = LocalDensity.current
    val sizePx  = remember(size) { with(density) { size.toPx() } }

    val centralScale by animateFloatAsState(
        targetValue   = if (animationState.ordinal >= SplashAnimationState.LOGO_APPEAR.ordinal) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label         = "centralScale"
    )
    val connectionProgress by animateFloatAsState(
        targetValue   = if (animationState.ordinal >= SplashAnimationState.CONNECTIONS_ANIMATE.ordinal) 1f else 0f,
        animationSpec = tween(1000, easing = EaseInOutCubic),
        label         = "connectionProgress"
    )
    val nodesScale by animateFloatAsState(
        targetValue   = if (animationState.ordinal >= SplashAnimationState.NODES_APPEAR.ordinal) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "nodesScale"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "infiniteRotation")
    val infiniteRotation by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label         = "rotation"
    )

    Box(
        modifier = Modifier
            .size(size)
            .rotate(if (animationState.ordinal >= SplashAnimationState.COMPLETE.ordinal) infiniteRotation else 0f)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center        = Offset(sizePx / 2, sizePx / 2)
            val radius        = sizePx * 0.35f
            val centralRadius = sizePx * 0.08f
            val nodeRadius    = sizePx * 0.04f

            drawCircle(
                brush  = Brush.sweepGradient(themeColors.outerBorderColors),
                radius = sizePx * 0.48f,
                center = center,
                style  = Stroke(sizePx * 0.02f)
            )
            if (connectionProgress > 0f)
                drawConnections(center, radius, centralRadius, connectionProgress, sizePx, themeColors)
            if (centralScale > 0f)
                drawCentralCircle(center, centralRadius, centralScale, themeColors)
            if (nodesScale > 0f)
                drawNodes(center, radius, nodeRadius, nodesScale, themeColors)
        }
    }
}

private fun DrawScope.drawCentralCircle(
    center: Offset, centralRadius: Float, scale: Float, themeColors: SplashThemeColors
) {
    drawCircle(Brush.radialGradient(themeColors.centralColors), centralRadius * scale, center)
    drawCircle(themeColors.centralGlowColor, centralRadius * scale * 1.5f, center)
}

private fun DrawScope.drawConnections(
    center: Offset, radius: Float, centralRadius: Float,
    progress: Float, sizePx: Float, themeColors: SplashThemeColors
) {
    (0 until 8).forEach { i ->
        val radian      = i * 45f * PI / 180f
        val startX      = center.x + centralRadius * cos(radian).toFloat()
        val startY      = center.y + centralRadius * sin(radian).toFloat()
        val endX        = center.x + radius * cos(radian).toFloat()
        val endY        = center.y + radius * sin(radian).toFloat()
        val currentEndX = startX + (endX - startX) * progress
        val currentEndY = startY + (endY - startY) * progress
        drawLine(
            brush       = Brush.linearGradient(themeColors.connectionColors,
                Offset(startX, startY), Offset(currentEndX, currentEndY)),
            start       = Offset(startX, startY),
            end         = Offset(currentEndX, currentEndY),
            strokeWidth = sizePx * 0.008f,
            cap         = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawNodes(
    center: Offset, radius: Float, nodeRadius: Float, scale: Float, themeColors: SplashThemeColors
) {
    themeColors.nodeColors.forEachIndexed { i, color ->
        val radian = i * 45f * PI / 180f
        val pos    = Offset(center.x + radius * cos(radian).toFloat(),
            center.y + radius * sin(radian).toFloat())
        drawCircle(color.copy(alpha = themeColors.glowAlpha), nodeRadius * scale * 1.8f, pos)
        drawCircle(
            Brush.radialGradient(listOf(color, color.copy(alpha = 0.8f)), radius = nodeRadius * scale),
            nodeRadius * scale, pos
        )
    }
}

@Composable
fun PulsingDots(themeColors: SplashThemeColors) {
    val transition = rememberInfiniteTransition(label = "pulsingDots")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val s by transition.animateFloat(0.5f, 1f,
                infiniteRepeatable(tween(600, delayMillis = i * 200), RepeatMode.Reverse),
                label = "dot$i")
            Box(Modifier.size(8.dp).scale(s).clip(CircleShape).background(themeColors.dotColor))
        }
    }
}

data class SplashThemeColors(
    val backgroundColors  : List<Color>,
    val primaryTextColor  : Color,
    val secondaryTextColor: Color,
    val outerBorderColors : List<Color>,
    val centralColors     : List<Color>,
    val centralGlowColor  : Color,
    val connectionColors  : List<Color>,
    val nodeColors        : List<Color>,
    val dotColor          : Color,
    val glowAlpha         : Float
) {
    companion object {
        fun create(isDark: Boolean) = if (isDark) SplashThemeColors(
            backgroundColors   = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF334155)),
            primaryTextColor   = Color.White,
            secondaryTextColor = Color(0xFF94A3B8),
            outerBorderColors  = listOf(Color(0xFF4A90E2), Color(0xFF50E3C2), Color(0xFF9B59B6), Color(0xFF4A90E2)),
            centralColors      = listOf(Color(0xFF6366F1), Color(0xFF4F46E5), Color(0xFF3730A3)),
            centralGlowColor   = Color(0xFF6366F1).copy(alpha = 0.4f),
            connectionColors   = listOf(Color(0xFF6366F1), Color(0xFF06B6D4).copy(alpha = 0.8f)),
            nodeColors         = listOf(Color(0xFF9B59B6), Color(0xFF1ABC9C), Color(0xFFE74C3C),
                Color(0xFFF39C12), Color(0xFF3498DB), Color(0xFF27AE60),
                Color(0xFF2ECC71), Color(0xFF3F51B5)),
            dotColor           = Color(0xFF6366F1),
            glowAlpha          = 0.4f
        ) else SplashThemeColors(
            backgroundColors   = listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0), Color(0xFFCBD5E1)),
            primaryTextColor   = Color(0xFF1E293B),
            secondaryTextColor = Color(0xFF64748B),
            outerBorderColors  = listOf(Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFF8B5CF6), Color(0xFF3B82F6)),
            centralColors      = listOf(Color(0xFF6366F1), Color(0xFF4338CA), Color(0xFF312E81)),
            centralGlowColor   = Color(0xFF6366F1).copy(alpha = 0.2f),
            connectionColors   = listOf(Color(0xFF4338CA), Color(0xFF0891B2).copy(alpha = 0.9f)),
            nodeColors         = listOf(Color(0xFF8B5CF6), Color(0xFF14B8A6), Color(0xFFEF4444),
                Color(0xFFF59E0B), Color(0xFF3B82F6), Color(0xFF10B981),
                Color(0xFF22C55E), Color(0xFF6366F1)),
            dotColor           = Color(0xFF4338CA),
            glowAlpha          = 0.2f
        )
    }
}

enum class SplashAnimationState {
    INITIAL, LOGO_APPEAR, CONNECTIONS_ANIMATE, NODES_APPEAR, TEXT_APPEAR, COMPLETE
}