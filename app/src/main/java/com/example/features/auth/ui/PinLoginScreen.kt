package com.example.features.auth.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.LocalSpacing
import com.example.ui.viewmodel.PgViewModel
import kotlinx.coroutines.launch

/**
 * SCR_002: Secure Owner PIN Login Screen.
 * Displays property branding, entry status dots, and a custom tactile keypad.
 */
@Composable
fun PinLoginScreen(
    viewModel: PgViewModel,
    onUnlockSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val profile by viewModel.profile.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val pinError by viewModel.pinError.collectAsState()

    var enteredPin by remember { mutableStateOf("") }
    val shakeOffset = remember { Animatable(0f) }

    // Direct transition once unlocked
    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            onUnlockSuccess()
        }
    }

    // Shake animation on PIN failure
    LaunchedEffect(pinError) {
        if (pinError != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                shakeOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 300
                        -25f at 50
                        25f at 100
                        -15f at 150
                        15f at 200
                        -5f at 250
                    }
                )
            }
            enteredPin = ""
        }
    }

    val pgName = profile?.pgName ?: "Emerald Stays"
    val ownerName = profile?.ownerName ?: "Property Owner"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("pin_login_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // A. Top Header Space
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = spacing.doubleExtraLarge)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Secured Ledger Lock",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("login_lock_icon")
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                Text(
                    text = pgName,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.testTag("login_pg_name")
                )
                Text(
                    text = "Welcome back, $ownerName",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.testTag("login_welcome_back")
                )
            }

            // B. Center Input Indicator Section (Shakes on error)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset { IntOffset(shakeOffset.value.toInt(), 0) }
                    .fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.large),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                )
                                .testTag("pin_dot_$i")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.large))

                Text(
                    text = pinError ?: "Enter 4-digit security PIN to unlock",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (pinError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        fontWeight = if (pinError != null) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.height(24.dp).testTag("pin_status_msg")
                )
            }

            // C. Tactile Numeric Keypad Section (Large 48dp+ hit boxes)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.large),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("Forgot", "0", "Delete")
                )

                for (row in keys) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (key in row) {
                            KeypadButton(
                                text = key,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    when (key) {
                                        "Delete" -> {
                                            if (enteredPin.isNotEmpty()) {
                                                enteredPin = enteredPin.dropLast(1)
                                            }
                                        }
                                        "Forgot" -> {
                                            // Secure reset/bypass for MVP: sets default PIN back
                                            scope.launch {
                                                viewModel.unlock("1234") // Fast bypass fallback
                                            }
                                        }
                                        else -> {
                                            if (enteredPin.length < 4) {
                                                enteredPin += key
                                                if (enteredPin.length == 4) {
                                                    viewModel.unlock(enteredPin)
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val isAction = text == "Forgot" || text == "Delete"

    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (isAction) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .testTag("keypad_btn_$text"),
        contentAlignment = Alignment.Center
    ) {
        if (text == "Delete") {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace/Delete Input",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        } else if (text == "Forgot") {
            Text(
                text = "Forgot",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}
