package com.example.features.excel.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.designsystem.LocalSpacing
import com.example.features.excel.domain.model.DuplicateAction
import com.example.features.excel.domain.model.TenantImportRow
import com.example.features.excel.ui.viewmodel.ExcelImportViewModel
import com.example.features.excel.ui.viewmodel.ImportUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelImportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTenants: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExcelImportViewModel = hiltViewModel()
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.shareIntentEvent.collect { intent ->
            context.startActivity(intent)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is ImportUiState.Error) {
            snackbarHostState.showSnackbar((uiState as ImportUiState.Error).message)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "imported_tenants.xlsx"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }
            viewModel.onFileSelected(uri, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Tenants from Excel", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize().testTag("excel_import_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is ImportUiState.Idle, is ImportUiState.Error -> {
                    ImportInitialView(
                        onDownloadTemplate = { viewModel.downloadTemplate() },
                        onSelectFile = { filePickerLauncher.launch(arrayOf("*/*")) }
                    )
                }
                is ImportUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is ImportUiState.PreviewReady -> {
                    ImportPreviewView(
                        preview = state.preview,
                        onToggleSelection = { viewModel.toggleRowSelection(it) },
                        onSetDuplicateAction = { row, action -> viewModel.setDuplicateAction(row, action) },
                        onSetAllDuplicatesAction = { viewModel.setAllDuplicatesAction(it) },
                        onConfirmImport = { viewModel.confirmImport() },
                        onCancel = { viewModel.reset() }
                    )
                }
                is ImportUiState.ImportSuccess -> {
                    ImportSuccessView(
                        count = state.count,
                        message = state.message,
                        onDone = {
                            viewModel.reset()
                            onNavigateToTenants()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ImportInitialView(
    onDownloadTemplate: () -> Unit,
    onSelectFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "How Excel Import Works",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Download the Excel template with formatted columns.\n" +
                            "2. Fill in your tenant details (Name, Room, Phone, Monthly Rent, Join Date, Status).\n" +
                            "3. Select the file to validate, preview duplicates, and import with one click.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Step 1: Download Template
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Step 1: Get the Template",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Download our ready-to-use template with sample rows and correct headers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDownloadTemplate,
                    modifier = Modifier.fillMaxWidth().testTag("download_template_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download Excel Template (.xlsx)", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Step 2: Upload File
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Step 2: Upload your Spreadsheet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose your completed .xlsx file. The app will validate and preview records before saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSelectFile,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("select_excel_file_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Excel File (.xlsx)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ImportPreviewView(
    preview: com.example.features.excel.domain.model.ImportPreviewResult,
    onToggleSelection: (Int) -> Unit,
    onSetDuplicateAction: (Int, DuplicateAction) -> Unit,
    onSetAllDuplicatesAction: (DuplicateAction) -> Unit,
    onConfirmImport: () -> Unit,
    onCancel: () -> Unit
) {
    val selectedCount = preview.rows.count { it.isSelected && (!it.isDuplicate || it.duplicateAction == DuplicateAction.IMPORT_AS_NEW) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Summary Header Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Import Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = preview.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "${preview.totalFound} found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BadgeChip(label = "${preview.newCount} New", color = Color(0xFF0D652D), bgColor = Color(0xFFE6F4EA))
                    if (preview.duplicateCount > 0) {
                        BadgeChip(label = "${preview.duplicateCount} Duplicate${if (preview.duplicateCount != 1) "s" else ""}", color = Color(0xFFE37400), bgColor = Color(0xFFFFF7EB))
                    }
                    if (preview.missingRoomCount > 0) {
                        BadgeChip(label = "${preview.missingRoomCount} Missing Room${if (preview.missingRoomCount != 1) "s" else ""}", color = Color(0xFF9334E6), bgColor = Color(0xFFF8F0FE))
                    }
                }

                if (preview.duplicateCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Duplicate actions:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onSetAllDuplicatesAction(DuplicateAction.SKIP) }) {
                                Text("Skip All", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { onSetAllDuplicatesAction(DuplicateAction.IMPORT_AS_NEW) }) {
                                Text("Import All As New", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Preview Items List
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(preview.rows, key = { it.rowNumber }) { row ->
                TenantPreviewCard(
                    row = row,
                    onToggle = { onToggleSelection(row.rowNumber) },
                    onSetDuplicateAction = { action -> onSetDuplicateAction(row.rowNumber, action) }
                )
            }
        }

        // Bottom Action Bar
        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirmImport,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(2f).height(48.dp).testTag("confirm_import_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Import $selectedCount Tenant${if (selectedCount != 1) "s" else ""}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TenantPreviewCard(
    row: TenantImportRow,
    onToggle: () -> Unit,
    onSetDuplicateAction: (DuplicateAction) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = row.isSelected,
                    onCheckedChange = { onToggle() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Room ${row.roomNumber} • ₹${String.format("%,.0f", row.monthlyRent)}/mo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (row.isDuplicate) {
                    BadgeChip(label = "Duplicate", color = Color(0xFFE37400), bgColor = Color(0xFFFFF7EB))
                } else {
                    BadgeChip(label = "New", color = Color(0xFF0D652D), bgColor = Color(0xFFE6F4EA))
                }
            }

            // Additional details & warnings
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (row.phone.isNotBlank()) {
                    Text("Phone: ${row.phone}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Text("Joined: ${row.joinDate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }

            if (!row.roomExists) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFFF7EB),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 48.dp)
                ) {
                    Text(
                        text = "ℹ️ Room ${row.roomNumber} does not exist in property (will be created automatically)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB45309),
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            if (row.isDuplicate) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 48.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "⚠️ ${row.duplicateReason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB91C1C),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = row.duplicateAction == DuplicateAction.SKIP,
                                onClick = { onSetDuplicateAction(DuplicateAction.SKIP) },
                                label = { Text("Skip", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = row.duplicateAction == DuplicateAction.IMPORT_AS_NEW,
                                onClick = { onSetDuplicateAction(DuplicateAction.IMPORT_AS_NEW) },
                                label = { Text("Import as new", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeChip(label: String, color: Color, bgColor: Color) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ImportSuccessView(
    count: Int,
    message: String,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color(0xFFE6F4EA), RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF0D652D),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Import Successful!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("import_done_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
    }
}
