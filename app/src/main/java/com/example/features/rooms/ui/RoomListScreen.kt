package com.example.features.rooms.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.example.core.designsystem.LocalSpacing
import com.example.features.rooms.domain.model.RoomSummary
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.ui.viewmodel.RoomListUiState
import com.example.features.rooms.ui.viewmodel.RoomListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomListScreen(
    viewModel: RoomListViewModel,
    onBackClick: () -> Unit,
    onAddRoomClick: () -> Unit,
    onRoomDetailsClick: (String) -> Unit,
    onEditRoomClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showFilterDialog by remember { mutableStateOf(false) }
    var roomToDelete by remember { mutableStateOf<String?>(null) }

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
        topBar = {
            TopAppBar(
                title = { Text("Rooms Directory", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back to Dashboard"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.testTag("filter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter Rooms"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("rooms_top_bar")
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddRoomClick() },
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .testTag("add_room_fab")
                    .padding(bottom = 16.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add New Room"
                )
            }
        },
        modifier = modifier.fillMaxSize().testTag("rooms_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is RoomListUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is RoomListUiState.Empty -> {
                    EmptyRoomsState(onAddRoomClick = onAddRoomClick)
                }
                is RoomListUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.medium)
                    ) {
                        // 1. Hero Summary Stats Card
                        OccupancyHeroCard(stats = state.overallOccupancy)
                        
                        Spacer(modifier = Modifier.height(spacing.medium))

                        // 2. Search Box
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Search by Room No, Floor or Type...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search icon"
                                )
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear search query"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("room_search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(spacing.medium))

                        // Selected filters summary
                        if (state.selectedFloor != "All" || state.selectedRoomType != "All" || state.selectedStatus != "All") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = spacing.small),
                                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Active Filters: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                if (state.selectedFloor != "All") {
                                    SuggestionChip(
                                        onClick = { viewModel.onFloorChanged("All") },
                                        label = { Text("Floor: ${state.selectedFloor}") }
                                    )
                                }
                                if (state.selectedRoomType != "All") {
                                    SuggestionChip(
                                        onClick = { viewModel.onRoomTypeChanged("All") },
                                        label = { Text("Type: ${state.selectedRoomType}") }
                                    )
                                }
                                if (state.selectedStatus != "All") {
                                    SuggestionChip(
                                        onClick = { viewModel.onStatusChanged("All") },
                                        label = { Text("Status: ${state.selectedStatus}") }
                                    )
                                }
                            }
                        }

                        // 3. Room List
                        if (state.rooms.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching rooms found.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("rooms_list"),
                                verticalArrangement = Arrangement.spacedBy(spacing.medium),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(
                                    items = state.rooms,
                                    key = { it.roomNumber }
                                ) { summary ->
                                    RoomListItem(
                                        summary = summary,
                                        onViewDetails = { onRoomDetailsClick(summary.roomNumber) },
                                        onEdit = { onEditRoomClick(summary.roomNumber) },
                                        onDelete = { roomToDelete = summary.roomNumber }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Deletion confirmation Dialog
            roomToDelete?.let { number ->
                AlertDialog(
                    onDismissRequest = { roomToDelete = null },
                    title = { Text("Confirm Deletion") },
                    text = { Text("Are you sure you want to delete Room $number? This action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteRoom(number)
                                roomToDelete = null
                            },
                            modifier = Modifier.testTag("confirm_delete_button")
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { roomToDelete = null }) {
                            Text("Cancel")
                        }
                    },
                    modifier = Modifier.testTag("delete_confirmation_dialog")
                )
            }

            // Filters and Sort Dialog
            if (showFilterDialog && uiState is RoomListUiState.Success) {
                val state = uiState as RoomListUiState.Success
                AlertDialog(
                    onDismissRequest = { showFilterDialog = false },
                    title = { Text("Filter & Sort Rooms") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
                            // Sort by Option
                            Text("Sort By", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            val sortOptions = listOf("Room Number", "Rent Asc", "Rent Desc", "Beds Available")
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                                sortOptions.forEach { opt ->
                                    FilterChip(
                                        selected = state.sortBy == opt,
                                        onClick = { viewModel.onSortByChanged(opt) },
                                        label = { Text(opt) }
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Floor selection
                            Text("Floor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                                state.availableFloors.forEach { fl ->
                                    FilterChip(
                                        selected = state.selectedFloor == fl,
                                        onClick = { viewModel.onFloorChanged(fl) },
                                        label = { Text(fl) }
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Room type selection
                            Text("Room Type", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                                state.availableRoomTypes.forEach { ty ->
                                    FilterChip(
                                        selected = state.selectedRoomType == ty,
                                        onClick = { viewModel.onRoomTypeChanged(ty) },
                                        label = { Text(ty) }
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Status selection
                            Text("Occupancy Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            val statusOpts = listOf("All", "Empty", "Available", "Full")
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                                statusOpts.forEach { st ->
                                    FilterChip(
                                        selected = state.selectedStatus == st,
                                        onClick = { viewModel.onStatusChanged(st) },
                                        label = { Text(st) }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showFilterDialog = false }) {
                            Text("Apply")
                        }
                    },
                    modifier = Modifier.testTag("filter_dialog")
                )
            }
        }
    }
}

@Composable
fun OccupancyHeroCard(
    stats: com.example.features.rooms.domain.usecase.OverallOccupancy,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(spacing.large)) {
            Text(
                "Occupancy Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Beds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text("${stats.totalBeds}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text("Occupied", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text("${stats.occupiedBeds}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text("Available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text("${stats.availableBeds}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(modifier = Modifier.height(spacing.medium))
            // Progress Bar
            val fillRatio = if (stats.totalBeds > 0) stats.occupiedBeds.toFloat() / stats.totalBeds else 0f
            LinearProgressIndicator(progress = { fillRatio },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${String.format("%.1f", stats.occupancyPercentage)}% Occupied",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Text(
                    text = "${String.format("%.1f", stats.vacancyPercentage)}% Vacant",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun RoomListItem(
    summary: RoomSummary,
    onViewDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val statusColor = when (summary.occupancyStatus) {
        "Full" -> MaterialTheme.colorScheme.error
        "Available" -> Color(0xFFFFB300) // Beautiful Amber
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onViewDetails() }
    ) {
        Column(modifier = Modifier.padding(spacing.large)) {
            // Row 1: Title and Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MeetingRoom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(spacing.small))
                    Column {
                        Text(
                            text = "Room ${summary.roomNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = summary.floor,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status pill
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = spacing.small, vertical = 4.dp)
                ) {
                    Text(
                        text = summary.occupancyStatus,
                        color = statusColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Row 2: Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Monthly Rent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${String.format("%.0f", summary.ratePerBed)} / bed", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Beds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${summary.occupiedBeds} / ${summary.totalBeds} Occupied", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Active Tenants", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${summary.activeTenantCount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(spacing.small))

            // Row 3: Action Buttons (accessibility sizes: min 48dp touch targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.roomType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                    IconButton(
                        onClick = onViewDetails,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "View details for Room ${summary.roomNumber}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Room ${summary.roomNumber}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Room ${summary.roomNumber}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyRoomsState(
    onAddRoomClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(spacing.large)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MeetingRoom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )
            }
            Spacer(modifier = Modifier.height(spacing.medium))
            Text(
                "No Registered Rooms",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                "Register your PG's rooms to start managing inventory, allocating beds, tracking vacancies, and collecting monthly rents.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = spacing.medium)
            )
            Spacer(modifier = Modifier.height(spacing.large))
            Button(
                onClick = { onAddRoomClick() },
                modifier = Modifier.testTag("add_first_room_button")
            ) {
                Text("Register First Room")
            }
        }
    }
}
