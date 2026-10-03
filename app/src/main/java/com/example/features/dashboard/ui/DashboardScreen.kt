package com.example.features.dashboard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.designsystem.AppBottomNavBar
import com.example.core.designsystem.MainTab
import com.example.core.language.AppLanguageManager
import com.example.core.language.rememberTranslation
import com.example.features.dashboard.domain.usecase.ActivityType
import com.example.features.dashboard.domain.usecase.DashboardSummary
import com.example.features.dashboard.domain.usecase.RecentActivity
import com.example.features.dashboard.domain.usecase.UpcomingVacancyItem
import com.example.features.properties.ui.components.AddPropertyDialog
import com.example.features.properties.ui.components.PropertySelectorBottomSheet
import com.example.features.properties.ui.viewmodel.PropertyViewModel
import com.example.features.reports.domain.model.AttentionItem
import com.example.features.reports.domain.model.AttentionType
import com.example.navigation.Screen
import com.example.ui.viewmodel.PgViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: PgViewModel,
    dashboardViewModel: DashboardViewModel,
    onNavigate: (String) -> Unit,
    onLockRequested: () -> Unit,
    modifier: Modifier = Modifier,
    propertyViewModel: PropertyViewModel = hiltViewModel()
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val currentProperty by propertyViewModel.currentProperty.collectAsState()
    val properties by propertyViewModel.activeProperties.collectAsState()
    val isLoadingProperties by propertyViewModel.isLoading.collectAsState()
    val propertySheetState = rememberModalBottomSheetState()
    var showPropertySheet by remember { mutableStateOf(false) }
    var showAddPropertyDialog by remember { mutableStateOf(false) }
    val selectedLanguage by AppLanguageManager.languageFlow.collectAsState()

    val activePgName = currentProperty?.propertyName ?: profile?.pgName?.ifBlank { "Reddy PG" } ?: "Reddy PG"
    val pgLocation = currentProperty?.city?.takeIf { it.isNotBlank() }
        ?: currentProperty?.address?.takeIf { it.isNotBlank() }
        ?: "Bhubaneswar"
    val ownerName = profile?.ownerName?.ifBlank { "Nithish Prasad" } ?: "Nithish Prasad"

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour, selectedLanguage) {
        if (selectedLanguage == "తెలుగు") {
            when {
                currentHour < 12 -> "శుభోదయం"
                currentHour < 17 -> "శుభ మధ్యాహ్నం"
                else -> "శుభ సాయంత్రం"
            }
        } else {
            when {
                currentHour < 12 -> "Good morning"
                currentHour < 17 -> "Good afternoon"
                else -> "Good evening"
            }
        }
    }

    if (showPropertySheet) {
        PropertySelectorBottomSheet(
            sheetState = propertySheetState,
            properties = properties,
            currentProperty = currentProperty,
            onSelectProperty = { prop ->
                propertyViewModel.switchProperty(prop)
                showPropertySheet = false
            },
            onAddNewProperty = {
                showPropertySheet = false
                showAddPropertyDialog = true
            },
            onDismiss = { showPropertySheet = false }
        )
    }

    if (showAddPropertyDialog) {
        AddPropertyDialog(
            isOpen = showAddPropertyDialog,
            isLoading = isLoadingProperties,
            onDismiss = { showAddPropertyDialog = false },
            onConfirm = { name, address, city, state, postalCode, phone, desc ->
                propertyViewModel.createProperty(
                    name = name,
                    address = address,
                    city = city,
                    state = state,
                    postalCode = postalCode,
                    contactNumber = phone,
                    description = desc,
                    onSuccess = { showAddPropertyDialog = false }
                )
            }
        )
    }

    Scaffold(
        bottomBar = {
            AppBottomNavBar(
                currentTab = MainTab.HOME,
                onNavigate = onNavigate
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("dashboard_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(targetState = uiState, label = "DashboardStateAnimation") { state ->
                when (state) {
                    is DashboardUiState.Loading -> {
                        DashboardSkeletonLoading()
                    }
                    is DashboardUiState.Empty -> {
                        DashboardEmptyRoomsState(
                            onAddRoomClick = { onNavigate(Screen.AddRoom.route) }
                        )
                    }
                    is DashboardUiState.Error -> {
                        DashboardErrorState(
                            errorMessage = state.message,
                            onRetryClick = { dashboardViewModel.handleEvent(DashboardUiEvent.Retry) }
                        )
                    }
                    is DashboardUiState.Success -> {
                        val activeBillingMonth by dashboardViewModel.currentBillingMonth.collectAsState()
                        DashboardContent(
                            ownerName = ownerName,
                            greeting = greeting,
                            selectedLanguage = selectedLanguage,
                            onLanguageSelect = { AppLanguageManager.setLanguage(it) },
                            summary = state.data,
                            onNavigate = onNavigate,
                            activePgName = activePgName,
                            pgLocation = pgLocation,
                            onSwitchProperty = { showPropertySheet = true },
                            activeBillingMonth = activeBillingMonth,
                            onSelectBillingMonth = { dashboardViewModel.selectBillingMonth(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    ownerName: String,
    greeting: String,
    selectedLanguage: String,
    onLanguageSelect: (String) -> Unit,
    summary: DashboardSummary,
    onNavigate: (String) -> Unit,
    activePgName: String,
    pgLocation: String,
    onSwitchProperty: () -> Unit,
    activeBillingMonth: String,
    onSelectBillingMonth: (String) -> Unit
) {
    val initialLetter = activePgName.trim().firstOrNull()?.toString()?.uppercase() ?: "P"
    val isTe = selectedLanguage == "తెలుగు"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_scroll_content"),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. COMPACT HEADER
        // ==========================================
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                // Top row: Greeting & PG Info + Actions (Language, Settings)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Profile / PG Info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // PG Avatar
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                                .clickable { onSwitchProperty() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initialLetter,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "$greeting, $ownerName",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onSwitchProperty() }
                                    .padding(vertical = 1.dp)
                                    .testTag("dashboard_property_selector_trigger")
                            ) {
                                Text(
                                    text = activePgName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Switch Property",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (pgLocation.isNotBlank()) {
                                Text(
                                    text = pgLocation,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Right Actions: Language Toggle & Settings Gear
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageTogglePill(
                            selectedLanguage = selectedLanguage,
                            onLanguageSelect = onLanguageSelect
                        )

                        Surface(
                            onClick = { onNavigate(Screen.SettingsHome.route) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("dashboard_settings_button")
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom row of Header: Current Billing Month Selector Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isTe) "డాష్‌బోర్డ్" else "Dashboard",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp
                        )
                    )

                    var monthDropdownExpanded by remember { mutableStateOf(false) }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { monthDropdownExpanded = true }
                                .testTag("dashboard_billing_month_selector")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activeBillingMonth,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select Month",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = monthDropdownExpanded,
                            onDismissRequest = { monthDropdownExpanded = false }
                        ) {
                            val availableMonths = remember {
                                val list = mutableListOf<String>()
                                val cal = Calendar.getInstance()
                                cal.add(Calendar.MONTH, -3)
                                for (i in 0..6) {
                                    list.add(com.example.features.rent.domain.util.RentBillingEngine.formatCanonicalBillingMonth(cal.time))
                                    cal.add(Calendar.MONTH, 1)
                                }
                                list.distinct().reversed()
                            }
                            availableMonths.forEach { month ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = month,
                                            fontWeight = if (month == activeBillingMonth) FontWeight.Bold else FontWeight.Normal,
                                            color = if (month == activeBillingMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        onSelectBillingMonth(month)
                                        monthDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. QUICK ACTIONS (Compact 2x2 Layout)
        // ==========================================
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isTe) "త్వరిత చర్యలు" else "Quick Actions",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action 1: Add Room
                    CompactQuickActionButton(
                        label = if (isTe) "రూమ్ జోడించు" else "Add Room",
                        icon = Icons.Default.AddHome,
                        tint = Color(0xFF2563EB),
                        onClick = { onNavigate(Screen.AddRoom.route) },
                        testTag = "quick_action_add_room",
                        modifier = Modifier.weight(1f)
                    )

                    // Action 2: Add Tenant
                    CompactQuickActionButton(
                        label = if (isTe) "అద్దెదారుని జోడించు" else "Add Tenant",
                        icon = Icons.Default.PersonAdd,
                        tint = Color(0xFF16A34A),
                        onClick = { onNavigate(Screen.AddTenant.route) },
                        testTag = "quick_action_add_tenant",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action 3: Collect Rent
                    CompactQuickActionButton(
                        label = if (isTe) "అద్దె వసూలు" else "Collect Rent",
                        icon = Icons.Default.CurrencyRupee,
                        tint = Color(0xFFEA580C),
                        onClick = { onNavigate(Screen.RecordPayment.route) },
                        testTag = "quick_action_collect_rent",
                        modifier = Modifier.weight(1f)
                    )

                    // Action 4: Add Expense
                    CompactQuickActionButton(
                        label = if (isTe) "ఖర్చు జోడించు" else "Add Expense",
                        icon = Icons.Default.ReceiptLong,
                        tint = Color(0xFF9333EA),
                        onClick = { onNavigate(Screen.AddExpense.route) },
                        testTag = "quick_action_add_expense",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ==========================================
        // 3. FINANCIAL SNAPSHOT (Compact 2x2 Grid)
        // ==========================================
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isTe) "ఆర్థిక సారాంశం" else "Financial Snapshot",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    )
                    Text(
                        text = activeBillingMonth,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2x2 Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Rent Received
                    CompactFinancialCard(
                        title = if (isTe) "వసూలైన అద్దె" else "Rent Received",
                        amount = summary.revenue.monthlyRevenue,
                        subtitle = if (isTe) "ఈ నెల" else "This month",
                        accentColor = Color(0xFF16A34A),
                        onClick = null,
                        testTag = "fin_card_rent_received",
                        modifier = Modifier.weight(1f)
                    )

                    // 2. Rent Outstanding
                    CompactFinancialCard(
                        title = if (isTe) "బకాయి అద్దె" else "Rent Outstanding",
                        amount = summary.revenue.pendingRent,
                        subtitle = if (isTe) "ఈ నెల" else "This month",
                        accentColor = if (summary.revenue.pendingRent > 0) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onNavigate(Screen.RentLedger.route) },
                        testTag = "fin_card_rent_outstanding",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 3. Expenses
                    CompactFinancialCard(
                        title = if (isTe) "ఖర్చులు" else "Expenses",
                        amount = summary.expenses.totalExpenses,
                        subtitle = if (isTe) "ఈ నెల" else "This month",
                        accentColor = Color(0xFF9333EA),
                        onClick = { onNavigate(Screen.Expenses.route) },
                        testTag = "fin_card_expenses",
                        modifier = Modifier.weight(1f)
                    )

                    // 4. Balance After Expenses
                    CompactFinancialCard(
                        title = if (isTe) "ఖర్చుల తర్వాత నికర మొత్తం" else "Balance After Expenses",
                        amount = summary.profit.netProfit,
                        subtitle = if (isTe) "నికర మొత్తం" else "Net balance",
                        accentColor = if (summary.profit.netProfit >= 0) Color(0xFF2563EB) else Color(0xFFDC2626),
                        onClick = null,
                        testTag = "fin_card_balance_after_expenses",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Previous Dues Indicator if applicable
                if (summary.revenue.previousDuesReceived > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFECFDF5),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "•",
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = if (isTe) {
                                    "+ ₹${String.format(Locale.US, "%,.0f", summary.revenue.previousDuesReceived)} మునుపటి బకాయిలు వసూలయ్యాయి"
                                } else {
                                    "+ ₹${String.format(Locale.US, "%,.0f", summary.revenue.previousDuesReceived)} previous dues collected"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF047857),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 4. OCCUPANCY (Tenants / Total PG Capacity)
        // ==========================================
        item {
            val activeTenants = summary.occupancy.activeTenantsCount
            val totalCapacity = summary.occupancy.totalPgCapacity
            val vacancies = summary.occupancy.vacanciesCount
            val occupancyPercentage = summary.occupancy.homeOccupancyPercentage
            val occupancyRatio = if (totalCapacity > 0) (activeTenants.toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f) else 0f

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Screen.Rooms.route) }
                    .testTag("dashboard_occupancy_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isTe) "ఆక్రమణ" else "Occupancy",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.5.sp
                                )
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (isTe) "గదుల వివరాలు" else "View Rooms",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Main Primary Metric: 7 / 8 Tenants
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "$activeTenants / $totalCapacity",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 22.sp
                                )
                            )
                            Text(
                                text = if (isTe) "అద్దెదారులు" else "Tenants",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            )
                        }

                        // Subtitle: 87.5% occupancy · 1 vacancy
                        val vacancyText = if (isTe) {
                            "${String.format(Locale.US, "%.1f", occupancyPercentage)}% ఆక్రమణ · $vacancies ఖాళీ" + (if (vacancies == 1) "" else "లు")
                        } else {
                            "${String.format(Locale.US, "%.1f", occupancyPercentage)}% occupancy · $vacancies vacanc" + (if (vacancies == 1) "y" else "ies")
                        }

                        Text(
                            text = vacancyText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { occupancyRatio },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        // ==========================================
        // 5. NEEDS ATTENTION (Compact Navigational Rows)
        // ==========================================
        val hasAttentionItems = summary.whatNeedsAttention.isNotEmpty()
        if (hasAttentionItems) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isTe) "దృష్టి సారించాల్సినవి" else "Needs Attention",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        summary.whatNeedsAttention.forEach { item ->
                            val (dotColor, targetRoute) = when (item.type) {
                                AttentionType.RENT_REMAINING -> Pair(Color(0xFFDC2626), Screen.RentLedger.route)
                                AttentionType.VACATING_SOON -> Pair(Color(0xFFEA580C), Screen.Tenants.route)
                                AttentionType.VACANCY -> Pair(Color(0xFF2563EB), Screen.Rooms.route)
                                AttentionType.UNPAID_EXPENSE -> Pair(Color(0xFF9333EA), Screen.Expenses.route)
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val route = item.actionRoute ?: targetRoute
                                        onNavigate(route)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Status Dot
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.subtitle,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.5.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 6. UPCOMING MOVE-OUTS (Detailed List)
        // ==========================================
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isTe) "రాబోయే నిష్క్రమణలు" else "Upcoming Move-outs",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                        )
                        if (summary.upcomingVacancies.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = "${summary.upcomingVacancies.size}",
                                    color = Color(0xFFB45309),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigate(Screen.Tenants.route) }
                    ) {
                        Text(
                            text = if (isTe) "అన్నీ చూడండి" else "See all",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "See all",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        if (summary.upcomingVacancies.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isTe) "షెడ్యూల్ చేసిన నిష్క్రమణలు లేవు" else "No upcoming move-outs scheduled",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.5.sp
                                    )
                                )
                            }
                        } else {
                            summary.upcomingVacancies.take(5).forEachIndexed { index, vacancy ->
                                UpcomingMoveOutRow(
                                    vacancy = vacancy,
                                    isTe = isTe,
                                    isLast = index == summary.upcomingVacancies.take(5).lastIndex,
                                    onClick = {
                                        onNavigate(Screen.TenantDetails.createRoute(vacancy.tenantId))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 7. RECENT ACTIVITY (Meaningful Events)
        // ==========================================
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isTe) "ఇటీవలి కార్యకలాపాలు" else "Recent Activity",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigate(Screen.RentLedger.route) }
                    ) {
                        Text(
                            text = if (isTe) "అన్నీ చూడండి" else "View All",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View All",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        if (summary.recentActivities.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isTe) "కార్యకలాపాలు ఏవీ లేవు" else "No recent activity recorded",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.5.sp
                                    )
                                )
                            }
                        } else {
                            summary.recentActivities.take(6).forEachIndexed { index, activity ->
                                MeaningfulActivityRow(
                                    activity = activity,
                                    isTe = isTe,
                                    isLast = index == summary.recentActivities.take(6).lastIndex,
                                    onClick = {
                                        when (activity.type) {
                                            ActivityType.RENT_PAID -> onNavigate(Screen.RentLedger.route)
                                            ActivityType.TENANT_ADDED -> onNavigate(Screen.Tenants.route)
                                            ActivityType.EXPENSE_ADDED -> onNavigate(Screen.Expenses.route)
                                            ActivityType.ROOM_VACATED -> onNavigate(Screen.Rooms.route)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact Quick Action Button
 */
@Composable
private fun CompactQuickActionButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 0.5.dp,
        modifier = modifier
            .height(52.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact Financial Card for 2x2 Snapshot
 */
@Composable
private fun CompactFinancialCard(
    title: String,
    amount: Double,
    subtitle: String,
    accentColor: Color,
    onClick: (() -> Unit)?,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 0.5.dp,
        modifier = modifier
            .height(84.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = "₹${String.format(Locale.US, "%,.0f", amount)}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    fontSize = 17.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Top-Right Language Toggle Capsule [ EN | తెలుగు ]
 */
@Composable
private fun LanguageTogglePill(
    selectedLanguage: String,
    onLanguageSelect: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isEn = selectedLanguage == "EN"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isEn) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onLanguageSelect("EN") }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isEn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isEn) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                )
            }

            val isTe = selectedLanguage == "తెలుగు"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isTe) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onLanguageSelect("తెలుగు") }
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "తెలుగు",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isTe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isTe) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

/**
 * Upcoming Move-out Row
 */
@Composable
private fun UpcomingMoveOutRow(
    vacancy: UpcomingVacancyItem,
    isTe: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val initial = vacancy.tenantName.trim().firstOrNull()?.toString()?.uppercase() ?: "T"
    val formattedDate = formatHumanReadableDate(vacancy.leavingDate)

    val badgeText = if (vacancy.daysRemaining <= 0) {
        if (isTe) "ఈరోజే ఖాళీ చేస్తున్నారు" else "Moving out today"
    } else {
        if (isTe) "${vacancy.daysRemaining} రోజుల్లో ఖాళీ" else "Moving out in ${vacancy.daysRemaining}d"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("upcoming_move_out_row_${vacancy.tenantId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date / Avatar Circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFFEF3C7)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD97706),
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Tenant Name & Room
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = vacancy.tenantName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "· $formattedDate",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.5.sp
                    )
                )
            }
            Text(
                text = if (vacancy.bedId.isNotBlank()) "Room ${vacancy.roomNumber} · ${vacancy.bedId}" else "Room ${vacancy.roomNumber}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Move-out Status Badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (vacancy.daysRemaining <= 0) Color(0xFFFEE2E2) else Color(0xFFFEF3C7)
        ) {
            Text(
                text = badgeText,
                color = if (vacancy.daysRemaining <= 0) Color(0xFFDC2626) else Color(0xFFB45309),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }

    if (!isLast) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
    }
}

/**
 * Meaningful Activity Row
 */
@Composable
private fun MeaningfulActivityRow(
    activity: RecentActivity,
    isTe: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val (icon, iconTint, bgColor) = when (activity.type) {
        ActivityType.TENANT_ADDED -> Triple(Icons.Default.Person, Color(0xFF2563EB), Color(0xFFEBF3FE))
        ActivityType.RENT_PAID -> Triple(Icons.Default.CurrencyRupee, Color(0xFF16A34A), Color(0xFFEDF9F0))
        ActivityType.EXPENSE_ADDED -> Triple(Icons.Default.ReceiptLong, Color(0xFF9333EA), Color(0xFFF6EEFD))
        ActivityType.ROOM_VACATED -> Triple(Icons.Default.MeetingRoom, Color(0xFF64748B), Color(0xFFF1F5F9))
    }

    val formattedDate = formatHumanReadableDate(activity.date)

    val (primaryTitle, subtitleText) = when (activity.type) {
        ActivityType.TENANT_ADDED -> {
            val title = activity.param1.ifBlank { "New Tenant" }
            val sub = if (activity.param2.isNotBlank()) {
                if (isTe) "గది ${activity.param2} · అద్దెదారు చేరారు · $formattedDate" else "Room ${activity.param2} · Tenant added · $formattedDate"
            } else {
                if (isTe) "అద్దెదారు చేరారు · $formattedDate" else "Tenant added · $formattedDate"
            }
            Pair(title, sub)
        }
        ActivityType.RENT_PAID -> {
            val title = activity.param1.ifBlank { "Tenant" }
            val sub = if (isTe) {
                "₹${String.format(Locale.US, "%,.0f", activity.amount)} · అద్దె వసూలు · $formattedDate"
            } else {
                "₹${String.format(Locale.US, "%,.0f", activity.amount)} · Rent received · $formattedDate"
            }
            Pair(title, sub)
        }
        ActivityType.EXPENSE_ADDED -> {
            val title = activity.param1.ifBlank { if (isTe) "ఖర్చు" else "Expense" }
            val sub = if (isTe) {
                "₹${String.format(Locale.US, "%,.0f", activity.amount)} · ఖర్చు · $formattedDate"
            } else {
                "₹${String.format(Locale.US, "%,.0f", activity.amount)} · Expense · $formattedDate"
            }
            Pair(title, sub)
        }
        ActivityType.ROOM_VACATED -> {
            val title = activity.param1.ifBlank { "Tenant" }
            val sub = if (isTe) {
                "గది ${activity.param2} · గది ఖాళీ చేశారు · $formattedDate"
            } else {
                "Room ${activity.param2} · Room vacated · $formattedDate"
            }
            Pair(title, sub)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }

    if (!isLast) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
    }
}

/**
 * Format raw date string ("2026-10-01" or "2026-10-01 10:30") into human readable "Oct 1, 2026" or "Oct 1"
 */
private fun formatHumanReadableDate(rawDate: String): String {
    if (rawDate.isBlank()) return ""
    return try {
        val cleanDate = rawDate.split(" ").first()
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = parser.parse(cleanDate)
        if (date != null) {
            val formatter = SimpleDateFormat("MMM d", Locale.US)
            formatter.format(date)
        } else {
            cleanDate
        }
    } catch (_: Exception) {
        rawDate.take(10)
    }
}

@Composable
fun DashboardSkeletonLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun DashboardEmptyRoomsState(onAddRoomClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AddHome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Welcome to your PG Manager!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add your first room to start managing your PG effortlessly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddRoomClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Add Room", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DashboardErrorState(errorMessage: String, onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = Color(0xFFDC2626),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Operational Sync Failure",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFDC2626)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetryClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Retry")
        }
    }
}
