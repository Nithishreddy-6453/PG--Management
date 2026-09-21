package com.example.features.tenants

import androidx.compose.runtime.collectAsState
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.LocalSpacing
import com.example.data.database.TenantEntity
import com.example.features.tenants.ui.viewmodel.TenantListUiState
import com.example.features.tenants.ui.viewmodel.TenantListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantsScreen(
    viewModel: TenantListViewModel,
    onBackClick: () -> Unit,
    onTenantClick: (Int) -> Unit,
    onAddTenantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val state by viewModel.uiState.collectAsState()
    
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tenants Directory", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("tenants_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("sort_tenants_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sort Alphabetically") },
                            onClick = {
                                viewModel.onSortByChanged("Alphabetical")
                                showSortMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Move-in Date") },
                            onClick = {
                                viewModel.onSortByChanged("Move-in Date")
                                showSortMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("tenants_top_bar")
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTenantClick,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Onboard Tenant") },
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_tenant_fab")
            )
        },
        modifier = modifier.fillMaxSize().testTag("tenants_screen_container")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val currentState = state) {
                is TenantListUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag("tenants_loading_indicator"))
                    }
                }
                is TenantListUiState.Empty -> {
                    EmptyTenantsView(onAddTenantClick)
                }
                is TenantListUiState.Success -> {
                    // Search Bar
                    OutlinedTextField(
                        value = currentState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.medium, vertical = spacing.small)
                            .testTag("search_tenants_input"),
                        placeholder = { Text("Search by name or phone...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (currentState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                    )

                    // Filters
                    FilterRow(
                        rooms = currentState.roomsList,
                        selectedRoom = currentState.selectedRoomFilter,
                        selectedOccupancy = currentState.selectedOccupancyFilter,
                        onRoomSelected = { viewModel.onRoomFilterChanged(it) },
                        onOccupancySelected = { viewModel.onOccupancyFilterChanged(it) },
                        spacing = spacing
                    )

                    Spacer(modifier = Modifier.height(spacing.small))

                    // Tenant List
                    if (currentState.tenants.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No tenants match your filters.", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                                .testTag("tenants_list"),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            items(currentState.tenants, key = { it.id }) { tenant ->
                                TenantCard(
                                    tenant = tenant,
                                    onClick = { onTenantClick(tenant.id) },
                                    spacing = spacing
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
fun FilterRow(
    rooms: List<String>,
    selectedRoom: String,
    selectedOccupancy: String,
    onRoomSelected: (String) -> Unit,
    onOccupancySelected: (String) -> Unit,
    spacing: com.example.core.designsystem.PgSpacing
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        // Occupancy States Filter
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedOccupancy == "Active",
                    onClick = { onOccupancySelected("Active") },
                    label = { Text("Active") },
                    leadingIcon = if (selectedOccupancy == "Active") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(80.dp)) }
                    } else null,
                    modifier = Modifier.testTag("chip_active_residents")
                )
            }
            item {
                FilterChip(
                    selected = selectedOccupancy == "Vacated",
                    onClick = { onOccupancySelected("Vacated") },
                    label = { Text("Vacated History") },
                    leadingIcon = if (selectedOccupancy == "Vacated") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(80.dp)) }
                    } else null,
                    modifier = Modifier.testTag("chip_vacated_residents")
                )
            }
            item {
                FilterChip(
                    selected = selectedOccupancy == "All",
                    onClick = { onOccupancySelected("All") },
                    label = { Text("All Records") },
                    leadingIcon = if (selectedOccupancy == "All") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(80.dp)) }
                    } else null,
                    modifier = Modifier.testTag("chip_all_residents")
                )
            }
        }

        // Room Numbers Filter
        if (rooms.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedRoom == "All",
                        onClick = { onRoomSelected("All") },
                        label = { Text("All Rooms") },
                        leadingIcon = if (selectedRoom == "All") {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(80.dp)) }
                        } else null
                    )
                }
                items(rooms) { roomNo ->
                    FilterChip(
                        selected = selectedRoom == roomNo,
                        onClick = { onRoomSelected(roomNo) },
                        label = { Text("Room $roomNo") },
                        leadingIcon = if (selectedRoom == roomNo) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(80.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun TenantCard(
    tenant: TenantEntity,
    onClick: () -> Unit,
    spacing: com.example.core.designsystem.PgSpacing
) {
    val isVacated = tenant.roomNumber.isBlank()
    val initials = if (tenant.name.isNotBlank()) {
        tenant.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
    } else "T"

    val avatarBg = if (isVacated) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    
    val avatarColor = if (isVacated) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium)
            .clickable(onClick = onClick)
            .testTag("tenant_card_${tenant.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = avatarColor
                )
            }

            Spacer(modifier = Modifier.width(spacing.medium))

            // Text Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = tenant.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = tenant.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    if (isVacated) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Vacated") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = MaterialTheme.colorScheme.error
                            )
                        )
                    } else {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Room ${tenant.roomNumber}") }
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tenant.bedId) }
                        )
                    }
                }
            }

            // Arrow indicator
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun EmptyTenantsView(
    onAddTenantClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
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
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Tenants Registered",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Welcome to your tenant log. Check in and register your first guest to begin tracking occupancy, rents, and deposits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAddTenantClick,
                modifier = Modifier.testTag("empty_add_tenant_button")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Register First Tenant")
            }
        }
    }
}
