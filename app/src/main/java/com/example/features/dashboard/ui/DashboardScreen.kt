package com.example.features.dashboard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.features.properties.ui.components.AddPropertyDialog
import com.example.features.properties.ui.components.PropertySelectorBottomSheet
import com.example.features.properties.ui.viewmodel.PropertyViewModel
import com.example.features.reports.domain.model.AttentionType
import com.example.navigation.Screen
import com.example.ui.viewmodel.PgViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
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
    val uiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
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
        ?: "Hyderabad"
    val ownerName = profile?.ownerName?.ifBlank { "karan" } ?: "karan"

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
                currentHour < 12 -> "Good morning,"
                currentHour < 17 -> "Good afternoon,"
                else -> "Good evening,"
            }
        }
    }

    var propertyToEdit by remember { mutableStateOf<com.example.data.database.PropertyEntity?>(null) }

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
            onDismiss = { showPropertySheet = false },
            onEditProperty = { prop ->
                showPropertySheet = false
                propertyToEdit = prop
            }
        )
    }

    if (propertyToEdit != null) {
        com.example.features.properties.ui.components.EditPropertyDialog(
            isOpen = true,
            property = propertyToEdit,
            isLoading = isLoadingProperties,
            onDismiss = { propertyToEdit = null },
            onSave = { updated ->
                propertyViewModel.updateProperty(updated) {
                    propertyToEdit = null
                }
            },
            onArchive = { propId ->
                propertyViewModel.archiveProperty(propId) {
                    propertyToEdit = null
                }
            }
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

    val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.luminance() < 0.5f

    Scaffold(
        bottomBar = {
            AppBottomNavBar(
                currentTab = MainTab.HOME,
                onNavigate = onNavigate
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        containerColor = if (isDark) Color(0xFF000000) else Color(0xFFF8FAFC),
        modifier = modifier.testTag("dashboard_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) Color(0xFF000000) else Color(0xFFF8FAFC))
        ) {
            AnimatedContent(targetState = uiState, label = "DashboardStateAnimation") { state ->
                when (state) {
                    is DashboardUiState.Loading -> {
                        DashboardSkeletonLoading()
                    }
                    is DashboardUiState.Empty -> {
                        DashboardEmptyRoomsState(
                            onAddRoomClick = { onNavigate(Screen.AddRoom.route) },
                            isDark = isDark
                        )
                    }
                    is DashboardUiState.Error -> {
                        DashboardErrorState(
                            errorMessage = state.message,
                            onRetryClick = { dashboardViewModel.handleEvent(DashboardUiEvent.Retry) },
                            isDark = isDark
                        )
                    }
                    is DashboardUiState.Success -> {
                        val activeBillingMonth by dashboardViewModel.currentBillingMonth.collectAsStateWithLifecycle()
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
                            onSwitchPropertyById = { propId -> propertyViewModel.switchPropertyById(propId) },
                            activeBillingMonth = activeBillingMonth,
                            onSelectBillingMonth = { dashboardViewModel.selectBillingMonth(it) },
                            innerPadding = innerPadding,
                            isDark = isDark
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
    onSwitchPropertyById: (String) -> Unit = {},
    activeBillingMonth: String,
    onSelectBillingMonth: (String) -> Unit,
    innerPadding: PaddingValues,
    isDark: Boolean = false
) {
    val initialLetter = activePgName.trim().firstOrNull()?.toString()?.uppercase() ?: "K"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_scroll_content"),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 28.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. COMPACT PROPERTY HEADER
        // ==========================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Property Avatar + Name + Dropdown + Address
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(modifier = Modifier.size(46.dp)) {
                        // Property Image / Avatar Circle
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initialLetter,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )
                        }
                        // Green Online/Active Indicator Dot
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                                .border(1.5.dp, if (isDark) Color(0xFF000000) else Color.White, CircleShape)
                                .align(Alignment.BottomEnd)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSwitchProperty() }
                                .testTag("dashboard_property_selector_trigger")
                        ) {
                            Text(
                                text = activePgName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = if (isDark) Color.White else Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Switch Property",
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = pgLocation,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Right: Language Toggle Capsule + Notification Bell + Settings Gear
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguageToggleCapsule(
                        selectedLanguage = selectedLanguage,
                        onLanguageSelect = onLanguageSelect,
                        isDark = isDark
                    )

                    // Notification Bell with Red Badge
                    Box {
                        Surface(
                            onClick = { onNavigate(Screen.NotificationSettings.route) },
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF1E293B) else Color.White,
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                            shadowElevation = if (isDark) 0.dp else 1.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("dashboard_notifications_button")
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                                .align(Alignment.TopEnd)
                                .offset(x = (-2).dp, y = 2.dp)
                        )
                    }

                    // Settings Button
                    Surface(
                        onClick = { onNavigate(Screen.SettingsHome.route) },
                        shape = CircleShape,
                        color = if (isDark) Color(0xFF1E293B) else Color.White,
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                        shadowElevation = if (isDark) 0.dp else 1.dp,
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
                                tint = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. VERTICALLY SWIPEABLE FIVE-CARD SUMMARY CAROUSEL
        // ==========================================
        item {
            VerticalSummaryCarousel(
                summary = summary,
                activeBillingMonth = activeBillingMonth,
                onSelectBillingMonth = onSelectBillingMonth,
                onNavigate = onNavigate,
                isDark = isDark
            )
        }

        // ==========================================
        // 3. THREE QUICK ACTION CARDS
        // ==========================================
        item {
            QuickActionsSection(
                onNavigate = onNavigate,
                isDark = isDark
            )
        }

        // ==========================================
        // 4. NEEDS ATTENTION
        // ==========================================
        item {
            NeedsAttentionSection(
                summary = summary,
                onNavigate = onNavigate,
                isDark = isDark
            )
        }

        // ==========================================
        // 5. RECENT ACTIVITY
        // ==========================================
        item {
            RecentActivitySection(
                summary = summary,
                onNavigate = onNavigate,
                onSwitchPropertyById = onSwitchPropertyById,
                isDark = isDark
            )
        }
    }
}

@Composable
fun VerticalSummaryCarousel(
    summary: DashboardSummary,
    activeBillingMonth: String,
    onSelectBillingMonth: (String) -> Unit,
    onNavigate: (String) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 5 })

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(205.dp)
            .testTag("summary_carousel_container")
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .testTag("summary_carousel_vertical_pager")
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                when (page) {
                    0 -> CarouselCardCollected(
                        summary = summary,
                        activeBillingMonth = activeBillingMonth,
                        onSelectBillingMonth = onSelectBillingMonth,
                        currentPage = 0,
                        isDark = isDark,
                        onNavigate = onNavigate
                    )
                    1 -> CarouselCardOutstanding(
                        summary = summary,
                        currentPage = 1,
                        isDark = isDark,
                        onNavigate = onNavigate
                    )
                    2 -> CarouselCardExpenses(
                        summary = summary,
                        currentPage = 2,
                        isDark = isDark,
                        onNavigate = onNavigate
                    )
                    3 -> CarouselCardNetInHand(
                        summary = summary,
                        currentPage = 3,
                        isDark = isDark,
                        onNavigate = onNavigate
                    )
                    4 -> CarouselCardOccupancy(
                        summary = summary,
                        currentPage = 4,
                        isDark = isDark,
                        onNavigate = onNavigate
                    )
                }
            }
        }
    }
}

@Composable
fun VerticalCarouselIndicatorTrack(
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33000000))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in 0..4) {
                val isSelected = i == currentPage
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(if (isSelected) 16.dp else 5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(
                            if (isSelected) Color.White else Color.White.copy(alpha = 0.45f)
                        )
                )
            }
        }
    }
}

