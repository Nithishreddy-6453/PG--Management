package com.example.features.rent.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SingleBed
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.language.GlobalLanguageToggle
import com.example.features.rent.domain.util.RentReceiptPdfGenerator
import com.example.features.rent.ui.viewmodel.PaymentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ReceiptBlue = Color(0xFF0866F5)
private val ReceiptNavy = Color(0xFF10234F)
private val ReceiptGreen = Color(0xFF0A9B55)
private val ReceiptCardShape = RoundedCornerShape(24.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(
    onNavigateBack: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val displayReceiptNumber = "REC-${uiState.paymentId ?: "N/A"}"
    val displayTenantName = uiState.tenantName.ifBlank { "Not provided" }
    val displayPropertyName = uiState.propertyName.ifBlank { "Not provided" }
    val displayRoomNumber = uiState.roomNumber.ifBlank { "—" }
    val displayBillingMonth = uiState.billingMonth.ifBlank { "—" }
    val displayAmount = formatRupees(uiState.amountPaid)
    val displayPaymentDate = uiState.paymentDate.ifBlank { "—" }
    val displayPaymentMode = uiState.paymentMode.ifBlank { "—" }
    val displayTxnRef = uiState.transactionReference

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val outputStream = context.contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        outputStream.use { stream ->
                            RentReceiptPdfGenerator.generateReceiptPdf(
                                outputStream = stream,
                                receiptNumber = displayReceiptNumber,
                                tenantName = displayTenantName,
                                propertyName = displayPropertyName,
                                roomNumber = displayRoomNumber,
                                billingMonth = displayBillingMonth,
                                amount = displayAmount,
                                paymentDate = displayPaymentDate,
                                paymentMode = displayPaymentMode,
                                transactionReference = displayTxnRef
                            )
                        }
                        withContext(Dispatchers.Main) {
                            snackbarHostState.showSnackbar("Receipt PDF saved successfully.")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            snackbarHostState.showSnackbar("Unable to open file for writing.")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Failed to save PDF: ${e.message ?: "Unknown error"}")
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Rent Receipt",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = ReceiptNavy
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ReceiptNavy
                        )
                    }
                },
                actions = {
                    GlobalLanguageToggle(
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ReceiptBlue)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ReceiptPaper(
                    receiptNumber = displayReceiptNumber,
                    tenantName = displayTenantName,
                    propertyName = displayPropertyName,
                    roomNumber = displayRoomNumber,
                    billingMonth = displayBillingMonth,
                    amount = displayAmount,
                    paymentDate = displayPaymentDate,
                    paymentMode = displayPaymentMode,
                    transactionReference = displayTxnRef
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val receiptText = buildReceiptText(
                                receiptNumber = displayReceiptNumber,
                                tenantName = displayTenantName,
                                propertyName = displayPropertyName,
                                roomNumber = displayRoomNumber,
                                billingMonth = displayBillingMonth,
                                amount = displayAmount,
                                paymentDate = displayPaymentDate,
                                paymentMode = displayPaymentMode,
                                transactionReference = displayTxnRef
                            )
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "PG Manager Rent Receipt - $displayReceiptNumber")
                                putExtra(Intent.EXTRA_TEXT, receiptText)
                            }
                            context.startActivity(
                                Intent.createChooser(sendIntent, "Share rent receipt")
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 58.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share Receipt",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val sanitizedTenant = displayTenantName.replace(Regex("[^a-zA-Z0-9]"), "_").trim('_').ifBlank { "Tenant" }
                            val sanitizedMonth = displayBillingMonth.replace(Regex("[^a-zA-Z0-9]"), "_").trim('_').ifBlank { "Rent" }
                            val defaultFileName = "Receipt_${uiState.paymentId ?: "N_A"}_${sanitizedTenant}_$sanitizedMonth.pdf"
                            pdfLauncher.launch(defaultFileName)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 58.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download PDF",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ReceiptPaper(
    receiptNumber: String,
    tenantName: String,
    propertyName: String,
    roomNumber: String,
    billingMonth: String,
    amount: String,
    paymentDate: String,
    paymentMode: String,
    transactionReference: String
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colors.outlineVariant.copy(alpha = 0.55f),
                shape = ReceiptCardShape
            ),
        shape = ReceiptCardShape,
        color = colors.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ReceiptBlue.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Apartment,
                        contentDescription = null,
                        tint = ReceiptBlue,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "PG MANAGER",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = ReceiptNavy
                    )
                    Text(
                        text = "Simpler Property Management",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = colors.outlineVariant.copy(alpha = 0.7f),
                thickness = 1.dp
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "RENT PAYMENT RECEIPT",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = ReceiptNavy,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE1F7EB)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TaskAlt,
                            contentDescription = null,
                            tint = ReceiptGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "PAYMENT RECORDED",
                            style = MaterialTheme.typography.labelLarge,
                            color = ReceiptGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ReceiptBlue.copy(alpha = 0.07f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Receipt Number",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = receiptNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ReceiptBlue
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                ReceiptDetailRow(Icons.Default.Person, "Tenant Name", tenantName)
                ReceiptDetailRow(
                    Icons.Default.Apartment,
                    "Property",
                    propertyName,
                    mutedValue = propertyName == "Not provided" || propertyName == "—"
                )
                ReceiptDetailRow(Icons.Default.SingleBed, "Room Number", roomNumber)
                ReceiptDetailRow(Icons.Default.CalendarMonth, "Billing Month", billingMonth)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ReceiptBlue.copy(alpha = 0.08f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(ReceiptBlue.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = ReceiptBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Amount Received",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = amount,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = ReceiptBlue
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                ReceiptDetailRow(Icons.Default.CalendarMonth, "Payment Date", paymentDate)
                ReceiptDetailRow(Icons.Default.CreditCard, "Payment Method", paymentMode)
                ReceiptDetailRow(
                    Icons.Default.ReceiptLong,
                    "Transaction Reference",
                    transactionReference.ifBlank { "Not provided" },
                    mutedValue = transactionReference.isBlank()
                )
            }

            HorizontalDivider(
                color = colors.outlineVariant.copy(alpha = 0.7f),
                thickness = 1.dp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Celebration,
                    contentDescription = null,
                    tint = ReceiptBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Thank you for your payment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReceiptNavy,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ReceiptDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    mutedValue: Boolean = false
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(23.dp)
        )

        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )

        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (mutedValue) colors.onSurfaceVariant.copy(alpha = 0.7f) else ReceiptNavy,
            textAlign = TextAlign.End
        )
    }

    HorizontalDivider(
        color = colors.outlineVariant.copy(alpha = 0.5f),
        thickness = 1.dp
    )
}

private fun formatRupees(value: Any?): String {
    val raw = value?.toString()?.trim().orEmpty()
    if (raw.isBlank()) return "₹0.00"
    val amount = raw.toDoubleOrNull()
    return if (amount != null) {
        "₹%,.2f".format(java.util.Locale("en", "IN"), amount)
    } else {
        if (raw.startsWith("₹")) raw else "₹$raw"
    }
}

private fun buildReceiptText(
    receiptNumber: String,
    tenantName: String,
    propertyName: String,
    roomNumber: String,
    billingMonth: String,
    amount: String,
    paymentDate: String,
    paymentMode: String,
    transactionReference: String
): String = """
    PG MANAGER
    RENT PAYMENT RECEIPT

    Receipt Number: $receiptNumber
    Tenant Name: $tenantName
    Property: $propertyName
    Room Number: $roomNumber
    Billing Month: $billingMonth
    Amount Received: $amount
    Payment Date: $paymentDate
    Payment Method: $paymentMode
    Transaction Reference: ${transactionReference.ifBlank { "Not provided" }}

    Thank you for your payment.
""".trimIndent()
