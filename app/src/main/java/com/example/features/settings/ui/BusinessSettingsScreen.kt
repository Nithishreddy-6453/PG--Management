package com.example.features.settings.ui

import androidx.compose.runtime.collectAsState
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.LocalSpacing
import com.example.features.settings.ui.viewmodel.BusinessSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessSettingsScreen(
    viewModel: BusinessSettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(key1 = true) {
        viewModel.saveEvent.collect { success ->
            if (success) {
                Toast.makeText(context, "Business Settings Saved", Toast.LENGTH_SHORT).show()
                onBackClick()
            } else {
                Toast.makeText(context, "Failed to save settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                PaddingValues(spacing.medium).let {
                    Button(
                        onClick = { viewModel.saveSettings() },
                        enabled = !uiState.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(it)
                            .height(56.dp)
                            .testTag("save_business_settings_button"),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(80.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save Changes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize().testTag("business_settings_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.large)
        ) {
            // Profile Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    Text(
                        text = "Business Profile",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    SettingsTextField(
                        value = uiState.pgName,
                        onValueChange = viewModel::onPgNameChange,
                        label = "PG / Hostel Name *",
                        icon = Icons.Default.Business,
                        errorText = uiState.pgNameError
                    )

                    SettingsTextField(
                        value = uiState.ownerName,
                        onValueChange = viewModel::onOwnerNameChange,
                        label = "Owner Name *",
                        icon = Icons.Default.Person,
                        errorText = uiState.ownerNameError
                    )

                    SettingsTextField(
                        value = uiState.contactNumber,
                        onValueChange = viewModel::onContactNumberChange,
                        label = "Contact Number",
                        icon = Icons.Default.Phone,
                        keyboardType = KeyboardType.Phone,
                        errorText = uiState.contactNumberError
                    )

                    SettingsTextField(
                        value = uiState.emailAddress,
                        onValueChange = viewModel::onEmailAddressChange,
                        label = "Email Address",
                        icon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email,
                        errorText = uiState.emailAddressError
                    )

                    SettingsTextField(
                        value = uiState.address,
                        onValueChange = viewModel::onAddressChange,
                        label = "Business Address",
                        icon = Icons.Default.LocationOn,
                        singleLine = false,
                        minLines = 2
                    )
                }
            }

            // Defaults Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    Text(
                        text = "Default Values",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "These defaults will be auto-filled when adding new rooms or tenants.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SettingsTextField(
                                value = uiState.defaultMonthlyRent,
                                onValueChange = viewModel::onMonthlyRentChange,
                                label = "Monthly Rent",
                                icon = Icons.Default.AttachMoney,
                                keyboardType = KeyboardType.Number,
                                errorText = uiState.monthlyRentError
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            SettingsTextField(
                                value = uiState.defaultSecurityDeposit,
                                onValueChange = viewModel::onSecurityDepositChange,
                                label = "Security Deposit",
                                icon = Icons.Default.AttachMoney,
                                keyboardType = KeyboardType.Number,
                                errorText = uiState.securityDepositError
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SettingsTextField(
                                value = uiState.defaultRentDueDay,
                                onValueChange = viewModel::onDueDayChange,
                                label = "Due Day (1-31)",
                                icon = Icons.Default.CalendarToday,
                                keyboardType = KeyboardType.Number,
                                errorText = uiState.dueDayError
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            // Currency Dropdown
                            var expanded by remember { mutableStateOf(false) }
                            val currencies = listOf("₹" to "INR (₹)")
                            
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    value = currencies.find { it.first == uiState.currency }?.second ?: uiState.currency,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Currency") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                    modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    currencies.forEach { (symbol, name) ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                viewModel.onCurrencyChange(symbol)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    errorText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
