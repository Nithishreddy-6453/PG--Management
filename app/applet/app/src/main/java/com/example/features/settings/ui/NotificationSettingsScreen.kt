package com.example.features.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.core.designsystem.LocalSpacing
import com.example.features.settings.ui.viewmodel.NotificationSettingsUiState
import com.example.features.settings.ui.viewmodel.NotificationSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    viewModel: NotificationSettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is NotificationSettingsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is NotificationSettingsUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    NotificationToggle(
                        title = "Rent Due Reminder",
                        subtitle = "Alert when rent is due",
                        checked = state.settings.rentDueReminder,
                        onCheckedChange = { viewModel.updateSettings(state.settings.copy(rentDueReminder = it)) }
                    )
                    NotificationToggle(
                        title = "Overdue Rent Reminder",
                        subtitle = "Alert for pending payments",
                        checked = state.settings.overdueRentReminder,
                        onCheckedChange = { viewModel.updateSettings(state.settings.copy(overdueRentReminder = it)) }
                    )
                    NotificationToggle(
                        title = "Monthly Invoice Reminder",
                        subtitle = "Alert to generate invoices",
                        checked = state.settings.monthlyInvoiceReminder,
                        onCheckedChange = { viewModel.updateSettings(state.settings.copy(monthlyInvoiceReminder = it)) }
                    )
                    NotificationToggle(
                        title = "Backup Reminder",
                        subtitle = "Alert to take periodic backups",
                        checked = state.settings.backupReminder,
                        onCheckedChange = { viewModel.updateSettings(state.settings.copy(backupReminder = it)) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
