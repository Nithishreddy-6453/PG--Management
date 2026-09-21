package com.example.features.settings.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.features.settings.ui.viewmodel.NotificationSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Rent Reminders",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Rent Due Reminder") },
                            supportingContent = { Text("Notify me when rent payments are due") },
                            trailingContent = {
                                Switch(
                                    checked = uiState.settings.rentDueReminder,
                                    onCheckedChange = { viewModel.updateRentDueReminder(it) }
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Overdue Rent Reminder") },
                            supportingContent = { Text("Notify me when rent payments are overdue") },
                            trailingContent = {
                                Switch(
                                    checked = uiState.settings.overdueRentReminder,
                                    onCheckedChange = { viewModel.updateOverdueRentReminder(it) }
                                )
                            }
                        )
                    }
                }

                Text(
                    text = "System & Updates",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Monthly Invoice Reminder") },
                            supportingContent = { Text("Reminder to generate monthly invoices") },
                            trailingContent = {
                                Switch(
                                    checked = uiState.settings.monthlyInvoiceReminder,
                                    onCheckedChange = { viewModel.updateMonthlyInvoiceReminder(it) }
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Backup Reminder") },
                            supportingContent = { Text("Periodic reminders to backup data") },
                            trailingContent = {
                                Switch(
                                    checked = uiState.settings.backupReminder,
                                    onCheckedChange = { viewModel.updateBackupReminder(it) }
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("App Updates & News") },
                            supportingContent = { Text("Information about new features") },
                            trailingContent = {
                                Switch(
                                    checked = uiState.settings.appUpdates,
                                    onCheckedChange = { viewModel.updateAppUpdates(it) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
