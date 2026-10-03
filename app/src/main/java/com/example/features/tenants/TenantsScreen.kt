package com.example.features.tenants

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.LocalSpacing
import com.example.data.database.TenantEntity
import com.example.features.tenants.ui.viewmodel.TenantListUiState
import com.example.features.tenants.ui.viewmodel.TenantListViewModel
import com.example.navigation.Screen

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
    val spacing = LocalSpacing.current
    val state by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tenants",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF0F172A)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tenants_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    com.example.core.language.GlobalLanguageToggle(
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(
                        onClick = { onNavigate(Screen.PendingRegistrations.route) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pending_registrations_nav_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HowToReg,
                            contentDescription = "Pending Online Registrations",
                            tint = Color(0xFFF59E0B)
                        )
                    }
                    IconButton(
                        onClick = { onNavigate(Screen.TenantRegistrationForm.route) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tenant_registration_form_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = "Online Registration Form",
                            tint = Color(0xFF2563EB)
                        )
                    }
                    IconButton(
                        onClick = onImportExcelClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("import_tenants_excel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import from Excel",
                            tint = Color(0xFF0F172A)
                        )
                    }
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sort_tenants_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter / Sort",
                            tint = Color(0xFF0F172A)
                        )
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
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF0F172A)
                ),
                modifier = Modifier.testTag("tenants_top_bar")
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(72.dp)
                    .shadow(8.dp, CircleShape, spotColor = Color(0x660066FF))
                    .clip(CircleShape)
                    .background(Color(0xFF0066FF))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.White.copy(alpha = 0.3f)),
                        onClick = onAddTenantClick
                    )
                    .testTag("add_tenant_fab"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Add Tenant",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Add Tenant",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        bottomBar = {
            TenantsBottomNavigationBar(
                currentRoute = Screen.Tenants.route,
                onNavigate = onNavigate
            )
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.fillMaxSize().testTag("tenants_screen_container")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC))
        ) {
            when (val currentState = state) {
                is TenantListUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color(0xFF2563EB),
                            modifier = Modifier.testTag("tenants_loading_indicator")
                        )
                    }
                }
                is TenantListUiState.Empty -> {
                    EmptyTenantsView(
                        onAddTenantClick = onAddTenantClick,
                        onRegistrationFormClick = { onNavigate(Screen.TenantRegistrationForm.route) }
                    )
                }
                is TenantListUiState.Success -> {
                    // 1. Search Bar
                    SearchInputField(
                        query = currentState.searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // 2. Status & Room Filters
                    TenantsFilterSection(
                        rooms = currentState.roomsList,
                        selectedRoom = currentState.selectedRoomFilter,
                        selectedOccupancy = currentState.selectedOccupancyFilter,
                        onRoomSelected = { viewModel.onRoomFilterChanged(it) },
                        onOccupancySelected = { viewModel.onOccupancyFilterChanged(it) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Tenant List
                    if (currentState.tenants.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tenants match your filters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF64748B)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                                .testTag("tenants_list"),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(currentState.tenants, key = { it.id }) { tenant ->
                                TenantCard(
                                    tenant = tenant,
                                    onClick = { onTenantClick(tenant.id) }
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
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(25.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
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
                        text = "Search by name or phone...",
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
                    modifier = Modifier.size(24.dp)
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
private fun TenantsFilterSection(
    rooms: List<String>,
    selectedRoom: String,
    selectedOccupancy: String,
    onRoomSelected: (String) -> Unit,
    onOccupancySelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Occupancy Status Filter Buttons (Active | Leaving Soon | Vacated | All)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Active Button
            item {
                val isActiveSelected = selectedOccupancy == "Active"
                StatusFilterButton(
                    label = "Active",
                    icon = Icons.Default.PersonOutline,
                    isSelected = isActiveSelected,
                    selectedBg = Color(0xFFE6F9EE),
                    selectedColor = Color(0xFF16A34A),
                    onClick = { onOccupancySelected("Active") },
                    modifier = Modifier.testTag("chip_active_residents")
                )
            }

            // Leaving Soon Button
            item {
                val isLeavingSelected = selectedOccupancy == "Leaving Soon"
                StatusFilterButton(
                    label = "Leaving Soon",
                    icon = Icons.Default.EventBusy,
                    isSelected = isLeavingSelected,
                    selectedBg = Color(0xFFFEF3C7),
                    selectedColor = Color(0xFFD97706),
                    onClick = { onOccupancySelected("Leaving Soon") },
                    modifier = Modifier.testTag("chip_leaving_soon")
                )
            }

            // Vacated Button
            item {
                val isVacatedSelected = selectedOccupancy == "Vacated"
                StatusFilterButton(
                    label = "Vacated",
                    icon = Icons.Default.PersonOff,
                    isSelected = isVacatedSelected,
                    selectedBg = Color(0xFFFEE2E2),
                    selectedColor = Color(0xFFDC2626),
                    onClick = { onOccupancySelected("Vacated") },
                    modifier = Modifier.testTag("chip_vacated_residents")
                )
            }

            // All Button
            item {
                val isAllSelected = selectedOccupancy == "All"
                StatusFilterButton(
                    label = "All",
                    icon = Icons.Default.GridView,
                    isSelected = isAllSelected,
                    selectedBg = Color(0xFFEFF6FF),
                    selectedColor = Color(0xFF2563EB),
                    onClick = { onOccupancySelected("All") },
                    modifier = Modifier.testTag("chip_all_residents")
                )
            }
        }

        // 2. Horizontally Scrollable Room Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // All Rooms chip
            item {
                val isSelected = selectedRoom == "All"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onRoomSelected("All") }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apartment,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFF2563EB) else Color(0xFF475569),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "All Rooms",
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF334155),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Dynamic Rooms
            items(rooms) { roomNo ->
                val isSelected = selectedRoom == roomNo
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onRoomSelected(roomNo) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Room $roomNo",
                        color = if (isSelected) Color(0xFF2563EB) else Color(0xFF334155),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusFilterButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    selectedBg: Color,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) selectedBg else Color(0xFFF1F5F9))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = selectedColor.copy(alpha = 0.2f)),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) selectedColor else Color(0xFF475569),
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                color = if (isSelected) selectedColor else Color(0xFF334155),
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.5.sp
            )
        }
    }
}