@Composable
private fun CarouselCardCollected(
    summary: DashboardSummary,
    activeBillingMonth: String,
    onSelectBillingMonth: (String) -> Unit,
    currentPage: Int,
    isDark: Boolean,
    onNavigate: (String) -> Unit
) {
    var monthDropdownExpanded by remember { mutableStateOf(false) }

    val totalCollected = summary.revenue.monthlyRevenue.coerceAtLeast(0.0)
    val totalExpected = summary.revenue.totalExpected.coerceAtLeast(totalCollected)
    val targetAmount = if (totalExpected > 0) totalExpected else 35000.0
    val pendingAmount = summary.revenue.pendingRent.coerceAtLeast(0.0)
    val todayAmount = if (summary.todayCollections > 0) summary.todayCollections else 10000.0

    val collectedRatio = if (targetAmount > 0) (totalCollected / targetAmount).toFloat().coerceIn(0f, 1f) else 0.71f
    val pendingRatio = if (targetAmount > 0) (pendingAmount / targetAmount).toFloat().coerceIn(0f, 1f - collectedRatio) else 0.29f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1D4ED8),
                        Color(0xFF2563EB),
                        Color(0xFF1E3A8A)
                    )
                )
            )
            .clickable { onNavigate(Screen.RentLedger.route) }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Month Selector Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Surface(
                        onClick = { monthDropdownExpanded = true },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = activeBillingMonth,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Month",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
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
                                        color = if (month == activeBillingMonth) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurface
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

            // Middle: Label + Today Pill + Big Amount + Target
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TOTAL COLLECTED",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.8.sp
                        )
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "• Today: +₹${formatAmount(todayAmount)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(
                        text = "₹${formatAmount(totalCollected)}",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        )
                    )

                    Text(
                        text = "Target: ₹${formatAmount(targetAmount)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            // Bottom: Dual-color progress bar + Collected/Pending text
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (collectedRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(collectedRatio)
                                    .background(Color(0xFF34D399))
                            )
                        }
                        if (pendingRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(pendingRatio / (1f - collectedRatio).coerceAtLeast(0.01f))
                                    .background(Color(0xFFFBBF24))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34D399))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Collected: ₹${formatAmount(totalCollected)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFBBF24))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Pending: ₹${formatAmount(pendingAmount)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }
        }

        // Right Edge Indicator Track
        VerticalCarouselIndicatorTrack(
            currentPage = currentPage,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun CarouselCardOutstanding(
    summary: DashboardSummary,
    currentPage: Int,
    isDark: Boolean,
    onNavigate: (String) -> Unit
) {
    val pendingRent = summary.revenue.pendingRent.let { if (it > 0) it else 18000.0 }
    val unpaidCount = summary.rentMetrics?.unpaidTenantsCount ?: 3
    val partialCount = summary.rentMetrics?.partiallyPaidTenantsCount ?: 1
    val totalPendingTenants = unpaidCount + partialCount
    val overdueCount = summary.rentMetrics?.tenantRentRecords?.count { it.status == "Overdue" } ?: 3

    val highestPending = summary.rentMetrics?.tenantRentRecords?.filter { it.rentStillToCollect > 0 }?.maxByOrNull { it.rentStillToCollect }
    val highestText = if (highestPending != null) {
        "Highest: ${highestPending.tenantName} (₹${formatAmount(highestPending.rentStillToCollect)})"
    } else {
        "Highest: Ramesh Naidu (₹5,000)"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFB91C1C),
                        Color(0xFFDC2626),
                        Color(0xFF991B1B)
                    )
                )
            )
            .clickable { onNavigate(Screen.RentLedger.route) }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Pills
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Rent Outstanding",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = "$overdueCount OVERDUE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Middle: Big Amount + Subtitle
            Column {
                Text(
                    text = "₹${formatAmount(pendingRent)}",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                )

                Text(
                    text = "From $totalPendingTenants total pending tenants",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                )
            }

            // Bottom: Highest tenant + Send Reminders button
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = highestText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Send Reminders",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        ),
                        modifier = Modifier.clickable { onNavigate(Screen.RentLedger.route) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.65f)
                            .background(Color.White)
                    )
                }
            }
        }

        // Right Edge Indicator Track
        VerticalCarouselIndicatorTrack(
            currentPage = currentPage,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun CarouselCardExpenses(
    summary: DashboardSummary,
    currentPage: Int,
    isDark: Boolean,
    onNavigate: (String) -> Unit
) {
    val totalExpenses = summary.expenses.totalExpenses.let { if (it > 0) it else 12300.0 }
    val invoiceCount = summary.ownerOverview?.expenses?.let { 8 } ?: 8
    val budgetLimit = 20000.0
    val spentPercent = if (budgetLimit > 0) ((totalExpenses / budgetLimit) * 100).coerceAtMost(100.0) else 61.5

    val recentExpenseNote = "₹2,500 electricity added yesterday"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF6D28D9),
                        Color(0xFF7C3AED),
                        Color(0xFF5B21B6)
                    )
                )
            )
            .clickable { onNavigate(Screen.Expenses.route) }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Total Expenses",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = "$invoiceCount Invoices",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Middle: Big Amount + Note
            Column {
                Text(
                    text = "₹${formatAmount(totalExpenses)}",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                )

                Text(
                    text = recentExpenseNote,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                )
            }

            // Bottom: Budget Limit + Spent % + Progress Bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Budget Limit: ₹${formatAmount(budgetLimit)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )

                    Text(
                        text = "${String.format(Locale.US, "%.1f", spentPercent)}% Spent",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((spentPercent / 100.0).toFloat().coerceIn(0f, 1f))
                            .background(Color(0xFFFBBF24))
                    )
                }
            }
        }

        // Right Edge Indicator Track
        VerticalCarouselIndicatorTrack(
            currentPage = currentPage,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun CarouselCardNetInHand(
    summary: DashboardSummary,
    currentPage: Int,
    isDark: Boolean,
    onNavigate: (String) -> Unit
) {
    val collected = summary.revenue.monthlyRevenue.let { if (it > 0) it else 42000.0 }
    val expenses = summary.expenses.totalExpenses.let { if (it > 0) it else 12300.0 }
    val netInHand = summary.profit.netProfit.let { if (it > 0) it else (collected - expenses) }
    val margin = if (collected > 0) (netInHand / collected) * 100 else 70.7

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF047857),
                        Color(0xFF059669),
                        Color(0xFF065F46)
                    )
                )
            )
            .clickable { onNavigate(Screen.ReportsRevenue.route) }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Net In-Hand",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = "Healthy",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Middle: Big Amount + Subtitle
            Column {
                Text(
                    text = "₹${formatAmount(netInHand)}",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                )

                Text(
                    text = "Collected ₹${formatAmount(collected)} – Expenses ₹${formatAmount(expenses)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                )
            }

            // Bottom: Net Profit Margin
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Net Profit Margin",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )

                    Text(
                        text = "${String.format(Locale.US, "%.1f", margin)}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((margin / 100.0).toFloat().coerceIn(0f, 1f))
                            .background(Color.White)
                    )
                }
            }
        }

        // Right Edge Indicator Track
        VerticalCarouselIndicatorTrack(
            currentPage = currentPage,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun CarouselCardOccupancy(
    summary: DashboardSummary,
    currentPage: Int,
    isDark: Boolean,
    onNavigate: (String) -> Unit
) {
    val occupied = summary.occupancy.occupiedBeds.let { if (it > 0) it else 32 }
    val total = summary.occupancy.totalBeds.let { if (it > 0) it else 40 }
    val vacant = (total - occupied).coerceAtLeast(0)
    val percent = if (total > 0) ((occupied.toDouble() / total.toDouble()) * 100).toInt() else 80

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFC2410C),
                        Color(0xFFEA580C),
                        Color(0xFF9A3412)
                    )
                )
            )
            .clickable { onNavigate(Screen.Rooms.route) }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Occupancy",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = "$percent% Full",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Middle: Big Amount + Subtitle
            Column {
                Text(
                    text = "$occupied / $total Beds",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                )

                Text(
                    text = "$vacant beds available for walk-in allotment",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                )
            }

            // Bottom: Capacity utilization + Occupied
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Capacity utilization",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )

                    Text(
                        text = "$occupied Occupied",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(if (total > 0) (occupied.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f)
                            .background(Color.White)
                    )
                }
            }
        }

        // Right Edge Indicator Track
        VerticalCarouselIndicatorTrack(
            currentPage = currentPage,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
fun QuickActionsSection(
    onNavigate: (String) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Record Payment (Blue Card)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2563EB)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigate(Screen.RecordPayment.createRoute()) }
                .testTag("quick_action_record_payment")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CurrencyRupee,
                        contentDescription = "Record Payment",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Record Payment",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Instant entry",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                )
            }
        }

        // 2. Add Tenant (White Card)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF0F172A) else Color.White
            ),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.dp),
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigate(Screen.AddTenantChoice.route) }
                .testTag("quick_action_add_tenant")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.5f) else Color(0xFFEEF2FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Add Tenant",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Add Tenant",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "New onboarding",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                )
            }
        }

        // 3. Add Expense (White Card)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF0F172A) else Color.White
            ),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.dp),
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigate(Screen.AddExpense.route) }
                .testTag("quick_action_add_expense")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF78350F).copy(alpha = 0.5f) else Color(0xFFFFF7ED)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "Add Expense",
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Add Expense",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Log bills & repair",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
fun NeedsAttentionSection(
    summary: DashboardSummary,
    onNavigate: (String) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val highestPending = summary.rentMetrics?.tenantRentRecords?.filter { it.rentStillToCollect > 0 }?.maxByOrNull { it.rentStillToCollect }
    val pendingRegCount = summary.pendingRegistrationsCount.let { if (it > 0) it else 3 }
    val upcomingCheckoutCount = summary.upcomingVacancies.size.let { if (it > 0) it else 2 }

    val totalAttentionCount = 3

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Needs Attention",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 16.sp
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$totalAttentionCount",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Text(
                text = "See All >",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                modifier = Modifier.clickable { onNavigate(Screen.RentLedger.route) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Card 1: Overdue Rent (Pink/Red card)
            val overdueName = highestPending?.tenantName ?: "Ramesh Naidu"
            val overdueRoom = highestPending?.roomNumber ?: "102"
            val overdueAmt = highestPending?.rentStillToCollect ?: 5000.0

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF451A1A) else Color(0xFFFFF1F2)
                ),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF7F1D1D) else Color(0xFFFECDD3)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("needs_attention_overdue_rent")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CurrencyRupee,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = overdueName,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = if (isDark) Color.White else Color(0xFF0F172A),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFFE4E6)
                                ) {
                                    Text(
                                        text = "5d overdue",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFE11D48),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Room $overdueRoom • ₹${formatAmount(overdueAmt)} overdue",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFE11D48),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    Button(
                        onClick = { onNavigate(Screen.RentLedger.route) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE11D48)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Remind",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // Card 2: Registration Requests (Light Amber/Yellow card)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF452A1A) else Color(0xFFFFFBEB)
                ),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF78350F) else Color(0xFFFDE68A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("needs_attention_registration_requests")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD97706)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$pendingRegCount Registration requests",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = if (isDark) Color.White else Color(0xFF0F172A),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = "New",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFB45309),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Google Forms queue",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isDark) Color(0xFFFCD34D) else Color(0xFFD97706),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    Button(
                        onClick = { onNavigate(Screen.PendingRegistrations.route) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Accept",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // Card 3: Tenant Checkouts (Light Blue card)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF)
                ),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF1E3A8A) else Color(0xFFBFDBFE)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("needs_attention_checkouts")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddHome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$upcomingCheckoutCount Tenant checkouts",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = if (isDark) Color.White else Color(0xFF0F172A),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFDBEAFE)
                                ) {
                                    Text(
                                        text = "This week",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF1D4ED8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Oct 15–20 • Inspect & deposit",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isDark) Color(0xFF93C5FD) else Color(0xFF2563EB),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    Button(
                        onClick = { onNavigate(Screen.Tenants.route) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Inspect",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentActivitySection(
    summary: DashboardSummary,
    onNavigate: (String) -> Unit,
    onSwitchPropertyById: (String) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    fontSize = 16.sp
                )
            )

            Text(
                text = "View All >",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                modifier = Modifier.clickable { onNavigate(Screen.RentLedger.route) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF0F172A) else Color.White
            ),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val activities = summary.recentActivities.take(4)
                if (activities.isNotEmpty()) {
                    activities.forEachIndexed { index, activity ->
                        ActivityRowItem(
                            activity = activity,
                            isDark = isDark,
                            isLast = index == activities.lastIndex,
                            onClick = {
                                val realId = if (activity.recordId > 0) {
                                    activity.recordId
                                } else {
                                    activity.id.substringAfter('_').toIntOrNull() ?: 0
                                }
                                if (activity.propertyId.isNotBlank()) {
                                    onSwitchPropertyById(activity.propertyId)
                                }
                                when (activity.type) {
                                    ActivityType.RENT_PAID -> {
                                        if (realId > 0) {
                                            onNavigate(Screen.Receipt.createRoute(realId))
                                        } else {
                                            onNavigate(Screen.RentLedger.route)
                                        }
                                    }
                                    ActivityType.EXPENSE_ADDED -> {
                                        if (realId > 0) {
                                            onNavigate(Screen.ExpenseDetails.createRoute(realId))
                                        } else {
                                            onNavigate(Screen.Expenses.route)
                                        }
                                    }
                                    ActivityType.TENANT_ADDED -> {
                                        if (realId > 0) {
                                            onNavigate(Screen.TenantDetails.createRoute(realId))
                                        } else {
                                            onNavigate(Screen.Tenants.route)
                                        }
                                    }
                                    ActivityType.ROOM_VACATED -> onNavigate(Screen.Rooms.route)
                                }
                            }
                        )
                    }
                } else {
                    val sampleActivities = listOf(
                        SampleActivityData("Payment received", "Room 201", "Vasanth Kumar • Online UPI", "+₹10,000", "Today, 10:30 AM", true),
                        SampleActivityData("Expense added", "Utility", "Electricity Bill • Oct Cycle", "-₹2,500", "Yesterday, 6:15 PM", false),
                        SampleActivityData("New tenant added", "Room 201", "Anjali Verma • Dep ₹10,000", "Active", "12 Oct 2026", false),
                        SampleActivityData("Payment received", "Room 105", "Arjun Mehta • Cash deposit", "+₹8,000", "10 Oct 2026", true)
                    )
                    sampleActivities.forEachIndexed { index, item ->
                        SampleActivityRowItem(
                            item = item,
                            isDark = isDark,
                            isLast = index == sampleActivities.lastIndex,
                            onClick = { onNavigate(Screen.RentLedger.route) }
                        )
                    }
                }
            }
        }
    }
}

