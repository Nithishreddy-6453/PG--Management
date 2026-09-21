package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PremiumLightColorScheme = lightColorScheme(
    primary = MaterialBlue,
    onPrimary = Color.White,
    primaryContainer = MaterialBlueContainer,
    onPrimaryContainer = OnMaterialBlueContainer,
    secondary = EmeraldGreen,
    onSecondary = Color.White,
    secondaryContainer = EmeraldGreenContainer,
    onSecondaryContainer = OnEmeraldGreenContainer,
    error = PremiumError,
    onError = Color.White,
    errorContainer = PremiumErrorContainer,
    onErrorContainer = OnPremiumErrorContainer,
    background = PremiumBackground,
    onBackground = TextPrimary,
    surface = PremiumSurface,
    onSurface = TextPrimary,
    surfaceVariant = PremiumSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TextSecondary,
    outlineVariant = OutlineVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = PremiumLightColorScheme // Force Premium Light Theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
