package com.example.ritik_2.contact

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(
    onEmailClick: (String) -> Unit,
    onPhoneClick: (String) -> Unit,
    onLocationClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    var isVisible by remember { mutableStateOf(false) }

    // Developer info
    val developerInfo = DeveloperInfo(
        name = "Ritik Saini",
        email = "ritiksaini19757@gmail.com",
        phone = "8279991971",
        address = "Roorkee",
        role = "Developer & Administrator"
    )

    // Animation triggers
    LaunchedEffect(Unit) {
        delay(300)
        isVisible = true
    }

    // Background animation
    val infiniteTransition = rememberInfiniteTransition()
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                    )
                )
            )
    ) {
        // Animated background particles
        AnimatedBackgroundParticles(animatedOffset)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Header with animated appearance
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(800, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(800))
            ) {
                HeaderSection()
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Developer profile card
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(1000, delayMillis = 200, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(1000, delayMillis = 200))
            ) {
                DeveloperProfileCard(developerInfo)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Contact options
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(1200, delayMillis = 400, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(1200, delayMillis = 400))
            ) {
                ContactOptionsSection(
                    developerInfo = developerInfo,
                    onEmailClick = onEmailClick,
                    onPhoneClick = onPhoneClick,
                    onLocationClick = onLocationClick
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Copyright section
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(1400, delayMillis = 600))
            ) {
                CopyrightSection(developerInfo.name)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AnimatedBackgroundParticles(offset: Float) {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val particleColor = Color(0xFF4A90E2).copy(alpha = 0.1f)
        val particleCount = 8

        repeat(particleCount) { index ->
            val angle = (offset + index * 45f) * (Math.PI / 180f)
            val radius = size.minDimension * 0.3f + (index * 20f)
            val centerX = size.width / 2f
            val centerY = size.height / 2f

            val x = centerX + (radius * cos(angle)).toFloat()
            val y = centerY + (radius * sin(angle)).toFloat()

            drawCircle(
                color = particleColor,
                radius = 4f + (index * 2f),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated app icon
        val scale by rememberInfiniteTransition().animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Card(
            modifier = Modifier
                .size(80.dp)
                .scale(scale),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "App Icon",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "About Developer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Application Information",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeveloperProfileCard(developerInfo: DeveloperInfo) {
    var isHovered by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isHovered = !isHovered },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHovered) 16.dp else 8.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile avatar
//            val rotation by rememberInfiniteTransition().animateFloat(
//                initialValue = 0f,
//                targetValue = 360f,
//                animationSpec = infiniteRepeatable(
//                    animation = tween(10000, easing = LinearEasing),
//                    repeatMode = RepeatMode.Restart
//                )
//            )

            Card(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Developer Avatar",
                        modifier = Modifier
                            .size(50.dp),
                            //.rotate(rotation / 10),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Developer name
            Text(
                text = developerInfo.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Role
            Text(
                text = developerInfo.role,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Skills/Tags
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Android", "Kotlin", "Jetpack Compose").forEach { skill ->
                    AssistChip(
                        onClick = { },
                        label = { Text(text = skill, fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp) // Add manual spacing
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactOptionsSection(
    developerInfo: DeveloperInfo,
    onEmailClick: (String) -> Unit,
    onPhoneClick: (String) -> Unit,
    onLocationClick: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Get in Touch",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )

        ContactOptionItem(
            icon = Icons.Default.Email,
            title = "Email",
            value = developerInfo.email,
            onClick = { onEmailClick(developerInfo.email) }
        )

        ContactOptionItem(
            icon = Icons.Default.Phone,
            title = "Phone",
            value = developerInfo.phone,
            onClick = { onPhoneClick(developerInfo.phone) }
        )

        ContactOptionItem(
            icon = Icons.Default.LocationOn,
            title = "Location",
            value = developerInfo.address,
            onClick = { onLocationClick(developerInfo.address) }
        )
    }
}

@Composable
private fun ContactOptionItem(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                isPressed = !isPressed
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
//        colors = CardDefaults.cardColors(
//            containerColor = if (isPressed)
//                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
//            else
//                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
//        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = CircleShape,
//                colors = CardDefaults.cardColors(
//                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
//                ),
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Go",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CopyrightSection(developerName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Divider(
            modifier = Modifier.width(100.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )

        Text(
            text = "© 2024 $developerName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Text(
            text = "All rights reserved",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        // Animated heart
        val heartScale by rememberInfiniteTransition().animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Made with",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Love",
                tint = Color.Red,
                modifier = Modifier
                    .size(16.dp)
                    .scale(heartScale)
            )
            Text(
                text = "by Ritik Saini",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

data class DeveloperInfo(
    val name: String,
    val email: String,
    val phone: String,
    val address: String,
    val role: String
)