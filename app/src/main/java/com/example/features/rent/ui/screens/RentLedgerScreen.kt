package com.example.features.rent.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.designsystem.AppBottomNavBar
import com.example.core.designsystem.MainTab
import com.example.core.language.rememberTranslation
import com.example.data.database.RentPaymentEntity
import com.example.features.rent.domain.model.MonthGenerationPreview
import com.example.features.rent.ui.viewmodel.RentLedgerUiState
import com.example.features.rent.ui.viewmodel.RentLedgerViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentLedgerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPaymentDetails: (Int) -> Unit,
    onNavigateToRecordPayment: (Int?, String?) -> Unit = { _, _ -> },
    onNavigate: (String) -> Unit = {},
    viewModel: RentLedgerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val titleText = rememberTranslation("Payments")
    val subtitleText = rememberTranslation("Lakshmi...")

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect((uiState as? RentLedgerUiState.Success)?.feedbackMessage) {
        val msg = (uiState as? RentLedgerUiState.Success)?.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = rememberTranslation("Back"),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.example.core.language.GlobalLanguageToggle()
                        IconButton(
                            onClick = { },
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = rememberTranslation("Notifications"),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { onNavigate(com.example.navigation.Screen.Settings.route) },
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = rememberTranslation("Settings"),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            AppBottomNavBar(
                currentTab = MainTab.PAYMENTS,
                onNavigate = onNavigate
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is RentLedgerUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF1769D1))
                    }
                }
                is RentLedgerUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is RentLedgerUiState.Success -> {
                    RentDashboardContent(
                        state = state,
                        onSearchQueryChanged = viewModel::updateSearchQuery,
                        onStatusFilterChanged = viewModel::updateStatusFilter,
                        onMonthFilterChanged = viewModel::updateMonthFilter,
                        onRequestPreview = { viewModel.requestMonthPreview() },
                        onPaymentClick = onNavigateToPaymentDetails,
                        onQuickPayClick = { payment -> onNavigateToRecordPayment(payment.tenantId, payment.billingMonth) },
                        onNavigateToRecordPayment = onNavigateToRecordPayment,
                        onNavigate = onNavigate
                    )

                    if (state.previewData != null) {
                        MonthGenerationPreviewSheet(
                            preview = state.previewData!!,
                            isGenerating = state.isGenerating,
                            onDismiss = viewModel::dismissPreview,
                            onConfirm = { month, backupPrev ->
                                viewModel.confirmAndGenerateMonth(month, backupPreviousMonth = backupPrev)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentDashboardContent(
    state: RentLedgerUiState.Success,
    onSearchQueryChanged: (String) -> Unit,
    onStatusFilterChanged: (String) -> Unit,
    onMonthFilterChanged: (String) -> Unit,
    onRequestPreview: () -> Unit,
    onPaymentClick: (Int) -> Unit,
    onQuickPayClick: (RentPaymentEntity) -> Unit,
    onNavigateToRecordPayment: (Int?, String?) -> Unit,
    onNavigate: (String) -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf("Latest First") }
    var showFilterMenu by remember { mutableStateOf(false) }

    val sortedPayments = remember(state.filteredPayments, sortOrder) {
        if (sortOrder == "Latest First") {
            state.filteredPayments.sortedByDescending { it.id }
        } else {
            state.filteredPayments.sortedBy { it.id }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Month Selection Row Card (< Month Year >)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val currentIndex = state.availableMonths.indexOf(state.filters.selectedMonth)
                            if (currentIndex > 0) {
                                onMonthFilterChanged(state.availableMonths[currentIndex - 1])
                            } else if (state.availableMonths.isNotEmpty()) {
                                onMonthFilterChanged(state.availableMonths.last())
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = rememberTranslation("Previous Month"),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    var monthDropdownExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent,
                            modifier = Modifier.clickable { monthDropdownExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = Color(0xFF1769D1),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = state.filters.selectedMonth,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = monthDropdownExpanded,
                            onDismissRequest = { monthDropdownExpanded = false }
                        ) {
                            state.availableMonths.forEach { month ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = month,
                                            fontWeight = if (month == state.filters.selectedMonth) FontWeight.Bold else FontWeight.Normal,
                                            color = if (month == state.filters.selectedMonth) Color(0xFF1769D1) else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        onMonthFilterChanged(month)
                                        monthDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val currentIndex = state.availableMonths.indexOf(state.filters.selectedMonth)
                            if (currentIndex >= 0 && currentIndex < state.availableMonths.size - 1) {
                                onMonthFilterChanged(state.availableMonths[currentIndex + 1])
                            } else if (state.availableMonths.isNotEmpty()) {
                                onMonthFilterChanged(state.availableMonths.first())
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = rememberTranslation("Next Month"),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 2. Rent Overview Card (Stitch faithful layout)
        item {
            val stats = state.monthlyStats
            val expected = stats.totalExpectedRent
            val collected = stats.totalPaid
            val outstanding = stats.totalOutstanding
            val percentage = stats.collectionPercentage

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header: Two-line title + "View Reports >"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${state.filters.selectedMonth.substringBefore(" ")} Rent",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = rememberTranslation("Overview"),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onNavigate(com.example.navigation.Screen.ReportsRent.route) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = Color(0xFF1769D1),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = rememberTranslation("View Reports"),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1769D1)
                                )
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFF1769D1),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Main row: Total Due left (in light blue box), Collected & Outstanding right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Total Rent Due inside a light-blue rectangular container
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                            modifier = Modifier
                                .weight(1.1f)
                                .height(104.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.Start
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFDBEAFE)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF1769D1),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = rememberTranslation("Total Rent Due"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF1E40AF),
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "₹${formatCurrency(expected)}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        // Right: Stacked Collected & Outstanding cards
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Rent Collected
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF16803C)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("₹", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text(
                                            text = rememberTranslation("Rent Collected"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF166534),
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "₹${formatCurrency(collected)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF16803C)
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Outstanding
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFDC2626)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PriorityHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                    Column {
                                        Text(
                                            text = rememberTranslation("Outstanding"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF991B1B),
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "₹${formatCurrency(outstanding)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFDC2626)
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Progress bar text & indicator
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "₹${formatCurrency(collected)} of ₹${formatCurrency(expected)} collected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${Math.round(percentage)}%",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (percentage / 100).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF16803C),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // 3. Three Status Cards Row (Fully Paid, Partially Paid, Unpaid / Overdue)
        item {
            val stats = state.monthlyStats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Fully Paid
                StatusSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = rememberTranslation("Fully Paid"),
                    countValue = stats.paidTenantsCount.toString(),
                    icon = Icons.Default.Check,
                    cardBg = Color(0xFFEDFCF3),
                    cardBorder = Color(0xFFB8F5D0),
                    iconCircleBg = Color(0xFF10B96F),
                    titleColor = Color(0xFF087F4F)
                )
                // Partially Paid
                StatusSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = rememberTranslation("Partially Paid"),
                    countValue = stats.partiallyPaidTenantsCount.toString(),
                    icon = Icons.Default.Schedule,
                    cardBg = Color(0xFFFFFBEB),
                    cardBorder = Color(0xFFFDE047),
                    iconCircleBg = Color(0xFFF59E0B),
                    titleColor = Color(0xFFB45309)
                )
                // Unpaid / Overdue
                StatusSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = rememberTranslation("Unpaid /\nOverdue"),
                    countValue = stats.pendingTenantsCount.toString(),
                    icon = Icons.Default.Close,
                    cardBg = Color(0xFFFFF1F2),
                    cardBorder = Color(0xFFFECDD3),
                    iconCircleBg = Color(0xFFF43F5E),
                    titleColor = Color(0xFFE11D48)
                )
            }
        }

        // 4. Three Quick Action Buttons (+ Collect, Reminder, Reports)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onNavigateToRecordPayment(null, state.filters.selectedMonth) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769D1)),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(rememberTranslation("Collect"), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }

                Button(
                    onClick = { },
                    modifier = Modifier
                        .weight(1.1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(rememberTranslation("Reminder"), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }

                Button(
                    onClick = { onNavigate(com.example.navigation.Screen.ReportsRent.route) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(rememberTranslation("Reports"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }
        }

        // 5. Tenant Rent Ledger Section Header & Search
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rememberTranslation("Tenant Rent Ledger"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = Color(0xFF1769D1)
                        )
                    }
                }

                OutlinedTextField(
                    value = state.filters.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = {
                        Text(
                            text = rememberTranslation("Search tenant, room or phone..."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (state.filters.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = Color(0xFF1769D1),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )
            }
        }

        // 6. Horizontal Status Filter Chips (All, Paid, Partial, Unpaid, Overdue)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf("All", "Paid", "Partial", "Unpaid", "Overdue")
                items(filters) { filter ->
                    val isSelected = when (filter) {
                        "Partial" -> state.filters.selectedStatus.equals("Partial", true) || state.filters.selectedStatus.equals("Partially Paid", true)
                        "Unpaid" -> state.filters.selectedStatus.equals("Pending", true) || state.filters.selectedStatus.equals("Unpaid", true)
                        else -> state.filters.selectedStatus.equals(filter, true)
                    }
                    val count = when (filter) {
                        "All" -> state.allPayments.size
                        "Paid" -> state.monthlyStats.paidTenantsCount
                        "Partial" -> state.monthlyStats.partiallyPaidTenantsCount
                        "Unpaid" -> state.monthlyStats.pendingTenantsCount
                        "Overdue" -> state.allPayments.count { it.status.equals("Overdue", true) }
                        else -> 0
                    }
                    val displayLabel = when (filter) {
                        "All" -> "All (${state.allPayments.size})"
                        "Paid" -> "Paid (${state.monthlyStats.paidTenantsCount})"
                        "Partial" -> "Partial (${state.monthlyStats.partiallyPaidTenantsCount})"
                        "Unpaid" -> "Unpaid ($count)"
                        "Overdue" -> "Overdue ($count)"
                        else -> "$filter ($count)"
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { onStatusFilterChanged(if (filter == "Unpaid") "Pending" else filter) },
                        label = { Text(displayLabel, fontWeight = FontWeight.Medium) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1769D1),
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color(0xFF1769D1) else MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = Color(0xFF1769D1)
                        )
                    )
                }
            }
        }

        // 7. Tenant Ledger Payment Cards
        if (sortedPayments.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = rememberTranslation("No payment records found"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(sortedPayments, key = { it.id }) { payment ->
                TenantPaymentCard(
                    payment = payment,
                    onClick = { onPaymentClick(payment.id) },
                    onQuickPayClick = { onQuickPayClick(payment) }
                )
            }
        }

        // 8. Bottom Footer Cards (View all tenants, Follow-ups, Payment history)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // View all tenants link card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(com.example.navigation.Screen.Tenants.route) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = rememberTranslation("View all tenants"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1769D1)
                            )
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(0xFF1769D1)
                        )
                    }
                }

                // Follow-ups card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF1769D1), modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = rememberTranslation("Follow-ups"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = rememberTranslation("7 tenants need a rent follow-up"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(rememberTranslation("View unpaid"), color = Color(0xFF1769D1), fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769D1))
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(rememberTranslation("Send reminder"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Payment history & receipts card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(com.example.navigation.Screen.RentLedger.route) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF1769D1), modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = rememberTranslation("Payment history & receipts"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    countValue: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cardBg: Color,
    cardBorder: Color,
    iconCircleBg: Color,
    titleColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier.height(116.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (!title.contains("\n")) "$title\n" else title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = titleColor,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(iconCircleBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = countValue,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF172033)
                    ),
                    maxLines = 1
                )
            }
            Text(
                text = rememberTranslation("tenants"),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TenantPaymentCard(
    payment: RentPaymentEntity,
    onClick: () -> Unit,
    onQuickPayClick: () -> Unit
) {
    val status = payment.status.lowercase()
    val isPaid = status == "paid" || status == "overpaid"
    val isPartial = status == "partial" || status == "partially paid"
    val isOverdue = status == "overdue" || status == "pending" || status == "unpaid"

    data class TenantCardStyle(
        val cardBg: Color,
        val cardBorder: Color,
        val avatarBg: Color,
        val primaryColor: Color,
        val badgeBg: Color,
        val badgeText: String,
        val badgeColor: Color
    )

    val style = when {
        isPaid -> TenantCardStyle(
            cardBg = Color(0xFFEDFCF3),
            cardBorder = Color(0xFFB8F5D0),
            avatarBg = Color(0xFFDCFCE7),
            primaryColor = Color(0xFF16A05D),
            badgeBg = Color(0xFFE0F5E8),
            badgeText = "Paid",
            badgeColor = Color(0xFF187847)
        )
        isPartial -> TenantCardStyle(
            cardBg = Color(0xFFFFFCEB),
            cardBorder = Color(0xFFF8E88B),
            avatarBg = Color(0xFFFFF1C2),
            primaryColor = Color(0xFFA9570B),
            badgeBg = Color(0xFFFFF1C2),
            badgeText = "Partial",
            badgeColor = Color(0xFFA9570B)
        )
        else -> TenantCardStyle(
            cardBg = Color(0xFFFFF5F5),
            cardBorder = Color(0xFFFADADA),
            avatarBg = Color(0xFFFDE2E2),
            primaryColor = Color(0xFFB42323),
            badgeBg = Color(0xFFFDE2E2),
            badgeText = if (status.contains("overdue")) "Overdue" else "Pending",
            badgeColor = Color(0xFFB42323)
        )
    }

    val initial = payment.tenantName.take(1).uppercase()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = style.cardBg),
        border = BorderStroke(1.dp, style.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT SIDE: Circular Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(style.avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = style.primaryColor
                    )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // MIDDLE: Tenant Name, Room, Amount & Dates
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = payment.tenantName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF172033)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val roomBed = listOf(
                    "${rememberTranslation("Room")} ${payment.roomNumber}",
                    "${rememberTranslation("Bed")} B"
                ).joinToString(" • ")

                Text(
                    text = roomBed,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "₹${formatCurrency(payment.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = style.primaryColor
                    )
                )

                val dateInfo = if (isPaid) "${rememberTranslation("Paid on")} ${payment.paymentDate ?: payment.billingMonth}" else "${rememberTranslation("Due on")} ${payment.dueDate}"
                Text(
                    text = dateInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // RIGHT SIDE: Status Badge & Action / Arrow
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = style.badgeBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isPaid) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = style.badgeColor, modifier = Modifier.size(12.dp))
                        } else if (isOverdue) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = style.badgeColor, modifier = Modifier.size(12.dp))
                        }
                        Text(
                            text = rememberTranslation(style.badgeText),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = style.badgeColor
                            )
                        )
                    }
                }

                if (!isPaid) {
                    Button(
                        onClick = onQuickPayClick,
                        colors = ButtonDefaults.buttonColors(containerColor = style.badgeBg),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = rememberTranslation("Collect"),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = style.badgeColor)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthGenerationPreviewSheet(
    preview: MonthGenerationPreview,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit
) {
    var backupPrevious by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "${rememberTranslation("Generate Invoices")}: ${preview.billingMonth}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "${rememberTranslation("Total Tenants")}: ${preview.totalApplicableTenants} (${preview.fullMonthTenants} ${rememberTranslation("Full")}, ${preview.proratedTenants} ${rememberTranslation("Prorated")})\n${rememberTranslation("Expected Revenue")}: ₹${preview.expectedTotalRent}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = backupPrevious,
                    onCheckedChange = { backupPrevious = it }
                )
                Text(
                    text = rememberTranslation("Backup / Carry forward unpaid dues"),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = { onConfirm(preview.billingMonth, backupPrevious) },
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769D1))
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(rememberTranslation("Generating..."))
                } else {
                    Text(rememberTranslation("Confirm & Generate Invoices"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun formatCurrency(amount: Double): String {
    return try {
        java.text.NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount)
    } catch (e: Exception) {
        amount.toInt().toString()
    }
}
