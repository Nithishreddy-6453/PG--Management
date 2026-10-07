package com.example.features.rooms.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.LocalSpacing
import com.example.core.language.GlobalLanguageToggle
import com.example.core.util.PgDateUtil
import com.example.data.database.RentPaymentEntity
import com.example.data.database.TenantEntity
import com.example.features.rent.domain.util.RentBillingEngine
import com.example.features.rooms.domain.model.RoomSummary
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.ui.viewmodel.RoomDetailsUiState
import com.example.features.rooms.ui.viewmodel.RoomDetailsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailsScreen(
    viewModel: RoomDetailsViewModel,
    onBackClick: () -> Unit,
    onEditRoomClick: (String) -> Unit,
    onTenantClick: (Int) -> Unit = {},
    onCollectRentClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Handle delete action event
    LaunchedEffect(key1 = true) {
        viewModel.deleteEvent.collect { result ->
            when (result) {
                is RoomValidationResult.Success -> {
                    Toast.makeText(context, "Room deleted successfully", Toast.LENGTH_SHORT).show()
                    onBackClick()
                }
                is RoomValidationResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val roomNo = (uiState as? RoomDetailsUiState.Success)?.roomSummary?.roomNumber ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (roomNo.isNotBlank()) "Room $roomNo" else "Room Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF0F172A)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    GlobalLanguageToggle(
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    if (uiState is RoomDetailsUiState.Success) {
                        IconButton(
                            onClick = { onEditRoomClick(roomNo) },
                            modifier = Modifier.testTag("edit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Room",
                                tint = Color(0xFF2563EB)
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.testTag("room_details_overflow_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = Color(0xFF64748B)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete Room", color = Color(0xFFDC2626), fontSize = 13.sp) },
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
                                        showDeleteConfirmDialog = true
                                    },
                                    modifier = Modifier.testTag("delete_button")
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF0F172A)
                ),
                modifier = Modifier.testTag("room_details_top_bar")
            )
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.fillMaxSize().testTag("room_details_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is RoomDetailsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF2563EB))
                    }
                }
                is RoomDetailsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                is RoomDetailsUiState.Success -> {
                    val summary = state.roomSummary
                    val payments = state.recentPayments

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Room Identity Header
                        RoomIdentityHeader(summary = summary)

                        // 2. Compact Occupancy Visualizer
                        RoomOccupancySection(summary = summary)

                        // 3. Tenants List
                        RoomTenantsSection(
                            summary = summary,
                            onTenantClick = onTenantClick
                        )

                        // 4. Current Room Financial Summary
                        RoomFinancialSummarySection(
                            summary = summary,
                            payments = payments,
                            onCollectRentClick = onCollectRentClick
                        )

                        // 5. Upcoming Billing
                        RoomUpcomingBillingSection(summary = summary, payments = payments)

                        // 6. Chronological Billing History
                        RoomBillingHistorySection(payments = payments)
                    }
                }
            }

            // Deletion dialog
            com.example.core.designsystem.PgConfirmDialog(
                isOpen = showDeleteConfirmDialog && uiState is RoomDetailsUiState.Success,
                title = "Delete Room $roomNo?",
                message = "Are you sure you want to delete this room? Active tenants must be checked out first. Historical rent and billing records will remain intact.",
                confirmText = "Delete Room",
                isDestructive = true,
                onConfirm = {
                    viewModel.deleteRoom()
                    showDeleteConfirmDialog = false
                },
                onDismiss = { showDeleteConfirmDialog = false }
            )
        }
    }
}

/**
 * 1. HEADER: Immediate Room Identity
 */
