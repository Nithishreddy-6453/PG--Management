package com.example.features.startup

import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.LocalSpacing

/**
 * Splash screen layout rendering a pulsing brand icon and progress indicators.
 */
@Composable
fun SplashScreen(
    startupManager: StartupManager,
    onInitializationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val startupState by startupManager.state.collectAsState()

    // 1. Kickstart bootstrap sequence
    LaunchedEffect(Unit) {
        startupManager.startBootstrap()
    }

    // 2. Observe startup success
    LaunchedEffect(startupState) {
        if (startupState is StartupState.Ready) {
            onInitializationComplete()
        }
    }

    // 3. Pulse scale animation for brand logomark
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_animation")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("splash_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(spacing.extraLarge)
        ) {
            // Reusable decorative building/guest house vector icon
            Icon(
                imageVector = Icons.Default.HomeWork,
                contentDescription = "PG Manager Brand Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(96.dp)
                    .scale(logoScale)
                    .testTag("brand_logo_icon")
            )

            Spacer(modifier = Modifier.height(spacing.extraLarge))

            // Brand Header display heading
            Text(
                text = "PG Manager",
                style = MaterialTheme.typography.displayLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                ),
                modifier = Modifier.testTag("splash_title_text")
            )

            Text(
                text = "Premium Paying Guest Management",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.testTag("splash_subtitle_text")
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Render Loader driven by StartupCoordinator status
            when (val state = startupState) {
                is StartupState.Initializing -> {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("splash_progress_bar"),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(spacing.large))
                    Text(
                        text = state.status,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("splash_status_text")
                    )
                }
                is StartupState.Failed -> {
                    Text(
                        text = "Initialization Failed: ${state.reason}",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("splash_error_text")
                    )
                }
                else -> {
                    // Pre-warm fallback
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("splash_loading_spinner"),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                }
            }
        }
    }
}
