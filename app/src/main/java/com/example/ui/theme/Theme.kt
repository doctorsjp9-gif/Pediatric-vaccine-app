package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DefaultTealLight = lightColorScheme(
    primary = PrimaryTeal,
    onPrimary = OnPrimaryTeal,
    primaryContainer = PrimaryContainerTeal,
    onPrimaryContainer = OnPrimaryContainerTeal,
    secondary = SecondarySlate,
    onSecondary = OnSecondarySlate,
    secondaryContainer = SecondaryContainerSlate,
    onSecondaryContainer = OnSecondaryContainerSlate,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

private fun getLightSchemeForTheme(themeName: String) = when (themeName) {
    "BLUE" -> DefaultTealLight.copy(
        primary = Color(0xFF0061A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD1E4FF),
        onPrimaryContainer = Color(0xFF001D36)
    )
    "EMERALD" -> DefaultTealLight.copy(
        primary = Color(0xFF006C4C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF89F8C7),
        onPrimaryContainer = Color(0xFF002114)
    )
    "CORAL" -> DefaultTealLight.copy(
        primary = Color(0xFFA04020),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBCF),
        onPrimaryContainer = Color(0xFF380D00)
    )
    "PURPLE" -> DefaultTealLight.copy(
        primary = Color(0xFF6B4EA2),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFECDCFF),
        onPrimaryContainer = Color(0xFF260058)
    )
    "BERRY" -> DefaultTealLight.copy(
        primary = Color(0xFFC2185B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD8E4),
        onPrimaryContainer = Color(0xFF3B0018)
    )
    "GOLDEN" -> DefaultTealLight.copy(
        primary = Color(0xFFC66900),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE0B2),
        onPrimaryContainer = Color(0xFF3E1F00)
    )
    else -> DefaultTealLight
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    clinicTheme: String = "TEAL",
    content: @Composable () -> Unit,
) {
    // Always use the high-contrast clinical light color scheme so cards, forms, and badges
    // remain 100% legible on Android 14 regardless of device system dark mode setting.
    val colorScheme = getLightSchemeForTheme(clinicTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.surface.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
