package com.example.features.settings.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.features.settings.ui.viewmodel.SecurityViewModel

private val SecurityCardShape = RoundedCornerShape(24.dp)
private val SecurityIconShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    viewModel: SecurityViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(viewModel) {
        viewModel.messageEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val biometricManager = remember(context) { BiometricManager.from(context) }
    val biometricResult = remember(context) {
        biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
    }
    val isBiometricAvailable = biometricResult == BiometricManager.BIOMETRIC_SUCCESS

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Security Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ChangePinCard(viewModel = viewModel)

            PreferenceCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PreferenceToggleRow(
                        icon = Icons.Default.Fingerprint,
                        title = "Biometric Authentication",
                        subtitle = if (isBiometricAvailable) {
                            "Use fingerprint or face to unlock"
                        } else {
                            "Biometrics are not available on this device"
                        },
                        checked = uiState.biometricEnabled,
                        enabled = isBiometricAvailable,
                        onCheckedChange = { isChecked ->
                            if (!isChecked) {
                                viewModel.updateBiometricEnabled(false)
                            } else {
                                val activity = context as? FragmentActivity
                                if (activity != null && isBiometricAvailable) {
                                    val executor = ContextCompat.getMainExecutor(context)
                                    val prompt = BiometricPrompt(
                                        activity,
                                        executor,
                                        object : BiometricPrompt.AuthenticationCallback() {
                                            override fun onAuthenticationSucceeded(
                                                result: BiometricPrompt.AuthenticationResult
                                            ) {
                                                super.onAuthenticationSucceeded(result)
                                                viewModel.updateBiometricEnabled(true)
                                            }
                                        }
                                    )
                                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                        .setTitle("Enable Biometric Unlock")
                                        .setSubtitle("Authenticate to enable biometric unlock")
                                        .setNegativeButtonText("Cancel")
                                        .build()
                                    prompt.authenticate(promptInfo)
                                }
                            }
                        }
                    )

                    SecurityDivider()

                    var autoLockExpanded by remember { mutableStateOf(false) }
                    val autoLockOptions = listOf(
                        0L to "Immediately",
                        60_000L to "1 Minute",
                        300_000L to "5 Minutes",
                        600_000L to "10 Minutes",
                        -1L to "Never"
                    )

                    PreferenceDropdownItem(
                        icon = Icons.Default.Timer,
                        title = "Auto Lock",
                        subtitle = "Lock after inactivity",
                        selectedValue = autoLockOptions.firstOrNull {
                            it.first == uiState.autoLockTimeout
                        }?.second ?: "Immediately",
                        expanded = autoLockExpanded,
                        onExpandedChange = { autoLockExpanded = it },
                        options = autoLockOptions.map { it.second },
                        onOptionSelected = { selected ->
                            val timeout = autoLockOptions.firstOrNull {
                                it.second == selected
                            }?.first ?: 0L
                            viewModel.updateAutoLockTimeout(timeout)
                            autoLockExpanded = false
                        }
                    )

                    SecurityDivider()

                    PreferenceToggleRow(
                        icon = Icons.Default.PhonelinkLock,
                        title = "Require PIN on Resume",
                        subtitle = "Ask for PIN when returning to the app",
                        checked = uiState.requirePinOnResume,
                        onCheckedChange = viewModel::updateRequirePinOnResume
                    )
                }
            }

            SecurityStatusCard(
                biometricAvailable = isBiometricAvailable,
                biometricEnabled = uiState.biometricEnabled,
                autoLockTimeout = uiState.autoLockTimeout,
                pinOnResume = uiState.requirePinOnResume
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ChangePinCard(viewModel: SecurityViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showCurrentPin by remember { mutableStateOf(false) }
    var showNewPin by remember { mutableStateOf(false) }
    var showConfirmPin by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    SecurityCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SecurityIcon(icon = Icons.Default.Lock)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Change PIN",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Update your 4-digit PIN to keep your account secure.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            PinTextField(
                value = currentPin,
                onValueChange = { currentPin = it },
                label = "Current PIN",
                placeholder = "Enter current PIN",
                isError = uiState.currentPinError != null,
                errorText = uiState.currentPinError,
                showPassword = showCurrentPin,
                onToggleVisibility = { showCurrentPin = !showCurrentPin }
            )

            PinTextField(
                value = newPin,
                onValueChange = { newPin = it },
                label = "New PIN (4 digits)",
                placeholder = "Enter new PIN",
                isError = uiState.newPinError != null,
                errorText = uiState.newPinError,
                showPassword = showNewPin,
                onToggleVisibility = { showNewPin = !showNewPin }
            )

            PinTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it },
                label = "Confirm New PIN",
                placeholder = "Confirm new PIN",
                isError = uiState.confirmPinError != null,
                errorText = uiState.confirmPinError,
                showPassword = showConfirmPin,
                onToggleVisibility = { showConfirmPin = !showConfirmPin }
            )

            Button(
                onClick = {
                    viewModel.changePin(currentPin, newPin, confirmPin)
                    // Clear the fields only after a valid-looking submission.
                    // The ViewModel remains responsible for validating the current PIN.
                    if (
                        currentPin.length == 4 &&
                        newPin.length == 4 &&
                        newPin == confirmPin &&
                        uiState.currentPinError == null &&
                        uiState.newPinError == null &&
                        uiState.confirmPinError == null
                    ) {
                        currentPin = ""
                        newPin = ""
                        confirmPin = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Update PIN",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isError: Boolean,
    errorText: String?,
    showPassword: Boolean,
    onToggleVisibility: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(4)
            onValueChange(digits)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = if (showPassword) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (showPassword) {
                        Icons.Default.Visibility
                    } else {
                        Icons.Default.VisibilityOff
                    },
                    contentDescription = if (showPassword) "Hide PIN" else "Show PIN"
                )
            }
        },
        isError = isError,
        supportingText = errorText?.let { error -> { Text(error) } },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun PreferenceCard(content: @Composable ColumnScope.() -> Unit) {
    SecurityCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun PreferenceToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 88.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SecurityIcon(icon = icon)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferenceDropdownItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selectedValue: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .clickable { onExpandedChange(true) }
                .defaultMinSize(minHeight = 88.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SecurityIcon(icon = icon)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }

            Text(
                text = selectedValue,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onOptionSelected(option) }
                )
            }
        }
    }
}

