package com.example.features.excel.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.shareIntentEvent.collect { intent ->
            try {
                context.startActivity(intent)
            } catch (_: android.content.ActivityNotFoundException) {
                snackbarHostState.showSnackbar("No app is available to save or share the Excel template.")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Could not open template sharing: ${e.message ?: "Unknown error"}")
            }
        }
    }
    LaunchedEffect(uiState) {
        if (uiState is ImportUiState.Error) {
            snackbarHostState.showSnackbar((uiState as ImportUiState.Error).message)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            var fileName = "imported_tenants.xlsx"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: fileName
                    }
                }
            } catch (_: Exception) {
                // The fallback name is used if the provider does not expose display-name metadata.
            }
            viewModel.onFileSelected(uri, fileName)
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(56.dp)
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.fillMaxSize().testTag("excel_import_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(26.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import Tenants from Excel",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Add multiple tenants at once using an Excel file",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize().testTag("excel_import_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                ImportUiState.Idle, is ImportUiState.Error -> ImportInitialView(
                    onDownloadTemplate = { viewModel.downloadTemplate() },
                    onSelectFile = {
                        filePickerLauncher.launch(arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel",
                            "application/octet-stream"
                        ))
                    }
                )
                is ImportUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is ImportUiState.PreviewReady -> ImportPreviewView(
                    preview = state.preview,
                    onToggleSelection = { viewModel.toggleRowSelection(it) },
                    onSetDuplicateAction = { row, action -> viewModel.setDuplicateAction(row, action) },
                    onSetAllDuplicatesAction = { viewModel.setAllDuplicatesAction(it) },
                    onConfirmImport = { viewModel.confirmImport() },
                    onCancel = { viewModel.reset() }
                )
                is ImportUiState.ImportSuccess -> ImportSuccessView(
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

@Composable
fun ImportInitialView(
    onDownloadTemplate: () -> Unit,
    onSelectFile: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val blue = Color(0xFF0866FF)
    val green = Color(0xFF16A34A)
    val purple = Color(0xFF6842F5)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.primaryContainer.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(64.dp).background(Color(0xFFD9E9FF), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = blue, modifier = Modifier.size(38.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("How Excel Import Works", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text("Follow these simple steps to import multiple tenants into your PG Manager.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                }
                ImportInstructionRow("1", "Download the Excel template with formatted columns.")
                ImportInstructionRow("2", "Fill in your tenant details (Name, Room, Phone, Monthly Rent, Join Date, Status, etc.).")
                ImportInstructionRow("3", "Select the file to validate, preview duplicates, and import with one click.")
            }
        }

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.22f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(96.dp).background(Color(0xFFEAF8EF), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.TableChart, contentDescription = "Excel spreadsheet", tint = Color(0xFF17864A), modifier = Modifier.size(48.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        StepPill("STEP 1")
                        Text("Get the Excel Template", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
                        Text("Download our ready-to-use template with sample data and correct column headers.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                }
                OutlinedButton(
                    onClick = onDownloadTemplate,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("download_template_button"),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, blue),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = blue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Download Excel Template (.xlsx)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF0F9F3), RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(48.dp).background(Color(0xFFD9F4E2), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = green, modifier = Modifier.size(30.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Template includes columns like:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF183A2A))
                        Spacer(Modifier.height(4.dp))
                        Text("Name, Room Number, Phone, Monthly Rent, Join Date, Status and more.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF50645A))
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.22f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(96.dp).background(Color(0xFFF1EBFF), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Upload spreadsheet", tint = purple, modifier = Modifier.size(48.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        StepPill("STEP 2")
                        Text("Upload Your Spreadsheet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
                        Text("Choose your completed .xlsx file. The app will validate and preview records before saving.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = onSelectFile,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("select_excel_file_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blue, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 18.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Select Excel File (.xlsx)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(colors.secondaryContainer.copy(alpha = 0.55f), RoundedCornerShape(24.dp)).padding(20.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = purple, modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Text("Your data is safe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("We only read the selected Excel file to import tenants. No changes are made to your file.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ImportInstructionRow(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(44.dp).background(Color(0xFFD9E9FF), CircleShape), contentAlignment = Alignment.Center) {
            Text(number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0866FF))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(top = 5.dp))
    }
}

@Composable
private fun StepPill(label: String) {
    Surface(color = Color(0xFFE2EEFF), shape = RoundedCornerShape(50)) {
        Text(label, color = Color(0xFF0866FF), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
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
