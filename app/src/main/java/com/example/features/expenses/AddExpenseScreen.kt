package com.example.features.expenses

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.language.GlobalLanguageToggle
import com.example.core.language.rememberTranslation
import com.example.features.expenses.ui.viewmodel.AddExpenseUiEffect
import com.example.features.expenses.ui.viewmodel.AddExpenseUiEvent
import com.example.features.expenses.ui.viewmodel.AddExpenseUiState
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

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showPropertyDialog by remember { mutableStateOf(false) }
    var showRoomDialog by remember { mutableStateOf(false) }
    var showFrequencyDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            viewModel.onEvent(AddExpenseUiEvent.AttachReceiptUri(it, source = "UPLOAD"))
        }
    }

    val tempCameraUri = remember {
        try {
            val tempFile = java.io.File(context.cacheDir, "scan_bill_${System.currentTimeMillis()}.jpg")
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
        } catch (_: Exception) { null }
    }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            viewModel.onEvent(AddExpenseUiEvent.AttachReceiptUri(tempCameraUri, mimeType = "image/jpeg", source = "SCAN"))
        }
    }

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

    if (uiState.isSuccess) {
        // Success Screen (Screenshot 3)
        ExpenseAddedSuccessScreen(
            uiState = uiState,
            onAddAnother = { viewModel.onEvent(AddExpenseUiEvent.ResetSuccess) },
            onViewExpenses = onBackClick,
            onBackClick = onBackClick
        )
    } else {
        // Add Expense Form Screen (Screenshot 1)
        Scaffold(
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = rememberTranslation("Back"),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = rememberTranslation("Add Expense"),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { showPropertyDialog = true }
                                    ) {
                                        Text(
                                            text = uiState.propertyName.ifBlank { "Sri Lakshmi Residency" },
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            GlobalLanguageToggle()
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = rememberTranslation("Back"),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = { viewModel.onEvent(AddExpenseUiEvent.SaveExpense) },
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .weight(2f)
                                .height(50.dp)
                                .testTag("save_expense_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0866FF))
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text(
                                    text = rememberTranslation("Save Expense"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFFF8FAFC),
            modifier = modifier.testTag("add_expense_screen")
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Validation Error Alert
                if (uiState.validationError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
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

                // 1. BASIC INFORMATION CARD
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFE8F0FE), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color(0xFF0866FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = rememberTranslation("Basic Information"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                )
                                Text(
                                    text = rememberTranslation("Enter the main details of this expense"),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                                )
                            }
                        }

                        // Amount & Date Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.amount,
                                onValueChange = { viewModel.onEvent(AddExpenseUiEvent.AmountChanged(it)) },
                                label = { Text("Amount *") },
                                placeholder = { Text("1,200") },
                                prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("expense_amount_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = uiState.date,
                                onValueChange = { viewModel.onEvent(AddExpenseUiEvent.DateChanged(it)) },
                                label = { Text("Date *") },
                                trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF64748B)) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("expense_date_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Category Selector
                        OutlinedCard(
                            onClick = { showCategoryDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("expense_category_selector")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color(0xFFFEF3C7), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = rememberTranslation("Category *"),
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                                        )
                                        Text(
                                            text = rememberTranslation(uiState.category),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B)
                                )
                            }
                        }

                        // Description / Title
                        OutlinedTextField(
                            value = uiState.title,
                            onValueChange = { viewModel.onEvent(AddExpenseUiEvent.TitleChanged(it)) },
                            label = { Text("Description *") },
                            placeholder = { Text("Electricity bill for common area (Sept 2026)") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("expense_title_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${uiState.title.length}/200",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                            )
                        }
                    }
                }

                // 2. PROPERTY & ROOM CARD
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFDCFCE7), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = rememberTranslation("Property & Room"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                )
                                Text(
                                    text = rememberTranslation("Link this expense to a specific property or room (optional)"),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                                )
                            }
                        }

                        // Property Dropdown
                        OutlinedCard(
                            onClick = { showPropertyDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Apartment, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(rememberTranslation("Property"), style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B)))
                                        Text(rememberTranslation(uiState.propertyName), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B)))
                                    }
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B))
                            }
                        }

                        // Room Dropdown
                        OutlinedCard(
                            onClick = { showRoomDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MeetingRoom, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(rememberTranslation("Room (Optional)"), style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B)))
                                        Text(rememberTranslation(uiState.room), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B)))
                                    }
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B))
                            }
                        }
                    }
                }

                // 3. PAYMENT DETAILS CARD
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFFEF3C7), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payment,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = rememberTranslation("Payment Details"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                )
                                Text(
                                    text = rememberTranslation("How was this expense paid?"),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                                )
                            }
                        }

                        // Payment Method
                        Text(
                            text = rememberTranslation("Payment Method"),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("UPI", "Cash", "Bank Transfer", "Other").forEach { method ->
                                val isSelected = uiState.paymentMethod == method
                                Surface(
                                    onClick = { viewModel.onEvent(AddExpenseUiEvent.PaymentMethodChanged(method)) },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) Color(0xFF0866FF) else Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF0866FF) else Color(0xFFE2E8F0)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = rememberTranslation(method),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) Color.White else Color(0xFF475569)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Payment Status
                        Text(
                            text = rememberTranslation("Payment Status"),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Paid", "Pending", "Partially Paid").forEach { status ->
                                val isSelected = uiState.paymentStatus == status
                                val bgCol = when {
                                    isSelected && status == "Paid" -> Color(0xFF0866FF)
                                    isSelected && status == "Pending" -> Color(0xFFD97706)
                                    isSelected -> Color(0xFF64748B)
                                    else -> Color(0xFFF1F5F9)
                                }
                                Surface(
                                    onClick = { viewModel.onEvent(AddExpenseUiEvent.PaymentStatusChanged(status)) },
                                    shape = RoundedCornerShape(20.dp),
                                    color = bgCol,
                                    border = BorderStroke(1.dp, if (isSelected) Color.Transparent else Color(0xFFE2E8F0)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = rememberTranslation(status),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) Color.White else Color(0xFF475569)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Reference / Transaction ID
                        OutlinedTextField(
                            value = uiState.paymentReference,
                            onValueChange = { viewModel.onEvent(AddExpenseUiEvent.PaymentReferenceChanged(it)) },
                            label = { Text("Reference / Transaction ID (Optional)") },
                            placeholder = { Text("Enter UPI ID / transaction number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // 4. RECURRING EXPENSE CARD
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFFF3E8FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = null,
                                        tint = Color(0xFF7C3AED),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = rememberTranslation("Recurring Expense"),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    )
                                    Text(
                                        text = rememberTranslation("Set if this is a recurring expense"),
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.isRecurring,
                                onCheckedChange = { viewModel.onEvent(AddExpenseUiEvent.RecurringToggled(it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0866FF))
                            )
                        }

                        AnimatedVisibility(visible = uiState.isRecurring) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFE8F0FE),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF0866FF), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = rememberTranslation("This will create automatic reminder for this expense."),
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF1E3A8A), fontWeight = FontWeight.Medium)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedCard(
                                        onClick = { showFrequencyDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("Repeat Every", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B)))
                                                Text(uiState.recurringFrequency, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B)))
                                            }
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B))
                                        }
                                    }

                                    OutlinedTextField(
                                        value = uiState.recurringStartDate.ifBlank { uiState.date },
                                        onValueChange = { viewModel.onEvent(AddExpenseUiEvent.RecurringEndDateChanged(it)) },
                                        label = { Text("Start Date") },
                                        trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Text(
                                    text = rememberTranslation("End Type"),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("No end date", "End on date", "End after a number of times").forEach { type ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.onEvent(AddExpenseUiEvent.EndTypeChanged(type)) }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = uiState.endType == type,
                                                onClick = { viewModel.onEvent(AddExpenseUiEvent.EndTypeChanged(type)) }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = rememberTranslation(type),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. UPLOAD RECEIPT CARD
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().testTag("add_expense_receipt_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFE0F2FE), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = rememberTranslation("Upload Receipt or Bill"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                )
                                Text(
                                    text = rememberTranslation("Attach bill image or PDF (saved to Google Drive)"),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                                )
                            }
                        }

                        if (uiState.isPreparingAttachment) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Preparing attachment...", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else if (uiState.selectedAttachment != null) {
                            val att = uiState.selectedAttachment!!
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF0284C7)
                                            ) {
                                                Text(
                                                    text = att.source,
                                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = att.fileName,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF334155)),
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${if (att.fileSize > 0) "${att.fileSize / 1024} KB" else "Attached file"} • Will upload to Google Drive",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B))
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.onEvent(AddExpenseUiEvent.RemoveReceipt) },
                                        modifier = Modifier.size(24.dp).testTag("remove_receipt_button")
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove receipt", tint = Color(0xFF64748B))
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Upload file button
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(84.dp)
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                        .border(BorderStroke(1.dp, Color(0xFF93C5FD)), RoundedCornerShape(12.dp))
                                        .clickable { filePickerLauncher.launch("*/*") }
                                        .testTag("upload_receipt_action"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0866FF), modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = rememberTranslation("Upload Receipt"),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF0866FF))
                                        )
                                        Text(
                                            text = "JPG, PNG, PDF",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = Color(0xFF94A3B8))
                                        )
                                    }
                                }

                                // Scan bill camera button
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(84.dp)
                                        .background(Color(0xFFF3E8FF), RoundedCornerShape(12.dp))
                                        .border(BorderStroke(1.dp, Color(0xFFC084FC)), RoundedCornerShape(12.dp))
                                        .clickable {
                                            tempCameraUri?.let { cameraLauncher.launch(it) }
                                        }
                                        .testTag("scan_bill_action"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = rememberTranslation("Scan Bill"),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                                        )
                                        Text(
                                            text = "Take Photo",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = Color(0xFF9333EA))
                                        )
                                    }
                                }
                            }
                        }

                        if (!uiState.receiptUploadError.isNullOrBlank()) {
                            Text(
                                text = uiState.receiptUploadError!!,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                            )
                        }
                    }
                }

                // 6. ADDITIONAL NOTES CARD
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFFEF3C7), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = rememberTranslation("Additional Notes"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                )
                                Text(
                                    text = rememberTranslation("Add any additional information (optional)"),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                                )
                            }
                        }

                        OutlinedTextField(
                            value = uiState.notes,
                            onValueChange = { viewModel.onEvent(AddExpenseUiEvent.NotesChanged(it)) },
                            placeholder = { Text("e.g. common area, paid online, bill period, etc.") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${uiState.notes.length}/200",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Select Category Dialog (Screenshot 2)
    if (showCategoryDialog) {
        CategorySelectionDialog(
            selectedCategory = uiState.category,
            onCategorySelected = { cat ->
                viewModel.onEvent(AddExpenseUiEvent.CategoryChanged(cat))
                showCategoryDialog = false
            },
            onDismiss = { showCategoryDialog = false }
        )
    }

    // Select Property Dialog
    if (showPropertyDialog) {
        SelectionDialog(
            title = "Select Property",
            items = listOf("Sri Lakshmi Residency", "Sai Paradise PG", "Greenwood Mens PG", "Royal Executive Stay"),
            selectedItem = uiState.propertyName,
            onItemSelected = { prop ->
                viewModel.onEvent(AddExpenseUiEvent.PropertyChanged(prop))
                showPropertyDialog = false
            },
            onDismiss = { showPropertyDialog = false }
        )
    }

    // Select Room Dialog
    if (showRoomDialog) {
        SelectionDialog(
            title = "Select Room",
            items = listOf("Select room", "Room 101", "Room 102", "Room 103", "Room 201", "Room 202", "Common Area", "All Rooms"),
            selectedItem = uiState.room,
            onItemSelected = { rm ->
                viewModel.onEvent(AddExpenseUiEvent.RoomChanged(rm))
                showRoomDialog = false
            },
            onDismiss = { showRoomDialog = false }
        )
    }

    // Select Frequency Dialog
    if (showFrequencyDialog) {
        SelectionDialog(
            title = "Repeat Frequency",
            items = listOf("Weekly", "Monthly", "Quarterly", "Yearly"),
            selectedItem = uiState.recurringFrequency,
            onItemSelected = { freq ->
                viewModel.onEvent(AddExpenseUiEvent.RecurringFrequencyChanged(freq))
                showFrequencyDialog = false
            },
            onDismiss = { showFrequencyDialog = false }
        )
    }
}

// ==========================================
// GENERIC SELECTION DIALOG
// ==========================================
@Composable
fun SelectionDialog(
    title: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFF1F5F9), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E293B))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = rememberTranslation(title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { item ->
                        val isSelected = selectedItem == item
                        Surface(
                            onClick = { onItemSelected(item) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFEFF6FF) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = rememberTranslation(item),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF0866FF) else Color(0xFF1E293B)
                                    )
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF0866FF),
                                        modifier = Modifier.size(24.dp)
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