@Composable
private fun SecurityStatusCard(
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    autoLockTimeout: Long,
    pinOnResume: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val autoLockText = when (autoLockTimeout) {
        0L -> "Immediately"
        -1L -> "Never"
        else -> "${autoLockTimeout / 60_000L} Minutes"
    }

    SecurityCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SecurityIcon(icon = Icons.Default.Lock)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Security Status",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Overview of your current security settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        width = 1.dp,
                        color = colors.outlineVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 14.dp)
            ) {
                StatusRow(
                    icon = Icons.Default.Lock,
                    label = "PIN protection",
                    value = "Active",
                    positive = true
                )
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.55f))
                StatusRow(
                    icon = Icons.Default.Fingerprint,
                    label = "Biometric authentication",
                    value = when {
                        !biometricAvailable -> "Unavailable"
                        biometricEnabled -> "Active"
                        else -> "Disabled"
                    },
                    positive = biometricAvailable && biometricEnabled
                )
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.55f))
                StatusRow(
                    icon = Icons.Default.Timer,
                    label = "Auto lock",
                    value = autoLockText,
                    positive = true
                )
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.55f))
                StatusRow(
                    icon = Icons.Default.PhonelinkLock,
                    label = "PIN on resume",
                    value = if (pinOnResume) "Enabled" else "Disabled",
                    positive = pinOnResume
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    label: String,
    value: String,
    positive: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val badgeBackground = if (positive) {
        colors.tertiaryContainer
    } else {
        colors.errorContainer
    }
    val badgeContent = if (positive) {
        colors.onTertiaryContainer
    } else {
        colors.onErrorContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(23.dp)
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface
        )
        Surface(
            shape = CircleShape,
            color = badgeBackground
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = badgeContent
            )
        }
    }
}

@Composable
private fun SecurityIcon(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(SecurityIconShape)
            .background(colors.primaryContainer.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(27.dp)
        )
    }
}

@Composable
private fun SecurityDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

@Composable
private fun SecurityCard(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colors.outlineVariant.copy(alpha = 0.5f),
                shape = SecurityCardShape
            ),
        shape = SecurityCardShape,
        color = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        content()
    }
}
