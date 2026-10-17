package com.example.features.rooms.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.designsystem.AppBottomNavBar
import com.example.core.designsystem.MainTab
import com.example.core.language.AppLanguageManager
import com.example.core.language.rememberTranslation
import com.example.data.database.PropertyEntity
import com.example.features.properties.ui.viewmodel.PropertyViewModel
import com.example.features.rooms.domain.model.RoomSummary
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.ui.viewmodel.RoomListUiState
import com.example.features.rooms.ui.viewmodel.RoomListViewModel
import com.example.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomListScreen(
    viewModel: RoomListViewModel,
    onBackClick: () -> Unit,
    onAddRoomClick: () -> Unit,
    onRoomDetailsClick: (String) -> Unit,
    onEditRoomClick: (String) -> Unit,
    onNavigate: (String) -> Unit = {},
    onAssignBedClick: (roomNumber: String, bedId: String) -> Unit = { _, _ -> onNavigate(Screen.AddTenantChoice.route) },
    onTenantClick: (tenantId: Int) -> Unit = { tenantId -> onNavigate(Screen.TenantDetails.createRoute(tenantId)) },
    modifier: Modifier = Modifier,
    propertyViewModel: PropertyViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val currentProperty by propertyViewModel.currentProperty.collectAsState()
    val activeProperties by propertyViewModel.activeProperties.collectAsState()
    val selectedLanguage by AppLanguageManager.languageFlow.collectAsState()

    val propertyName = currentProperty?.propertyName?.ifBlank { "Karan PG" } ?: "Karan PG"
    val propertyCity = currentProperty?.city?.takeIf { it.isNotBlank() }
        ?: currentProperty?.address?.takeIf { it.isNotBlank() }
        ?: "Hyderabad"

    var showFilterSheet by remember { mutableStateOf(false) }
    var roomToDelete by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Handle delete action event
    LaunchedEffect(key1 = true) {
        viewModel.actionEvent.collect { result ->
            when (result) {
                is RoomValidationResult.Success -> {
                    Toast.makeText(context, "Room deleted successfully", Toast.LENGTH_SHORT).show()
                }
                is RoomValidationResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            AppBottomNavBar(
                currentTab = MainTab.ROOMS,
                onNavigate = onNavigate
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier
            .fillMaxSize()
            .testTag("rooms_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            when (val state = uiState) {
                is RoomListUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF2563EB),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                is RoomListUiState.Empty -> {
                    EmptyRoomsState(
                        innerPadding = innerPadding,
                        propertyName = propertyName,
                        propertyCity = propertyCity,
                        selectedLanguage = selectedLanguage,
                        onLanguageSelect = { AppLanguageManager.setLanguage(it) },
                        onSettingsClick = { onNavigate(Screen.SettingsHome.route) },
                        onAddRoomClick = onAddRoomClick,
                        activeProperties = activeProperties,
                        onSwitchProperty = { propertyViewModel.switchProperty(it) }
                    )
                }
                is RoomListUiState.Success -> {
                    RoomsSuccessContent(
                        state = state,
                        propertyName = propertyName,
                        propertyCity = propertyCity,
                        selectedLanguage = selectedLanguage,
                        onLanguageSelect = { AppLanguageManager.setLanguage(it) },
                        onSettingsClick = { onNavigate(Screen.SettingsHome.route) },
                        onAddRoomClick = onAddRoomClick,
                        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onStatusChanged = { viewModel.onStatusChanged(it) },
                        onFilterClick = { showFilterSheet = true },
                        onRoomClick = onRoomDetailsClick,
                        onEditRoom = onEditRoomClick,
                        onDeleteRoom = { roomToDelete = it },
                        onResetFilters = { viewModel.resetFilters() },
                        onAssignBedClick = onAssignBedClick,
                        onTenantClick = onTenantClick,
                        activeProperties = activeProperties,
                        onSwitchProperty = { propertyViewModel.switchProperty(it) },
                        innerPadding = innerPadding,
                        keyboardController = keyboardController
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (roomToDelete != null) {
        AlertDialog(
            onDismissRequest = { roomToDelete = null },
            title = {
                Text(
                    text = rememberTranslation("Delete Room"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete Room $roomToDelete? This action cannot be undone.",
                    fontSize = 14.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        roomToDelete?.let { viewModel.deleteRoom(it) }
                        roomToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                ) {
                    Text(
                        text = rememberTranslation("Delete"),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { roomToDelete = null }) {
                    Text(
                        text = rememberTranslation("Cancel"),
                        color = Color(0xFF64748B)
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Filter Bottom Sheet (Floor, Room Type & Sharing Type)
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            val successState = uiState as? RoomListUiState.Success
            if (successState != null) {
                var tempFloor by remember { mutableStateOf(successState.selectedFloor) }
                var tempType by remember { mutableStateOf(successState.selectedRoomType) }
                var tempSharing by remember { mutableStateOf(successState.selectedSharingType) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = rememberTranslation("Filter Rooms"),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        IconButton(onClick = { showFilterSheet = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Floor Filter
                    Text(
                        text = rememberTranslation("Floor"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(successState.availableFloors) { floor ->
                            FilterChip(
                                selected = tempFloor == floor,
                                onClick = { tempFloor = floor },
                                label = { Text(floor) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEFF6FF),
                                    selectedLabelColor = Color(0xFF1D4ED8),
                                    containerColor = Color.White,
                                    labelColor = Color(0xFF475569)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = tempFloor == floor,
                                    selectedBorderColor = Color(0xFF3B82F6),
                                    borderColor = Color(0xFFE2E8F0)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Room Type (AC / Non-AC) Filter
                    Text(
                        text = rememberTranslation("Room Type"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(successState.availableRoomTypes) { type ->
                            FilterChip(
                                selected = tempType == type,
                                onClick = { tempType = type },
                                label = { Text(type) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEFF6FF),
                                    selectedLabelColor = Color(0xFF1D4ED8),
                                    containerColor = Color.White,
                                    labelColor = Color(0xFF475569)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = tempType == type,
                                    selectedBorderColor = Color(0xFF3B82F6),
                                    borderColor = Color(0xFFE2E8F0)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Sharing Type Filter
                    Text(
                        text = rememberTranslation("Sharing Type"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("sharing_type_filter_row")
                    ) {
                        items(successState.availableSharingTypes) { sharing ->
                            FilterChip(
                                selected = tempSharing == sharing,
                                onClick = { tempSharing = sharing },
                                label = { Text(rememberTranslation(sharing)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEFF6FF),
                                    selectedLabelColor = Color(0xFF1D4ED8),
                                    containerColor = Color.White,
                                    labelColor = Color(0xFF475569)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = tempSharing == sharing,
                                    selectedBorderColor = Color(0xFF3B82F6),
                                    borderColor = Color(0xFFE2E8F0)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("sharing_chip_${sharing.replace(" ", "_")}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.resetFilters()
                                showFilterSheet = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Text(
                                text = rememberTranslation("Clear Filters"),
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = {
                                viewModel.onFloorChanged(tempFloor)
                                viewModel.onRoomTypeChanged(tempType)
                                viewModel.onSharingTypeChanged(tempSharing)
                                showFilterSheet = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("apply_filters_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = rememberTranslation("Apply Filters"),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Main Content Viewport for RoomListScreen
 */
@Composable
private fun RoomsSuccessContent(
    state: RoomListUiState.Success,
    propertyName: String,
    propertyCity: String,
    selectedLanguage: String,
    onLanguageSelect: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAddRoomClick: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onStatusChanged: (String) -> Unit,
    onFilterClick: () -> Unit,
    onRoomClick: (String) -> Unit,
    onEditRoom: (String) -> Unit,
    onDeleteRoom: (String) -> Unit,
    onResetFilters: () -> Unit,
    onAssignBedClick: (roomNumber: String, bedId: String) -> Unit,
    onTenantClick: (tenantId: Int) -> Unit,
    activeProperties: List<PropertyEntity>,
    onSwitchProperty: (PropertyEntity) -> Unit,
    innerPadding: PaddingValues,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
    // Maintenance or blocked beds calculation
    val maintenanceBedsCount = state.rooms.sumOf { it.blockedBeds }
    val leavingSoonCount = state.rooms.sumOf { it.leavingTenants.size }
    val maintenanceDisplayCount = if (maintenanceBedsCount > 0) maintenanceBedsCount else leavingSoonCount

    val allText = rememberTranslation("All")
    val availableText = rememberTranslation("Available")
    val partialText = rememberTranslation("Partial")
    val fullText = rememberTranslation("Full")
    val vacantText = rememberTranslation("Vacant")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("rooms_scroll_content"),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // A. HEADER (Title, Location, Property Selector, Language & Settings)
        // ==========================================
        item {
            RoomsHeader(
                propertyName = propertyName,
                propertyCity = propertyCity,
                selectedLanguage = selectedLanguage,
                onLanguageSelect = onLanguageSelect,
                onSettingsClick = onSettingsClick,
                activeProperties = activeProperties,
                onSwitchProperty = onSwitchProperty
            )
        }

        // ==========================================
        // B. ROOM & BED SUMMARY (Title, Add Room Button, 2x2 Cards Grid)
        // ==========================================
        item {
            RoomAndBedSummarySection(
                totalRooms = state.totalCount,
                occupiedBeds = state.overallOccupancy.occupiedBeds,
                availableBeds = state.overallOccupancy.availableBeds,
                maintenanceCount = maintenanceDisplayCount,
                onAddRoomClick = onAddRoomClick
            )
        }

        // ==========================================
        // C. SEARCH & FILTER CONTROLS
        // ==========================================
        item {
            RoomsSearchAndFilterControls(
                searchQuery = state.searchQuery,
                onSearchQueryChanged = onSearchQueryChanged,
                onFilterClick = onFilterClick,
                hasActiveSecondaryFilters = state.selectedFloor != "All" || state.selectedRoomType != "All" || (state.selectedSharingType != "All Sharing" && state.selectedSharingType != "All"),
                selectedStatus = state.selectedStatus,
                onStatusChanged = onStatusChanged,
                totalCount = state.totalCount,
                availableCount = state.availableCount,
                partialCount = state.partiallyOccupiedCount,
                fullCount = state.fullCount,
                vacantCount = state.vacantCount,
                keyboardController = keyboardController,
                allText = allText,
                availableText = availableText,
                partialText = partialText,
                fullText = fullText,
                vacantText = vacantText
            )
        }

        // ==========================================
        // D. ROOM CARDS OR EMPTY LIST STATE
        // ==========================================
        if (state.rooms.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = rememberTranslation("No rooms found"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = rememberTranslation("Try changing your filters or search query."),
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onResetFilters,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Text(
                                text = rememberTranslation("Clear Filters"),
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else {
            items(state.rooms, key = { it.roomNumber }) { roomSummary ->
                RoomCardReferenceItem(
                    summary = roomSummary,
                    onViewDetails = { onRoomClick(roomSummary.roomNumber) },
                    onEdit = { onEditRoom(roomSummary.roomNumber) },
                    onDelete = { onDeleteRoom(roomSummary.roomNumber) },
                    onAssignClick = onAssignBedClick,
                    onTenantClick = onTenantClick
                )
            }
        }
    }
}

/**
 * A. Top Header Component with Property Selector Dropdown matching Reference
 */
@Composable
private fun RoomsHeader(
    propertyName: String,
    propertyCity: String,
    selectedLanguage: String,
    onLanguageSelect: (String) -> Unit,
    onSettingsClick: () -> Unit,
    activeProperties: List<PropertyEntity> = emptyList(),
    onSwitchProperty: (PropertyEntity) -> Unit = {}
) {
    var showPropertyDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rooms_header")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Title
            Text(
                text = rememberTranslation("Rooms"),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = Color(0xFF0F172A)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Language Pill Toggle & Settings Gear
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LanguageToggleCapsule(
                    selectedLanguage = selectedLanguage,
                    onLanguageSelect = onLanguageSelect
                )

                Surface(
                    onClick = onSettingsClick,
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("rooms_settings_button")
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Property Selector Card with Dropdown
        Box {
            Surface(
                onClick = { if (activeProperties.size > 1) showPropertyDropdown = true },
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 0.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("property_selector_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = propertyName,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = propertyCity,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    if (activeProperties.size > 1) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Property",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (activeProperties.size > 1) {
                DropdownMenu(
                    expanded = showPropertyDropdown,
                    onDismissRequest = { showPropertyDropdown = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    activeProperties.forEach { prop ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = prop.propertyName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    if (prop.city.isNotBlank()) {
                                        Text(
                                            text = prop.city,
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                showPropertyDropdown = false
                                onSwitchProperty(prop)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Capsule Language Toggle [ EN | తెలుగు ]
 */
@Composable
private fun LanguageToggleCapsule(
    selectedLanguage: String,
    onLanguageSelect: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isEn = selectedLanguage == "EN"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isEn) Color(0xFF2563EB) else Color.Transparent)
                    .clickable { onLanguageSelect("EN") }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isEn) Color.White else Color(0xFF64748B),
                        fontWeight = if (isEn) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.5.sp
                    )
                )
            }

            val isTe = selectedLanguage == "తెలుగు"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isTe) Color(0xFF2563EB) else Color.Transparent)
                    .clickable { onLanguageSelect("తెలుగు") }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "తెలుగు",
                    style = MaterialTheme.typography.labelSmall.copy(
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
 * B. Room & Bed Summary Section (Title, + Add Room, 2x2 Grid)
 */
@Composable
private fun RoomAndBedSummarySection(
    totalRooms: Int,
    occupiedBeds: Int,
    availableBeds: Int,
    maintenanceCount: Int,
    onAddRoomClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rememberTranslation("Room & Bed Summary"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
                )
            )

            Button(
                onClick = onAddRoomClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("add_room_summary_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = rememberTranslation("Add Room"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2-column, 2-row Summary Cards Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricCard(
                    title = rememberTranslation("Total Rooms"),
                    value = totalRooms.toString(),
                    icon = Icons.Default.MeetingRoom,
                    iconBgColor = Color(0xFFEFF6FF),
                    iconTintColor = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = rememberTranslation("Occupied Beds"),
                    value = occupiedBeds.toString(),
                    icon = Icons.Default.Group,
                    iconBgColor = Color(0xFFEEF2FF),
                    iconTintColor = Color(0xFF4F46E5),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricCard(
                    title = rememberTranslation("Available Beds"),
                    value = availableBeds.toString(),
                    icon = Icons.Default.Hotel,
                    iconBgColor = Color(0xFFECFDF5),
                    iconTintColor = Color(0xFF059669),
                    valueColor = Color(0xFF059669),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = rememberTranslation("Blocked Beds"),
                    value = maintenanceCount.toString(),
                    icon = Icons.Default.Build,
                    iconBgColor = Color(0xFFFFFBEB),
                    iconTintColor = Color(0xFFD97706),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Clean Single Metric Card
 */
@Composable
private fun SummaryMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconTintColor: Color,
    modifier: Modifier = Modifier,
    valueColor: Color = Color(0xFF0F172A)
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * C. Search Input Field & Horizontal Status Filter Chips
 */
@Composable
private fun RoomsSearchAndFilterControls(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onFilterClick: () -> Unit,
    hasActiveSecondaryFilters: Boolean,
    selectedStatus: String,
    onStatusChanged: (String) -> Unit,
    totalCount: Int,
    availableCount: Int,
    partialCount: Int,
    fullCount: Int,
    vacantCount: Int,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    allText: String,
    availableText: String,
    partialText: String,
    fullText: String,
    vacantText: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search Input + Filter Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = {
                    Text(
                        text = rememberTranslation("Search room or tenant..."),
                        fontSize = 13.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF2563EB),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("room_search_input")
            )

            OutlinedIconButton(
                onClick = onFilterClick,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (hasActiveSecondaryFilters) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                ),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    containerColor = if (hasActiveSecondaryFilters) Color(0xFFEFF6FF) else Color.White
                ),
                modifier = Modifier
                    .size(50.dp)
                    .testTag("filter_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = "Filter Rooms",
                    tint = if (hasActiveSecondaryFilters) Color(0xFF2563EB) else Color(0xFF475569),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Horizontal Status Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val chips = listOf(
                Triple("All", allText, totalCount),
                Triple("Available", availableText, availableCount),
                Triple("Partial", partialText, partialCount),
                Triple("Full", fullText, fullCount),
                Triple("Vacant", vacantText, vacantCount)
            )

            items(chips) { (id, label, count) ->
                val isSelected = selectedStatus == id
                Surface(
                    onClick = { onStatusChanged(id) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFFEFF6FF) else Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.testTag("status_chip_$id")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF475569)
                        )
                        Text(
                            text = "($count)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

/**
 * D. Room Card matching the Reference UI Specification with Expandable Bed Breakdown
 */
@Composable
private fun RoomCardReferenceItem(
    summary: RoomSummary,
    onViewDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAssignClick: (roomNumber: String, bedId: String) -> Unit,
    onTenantClick: (tenantId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    // Status Styling
    val statusText: String
    val statusColor: Color
    val statusBg: Color

    when {
        summary.occupiedBeds == 0 -> {
            statusText = rememberTranslation("Vacant")
            statusColor = Color(0xFF64748B)
            statusBg = Color(0xFFF1F5F9)
        }
        summary.availableBeds == 0 && summary.usableBeds > 0 -> {
            statusText = rememberTranslation("Full")
            statusColor = Color(0xFFDC2626)
            statusBg = Color(0xFFFEF2F2)
        }
        summary.occupiedBeds > 0 && summary.availableBeds > 0 -> {
            statusText = rememberTranslation("Available")
            statusColor = Color(0xFF059669)
            statusBg = Color(0xFFECFDF5)
        }
        else -> {
            statusText = rememberTranslation("Available")
            statusColor = Color(0xFF059669)
            statusBg = Color(0xFFECFDF5)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("room_card_${summary.roomNumber}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Room Number & Type on Left, Status Pill & Overflow on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Room ${summary.roomNumber}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.clickable { onViewDetails() }
                        )

                        if (summary.hasUpcomingVacancy) {
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = rememberTranslation("Vacating Soon"),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${summary.floor} · ${summary.sharingType} · ${summary.roomType}",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Normal
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Status Badge (e.g. "Available", "Full", "Vacant")
                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }

                    // Ratio Pill (e.g. "2/3 Beds")
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Text(
                            text = "${summary.occupiedBeds}/${summary.usableBeds} ${rememberTranslation("Beds")}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Expand / Collapse Bed Breakdown Toggle Button
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("expand_room_${summary.roomNumber}")
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse Bed Breakdown" else "Expand Bed Breakdown",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // More Options Dropdown
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("room_options_${summary.roomNumber}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Room Options",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = rememberTranslation("Edit Room"),
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = rememberTranslation("Delete Room"),
                                        fontSize = 13.5.sp,
                                        color = Color(0xFFDC2626)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Middle: Bed Progress Bar + Beds Available Indicator
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onViewDetails() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val availableLabel = if (summary.availableBeds == 1) {
                        "1 ${rememberTranslation("bed available")}"
                    } else if (summary.availableBeds > 1) {
                        "${summary.availableBeds} ${rememberTranslation("beds available")}"
                    } else {
                        rememberTranslation("No beds available")
                    }

                    Text(
                        text = availableLabel,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (summary.availableBeds > 0) Color(0xFF059669) else Color(0xFF64748B)
                    )

                    Text(
                        text = "${String.format("%.0f", summary.occupancyPercentage)}% occupied",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = {
                        if (summary.usableBeds > 0) {
                            (summary.occupiedBeds.toFloat() / summary.usableBeds.toFloat())
                        } else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when {
                        summary.availableBeds == 0 && summary.usableBeds > 0 -> Color(0xFFDC2626)
                        summary.occupiedBeds > 0 -> Color(0xFF2563EB)
                        else -> Color(0xFF94A3B8)
                    },
                    trackColor = Color(0xFFF1F5F9)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Tenants Chips (if any assigned)
            if (summary.activeTenants.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = summary.activeTenants.take(3).joinToString(separator = ", ") { it.name },
                        fontSize = 12.5.sp,
                        color = Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (summary.activeTenants.size > 3) {
                        Text(
                            text = "+${summary.activeTenants.size - 3} more",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Bottom Row: Rate per bed and "View Details >" CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₹${String.format("%,.0f", summary.ratePerBed)} / bed / month",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onViewDetails() }
                ) {
                    Text(
                        text = rememberTranslation("View Details"),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2563EB)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            // EXPANDABLE BEDS BREAKDOWN SECTION
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BEDS BREAKDOWN (${summary.availableBeds} Vacant)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Capacity: ${summary.totalBeds}",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val physicalBeds = summary.canonicalPhysicalBeds
                    val activeAssignments = summary.activeAssignments
                    val activeTenantsMap = summary.tenants.associateBy { it.id }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        physicalBeds.forEach { bed ->
                            val bedLabel = "Bed ${bed.bedId}"
                            val isBlocked = bed.status == "BLOCKED"
                            val assignment = activeAssignments.firstOrNull {
                                com.example.core.util.BedIdentityUtil.matchesSameBed(it.bedId, bed.bedId)
                            }
                            var tenant = if (assignment != null) activeTenantsMap[assignment.tenantId] else null

                            if (tenant == null && !isBlocked) {
                                tenant = summary.activeTenants.firstOrNull { t ->
                                    com.example.core.util.BedIdentityUtil.matchesSameBed(t.bedId, bed.bedId)
                                }
                            }

                            if (tenant == null && !isBlocked) {
                                val assignedTenantIds = physicalBeds.mapNotNull { b ->
                                    val a = activeAssignments.firstOrNull { com.example.core.util.BedIdentityUtil.matchesSameBed(it.bedId, b.bedId) }
                                    a?.tenantId ?: summary.activeTenants.firstOrNull { com.example.core.util.BedIdentityUtil.matchesSameBed(it.bedId, b.bedId) }?.id
                                }.toSet()
                                tenant = summary.activeTenants.firstOrNull { it.id !in assignedTenantIds }
                            }

                            val isOccupied = tenant != null && !isBlocked

                            when {
                                isBlocked -> {
                                    BlockedBedCard(bedLabel = bedLabel)
                                }
                                isOccupied -> {
                                    OccupiedBedCard(
                                        bedLabel = bedLabel,
                                        tenantName = tenant!!.name,
                                        tenantPhone = tenant.phone,
                                        joinDate = assignment?.startDate ?: tenant.moveInDate,
                                        rentAmount = tenant.monthlyRent,
                                        onTenantClick = { onTenantClick(tenant.id) }
                                    )
                                }
                                else -> {
                                    VacantBedCard(
                                        bedLabel = bedLabel,
                                        onAssignClick = { onAssignClick(summary.roomNumber, bed.bedId) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onEdit,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF334155)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = rememberTranslation("Edit Room"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF334155)
                            )
                        }

                        Button(
                            onClick = onViewDetails,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEFF6FF),
                                contentColor = Color(0xFF2563EB)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Text(
                                text = rememberTranslation("Room Details"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Vacant Bed Card with + Assign Button
 */
@Composable
private fun VacantBedCard(
    bedLabel: String,
    onAssignClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD1FAE5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hotel,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column {
                    Text(
                        text = bedLabel,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF065F46)
                    )
                    Text(
                        text = rememberTranslation("Vacant & Ready to Allocate"),
                        fontSize = 11.5.sp,
                        color = Color(0xFF047857)
                    )
                }
            }

            Button(
                onClick = onAssignClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("assign_bed_button_${bedLabel.replace(" ", "_")}")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = rememberTranslation("Assign"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Occupied Bed Card with Tenant Details
 */
@Composable
private fun OccupiedBedCard(
    bedLabel: String,
    tenantName: String,
    tenantPhone: String,
    joinDate: String,
    rentAmount: Double,
    onTenantClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTenantClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = bedLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = rememberTranslation("Occupied"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = tenantName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    if (tenantPhone.isNotBlank()) {
                        Text(
                            text = tenantPhone,
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (rentAmount > 0) {
                    Text(
                        text = "₹${String.format("%,.0f", rentAmount)}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "View Tenant",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Blocked Bed Card
 */
@Composable
private fun BlockedBedCard(
    bedLabel: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEF3C7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column {
                    Text(
                        text = bedLabel,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E)
                    )
                    Text(
                        text = rememberTranslation("Under Maintenance / Blocked"),
                        fontSize = 11.5.sp,
                        color = Color(0xFFB45309)
                    )
                }
            }

            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = rememberTranslation("Blocked"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Empty Rooms State
 */
@Composable
private fun EmptyRoomsState(
    innerPadding: PaddingValues,
    propertyName: String,
    propertyCity: String,
    selectedLanguage: String,
    onLanguageSelect: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAddRoomClick: () -> Unit,
    activeProperties: List<PropertyEntity> = emptyList(),
    onSwitchProperty: (PropertyEntity) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("rooms_empty_screen"),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            RoomsHeader(
                propertyName = propertyName,
                propertyCity = propertyCity,
                selectedLanguage = selectedLanguage,
                onLanguageSelect = onLanguageSelect,
                onSettingsClick = onSettingsClick,
                activeProperties = activeProperties,
                onSwitchProperty = onSwitchProperty
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MeetingRoom,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = rememberTranslation("No Rooms Yet"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = rememberTranslation("Register your property rooms to manage bed allocation, occupancy, and rent collection."),
                        fontSize = 13.5.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onAddRoomClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = Modifier.testTag("add_first_room_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = rememberTranslation("Add Room"),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
