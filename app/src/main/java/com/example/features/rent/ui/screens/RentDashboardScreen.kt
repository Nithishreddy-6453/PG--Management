package com.example.features.rent.ui.screens

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.designsystem.PgTheme
import com.example.features.rent.domain.repository.RentSummary
import com.example.features.rent.ui.viewmodel.RentDashboardUiState
import com.example.features.rent.ui.viewmodel.RentDashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToRecordPayment: () -> Unit,
    viewModel: RentDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val invoiceState by viewModel.invoiceGenerationState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rent Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.generateInvoices() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Generate Invoices")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToRecordPayment) {
                Icon(Icons.Filled.Add, contentDescription = "Record Payment")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val state = uiState) {
                is RentDashboardUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is RentDashboardUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is RentDashboardUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            RentSummaryCards(summary = state.summary)
                        }
                        item {
                            Button(
                                onClick = onNavigateToLedger,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text("View Master Rent Ledger")
                            }
                        }
                    }
                }
            }

            if (invoiceState != null) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearInvoiceGenerationState() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(invoiceState ?: "")
                }
            }
        }
    }
}

@Composable
fun RentSummaryCards(summary: RentSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard(
                title = "Expected Rent",
                amount = "₹${summary.expectedMonthlyRent}",
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Collected",
                amount = "₹${summary.collectedRent}",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard(
                title = "Pending",
                amount = "₹${summary.pendingRent}",
                modifier = Modifier.weight(1f),
                amountColor = MaterialTheme.colorScheme.error
            )
            SummaryCard(
                title = "Overdue",
                amount = "₹${summary.overdueRent}",
                modifier = Modifier.weight(1f),
                amountColor = MaterialTheme.colorScheme.error
            )
        }
        val percentage = if (summary.expectedMonthlyRent > 0) {
            (summary.collectedRent / summary.expectedMonthlyRent) * 100
        } else 0.0
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Collection Percentage", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (percentage / 100).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("${String.format("%.1f", percentage)}%", style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        SummaryCard(
            title = "Today's Collections",
            amount = "₹${summary.todaysCollections}",
            modifier = Modifier.fillMaxWidth(),
            amountColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SummaryCard(
    title: String,
    amount: String,
    modifier: Modifier = Modifier,
    amountColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(amount, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = amountColor)
        }
    }
}