// ==========================================
// CATEGORY SELECTION DIALOG (Screenshot 2)
// ==========================================
@Composable
fun CategorySelectionDialog(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf(
        Triple("Electricity Bill", Icons.Default.Bolt, Color(0xFFFEF3C7)),
        Triple("Water Bill", Icons.Default.WaterDrop, Color(0xFFE0F2FE)),
        Triple("WiFi / Internet", Icons.Default.Wifi, Color(0xFFDCFCE7)),
        Triple("Gas", Icons.Default.LocalFireDepartment, Color(0xFFFEE2E2)),
        Triple("Maintenance", Icons.Default.Build, Color(0xFFF3E8FF)),
        Triple("Repairs & Plumbing", Icons.Default.HomeRepairService, Color(0xFFFFEDD5)),
        Triple("Furniture & Appliances", Icons.Default.Chair, Color(0xFFD1FAE5)),
        Triple("Security", Icons.Default.Security, Color(0xFFDBEAFE)),
        Triple("Property Tax", Icons.Default.ReceiptLong, Color(0xFFFCE7F3)),
        Triple("Housekeeping", Icons.Default.CleaningServices, Color(0xFFCCFBF1)),
        Triple("Groceries", Icons.Default.ShoppingCart, Color(0xFFFEF08A))
    )

    val filtered = categories.filter { it.first.contains(searchQuery, ignoreCase = true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFF1F5F9), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E293B))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = rememberTranslation("Select Category"),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search categories") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    )
                )

                Text(
                    text = rememberTranslation("Common Categories"),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                )

                // List of Categories
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filtered.forEach { (catName, icon, bgCol) ->
                        val isSelected = selectedCategory == catName
                        Surface(
                            onClick = { onCategorySelected(catName) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFEFF6FF) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(bgCol, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(icon, contentDescription = null, tint = Color(0xFF1E293B), modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = rememberTranslation(catName),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color(0xFF0866FF) else Color(0xFF1E293B)
                                        )
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF0866FF),
                                        modifier = Modifier.size(24.dp)
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

// ==========================================
// EXPENSE ADDED SUCCESS SCREEN (Screenshot 3)
// ==========================================
@Composable
fun ExpenseAddedSuccessScreen(
    uiState: AddExpenseUiState,
    onAddAnother: () -> Unit,
    onViewExpenses: () -> Unit,
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            Surface(
                color = Color(0xFFF8FAFC),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp).background(Color(0xFFE2E8F0).copy(alpha = 0.5f), CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E293B))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = rememberTranslation("Expense Added"),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAddAnother,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("add_another_expense_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0866FF))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = rememberTranslation("Add Another Expense"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }

                    TextButton(
                        onClick = onViewExpenses,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("view_expenses_button")
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = null, tint = Color(0xFF0866FF), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = rememberTranslation("View Expenses"),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF0866FF))
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Success Header Icon & Celebration
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF10B981), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = rememberTranslation("Expense Added Successfully!"),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rememberTranslation("The expense has been added to your records."),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF64748B))
                )
            }

            // Card 1: Expense Details Summary
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(28.dp).background(Color(0xFFE8F0FE), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = Color(0xFF0866FF), modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = rememberTranslation("Expense Details"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            )
                        }
                        TextButton(onClick = { }) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF0866FF), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF0866FF)))
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    SuccessDetailRow("Amount", "₹ ${uiState.amount.ifBlank { "0" }}", isAmount = true)
                    SuccessDetailRow("Date", uiState.date)
                    SuccessDetailRow("Category", uiState.category)
                    SuccessDetailRow("Property", uiState.propertyName)
                    SuccessDetailRow("Payment Method", uiState.paymentMethod)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Status", style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF64748B)))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = uiState.paymentStatus,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                            )
                        }
                    }

                    if (uiState.title.isNotBlank()) {
                        SuccessDetailRow("Description", uiState.title)
                    }
                }
            }

            // Card 2: Recurring Expense Summary (if recurring)
            if (uiState.isRecurring) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(28.dp).background(Color(0xFFF3E8FF), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Repeat, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = rememberTranslation("Recurring Expense"),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                )
                            }
                            TextButton(onClick = { }) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF0866FF), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF0866FF)))
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        SuccessDetailRow("Repeat", uiState.recurringFrequency)
                        SuccessDetailRow("Start Date", uiState.recurringStartDate.ifBlank { uiState.date })
                        SuccessDetailRow("End", uiState.endType)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEFF6FF),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF0866FF), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = rememberTranslation("You will get a reminder every ${uiState.recurringFrequency.lowercase()} for this expense."),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF1E40AF), fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SuccessDetailRow(label: String, value: String, isAmount: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rememberTranslation(label),
            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF64748B))
        )
        Text(
            text = rememberTranslation(value),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = if (isAmount) Color(0xFF0866FF) else Color(0xFF1E293B)
            )
        )
    }
}
