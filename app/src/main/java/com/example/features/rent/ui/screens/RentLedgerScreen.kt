package com.example.features.rent.ui.screens

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.data.database.RentPaymentEntity
import com.example.features.rent.ui.viewmodel.RentLedgerUiState
import com.example.features.rent.ui.viewmodel.RentLedgerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentLedgerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPaymentDetails: (Int) -> Unit,
    viewModel: RentLedgerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rent Ledger") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val state = uiState) {
                is RentLedgerUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is RentLedgerUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is RentLedgerUiState.Empty -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No rent payments recorded yet.")
                    }
                }
                is RentLedgerUiState.Success -> {
                    LedgerContent(
                        state = state,
                        onSearchQueryChanged = viewModel::updateSearchQuery,
                        onStatusFilterChanged = viewModel::updateStatusFilter,
                        onMonthFilterChanged = viewModel::updateMonthFilter,
                        onPaymentClick = onNavigateToPaymentDetails
                    )
                }
            }
        }
    }
}

@Composable
fun LedgerContent(
    state: RentLedgerUiState.Success,
    onSearchQueryChanged: (String) -> Unit,
    onStatusFilterChanged: (String) -> Unit,
    onMonthFilterChanged: (String) -> Unit,
    onPaymentClick: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.filters.searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = { Text("Search Tenant or Room") },
            singleLine = true
        )
        // Simple filters (could be improved with Dropdowns)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.payments) { payment ->
                PaymentItem(payment, onClick = { onPaymentClick(payment.id) })
            }
        }
    }
}

@Composable
fun PaymentItem(payment: RentPaymentEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(payment.tenantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Room ${payment.roomNumber} - ${payment.billingMonth}", style = MaterialTheme.typography.bodyMedium)
                Text("Due: ${payment.dueDate}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${payment.amountPaid} / ₹${payment.amount}", style = MaterialTheme.typography.titleMedium)
                val statusColor = when (payment.status) {
                    "Paid" -> MaterialTheme.colorScheme.primary
                    "Pending", "Partial" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(payment.status, style = MaterialTheme.typography.labelMedium, color = statusColor)
            }
        }
    }
}
