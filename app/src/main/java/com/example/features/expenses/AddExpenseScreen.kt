package com.example.features.expenses

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.language.GlobalLanguageToggle
import com.example.core.language.rememberTranslation
import com.example.features.expenses.ui.viewmodel.AddExpenseUiEffect
import com.example.features.expenses.ui.viewmodel.AddExpenseUiEvent
import com.example.features.expenses.ui.viewmodel.AddExpenseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    viewModel: AddExpenseViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val categories = listOf(
        "Electricity", "Water", "Internet", "Maintenance", "Cleaning",
        "Repairs", "Staff Salary", "Food", "Furniture", "Security",
        "Property Tax", "Rent/Lease", "Appliances", "Utilities", "Other"
    )
    val paymentMethods = listOf("UPI", "Cash", "Bank Transfer", "Card")

    var categoryExpanded by remember { mutableStateOf(false) }
    var paymentMethodExpanded by remember { mutableStateOf(false) }
    var frequencyExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AddExpenseUiEffect.NavigateBack -> onBackClick()
                is AddExpenseUiEffect.ShowMessage -> {
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
                        text = rememberTranslation("Add Expense"),
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
                    GlobalLanguageToggle(modifier = Modifier.padding(end = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("add_expense_top_bar")
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("add_expense_screen_container")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Validation Error Alert
            if (uiState.validationError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("validation_error_box")
                ) {
                    Text(
                        text = uiState.validationError ?: "Validation Error",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // 1. EXPENSE TYPE SELECTOR: One-time vs Recurring
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Expense Type",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // One-time Option
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (!uiState.isRecurring) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (!uiState.isRecurring) MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.onEvent(AddExpenseUiEvent.RecurringToggled(false)) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !uiState.isRecurring,
                                    onClick = { viewModel.onEvent(AddExpenseUiEvent.RecurringToggled(false)) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = rememberTranslation("One-time"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Recurring Option
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (uiState.isRecurring) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (uiState.isRecurring) MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.onEvent(AddExpenseUiEvent.RecurringToggled(true)) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.isRecurring,
                                    onClick = { viewModel.onEvent(AddExpenseUiEvent.RecurringToggled(true)) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = rememberTranslation("Recurring"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // 2. EXPENSE CORE DETAILS
            // Title
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.onEvent(AddExpenseUiEvent.TitleChanged(it)) },
                label = { Text("Expense Title *") },
                placeholder = { Text("e.g. Electricity Bill, Internet Subscription") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("expense_title_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Category Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = rememberTranslation(uiState.category),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("${rememberTranslation("Category")} *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("expense_category_dropdown"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(rememberTranslation(cat)) },
                                onClick = {
                                    viewModel.onEvent(AddExpenseUiEvent.CategoryChanged(cat))
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Amount Input
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = { viewModel.onEvent(AddExpenseUiEvent.AmountChanged(it)) },
                label = { Text("${rememberTranslation("Expected Amount")} (₹) *") },
                placeholder = { Text("e.g. 5000") },
                prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("expense_amount_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Expense Date
            OutlinedTextField(
                value = uiState.date,
                onValueChange = { viewModel.onEvent(AddExpenseUiEvent.DateChanged(it)) },
                label = { Text("${rememberTranslation("Expense Date")} (YYYY-MM-DD) *") },
                trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("expense_date_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // 3. RECURRING CONFIGURATION (When Recurring is selected)
            if (uiState.isRecurring) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Recurring Configuration",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        // Frequency Dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            ExposedDropdownMenuBox(
                                expanded = frequencyExpanded,
                                onExpandedChange = { frequencyExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = rememberTranslation(uiState.recurringFrequency),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(rememberTranslation("Frequency")) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = frequencyExpanded,
                                    onDismissRequest = { frequencyExpanded = false }
                                ) {
                                    listOf("Monthly", "Yearly").forEach { freq ->
                                        DropdownMenuItem(
                                            text = { Text(rememberTranslation(freq)) },
                                            onClick = {
                                                viewModel.onEvent(AddExpenseUiEvent.RecurringFrequencyChanged(freq))
                                                frequencyExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Optional End Date
                        OutlinedTextField(
                            value = uiState.recurringEndDate,
                            onValueChange = { viewModel.onEvent(AddExpenseUiEvent.RecurringEndDateChanged(it)) },
                            label = { Text("End Date (Optional, YYYY-MM-DD)") },
                            placeholder = { Text("Leave blank for indefinite") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // 4. PAYMENT STATUS & SETTLEMENT (For One-time expenses)
            if (!uiState.isRecurring) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Payment Status",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Paid Option
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (uiState.paymentStatus == "Paid") Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, if (uiState.paymentStatus == "Paid") Color(0xFF16A34A) else Color.Transparent),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.onEvent(AddExpenseUiEvent.PaymentStatusChanged("Paid")) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = uiState.paymentStatus == "Paid",
                                        onClick = { viewModel.onEvent(AddExpenseUiEvent.PaymentStatusChanged("Paid")) }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = rememberTranslation("Paid"),
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.paymentStatus == "Paid") Color(0xFF166534) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Unpaid Option
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (uiState.paymentStatus == "Unpaid") Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, if (uiState.paymentStatus == "Unpaid") Color(0xFFDC2626) else Color.Transparent),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.onEvent(AddExpenseUiEvent.PaymentStatusChanged("Unpaid")) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = uiState.paymentStatus == "Unpaid",
                                        onClick = { viewModel.onEvent(AddExpenseUiEvent.PaymentStatusChanged("Unpaid")) }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = rememberTranslation("Unpaid"),
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.paymentStatus == "Unpaid") Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // If Paid, show payment details
                        if (uiState.paymentStatus == "Paid") {
                            // Payment Date
                            OutlinedTextField(
                                value = uiState.paymentDate,
                                onValueChange = { viewModel.onEvent(AddExpenseUiEvent.PaymentDateChanged(it)) },
                                label = { Text(rememberTranslation("Payment Date")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            // Payment Method
                            Box(modifier = Modifier.fillMaxWidth()) {
                                ExposedDropdownMenuBox(
                                    expanded = paymentMethodExpanded,
                                    onExpandedChange = { paymentMethodExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = rememberTranslation(uiState.paymentMethod),
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(rememberTranslation("Payment Method")) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentMethodExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = paymentMethodExpanded,
                                        onDismissRequest = { paymentMethodExpanded = false }
                                    ) {
                                        paymentMethods.forEach { method ->
                                            DropdownMenuItem(
                                                text = { Text(rememberTranslation(method)) },
                                                onClick = {
                                                    viewModel.onEvent(AddExpenseUiEvent.PaymentMethodChanged(method))
                                                    paymentMethodExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Payment Reference / Txn ID
                            OutlinedTextField(
                                value = uiState.paymentReference,
                                onValueChange = { viewModel.onEvent(AddExpenseUiEvent.PaymentReferenceChanged(it)) },
                                label = { Text("${rememberTranslation("Reference")} / Txn ID") },
                                placeholder = { Text("e.g. UPI Ref # or Check #") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            // 5. VENDOR & NOTES
            OutlinedTextField(
                value = uiState.vendor,
                onValueChange = { viewModel.onEvent(AddExpenseUiEvent.VendorChanged(it)) },
                label = { Text("${rememberTranslation("Vendor")} / ${rememberTranslation("Payee")}") },
                placeholder = { Text("e.g. BESCOM, Kent RO, Amazon") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("expense_vendor_input"),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.onEvent(AddExpenseUiEvent.NotesChanged(it)) },
                label = { Text(rememberTranslation("Notes")) },
                placeholder = { Text("Additional information or remarks") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("expense_notes_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Save Expense Button
            Button(
                onClick = { viewModel.onEvent(AddExpenseUiEvent.SaveExpense) },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_expense_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = rememberTranslation("Save"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