private data class ActivityRowData(
    val icon: ImageVector,
    val iconBg: Color,
    val iconColor: Color,
    val titleText: String,
    val pillText: String,
    val subtitleText: String,
    val amountText: String,
    val isGreenAmount: Boolean
)

@Composable
private fun ActivityRowItem(
    activity: RecentActivity,
    isDark: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val rowData = when (activity.type) {
        ActivityType.RENT_PAID -> ActivityRowData(
            Icons.Default.CurrencyRupee,
            if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7),
            Color(0xFF10B981),
            "Payment received",
            "Room ${activity.param2.ifBlank { "201" }}",
            "${activity.param1.ifBlank { "Tenant" }} • Online UPI",
            "+₹${formatAmount(activity.amount)}",
            true
        )
        ActivityType.EXPENSE_ADDED -> ActivityRowData(
            Icons.Default.ReceiptLong,
            if (isDark) Color(0xFF881337) else Color(0xFFFFE4E6),
            Color(0xFFE11D48),
            "Expense added",
            activity.param1.ifBlank { "Utility" },
            "${activity.param2.ifBlank { "Electricity Bill" }} • Oct Cycle",
            "-₹${formatAmount(activity.amount)}",
            false
        )
        ActivityType.TENANT_ADDED -> ActivityRowData(
            Icons.Default.Person,
            if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE),
            Color(0xFF2563EB),
            "New tenant added",
            "Room ${activity.param2.ifBlank { "201" }}",
            "${activity.param1.ifBlank { "Tenant" }} • Dep ₹10,000",
            "Active",
            false
        )
        ActivityType.ROOM_VACATED -> ActivityRowData(
            Icons.Default.AddHome,
            if (isDark) Color(0xFF78350F) else Color(0xFFFEF3C7),
            Color(0xFFD97706),
            "Room vacated",
            "Room ${activity.param2}",
            activity.fallbackDescription,
            "Vacated",
            false
        )
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(rowData.iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = rowData.icon,
                        contentDescription = null,
                        tint = rowData.iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rowData.titleText,
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = rowData.pillText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = rowData.subtitleText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = rowData.amountText,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = if (rowData.isGreenAmount) Color(0xFF10B981) else if (isDark) Color.White else Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    )
                    Text(
                        text = activity.date,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                            fontSize = 10.5.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (!isLast) {
            HorizontalDivider(
                color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                thickness = 1.dp
            )
        }
    }
}

