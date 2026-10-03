package com.example.features.dashboard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.core.language.rememberTranslation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.features.dashboard.domain.usecase.ActivityType
import com.example.features.dashboard.domain.usecase.DashboardSummary
import com.example.features.dashboard.domain.usecase.OccupancyStats
import com.example.features.dashboard.domain.usecase.RecentActivity
import com.example.features.dashboard.domain.usecase.UpcomingVacancyItem
import com.example.features.properties.ui.components.AddPropertyDialog
import com.example.features.properties.ui.components.PropertySelectorBottomSheet
import com.example.features.properties.ui.viewmodel.PropertyViewModel
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
    val selectedLanguage by com.example.core.language.AppLanguageManager.languageFlow.collectAsState()

    val activePgName = currentProperty?.propertyName ?: profile?.pgName?.ifBlank { "Reddy PG" } ?: "Reddy PG"
    val pgLocation = currentProperty?.city?.takeIf { it.isNotBlank() }
        ?: currentProperty?.address?.takeIf { it.isNotBlank() }
        ?: "Bhubaneswar"
    val ownerName = profile?.ownerName?.ifBlank { "Nithish Prasad" } ?: "Nithish Prasad"

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour, selectedLanguage) {
        if (selectedLanguage == "తెలుగు") {
            when {
                currentHour < 12 -> "శుభోదయం,"
                currentHour < 17 -> "శుభ మధ్యాహ్నం,"
                else -> "శుభ సాయంత్రం,"
            }
        } else {
            when {
                currentHour < 12 -> "Good Morning,"
                currentHour < 17 -> "Good Afternoon,"
                else -> "Good Evening,"
            }
        }
    }

    val formattedDate = remember {
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date())
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

    val bgScreenColor = Color(0xFFF6F8FB)

    Scaffold(
        bottomBar = {
            DashboardBottomBar(
                currentRoute = Screen.Dashboard.route,
                onNavigate = onNavigate,
                selectedLanguage = selectedLanguage
            )
        },
        containerColor = bgScreenColor,
        modifier = modifier.testTag("dashboard_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(bgScreenColor)
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
                            formattedDate = formattedDate,
                            selectedLanguage = selectedLanguage,
                            onLanguageSelect = { com.example.core.language.AppLanguageManager.setLanguage(it) },
                            summary = state.data,
                            onNavigate = onNavigate,
                            onLockRequested = onLockRequested,
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
    formattedDate: String,
    selectedLanguage: String,
    onLanguageSelect: (String) -> Unit,
    summary: DashboardSummary,
    onNavigate: (String) -> Unit,
    onLockRequested: () -> Unit,
    activePgName: String,
    pgLocation: String,
    onSwitchProperty: () -> Unit,
    activeBillingMonth: String,
    onSelectBillingMonth: (String) -> Unit
) {
    val initialLetter = activePgName.trim().firstOrNull()?.toString()?.uppercase() ?: "R"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_scroll_content"),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Top Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
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
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFE4D6))
                                .border(1.5.dp, Color(0xFFFFD1BA), CircleShape)
                                .clickable { onSwitchProperty() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initialLetter,
                                color = Color(0xFF9A3412),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 12.5.sp
                                )
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
                                        color = Color(0xFF0F172A),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 17.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Switch PG Property",
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = pgLocation,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right Actions: Language Toggle, Notification, Settings
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Language Toggle Pill [ EN | తెలుగు ]
                        LanguageTogglePill(
                            selectedLanguage = selectedLanguage,
                            onLanguageSelect = onLanguageSelect
                        )

                        // Notification Icon with Red Dot
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shadowElevation = 0.5.dp,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(19.dp)
                                )
                                // Red Notification Dot
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .align(Alignment.TopEnd)
                                        .padding(top = 2.dp, end = 2.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                )
                            }
                        }

                        // Settings Gear
                        Surface(
                            onClick = { onNavigate(Screen.SettingsHome.route) },
                            shape = CircleShape,
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shadowElevation = 0.5.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("dashboard_settings_button")
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Date Chip below header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 0.5.dp,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF334155)
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. QUICK ACTIONS
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedLanguage == "తెలుగు") "త్వరిత చర్యలు" else "Quick Actions",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 17.sp
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigate(Screen.Rooms.route) }
                    ) {
                        Text(
                            text = if (selectedLanguage == "తెలుగు") "అన్నీ చూడండి" else "See all",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "See all",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Add Room
                    QuickActionPastelCard(
                        title = if (selectedLanguage == "తెలుగు") "రూమ్\nజోడించు" else "Add\nRoom",
                        icon = Icons.Default.AddHome,
                        iconTint = Color(0xFF2563EB),
                        bgColor = Color(0xFFEBF3FE),
                        onClick = { onNavigate(Screen.AddRoom.route) },
                        modifier = Modifier.weight(1f)
                    )

                    // 2. Add Tenant
                    QuickActionPastelCard(
                        title = if (selectedLanguage == "తెలుగు") "అద్దెదారు\nజోడించు" else "Add\nTenant",
                        icon = Icons.Default.Group,
                        iconTint = Color(0xFF16A34A),
                        bgColor = Color(0xFFEDF9F0),
                        onClick = { onNavigate(Screen.AddTenant.route) },
                        modifier = Modifier.weight(1f)
                    )

                    // 3. Collect Rent
                    QuickActionPastelCard(
                        title = if (selectedLanguage == "తెలుగు") "అద్దె\nవసూలు" else "Collect\nRent",
                        icon = Icons.Default.AttachMoney,
                        iconTint = Color(0xFFEA580C),
                        bgColor = Color(0xFFFEF6E9),
                        onClick = { onNavigate(Screen.RentLedger.route) },
                        modifier = Modifier.weight(1f)
                    )

                    // 4. Add Expense
                    QuickActionPastelCard(
                        title = if (selectedLanguage == "తెలుగు") "ఖర్చు\nజోడించు" else "Add\nExpense",
                        icon = Icons.Default.Receipt,
                        iconTint = Color(0xFF9333EA),
                        bgColor = Color(0xFFF6EEFD),
                        onClick = { onNavigate(Screen.AddExpense.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. OVERVIEW CARDS
        item {
            var monthDropdownExpanded by remember { mutableStateOf(false) }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedLanguage == "తెలుగు") "అవలోకనం" else "Overview",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 17.sp
                        )
                    )
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .clickable { monthDropdownExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFF1E293B),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = activeBillingMonth,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = Color(0xFF1E293B),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Billing Month",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(15.dp)
                            )
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
                                            color = if (month == activeBillingMonth) Color(0xFF1769D1) else Color(0xFF1E293B)
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

                Spacer(modifier = Modifier.height(10.dp))

                // 2x2 Grid of Overview Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 1: Tenants / Total PG Capacity (Section 3 Requirement)
                    val activeTenants = summary.occupancy.activeTenantsCount
                    val totalCapacity = summary.occupancy.totalPgCapacity
                    val vacancies = summary.occupancy.vacanciesCount
                    val occupancyPercentage = summary.occupancy.homeOccupancyPercentage
                    val occupancyRatio = if (totalCapacity > 0) (activeTenants.toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f) else 0f

                    val occupancySubtitle = if (selectedLanguage == "తెలుగు") {
                        "${String.format(Locale.US, "%.1f", occupancyPercentage)}% ఆక్రమణ • $vacancies ఖాళీ" + (if (vacancies == 1) "" else "లు")
                    } else {
                        "${String.format(Locale.US, "%.1f", occupancyPercentage)}% Occupancy • $vacancies Vacanc" + (if (vacancies == 1) "y" else "ies")
                    }

                    OverviewOccupancyCard(
                        title = if (selectedLanguage == "తెలుగు") "అద్దెదారులు / సామర్థ్యం" else "Tenants / Total Capacity",
                        value = "$activeTenants / $totalCapacity Tenants",
                        subtitle = occupancySubtitle,
                        progress = occupancyRatio,
                        modifier = Modifier.weight(1f)
                    )

                    // Card 2: Monthly Revenue / Rent Received
                    val revSubtitle = if (summary.revenue.previousDuesReceived > 0) {
                        "+ ₹${String.format(Locale.US, "%,.0f", summary.revenue.previousDuesReceived)} Prev Dues"
                    } else {
                        activeBillingMonth
                    }

                    OverviewRevenueCard(
                        title = if (selectedLanguage == "తెలుగు") "వసూలైన అద్దె" else "Rent Received",
                        value = "₹${String.format(Locale.US, "%,.0f", summary.revenue.monthlyRevenue)}",
                        subtitle = revSubtitle,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 3: Pending Rent / Rent Still to Collect
                    val pendingAmount = summary.revenue.pendingRent
                    val pendingCount = if (pendingAmount > 0) 1 else 0

                    OverviewPendingRentCard(
                        title = if (selectedLanguage == "తెలుగు") "ఇంకా వసూలు చేయాల్సినది" else "Rent Still to Collect",
                        value = "₹${String.format(Locale.US, "%,.0f", pendingAmount)}",
                        subtitle = if (pendingCount > 0) "$activeBillingMonth remaining" else "All rent collected",
                        onClick = { onNavigate(Screen.RentLedger.route) },
                        modifier = Modifier.weight(1f)
                    )

                    // Card 4: Monthly Expenses
                    val profitLeft = summary.profit.netProfit
                    OverviewExpensesCard(
                        title = if (selectedLanguage == "తెలుగు") "ఖర్చులు" else "Expenses",
                        value = "₹${String.format(Locale.US, "%,.0f", summary.expenses.totalExpenses)}",
                        subtitle = "Left: ₹${String.format(Locale.US, "%,.0f", profitLeft)}",
                        onClick = { onNavigate(Screen.Expenses.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // WHAT NEEDS ATTENTION (Section 24)
        if (summary.whatNeedsAttention.isNotEmpty()) {
            item {
                WhatNeedsAttentionCard(
                    attentionItems = summary.whatNeedsAttention,
                    selectedLanguage = selectedLanguage,
                    onNavigate = onNavigate
                )
            }
        }

        // 4. UPCOMING VACANCIES
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (selectedLanguage == "తెలుగు") "రాబోయే ఖాళీలు" else "Upcoming Vacancies",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 17.sp
                            )
                        )
                        if (summary.upcomingVacancies.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFEF3C7))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${summary.upcomingVacancies.size}",
                                    color = Color(0xFFD97706),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigate(Screen.Tenants.route) }
                    ) {
                        Text(
                            text = if (selectedLanguage == "తెలుగు") "అన్నీ చూడండి" else "See all",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "See all",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEDF2F7)),
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
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedLanguage == "తెలుగు") "రాబోయే ఖాళీలు ఏవీ షెడ్యూల్ చేయబడలేదు" else "No upcoming vacancies scheduled",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        } else {
                            summary.upcomingVacancies.take(5).forEachIndexed { index, vacancy ->
                                UpcomingVacancyRow(
                                    vacancy = vacancy,
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

        // 5. RECENT ACTIVITY
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedLanguage == "తెలుగు") "ఇటీవలి కార్యకలాపాలు" else "Recent Activity",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 17.sp
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigate(Screen.RentLedger.route) }
                    ) {
                        Text(
                            text = if (selectedLanguage == "తెలుగు") "అన్నీ చూడండి" else "View All",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View All",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Card container holding activity items
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEDF2F7)),
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
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedLanguage == "తెలుగు") "కార్యకలాపాలు ఏవీ కనుగొనబడలేదు" else "No recent activities recorded",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        } else {
                            summary.recentActivities.take(6).forEachIndexed { index, activity ->
                                RecentActivityRow(
                                    activity = activity,
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
 * Top-Right Language Toggle Capsule [ EN | తెలుగు ]
 */
@Composable
private fun LanguageTogglePill(
    selectedLanguage: String,
    onLanguageSelect: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // EN Pill
            val isEn = selectedLanguage == "EN"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isEn) Color(0xFF2563EB) else Color.Transparent)
                    .clickable { onLanguageSelect("EN") }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EN",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isEn) Color.White else Color(0xFF64748B),
                        fontWeight = if (isEn) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.5.sp
                    )
                )
            }

            // Telugu Pill
            val isTe = selectedLanguage == "తెలుగు"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isTe) Color(0xFF2563EB) else Color.Transparent)
                    .clickable { onLanguageSelect("తెలుగు") }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "తెలుగు",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isTe) Color.White else Color(0xFF64748B),
                        fontWeight = if (isTe) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.5.sp
                    )
                )
            }
        }
    }
}

