package com.example.features.rent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.language.rememberTranslation
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
    val previewData by viewModel.previewData.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rememberTranslation("Rent Dashboard"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = rememberTranslation("Back"))
                    }
                },
                actions = {
                    com.example.core.language.GlobalLanguageToggle(
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(onClick = { viewModel.requestMonthPreview() }) {
                        Icon(Icons.Default.AutoMode, contentDescription = rememberTranslation("Generate Month Rent"))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToRecordPayment,
                containerColor = Color(0xFF1769D1),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = rememberTranslation("Add Payment"))
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
                            DashboardRentSummaryCards(summary = state.summary)
                        }

                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = rememberTranslation("Generate Monthly Rent"),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E40AF)
                                        )
                                    )
                                    Text(
                                        text = rememberTranslation("Review prorated and full-month tenant calculations before generating invoices."),
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF3B82F6))
                                    )
                                    Button(
                                        onClick = { viewModel.requestMonthPreview() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769D1)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.AutoMode, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(rememberTranslation("Generate Month Rent"), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = onNavigateToLedger,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(rememberTranslation("View Master Rent Ledger"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Month Generation Preview Sheet
            if (previewData != null) {
                MonthGenerationPreviewSheet(
                    preview = previewData!!,
                    isGenerating = invoiceState == "Generating...",
                    onDismiss = viewModel::dismissPreview,
                    onConfirm = { month, backupPrev ->
                        viewModel.confirmAndGenerateMonth(month, backupPreviousMonth = backupPrev)
                    }
                )
            }

            if (invoiceState != null && invoiceState != "Generating...") {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearInvoiceGenerationState() }) {
                            Text(rememberTranslation("Dismiss"))
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
fun DashboardRentSummaryCards(summary: RentSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryItemCard(
                title = rememberTranslation("Expected Rent"),
                amount = "₹${summary.expectedMonthlyRent}",
                modifier = Modifier.weight(1f),
                titleColor = Color(0xFF1E293B)
            )
            SummaryItemCard(
                title = rememberTranslation("Collected"),
                amount = "₹${summary.collectedRent}",
                modifier = Modifier.weight(1f),
                titleColor = Color(0xFF16803C)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryItemCard(
                title = rememberTranslation("Pending"),
                amount = "₹${summary.pendingRent}",
                modifier = Modifier.weight(1f),
                titleColor = Color(0xFFC62828)
            )
            SummaryItemCard(
                title = rememberTranslation("Overdue"),
                amount = "₹${summary.overdueRent}",
                modifier = Modifier.weight(1f),
                titleColor = Color(0xFFB86B00)
            )
        }
        val percentage = if (summary.expectedMonthlyRent > 0) {
            ((summary.collectedRent / summary.expectedMonthlyRent) * 100.0).coerceIn(0.0, 100.0)
        } else 0.0
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(rememberTranslation("Collection Percentage"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${Math.round(percentage)}%", fontWeight = FontWeight.Bold, color = Color(0xFF1769D1))
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (percentage / 100).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Color(0xFF1769D1),
                    trackColor = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

@Composable
fun SummaryItemCard(
    title: String,
    amount: String,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
            )
        }
    }
}
