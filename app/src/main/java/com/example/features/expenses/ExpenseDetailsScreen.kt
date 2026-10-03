package com.example.features.expenses

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.language.GlobalLanguageToggle
import com.example.core.language.rememberTranslation
import com.example.features.expenses.domain.model.ExpensePayment
import com.example.features.expenses.ui.viewmodel.ExpenseDetailsUiEffect
import com.example.features.expenses.ui.viewmodel.ExpenseDetailsUiEvent
import com.example.features.expenses.ui.viewmodel.ExpenseDetailsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailsScreen(
    viewModel: ExpenseDetailsViewModel,
    onBackClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ExpenseDetailsUiEffect.NavigateBack -> onBackClick()
                is ExpenseDetailsUiEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = rememberTranslation("Expense Details"),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = rememberTranslation("Back")
                        )
                    }
                },
                actions = {
                    GlobalLanguageToggle(modifier = Modifier.padding(end = 4.dp))
                    uiState.expense?.let { expense ->
                        IconButton(
                            onClick = { onEditClick(expense.id) },
                            modifier = Modifier.testTag("edit_expense_button")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Expense")
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("delete_expense_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Expense",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("expense_details_top_bar")
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("expense_details_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "An error occurred",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            } else {
                uiState.expense?.let { expense ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Hero Card: Category Icon, Title, Status, Total Expected Amount
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(getCategoryBgColor(expense.category)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getExpenseCategoryIcon(expense.category),
                                        contentDescription = null,
                                        tint = getCategoryIconColor(expense.category),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = expense.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = rememberTranslation(expense.category),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    if (expense.isRecurring) {
                                        Text(
                                            text = " • ${rememberTranslation("Recurring")} (${rememberTranslation(expense.recurringFrequency ?: "Monthly")})",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "₹${String.format(Locale.getDefault(), "%,.0f", expense.amount)}",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                ExpenseStatusBadge(status = expense.status)
                            }
                        }

                        // 2. Financial Settlement Breakdown: Expected, Paid, Remaining
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = rememberTranslation("Expected Amount"),
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        text = "₹${String.format(Locale.getDefault(), "%,.0f", expense.amount)}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = rememberTranslation("Paid Amount"),
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF166534))
                                    )
                                    Text(
                                        text = "₹${String.format(Locale.getDefault(), "%,.0f", expense.paidAmount)}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D)
                                        )
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = rememberTranslation("Remaining Amount"),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (expense.remainingAmount > 0) Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Text(
                                        text = "₹${String.format(Locale.getDefault(), "%,.0f", expense.remainingAmount)}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (expense.remainingAmount > 0) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }

                        // 3. Record Payment Action Button (If Not Fully Paid)
                        if (!expense.isFullyPaid) {
                            Button(
                                onClick = { showRecordPaymentDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = rememberTranslation("Record Payment"),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 4. Detailed Meta Information
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DetailRow(label = rememberTranslation("Expense Date"), value = expense.date)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                DetailRow(
                                    label = rememberTranslation("Vendor"),
                                    value = expense.vendor?.ifBlank { "—" } ?: "—"
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                DetailRow(
                                    label = rememberTranslation("Payment Method"),
                                    value = rememberTranslation(expense.paymentMethod)
                                )
                                if (expense.notes.isNotBlank()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    DetailRow(label = rememberTranslation("Notes"), value = expense.notes)
                                }
                            }
                        }

                        // 5. Payment Transactions History (Supports Multiple Partial Payments)
                        Text(
                            text = rememberTranslation("Payment History"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        if (expense.payments.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No payments recorded yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                expense.payments.forEachIndexed { index, payment ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Payment #${index + 1}",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = "${payment.paymentDate} • ${rememberTranslation(payment.paymentMethod)}",
                                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                )
                                                if (payment.reference.isNotBlank()) {
                                                    Text(
                                                        text = "Ref: ${payment.reference}",
                                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "₹${String.format(Locale.getDefault(), "%,.0f", payment.amount)}",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFF15803D)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Record Payment Dialog
    if (showRecordPaymentDialog) {
        val expense = uiState.expense
        if (expense != null) {
            RecordPaymentDialog(
                defaultAmount = expense.remainingAmount,
                onConfirm = { amount, date, method, ref, notes ->
                    viewModel.onEvent(
                        ExpenseDetailsUiEvent.RecordPayment(
                            amount = amount,
                            paymentDate = date,
                            paymentMethod = method,
                            reference = ref,
                            notes = notes
                        )
                    )
                    showRecordPaymentDialog = false
                },
                onDismiss = { showRecordPaymentDialog = false }
            )
        }
    }

    // Delete Confirmation Dialog
    com.example.core.designsystem.PgConfirmDialog(
        isOpen = showDeleteConfirm,
        title = rememberTranslation("Delete Expense?"),
        message = rememberTranslation("Are you sure you want to delete this expense record? This will remove the transaction from monthly expense totals."),
        confirmText = rememberTranslation("Delete"),
        isDestructive = true,
        onConfirm = {
            showDeleteConfirm = false
            viewModel.onEvent(ExpenseDetailsUiEvent.DeleteExpense)
        },
        onDismiss = { showDeleteConfirm = false }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPaymentDialog(
    defaultAmount: Double,
    onConfirm: (Double, String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    var amountText by remember { mutableStateOf(if (defaultAmount > 0) String.format(Locale.US, "%.0f", defaultAmount) else "") }
    var paymentDate by remember { mutableStateOf(todayStr) }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var methodExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val methods = listOf("UPI", "Cash", "Bank Transfer", "Card")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = rememberTranslation("Record Payment"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorText != null) {
                    Text(text = errorText!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("${rememberTranslation("Paid Amount")} (₹)") },
                    prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Date
                OutlinedTextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it },
                    label = { Text(rememberTranslation("Payment Date")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Method
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = methodExpanded,
                        onExpandedChange = { methodExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = rememberTranslation(paymentMethod),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(rememberTranslation("Payment Method")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = methodExpanded,
                            onDismissRequest = { methodExpanded = false }
                        ) {
                            methods.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(rememberTranslation(m)) },
                                    onClick = {
                                        paymentMethod = m
                                        methodExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Reference
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("${rememberTranslation("Reference")} / Txn ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(rememberTranslation("Notes")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amountText.toDoubleOrNull()
                    if (parsed == null || parsed <= 0) {
                        errorText = "Please enter a valid amount"
                        return@Button
                    }
                    onConfirm(parsed, paymentDate, paymentMethod, reference, notes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
            ) {
                Text(rememberTranslation("Confirm"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(rememberTranslation("Cancel"))
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}