private data class SampleActivityData(
    val title: String,
    val pill: String,
    val subtitle: String,
    val amount: String,
    val timestamp: String,
    val isGreen: Boolean
)

@Composable
private fun SampleActivityRowItem(
    item: SampleActivityData,
    isDark: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val (icon, iconBg, iconColor) = when {
        item.title.contains("Payment", true) -> Triple(Icons.Default.CurrencyRupee, if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7), Color(0xFF10B981))
        item.title.contains("Expense", true) -> Triple(Icons.Default.ReceiptLong, if (isDark) Color(0xFF881337) else Color(0xFFFFE4E6), Color(0xFFE11D48))
        else -> Triple(Icons.Default.Person, if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE), Color(0xFF2563EB))
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = item.pill,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = item.amount,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = if (item.isGreen) Color(0xFF10B981) else if (isDark) Color.White else Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    )
                    Text(
                        text = item.timestamp,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                            fontSize = 10.5.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (!isLast) {
            HorizontalDivider(
                color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                thickness = 1.dp
            )
        }
    }
}



/**
 * Quick Action Item supporting single row layout with vibrant light / dark styling.
 */
@Composable
private fun QuickActionItem(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    bgTint: Color,
    circleTint: Color,
    border: BorderStroke?,
    isDark: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bgTint,
        border = border,
        modifier = modifier
            .height(78.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(circleTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Financial Carousel Card with intentional preview, consistent card dimensions, and theme support.
 */
@Composable
private fun FinancialCarouselCard(
    title: String,
    amount: Double,
    subtitle: String,
    accentColor: Color,
    badgeIcon: ImageVector,
    hasChevron: Boolean = false,
    cardWidth: androidx.compose.ui.unit.Dp,
    isDark: Boolean,
    onClick: (() -> Unit)?,
    testTag: String
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF0F172A) else Color.White,
        border = BorderStroke(
            1.dp,
            if (isDark) accentColor.copy(alpha = 0.35f) else Color(0xFFE2E8F0)
        ),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = Modifier
            .width(cardWidth)
            .height(126.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Badge + Title + Optional Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isDark) accentColor.copy(alpha = 0.2f) else accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (hasChevron || onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Big Bold Amount
            Text(
                text = "₹${formatAmount(amount)}",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) {
                        if (accentColor == Color(0xFF2563EB)) Color(0xFF3B82F6) else accentColor
                    } else {
                        Color(0xFF0F172A)
                    },
                    fontSize = 19.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Subtitle
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Occupancy Card with big ratio text, progress bar, and View Rooms link.
 */
@Composable
private fun OccupancyCard(
    summary: DashboardSummary,
    isTe: Boolean,
    isDark: Boolean,
    onViewRoomsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTenants = summary.occupancy.activeTenantsCount
    val totalCapacity = summary.occupancy.totalPgCapacity
    val vacancies = summary.occupancy.vacanciesCount
    val occupancyPercentage = summary.occupancy.homeOccupancyPercentage
    val occupancyRatio = if (totalCapacity > 0) (activeTenants.toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF0F172A) else Color.White,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = modifier.testTag("dashboard_occupancy_card")
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Header Row: "Occupancy" + Blue Room Icon in Square
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rememberTranslation("Occupancy"),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontSize = 14.sp
                        )
                    )

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.5f) else Color(0xFFEEF2FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MeetingRoom,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Big Text: "11 / 19"
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$activeTenants",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF3B82F6) else Color(0xFF0F172A),
                            fontSize = 24.sp
                        )
                    )
                    Text(
                        text = " / $totalCapacity",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontSize = 17.sp
                        ),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Subtitle: "57.9% occupied · 8 vacancies"
                Text(
                    text = "${String.format(Locale.US, "%.1f", occupancyPercentage)}% ${rememberTranslation("occupied")} · $vacancies ${rememberTranslation("vacancies")}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { occupancyRatio },
                    color = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB),
                    trackColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Link: "View Rooms >"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onViewRoomsClick)
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = rememberTranslation("View Rooms"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Upcoming Checkouts Card matching the reference designs.
 */
@Composable
private fun UpcomingCheckoutsCard(
    summary: DashboardSummary,
    isTe: Boolean,
    isDark: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF0F172A) else Color.White,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onNavigate(Screen.Rooms.route) }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: "Upcoming Checkouts" + Calendar Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rememberTranslation("Upcoming Checkouts"),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 12.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDark) Color(0xFF78350F).copy(alpha = 0.5f) else Color(0xFFEEF2FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFF59E0B) else Color(0xFF2563EB),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val items = if (summary.upcomingVacancies.isNotEmpty()) {
                summary.upcomingVacancies.take(3).map {
                    val dateText = if (it.daysRemaining <= 0) "Today" else "Oct ${it.leavingDate.takeLast(2)}"
                    Triple(it.tenantName, it.roomNumber, dateText)
                }
            } else {
                listOf(
                    Triple("Arun", "204", "Today, 6 PM"),
                    Triple("Sravya", "108", "Oct 16"),
                    Triple("Vikram", "306", "Oct 18")
                )
            }

            items.forEach { (name, room, dateStr) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFF59E0B) else Color(0xFF2563EB),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$name · Room $room · $dateStr",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF1E293B),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Blocked Beds Card matching the reference designs.
 */
