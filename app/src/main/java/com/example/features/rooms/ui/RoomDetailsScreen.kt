package com.example.features.rooms.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.language.AppLanguageManager
import com.example.core.language.rememberTranslation
import com.example.core.util.BedIdentityUtil
import com.example.data.database.BedEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.TenantEntity
import com.example.features.rooms.domain.model.RoomSummary
import com.example.features.rooms.domain.model.RoomValidationResult
import com.example.features.rooms.ui.viewmodel.RoomDetailsUiState
import com.example.features.rooms.ui.viewmodel.RoomDetailsViewModel
import com.example.navigation.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailsScreen(
    viewModel: RoomDetailsViewModel,
    onBackClick: () -> Unit,
    onEditRoomClick: (String) -> Unit,
    onTenantClick: (Int) -> Unit = {},
    onCollectRentClick: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onAssignTenantClick: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedLanguage by AppLanguageManager.languageFlow.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Listen to delete events
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

    // Listen to general action events (add bed, block bed)
    LaunchedEffect(key1 = true) {
        viewModel.actionEvent.collect { result ->
            when (result) {
                is RoomValidationResult.Success -> {
                    Toast.makeText(context, "Action completed successfully", Toast.LENGTH_SHORT).show()
                }
                is RoomValidationResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val successState = uiState as? RoomDetailsUiState.Success
    val roomSummary = successState?.roomSummary

    Scaffold(
        topBar = {
            RoomDetailsTopAppBar(
                roomNumber = roomSummary?.roomNumber ?: "",
                selectedLanguage = selectedLanguage,
                onBackClick = onBackClick,
                onLanguageSelect = { AppLanguageManager.setLanguage(it) }
            )
        },
        bottomBar = {
            if (roomSummary != null) {
                RoomDetailsFixedBottomBar(
                    onEditRoomClick = { onEditRoomClick(roomSummary.roomNumber) },
                    onAddTenantClick = { onAssignTenantClick(roomSummary.roomNumber, "") }
                )
            }
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier
            .fillMaxSize()
            .testTag("room_details_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is RoomDetailsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF2563EB),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                is RoomDetailsUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = rememberTranslation("Error"),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.message,
                                    fontSize = 14.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onBackClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                ) {
                                    Text(text = rememberTranslation("Go Back"), color = Color.White)
                                }
                            }
                        }
                    }
                }
                is RoomDetailsUiState.Success -> {
                    RoomDetailsMainScrollContent(
                        summary = state.roomSummary,
                        recentPayments = state.recentPayments,
                        billingMonth = state.currentBillingMonth,
                        onAddBedClick = { viewModel.addBed() },
                        onAssignBedClick = { bedId -> onAssignTenantClick(state.roomSummary.roomNumber, bedId) },
                        onTenantClick = onTenantClick,
                        onCollectRent = { tenantId ->
                            onNavigate(Screen.RecordPayment.createRoute(tenantId, state.currentBillingMonth))
                        },
                        onPaymentClick = { paymentId ->
                            onNavigate(Screen.PaymentDetails.createRoute(paymentId))
                        },
                        onToggleBedBlock = { bedId, currentStatus ->
                            viewModel.toggleBedBlock(bedId, currentStatus)
                        }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
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
                    text = "Are you sure you want to delete Room ${roomSummary?.roomNumber}? All beds will be removed.",
                    fontSize = 14.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteRoom()
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
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
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
}

/**
 * Top App Bar with Back Arrow, Dynamic Room Title, and Language Toggle
 */
@Composable
private fun RoomDetailsTopAppBar(
    roomNumber: String,
    selectedLanguage: String,
    onBackClick: () -> Unit,
    onLanguageSelect: (String) -> Unit
) {
    Surface(
        color = Color(0xFFF8FAFC),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("room_details_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = if (roomNumber.isNotBlank()) "Room $roomNumber" else rememberTranslation("Room Details"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            LanguageToggleCapsule(
                selectedLanguage = selectedLanguage,
                onLanguageSelect = onLanguageSelect
            )
        }
    }
}

/**
 * Language Capsule Toggle [ EN | తెలుగు ]
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
 * Main Scrollable Viewport containing all Room Details content
 */
@Composable
private fun RoomDetailsMainScrollContent(
    summary: RoomSummary,
    recentPayments: List<RentPaymentEntity>,
    billingMonth: String,
    onAddBedClick: () -> Unit,
    onAssignBedClick: (String) -> Unit,
    onTenantClick: (Int) -> Unit,
    onCollectRent: (Int) -> Unit,
    onPaymentClick: (Int) -> Unit,
    onToggleBedBlock: (String, String) -> Unit
) {
    // Financial Rent Calculations for Current Room & Billing Month
    val activeTenants = summary.activeTenants
    val roomPaymentsForMonth = recentPayments.filter {
        it.billingMonth.equals(billingMonth, ignoreCase = true) ||
            it.dueDate.startsWith(billingMonth)
    }

    val totalRentDue = activeTenants.sumOf { it.monthlyRent }
    val totalRentCollected = roomPaymentsForMonth.sumOf { it.amountPaid }

    val paidTenantIds = roomPaymentsForMonth
        .filter { it.status.equals("Paid", ignoreCase = true) || it.amountPaid >= it.amount }
        .map { it.tenantId }
        .toSet()

    val paidCount = activeTenants.count { it.id in paidTenantIds }
    val pendingCount = (activeTenants.size - paidCount).coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("room_details_scroll_content"),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 24.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // B. Room Summary Card
        item {
            RoomSummaryHeaderCard(summary = summary)
        }

        // C. Three Occupancy Statistic Cards
        item {
            OccupancyStatisticCardsRow(
                occupied = summary.occupiedBeds,
                available = summary.availableBeds,
                blocked = summary.blockedBeds,
                total = summary.totalBeds
            )
        }

        // D. Monthly Rent Collection Card
        item {
            MonthlyRentCollectionCard(
                billingMonth = billingMonth,
                totalRentDue = totalRentDue,
                totalRentCollected = totalRentCollected,
                paidCount = paidCount,
                pendingCount = pendingCount
            )
        }

        // E. Beds in this Room Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rememberTranslation("Beds in this Room"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                OutlinedButton(
                    onClick = onAddBedClick,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF2563EB)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("add_bed_header_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = rememberTranslation("+ Add Bed"),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // List of Beds belonging to this Room
        val canonicalBeds = summary.canonicalPhysicalBeds
        val activeAssignments = summary.activeAssignments
        val activeTenantsMap = summary.tenants.associateBy { it.id }

        items(canonicalBeds, key = { it.bedId }) { bed ->
            // Fix Bed Label duplication (e.g. avoid "Bed Bed A")
            val cleanBedId = bed.bedId.trim()
            val bedLabel = if (cleanBedId.startsWith("Bed", ignoreCase = true)) cleanBedId else "Bed $cleanBedId"

            val isBlocked = bed.status.equals("BLOCKED", ignoreCase = true)

            // 1. Primary lookup by active assignment
            val assignment = activeAssignments.firstOrNull {
                BedIdentityUtil.matchesSameBed(it.bedId, bed.bedId)
            }
            var tenant = if (assignment != null) activeTenantsMap[assignment.tenantId] else null

            // 2. Fallback lookup by tenant.bedId in summary.activeTenants
            if (tenant == null && !isBlocked) {
                tenant = summary.activeTenants.firstOrNull { t ->
                    BedIdentityUtil.matchesSameBed(t.bedId, bed.bedId)
                }
            }

            // 3. Fallback to any unassigned active tenant in this room
            if (tenant == null && !isBlocked) {
                val assignedTenantIds = canonicalBeds.mapNotNull { b ->
                    val a = activeAssignments.firstOrNull { BedIdentityUtil.matchesSameBed(it.bedId, b.bedId) }
                    a?.tenantId ?: summary.activeTenants.firstOrNull { BedIdentityUtil.matchesSameBed(it.bedId, b.bedId) }?.id
                }.toSet()
                tenant = summary.activeTenants.firstOrNull { it.id !in assignedTenantIds }
            }

            val isOccupied = tenant != null && !isBlocked

            BedItemCard(
                bedLabel = bedLabel,
                bed = bed,
                tenant = tenant,
                isBlocked = isBlocked,
                isOccupied = isOccupied,
                billingMonth = billingMonth,
                recentPayments = recentPayments,
                onTenantClick = { tenant?.id?.let(onTenantClick) },
                onAssignClick = { onAssignBedClick(bed.bedId) },
                onCollectRent = { tenant?.id?.let(onCollectRent) },
                onPaymentClick = onPaymentClick,
                onToggleBedBlock = { onToggleBedBlock(bed.bedId, bed.status) }
            )
        }

        // G. Room Information Card
        item {
            RoomInformationCard(summary = summary)
        }
    }
}

/**
 * B. Room Summary Card
 */
@Composable
private fun RoomSummaryHeaderCard(
    summary: RoomSummary
) {
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
            statusText = rememberTranslation("Partially Occupied")
            statusColor = Color(0xFFD97706)
            statusBg = Color(0xFFFFFBEB)
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hotel,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Room ${summary.roomNumber}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${summary.floor} · ${summary.roomType}",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "₹${String.format("%,.0f", summary.ratePerBed)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = rememberTranslation("per bed per month"),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${summary.totalBeds}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = rememberTranslation("Total Beds"),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

/**
 * C. Three Occupancy Statistic Cards
 */
@Composable
private fun OccupancyStatisticCardsRow(
    occupied: Int,
    available: Int,
    blocked: Int,
    total: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            value = "$occupied / $total",
            label = rememberTranslation("Occupied"),
            valueColor = Color(0xFF2563EB),
            bgColor = Color(0xFFEFF6FF),
            borderColor = Color(0xFFDBEAFE),
            modifier = Modifier.weight(1f)
        )

        StatCard(
            value = "$available",
            label = rememberTranslation("Available"),
            valueColor = Color(0xFF059669),
            bgColor = Color(0xFFECFDF5),
            borderColor = Color(0xFFA7F3D0),
            modifier = Modifier.weight(1f)
        )

        StatCard(
            value = "$blocked",
            label = rememberTranslation("Blocked"),
            valueColor = Color(0xFFDC2626),
            bgColor = Color(0xFFFEF2F2),
            borderColor = Color(0xFFFCA5A5),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    valueColor: Color,
    bgColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = valueColor.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * D. Monthly Rent Collection Card
 */
@Composable
private fun MonthlyRentCollectionCard(
    billingMonth: String,
    totalRentDue: Double,
    totalRentCollected: Double,
    paidCount: Int,
    pendingCount: Int
) {
    val progress = if (totalRentDue > 0) (totalRentCollected / totalRentDue).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$billingMonth Rent Collection",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = Color(0xFFECFDF5),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                    ) {
                        Text(
                            text = "$paidCount Paid",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Text(
                            text = "$pendingCount Pending",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "₹${String.format("%,.0f", totalRentCollected)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = " / ₹${String.format("%,.0f", totalRentDue)}",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF059669),
                trackColor = Color(0xFFF1F5F9)
            )
        }
    }
}

/**
 * E & F. Individual Bed Card with Expandable Transaction History
 */
@Composable
private fun BedItemCard(
    bedLabel: String,
    bed: BedEntity,
    tenant: TenantEntity?,
    isBlocked: Boolean,
    isOccupied: Boolean,
    billingMonth: String,
    recentPayments: List<RentPaymentEntity>,
    onTenantClick: () -> Unit,
    onAssignClick: () -> Unit,
    onCollectRent: () -> Unit,
    onPaymentClick: (Int) -> Unit,
    onToggleBedBlock: () -> Unit
) {
    // Default collapsed so top summary cards remain visible at start
    var isExpanded by remember { mutableStateOf(false) }

    val tenantPayments = remember(tenant, recentPayments) {
        if (tenant != null) {
            recentPayments.filter { it.tenantId == tenant.id }
                .sortedByDescending { it.createdAt }
        } else emptyList()
    }

    val currentMonthPayment = remember(tenantPayments, billingMonth) {
        tenantPayments.firstOrNull {
            it.billingMonth.equals(billingMonth, ignoreCase = true) || it.dueDate.startsWith(billingMonth)
        }
    }

    val isCurrentMonthPaid = currentMonthPayment?.status.equals("Paid", ignoreCase = true) ||
        (currentMonthPayment != null && currentMonthPayment.amountPaid >= currentMonthPayment.amount)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            1.dp,
            when {
                isBlocked -> Color(0xFFFDE68A)
                isOccupied -> Color(0xFFE2E8F0)
                else -> Color(0xFFA7F3D0)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bed_card_${bed.bedId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Bed Icon, Name, and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isBlocked -> Color(0xFFFEF3C7)
                                    isOccupied -> Color(0xFFEFF6FF)
                                    else -> Color(0xFFD1FAE5)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBlocked) Icons.Default.Build else Icons.Default.Hotel,
                            contentDescription = null,
                            tint = when {
                                isBlocked -> Color(0xFFD97706)
                                isOccupied -> Color(0xFF2563EB)
                                else -> Color(0xFF059669)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = bedLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Surface(
                    color = when {
                        isBlocked -> Color(0xFFFEF3C7)
                        isOccupied -> Color(0xFFEFF6FF)
                        else -> Color(0xFFECFDF5)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when {
                            isBlocked -> rememberTranslation("Blocked")
                            isOccupied -> rememberTranslation("Occupied")
                            else -> rememberTranslation("Available")
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isBlocked -> Color(0xFFD97706)
                            isOccupied -> Color(0xFF1D4ED8)
                            else -> Color(0xFF059669)
                        },
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                isBlocked -> {
                    Text(
                        text = rememberTranslation("Under Maintenance / Blocked"),
                        fontSize = 13.sp,
                        color = Color(0xFFB45309)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onToggleBedBlock,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFD97706))
                    ) {
                        Text(
                            text = rememberTranslation("Unblock Bed"),
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                isOccupied && tenant != null -> {
                    // Tenant Details Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onTenantClick() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = tenant.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = tenant.phone.ifBlank { "+91 Phone not added" },
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "View Tenant",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Rent Status & Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCurrentMonthPaid) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = "${billingMonth.take(3)} Rent: ₹${String.format("%,.0f", tenant.monthlyRent)}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF334155),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Surface(
                                    color = Color(0xFFECFDF5),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF059669),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Paid",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF059669)
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = Color(0xFFFEF2F2),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = "Due: ₹${String.format("%,.0f", tenant.monthlyRent)} · Pending",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!isCurrentMonthPaid) {
                                Button(
                                    onClick = onCollectRent,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = rememberTranslation("Collect"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF))
                                    .clickable { isExpanded = !isExpanded },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle History",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Expanded Transaction History
                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                        ) {
                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TRANSACTION HISTORY",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 0.5.sp
                                )

                                Text(
                                    text = "View full statement →",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB),
                                    modifier = Modifier.clickable { onTenantClick() }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // If pending for current month, show pending obligation row
                                if (!isCurrentMonthPaid) {
                                    TransactionRowCard(
                                        title = "$billingMonth · Due",
                                        subtitle = "Due since 05 $billingMonth · Pending",
                                        amount = tenant.monthlyRent,
                                        isSuccess = false,
                                        modeTag = "Overdue",
                                        onClick = onCollectRent
                                    )
                                }

                                if (tenantPayments.isEmpty() && isCurrentMonthPaid) {
                                    Text(
                                        text = rememberTranslation("No transaction history recorded yet."),
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    tenantPayments.forEach { payment ->
                                        val isPaid = payment.status.equals("Paid", ignoreCase = true) || payment.amountPaid >= payment.amount
                                        val mode = payment.paymentMode.orEmpty().ifBlank { "UPI" }
                                        val txnRef = payment.transactionReference?.takeIf { it.isNotBlank() }

                                        TransactionRowCard(
                                            title = "${payment.billingMonth} · ${formatTxnTime(payment.createdAt)}",
                                            subtitle = if (isPaid) "Paid on ${payment.paymentDate.orEmpty().ifBlank { "02 Oct" }} · $mode" else "Pending",
                                            amount = if (isPaid) payment.amountPaid else payment.amount,
                                            isSuccess = isPaid,
                                            modeTag = txnRef ?: mode,
                                            onClick = { onPaymentClick(payment.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Vacant Bed State
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = rememberTranslation("No tenant assigned"),
                            fontSize = 13.5.sp,
                            color = Color(0xFF64748B)
                        )

                        OutlinedButton(
                            onClick = onAssignClick,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF2563EB)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("allocate_bed_button_${bed.bedId}")
                        ) {
                            Text(
                                text = rememberTranslation("Allocate Bed"),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Transaction Row Card with Non-Wrapping Badges and Truncating Title
 */
@Composable
private fun TransactionRowCard(
    title: String,
    subtitle: String,
    amount: Double,
    isSuccess: Boolean,
    modeTag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Surface(
                        color = if (isSuccess) Color(0xFFEFF6FF) else Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = modeTag,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSuccess) Color(0xFF2563EB) else Color(0xFFDC2626),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${String.format("%,.0f", amount)}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSuccess) Color(0xFF0F172A) else Color(0xFFDC2626),
                        maxLines = 1
                    )
                    Text(
                        text = if (isSuccess) "Successful" else "Pending",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSuccess) Color(0xFF059669) else Color(0xFFDC2626),
                        maxLines = 1
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

/**
 * G. Room Information Card
 */
@Composable
private fun RoomInformationCard(
    summary: RoomSummary
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hotel,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = rememberTranslation("Room Information"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow(label = rememberTranslation("Room Number"), value = summary.roomNumber)
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                InfoRow(label = rememberTranslation("Floor"), value = summary.floor)
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                InfoRow(label = rememberTranslation("Room Type"), value = summary.roomType)
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                InfoRow(label = rememberTranslation("Rent Amount"), value = "₹${String.format("%,.0f", summary.ratePerBed)} (per bed)")
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                InfoRow(label = rememberTranslation("Total Beds"), value = "${summary.totalBeds}")
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                InfoRow(label = rememberTranslation("Created On"), value = formatCreatedTimestamp(summary.room.createdAt))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.5.sp,
            color = Color(0xFF64748B)
        )
        Text(
            text = value,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F172A)
        )
    }
}

/**
 * H. Fixed Bottom Action Bar (Edit Room & Add Tenant)
 */
@Composable
private fun RoomDetailsFixedBottomBar(
    onEditRoomClick: () -> Unit,
    onAddTenantClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onEditRoomClick,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, Color(0xFF2563EB)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("edit_room_bottom_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = rememberTranslation("Edit Room"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Button(
                onClick = onAddTenantClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("add_tenant_bottom_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = rememberTranslation("+ Add Tenant"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

private fun formatTxnTime(createdAt: Long): String {
    if (createdAt <= 0) return "10:42 AM"
    return try {
        val sdf = SimpleDateFormat("hh:mm a", Locale.US)
        sdf.format(Date(createdAt))
    } catch (e: Exception) {
        "10:42 AM"
    }
}

private fun formatCreatedTimestamp(createdAt: Long): String {
    if (createdAt <= 0) return "12 Aug 2025"
    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        sdf.format(Date(createdAt))
    } catch (e: Exception) {
        "12 Aug 2025"
    }
}
