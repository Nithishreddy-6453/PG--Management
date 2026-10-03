package com.example.features.expenses

import android.widget.Toast
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
import com.example.core.language.GlobalLanguageToggle
import com.example.core.language.rememberTranslation
import com.example.features.expenses.ui.viewmodel.EditExpenseUiEffect
import com.example.features.expenses.ui.viewmodel.EditExpenseUiEvent
import com.example.features.expenses.ui.viewmodel.EditExpenseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseScreen(
    viewModel: EditExpenseViewModel,
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

    LaunchedEffect(key1 = true) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditExpenseUiEffect.NavigateBack -> onBackClick()
                is EditExpenseUiEffect.ShowMessage -> {
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
                        text = rememberTranslation("Edit"),
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
                modifier = Modifier.testTag("edit_expense_top_bar")
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("edit_expense_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isFetching) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.fetchError != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.fetchError ?: "Failed to load details",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackClick) {
                        Text(rememberTranslation("Back"))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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

                    // Title
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = { viewModel.onEvent(EditExpenseUiEvent.TitleChanged(it)) },
                        label = { Text("Expense Title *") },
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
                                            viewModel.onEvent(EditExpenseUiEvent.CategoryChanged(cat))
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Amount
                    OutlinedTextField(
                        value = uiState.amount,
                        onValueChange = { viewModel.onEvent(EditExpenseUiEvent.AmountChanged(it)) },
                        label = { Text("${rememberTranslation("Expected Amount")} (₹) *") },
                        prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("expense_amount_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Expense Date
                    OutlinedTextField(
                        value = uiState.date,
                        onValueChange = { viewModel.onEvent(EditExpenseUiEvent.DateChanged(it)) },
                        label = { Text("${rememberTranslation("Expense Date")} (YYYY-MM-DD) *") },
                        trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("expense_date_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Payment Method Dropdown
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
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = paymentMethodExpanded,
                                onDismissRequest = { paymentMethodExpanded = false }
                            ) {
                                paymentMethods.forEach { method ->
                                    DropdownMenuItem(
                                        text = { Text(rememberTranslation(method)) },
                                        onClick = {
                                            viewModel.onEvent(EditExpenseUiEvent.PaymentMethodChanged(method))
                                            paymentMethodExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Vendor
                    OutlinedTextField(
                        value = uiState.vendor,
                        onValueChange = { viewModel.onEvent(EditExpenseUiEvent.VendorChanged(it)) },
                        label = { Text("${rememberTranslation("Vendor")} / ${rememberTranslation("Payee")}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("expense_vendor_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Notes
                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = { viewModel.onEvent(EditExpenseUiEvent.NotesChanged(it)) },
                        label = { Text(rememberTranslation("Notes")) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("expense_notes_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.onEvent(EditExpenseUiEvent.SaveExpense) },
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
    }
}