/**
 * 1. Quick Action Pastel Card
 */
@Composable
private fun QuickActionPastelCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        shadowElevation = 0.dp,
        modifier = modifier.height(118.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )

            // Title and Forward Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    ),
                    maxLines = 2
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 2. Overview Card: Rooms Occupied
 */
@Composable
private fun OverviewOccupancyCard(
    title: String,
    value: String,
    subtitle: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEDF2F7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier.height(140.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEBF3FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MeetingRoom,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Big Stat
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    fontSize = 22.sp
                )
            )

            // Subtitle + Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = Color(0xFF2563EB),
                    trackColor = Color(0xFFE2E8F0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

/**
 * 3. Overview Card: Monthly Revenue with Upward Sparkline
 */
@Composable
private fun OverviewRevenueCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEDF2F7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier.height(140.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEDF9F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "₹",
                        color = Color(0xFF16A34A),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Big Stat
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF16A34A),
                    fontSize = 22.sp
                )
            )

            // Subtitle + Mini Trend Line Canvas
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF16A34A),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                // Smooth green upward curve
                Canvas(
                    modifier = Modifier
                        .width(64.dp)
                        .height(20.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(0f, h * 0.8f)
                        cubicTo(w * 0.35f, h * 0.75f, w * 0.65f, h * 0.3f, w, h * 0.1f)
                    }
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF16A34A).copy(alpha = 0.25f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = path,
                        color = Color(0xFF16A34A),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * 4. Overview Card: Pending Rent (Alert red style)
 */
@Composable
private fun OverviewPendingRentCard(
    title: String,
    value: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEDF2F7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier.height(130.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEF2F2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Big Stat
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFDC2626),
                    fontSize = 22.sp
                )
            )

            // Subtitle + Forward Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 5. Overview Card: Monthly Expenses (Purple style)
 */