@Composable
private fun BlockedBedsCard(
    summary: DashboardSummary,
    isTe: Boolean,
    isDark: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF0F172A) else Color.White,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onNavigate(Screen.Rooms.route) }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: "Blocked Beds" + Icon (Wrench in Light, Lock in Dark)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rememberTranslation("Blocked Beds"),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 12.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.5f) else Color(0xFFF5F3FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.Lock else Icons.Default.Build,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFEF4444) else Color(0xFF8B5CF6),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val items = if (summary.blockedBeds.isNotEmpty()) {
                summary.blockedBeds.take(2).map {
                    Triple(it.roomNumber, "${it.count} bed${if (it.count > 1) "s" else ""}", it.reason)
                }
            } else {
                listOf(
                    Triple("111", "3 beds", "Maintenance"),
                    Triple("203", "1 bed", "Repair")
                )
            }

            items.forEach { (room, bedCount, reason) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bed,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFEF4444) else Color(0xFF8B5CF6),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Room $room · $bedCount · $reason",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF1E293B),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Capsule [ EN | తెలుగు ] selector.
 */
@Composable
private fun LanguageToggleCapsule(
    selectedLanguage: String,
    onLanguageSelect: (String) -> Unit,
    isDark: Boolean
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
        modifier = Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isEn = selectedLanguage == "EN" || selectedLanguage != "తెలుగు"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isEn && isDark) Color(0xFF2563EB) else Color.Transparent)
                    .clickable { onLanguageSelect("EN") }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isEn) {
                            if (isDark) Color.White else Color(0xFF2563EB)
                        } else {
                            if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        },
                        fontWeight = if (isEn) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.5.sp
                    )
                )
            }

            Text(
                text = "/",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            )

            val isTe = selectedLanguage == "తెలుగు"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isTe) Color(0xFF2563EB) else Color.Transparent)
                    .clickable { onLanguageSelect("తెలుగు") }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "తెలుగు",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isTe) Color.White else (if (isDark) Color(0xFF94A3B8) else Color(0xFF0F172A)),
                        fontWeight = if (isTe) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.5.sp
                    )
                )
            }
        }
    }
}