@Composable
fun RoomIdentityHeader(
    summary: RoomSummary,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Room ${summary.roomNumber}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Text(
                text = "${summary.floor} · ${summary.roomType}",
                fontSize = 13.sp,
                color = Color(0xFF64748B)
            )

            Text(
                text = "₹${String.format("%,.0f", summary.ratePerBed)} / bed / month",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2563EB)
            )

            if (summary.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = summary.notes,
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * 2. OCCUPANCY: Dot-based occupancy indicators and usable bed breakdown
 */
@Composable
fun RoomOccupancySection(
    summary: RoomSummary,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth().testTag("room_occupancy_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Occupancy",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                val statusColor = when (summary.occupancyStatus) {
                    "Full" -> Color(0xFFDC2626)
                    "Empty" -> Color(0xFF64748B)
                    else -> Color(0xFF2563EB)
                }
                val statusBg = when (summary.occupancyStatus) {
                    "Full" -> Color(0xFFFEF2F2)
                    "Empty" -> Color(0xFFF1F5F9)
                    else -> Color(0xFFEFF6FF)
                }

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = summary.occupancyStatus,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Visual Dots: Filled circles for occupied, outlines for vacant usable beds
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val total = summary.usableBeds.coerceAtLeast(1)
                val occupied = summary.occupiedBeds
                for (i in 0 until total) {
                    if (i < occupied) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    if (summary.availableBeds == 0) Color(0xFFDC2626) else Color(0xFF2563EB),
                                    CircleShape
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .border(1.5.dp, Color(0xFFCBD5E1), CircleShape)
                                .background(Color.Transparent, CircleShape)
                        )
                    }
                }
            }

            val occSummaryText = if (summary.availableBeds > 0) {
                "${summary.occupiedBeds} / ${summary.usableBeds} beds occupied · ${summary.availableBeds} ${if (summary.availableBeds == 1) "bed" else "beds"} available"
            } else {
                "${summary.occupiedBeds} / ${summary.usableBeds} beds occupied"
            }
            Text(
                text = occSummaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B)
            )
        }
    }
}

/**
 * 3. TENANTS: Primary section with compact tenant cards
 */
