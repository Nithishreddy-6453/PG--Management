package com.example.features.expenses

import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.AppBottomNavBar
import com.example.core.designsystem.MainTab
import com.example.core.language.GlobalLanguageToggle
import com.example.core.language.rememberTranslation
import com.example.features.expenses.domain.model.Expense
import com.example.features.expenses.domain.usecase.SortType
import com.example.features.expenses.ui.viewmodel.ExpenseListUiEvent
import com.example.features.expenses.ui.viewmodel.ExpenseListViewModel
import com.example.features.expenses.ui.viewmodel.ExpenseTimeRange
import java.util.Locale

private val BrandPrimaryBlue = Color(0xFF0866FF)
private val LightScreenBg = Color(0xFFF8FAFC)
private val PaidGreenBg = Color(0xFFDCFCE7)
private val PaidGreenText = Color(0xFF15803D)
private val PendingAmberBg = Color(0xFFFEF3C7)
private val PendingAmberText = Color(0xFFB45309)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: ExpenseListViewModel,
    onBackClick: () -> Unit,
    onExpenseClick: (Int) -> Unit,
    onAddExpenseClick: () -> Unit,
    onViewReportClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.state.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedStatusFilter by remember { mutableStateOf("All") }

    val categoryFilterOptions = listOf("All", "Food", "Electricity", "Water", "Maintenance", "Salary", "Other")
    val statusFilterOptions = listOf("All", "Paid", "Pending")

    val quickAddCategories = listOf(
        Triple("Food", Icons.Default.Restaurant, Color(0xFFFEF9C3)),
        Triple("Electricity", Icons.Default.Bolt, Color(0xFFFEF3C7)),
        Triple("Water", Icons.Default.WaterDrop, Color(0xFFE0F2FE)),
        Triple("Maintenance", Icons.Default.Build, Color(0xFFF1F5F9)),
        Triple("Cleaning", Icons.Default.CleaningServices, Color(0xFFECFDF5)),
        Triple("Other", Icons.Default.MoreHoriz, Color(0xFFF3E8FF))
    )

    Scaffold(
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rememberTranslation("Expenses"),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val propertyDisplay = uiState.propertyName.ifBlank { "All Properties" }
                        Text(
                            text = rememberTranslation(propertyDisplay),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlobalLanguageToggle()
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = rememberTranslation("Settings"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            AppBottomNavBar(
                currentTab = MainTab.EXPENSES,
                onNavigate = onNavigate
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpenseClick,
                containerColor = BrandPrimaryBlue,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        text = rememberTranslation("Add Expense"),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .testTag("add_expense_fab")
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("expenses_screen_container")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Month Selector + Time-Range Controls
            item {
                ExpenseMonthAndTimeRangeHeader(
                    currentMonth = uiState.selectedBillingMonth,
                    timeRange = uiState.timeRange,
                    onPreviousMonth = { viewModel.onEvent(ExpenseListUiEvent.PreviousMonth) },
                    onNextMonth = { viewModel.onEvent(ExpenseListUiEvent.NextMonth) },
                    onCurrentMonth = { viewModel.onEvent(ExpenseListUiEvent.CurrentMonth) },
                    onTimeRangeSelected = { viewModel.onEvent(ExpenseListUiEvent.TimeRangeChanged(it)) }
                )
            }

            // 2. Financial Overview Card (Total, Paid, Pending)
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ExpenseMonthlyOverviewCard(
                        totalExpenses = uiState.totalExpenses,
                        paidExpenses = uiState.paidExpenses,
                        outstandingExpenses = uiState.outstandingExpenses,
                        expenseCount = uiState.expenseCount,
                        categoryBreakdown = uiState.categoryBreakdown,
                        billingMonth = uiState.selectedBillingMonth,
                        timeRange = uiState.timeRange,
                        onViewReportClick = onViewReportClick
                    )
                }
            }

            // 3. Quick Actions Row (Add Expense, Scan Bill, Recurring, Categories)
            item {
                ExpenseQuickActionsRow(
                    onAddExpenseClick = onAddExpenseClick,
                    onShowCategoriesClick = { showFilterSheet = true }
                )
            }

            // 4. Expense by Category Section
            item {
                ExpenseByCategorySection(
                    categoryBreakdown = uiState.categoryBreakdown,
                    totalExpenses = uiState.totalExpenses,
                    onViewReportClick = onViewReportClick
                )
            }

            // 4. Search and Filter Chips
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Search bar + Filter button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onEvent(ExpenseListUiEvent.SearchChanged(it)) },
                            placeholder = { Text(rememberTranslation("Search expense, vendor, notes...")) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandPrimaryBlue) },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onEvent(ExpenseListUiEvent.SearchChanged("")) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("expense_search_bar"),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandPrimaryBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            singleLine = true
                        )

                        val hasFilters = selectedCategoryFilter != "All" ||
                                selectedStatusFilter != "All" ||
                                uiState.selectedCategory != "All" ||
                                uiState.selectedStatus != "All"

                        FilledTonalIconButton(
                            onClick = { showFilterSheet = true },
                            shape = RoundedCornerShape(16.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (hasFilters) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("filter_button")
                        ) {
                            BadgeBoxWrapper(hasFilters = hasFilters) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = rememberTranslation("Filters"),
                                    tint = if (hasFilters) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Category Filter Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categoryFilterOptions) { cat ->
                            val isSelected = uiState.selectedCategory == cat || selectedCategoryFilter == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedCategoryFilter = cat
                                    viewModel.onEvent(ExpenseListUiEvent.CategoryFilterChanged(cat))
                                },
                                label = {
                                    Text(
                                        text = rememberTranslation(cat),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                leadingIcon = if (cat != "All") {
                                    {
                                        Icon(
                                            imageVector = getExpenseCategoryIcon(cat),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandPrimaryBlue,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                )
                            )
                        }
                    }

                    // Status Filter Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(statusFilterOptions) { status ->
                            val isSelected = uiState.selectedStatus.equals(status, ignoreCase = true) || selectedStatusFilter == status
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedStatusFilter = status
                                    val mappedStatus = if (status == "Pending") "Unpaid" else status
                                    viewModel.onEvent(ExpenseListUiEvent.StatusFilterChanged(mappedStatus))
                                },
                                label = {
                                    Text(
                                        text = rememberTranslation(status),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (status == "Paid") PaidGreenBg else if (status == "Pending") PendingAmberBg else BrandPrimaryBlue,
                                    selectedLabelColor = if (status == "Paid") PaidGreenText else if (status == "Pending") PendingAmberText else Color.White
                                )
                            )
                        }
                    }
                }
            }

            // 5. Expense Items Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rememberTranslation("Expense Records"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = "${uiState.filteredExpenses.size} ${rememberTranslation("items")}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // 6. Expense List
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BrandPrimaryBlue)
                    }
                }
            } else if (uiState.filteredExpenses.isEmpty()) {
                item {
                    EmptyExpensesView(
                        month = uiState.selectedBillingMonth,
                        onAddExpenseClick = onAddExpenseClick
                    )
                }
            } else {
                items(
                    items = uiState.filteredExpenses,
                    key = { it.id }
                ) { expense ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ExpenseListItemCard(
                            expense = expense,
                            onClick = { onExpenseClick(expense.id) }
                        )
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        ExpenseFilterBottomSheet(
            currentCategory = uiState.selectedCategory,
            currentStatus = uiState.selectedStatus,
            currentPaymentMethod = uiState.selectedPaymentMethod,
            currentSort = uiState.sortBy,
            onCategoryChange = { viewModel.onEvent(ExpenseListUiEvent.CategoryFilterChanged(it)) },
            onStatusChange = { viewModel.onEvent(ExpenseListUiEvent.StatusFilterChanged(it)) },
            onPaymentMethodChange = { viewModel.onEvent(ExpenseListUiEvent.PaymentMethodFilterChanged(it)) },
            onSortChange = { viewModel.onEvent(ExpenseListUiEvent.SortOrderChanged(it)) },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
fun ExpenseMonthAndTimeRangeHeader(
    currentMonth: String,
    timeRange: ExpenseTimeRange,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onTimeRangeSelected: (ExpenseTimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Month Selector with working previous/next controls
        Surface(
            modifier = Modifier.weight(1.05f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shadowElevation = 0.5.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = rememberTranslation("Previous Month"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCurrentMonth() }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = BrandPrimaryBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = currentMonth,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = rememberTranslation("Next Month"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Right: Segmented selector with "This Month", "Last 3M", "All Time"
        Surface(
            modifier = Modifier.weight(1.25f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val segments = listOf(
                    ExpenseTimeRange.THIS_MONTH to "This Month",
                    ExpenseTimeRange.LAST_3M to "Last 3M",
                    ExpenseTimeRange.ALL_TIME to "All Time"
                )

                segments.forEach { (range, label) ->
                    val isSelected = timeRange == range
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) BrandPrimaryBlue else Color.Transparent)
                            .clickable { onTimeRangeSelected(range) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = rememberTranslation(label),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseMonthlyOverviewCard(
    totalExpenses: Double,
    paidExpenses: Double,
    outstandingExpenses: Double,
    expenseCount: Int,
    categoryBreakdown: Map<String, Double>,
    billingMonth: String,
    timeRange: ExpenseTimeRange,
    onViewReportClick: () -> Unit
) {
    val rangeLabel = when (timeRange) {
        ExpenseTimeRange.THIS_MONTH -> billingMonth
        ExpenseTimeRange.LAST_3M -> rememberTranslation("Last 3 Months")
        ExpenseTimeRange.ALL_TIME -> rememberTranslation("All Time")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Total Expenses title and View Report link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = rememberTranslation("Total Expenses"),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = rangeLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = BrandPrimaryBlue,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                TextButton(
                    onClick = onViewReportClick,
                    modifier = Modifier.testTag("view_report_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${rememberTranslation("View Report")} >",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = BrandPrimaryBlue
                        )
                    )
                }
            }

            // Big Total Amount + Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "₹${String.format(Locale.getDefault(), "%,.0f", totalExpenses)}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "$expenseCount ${rememberTranslation("expenses")}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 1.dp
            )

            // Two Sub-cards: Green for Paid, Amber for Pending
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Paid Card
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(PaidGreenBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = rememberTranslation("Paid"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = PaidGreenText
                                )
                            )
                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%,.0f", paidExpenses)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Pending Card (Amber)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(PendingAmberBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = rememberTranslation("Pending"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = PendingAmberText
                                )
                            )
                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%,.0f", outstandingExpenses)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Top categories preview if available
            val topCategories = categoryBreakdown.entries.sortedByDescending { it.value }.take(3)
            if (topCategories.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topCategories.forEach { (cat, amount) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = rememberTranslation(cat),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "₹${String.format(Locale.getDefault(), "%,.0f", amount)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickCategoryCard(
    name: String,
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .width(82.dp)
            .height(82.dp)
            .testTag("quick_add_$name")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = getCategoryIconColor(name),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = rememberTranslation(name),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ExpenseQuickActionsRow(
    onAddExpenseClick: () -> Unit,
    onShowCategoriesClick: () -> Unit
) {
    val actions = listOf(
        Triple("Add Expense", Icons.Default.Add, Color(0xFFE8F0FE)),
        Triple("Scan Bill", Icons.Default.CameraAlt, Color(0xFFF3E8FF)),
        Triple("Recurring", Icons.Default.Repeat, Color(0xFFECFDF5)),
        Triple("Categories", Icons.Default.Category, Color(0xFFFEF3C7))
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actions.forEach { (label, icon, bg) ->
            val translatedLabel = rememberTranslation(label)
            Card(
                onClick = {
                    if (label == "Categories") {
                        onShowCategoriesClick()
                    } else {
                        onAddExpenseClick()
                    }
                },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp)
                    .testTag("quick_action_$label")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = translatedLabel,
                            tint = when (label) {
                                "Add Expense" -> BrandPrimaryBlue
                                "Scan Bill" -> Color(0xFF7C3AED)
                                "Recurring" -> Color(0xFF059669)
                                else -> Color(0xFFD97706)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = translatedLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ExpenseByCategorySection(
    categoryBreakdown: Map<String, Double>,
    totalExpenses: Double,
    onViewReportClick: () -> Unit
) {
    if (categoryBreakdown.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rememberTranslation("Expense by Category"),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                TextButton(
                    onClick = onViewReportClick,
                    modifier = Modifier.testTag("expense_category_view_report")
                ) {
                    Text(
                        text = "${rememberTranslation("View Report")} >",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = BrandPrimaryBlue
                        )
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            categoryBreakdown.entries.sortedByDescending { it.value }.forEach { (category, amount) ->
                val percentage = if (totalExpenses > 0) (amount / totalExpenses * 100).toInt() else 0
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(getCategoryIconColor(category))
                            )
                            Text(
                                text = rememberTranslation(category),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        Text(
                            text = "₹${String.format(Locale.getDefault(), "%,.0f", amount)} ($percentage%)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (totalExpenses > 0) (amount / totalExpenses).toFloat().coerceIn(0f, 1f) else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = getCategoryIconColor(category),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ExpenseListItemCard(
    expense: Expense,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("expense_item_${expense.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category circular icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(getCategoryBgColor(expense.category)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getExpenseCategoryIcon(expense.category),
                    contentDescription = expense.category,
                    tint = getCategoryIconColor(expense.category),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center details: Title, Category • Date, Vendor/Notes
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = rememberTranslation(expense.category),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    Text(
                        text = expense.date,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val subText = listOfNotNull(
                    expense.vendor.takeIf { !it.isNullOrBlank() },
                    expense.notes.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                if (subText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right: Amount and Status badge
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${String.format(Locale.getDefault(), "%,.0f", expense.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                ExpenseStatusBadge(status = expense.status)
            }
        }
    }
}

@Composable
fun ExpenseStatusBadge(status: String) {
    val (bgColor, textColor, label) = when (status.lowercase()) {
        "paid" -> Triple(PaidGreenBg, PaidGreenText, rememberTranslation("Paid"))
        "partially paid" -> Triple(PendingAmberBg, PendingAmberText, rememberTranslation("Partially Paid"))
        else -> Triple(PendingAmberBg, PendingAmberText, rememberTranslation("Pending"))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun EmptyExpensesView(
    month: String,
    onAddExpenseClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = BrandPrimaryBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${rememberTranslation("No expenses recorded for")} $month",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = rememberTranslation("Start tracking operational costs, utilities, supplies, and maintenance."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onAddExpenseClick,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimaryBlue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(rememberTranslation("Add Expense"))
            }
        }
    }
}

@Composable
fun BadgeBoxWrapper(hasFilters: Boolean, content: @Composable () -> Unit) {
    if (hasFilters) {
        BadgedBox(
            badge = { Badge(containerColor = BrandPrimaryBlue) }
        ) {
            content()
        }
    } else {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFilterBottomSheet(
    currentCategory: String,
    currentStatus: String,
    currentPaymentMethod: String,
    currentSort: SortType,
    onCategoryChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onPaymentMethodChange: (String) -> Unit,
    onSortChange: (SortType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = rememberTranslation("Filter & Sort Expenses"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            // Status Filter
            Text(rememberTranslation("Payment Status"), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Paid", "Pending", "Overdue").forEach { status ->
                    FilterChip(
                        selected = currentStatus.equals(status, true),
                        onClick = { onStatusChange(if (status == "Pending") "Unpaid" else status) },
                        label = { Text(rememberTranslation(status)) }
                    )
                }
            }

            // Payment Method Filter
            Text(rememberTranslation("Payment Method"), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "UPI", "Cash", "Bank Transfer").forEach { method ->
                    FilterChip(
                        selected = currentPaymentMethod == method,
                        onClick = { onPaymentMethodChange(method) },
                        label = { Text(rememberTranslation(method)) }
                    )
                }
            }

            // Sort Order
            Text(rememberTranslation("Sort Order"), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentSort == SortType.DATE_DESC,
                    onClick = { onSortChange(SortType.DATE_DESC) },
                    label = { Text(rememberTranslation("Newest Date")) }
                )
                FilterChip(
                    selected = currentSort == SortType.AMOUNT_DESC,
                    onClick = { onSortChange(SortType.AMOUNT_DESC) },
                    label = { Text(rememberTranslation("Highest Amount")) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimaryBlue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(rememberTranslation("Close"))
            }
        }
    }
}

fun getExpenseCategoryIcon(category: String): ImageVector {
    return when (category.lowercase()) {
        "electricity" -> Icons.Default.Bolt
        "water" -> Icons.Default.WaterDrop
        "internet" -> Icons.Default.Wifi
        "maintenance" -> Icons.Default.Build
        "cleaning" -> Icons.Default.CleaningServices
        "repairs" -> Icons.Default.Handyman
        "staff salary", "salary" -> Icons.Default.Badge
        "food" -> Icons.Default.Restaurant
        "furniture" -> Icons.Default.Chair
        "security" -> Icons.Default.Security
        "property tax" -> Icons.Default.AccountBalance
        "rent/lease" -> Icons.Default.House
        "appliances" -> Icons.Default.Kitchen
        "utilities" -> Icons.Default.Power
        else -> Icons.Default.Receipt
    }
}

fun getCategoryBgColor(category: String): Color {
    return when (category.lowercase()) {
        "electricity" -> Color(0xFFFEF3C7)
        "water" -> Color(0xFFE0F2FE)
        "internet" -> Color(0xFFEDE9FE)
        "maintenance" -> Color(0xFFF1F5F9)
        "cleaning" -> Color(0xFFECFDF5)
        "repairs" -> Color(0xFFFFEDD5)
        "staff salary", "salary" -> Color(0xFFFCE7F3)
        "food" -> Color(0xFFFEF9C3)
        "furniture" -> Color(0xFFF3E8FF)
        else -> Color(0xFFE2E8F0)
    }
}

fun getCategoryIconColor(category: String): Color {
    return when (category.lowercase()) {
        "electricity" -> Color(0xFFD97706)
        "water" -> Color(0xFF0284C7)
        "internet" -> Color(0xFF7C3AED)
        "maintenance" -> Color(0xFF475569)
        "cleaning" -> Color(0xFF059669)
        "repairs" -> Color(0xFFEA580C)
        "staff salary", "salary" -> Color(0xFFDB2777)
        "food" -> Color(0xFFCA8A04)
        "furniture" -> Color(0xFF9333EA)
        else -> Color(0xFF334155)
    }
}
