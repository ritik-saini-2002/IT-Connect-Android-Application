@file:OptIn(ExperimentalAnimationApi::class)

package com.example.ritik_2.ui.theme.ui.theme.logo

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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

@Composable
fun ITConnectSplashScreen(
    onSplashComplete: () -> Unit = {}
) {
    var animationState by remember { mutableStateOf(SplashAnimationState.INITIAL) }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Calculate responsive logo size (30% of screen width, max 300dp)
    val logoSize = minOf(screenWidth * 0.3f, 300.dp)

    // Animation sequences
    LaunchedEffect(Unit) {
        delay(300)
        animationState = SplashAnimationState.LOGO_APPEAR
        delay(800)
        animationState = SplashAnimationState.CONNECTIONS_ANIMATE
        delay(1200)
        animationState = SplashAnimationState.NODES_APPEAR
        delay(1000)
        animationState = SplashAnimationState.TEXT_APPEAR
        delay(1500)
        animationState = SplashAnimationState.COMPLETE
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F172A)
                    ),
                    radius = screenWidth.value * 0.8f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Logo
            ITConnectLogo(
                size = logoSize,
                animationState = animationState
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Animated Text
            AnimatedVisibility(
                visible = animationState.ordinal >= SplashAnimationState.TEXT_APPEAR.ordinal,
                enter = fadeIn(animationSpec = tween(800)) + slideInVertically(
                    animationSpec = tween(800),
                    initialOffsetY = { it / 2 }
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "IT Connect",
                        fontSize = (screenWidth.value * 0.06f).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Network Solutions",
                        fontSize = (screenWidth.value * 0.035f).sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Loading indicator
            AnimatedVisibility(
                visible = animationState.ordinal >= SplashAnimationState.TEXT_APPEAR.ordinal,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 400))
            ) {
                PulsingDots()
            }
        }
    }
}

@Composable
fun ITConnectLogo(
    size: Dp,
    animationState: SplashAnimationState
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }

    // Central circle animation
    val centralScale by animateFloatAsState(
        targetValue = if (animationState.ordinal >= SplashAnimationState.LOGO_APPEAR.ordinal) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    // Connection lines animation
    val connectionProgress by animateFloatAsState(
        targetValue = if (animationState.ordinal >= SplashAnimationState.CONNECTIONS_ANIMATE.ordinal) 1f else 0f,
        animationSpec = tween(1000, easing = EaseInOutCubic)
    )

    // Nodes animation
    val nodesScale by animateFloatAsState(
        targetValue = if (animationState.ordinal >= SplashAnimationState.NODES_APPEAR.ordinal) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    // Rotation animation for the entire logo
    val infiniteRotation by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier
            .size(size)
            .rotate(if (animationState.ordinal >= SplashAnimationState.COMPLETE.ordinal) infiniteRotation else 0f)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(sizePx / 2, sizePx / 2)
            val radius = sizePx * 0.35f
            val centralRadius = sizePx * 0.08f
            val nodeRadius = sizePx * 0.04f

            // Draw outer gradient border
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF4A90E2),
                        Color(0xFF50E3C2),
                        Color(0xFF9B59B6),
                        Color(0xFF4A90E2)
                    )
                ),
                radius = sizePx * 0.48f,
                center = center,
                style = Stroke(width = sizePx * 0.02f)
            )

            // Draw connection lines with animation
            if (connectionProgress > 0f) {
                drawConnections(
                    center = center,
                    radius = radius,
                    centralRadius = centralRadius,
                    progress = connectionProgress,
                    sizePx = sizePx
                )
            }

            // Draw central circle
            if (centralScale > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6366F1),
                            Color(0xFF4F46E5),
                            Color(0xFF3730A3)
                        )
                    ),
                    radius = centralRadius * centralScale,
                    center = center
                )

                // Central circle glow effect
                drawCircle(
                    color = Color(0xFF6366F1).copy(alpha = 0.3f),
                    radius = centralRadius * centralScale * 1.5f,
                    center = center
                )
            }

            // Draw nodes with animation
            if (nodesScale > 0f) {
                drawNodes(
                    center = center,
                    radius = radius,
                    nodeRadius = nodeRadius,
                    scale = nodesScale
                )
            }
        }
    }
}

fun DrawScope.drawConnections(
    center: Offset,
    radius: Float,
    centralRadius: Float,
    progress: Float,
    sizePx: Float
) {
    val angles = (0 until 8).map { it * 45f }

    angles.forEach { angle ->
        val radian = angle * PI / 180f
        val startX = center.x + centralRadius * cos(radian).toFloat()
        val startY = center.y + centralRadius * sin(radian).toFloat()
        val endX = center.x + radius * cos(radian).toFloat()
        val endY = center.y + radius * sin(radian).toFloat()

        val currentEndX = startX + (endX - startX) * progress
        val currentEndY = startY + (endY - startY) * progress

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF6366F1),
                    Color(0xFF06B6D4).copy(alpha = 0.8f)
                ),
                start = Offset(startX, startY),
                end = Offset(currentEndX, currentEndY)
            ),
            start = Offset(startX, startY),
            end = Offset(currentEndX, currentEndY),
            strokeWidth = sizePx * 0.008f,
            cap = StrokeCap.Round
        )
    }
}

fun DrawScope.drawNodes(
    center: Offset,
    radius: Float,
    nodeRadius: Float,
    scale: Float
) {
    val nodeColors = listOf(
        Color(0xFF9B59B6), // Purple
        Color(0xFF1ABC9C), // Teal
        Color(0xFFE74C3C), // Red
        Color(0xFFF39C12), // Orange
        Color(0xFF3498DB), // Blue
        Color(0xFF27AE60), // Green
        Color(0xFF2ECC71), // Light Green
        Color(0xFF3F51B5)  // Indigo
    )

    nodeColors.forEachIndexed { index, color ->
        val angle = index * 45f
        val radian = angle * PI / 180f
        val x = center.x + radius * cos(radian).toFloat()
        val y = center.y + radius * sin(radian).toFloat()

        // Node glow effect
        drawCircle(
            color = color.copy(alpha = 0.4f),
            radius = nodeRadius * scale * 1.8f,
            center = Offset(x, y)
        )

        // Main node
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color,
                    color.copy(alpha = 0.8f)
                ),
                radius = nodeRadius * scale
            ),
            radius = nodeRadius * scale,
            center = Offset(x, y)
        )
    }
}

@Composable
fun PulsingDots() {
    val infiniteTransition = rememberInfiniteTransition()

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Color(0xFF6366F1))
            )
        }
    }
}

enum class SplashAnimationState {
    INITIAL,
    LOGO_APPEAR,
    CONNECTIONS_ANIMATE,
    NODES_APPEAR,
    TEXT_APPEAR,
    COMPLETE
}

// Usage in your MainActivity or wherever you need it
@Composable
fun SplashActivity() {
    ITConnectSplashScreen(
        onSplashComplete = {
            // Navigate to your main screen
            // navController.navigate("main_screen")
        }
    )
}