@Composable
private fun OverviewExpensesCard(
    title: String,
    value: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEDF2F7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier.height(130.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF6EEFD)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = Color(0xFF9333EA),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Big Stat
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF7C3AED),
                    fontSize = 22.sp
                )
            )

            // Subtitle + Forward Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 6. Recent Activity Row
 */
@Composable
private fun RecentActivityRow(
    activity: RecentActivity,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val (icon, iconTint, bgColor) = when (activity.type) {
        ActivityType.TENANT_ADDED -> Triple(Icons.Default.Person, Color(0xFF2563EB), Color(0xFFEBF3FE))
        ActivityType.RENT_PAID -> Triple(Icons.Default.AttachMoney, Color(0xFF16A34A), Color(0xFFEDF9F0))
        ActivityType.EXPENSE_ADDED -> Triple(Icons.Default.Receipt, Color(0xFF9333EA), Color(0xFFF6EEFD))
        ActivityType.ROOM_VACATED -> Triple(Icons.Default.MeetingRoom, Color(0xFF64748B), Color(0xFFF1F5F9))
    }

    val titleKey = when (activity.type) {
        ActivityType.TENANT_ADDED -> "Tenant Checked In"
        ActivityType.RENT_PAID -> if (activity.status == "Partial") "Rent Partially Paid" else "Rent Paid"
        ActivityType.EXPENSE_ADDED -> "Expense Recorded"
        ActivityType.ROOM_VACATED -> "Room Vacated"
    }
    val translatedTitle = rememberTranslation(titleKey)

    val translatedDescription = when (activity.type) {
        ActivityType.TENANT_ADDED -> "${activity.param1} ${rememberTranslation("moved into")} ${rememberTranslation("Room")} ${activity.param2} (${rememberTranslation("Bed")} ${activity.param3})"
        ActivityType.RENT_PAID -> "₹${String.format("%,.0f", activity.amount)} ${rememberTranslation("collected from")} ${activity.param1} (${rememberTranslation("Room")} ${activity.param2})"
        ActivityType.EXPENSE_ADDED -> "₹${String.format("%,.0f", activity.amount)} ${rememberTranslation("for")} ${activity.param1} - ${activity.param2}"
        ActivityType.ROOM_VACATED -> "${activity.param1} ${rememberTranslation("vacated")} ${rememberTranslation("Room")} ${activity.param2}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Description
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = translatedTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 13.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = translatedDescription,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Time / Date + Forward Chevron
        val parts = activity.date.split(" ")
        val displayTime = if (parts.size > 1) parts[1] else activity.date.takeLast(5)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = displayTime,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF64748B),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (!isLast) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFF1F5F9))
        )
    }
}