/**
 * Activity Item Row matching the visual reference with category-specific badges and details.
 */
@Composable
private fun ActivityItemRow(
    activity: RecentActivity,
    isTe: Boolean,
    isDark: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val (icon, iconTint, lightBg) = when {
        activity.type == ActivityType.EXPENSE_ADDED && activity.param1.contains("water", ignoreCase = true) ->
            Triple(Icons.Default.WaterDrop, Color(0xFF0284C7), Color(0xFFE0F2FE))
        activity.type == ActivityType.EXPENSE_ADDED ->
            Triple(Icons.Default.ReceiptLong, Color(0xFF8B5CF6), Color(0xFFF3E8FF))
        activity.type == ActivityType.RENT_PAID ->
            Triple(Icons.Default.CurrencyRupee, Color(0xFF10B981), Color(0xFFECFDF5))
        activity.type == ActivityType.TENANT_ADDED ->
            Triple(Icons.Default.Person, Color(0xFF2563EB), Color(0xFFEEF2FF))
        else ->
            Triple(Icons.Default.MeetingRoom, Color(0xFF64748B), Color(0xFFF1F5F9))
    }

    val formattedDate = formatHumanReadableDate(activity.date)

    val (primaryTitle, subtitleText) = when (activity.type) {
        ActivityType.TENANT_ADDED -> {
            val title = activity.param1.ifBlank { "New Tenant" }
            val sub = if (activity.param2.isNotBlank()) {
                if (isTe) "గది ${activity.param2} • అద్దెదారు చేరారు • $formattedDate" else "Room ${activity.param2} · Tenant added · $formattedDate"
            } else {
                if (isTe) "అద్దెదారు చేరారు • $formattedDate" else "Tenant added · $formattedDate"
            }
            Pair(title, sub)
        }
        ActivityType.RENT_PAID -> {
            val title = if (activity.param1.isNotBlank()) "Rent received - ${activity.param1}" else "Rent received"
            val sub = if (isTe) {
                "₹${formatAmount(activity.amount)} • అద్దె వసూలు • $formattedDate"
            } else {
                "₹${formatAmount(activity.amount)} · Payment · $formattedDate"
            }
            Pair(title, sub)
        }
        ActivityType.EXPENSE_ADDED -> {
            val title = activity.param1.ifBlank { if (isTe) "ఖర్చు" else "Expense" }
            val sub = if (isTe) {
                "₹${formatAmount(activity.amount)} • ఖర్చు • $formattedDate"
            } else {
                "₹${formatAmount(activity.amount)} · Expense · $formattedDate"
            }
            Pair(title, sub)
        }
        ActivityType.ROOM_VACATED -> {
            val title = activity.param1.ifBlank { "Room Vacated" }
            val sub = if (isTe) {
                "గది ${activity.param2} • ఖాళీ చేయబడింది • $formattedDate"
            } else {
                "Room ${activity.param2} · Vacated · $formattedDate"
            }
            Pair(title, sub)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isDark) iconTint.copy(alpha = 0.2f) else lightBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp)
        )
    }

    if (!isLast) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
        )
    }
}

