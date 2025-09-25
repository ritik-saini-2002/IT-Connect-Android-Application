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

// Light Theme Colors
private val md_theme_light_primary = Color(0xFF6366F1)
private val md_theme_light_onPrimary = Color(0xFFFFFFFF)
private val md_theme_light_primaryContainer = Color(0xFFE0E7FF)
private val md_theme_light_onPrimaryContainer = Color(0xFF1E1B3E)
private val md_theme_light_secondary = Color(0xFF8B5CF6)
private val md_theme_light_onSecondary = Color(0xFFFFFFFF)
private val md_theme_light_secondaryContainer = Color(0xFFEDE9FE)
private val md_theme_light_onSecondaryContainer = Color(0xFF2E1065)
private val md_theme_light_tertiary = Color(0xFFEC4899)
private val md_theme_light_onTertiary = Color(0xFFFFFFFF)
private val md_theme_light_tertiaryContainer = Color(0xFFFCE7F3)
private val md_theme_light_onTertiaryContainer = Color(0xFF831843)
private val md_theme_light_error = Color(0xFFEF4444)
private val md_theme_light_onError = Color(0xFFFFFFFF)
private val md_theme_light_errorContainer = Color(0xFFFEE2E2)
private val md_theme_light_onErrorContainer = Color(0xFF7F1D1D)
private val md_theme_light_background = Color(0xFFFAFAFA)
private val md_theme_light_onBackground = Color(0xFF1F2937)
private val md_theme_light_surface = Color(0xFFFFFFFF)
private val md_theme_light_onSurface = Color(0xFF374151)
private val md_theme_light_surfaceVariant = Color(0xFFF3F4F6)
private val md_theme_light_onSurfaceVariant = Color(0xFF6B7280)
private val md_theme_light_outline = Color(0xFFD1D5DB)
private val md_theme_light_outlineVariant = Color(0xFFE5E7EB)

// Improved Dark Theme Colors - Less dark, better visibility
private val md_theme_dark_primary = Color(0xFFA5A6F6)  // Lighter, more visible
private val md_theme_dark_onPrimary = Color(0xFF1A1A2E)
private val md_theme_dark_primaryContainer = Color(0xFF5B5FC7)
private val md_theme_dark_onPrimaryContainer = Color(0xFFEEF2FF)
private val md_theme_dark_secondary = Color(0xFFC4B5FD)  // Lighter purple
private val md_theme_dark_onSecondary = Color(0xFF1F1631)
private val md_theme_dark_secondaryContainer = Color(0xFF7C3AED)
private val md_theme_dark_onSecondaryContainer = Color(0xFFF5F3FF)
private val md_theme_dark_tertiary = Color(0xFFF9A8D4)  // Lighter pink
private val md_theme_dark_onTertiary = Color(0xFF701A3C)
private val md_theme_dark_tertiaryContainer = Color(0xFFBE185D)
private val md_theme_dark_onTertiaryContainer = Color(0xFFFCE7F3)
private val md_theme_dark_error = Color(0xFFFF8A80)  // Softer red
private val md_theme_dark_onError = Color(0xFF5F0A0A)
private val md_theme_dark_errorContainer = Color(0xFFDC2626)
private val md_theme_dark_onErrorContainer = Color(0xFFFFCDD2)
private val md_theme_dark_background = Color(0xFF1C1C1E)  // Lighter than pure black
private val md_theme_dark_onBackground = Color(0xFFF3F4F6)  // Much lighter text
private val md_theme_dark_surface = Color(0xFF2C2C2E)  // Elevated surface
private val md_theme_dark_onSurface = Color(0xFFE8E9EB)  // High contrast text
private val md_theme_dark_surfaceVariant = Color(0xFF3A3A3C)  // Lighter variant
private val md_theme_dark_onSurfaceVariant = Color(0xFFD1D1D6)  // Lighter secondary text
private val md_theme_dark_outline = Color(0xFF636366)  // More visible borders
private val md_theme_dark_outlineVariant = Color(0xFF48484A)

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
)

@Composable
fun Ritik_2Theme(
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
            // Use semi-transparent for smooth transitions
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Custom semantic colors with improved visibility
object AppColors {
    val success: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6EE7B7) else Color(0xFF10B981)

    val warning: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFCD34D) else Color(0xFFF59E0B)

    val info: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF93C5FD) else Color(0xFF3B82F6)

    // Additional semantic colors
    val successContainer: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF065F46) else Color(0xFFD1FAE5)

    val warningContainer: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF78350F) else Color(0xFFFEF3C7)

    val infoContainer: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1E3A8A) else Color(0xFFDBEAFE)
}

// Surface elevation colors for dark theme depth
object SurfaceElevation {
    @Composable
    fun level1(): Color = if (isSystemInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)

    @Composable
    fun level2(): Color = if (isSystemInDarkTheme()) Color(0xFF3A3A3C) else Color(0xFFFAFAFA)

    @Composable
    fun level3(): Color = if (isSystemInDarkTheme()) Color(0xFF48484A) else Color(0xFFF5F5F5)
}