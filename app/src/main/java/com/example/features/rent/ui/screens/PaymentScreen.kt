package com.example.features.rent.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.core.language.GlobalLanguageToggle
import com.example.core.language.rememberTranslation
import com.example.data.database.RentPaymentEntity
import com.example.data.database.TenantEntity
import com.example.features.rent.ui.viewmodel.PaymentViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onNavigateBack: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var methodExpanded by remember { mutableStateOf(false) }
    var monthDropdownExpanded by remember { mutableStateOf(false) }
    var showTenantPicker by remember { mutableStateOf(false) }
    var showAllHistory by remember { mutableStateOf(false) }

    val paymentMethods = listOf("UPI", "Bank Transfer", "Cash", "Card", "Cheque")

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onAttachmentSelected(uri, "Payment_Screenshot_${System.currentTimeMillis()}.jpg")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.actionEvent.collectLatest { success ->
            if (success) {
                onNavigateBack()
            }
        }
    }

    val amountPaidDouble = uiState.amountPaid.replace(",", "").replace("₹", "").trim().toDoubleOrNull() ?: 0.0
    val totalPaid = uiState.alreadyPaidAmount + amountPaidDouble
    val currentOutstanding = (uiState.expectedAmount - uiState.alreadyPaidAmount).coerceAtLeast(0.0)

    // DatePicker setup
    val calendar = Calendar.getInstance()
    try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val d = sdf.parse(uiState.paymentDate)
        if (d != null) calendar.time = d
    } catch (_: Exception) {}

    val datePickerDialog = remember(context, uiState.paymentDate) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth)
                val newDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                viewModel.onPaymentDateChanged(newDateStr)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Scaffold(
        containerColor = Color(0xFFF1F5FB),
        bottomBar = {
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = { viewModel.savePayment() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = rememberTranslation("Save Payment"),
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF2563EB))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(bottom = paddingValues.calculateBottomPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                // 1. Top Header Row: Circular Back Button, Title + Property Subtitle, EN/Telugu Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), CircleShape)
                            .clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E293B),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rememberTranslation("Collect Rent"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF101828)
                        )
                        Text(
                            text = uiState.propertyName.ifBlank { "Sri Lakshmi Residency" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF64748B)
                        )
                    }

                    GlobalLanguageToggle()
                }

                if (uiState.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.error!!,
                            color = Color(0xFFB91C1C),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                if (uiState.attachmentError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.attachmentError!!,
                            color = Color(0xFF92400E),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                // 2. Tenant Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Card Header Row: Icon + "Tenant" + "Change" button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0F2FE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = rememberTranslation("Tenant"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEFF6FF))
                                    .clickable { showTenantPicker = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = rememberTranslation("Change"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2563EB)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Tenant Info Row (Clickable to change tenant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showTenantPicker = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular Avatar with Blue Ring
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDBEAFE))
                                    .border(2.dp, Color(0xFF3B82F6), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val initials = uiState.tenantName
                                    .split(" ")
                                    .filter { it.isNotBlank() }
                                    .mapNotNull { it.firstOrNull()?.toString() }
                                    .take(2)
                                    .joinToString("")
                                    .uppercase()
                                    .ifBlank { "TN" }

                                Text(
                                    text = initials,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.tenantName.ifBlank { "Select Tenant" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val roomInfo = buildString {
                                    append("Room ")
                                    append(uiState.roomNumber.ifBlank { "A-101" })
                                    if (uiState.bedId.isNotBlank()) {
                                        append(" (${uiState.bedId})")
                                    }
                                    append(" • ")
                                    append(uiState.propertyName.ifBlank { "Sri Lakshmi Residency" })
                                }
                                Text(
                                    text = roomInfo,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = uiState.tenantPhone.ifBlank { "+91 98765 43210" },
                                        fontSize = 12.sp,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Select Tenant",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 3. Billing Month Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Top row with month dropdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rememberTranslation("Billing Month"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = rememberTranslation("Select the month for this payment"),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            // Month Dropdown Box
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF8FAFC))
                                        .clickable { monthDropdownExpanded = true }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = uiState.billingMonth.ifBlank { "October 2026" },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = monthDropdownExpanded,
                                    onDismissRequest = { monthDropdownExpanded = false }
                                ) {
                                    uiState.availableBillingMonths.forEach { month ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = month,
                                                    fontWeight = if (month == uiState.billingMonth) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (month == uiState.billingMonth) Color(0xFF2563EB) else Color(0xFF1E293B)
                                                )
                                            },
                                            onClick = {
                                                viewModel.onBillingMonthChanged(month)
                                                monthDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 3 Summary Metric Cards: Monthly Rent, Already Paid, Outstanding
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Monthly Rent Card (Pale Blue)
                            MetricPillCard(
                                modifier = Modifier.weight(1f),
                                label = rememberTranslation("Monthly Rent"),
                                amount = "₹ ${formatAmount(uiState.expectedAmount)}",
                                bgColor = Color(0xFFF0F7FF),
                                borderColor = Color(0xFFE0EDFE),
                                textColor = Color(0xFF1E293B)
                            )

                            // Already Paid Card (Pale Green)
                            MetricPillCard(
                                modifier = Modifier.weight(1f),
                                label = rememberTranslation("Already Paid"),
                                amount = "₹ ${formatAmount(uiState.alreadyPaidAmount)}",
                                bgColor = Color(0xFFF0FDF4),
                                borderColor = Color(0xFFDCFCE7),
                                textColor = Color(0xFF16A34A)
                            )

                            // Outstanding Card (Pale Red)
                            MetricPillCard(
                                modifier = Modifier.weight(1f),
                                label = rememberTranslation("Outstanding"),
                                amount = "₹ ${formatAmount(currentOutstanding)}",
                                bgColor = Color(0xFFFEF2F2),
                                borderColor = Color(0xFFFEE2E2),
                                textColor = Color(0xFFDC2626)
                            )
                        }
                    }
                }

                // 4. Payment Details Card (Two-Column Layout)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Section Header: Icon + "Payment Details"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payment,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = rememberTranslation("Payment Details"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Row 1: Amount Received * | Payment Date *
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Column 1: Amount Received
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = rememberTranslation("Amount Received"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF334155)
                                    )
                                    Text(text = " *", fontSize = 12.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .border(BorderStroke(1.5.dp, Color(0xFF2563EB)), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "₹  ",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                        BasicTextField(
                                            value = uiState.amountPaid,
                                            onValueChange = viewModel::onAmountPaidChanged,
                                            textStyle = TextStyle(
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E293B)
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            cursorBrush = SolidColor(Color(0xFF2563EB)),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Max outstanding: ₹ ${formatAmount(currentOutstanding)}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            // Column 2: Payment Date
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = rememberTranslation("Payment Date"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF334155)
                                    )
                                    Text(text = " *", fontSize = 12.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF8FAFC))
                                        .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(8.dp))
                                        .clickable { datePickerDialog.show() }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = formatDisplayDate(uiState.paymentDate),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(text = "", fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Row 2: Payment Method * | Transaction ID / Notes (Optional)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Column 1: Payment Method
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = rememberTranslation("Payment Method"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF334155)
                                    )
                                    Text(text = " *", fontSize = 12.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(8.dp))
                                            .clickable { methodExpanded = true }
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PaymentMethodIcon(mode = uiState.paymentMode)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = uiState.paymentMode,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E293B)
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = methodExpanded,
                                        onDismissRequest = { methodExpanded = false }
                                    ) {
                                        paymentMethods.forEach { method ->
                                            DropdownMenuItem(
                                                leadingIcon = { PaymentMethodIcon(mode = method) },
                                                text = {
                                                    Text(
                                                        text = method,
                                                        fontWeight = if (method == uiState.paymentMode) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (method == uiState.paymentMode) Color(0xFF2563EB) else Color(0xFF1E293B)
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.onPaymentModeChanged(method)
                                                    methodExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Column 2: Transaction ID / Notes (Optional)
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = rememberTranslation("Transaction ID / Notes"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF334155),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "(Optional)",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF8FAFC))
                                        .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        BasicTextField(
                                            value = uiState.transactionReference,
                                            onValueChange = viewModel::onTransactionReferenceChanged,
                                            textStyle = TextStyle(
                                                fontSize = 12.sp,
                                                color = Color(0xFF1E293B)
                                            ),
                                            cursorBrush = SolidColor(Color(0xFF2563EB)),
                                            singleLine = true,
                                            decorationBox = { innerTextField ->
                                                if (uiState.transactionReference.isEmpty()) {
                                                    Text(
                                                        text = rememberTranslation("Enter UPI ID or notes"),
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                                innerTextField()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Payment Screenshot / Receipt (Optional) Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Section Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = rememberTranslation("Payment Screenshot / Receipt"),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(Optional)",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Text(
                                    text = rememberTranslation("Upload payment proof (UPI screenshot, receipt, etc.)"),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Upload proof row: Preview card (if selected) + Add Another / Upload card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (uiState.attachmentUri != null) {
                                // Uploaded Attachment Card
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(72.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF8FAFC))
                                        .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(10.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = uiState.attachmentUri,
                                            contentDescription = "Attachment",
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = uiState.attachmentFileName.ifBlank { "Screenshot_202610..." },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E293B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "1.2 MB • ",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                                Text(
                                                    text = "Uploaded",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF16A34A)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeAttachment() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Add Another / Upload button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF0F7FF))
                                    .border(BorderStroke(1.dp, Color(0xFF93C5FD)), RoundedCornerShape(10.dp))
                                    .clickable {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircleOutline,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (uiState.attachmentUri != null) rememberTranslation("Add Another") else rememberTranslation("Upload Proof"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB)
                                    )
                                    Text(
                                        text = "JPG, PNG, PDF\n(Max 5 MB)",
                                        fontSize = 8.5.sp,
                                        color = Color(0xFF64748B),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Previous Payments Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Header with Clock Icon + "Previous Payments" + "View All"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = rememberTranslation("Previous Payments"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (uiState.pastPayments.isNotEmpty()) {
                                Text(
                                    text = if (showAllHistory) rememberTranslation("Show Less") else rememberTranslation("View All"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2563EB),
                                    modifier = Modifier.clickable { showAllHistory = !showAllHistory }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (uiState.pastPayments.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = rememberTranslation("No previous payments recorded for this tenant"),
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        } else {
                            val displayList = if (showAllHistory) uiState.pastPayments else uiState.pastPayments.take(3)
                            displayList.forEachIndexed { index, payment ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = Color(0xFFF1F5F9),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                PreviousPaymentItemRow(payment = payment, index = index)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Tenant Selection Modal Bottom Sheet
    if (showTenantPicker) {
        ModalBottomSheet(
            onDismissRequest = { showTenantPicker = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            TenantPickerContent(
                tenants = uiState.tenantList,
                selectedTenantId = uiState.tenantId,
                propertyName = uiState.propertyName,
                onSelect = { selectedId ->
                    viewModel.onTenantSelected(selectedId)
                    showTenantPicker = false
                },
                onDismiss = { showTenantPicker = false }
            )
        }
    }
}

@Composable
fun MetricPillCard(
    modifier: Modifier = Modifier,
    label: String,
    amount: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = amount,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PreviousPaymentItemRow(
    payment: RentPaymentEntity,
    index: Int
) {
    val (iconBg, iconTint) = when (index % 3) {
        0 -> Color(0xFFDCFCE7) to Color(0xFF16A34A) // Green
        1 -> Color(0xFFFEF3C7) to Color(0xFFD97706) // Amber
        else -> Color(0xFFFEE2E2) to Color(0xFFDC2626) // Red/Pink
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored circular date icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payment.billingMonth,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(2.dp))
            val dateFormatted = formatDisplayDate(payment.paymentDate ?: "")
            val modeFormatted = payment.paymentMode ?: "UPI"
            Text(
                text = "$dateFormatted • $modeFormatted",
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )
        }

        Text(
            text = "₹ ${formatAmount(payment.amountPaid)}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF16A34A)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = "Receipt",
            tint = Color(0xFF2563EB),
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun PaymentMethodIcon(mode: String) {
    when (mode.uppercase()) {
        "UPI" -> Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = "UPI",
            tint = Color(0xFFEA580C),
            modifier = Modifier.size(16.dp)
        )
        "BANK TRANSFER" -> Icon(
            imageVector = Icons.Default.AccountBalance,
            contentDescription = "Bank Transfer",
            tint = Color(0xFF2563EB),
            modifier = Modifier.size(16.dp)
        )
        "CASH" -> Icon(
            imageVector = Icons.Default.Payments,
            contentDescription = "Cash",
            tint = Color(0xFF16A34A),
            modifier = Modifier.size(16.dp)
        )
        "CARD" -> Icon(
            imageVector = Icons.Default.CreditCard,
            contentDescription = "Card",
            tint = Color(0xFF7C3AED),
            modifier = Modifier.size(16.dp)
        )
        else -> Icon(
            imageVector = Icons.Default.ReceiptLong,
            contentDescription = "Cheque",
            tint = Color(0xFF475569),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun TenantPickerContent(
    tenants: List<TenantEntity>,
    selectedTenantId: Int,
    propertyName: String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredTenants = remember(tenants, searchQuery) {
        if (searchQuery.isBlank()) tenants
        else tenants.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.roomNumber.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rememberTranslation("Select Tenant"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(rememberTranslation("Search by name, room or phone")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B)) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredTenants.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rememberTranslation("No tenants found"),
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTenants) { tenant ->
                    val isSelected = tenant.id == selectedTenantId
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFFEFF6FF) else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF3B82F6) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(tenant.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFFDBEAFE) else Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                val initials = tenant.name
                                    .split(" ")
                                    .filter { it.isNotBlank() }
                                    .mapNotNull { it.firstOrNull()?.toString() }
                                    .take(2)
                                    .joinToString("")
                                    .uppercase()
                                    .ifBlank { "T" }
                                Text(
                                    text = initials,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF475569)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tenant.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "Room ${tenant.roomNumber}${if (tenant.bedId.isNotBlank()) " (${tenant.bedId})" else ""} • ${propertyName.ifBlank { "Sri Lakshmi Residency" }}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                if (tenant.phone.isNotBlank()) {
                                    Text(
                                        text = tenant.phone,
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }
                            Text(
                                text = "₹ ${formatAmount(tenant.monthlyRent)}/mo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatAmount(amount: Double): String {
    return String.format(Locale.US, "%,.0f", amount)
}

fun formatDisplayDate(dateStr: String): String {
    if (dateStr.isBlank()) return "15 Oct 2026"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val d = inputFormat.parse(dateStr)
        if (d != null) outputFormat.format(d) else dateStr
    } catch (_: Exception) {
        dateStr
    }
}
