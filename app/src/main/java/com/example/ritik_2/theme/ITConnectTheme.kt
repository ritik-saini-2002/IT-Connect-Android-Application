package com.example.ritik_2.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────
//  IT Connect Primary Theme Colors
//  These are used consistently across ALL activities.
//  FIX: Dynamic color is now DISABLED by default so every device
//  sees the same vibrant blue theme — Material You was overriding
//  our colors on Pixel devices making them look different.
// ─────────────────────────────────────────────────────────────




object ITConnectGlass {
    // Dark mode glass
    val darkBg1          = Color(0xFF0D0D1A)
    val darkBg2          = Color(0xFF0F0F23)
    val darkBg3          = Color(0xFF1A1A2E)
    val darkGlassBg      = Color.White.copy(alpha = 0.07f)
    val darkGlassBorder  = Color.White.copy(alpha = 0.12f)
    val darkAccentBlue   = Color(0xFF6C63FF)
    val darkAccentTeal   = Color(0xFF00D4AA)
    val darkAccentPurple = Color(0xFFB388FF)
    val darkDanger       = Color(0xFFFF6B9D)

    // Light mode glass
    val lightBg1         = Color(0xFFF0F2F5)
    val lightBg2         = Color(0xFFE8EBF0)
    val lightGlassBg     = Color.White.copy(alpha = 0.60f)
    val lightGlassBorder = Color.Black.copy(alpha = 0.08f)
    val lightAccentBlue  = Color(0xFF5B52E0)
    val lightAccentTeal  = Color(0xFF00B894)
    val lightDanger      = Color(0xFFE74C6F)
}


private val primaryLight = Color(0xFF2962FF) // Vibrant blue
private val secondaryLight = Color(0xFF00BFA5) // Teal
private val tertiaryLight = Color(0xFF7C4DFF) // Purple

private val primaryDark = Color(0xFF448AFF) // Lighter blue for dark mode
private val secondaryDark = Color(0xFF1DE9B6) // Brighter teal for dark mode
private val tertiaryDark = Color(0xFFB388FF) // Lighter purple for dark mode

private val DarkColorScheme = darkColorScheme(
    primary = primaryDark,
    secondary = secondaryDark,
    tertiary = tertiaryDark,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = primaryLight,
    secondary = secondaryLight,
    tertiary = tertiaryLight,
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun ITConnectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}