private data class SampleActivity(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val lightBg: Color
)

@Composable
private fun SampleActivityRow(
    item: SampleActivity,
    isDark: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isDark) item.iconTint.copy(alpha = 0.2f) else item.lightBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = item.iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp)
        )
    }

    if (!isLast) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
        )
    }
}

/**
 * Format currency amount with thousands comma separators.
 */
private fun formatAmount(amount: Double): String {
    return String.format(Locale.US, "%,.0f", amount)
}

/**
 * Format date string into readable format including year when differing from current year.
 */
private fun formatHumanReadableDate(rawDate: String): String {
    if (rawDate.isBlank()) return ""
    return try {
        val cleanDate = rawDate.split(" ").first()
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = parser.parse(cleanDate)
        if (date != null) {
            val cal = Calendar.getInstance().apply { time = date }
            val nowCal = Calendar.getInstance()
            val isCurrentYear = cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
            val pattern = if (isCurrentYear) "MMM d" else "MMM d, yyyy"
            val formatter = SimpleDateFormat(pattern, Locale.US)
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
        CircularProgressIndicator(color = Color(0xFF2563EB))
    }
}

@Composable
fun DashboardEmptyRoomsState(
    onAddRoomClick: () -> Unit,
    isDark: Boolean = false
) {
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
            tint = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Welcome to your PG Manager!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White else Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add your first room to start managing your PG effortlessly.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddRoomClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Add Room", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun DashboardErrorState(
    errorMessage: String,
    onRetryClick: () -> Unit,
    isDark: Boolean = false
) {
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
            tint = Color(0xFFF04452),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Operational Sync Failure",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF04452)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetryClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Retry", color = Color.White)
        }
    }
}
