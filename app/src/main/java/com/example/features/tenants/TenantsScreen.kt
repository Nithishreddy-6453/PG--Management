package com.example.features.tenants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.designsystem.AppBottomNavBar
import com.example.core.designsystem.MainTab
import com.example.core.language.GlobalLanguageToggle
import com.example.core.language.rememberTranslation
import com.example.core.util.PgDateUtil
import com.example.data.database.TenantEntity
import com.example.features.tenants.ui.viewmodel.TenantListUiState
import com.example.features.tenants.ui.viewmodel.TenantListViewModel
import com.example.navigation.Screen
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantsScreen(
    viewModel: TenantListViewModel,
    onBackClick: () -> Unit,
    onTenantClick: (Int) -> Unit,
    onAddTenantClick: () -> Unit,
    onImportExcelClick: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showRoomFilterMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Translations
    val titleText = rememberTranslation("Tenants")
    val addTenantText = rememberTranslation("Add Tenant")
    val searchPlaceholder = rememberTranslation("Search tenant, phone or room...")
    val tenantRegistrationTitle = rememberTranslation("Tenant Registration")
    val shareFormText = rememberTranslation("Share Form")
    val allRoomsText = rememberTranslation("All Rooms")
    val filterText = rememberTranslation("Filter")

    Scaffold(
        bottomBar = {
            AppBottomNavBar(
                currentTab = MainTab.TENANTS,
                onNavigate = onNavigate
            )
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier
            .fillMaxSize()
            .testTag("tenants_screen_container")
    ) { innerPadding ->
        when (val currentState = state) {
            is TenantListUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF2563EB),
                        modifier = Modifier.testTag("tenants_loading_indicator")
                    )
                }
            }

            is TenantListUiState.Empty -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color(0xFFF8FAFC)),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        TenantsHeader(
                            titleText = titleText,
                            addTenantText = addTenantText,
                            onAddTenantClick = onAddTenantClick,
                            onNavigate = onNavigate,
                            onImportExcelClick = onImportExcelClick
                        )
                    }
                    item {
                        EmptyTenantsView(
                            onAddTenantClick = onAddTenantClick,
                            onRegistrationFormClick = { onNavigate(Screen.TenantRegistrationForm.route) }
                        )
                    }
                }
            }

            is TenantListUiState.Success -> {
                // The ENTIRE Tenants page belongs to one continuous vertical scroll container
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color(0xFFF8FAFC))
                        .testTag("tenants_list"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Header (Scrolls upward together with all content)
                    item {
                        TenantsHeader(
                            titleText = titleText,
                            addTenantText = addTenantText,
                            onAddTenantClick = onAddTenantClick,
                            onNavigate = onNavigate,
                            onImportExcelClick = onImportExcelClick
                        )
                    }

                    // 2. Search Bar
                    item {
                        SearchInputField(
                            query = currentState.searchQuery,
                            onQueryChange = { viewModel.onSearchQueryChanged(it) },
                            placeholderText = searchPlaceholder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }

                    // 3. Tenant Registration Secondary Card
                    item {
                        TenantRegistrationCard(
                            pendingCount = currentState.pendingSubmissionsCount,
                            onViewSubmissions = { onNavigate(Screen.PendingRegistrations.route) },
                            onShareForm = { onNavigate(Screen.TenantRegistrationForm.route) },
                            titleText = tenantRegistrationTitle,
                            shareText = shareFormText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }

                    // 4. Status Filter Chips (All, Active, Leaving Soon, Vacated, New)
                    item {
                        StatusFilterChipsRow(
                            selectedStatus = currentState.selectedOccupancyFilter,
                            totalCount = currentState.totalCount,
                            activeCount = currentState.activeCount,
                            leavingSoonCount = currentState.leavingSoonCount,
                            vacatedCount = currentState.vacatedCount,
                            newCount = currentState.newCount,
                            onStatusSelected = { viewModel.onOccupancyFilterChanged(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 5. Room Filter Button + Filter Button Row
                    item {
                        RoomAndFilterRow(
                            rooms = currentState.roomsList,
                            selectedRoom = currentState.selectedRoomFilter,
                            onRoomSelected = { viewModel.onRoomFilterChanged(it) },
                            onOpenFilterSheet = { showFilterSheet = true },
                            allRoomsLabel = allRoomsText,
                            filterLabel = filterText,
                            showRoomMenu = showRoomFilterMenu,
                            onDismissRoomMenu = { showRoomFilterMenu = false },
                            onToggleRoomMenu = { showRoomFilterMenu = !showRoomFilterMenu },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }

                    // 6. Result Count & Sort Selector Row
                    item {
                        ResultAndSortRow(
                            count = currentState.tenants.size,
                            sortBy = currentState.sortBy,
                            showSortMenu = showSortMenu,
                            onToggleSortMenu = { showSortMenu = !showSortMenu },
                            onDismissSortMenu = { showSortMenu = false },
                            onSortSelected = { viewModel.onSortByChanged(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }

                    // 7. Tenant Cards List or Filtered Empty View
                    if (currentState.tenants.isEmpty()) {
                        item {
                            FilteredEmptyView(
                                isSearchActive = currentState.searchQuery.isNotBlank(),
                                onClearSearch = { viewModel.onSearchQueryChanged("") },
                                onClearFilters = {
                                    viewModel.onSearchQueryChanged("")
                                    viewModel.onRoomFilterChanged("All")
                                    viewModel.onOccupancyFilterChanged("All")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp)
                            )
                        }
                    } else {
                        items(currentState.tenants, key = { it.id }) { tenant ->
                            TenantCardItem(
                                tenant = tenant,
                                onClick = { onTenantClick(tenant.id) }
                            )
                        }
                    }

                    // Safety spacer so the last tenant card can be comfortably scrolled well above the bottom bar
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Full Filter Bottom Sheet
                if (showFilterSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showFilterSheet = false },
                        containerColor = Color.White
                    ) {
                        TenantFilterBottomSheetContent(
                            rooms = currentState.roomsList,
                            selectedRoom = currentState.selectedRoomFilter,
                            selectedOccupancy = currentState.selectedOccupancyFilter,
                            onApply = { room, occupancy ->
                                viewModel.onRoomFilterChanged(room)
                                viewModel.onOccupancyFilterChanged(occupancy)
                                showFilterSheet = false
                            },
                            onClearAll = {
                                viewModel.onRoomFilterChanged("All")
                                viewModel.onOccupancyFilterChanged("All")
                                showFilterSheet = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TenantsHeader(
    titleText: String,
    addTenantText: String,
    onAddTenantClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onImportExcelClick: () -> Unit
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFFF8FAFC),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .testTag("tenants_top_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Page Title
            Text(
                text = titleText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                modifier = Modifier.testTag("tenants_screen_title")
            )

            Spacer(modifier = Modifier.weight(1f))

            // Primary Header Action: + Add Tenant Button
            Button(
                onClick = onAddTenantClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("add_tenant_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = addTenantText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Language Selector
            GlobalLanguageToggle()

            Spacer(modifier = Modifier.width(4.dp))

            // Overflow Menu
            Box {
                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("tenants_overflow_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = Color(0xFF0F172A)
                    )
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    DropdownMenuItem(
                        text = { Text("Pending Registrations", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.HowToReg,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B)
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onNavigate(Screen.PendingRegistrations.route)
                        },
                        modifier = Modifier.testTag("overflow_pending_registrations")
                    )
                    DropdownMenuItem(
                        text = { Text("Tenant Registration Form", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = null,
                                tint = Color(0xFF2563EB)
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onNavigate(Screen.TenantRegistrationForm.route)
                        },
                        modifier = Modifier.testTag("overflow_registration_form")
                    )
                    DropdownMenuItem(
                        text = { Text("Import from Excel", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                tint = Color(0xFF0F172A)
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onImportExcelClick()
                        },
                        modifier = Modifier.testTag("overflow_import_excel")
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color(0xFF64748B),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholderText,
                        color = Color(0xFF94A3B8),
                        fontSize = 14.5.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF0F172A),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(Color(0xFF2563EB)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_tenants_input")
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TenantRegistrationCard(
    pendingCount: Int,
    onViewSubmissions: () -> Unit,
    onShareForm: () -> Unit,
    titleText: String,
    shareText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Assignment,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                if (pendingCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(onClick = onViewSubmissions)
                            .testTag("pending_submissions_click")
                    ) {
                        Text(
                            text = "$pendingCount new submission${if (pendingCount > 1) "s" else ""}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2563EB)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Share with new tenants.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Share Form Button
            OutlinedButton(
                onClick = onShareForm,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF2563EB)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("share_registration_form_button")
            ) {
                Text(
                    text = shareText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StatusFilterChipsRow(
    selectedStatus: String,
    totalCount: Int,
    activeCount: Int,
    leavingSoonCount: Int,
    vacatedCount: Int,
    newCount: Int,
    onStatusSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // 1. All
        item {
            StatusChip(
                label = "All",
                count = totalCount,
                isSelected = selectedStatus.equals("All", ignoreCase = true),
                selectedBg = Color(0xFFEFF6FF),
                selectedBorder = Color(0xFF2563EB),
                selectedText = Color(0xFF2563EB),
                unselectedBg = Color(0xFFF1F5F9),
                unselectedText = Color(0xFF475569),
                badgeBg = if (selectedStatus.equals("All", ignoreCase = true)) Color(0xFFDBEAFE) else Color(0xFFE2E8F0),
                onClick = { onStatusSelected("All") },
                testTag = "chip_all_residents"
            )
        }

        // 2. Active
        item {
            StatusChip(
                label = "Active",
                count = activeCount,
                isSelected = selectedStatus.equals("Active", ignoreCase = true),
                selectedBg = Color(0xFFDCFCE7),
                selectedBorder = Color(0xFF16A34A),
                selectedText = Color(0xFF16A34A),
                unselectedBg = Color(0xFFF0FDF4),
                unselectedText = Color(0xFF15803D),
                badgeBg = if (selectedStatus.equals("Active", ignoreCase = true)) Color(0xFFBBF7D0) else Color(0xFFDCFCE7),
                onClick = { onStatusSelected("Active") },
                testTag = "chip_active_residents"
            )
        }

        // 3. Leaving Soon
        item {
            StatusChip(
                label = "Leaving Soon",
                count = leavingSoonCount,
                isSelected = selectedStatus.equals("Leaving Soon", ignoreCase = true) || selectedStatus.equals("Leaving", ignoreCase = true),
                selectedBg = Color(0xFFFEF3C7),
                selectedBorder = Color(0xFFD97706),
                selectedText = Color(0xFFB45309),
                unselectedBg = Color(0xFFFFFBEB),
                unselectedText = Color(0xFFD97706),
                badgeBg = if (selectedStatus.equals("Leaving Soon", ignoreCase = true)) Color(0xFFFDE68A) else Color(0xFFFEF3C7),
                onClick = { onStatusSelected("Leaving Soon") },
                testTag = "chip_leaving_soon"
            )
        }

        // 4. Vacated
        item {
            StatusChip(
                label = "Vacated",
                count = vacatedCount,
                isSelected = selectedStatus.equals("Vacated", ignoreCase = true),
                selectedBg = Color(0xFFE2E8F0),
                selectedBorder = Color(0xFF64748B),
                selectedText = Color(0xFF1E293B),
                unselectedBg = Color(0xFFF1F5F9),
                unselectedText = Color(0xFF64748B),
                badgeBg = if (selectedStatus.equals("Vacated", ignoreCase = true)) Color(0xFFCBD5E1) else Color(0xFFE2E8F0),
                onClick = { onStatusSelected("Vacated") },
                testTag = "chip_vacated_residents"
            )
        }

        // 5. New
        item {
            StatusChip(
                label = "New",
                count = newCount,
                isSelected = selectedStatus.equals("New", ignoreCase = true),
                selectedBg = Color(0xFFF3E8FF),
                selectedBorder = Color(0xFF9333EA),
                selectedText = Color(0xFF7E22CE),
                unselectedBg = Color(0xFFFAF5FF),
                unselectedText = Color(0xFF9333EA),
                badgeBg = if (selectedStatus.equals("New", ignoreCase = true)) Color(0xFFE9D5FF) else Color(0xFFF3E8FF),
                onClick = { onStatusSelected("New") },
                testTag = "chip_new_residents"
            )
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    selectedBg: Color,
    selectedBorder: Color,
    selectedText: Color,
    unselectedBg: Color,
    unselectedText: Color,
    badgeBg: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) selectedBg else unselectedBg,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) selectedBorder else Color(0xFFE2E8F0)
        ),
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = selectedBorder.copy(alpha = 0.2f)),
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = rememberTranslation(label),
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) selectedText else unselectedText
            )

            Box(
                modifier = Modifier
                    .background(badgeBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) selectedText else unselectedText
                )
            }
        }
    }
}

@Composable
private fun RoomAndFilterRow(
    rooms: List<String>,
    selectedRoom: String,
    onRoomSelected: (String) -> Unit,
    onOpenFilterSheet: () -> Unit,
    allRoomsLabel: String,
    filterLabel: String,
    showRoomMenu: Boolean,
    onDismissRoomMenu: () -> Unit,
    onToggleRoomMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // All Rooms Dropdown Button
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .height(40.dp)
                    .clickable(onClick = onToggleRoomMenu)
                    .testTag("room_filter_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Apartment,
                        contentDescription = null,
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(18.dp)
                    )

                    Text(
                        text = if (selectedRoom == "All") allRoomsLabel else "Room $selectedRoom",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showRoomMenu,
                onDismissRequest = onDismissRoomMenu,
                modifier = Modifier.background(Color.White)
            ) {
                DropdownMenuItem(
                    text = { Text(allRoomsLabel, fontWeight = if (selectedRoom == "All") FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        onRoomSelected("All")
                        onDismissRoomMenu()
                    }
                )
                rooms.forEach { roomNo ->
                    DropdownMenuItem(
                        text = { Text("Room $roomNo", fontWeight = if (selectedRoom == roomNo) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            onRoomSelected(roomNo)
                            onDismissRoomMenu()
                        }
                    )
                }
            }
        }

        // Filter Button
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier
                .height(40.dp)
                .clickable(onClick = onOpenFilterSheet)
                .testTag("filter_sheet_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterAlt,
                    contentDescription = null,
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(18.dp)
                )

                Text(
                    text = filterLabel,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }
}

@Composable
private fun ResultAndSortRow(
    count: Int,
    sortBy: String,
    showSortMenu: Boolean,
    onToggleSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onSortSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Count Text
        Text(
            text = "$count ${if (count == 1) "tenant" else "tenants"}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        // Sort Selector
        Box {
            Row(
                modifier = Modifier
                    .clickable(onClick = onToggleSortMenu)
                    .padding(vertical = 4.dp, horizontal = 4.dp)
                    .testTag("sort_selector"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Sort",
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(16.dp)
                )

                Text(
                    text = "Sort: $sortBy",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155)
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = onDismissSortMenu,
                modifier = Modifier.background(Color.White)
            ) {
                listOf("Name", "Room", "Move-in Date", "Leaving Date").forEach { sortOption ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = sortOption,
                                fontWeight = if (sortBy == sortOption) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSortSelected(sortOption)
                            onDismissSortMenu()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TenantCardItem(
    tenant: TenantEntity,
    onClick: () -> Unit
) {
    val isVacated = tenant.roomNumber.isBlank() || tenant.deleted
    val initial = tenant.name.trim().firstOrNull()?.toString()?.uppercase() ?: "T"
    val context = LocalContext.current

    val cachedPhotoFile = remember(tenant.cloudId) {
        if (tenant.cloudId.isNotBlank()) {
            val f = File(context.cacheDir, "tenant_photos/${tenant.cloudId}_profile.jpg")
            if (f.exists() && f.length() > 0) f else null
        } else null
    }

    // Soft pastel color deterministic mapping
    val (avatarBg, avatarText) = remember(initial) {
        when (initial.firstOrNull()?.uppercaseChar()) {
            'A', 'G', 'M', 'S', 'Y' -> Pair(Color(0xFFDBEAFE), Color(0xFF2563EB)) // Blue
            'B', 'H', 'N', 'T', 'Z' -> Pair(Color(0xFFDCFCE7), Color(0xFF16A34A)) // Green
            'C', 'I', 'O', 'U' -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706)) // Amber
            'D', 'J', 'P', 'V' -> Pair(Color(0xFFFEE2E2), Color(0xFFDC2626)) // Red / Pink
            'E', 'K', 'Q', 'W' -> Pair(Color(0xFFF3E8FF), Color(0xFF9333EA)) // Purple
            else -> Pair(Color(0xFFEFF6FF), Color(0xFF2563EB))
        }
    }

    // Tenant Status Determination
    val todayIso = remember { PgDateUtil.todayIso() }
    val (statusLabel, statusColor, statusBg, subtitleInfo) = remember(tenant, todayIso) {
        if (isVacated) {
            val dateStr = if (tenant.leavingDate.isNotBlank()) {
                formatDisplayDate(tenant.leavingDate)
            } else if (tenant.moveInDate.isNotBlank()) {
                formatDisplayDate(tenant.moveInDate)
            } else ""
            val sub = if (dateStr.isNotBlank()) "Vacated $dateStr" else "Vacated"
            TenantStatusInfo("Vacated", Color(0xFF64748B), Color(0xFFF1F5F9), sub)
        } else if (tenant.leavingDate.isNotBlank()) {
            val isPast = PgDateUtil.isDateAfter(todayIso, tenant.leavingDate)
            val isToday = tenant.leavingDate.trim() == todayIso
            if (isPast) {
                TenantStatusInfo(
                    "Move-out overdue",
                    Color(0xFFDC2626),
                    Color(0xFFFEE2E2),
                    "Was due ${formatDisplayDate(tenant.leavingDate)}"
                )
            } else if (isToday) {
                TenantStatusInfo(
                    "Moves out today",
                    Color(0xFFD97706),
                    Color(0xFFFEF3C7),
                    "Moves out today"
                )
            } else {
                TenantStatusInfo(
                    "Leaving soon",
                    Color(0xFFD97706),
                    Color(0xFFFEF3C7),
                    "Vacates ${formatDisplayDate(tenant.leavingDate)}"
                )
            }
        } else if (isRecentJoin(tenant.moveInDate)) {
            TenantStatusInfo(
                "New",
                Color(0xFF2563EB),
                Color(0xFFEFF6FF),
                "Joined ${formatDisplayDate(tenant.moveInDate)}"
            )
        } else {
            TenantStatusInfo("Active", Color(0xFF16A34A), Color(0xFFDCFCE7), null)
        }
    }

    val bedLabel = when {
        tenant.bedId.isBlank() -> "Bed —"
        tenant.bedId.startsWith("Bed", ignoreCase = true) -> tenant.bedId
        else -> "Bed ${tenant.bedId}"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color(0xFF2563EB).copy(alpha = 0.1f)),
                onClick = onClick
            )
            .testTag("tenant_card_${tenant.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Initial / Photo Avatar Circle (~50dp)
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(avatarBg),
                contentAlignment = Alignment.Center
            ) {
                if (cachedPhotoFile != null) {
                    AsyncImage(
                        model = cachedPhotoFile,
                        contentDescription = "Photo of ${tenant.name}",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = initial,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = avatarText
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 2. Tenant Details Information
            Column(modifier = Modifier.weight(1f)) {
                // Name & Status Badge Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tenant.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = statusBg,
                        border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Text(
                                text = rememberTranslation(statusLabel),
                                color = statusColor,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Phone Number
                Text(
                    text = tenant.phone,
                    fontSize = 13.5.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Room & Bed Info
                Text(
                    text = "Room ${if (tenant.roomNumber.isNotBlank()) tenant.roomNumber else "—"} · $bedLabel",
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium
                )

                // Subtitle / Date Note (if applicable)
                if (!subtitleInfo.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = subtitleInfo,
                            fontSize = 12.sp,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Trailing Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View Details",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private data class TenantStatusInfo(
    val label: String,
    val color: Color,
    val bg: Color,
    val subtitle: String?
)

private fun isRecentJoin(moveInDate: String?): Boolean {
    if (moveInDate.isNullOrBlank()) return false
    val d = PgDateUtil.parseDate(moveInDate) ?: return false
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -20)
    return d.after(cal.time)
}

private fun formatDisplayDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    val parsed = PgDateUtil.parseDate(dateStr) ?: return dateStr
    val sdf = SimpleDateFormat("MMM d", Locale.US)
    return sdf.format(parsed)
}

@Composable
private fun TenantFilterBottomSheetContent(
    rooms: List<String>,
    selectedRoom: String,
    selectedOccupancy: String,
    onApply: (room: String, occupancy: String) -> Unit,
    onClearAll: () -> Unit
) {
    var tempRoom by remember { mutableStateOf(selectedRoom) }
    var tempOccupancy by remember { mutableStateOf(selectedOccupancy) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Filter Tenants",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status Filter Options
        Text(
            text = "Occupancy Status",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF475569)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("All", "Active", "Leaving Soon", "Vacated", "New").forEach { status ->
                val isSelected = tempOccupancy.equals(status, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                    modifier = Modifier.clickable { tempOccupancy = status }
                ) {
                    Text(
                        text = rememberTranslation(status),
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF2563EB) else Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Room Filter Options
        Text(
            text = "Room",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF475569)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val isSelected = tempRoom == "All"
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                    modifier = Modifier.clickable { tempRoom = "All" }
                ) {
                    Text(
                        text = "All Rooms",
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF2563EB) else Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            items(rooms) { roomNo ->
                val isSelected = tempRoom == roomNo
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                    modifier = Modifier.clickable { tempRoom = roomNo }
                ) {
                    Text(
                        text = "Room $roomNo",
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF2563EB) else Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons: Clear All & Apply
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onClearAll,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Clear all", fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            }

            Button(
                onClick = { onApply(tempRoom, tempOccupancy) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Apply", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FilteredEmptyView(
    isSearchActive: Boolean,
    onClearSearch: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isSearchActive) rememberTranslation("No tenants found") else rememberTranslation("No tenants match these filters"),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isSearchActive) "Try another name, phone number,\nor room number." else "Try adjusting your room or status filters.",
                fontSize = 13.5.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = if (isSearchActive) onClearSearch else onClearFilters,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isSearchActive) rememberTranslation("Clear Search") else rememberTranslation("Clear Filters"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EmptyTenantsView(
    onAddTenantClick: () -> Unit,
    onRegistrationFormClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color(0xFF2563EB).copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = rememberTranslation("No tenants yet"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Add your first tenant manually\nor share the registration form.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onAddTenantClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("empty_add_tenant_button")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(rememberTranslation("Add Tenant"), color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onRegistrationFormClick,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF2563EB)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("empty_registration_form_button")
            ) {
                Icon(Icons.Default.Assignment, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Registration Form", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