@Composable
fun RoomTenantsSection(
    summary: RoomSummary,
    onTenantClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TENANTS · ${summary.activeTenants.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 0.5.sp
            )
        }

        if (summary.activeTenants.isEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "This room is currently empty. No tenants assigned.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            summary.activeTenants.forEach { tenant ->
                val (statusLabel, statusColor) = getTenantStatusDisplay(tenant, summary.currentDateStr)
                val bedLabel = formatBedLabel(tenant.bedId)

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTenantClick(tenant.id) }
                        .testTag("tenant_item_${tenant.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = tenant.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            if (tenant.phone.isNotBlank()) {
                                Text(
                                    text = tenant.phone,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(statusColor, CircleShape)
                                )
                                Text(
                                    text = statusLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = bedLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4. CURRENT ROOM FINANCIAL SUMMARY: Reflects confirmed/edited tenant rent and active invoices
 */
@Composable
fun RoomFinancialSummarySection(
    summary: RoomSummary,
    payments: List<RentPaymentEntity>,
    onCollectRentClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val todayMonthYear = SimpleDateFormat("MMMM yyyy", Locale.US).format(Calendar.getInstance().time)
    val currentMonthIso = PgDateUtil.todayIso().substring(0, 7)

    val currentMonthPayments = payments.filter {
        !it.deleted && (
            it.billingMonth.equals(todayMonthYear, ignoreCase = true) ||
            it.dueDate.startsWith(currentMonthIso)
        )
    }

    val paymentsByTenantId = currentMonthPayments.associateBy { it.tenantId }

    // Derive expected amount from existing tenant confirmed/edited rent & existing invoices
    val expectedAmount = if (summary.activeTenants.isNotEmpty()) {
        summary.activeTenants.sumOf { tenant ->
            val payment = paymentsByTenantId[tenant.id]
            if (payment != null && payment.amount > 0) {
                payment.amount
            } else {
                val proration = RentBillingEngine.calculateProrationDetails(
                    moveInDateStr = tenant.moveInDate,
                    leavingDateStr = tenant.leavingDate,
                    billingMonthStr = todayMonthYear
                )
                if (proration.applicableDays > 0) {
                    RentBillingEngine.calculateExpectedRent(
                        monthlyRent = tenant.monthlyRent,
                        applicableDays = proration.applicableDays,
                        daysInMonth = proration.daysInMonth
                    )
                } else {
                    tenant.monthlyRent
                }
            }
        }
    } else if (currentMonthPayments.isNotEmpty()) {
        currentMonthPayments.sumOf { it.amount }
    } else {
        0.0
    }

    val collectedAmount = currentMonthPayments.sumOf { it.amountPaid }
    val outstandingAmount = (expectedAmount - collectedAmount).coerceAtLeast(0.0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "CURRENT FINANCIAL SUMMARY",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 0.5.sp
        )

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth().testTag("room_financial_summary_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = todayMonthYear.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Expected", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text("₹${String.format("%,.0f", expectedAmount)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Collected", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text("₹${String.format("%,.0f", collectedAmount)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Outstanding", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text(
                            "₹${String.format("%,.0f", outstandingAmount)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (outstandingAmount > 0) Color(0xFFDC2626) else Color(0xFF64748B)
                        )
                    }
                }

                if (outstandingAmount > 0) {
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCollectRentClick() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pending: ₹${String.format("%,.0f", outstandingAmount)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFDC2626)
                        )
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { onCollectRentClick() }
                        ) {
                            Text(
                                text = "Collect ₹${String.format("%,.0f", outstandingAmount)} →",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. UPCOMING: Future billing separated from historical records
 */
@Composable
fun RoomUpcomingBillingSection(
    summary: RoomSummary,
    payments: List<RentPaymentEntity>,
    modifier: Modifier = Modifier
) {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MONTH, 1)
    val nextMonthName = SimpleDateFormat("MMMM yyyy", Locale.US).format(calendar.time)

    val expectedRent = summary.activeTenants.sumOf { it.monthlyRent }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "UPCOMING",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 0.5.sp
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth().testTag("room_upcoming_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = nextMonthName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${summary.activeTenants.size} tenants · Upcoming",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Text(
                    text = "₹${String.format("%,.0f", expectedRent)} expected",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2563EB)
                )
            }
        }
    }
}

/**
 * 6. BILLING HISTORY: Compact chronological payment records
 */
@Composable
fun RoomBillingHistorySection(
    payments: List<RentPaymentEntity>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "BILLING HISTORY",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 0.5.sp
        )

        if (payments.isEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No billing records found for this room.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            payments.forEach { payment ->
                val isPaid = payment.status.equals("Paid", ignoreCase = true)
                val isOverdue = payment.status.equals("Overdue", ignoreCase = true)
                val statusColor = when {
                    isPaid -> Color(0xFF059669)
                    isOverdue -> Color(0xFFDC2626)
                    else -> Color(0xFFD97706)
                }
                val statusBg = when {
                    isPaid -> Color(0xFFECFDF5)
                    isOverdue -> Color(0xFFFEF2F2)
                    else -> Color(0xFFFEF3C7)
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${payment.billingMonth} · ${payment.tenantName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            val remaining = (payment.amount - payment.amountPaid).coerceAtLeast(0.0)
                            val amountDetail = if (payment.amountPaid > 0 && remaining > 0) {
                                "₹${String.format("%,.0f", payment.amount)} rent · ₹${String.format("%,.0f", payment.amountPaid)} paid · ₹${String.format("%,.0f", remaining)} remaining"
                            } else {
                                "₹${String.format("%,.0f", payment.amount)}"
                            }
                            Text(
                                text = amountDetail,
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Surface(
                            color = statusBg,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = payment.status,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Standardize presentation of bed identifiers to e.g. Bed A, Bed B, Bed C
 */
private fun formatBedLabel(bedId: String): String {
    if (bedId.isBlank()) return "Bed"
    val trimmed = bedId.trim()
    if (trimmed.startsWith("Bed ", ignoreCase = true)) return trimmed
    if (trimmed.length == 1 && trimmed[0].isLetter()) return "Bed ${trimmed.uppercase()}"
    if (trimmed.startsWith("bed_", ignoreCase = true) || trimmed.startsWith("bed-", ignoreCase = true)) {
        val suffix = trimmed.substring(4)
        return if (suffix.length == 1 && suffix[0].isLetter()) "Bed ${suffix.uppercase()}" else "Bed $suffix"
    }
    return if (trimmed.startsWith("Bed", ignoreCase = true)) trimmed else "Bed $trimmed"
}

/**
 * Human-readable tenant status display
 */
private fun getTenantStatusDisplay(tenant: TenantEntity, currentDateStr: String): Pair<String, Color> {
    return when {
        tenant.deleted -> "Vacated" to Color(0xFF64748B)
        tenant.leavingDate.isNotBlank() && PgDateUtil.isDatePastOrToday(tenant.leavingDate, currentDateStr) -> {
            if (tenant.leavingDate == currentDateStr) "Moves out today" to Color(0xFFDC2626)
            else "Move-out overdue" to Color(0xFFDC2626)
        }
        tenant.leavingDate.isNotBlank() && PgDateUtil.isDateFuture(tenant.leavingDate, currentDateStr) -> {
            val formattedDate = try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(tenant.leavingDate)
                if (parsed != null) SimpleDateFormat("MMM d", Locale.US).format(parsed) else tenant.leavingDate
            } catch (_: Exception) {
                tenant.leavingDate
            }
            "Moves out $formattedDate" to Color(0xFFD97706)
        }
        else -> "Active" to Color(0xFF059669)
    }
}
