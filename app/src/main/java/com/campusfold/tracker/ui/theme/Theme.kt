package com.campusfold.tracker.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val FoldDarkColorScheme = darkColorScheme(
    primary = NeonEmerald,
    onPrimary = OledBlack,
    secondary = NeonCyan,
    onSecondary = OledBlack,
    tertiary = NeonAmber,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = MatteCharcoal,
    onSurface = TextPrimary,
    surfaceVariant = CardElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderCharcoal
)

@Composable
fun CampusFoldTrackerTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = OledBlack.toArgb()
                window.navigationBarColor = OledBlack.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = FoldDarkColorScheme,
        typography = Typography,
        content = content
    )
}
