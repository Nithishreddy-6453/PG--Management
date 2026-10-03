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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: ExpenseListViewModel,
    onBackClick: () -> Unit,
    onExpenseClick: (Int) -> Unit,
    onAddExpenseClick: () -> Unit,
    onNavigate: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.state.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    val categories = listOf(
        "All", "Electricity", "Water", "Internet", "Maintenance",
        "Cleaning", "Repairs", "Staff Salary", "Food", "Furniture", "Other"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = rememberTranslation("Expenses"),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = rememberTranslation("Back")
                        )
                    }
                },
                actions = {
                    GlobalLanguageToggle(modifier = Modifier.padding(end = 4.dp))
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.testTag("filter_button")
                    ) {
                        val hasFilters = uiState.selectedCategory != "All" ||
                                uiState.selectedStatus != "All" ||
                                uiState.selectedPaymentMethod != "All"
                        BadgedBox(
                            badge = {
                                if (hasFilters) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filters"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("expenses_top_bar")
            )
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
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        text = rememberTranslation("Add Expense"),
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("add_expense_fab")
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("expenses_screen_container")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Month Selector Header
            ExpenseMonthSelector(
                currentMonth = uiState.selectedBillingMonth,
                onPreviousMonth = { viewModel.onEvent(ExpenseListUiEvent.PreviousMonth) },
                onNextMonth = { viewModel.onEvent(ExpenseListUiEvent.NextMonth) },
                onCurrentMonth = { viewModel.onEvent(ExpenseListUiEvent.CurrentMonth) }
            )

            // 2. Financial Summary Cards (Total, Paid, Outstanding, Count)
            ExpenseSummaryCards(
                totalExpenses = uiState.totalExpenses,
                paidExpenses = uiState.paidExpenses,
                outstandingExpenses = uiState.outstandingExpenses,
                expenseCount = uiState.expenseCount
            )

            // 3. Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onEvent(ExpenseListUiEvent.SearchChanged(it)) },
                placeholder = { Text(rememberTranslation("Search expenses...")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onEvent(ExpenseListUiEvent.SearchChanged("")) }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("expense_search_bar"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                singleLine = true
            )

            // 4. Quick Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = uiState.selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onEvent(ExpenseListUiEvent.CategoryFilterChanged(cat)) },
                        label = {
                            Text(
                                text = rememberTranslation(cat),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // 5. Expense Items List
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredExpenses.isEmpty()) {
                EmptyExpensesView(
                    month = uiState.selectedBillingMonth,
                    onAddExpenseClick = onAddExpenseClick
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .testTag("expenses_list"),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredExpenses, key = { it.id }) { expense ->
                        ExpenseListItemCard(
                            expense = expense,
                            onClick = { onExpenseClick(expense.id) }
                        )
                    }
                }
            }
        }
    }

    // Filter Bottom Sheet
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
fun ExpenseMonthSelector(
    currentMonth: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Month"
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCurrentMonth() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = currentMonth,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            IconButton(onClick = onNextMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Month"
                )
            }
        }
    }
}

@Composable
fun ExpenseSummaryCards(
    totalExpenses: Double,
    paidExpenses: Double,
    outstandingExpenses: Double,
    expenseCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main Total Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = rememberTranslation("Total Expenses"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "₹${String.format(Locale.getDefault(), "%,.0f", totalExpenses)}",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "$expenseCount ${rememberTranslation("Expenses")}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Row of Paid & Outstanding Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Paid Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                border = BorderStroke(1.dp, Color(0xFFBBF7D0))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = rememberTranslation("Paid"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF166534),
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "₹${String.format(Locale.getDefault(), "%,.0f", paidExpenses)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    )
                }
            }

            // Outstanding Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (outstandingExpenses > 0) Color(0xFFFEF2F2) else Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, if (outstandingExpenses > 0) Color(0xFFFECACA) else Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = rememberTranslation("Outstanding"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (outstandingExpenses > 0) Color(0xFF991B1B) else Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "₹${String.format(Locale.getDefault(), "%,.0f", outstandingExpenses)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (outstandingExpenses > 0) Color(0xFFDC2626) else Color(0xFF334155)
                        )
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
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

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rememberTranslation(expense.category),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = " • ",
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = expense.date,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                if (!expense.vendor.isNullOrBlank()) {
                    Text(
                        text = "${rememberTranslation("Vendor")}: ${expense.vendor}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount & Status Badge
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${String.format(Locale.getDefault(), "%,.0f", expense.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                ExpenseStatusBadge(status = expense.status)

                if (expense.isPartiallyPaid && expense.remainingAmount > 0) {
                    Text(
                        text = "${rememberTranslation("Remaining")}: ₹${String.format(Locale.getDefault(), "%,.0f", expense.remainingAmount)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFD97706),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ExpenseStatusBadge(status: String) {
    val (bgColor, textColor, label) = when (status.lowercase()) {
        "paid" -> Triple(Color(0xFFDCFCE7), Color(0xFF166534), rememberTranslation("Paid"))
        "partially paid" -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), rememberTranslation("Partially Paid"))
        else -> Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), rememberTranslation("Unpaid"))
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
            .fillMaxSize()
            .padding(32.dp),
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No expenses recorded for $month",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Track operational costs, utilities, supplies, and maintenance.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onAddExpenseClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(rememberTranslation("Add Expense"))
            }
        }
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
                text = "Filter & Sort Expenses",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            // Status Filter
            Text("Payment Status", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Paid", "Partially Paid", "Unpaid").forEach { status ->
                    FilterChip(
                        selected = currentStatus == status,
                        onClick = { onStatusChange(status) },
                        label = { Text(rememberTranslation(status)) }
                    )
                }
            }

            // Payment Method Filter
            Text("Payment Method", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
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
            Text("Sort Order", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentSort == SortType.DATE_DESC,
                    onClick = { onSortChange(SortType.DATE_DESC) },
                    label = { Text("Newest Date") }
                )
                FilterChip(
                    selected = currentSort == SortType.AMOUNT_DESC,
                    onClick = { onSortChange(SortType.AMOUNT_DESC) },
                    label = { Text("Highest Amount") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
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
        "staff salary" -> Icons.Default.Badge
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
        "staff salary" -> Color(0xFFFCE7F3)
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
        "staff salary" -> Color(0xFFDB2777)
        "food" -> Color(0xFFCA8A04)
        "furniture" -> Color(0xFF9333EA)
        else -> Color(0xFF334155)
    }
}
