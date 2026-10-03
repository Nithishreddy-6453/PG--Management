package com.example.features.tenants

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.core.designsystem.LocalSpacing
import com.example.data.database.TenantEntity
import com.example.features.tenants.domain.model.TenantMedia
import com.example.features.tenants.domain.model.UploadPhotoProgress
import com.example.features.tenants.ui.viewmodel.TenantDetailsUiEffect
import com.example.features.tenants.ui.viewmodel.TenantDetailsUiState
import com.example.features.tenants.ui.viewmodel.TenantDetailsViewModel
import kotlinx.coroutines.flow.collectLatest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantDetailsScreen(
    viewModel: TenantDetailsViewModel,
    onBackClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var showVacateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLeavingDateDialog by remember { mutableStateOf(false) }
    var inputLeavingDate by remember { mutableStateOf("") }

    // Photo Management States
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }
    var showFullPhotoPreview by remember { mutableStateOf(false) }
    var showDeletePhotoConfirmDialog by remember { mutableStateOf(false) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Launchers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.uploadProfilePhoto(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraTempUri != null) {
            viewModel.uploadProfilePhoto(cameraTempUri!!)
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleConsentResult(result.data)
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is TenantDetailsUiEffect.NavigateBack -> {
                    onBackClick()
                }
                is TenantDetailsUiEffect.ShowToast -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is TenantDetailsUiEffect.LaunchGoogleConsent -> {
                    consentLauncher.launch(effect.intent)
                }
            }
        }
    }

    // Refresh details when entering screen
    LaunchedEffect(key1 = true) {
        viewModel.loadTenantDetails()
    }

    fun openCamera() {
        try {
            val tempDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
            val tempFile = File(tempDir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            cameraTempUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            // Fallback to gallery picker if camera fails
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tenant Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("details_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    com.example.core.language.GlobalLanguageToggle(
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    val currentState = state
                    if (currentState is TenantDetailsUiState.Success) {
                        IconButton(
                            onClick = { onEditClick(currentState.tenant.id) },
                            modifier = Modifier.testTag("details_edit_button")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Profile")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.testTag("details_top_bar")
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize().testTag("tenant_details_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val currentState = state) {
                is TenantDetailsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag("details_loading_indicator"))
                    }
                }
                is TenantDetailsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = currentState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
                is TenantDetailsUiState.Success -> {
                    val tenant = currentState.tenant
                    val isVacated = tenant.roomNumber.isBlank()
                    val profilePhoto = currentState.profilePhoto

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // 1. Hero Header Card with Google Drive Profile Photo
                        ProfileHeroHeader(
                            tenant = tenant,
                            profilePhoto = profilePhoto,
                            isPhotoUploading = currentState.isPhotoUploading,
                            isPhotoDownloading = currentState.isPhotoDownloading,
                            photoDownloadError = currentState.photoDownloadError,
                            uploadProgress = currentState.uploadProgress,
                            needsDriveConsent = currentState.needsDriveConsent,
                            spacing = spacing,
                            isVacated = isVacated,
                            onPhotoClick = {
                                showPhotoOptionsDialog = true
                            },
                            onAddPhotoClick = {
                                showPhotoOptionsDialog = true
                            },
                            onAuthorizeDriveClick = {
                                consentLauncher.launch(viewModel.getDriveConsentIntent())
                            },
                            onRetryDownload = {
                                viewModel.retryPhotoDownload()
                            }
                        )

                        // 1.5. Scheduled to Vacate Banner (if leaving date is set and not vacated)
                        if (!isVacated && tenant.leavingDate.isNotBlank()) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.medium, vertical = spacing.small)
                                    .testTag("tenant_leaving_date_banner")
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EventBusy,
                                            contentDescription = null,
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Scheduled to Vacate",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF92400E)
                                                )
                                            )
                                            Text(
                                                text = "Leaving on: ${tenant.leavingDate}",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFFB45309)
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                inputLeavingDate = tenant.leavingDate
                                                showLeavingDateDialog = true
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color(0xFF92400E)
                                            ),
                                            modifier = Modifier.weight(1f).testTag("edit_leaving_date_button")
                                        ) {
                                            Text("Change Date", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.updateLeavingDate("")
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color(0xFFDC2626)
                                            ),
                                            modifier = Modifier.weight(1f).testTag("clear_leaving_date_button")
                                        ) {
                                            Text("Clear Date", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        Button(
                                            onClick = { showVacateDialog = true },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFDC2626),
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier.weight(1f).testTag("vacate_now_button")
                                        ) {
                                            Text("Vacate Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Action Controls Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium, vertical = spacing.small),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            if (!isVacated) {
                                if (tenant.leavingDate.isBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            inputLeavingDate = ""
                                            showLeavingDateDialog = true
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .testTag("details_set_leaving_date_button")
                                    ) {
                                        Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(spacing.small))
                                        Text("Set Leaving Date", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }

                                Button(
                                    onClick = { showVacateDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("details_vacate_button")
                                ) {
                                    Icon(Icons.Default.MeetingRoom, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(spacing.small))
                                    Text("Vacate Resident", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            } else {
                                Button(
                                    onClick = { showDeleteDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("details_delete_button")
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                                    Spacer(modifier = Modifier.width(spacing.small))
                                    Text("Erase Profile Permanently", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 3. Contact & Essential Demographics Card
                        Card(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium, vertical = spacing.small)
                        ) {
                            Column(modifier = Modifier.padding(spacing.large)) {
                                Text(
                                    text = "Contact & Personal Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = spacing.small)
                                )
                                HorizontalDivider(modifier = Modifier.padding(bottom = spacing.small))

                                InfoDetailRow(label = "Primary Phone", value = tenant.phone, icon = Icons.Default.Phone)
                                if (tenant.alternateContact.isNotBlank()) {
                                    InfoDetailRow(label = "Alternate Phone", value = tenant.alternateContact, icon = Icons.Default.PhoneIphone)
                                }
                                if (tenant.email.isNotBlank()) {
                                    InfoDetailRow(label = "Email Address", value = tenant.email, icon = Icons.Default.Email)
                                }
                                if (tenant.emergencyContact.isNotBlank()) {
                                    InfoDetailRow(label = "Emergency Contact", value = tenant.emergencyContact, icon = Icons.Default.ContactPhone)
                                }
                                if (tenant.gender.isNotBlank()) {
                                    InfoDetailRow(label = "Gender", value = tenant.gender, icon = Icons.Default.Person)
                                }
                                if (tenant.dob.isNotBlank()) {
                                    InfoDetailRow(label = "Date of Birth", value = tenant.dob, icon = Icons.Default.Cake)
                                }
                                if (tenant.occupation.isNotBlank()) {
                                    InfoDetailRow(label = "Occupation", value = tenant.occupation, icon = Icons.Default.Work)
                                }
                                if (tenant.companyOrCollege.isNotBlank()) {
                                    InfoDetailRow(label = "Company / College", value = tenant.companyOrCollege, icon = Icons.Default.Business)
                                }
                                if (tenant.address.isNotBlank()) {
                                    InfoDetailRow(label = "Permanent Address", value = tenant.address, icon = Icons.Default.Home)
                                }
                            }
                        }

                        // 4. Stay & Financial Agreement Card
                        Card(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium, vertical = spacing.small)
                        ) {
                            Column(modifier = Modifier.padding(spacing.large)) {
                                Text(
                                    text = "Lease & Financial Terms",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = spacing.small)
                                )
                                HorizontalDivider(modifier = Modifier.padding(bottom = spacing.small))

                                InfoDetailRow(label = "Move-In Date", value = tenant.moveInDate, icon = Icons.Default.CalendarToday)
                                if (tenant.leavingDate.isNotBlank()) {
                                    InfoDetailRow(
                                        label = "Scheduled Leaving Date",
                                        value = tenant.leavingDate,
                                        icon = Icons.Default.EventBusy,
                                        valueColor = Color(0xFFD97706)
                                    )
                                }
                                InfoDetailRow(label = "Monthly Rent", value = "₹${tenant.monthlyRent}", icon = Icons.Default.CurrencyRupee)
                                InfoDetailRow(label = "Security Deposit", value = "₹${tenant.securityDeposit}", icon = Icons.Default.Savings)
                                if (tenant.advancePaid > 0) {
                                    InfoDetailRow(label = "Advance Rent Paid", value = "₹${tenant.advancePaid}", icon = Icons.Default.Payment)
                                }
                                InfoDetailRow(
                                    label = "KYC Verification",
                                    value = if (tenant.isKycUploaded) "Verified (${tenant.kycDocType})" else "Pending Verification",
                                    icon = Icons.Default.VerifiedUser,
                                    valueColor = if (tenant.isKycUploaded) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // 5. Additional Notes Card
                        if (tenant.notes.isNotBlank()) {
                            Card(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.medium)
                            ) {
                                Column(modifier = Modifier.padding(spacing.large)) {
                                    Text(
                                        text = "Additional Notes",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = spacing.small)
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(bottom = spacing.small))
                                    Text(
                                        text = tenant.notes,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 6. Payment History Ledger Section
                        Card(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium, vertical = spacing.small)
                        ) {
                            Column(modifier = Modifier.padding(spacing.large)) {
                                Text(
                                    text = "Rent Ledger Timeline",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = spacing.small)
                                )
                                HorizontalDivider(modifier = Modifier.padding(bottom = spacing.small))

                                if (currentState.payments.isEmpty()) {
                                    Text(
                                        text = "No recorded rent statements.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(vertical = spacing.small),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    currentState.payments.forEach { payment ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = spacing.small),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(payment.billingMonth, fontWeight = FontWeight.Bold)
                                                Text("Due: ${payment.dueDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "₹${payment.amount}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(end = spacing.small)
                                                )
                                                
                                                val badgeColor = if (payment.status == "Paid") {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.errorContainer
                                                }
                                                val textCol = if (payment.status == "Paid") {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                }

                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                                                ) {
                                                    Text(
                                                        text = payment.status,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = textCol,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }

            // Photo Management Bottom Sheet / Dialog
            if (showPhotoOptionsDialog) {
                val currentState = state as? TenantDetailsUiState.Success
                val hasRemotePhoto = currentState?.profilePhoto != null && currentState.profilePhoto.driveFileId.isNotBlank()
                val hasLocalPhoto = currentState?.profilePhoto != null && currentState.profilePhoto.hasLocalFile

                AlertDialog(
                    onDismissRequest = { showPhotoOptionsDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasRemotePhoto) "Profile Photo Options" else "Add Profile Photo",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("photo_options_dialog")
                        ) {
                            Text(
                                text = "Stored in Google Drive under: PG Manager / <Property Name> / Tenants / <Tenant Name> - <Last 4 Phone> - <Short ID> / Photos/",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            // 1. Take Photo
                            FilledTonalButton(
                                onClick = {
                                    showPhotoOptionsDialog = false
                                    openCamera()
                                },
                                modifier = Modifier.fillMaxWidth().testTag("take_photo_option"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (hasRemotePhoto) "Replace with Camera" else "Take Photo with Camera", fontWeight = FontWeight.SemiBold)
                            }

                            // 2. Choose from Gallery
                            FilledTonalButton(
                                onClick = {
                                    showPhotoOptionsDialog = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("gallery_photo_option"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (hasRemotePhoto) "Replace from Gallery" else "Choose from Gallery", fontWeight = FontWeight.SemiBold)
                            }

                            // 3. View Full Photo (if local file exists)
                            if (hasLocalPhoto) {
                                OutlinedButton(
                                    onClick = {
                                        showPhotoOptionsDialog = false
                                        showFullPhotoPreview = true
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("view_photo_option"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("View Full Photo", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // 4. Remove Photo
                            if (hasRemotePhoto) {
                                OutlinedButton(
                                    onClick = {
                                        showPhotoOptionsDialog = false
                                        showDeletePhotoConfirmDialog = true
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth().testTag("remove_photo_option"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Remove Photo", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showPhotoOptionsDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            // Full Photo Preview Dialog
            if (showFullPhotoPreview) {
                val currentState = state as? TenantDetailsUiState.Success
                val photoPath = currentState?.profilePhoto?.localFilePath
                if (photoPath != null && File(photoPath).exists()) {
                    Dialog(onDismissRequest = { showFullPhotoPreview = false }) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .testTag("full_photo_preview_dialog")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentState.tenant.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = { showFullPhotoPreview = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close")
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                AsyncImage(
                                    model = File(photoPath),
                                    contentDescription = "Full Profile Photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 400.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Stored on Google Drive",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // Remove Photo Confirmation Dialog
            if (showDeletePhotoConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeletePhotoConfirmDialog = false },
                    title = { Text("Remove Profile Photo?", fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure you want to remove the profile photo for this tenant? This will delete the photo link and remove the file.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteProfilePhoto()
                                showDeletePhotoConfirmDialog = false
                            },
                            modifier = Modifier.testTag("confirm_delete_photo_button")
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeletePhotoConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Leaving Date Dialog
            if (showLeavingDateDialog) {
                AlertDialog(
                    onDismissRequest = { showLeavingDateDialog = false },
                    title = { Text("Set Leaving Date", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                text = "Set the expected move-out / vacating date for this tenant. This helps track upcoming room vacancies in advance.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedTextField(
                                value = inputLeavingDate,
                                onValueChange = { inputLeavingDate = it },
                                label = { Text("Leaving Date (YYYY-MM-DD)") },
                                placeholder = { Text("e.g. 2026-10-31") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("leaving_date_dialog_input"),
                                trailingIcon = {
                                    if (inputLeavingDate.isNotBlank()) {
                                        IconButton(onClick = { inputLeavingDate = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            // Quick Presets
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

                                val d7 = remember {
                                    val c = java.util.Calendar.getInstance()
                                    c.add(java.util.Calendar.DAY_OF_MONTH, 7)
                                    sdf.format(c.time)
                                }
                                val d15 = remember {
                                    val c = java.util.Calendar.getInstance()
                                    c.add(java.util.Calendar.DAY_OF_MONTH, 15)
                                    sdf.format(c.time)
                                }
                                val d30 = remember {
                                    val c = java.util.Calendar.getInstance()
                                    c.add(java.util.Calendar.DAY_OF_MONTH, 30)
                                    sdf.format(c.time)
                                }

                                SuggestionChip(
                                    onClick = { inputLeavingDate = d7 },
                                    label = { Text("+7 Days", fontSize = 11.sp) }
                                )
                                SuggestionChip(
                                    onClick = { inputLeavingDate = d15 },
                                    label = { Text("+15 Days", fontSize = 11.sp) }
                                )
                                SuggestionChip(
                                    onClick = { inputLeavingDate = d30 },
                                    label = { Text("+30 Days", fontSize = 11.sp) }
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.updateLeavingDate(inputLeavingDate)
                                showLeavingDateDialog = false
                            },
                            modifier = Modifier.testTag("confirm_leaving_date_button")
                        ) {
                            Text("Save Date", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLeavingDateDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Vacate Dialog Confirmation
            com.example.core.designsystem.PgConfirmDialog(
                isOpen = showVacateDialog,
                title = "Vacate Tenant?",
                message = "This will release the bed assignment and set the occupancy status of the tenant to Vacated. The historical rent logs and tenant personal history will be preserved.",
                confirmText = "Confirm Vacate",
                isDestructive = true,
                onConfirm = {
                    viewModel.vacateTenant()
                    showVacateDialog = false
                },
                onDismiss = { showVacateDialog = false }
            )

            // Delete Dialog Confirmation
            com.example.core.designsystem.PgConfirmDialog(
                isOpen = showDeleteDialog,
                title = "Delete Tenant Permanently?",
                message = "Are you sure you want to delete this tenant permanently? This action will completely erase the resident profile and all past payment history logs from the system and cannot be undone.",
                confirmText = "Delete Permanently",
                isDestructive = true,
                onConfirm = {
                    viewModel.deleteTenant()
                    showDeleteDialog = false
                },
                onDismiss = { showDeleteDialog = false }
            )
        }
    }
}

@Composable
fun ProfileHeroHeader(
    tenant: TenantEntity,
    profilePhoto: TenantMedia?,
    isPhotoUploading: Boolean,
    isPhotoDownloading: Boolean,
    photoDownloadError: String?,
    uploadProgress: UploadPhotoProgress,
    needsDriveConsent: Boolean,
    spacing: com.example.core.designsystem.PgSpacing,
    isVacated: Boolean,
    onPhotoClick: () -> Unit,
    onAddPhotoClick: () -> Unit,
    onAuthorizeDriveClick: () -> Unit,
    onRetryDownload: () -> Unit
) {
    val initials = if (tenant.name.isNotBlank()) {
        tenant.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
    } else "T"

    val hasRemotePhoto = profilePhoto != null && profilePhoto.driveFileId.isNotBlank()
    val hasLocalImage = profilePhoto != null && profilePhoto.hasLocalFile

    val avatarBg = if (isVacated) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    
    val avatarColor = if (isVacated) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(spacing.large)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Photo Circle with click action
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                    .clickable(
                        onClick = {
                            if (hasLocalImage) {
                                onPhotoClick()
                            } else if (hasRemotePhoto) {
                                if (needsDriveConsent) onAuthorizeDriveClick()
                                else if (photoDownloadError != null) onRetryDownload()
                                else onPhotoClick()
                            } else {
                                onAddPhotoClick()
                            }
                        }
                    )
                    .testTag("tenant_profile_photo_avatar"),
                contentAlignment = Alignment.Center
            ) {
                if (hasLocalImage) {
                    AsyncImage(
                        model = File(profilePhoto!!.localFilePath),
                        contentDescription = "Profile Photo of ${tenant.name}",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(avatarBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = avatarColor
                        )
                    }

                    // If remote photo exists but not downloaded, show indicator over initials
                    if (hasRemotePhoto && !isPhotoUploading && !isPhotoDownloading) {
                        if (needsDriveConsent) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = "Drive Permission Needed",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else if (photoDownloadError != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry Download",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Loading Overlay during photo upload or download
                if (isPhotoUploading || isPhotoDownloading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Action badge overlay at bottom right
                if (!isPhotoUploading && !isPhotoDownloading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasRemotePhoto && needsDriveConsent) Color(0xFFDC2626)
                                else MaterialTheme.colorScheme.primary
                            )
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val badgeIcon = when {
                            hasLocalImage -> Icons.Default.Edit
                            hasRemotePhoto && needsDriveConsent -> Icons.Default.CloudSync
                            hasRemotePhoto && photoDownloadError != null -> Icons.Default.Refresh
                            hasRemotePhoto -> Icons.Default.CloudDownload
                            else -> Icons.Default.AddAPhoto
                        }
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = if (hasRemotePhoto) "Manage Photo" else "Add Photo",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Photo Action Row (Add / Manage / Connect / Retry)
            if (!isVacated) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    when {
                        hasLocalImage -> {
                            TextButton(
                                onClick = onPhotoClick,
                                modifier = Modifier.testTag("manage_photo_button")
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Manage Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        hasRemotePhoto -> {
                            // Remote Drive photo exists! Never show "Add Photo" here.
                            when {
                                needsDriveConsent -> {
                                    OutlinedButton(
                                        onClick = onAuthorizeDriveClick,
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("connect_drive_photo_button")
                                    ) {
                                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Connect Drive to View", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                isPhotoDownloading -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Downloading from Drive...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                photoDownloadError != null -> {
                                    OutlinedButton(
                                        onClick = onRetryDownload,
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("retry_photo_download_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry Download", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                else -> {
                                    OutlinedButton(
                                        onClick = onRetryDownload,
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("fetch_photo_button")
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Fetch Photo (Drive)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        else -> {
                            // Truly no photo exists anywhere
                            OutlinedButton(
                                onClick = onAddPhotoClick,
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("add_photo_button")
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Photo (Drive)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Upload Status / Consent / Download Error Banners
            if (isPhotoUploading) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (uploadProgress) {
                        is UploadPhotoProgress.ProcessingImage -> uploadProgress.stage
                        is UploadPhotoProgress.UploadingToDrive -> "Uploading to Google Drive..."
                        else -> "Processing..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            } else if (hasRemotePhoto && needsDriveConsent) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Drive access required to view tenant photos.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                            Text("Connect Google Drive on this phone to synchronize and view profile photos.", fontSize = 11.sp, color = Color(0xFFB91C1C))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = onAuthorizeDriveClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("connect_drive_banner_button")
                        ) {
                            Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (hasRemotePhoto && photoDownloadError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Photo Download Pending", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                            Text(photoDownloadError, fontSize = 11.sp, color = Color(0xFFB45309), maxLines = 2)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = onRetryDownload,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("retry_banner_button")
                        ) {
                            Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (needsDriveConsent && !hasRemotePhoto) {
                // If user tapped add photo and consent is needed
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Drive Permission Required", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                            Text("Grant Google Drive permission to store tenant photos securely.", fontSize = 11.sp, color = Color(0xFFB91C1C))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = onAuthorizeDriveClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Full Name
            Text(
                text = tenant.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(spacing.extraSmall))

            // Allocated Location Label
            if (isVacated) {
                Text(
                    text = "Vacated Resident (History)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Room ${tenant.roomNumber} • ${tenant.bedId}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun InfoDetailRow(
    label: String,
    value: String,
    icon: ImageVector,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(spacing.medium))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
    }
}
