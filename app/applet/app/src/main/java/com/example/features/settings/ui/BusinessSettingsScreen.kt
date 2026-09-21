package com.example.features.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.LocalSpacing
import com.example.features.settings.domain.model.BusinessSettings
import com.example.features.settings.ui.viewmodel.BusinessSettingsUiState
import com.example.features.settings.ui.viewmodel.BusinessSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessSettingsScreen(
    viewModel: BusinessSettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is BusinessSettingsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is BusinessSettingsUiState.Success -> {
                var settings by remember { mutableStateOf(state.settings) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    OutlinedTextField(
                        value = settings.pgName,
                        onValueChange = { settings = settings.copy(pgName = it) },
                        label = { Text("PG/Hostel Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.ownerName,
                        onValueChange = { settings = settings.copy(ownerName = it) },
                        label = { Text("Owner Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.contactNumber,
                        onValueChange = { settings = settings.copy(contactNumber = it) },
                        label = { Text("Contact Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.emailAddress,
                        onValueChange = { settings = settings.copy(emailAddress = it) },
                        label = { Text("Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.address,
                        onValueChange = { settings = settings.copy(address = it) },
                        label = { Text("Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.defaultMonthlyRent.toString(),
                        onValueChange = { settings = settings.copy(defaultMonthlyRent = it.toDoubleOrNull() ?: 0.0) },
                        label = { Text("Default Monthly Rent") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.defaultSecurityDeposit.toString(),
                        onValueChange = { settings = settings.copy(defaultSecurityDeposit = it.toDoubleOrNull() ?: 0.0) },
                        label = { Text("Default Security Deposit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.defaultRentDueDay.toString(),
                        onValueChange = { settings = settings.copy(defaultRentDueDay = it.toIntOrNull() ?: 1) },
                        label = { Text("Default Rent Due Day") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.currency,
                        onValueChange = { settings = settings.copy(currency = it) },
                        label = { Text("Currency") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(spacing.medium))
                    
                    Button(
                        onClick = { viewModel.updateSettings(settings) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}
