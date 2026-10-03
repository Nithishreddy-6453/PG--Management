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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.features.rent.domain.model.TenantMonthPreview
import com.example.features.rent.domain.util.RentBillingEngine
import com.example.features.rent.ui.viewmodel.RentLedgerUiState
import com.example.features.rent.ui.viewmodel.RentLedgerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentLedgerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPaymentDetails: (Int) -> Unit,
    onNavigateToRecordPayment: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: RentLedgerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val titleText = rememberTranslation("Rent Ledger")
    val subtitleText = rememberTranslation("Track all rent payments")

    var showQuickPayDialog by remember { mutableStateOf<RentPaymentEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Display feedback message
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
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = rememberTranslation("Back"),
                                tint = Color(0xFF1E293B)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            )
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B)
                                )
                            )
                        }
                    }

                    com.example.core.language.GlobalLanguageToggle()
                }
            }
        },
        bottomBar = {
            AppBottomNavBar(
                currentTab = MainTab.PAYMENTS,
                onNavigate = onNavigate
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToRecordPayment,
                containerColor = Color(0xFF1769D1),
                contentColor = Color.White,
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = rememberTranslation("Add Payment"),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        containerColor = Color(0xFFF8FAFC)
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
                    LedgerContent(
                        state = state,
                        onSearchQueryChanged = viewModel::updateSearchQuery,
                        onStatusFilterChanged = viewModel::updateStatusFilter,
                        onMonthFilterChanged = viewModel::updateMonthFilter,
                        onRequestPreview = viewModel::requestMonthPreview,
                        onPaymentClick = onNavigateToPaymentDetails,
                        onQuickPayClick = { showQuickPayDialog = it }
                    )

                    // Month Generation Preview Modal Bottom Sheet
                    if (state.previewData != null) {
                        MonthGenerationPreviewSheet(
                            preview = state.previewData,
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

    // Quick Payment Recording Dialog
    if (showQuickPayDialog != null) {
        val payment = showQuickPayDialog!!
        val remaining = (payment.amount - payment.amountPaid).coerceAtLeast(0.0)
        var enteredAmount by remember { mutableStateOf(if (remaining > 0) remaining.toString() else payment.amount.toString()) }
        var selectedMode by remember { mutableStateOf("UPI") }

        AlertDialog(
            onDismissRequest = { showQuickPayDialog = null },
            title = {
                Text(
                    text = "${rememberTranslation("Record Payment")}: ${payment.tenantName}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${rememberTranslation("Billing Month")}: ${payment.billingMonth}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "${rememberTranslation("Expected Rent")}: ₹${payment.amount}  |  ${rememberTranslation("Already Paid")}: ₹${payment.amountPaid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569)
                    )

                    OutlinedTextField(
                        value = enteredAmount,
                        onValueChange = { enteredAmount = it },
                        label = { Text(rememberTranslation("Amount to Pay (₹)")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(rememberTranslation("Payment Mode"), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("UPI", "Cash", "Bank Transfer").forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(mode) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = enteredAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            viewModel.recordQuickPayment(payment.id, amt, selectedMode)
                            showQuickPayDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16803C))
                ) {
                    Text(rememberTranslation("Confirm Payment"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickPayDialog = null }) {
                    Text(rememberTranslation("Cancel"))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerContent(
    state: RentLedgerUiState.Success,
    onSearchQueryChanged: (String) -> Unit,
    onStatusFilterChanged: (String) -> Unit,
    onMonthFilterChanged: (String) -> Unit,
    onRequestPreview: (String?) -> Unit,
    onPaymentClick: (Int) -> Unit,
    onQuickPayClick: (RentPaymentEntity) -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf("Latest First") }
    var showFilterMenu by remember { mutableStateOf(false) }

    // Sort payments based on sortOrder
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Month Selector & Generate Month Action Bar
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Month Dropdown
                    var monthDropdownExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.clickable { monthDropdownExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = Color(0xFF1769D1),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = state.filters.selectedMonth,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
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
                                            color = if (month == state.filters.selectedMonth) Color(0xFF1769D1) else Color(0xFF1E293B)
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

                    // Generate New Month Action Button
                    Button(
                        onClick = { onRequestPreview(state.filters.selectedMonth) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1769D1),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoMode,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = rememberTranslation("Generate Month"),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // 2. Un-generated Month Warning Banner
        if (!state.isSelectedMonthGenerated && state.filters.selectedMonth != "All Months") {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFB86B00),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "${rememberTranslation("Rent for")} ${state.filters.selectedMonth} ${rememberTranslation("has not been generated.")}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                            )
                        }

                        Text(
                            text = rememberTranslation("Generate rent records for all active and incoming tenants for this calendar month. Prorated rent is automatically calculated for mid-month tenants."),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFB45309)
                            )
                        )

                        Button(
                            onClick = { onRequestPreview(state.filters.selectedMonth) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB86B00)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚡ ${rememberTranslation("Generate")} ${state.filters.selectedMonth} ${rememberTranslation("Rent")}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. Search + Status Filters Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.filters.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = {
                        Text(
                            text = rememberTranslation("Search tenants by name or room..."),
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF64748B)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF1769D1),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { showFilterMenu = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = Color(0xFF1E293B)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(rememberTranslation("All Statuses")) },
                        onClick = {
                            onStatusFilterChanged("All")
                            showFilterMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(rememberTranslation("Paid Only")) },
                        onClick = {
                            onStatusFilterChanged("Paid")
                            showFilterMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(rememberTranslation("Partially Paid")) },
                        onClick = {
                            onStatusFilterChanged("Partial")
                            showFilterMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(rememberTranslation("Pending Only")) },
                        onClick = {
                            onStatusFilterChanged("Pending")
                            showFilterMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(rememberTranslation("Overdue Only")) },
                        onClick = {
                            onStatusFilterChanged("Overdue")
                            showFilterMenu = false
                        }
                    )
                }
            }
        }

        // 4. Status Filter Chips Row
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf("All", "Paid", "Partial", "Pending", "Overdue")
                items(filters) { filter ->
                    val isSelected = when (filter) {
                        "Partial" -> state.filters.selectedStatus.equals("Partial", true) || state.filters.selectedStatus.equals("Partially Paid", true)
                        else -> state.filters.selectedStatus.equals(filter, true)
                    }
                    val displayLabel = when (filter) {
                        "Partial" -> rememberTranslation("Partially Paid")
                        else -> rememberTranslation(filter)
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { onStatusFilterChanged(filter) },
                        label = { Text(displayLabel) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1769D1),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF475569)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color(0xFF1769D1) else Color(0xFFE2E8F0),
                            selectedBorderColor = Color(0xFF1769D1)
                        )
                    )
                }
            }
        }

        // 5. Summary Metrics Cards (4 compact cards in horizontal row)
        item {
            val stats = state.monthlyStats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 1: Collected (Green)
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = Color(0xFFECFDF5),
                    borderColor = Color(0xFFD1FAE5),
                    icon = Icons.Default.AttachMoney,
                    iconTint = Color(0xFF16803C),
                    title = "₹${stats.totalPaid}",
                    subtitle = rememberTranslation("Collected"),
                    caption = "${stats.paidTenantsCount} " + rememberTranslation("paid"),
                    captionColor = Color(0xFF16803C)
                )

                // Card 2: Pending / Outstanding (Red/Amber)
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = Color(0xFFFEF2F2),
                    borderColor = Color(0xFFFEE2E2),
                    icon = Icons.Default.HourglassEmpty,
                    iconTint = Color(0xFFC62828),
                    title = "₹${stats.totalOutstanding}",
                    subtitle = rememberTranslation("Pending"),
                    caption = "${stats.pendingTenantsCount + stats.partiallyPaidTenantsCount} " + rememberTranslation("dues"),
                    captionColor = Color(0xFFC62828)
                )

                // Card 3: Total Expected (Blue)
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = Color(0xFFEFF6FF),
                    borderColor = Color(0xFFDBEAFE),
                    icon = Icons.Default.PieChart,
                    iconTint = Color(0xFF1769D1),
                    title = "₹${stats.totalExpectedRent}",
                    subtitle = rememberTranslation("Expected"),
                    caption = "${stats.collectionPercentage}% " + rememberTranslation("collected"),
                    captionColor = Color(0xFF1769D1)
                )

                // Card 4: Tenants (Purple)
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = Color(0xFFF5F3FF),
                    borderColor = Color(0xFFEDE9FE),
                    icon = Icons.Default.Group,
                    iconTint = Color(0xFF6D28D9),
                    title = "${stats.totalTenantsCount}",
                    subtitle = rememberTranslation("Tenants"),
                    caption = state.filters.selectedMonth.let { if (it == "All Months") rememberTranslation("All") else it },
                    captionColor = Color(0xFF6D28D9)
                )
            }
        }

        // 6. Payment Records Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${rememberTranslation("Payment Records")} (${sortedPayments.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                )

                Box {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.clickable { sortExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = rememberTranslation(sortOrder),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B)
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(rememberTranslation("Latest First")) },
                            onClick = {
                                sortOrder = "Latest First"
                                sortExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(rememberTranslation("Oldest First")) },
                            onClick = {
                                sortOrder = "Oldest First"
                                sortExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 7. Payment Records List or Empty State
        if (sortedPayments.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = rememberTranslation("No rent payment records found"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )
                        )
                        Text(
                            text = rememberTranslation("Generate the month or tap + Add Payment to record rent."),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }
        } else {
            items(sortedPayments, key = { it.id }) { payment ->
                PaymentRecordCard(
                    payment = payment,
                    onClick = { onPaymentClick(payment.id) },
                    onQuickPay = { onQuickPayClick(payment) }
                )
            }
        }

        // Bottom spacing for FAB
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    borderColor: Color,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    caption: String,
    captionColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1E293B)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = captionColor
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PaymentRecordCard(
    payment: RentPaymentEntity,
    onClick: () -> Unit,
    onQuickPay: () -> Unit
) {
    val initial = payment.tenantName.firstOrNull()?.uppercaseChar()?.toString() ?: "T"
    val remaining = (payment.amount - payment.amountPaid).coerceAtLeast(0.0)

    val (badgeBg, badgeText, statusLabel) = when {
        payment.status.equals("Paid", true) || payment.status.equals("Overpaid", true) ->
            Triple(Color(0xFFECFDF5), Color(0xFF16803C), rememberTranslation("Paid"))
        payment.status.equals("Partially Paid", true) || payment.status.equals("Partial", true) ->
            Triple(Color(0xFFFFFBEB), Color(0xFFB86B00), rememberTranslation("Partially Paid"))
        payment.status.equals("Overdue", true) ->
            Triple(Color(0xFFFEF2F2), Color(0xFFC62828), rememberTranslation("Overdue"))
        else ->
            Triple(Color(0xFFEFF6FF), Color(0xFF1769D1), rememberTranslation("Pending"))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEBF3FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1769D1)
                            )
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = payment.tenantName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Room ${payment.roomNumber}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text("•", color = Color(0xFFCBD5E1))
                            Text(
                                text = payment.billingMonth,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B)
                                )
                            )
                        }
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeBg,
                    border = BorderStroke(1.dp, badgeText.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = badgeText
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            // Amounts Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = rememberTranslation("Expected Rent"),
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                    )
                    Text(
                        text = "₹${payment.amount}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    )
                }

                Column {
                    Text(
                        text = rememberTranslation("Paid"),
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                    )
                    Text(
                        text = "₹${payment.amountPaid}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16803C)
                        )
                    )
                }

                Column {
                    Text(
                        text = rememberTranslation("Remaining"),
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                    )
                    Text(
                        text = "₹$remaining",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (remaining > 0) Color(0xFFC62828) else Color(0xFF16803C)
                        )
                    )
                }

                if (!payment.status.equals("Paid", true)) {
                    Button(
                        onClick = onQuickPay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769D1)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = rememberTranslation("Pay"),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
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
    val previousMonth = remember(preview.billingMonth) {
        RentBillingEngine.getPreviousBillingMonth(preview.billingMonth)
    }
    var backupPreviousMonthToExcel by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${rememberTranslation("Generate Rent")}: ${preview.billingMonth}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    )
                    Text(
                        text = "${preview.propertyName} • ${preview.daysInMonth} ${rememberTranslation("days in month")}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Already Generated Info Banner
            if (preview.isAlreadyGenerated) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1769D1), modifier = Modifier.size(20.dp))
                        Text(
                            text = "${preview.billingMonth} ${rememberTranslation("rent has already been generated")} (${preview.existingRecordsCount} ${rememberTranslation("records existing")}). ${rememberTranslation("Duplicate records will not be created.")}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF1E40AF))
                        )
                    }
                }
            }

            // Overview Metrics Grid
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rememberTranslation("Total Applicable Tenants"), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF475569))
                        Text("${preview.totalApplicableTenants}", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rememberTranslation("Full-Month Tenants"), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF475569))
                        Text("${preview.fullMonthTenants}", fontWeight = FontWeight.SemiBold, color = Color(0xFF16803C))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rememberTranslation("Prorated / Mid-Month Tenants"), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF475569))
                        Text("${preview.proratedTenants}", fontWeight = FontWeight.SemiBold, color = Color(0xFFB86B00))
                    }
                    if (preview.leavingTenants > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(rememberTranslation("Leaving During Month"), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF475569))
                            Text("${preview.leavingTenants}", fontWeight = FontWeight.SemiBold, color = Color(0xFFC62828))
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rememberTranslation("Expected Total Rent"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                        Text("₹${preview.expectedTotalRent}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1769D1))
                    }
                }
            }

            // Warning Notice
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                    Text(
                        text = rememberTranslation("Previous month records will not be modified or deleted."),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF991B1B)
                        )
                    )
                }
            }

            // Previous Month Excel Backup Checkbox Option
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (backupPreviousMonthToExcel) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, if (backupPreviousMonthToExcel) Color(0xFFBBF7D0) else Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { backupPreviousMonthToExcel = !backupPreviousMonthToExcel }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Checkbox(
                        checked = backupPreviousMonthToExcel,
                        onCheckedChange = { backupPreviousMonthToExcel = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF16803C))
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = rememberTranslation("Backup previous month's rent data to Excel"),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                        )
                        Text(
                            text = if (backupPreviousMonthToExcel) {
                                "$previousMonth ${rememberTranslation("will be backed up to Google Drive before")} ${preview.billingMonth} ${rememberTranslation("is generated.")}"
                            } else {
                                rememberTranslation("Previous month backup skipped.")
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (backupPreviousMonthToExcel) Color(0xFF15803D) else Color(0xFF64748B)
                            )
                        )
                    }
                }
            }

            // Tenant Preview List
            Text(
                text = "${rememberTranslation("Tenant Breakdown")} (${preview.tenantPreviews.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(preview.tenantPreviews) { tenant ->
                    TenantPreviewRow(tenant)
                }
            }

            // Action Buttons: Cancel and Confirm & Generate
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(rememberTranslation("Cancel"), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onConfirm(preview.billingMonth, backupPreviousMonthToExcel) },
                    enabled = !isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769D1)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(rememberTranslation("Confirm & Generate"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TenantPreviewRow(tenant: TenantMonthPreview) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (tenant.isProrated) Color(0xFFFFFBEB) else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, if (tenant.isProrated) Color(0xFFFDE68A) else Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = tenant.tenantName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    )
                    Text("• Room ${tenant.roomNumber}", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B)))
                }
                Text(
                    text = if (tenant.isProrated) "⚡ ${tenant.prorationReason}" else "Full Month (${tenant.daysInMonth} days)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (tenant.isProrated) Color(0xFFB86B00) else Color(0xFF64748B),
                        fontWeight = if (tenant.isProrated) FontWeight.Bold else FontWeight.Normal
                    )
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${tenant.expectedRent}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1769D1))
                )
                if (tenant.alreadyExists) {
                    Text(rememberTranslation("Existing"), style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF16803C), fontSize = 10.sp))
                }
            }
        }
    }
}
