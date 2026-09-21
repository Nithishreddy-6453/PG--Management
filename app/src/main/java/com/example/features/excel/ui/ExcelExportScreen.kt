package com.example.features.excel.ui

import android.app.DatePickerDialog
import android.content.Context
import android.widget.DatePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.designsystem.LocalSpacing
import com.example.features.excel.domain.model.ExportPeriodType
import com.example.features.excel.ui.viewmodel.ExcelExportViewModel
import com.example.features.excel.ui.viewmodel.ExportUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelExportScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExcelExportViewModel = hiltViewModel()
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.shareIntentEvent.collect { intent ->
            context.startActivity(intent)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is ExportUiState.Success) {
            snackbarHostState.showSnackbar((uiState as ExportUiState.Success).message)
        } else if (uiState is ExportUiState.Error) {
            snackbarHostState.showSnackbar((uiState as ExportUiState.Error).message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Data to Excel", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize().testTag("excel_export_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Comprehensive Excel Workbook",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Generates a complete 5-sheet report including Summary, Tenants, Rent Payments, Expenses, and Rooms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Period Selection Section
            Text(
                text = "Select Export Period",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            PeriodOptionCard(
                title = "This Month",
                subtitle = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()),
                isSelected = config.periodType == ExportPeriodType.THIS_MONTH,
                onClick = { viewModel.setPeriodType(ExportPeriodType.THIS_MONTH) }
            )

            PeriodOptionCard(
                title = "Previous Month",
                subtitle = run {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.MONTH, -1)
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
                },
                isSelected = config.periodType == ExportPeriodType.PREVIOUS_MONTH,
                onClick = { viewModel.setPeriodType(ExportPeriodType.PREVIOUS_MONTH) }
            )

            PeriodOptionCard(
                title = "Custom Date Range",
                subtitle = "Choose specific start and end dates",
                isSelected = config.periodType == ExportPeriodType.CUSTOM_RANGE,
                onClick = { viewModel.setPeriodType(ExportPeriodType.CUSTOM_RANGE) }
            )

            if (config.periodType == ExportPeriodType.CUSTOM_RANGE) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Custom Range Dates (YYYY-MM-DD)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = config.customStartDate,
                                onValueChange = { viewModel.setCustomStartDate(it) },
                                label = { Text("Start Date") },
                                placeholder = { Text("2026-08-01") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = config.customEndDate,
                                onValueChange = { viewModel.setCustomEndDate(it) },
                                label = { Text("End Date") },
                                placeholder = { Text("2026-09-20") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            PeriodOptionCard(
                title = "All Data",
                subtitle = "Export all historical tenant and transaction records",
                isSelected = config.periodType == ExportPeriodType.ALL_DATA,
                onClick = { viewModel.setPeriodType(ExportPeriodType.ALL_DATA) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sheet Contents Breakdown
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Workbook Sheets Included",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    SheetItem(name = "1. Summary", desc = "PG details, occupancy, revenue, expenses, net profit")
                    SheetItem(name = "2. Tenants", desc = "Name, room, phone, monthly rent, join date, status")
                    SheetItem(name = "3. Rent / Payments", desc = "Tenant payments, billing month, paid, pending")
                    SheetItem(name = "4. Expenses", desc = "Date, category, description, expense amount")
                    SheetItem(name = "5. Rooms", desc = "Room numbers, total & occupied beds, rent rates")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Export Button
            Button(
                onClick = { viewModel.exportExcel() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("export_excel_action_button"),
                enabled = uiState !is ExportUiState.Exporting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState is ExportUiState.Exporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Generating Excel Report...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export to Excel (.xlsx)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun PeriodOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SheetItem(name: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
