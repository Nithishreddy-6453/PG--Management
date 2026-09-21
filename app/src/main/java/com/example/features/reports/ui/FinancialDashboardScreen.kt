package com.example.features.reports.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.LocalSpacing
import com.example.features.reports.ui.viewmodel.ReportsUiState
import com.example.features.reports.ui.viewmodel.ReportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialDashboardScreen(
    viewModel: ReportsViewModel,
    onBackClick: () -> Unit,
    onNavigateToRevenue: () -> Unit,
    onNavigateToExpense: () -> Unit,
    onNavigateToOccupancy: () -> Unit,
    onNavigateToRent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.testTag("reports_top_bar")
            )
        },
        modifier = modifier.testTag("reports_screen_container")
    ) { innerPadding ->
        when (val state = uiState) {
            is ReportsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ReportsUiState.Success -> {
                val metrics = state.metrics
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    // Header Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.large),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Net Profit", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "₹${String.format("%,.0f", metrics.netProfit)}",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (metrics.netProfit >= 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Key Metrics Grid
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                        MetricCard(title = "Total Revenue", value = "₹${String.format("%,.0f", metrics.totalRevenue)}", modifier = Modifier.weight(1f))
                        MetricCard(title = "Total Expenses", value = "₹${String.format("%,.0f", metrics.totalExpenses)}", modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                        MetricCard(title = "Outstanding Rent", value = "₹${String.format("%,.0f", metrics.outstandingRent)}", modifier = Modifier.weight(1f), isError = metrics.outstandingRent > 0)
                        MetricCard(title = "Collection Rate", value = "${(metrics.collectionRate * 100).toInt()}%", modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                        MetricCard(title = "Occupancy Rate", value = "${(metrics.occupancyRate * 100).toInt()}%", modifier = Modifier.weight(1f))
                        MetricCard(title = "Vacancy Rate", value = "${(metrics.vacancyRate * 100).toInt()}%", modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(spacing.medium))

                    // Report Navigation Items
                    Text("Detailed Reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    ReportNavigationCard(title = "Revenue Analytics", description = "Income trends and sources", onClick = onNavigateToRevenue)
                    ReportNavigationCard(title = "Expense Reports", description = "Spending breakdown", onClick = onNavigateToExpense)
                    ReportNavigationCard(title = "Rent Analytics", description = "Collections and outstandings", onClick = onNavigateToRent)
                    ReportNavigationCard(title = "Occupancy Analytics", description = "Room and bed utilization", onClick = onNavigateToOccupancy)
                }
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    val spacing = LocalSpacing.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(spacing.large),
            horizontalAlignment = Alignment.Start
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ReportNavigationCard(title: String, description: String, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
