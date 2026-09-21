cat << 'INNER_EOF' > app/src/main/java/com/example/core/designsystem/Theme.kt
package com.example.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.settings.domain.model.AppTheme

/**
 * Custom Spacing tokens adhering to the 8dp grid system.
 */
@Immutable
data class PgSpacing(
    val default: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
    val doubleExtraLarge: Dp = 32.dp
)

val LocalSpacing = staticCompositionLocalOf { PgSpacing() }

// Visual Colors matching Premium Slate Aesthetics
val SlateDarkBackground = Color(0xFF020617)
val SlateDarkSurface = Color(0xFF0B0F19)
val SlateDarkPrimary = Color(0xFF38BDF8)
val SlateDarkSecondary = Color(0xFF94A3B8)
val SlateDarkOnBackground = Color(0xFFF8FAFC)
val SlateDarkOnSurface = Color(0xFFF1F5F9)

val SlateLightBackground = Color(0xFFF8FAFC)
val SlateLightSurface = Color(0xFFFFFFFF)
val SlateLightPrimary = Color(0xFF0284C7)
val SlateLightSecondary = Color(0xFF64748B)
val SlateLightOnBackground = Color(0xFF0F172A)
val SlateLightOnSurface = Color(0xFF1E293B)

val PgDarkColorScheme = darkColorScheme(
    primary = SlateDarkPrimary,
    secondary = SlateDarkSecondary,
    background = SlateDarkBackground,
    surface = SlateDarkSurface,
    onBackground = SlateDarkOnBackground,
    onSurface = SlateDarkOnSurface,
    error = Color(0xFFF87171)
)

val PgLightColorScheme = lightColorScheme(
    primary = SlateLightPrimary,
    secondary = SlateLightSecondary,
    background = SlateLightBackground,
    surface = SlateLightSurface,
    onBackground = SlateLightOnBackground,
    onSurface = SlateLightOnSurface,
    error = Color(0xFFEF4444)
)

// Typography definitions pairing sans-serif (Inter-feel) with display styles
val PgTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

val PgShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
)

/**
 * Central Enterprise Design System Theme Provider.
 */
@Composable
fun PgTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> PgDarkColorScheme
        else -> PgLightColorScheme
    }

    val spacing = PgSpacing()

    CompositionLocalProvider(
        LocalSpacing provides spacing
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PgTypography,
            shapes = PgShapes,
            content = content
        )
    }
}
INNER_EOF
