package com.example.features.settings.ui

import androidx.compose.runtime.collectAsState
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.LocalSpacing
import com.example.features.settings.domain.model.AppTheme
import com.example.features.settings.ui.viewmodel.AppPreferencesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPreferencesScreen(
    viewModel: AppPreferencesViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = true) {
        viewModel.messageEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Application Preferences", fontWeight = FontWeight.Bold) },
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
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            // Appearance Section
            PreferenceSection(title = "Appearance") {
                PreferenceCard {
                    Column {
                        PreferenceTitle(
                            icon = Icons.Default.Palette,
                            title = "Theme"
                        )
                        Column(Modifier.selectableGroup()) {
                            ThemeOptionRow(
                                title = "System Default",
                                selected = uiState.theme == AppTheme.SYSTEM,
                                onClick = { viewModel.updateTheme(AppTheme.SYSTEM) }
                            )
                            ThemeOptionRow(
                                title = "Light",
                                selected = uiState.theme == AppTheme.LIGHT,
                                onClick = { viewModel.updateTheme(AppTheme.LIGHT) }
                            )
                            ThemeOptionRow(
                                title = "Dark",
                                selected = uiState.theme == AppTheme.DARK,
                                onClick = { viewModel.updateTheme(AppTheme.DARK) }
                            )
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PreferenceCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ColorLens,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(spacing.medium))
                                Column {
                                    Text(
                                        text = "Dynamic Color",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Use system wallpaper colors",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.dynamicColor,
                                onCheckedChange = { viewModel.updateDynamicColor(it) }
                            )
                        }
                    }
                }
            }

            // Regional Section
            PreferenceSection(title = "Regional & Formatting") {
                PreferenceCard {
                    var dateExpanded by remember { mutableStateOf(false) }
                    val dateFormats = listOf("DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD")
                    
                    PreferenceDropdownItem(
                        icon = Icons.Default.DateRange,
                        title = "Date Format",
                        selectedValue = uiState.dateFormat,
                        expanded = dateExpanded,
                        onExpandedChange = { dateExpanded = it },
                        options = dateFormats,
                        onOptionSelected = {
                            viewModel.updateDateFormat(it)
                            dateExpanded = false
                        }
                    )
                }

                PreferenceCard {
                    var numberExpanded by remember { mutableStateOf(false) }
                    val numberFormats = listOf("Indian", "International")
                    
                    PreferenceDropdownItem(
                        icon = Icons.Default.Numbers,
                        title = "Number Format",
                        selectedValue = uiState.numberFormat,
                        expanded = numberExpanded,
                        onExpandedChange = { numberExpanded = it },
                        options = numberFormats,
                        onOptionSelected = {
                            viewModel.updateNumberFormat(it)
                            numberExpanded = false
                        }
                    )
                }

                PreferenceCard {
                    var langExpanded by remember { mutableStateOf(false) }
                    val languages = listOf("English")
                    val currentLangDisplay = if (uiState.language == "en") "English" else uiState.language
                    
                    PreferenceDropdownItem(
                        icon = Icons.Default.Language,
                        title = "Language",
                        selectedValue = currentLangDisplay,
                        expanded = langExpanded,
                        onExpandedChange = { langExpanded = it },
                        options = languages,
                        onOptionSelected = {
                            val code = if (it == "English") "en" else it
                            viewModel.updateLanguage(code)
                            langExpanded = false
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(spacing.extraLarge))
        }
    }
}

@Composable
private fun PreferenceSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = spacing.medium)
        )
        content()
    }
}

@Composable
private fun PreferenceCard(
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
private fun PreferenceTitle(
    icon: ImageVector,
    title: String
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(spacing.large)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(spacing.medium))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null // null recommended for accessibility with screen readers
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferenceDropdownItem(
    icon: ImageVector,
    title: String,
    selectedValue: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(true) }
            .padding(spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(spacing.medium))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            Text(
                text = selectedValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
            )
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
}