/**
 * 7. Upcoming Vacancy Row
 */
@Composable
private fun UpcomingVacancyRow(
    vacancy: UpcomingVacancyItem,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val initial = vacancy.tenantName.trim().firstOrNull()?.toString()?.uppercase() ?: "T"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag("upcoming_vacancy_card_${vacancy.tenantId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Circle (Warm Amber)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFFEF3C7)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD97706),
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Tenant Name and Room Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vacancy.tenantName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Room ${vacancy.roomNumber} • ${vacancy.bedId}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Vacating Date Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFEF3C7))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Vacating: ${vacancy.leavingDate}",
                    color = Color(0xFFB45309),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (!isLast) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFF1F5F9))
        )
    }
}

/**
 * Section 24: What Needs Attention Card
 */
@Composable
fun WhatNeedsAttentionCard(
    attentionItems: List<com.example.features.reports.domain.model.AttentionItem>,
    selectedLanguage: String,
    onNavigate: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_what_needs_attention_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (selectedLanguage == "తెలుగు") "దృష్టి సారించాల్సినవి" else "What Needs Attention",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E),
                        fontSize = 16.sp
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEF3C7))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${attentionItems.size}",
                        color = Color(0xFFB45309),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                attentionItems.take(4).forEach { item ->
                    val bgTint = when (item.severity) {
                        "ALERT" -> Color(0xFFFEF2F2)
                        "INFO" -> Color(0xFFEFF6FF)
                        else -> Color(0xFFFFFBEB)
                    }
                    val borderTint = when (item.severity) {
                        "ALERT" -> Color(0xFFFECACA)
                        "INFO" -> Color(0xFFBFDBFE)
                        else -> Color(0xFFFDE68A)
                    }
                    val textTint = when (item.severity) {
                        "ALERT" -> Color(0xFF991B1B)
                        "INFO" -> Color(0xFF1E40AF)
                        else -> Color(0xFF92400E)
                    }

                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = bgTint,
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderTint),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = item.actionRoute != null) {
                                item.actionRoute?.let { onNavigate(it) }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textTint,
                                        fontSize = 13.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF475569),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            if (item.actionRoute != null) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = textTint,
                                    modifier = Modifier.size(15.dp)
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
 * Modern Bottom Navigation Bar: Home | Rooms | Tenants | Payments | More
 */
@Composable
fun DashboardBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    selectedLanguage: String = "EN"
) {
    com.example.core.designsystem.AppBottomNavBar(
        currentTab = com.example.core.designsystem.MainTab.HOME,
        onNavigate = onNavigate
    )
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0xFFEBF3FE) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B)
                )
            )
        }
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
        CircularProgressIndicator(color = Color(0xFF2563EB))
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
            tint = Color(0xFF2563EB),
            modifier = Modifier.size(72.dp)
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
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddRoomClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
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
            modifier = Modifier.size(72.dp)
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
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetryClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Retry Sync")
        }
    }
}
