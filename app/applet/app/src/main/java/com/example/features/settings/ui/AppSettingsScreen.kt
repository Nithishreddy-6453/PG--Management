package com.example.features.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.LocalSpacing
import com.example.features.settings.domain.model.AppTheme
import com.example.features.settings.ui.viewmodel.AppSettingsUiState
import com.example.features.settings.ui.viewmodel.AppSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Application Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is AppSettingsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is AppSettingsUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.settings.theme == AppTheme.LIGHT,
                            onClick = { viewModel.updateSettings(state.settings.copy(theme = AppTheme.LIGHT)) }
                        )
                        Text("Light", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.settings.theme == AppTheme.DARK,
                            onClick = { viewModel.updateSettings(state.settings.copy(theme = AppTheme.DARK)) }
                        )
                        Text("Dark", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.settings.theme == AppTheme.SYSTEM,
                            onClick = { viewModel.updateSettings(state.settings.copy(theme = AppTheme.SYSTEM)) }
                        )
                        Text("System Default", modifier = Modifier.padding(start = 8.dp))
                    }

                    Spacer(modifier = Modifier.height(spacing.medium))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dynamic Color (Android 12+)")
                        Switch(
                            checked = state.settings.dynamicColor,
                            onCheckedChange = { viewModel.updateSettings(state.settings.copy(dynamicColor = it)) }
                        )
                    }

                    // For DateFormat, NumberFormat, Language you could add dropdowns/menus.
                }
            }
        }
    }
}
