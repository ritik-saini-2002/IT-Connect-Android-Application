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

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun Ritik_2Theme(
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

object ThemedColors {
    @Composable
    fun primary(isDark: Boolean) = if (isDark) Color(0xFF8B8CF6) else Color(0xFF6366F1)

    @Composable
    fun background(isDark: Boolean) = if (isDark) Color(0xFF121212) else Color(0xFFFAFAFA)

    @Composable
    fun surface(isDark: Boolean) = if (isDark) Color(0xFF1E1E1E) else Color.White

    @Composable
    fun surfaceVariant(isDark: Boolean) = if (isDark) Color(0xFF2D2D2D) else Color(0xFFF3F4F6)

    @Composable
    fun onSurface(isDark: Boolean) = if (isDark) Color(0xFFE0E0E0) else Color(0xFF374151)

    @Composable
    fun onSurfaceVariant(isDark: Boolean) = if (isDark) Color(0xFFB0B0B0) else Color(0xFF6B7280)

    @Composable
    fun error(isDark: Boolean) = if (isDark) Color(0xFFFF6B6B) else Color(0xFFEF4444)

    @Composable
    fun outline(isDark: Boolean) = if (isDark) Color(0xFF4A4A4A) else Color(0xFFE5E7EB)

    @Composable
    fun success(isDark: Boolean) = if (isDark) Color(0xFF4ADE80) else Color(0xFF10B981)

    @Composable
    fun warning(isDark: Boolean) = if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B)
}