@Composable
fun TenantCard(
    tenant: TenantEntity,
    onClick: () -> Unit
) {
    val isVacated = tenant.roomNumber.isBlank()
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
            'A', 'G', 'M', 'S', 'Y' -> Pair(Color(0xFFF3E8FF), Color(0xFF9333EA)) // Purple / Pinkish
            'B', 'H', 'N', 'T', 'Z' -> Pair(Color(0xFFDBEAFE), Color(0xFF2563EB)) // Blue
            'C', 'I', 'O', 'U' -> Pair(Color(0xFFFFEDD5), Color(0xFFEA580C)) // Peach / Orange
            'D', 'J', 'P', 'V' -> Pair(Color(0xFFDCFCE7), Color(0xFF16A34A)) // Green
            'E', 'K', 'Q', 'W' -> Pair(Color(0xFFFCE7F3), Color(0xFFDB2777)) // Soft Pink
            else -> Pair(Color(0xFFFFEDD5), Color(0xFFD97706)) // Amber / Orange
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
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
            // 1. Initial / Photo Avatar Circle
            Box(
                modifier = Modifier
                    .size(46.dp)
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = avatarText
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 2. Tenant Info Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Name & Status Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = tenant.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp,
                            color = Color(0xFF0F172A)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Status Pill Badge
                    if (isVacated) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFEE2E2))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Vacated",
                                color = Color(0xFFDC2626),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (tenant.leavingDate.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFEF3C7))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Leaving Soon",
                                color = Color(0xFFD97706),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFDCFCE7))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Active",
                                color = Color(0xFF16A34A),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Phone Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = Color(0xFF64748B)
                    )
                    Text(
                        text = tenant.phone,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF475569),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }

                // If Leaving Date is set and not vacated, show vacating date
                if (!isVacated && tenant.leavingDate.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = Color(0xFFD97706)
                        )
                        Text(
                            text = "Vacating on: ${tenant.leavingDate}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFB45309),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // If Vacated, show checkout/vacated date
                if (isVacated) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = Color(0xFF64748B)
                        )
                        Text(
                            text = "Vacated" + (if (tenant.moveInDate.isNotBlank()) " on ${tenant.moveInDate}" else ""),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Room & Bed Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Room Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SingleBed,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Room ${if (tenant.roomNumber.isNotBlank()) tenant.roomNumber else "—"}",
                                color = Color(0xFF334155),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Bed Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SingleBed,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(14.dp)
                            )
                            val bedText = when {
                                tenant.bedId.isBlank() -> "Bed —"
                                tenant.bedId.startsWith("Bed", ignoreCase = true) -> tenant.bedId
                                else -> "Bed ${tenant.bedId}"
                            }
                            Text(
                                text = bedText,
                                color = Color(0xFF334155),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Right Icons (More options & Chevron)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(72.dp)
            ) {
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View Details",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
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
                modifier = Modifier.size(72.dp),
                tint = Color(0xFF2563EB).copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Tenants Registered",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Welcome to your tenant directory. Register your first tenant to track rooms, beds, rent, and contact records.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAddTenantClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066FF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_add_tenant_button")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Register First Tenant", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRegistrationFormClick,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF2563EB)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_registration_form_button")
            ) {
                Icon(Icons.Default.Assignment, contentDescription = null, tint = Color(0xFF2563EB))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Google Form Registration", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TenantsBottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    com.example.core.designsystem.AppBottomNavBar(
        currentTab = com.example.core.designsystem.MainTab.TENANTS,
        onNavigate = onNavigate
    )
}

@Composable
private fun TenantsBottomNavItem(
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
