package com.example.features.rent.ui.screens

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.features.rent.ui.viewmodel.PaymentViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onNavigateBack: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.actionEvent.collectLatest { success ->
            if (success) {
                onNavigateBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.paymentId == null) "Record Payment" else "Edit Payment") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.error != null) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
                
                Text("Tenant: ${uiState.tenantName} (Room ${uiState.roomNumber})", style = MaterialTheme.typography.titleMedium)
                Text("Billing Month: ${uiState.billingMonth}", style = MaterialTheme.typography.bodyMedium)
                Text("Expected Amount: ₹${uiState.expectedAmount}", style = MaterialTheme.typography.bodyMedium)

                OutlinedTextField(
                    value = uiState.amountPaid,
                    onValueChange = viewModel::onAmountPaidChanged,
                    label = { Text("Amount Paid") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.paymentDate,
                    onValueChange = viewModel::onPaymentDateChanged,
                    label = { Text("Payment Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.paymentMode,
                    onValueChange = viewModel::onPaymentModeChanged,
                    label = { Text("Payment Mode (Cash/UPI/Bank)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.transactionReference,
                    onValueChange = viewModel::onTransactionReferenceChanged,
                    label = { Text("Transaction Reference") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.remarks,
                    onValueChange = viewModel::onRemarksChanged,
                    label = { Text("Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { viewModel.savePayment() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Payment")
                }
            }
        }
    }
}
