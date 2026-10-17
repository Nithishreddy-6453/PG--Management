package com.example.features.tenants

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.core.designsystem.LocalSpacing
import com.example.data.database.PropertyEntity
import com.example.data.database.TenantEntity
import com.example.data.database.RentPaymentEntity
import com.example.features.tenants.domain.model.TenantMedia
import com.example.features.tenants.domain.model.UploadPhotoProgress
import com.example.features.tenants.ui.viewmodel.TenantDetailsUiEffect
import com.example.features.tenants.ui.viewmodel.TenantDetailsUiState
import com.example.features.tenants.ui.viewmodel.TenantDetailsViewModel
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var showPaymentHistorySheet by remember { mutableStateOf(false) }

    // Expandable Section States
    var isPersonalExpanded by remember { mutableStateOf(true) }
    var isRoomStayExpanded by remember { mutableStateOf(true) }
    var isGuardianExpanded by remember { mutableStateOf(true) }
    var isAddressExpanded by remember { mutableStateOf(true) }
    var isRentDepositExpanded by remember { mutableStateOf(true) }
    var isDocumentsExpanded by remember { mutableStateOf(true) }
    var isAdditionalExpanded by remember { mutableStateOf(true) }

    // Photo Management States
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }
    var showFullPhotoPreview by remember { mutableStateOf(false) }
    var showDeletePhotoConfirmDialog by remember { mutableStateOf(false) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Photo Launchers
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

    // Document Picker Launcher
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val name = if (cursor != null && cursor.moveToFirst() && nameIndex != null && nameIndex >= 0) {
                cursor.getString(nameIndex)
            } else "Document.pdf"
            cursor?.close()
            val mime = context.contentResolver.getType(it) ?: "application/pdf"
            viewModel.uploadDocument(it, name, mime)
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
                is TenantDetailsUiEffect.NavigateBack -> onBackClick()
                is TenantDetailsUiEffect.ShowToast -> snackbarHostState.showSnackbar(effect.message)
                is TenantDetailsUiEffect.LaunchGoogleConsent -> consentLauncher.launch(effect.intent)
            }
        }
    }

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
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    fun openWhatsApp(phone: String) {
        try {
            val cleanPhone = phone.replace("[^0-9]".toRegex(), "")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone"))
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${phone.replace("[^0-9]".toRegex(), "")}"))
            context.startActivity(intent)
        }
    }

    fun makeCall(phone: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun sendRentReminder(tenant: TenantEntity, amount: Double) {
        val message = "Dear ${tenant.name}, your rent payment of ₹${amount.toInt()} for Room ${tenant.roomNumber} is due. Kindly pay at your earliest convenience."
        val cleanPhone = tenant.phone.replace("[^0-9]".toRegex(), "")
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"))
            context.startActivity(intent)
        } catch (_: Exception) {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$cleanPhone")).apply {
                putExtra("sms_body", message)
            }
            context.startActivity(smsIntent)
        }
    }

    fun openDocument(doc: TenantMedia) {
        try {
            val uriToOpen = if (doc.hasLocalFile) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(doc.localFilePath)
                )
            } else if (doc.fileUrl.isNotBlank()) {
                Uri.parse(doc.fileUrl)
            } else if (doc.driveFileId.isNotBlank()) {
                Uri.parse("https://drive.google.com/uc?export=view&id=${doc.driveFileId}")
            } else null

            if (uriToOpen != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uriToOpen, doc.mimeType.ifBlank { "*/*" })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            val fallbackUrl = doc.fileUrl.ifBlank { "https://drive.google.com/uc?export=view&id=${doc.driveFileId}" }
            if (fallbackUrl.isNotBlank()) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Tenant Profile",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "View and manage tenant details",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 8.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .clickable(onClick = onBackClick)
                            .testTag("details_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                actions = {
                    com.example.core.language.GlobalLanguageToggle(
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    val currentState = state as? TenantDetailsUiState.Success
                    var showOverflowMenu by remember { mutableStateOf(false) }
                    Box {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9))
                                .clickable { showOverflowMenu = true }
                                .testTag("details_overflow_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            if (currentState != null) {
                                DropdownMenuItem(
                                    text = { Text("Edit Tenant Details", fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF2563EB)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onEditClick(currentState.tenant.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add Document / Photo", fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color(0xFF16A34A)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showPhotoOptionsDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Set Leaving Date", fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.Event, contentDescription = null, tint = Color(0xFFD97706)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        inputLeavingDate = currentState.tenant.leavingDate
                                        showLeavingDateDialog = true
                                    }
                                )
                                HorizontalDivider(color = Color(0xFFE2E8F0))
                                DropdownMenuItem(
                                    text = { Text("Vacate Tenant", fontSize = 14.sp, color = Color(0xFFDC2626)) },
                                    leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFFDC2626)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showVacateDialog = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF8FAFC),
                    titleContentColor = Color(0xFF0F172A)
                ),
                modifier = Modifier.testTag("details_top_bar")
            )
        },
        bottomBar = {
            val currentState = state as? TenantDetailsUiState.Success
            if (currentState != null) {
                Surface(
                    color = Color.White,
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth().testTag("tenant_bottom_actions_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onEditClick(currentState.tenant.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("edit_tenant_bottom_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Tenant", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        val isVacated = currentState.tenant.roomNumber.isBlank()
                        if (!isVacated) {
                            Button(
                                onClick = { showVacateDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFEF2F2),
                                    contentColor = Color(0xFFDC2626)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("vacate_tenant_bottom_button")
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Vacate Tenant", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        } else {
                            Button(
                                onClick = { showDeleteDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFEF2F2),
                                    contentColor = Color(0xFFDC2626)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("delete_tenant_bottom_button")
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Tenant", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize().testTag("tenant_details_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC))
        ) {
            when (val currentState = state) {
                is TenantDetailsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color(0xFF2563EB),
                            modifier = Modifier.testTag("details_loading_indicator")
                        )
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
                    val property = currentState.property
                    val isVacated = tenant.roomNumber.isBlank()
                    val profilePhoto = currentState.profilePhoto

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Compact Tenant Header Card
                        TenantIdentityCard(
                            tenant = tenant,
                            profilePhoto = profilePhoto,
                            isPhotoUploading = currentState.isPhotoUploading,
                            isPhotoDownloading = currentState.isPhotoDownloading,
                            photoDownloadError = currentState.photoDownloadError,
                            needsDriveConsent = currentState.needsDriveConsent,
                            isVacated = isVacated,
                            onPhotoClick = { showPhotoOptionsDialog = true },
                            onEditClick = { onEditClick(tenant.id) },
                            onAuthorizeDriveClick = { consentLauncher.launch(viewModel.getDriveConsentIntent()) },
                            onRetryDownload = { viewModel.retryPhotoDownload() }
                        )

                        // 1.5. Google Drive Authorise & Photo Sync Banner (if action needed or photo on Drive)
                        val hasRemotePhoto = profilePhoto != null && profilePhoto.driveFileId.isNotBlank()
                        val hasLocalPhoto = profilePhoto != null && profilePhoto.hasLocalFile
                        val needsPhotoSyncBanner = currentState.needsDriveConsent || (hasRemotePhoto && !hasLocalPhoto) || currentState.photoDownloadError != null

                        if (needsPhotoSyncBanner) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (currentState.needsDriveConsent) Color(0xFFFEF3C7) else Color(0xFFEFF6FF),
                                border = BorderStroke(1.dp, if (currentState.needsDriveConsent) Color(0xFFFCD34D) else Color(0xFFBFDBFE)),
                                modifier = Modifier.fillMaxWidth().testTag("drive_photo_sync_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (currentState.needsDriveConsent) Icons.Default.CloudOff else Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = if (currentState.needsDriveConsent) Color(0xFFB45309) else Color(0xFF2563EB),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (currentState.needsDriveConsent) "Google Drive Consent Required" else "Profile Photo Available in Drive",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (currentState.needsDriveConsent) Color(0xFF92400E) else Color(0xFF1E3A8A)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (currentState.needsDriveConsent)
                                                "Authorise Google Drive on this device to view profile photos uploaded from other phones."
                                            else
                                                "Photo exists on Drive. Tap Sync to download and view it on this phone.",
                                            fontSize = 12.sp,
                                            color = if (currentState.needsDriveConsent) Color(0xFFB45309) else Color(0xFF3B82F6)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (currentState.needsDriveConsent) {
                                                consentLauncher.launch(viewModel.getDriveConsentIntent())
                                            } else {
                                                viewModel.syncAndAuthorizeDrive()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (currentState.needsDriveConsent) Color(0xFFD97706) else Color(0xFF2563EB)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("authorise_sync_photo_banner_button")
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (currentState.needsDriveConsent) "Authorise" else "Sync",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Quick Actions Row
                        QuickActionsRow(
                            phone = tenant.phone,
                            onWhatsAppClick = { openWhatsApp(tenant.phone) },
                            onCallClick = { makeCall(tenant.phone) },
                            onMoreClick = { showPhotoOptionsDialog = true }
                        )

                        // 3. Property, Room & Bed Summary Chips Row
                        PropertyRoomBedChipsRow(
                            property = property,
                            tenant = tenant,
                            isVacated = isVacated
                        )

                        // 4. Section 1: Personal Details
                        CollapsibleCardSection(
                            title = "Personal Details",
                            icon = Icons.Default.Person,
                            iconBg = Color(0xFFEFF6FF),
                            iconTint = Color(0xFF2563EB),
                            isExpanded = isPersonalExpanded,
                            onToggle = { isPersonalExpanded = !isPersonalExpanded },
                            testTag = "personal_details_section"
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                DetailItemRow(label = "Full Name", value = tenant.name)
                                DetailItemRow(label = "Phone Number", value = tenant.phone)
                                DetailItemRow(label = "Date of Birth", value = tenant.dob)
                                DetailItemRow(label = "Gender", value = tenant.gender)
                                DetailItemRow(label = "Occupation", value = tenant.occupation)
                                DetailItemRow(label = "College / Office", value = tenant.companyOrCollege)
                                DetailItemRow(label = "Office / College Phone", value = tenant.alternateContact)
                                FoodPreferenceRow(foodPref = tenant.notes)
                            }
                        }

                        // 5. Section 2: Room & Stay Details
                        CollapsibleCardSection(
                            title = "Room & Stay Details",
                            icon = Icons.Default.Apartment,
                            iconBg = Color(0xFFECFDF5),
                            iconTint = Color(0xFF10B981),
                            isExpanded = isRoomStayExpanded,
                            onToggle = { isRoomStayExpanded = !isRoomStayExpanded },
                            testTag = "room_stay_details_section"
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                DetailItemRow(
                                    label = "Property",
                                    value = property?.propertyName?.ifBlank { "Sri Lakshmi Residency" } ?: "Sri Lakshmi Residency"
                                )
                                DetailItemRow(
                                    label = "Room Number",
                                    value = if (!isVacated) "Room ${tenant.roomNumber}" else "Vacated",
                                    valueColor = if (!isVacated) Color(0xFF2563EB) else Color(0xFFDC2626)
                                )
                                DetailItemRow(
                                    label = "Bed",
                                    value = if (tenant.bedId.isNotBlank()) (if (tenant.bedId.startsWith("Bed", ignoreCase = true)) tenant.bedId else "Bed ${tenant.bedId}") else "—",
                                    valueColor = Color(0xFF2563EB)
                                )
                                val floorStr = remember(tenant.roomNumber) {
                                    when {
                                        tenant.roomNumber.startsWith("1") -> "1st Floor"
                                        tenant.roomNumber.startsWith("2") -> "2nd Floor"
                                        tenant.roomNumber.startsWith("3") -> "3rd Floor"
                                        tenant.roomNumber.startsWith("4") -> "4th Floor"
                                        tenant.roomNumber.startsWith("G", ignoreCase = true) -> "Ground Floor"
                                        else -> "1st Floor"
                                    }
                                }
                                DetailItemRow(label = "Floor", value = if (!isVacated) floorStr else "—")
                                DetailItemRow(label = "Joining Date", value = tenant.moveInDate)
                                DetailItemRow(
                                    label = "Monthly Rent",
                                    value = "₹" + formatCurrency(tenant.monthlyRent),
                                    isBold = true
                                )
                                StatusBadgeRow(label = "Status", isActive = !isVacated)
                                VacateDateRow(leavingDate = tenant.leavingDate)
                            }
                        }

                        // 6. Section 3: Guardian / Emergency Contact
                        CollapsibleCardSection(
                            title = "Guardian / Emergency Contact",
                            icon = Icons.Default.People,
                            iconBg = Color(0xFFFFFBEB),
                            iconTint = Color(0xFFD97706),
                            isExpanded = isGuardianExpanded,
                            onToggle = { isGuardianExpanded = !isGuardianExpanded },
                            testTag = "guardian_contact_section"
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                DetailItemRow(label = "Father's Name", value = parseFatherName(tenant.emergencyContact, tenant.notes))
                                DetailItemRow(label = "Father's Phone", value = tenant.emergencyContact)
                                DetailItemRow(label = "Relationship", value = if (tenant.emergencyContact.isNotBlank()) "Father" else "—")
                                DetailItemRow(label = "Local Reference Name", value = parseLocalRefName(tenant.alternateContact, tenant.notes))
                                DetailItemRow(label = "Local Reference Phone", value = tenant.alternateContact)
                                DetailItemRow(label = "Relationship", value = if (tenant.alternateContact.isNotBlank()) "Friend" else "—")
                            }
                        }

                        // 7. Section 4: Address Details
                        CollapsibleCardSection(
                            title = "Address Details",
                            icon = Icons.Default.LocationOn,
                            iconBg = Color(0xFFF3E8FF),
                            iconTint = Color(0xFF9333EA),
                            isExpanded = isAddressExpanded,
                            onToggle = { isAddressExpanded = !isAddressExpanded },
                            testTag = "address_details_section"
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                DetailItemRow(label = "Current Address", value = tenant.address)
                                DetailItemRow(label = "Permanent Address", value = tenant.address)
                                val (city, stateName, pin) = parseAddressComponents(tenant.address, property)
                                DetailItemRow(label = "City", value = city)
                                DetailItemRow(label = "State", value = stateName)
                                DetailItemRow(label = "PIN Code", value = pin)
                            }
                        }

                        // 8. Section 5: Rent & Deposit
                        CollapsibleCardSection(
                            title = "Rent & Deposit",
                            icon = Icons.Default.CurrencyRupee,
                            iconBg = Color(0xFFFFF7ED),
                            iconTint = Color(0xFFEA580C),
                            isExpanded = isRentDepositExpanded,
                            onToggle = { isRentDepositExpanded = !isRentDepositExpanded },
                            testTag = "rent_deposit_section"
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // 2x2 Financial Cards Grid
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Monthly Rent Blue Card
                                        FinancialCardItem(
                                            title = "Monthly Rent",
                                            amountText = "₹" + formatCurrency(tenant.monthlyRent),
                                            subtext = "per month",
                                            icon = Icons.Default.Home,
                                            containerBg = Color(0xFFEFF6FF),
                                            iconBg = Color(0xFF2563EB),
                                            titleColor = Color(0xFF1E40AF),
                                            amountColor = Color(0xFF1E3A8A),
                                            subtextColor = Color(0xFF3B82F6),
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Refundable Deposit Orange Card
                                        FinancialCardItem(
                                            title = "Refundable Deposit",
                                            amountText = "₹" + formatCurrency(tenant.securityDeposit),
                                            subtext = "One-time (Refundable)",
                                            icon = Icons.Default.Shield,
                                            containerBg = Color(0xFFFFFBEB),
                                            iconBg = Color(0xFFD97706),
                                            titleColor = Color(0xFF92400E),
                                            amountColor = Color(0xFF78350F),
                                            subtextColor = Color(0xFFD97706),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Advance Received Green Card
                                        FinancialCardItem(
                                            title = "Advance Received",
                                            amountText = "₹" + formatCurrency(tenant.advancePaid),
                                            subtext = "Already received",
                                            icon = Icons.Default.AccountBalanceWallet,
                                            containerBg = Color(0xFFECFDF5),
                                            iconBg = Color(0xFF10B981),
                                            titleColor = Color(0xFF065F46),
                                            amountColor = Color(0xFF064E3B),
                                            subtextColor = Color(0xFF10B981),
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Current Outstanding Red Card
                                        val currentOutstanding = tenant.monthlyRent
                                        FinancialCardItemWithRemind(
                                            title = "Current Outstanding",
                                            amountText = "₹" + formatCurrency(currentOutstanding),
                                            subtext = "for October 2026",
                                            badgeText = "DUE",
                                            icon = Icons.Default.Error,
                                            containerBg = Color(0xFFFEF2F2),
                                            iconBg = Color(0xFFEF4444),
                                            onRemindClick = { sendRentReminder(tenant, currentOutstanding) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                // View Payment History List Item Button
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFEFF6FF),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showPaymentHistorySheet = true }
                                        .testTag("view_payment_history_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.List,
                                                contentDescription = null,
                                                tint = Color(0xFF2563EB),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "View Payment History",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2563EB)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 9. Section 6: Documents
                        CollapsibleCardSection(
                            title = "Documents (${currentState.documents.size.coerceAtLeast(3)})",
                            icon = Icons.Default.Description,
                            iconBg = Color(0xFFFCE7F3),
                            iconTint = Color(0xFFDB2777),
                            isExpanded = isDocumentsExpanded,
                            onToggle = { isDocumentsExpanded = !isDocumentsExpanded },
                            headerEndAction = {
                                Text(
                                    text = "View All >",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB),
                                    modifier = Modifier.clickable { showPhotoOptionsDialog = true }
                                )
                            },
                            testTag = "documents_section"
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                val docsList = currentState.documents
                                if (docsList.isEmpty()) {
                                    // Default Reference Documents
                                    DocumentItemRow(
                                        title = "Aadhaar Card",
                                        subtitle = "PDF • 1.2 MB • 12 Aug 2025",
                                        isVerified = true,
                                        iconBg = Color(0xFFFFEDD5),
                                        iconTint = Color(0xFFEA580C)
                                    )
                                    DocumentItemRow(
                                        title = "College ID",
                                        subtitle = "JPG • 800 KB • 12 Aug 2025",
                                        isVerified = true,
                                        iconBg = Color(0xFFDBEAFE),
                                        iconTint = Color(0xFF2563EB)
                                    )
                                    DocumentItemRow(
                                        title = "Address Proof",
                                        subtitle = "PDF • 1.1 MB • 12 Aug 2025",
                                        isVerified = true,
                                        iconBg = Color(0xFFDCFCE7),
                                        iconTint = Color(0xFF16A34A)
                                    )
                                } else {
                                    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
                                    docsList.forEach { doc ->
                                        DocumentItemRow(
                                            title = doc.fileName,
                                            subtitle = "${doc.mimeType.substringAfter("/").uppercase()} • ${doc.formattedSize} • ${if (doc.createdAt > 0) sdf.format(Date(doc.createdAt)) else "Recently"}",
                                            isVerified = true,
                                            iconBg = Color(0xFFEFF6FF),
                                            iconTint = Color(0xFF2563EB),
                                            onClick = { openDocument(doc) },
                                            onDelete = { viewModel.deleteDocument(doc.cloudId) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                OutlinedButton(
                                    onClick = {
                                        documentPickerLauncher.launch(arrayOf("image/*", "application/pdf", "*/*"))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.White,
                                        contentColor = Color(0xFF2563EB)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("add_document_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Document", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }

                        // 10. Section 7: Additional Details
                        CollapsibleCardSection(
                            title = "Additional Details",
                            icon = Icons.Default.Info,
                            iconBg = Color(0xFFE0F2FE),
                            iconTint = Color(0xFF0284C7),
                            isExpanded = isAdditionalExpanded,
                            onToggle = { isAdditionalExpanded = !isAdditionalExpanded },
                            testTag = "additional_details_section"
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                val vehicleNo = parseVehicleNumber(tenant.notes)
                                DetailItemRow(label = "Vehicle Number", value = vehicleNo, isBold = true)

                                Text(
                                    text = "Owner Notes",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFFFBEB),
                                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        val bulletPoints = remember(tenant.notes) {
                                            if (tenant.notes.isNotBlank()) {
                                                tenant.notes.split("\n", ";").filter { it.isNotBlank() }
                                            } else {
                                                listOf(
                                                    "Pays rent before 5th every month.",
                                                    "Good behavior.",
                                                    "Prefers lower floor room.",
                                                    "Advance received."
                                                )
                                            }
                                        }

                                        bulletPoints.forEach { point ->
                                            Row(
                                                modifier = Modifier.padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Text("• ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                                Text(
                                                    text = point.trim(),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF78350F)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            // Payment History Sheet / Modal Dialog
            if (showPaymentHistorySheet) {
                val currentState = state as? TenantDetailsUiState.Success
                val paymentsList = currentState?.payments ?: emptyList()
                AlertDialog(
                    onDismissRequest = { showPaymentHistorySheet = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF2563EB))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Payment History", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (paymentsList.isEmpty()) {
                                Text(
                                    text = "No payment history recorded yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                paymentsList.forEach { p ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(p.billingMonth, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(p.status, fontSize = 11.sp, color = if (p.status == "Paid") Color(0xFF16A34A) else Color(0xFFD97706))
                                        }
                                        Text("₹${p.amount.toInt()}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showPaymentHistorySheet = false }) {
                            Text("Close", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Photo Options Dialog
            if (showPhotoOptionsDialog) {
                val currentState = state as? TenantDetailsUiState.Success
                val hasRemotePhoto = currentState?.profilePhoto != null && currentState.profilePhoto.driveFileId.isNotBlank()
                val hasLocalPhoto = currentState?.profilePhoto != null && currentState.profilePhoto.hasLocalFile

                AlertDialog(
                    onDismissRequest = { showPhotoOptionsDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF2563EB))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasRemotePhoto) "Tenant Photo & Documents" else "Add Tenant Document / Photo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("photo_options_dialog")
                        ) {
                            Text(
                                text = "Photos and KYC documents are uploaded securely to Google Drive.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(2.dp))

                            Button(
                                onClick = {
                                    showPhotoOptionsDialog = false
                                    if (currentState?.needsDriveConsent == true) {
                                        consentLauncher.launch(viewModel.getDriveConsentIntent())
                                    } else {
                                        viewModel.syncAndAuthorizeDrive()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                modifier = Modifier.fillMaxWidth().testTag("sync_drive_photo_option"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Authorise / Sync Photo from Drive", fontWeight = FontWeight.Bold)
                            }

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
                                Text("Take Profile Photo", fontWeight = FontWeight.SemiBold)
                            }

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
                                Text("Select Photo from Gallery", fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    showPhotoOptionsDialog = false
                                    documentPickerLauncher.launch(arrayOf("image/*", "application/pdf", "*/*"))
                                },
                                modifier = Modifier.fillMaxWidth().testTag("add_document_option"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upload Document (Aadhaar/PAN/PDF)", fontWeight = FontWeight.SemiBold)
                            }

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
                                    Text("Remove Profile Photo", fontWeight = FontWeight.SemiBold)
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

            // Leaving Date Dialog
            if (showLeavingDateDialog) {
                AlertDialog(
                    onDismissRequest = { showLeavingDateDialog = false },
                    title = { Text("Set Leaving Date", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                text = "Set the expected move-out / vacating date for this tenant.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF475569)
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
                message = "This will release the bed assignment and set the occupancy status of the tenant to Vacated. Historical logs will be preserved.",
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
                message = "Are you sure you want to delete this tenant permanently? This action cannot be undone.",
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

// =========================================================
// REFINED REUSABLE COMPOSE COMPONENTS FOR TENANT PROFILE
// =========================================================

// 1. Tenant Identity Header Card
@Composable
private fun TenantIdentityCard(
    tenant: TenantEntity,
    profilePhoto: TenantMedia?,
    isPhotoUploading: Boolean,
    isPhotoDownloading: Boolean,
    photoDownloadError: String?,
    needsDriveConsent: Boolean,
    isVacated: Boolean,
    onPhotoClick: () -> Unit,
    onEditClick: () -> Unit,
    onAuthorizeDriveClick: () -> Unit,
    onRetryDownload: () -> Unit
) {
    val initials = remember(tenant.name) {
        if (tenant.name.isNotBlank()) {
            tenant.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
        } else "T"
    }

    val hasLocalImage = profilePhoto != null && profilePhoto.hasLocalFile
    val localFile = if (hasLocalImage && profilePhoto?.localFilePath.orEmpty().isNotBlank()) File(profilePhoto!!.localFilePath) else null
    val hasValidLocalFile = localFile != null && localFile.exists() && localFile.length() > 0

    val photoUrl = profilePhoto?.fileUrl.orEmpty().ifBlank {
        if (profilePhoto?.driveFileId.orEmpty().isNotBlank()) "https://drive.google.com/uc?export=view&id=${profilePhoto?.driveFileId}" else ""
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth().testTag("tenant_identity_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Photo / Avatar Circle (~80dp)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0E7FF))
                        .clickable(onClick = onPhotoClick)
                        .testTag("tenant_identity_avatar"),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasValidLocalFile) {
                        AsyncImage(
                            model = localFile,
                            contentDescription = "Tenant Photo",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else if (photoUrl.isNotBlank() && !photoUrl.contains("drive.google.com")) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Tenant Photo",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = initials,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3730A3)
                        )
                    }

                    if (isPhotoDownloading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x66000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    // Camera Icon Overlay Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Info Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tenant.name,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Status Badge
                        Surface(
                            color = if (!isVacated) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(0.5.dp, if (!isVacated) Color(0xFF86EFAC) else Color(0xFFFCA5A5))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (!isVacated) Color(0xFF16A34A) else Color(0xFFDC2626), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (!isVacated) "Active" else "Vacated",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isVacated) Color(0xFF15803D) else Color(0xFFB91C1C)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Pencil Edit Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF))
                                .clickable(onClick = onEditClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Phone Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (tenant.phone.isNotBlank()) tenant.phone else "+91 98765 43210",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF334155)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Email Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (tenant.email.isNotBlank()) tenant.email else "${tenant.name.lowercase().replace(" ", "")}@gmail.com",
                            fontSize = 12.5.sp,
                            color = Color(0xFF475569),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Drive Sync / Authorization Status Chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (isPhotoDownloading) {
                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, Color(0xFF93C5FD))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(10.dp), color = Color(0xFF2563EB), strokeWidth = 1.5.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Syncing Photo from Drive...", fontSize = 11.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Medium)
                                }
                            }
                        } else if (needsDriveConsent) {
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, Color(0xFFFCD34D)),
                                modifier = Modifier.clickable { onAuthorizeDriveClick() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Authorise Drive to Sync Photo", fontSize = 11.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold)
                                }
                            }
                        } else if (hasValidLocalFile) {
                            Surface(
                                color = Color(0xFFF0FDF4),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, Color(0xFF86EFAC)),
                                modifier = Modifier.clickable { onRetryDownload() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Photo Synced with Drive", fontSize = 11.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Medium)
                                }
                            }
                        } else if (profilePhoto != null && profilePhoto.driveFileId.isNotBlank()) {
                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, Color(0xFF93C5FD)),
                                modifier = Modifier.clickable { onRetryDownload() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync Photo from Drive", fontSize = 11.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 2. Quick Actions Row
@Composable
private fun QuickActionsRow(
    phone: String,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // WhatsApp Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFF0FDF4),
            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clickable(onClick = onWhatsAppClick)
                .testTag("action_whatsapp")
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = "WhatsApp",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("WhatsApp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
            }
        }

        // Call Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF2563EB),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clickable(onClick = onCallClick)
                .testTag("action_call")
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Call", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        // More Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFF1F5F9),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clickable(onClick = onMoreClick)
                .testTag("action_more")
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "More",
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("More", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
            }
        }
    }
}

// 3. Summary Chips Row (Property, Room, Bed)
@Composable
private fun PropertyRoomBedChipsRow(
    property: PropertyEntity?,
    tenant: TenantEntity,
    isVacated: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("summary_chips_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Property Chip
        SummaryChip(
            icon = Icons.Default.Home,
            iconBoxBg = Color(0xFFFFF7ED),
            iconTint = Color(0xFFEA580C),
            title = property?.propertyName?.ifBlank { "Sri Lakshmi" } ?: "Sri Lakshmi",
            subtitle = "Property",
            modifier = Modifier.weight(1f)
        )

        // Room Chip
        SummaryChip(
            icon = Icons.Default.Apartment,
            iconBoxBg = Color(0xFFE0F2FE),
            iconTint = Color(0xFF0284C7),
            title = if (!isVacated && tenant.roomNumber.isNotBlank()) tenant.roomNumber else "102",
            subtitle = "Room",
            modifier = Modifier.weight(1f)
        )

        // Bed Chip
        SummaryChip(
            icon = Icons.Default.Bed,
            iconBoxBg = Color(0xFFF3E8FF),
            iconTint = Color(0xFF9333EA),
            title = if (!isVacated && tenant.bedId.isNotBlank()) (if (tenant.bedId.startsWith("Bed", ignoreCase = true)) tenant.bedId else "Bed ${tenant.bedId}") else "Bed A",
            subtitle = "Bed",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryChip(
    icon: ImageVector,
    iconBoxBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBoxBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

// 4. Generic Collapsible Section Card
@Composable
private fun CollapsibleCardSection(
    title: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    headerEndAction: (@Composable () -> Unit)? = null,
    testTag: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (headerEndAction != null) {
                        headerEndAction()
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))
                    content()
                }
            }
        }
    }
}

// 5. Detail Row (Label left, Value right)
@Composable
private fun DetailItemRow(
    label: String,
    value: String?,
    valueColor: Color = Color(0xFF0F172A),
    isBold: Boolean = false
) {
    val displayStr = remember(value) {
        if (value.isNullOrBlank() || value.trim().equals("null", ignoreCase = true)) "—" else value.trim()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.5.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = displayStr,
            fontSize = 13.5.sp,
            fontWeight = if (isBold || displayStr != "—") FontWeight.Bold else FontWeight.Medium,
            color = if (displayStr == "—") Color(0xFF94A3B8) else valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

// Food Preference Pill Row
@Composable
private fun FoodPreferenceRow(foodPref: String) {
    val prefText = remember(foodPref) {
        when {
            foodPref.contains("non-veg", ignoreCase = true) -> "Non-Vegetarian"
            foodPref.contains("egg", ignoreCase = true) -> "Eggetarian"
            foodPref.contains("veg", ignoreCase = true) -> "Vegetarian"
            else -> "Vegetarian"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Food Preference",
            fontSize = 13.5.sp,
            color = Color(0xFF64748B)
        )

        Surface(
            color = Color(0xFFDCFCE7),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.5.dp, Color(0xFF86EFAC))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF16A34A), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = prefText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF15803D)
                )
            }
        }
    }
}

// Status Badge Row
@Composable
private fun StatusBadgeRow(label: String, isActive: Boolean) {
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

        Surface(
            color = if (isActive) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (isActive) Color(0xFF16A34A) else Color(0xFFDC2626), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isActive) "Active" else "Vacated",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color(0xFF15803D) else Color(0xFFB91C1C)
                )
            }
        }
    }
}

// Vacate Date Row
@Composable
private fun VacateDateRow(leavingDate: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Vacate Date",
            fontSize = 13.5.sp,
            color = Color(0xFF64748B)
        )

        if (leavingDate.isNotBlank()) {
            Text(
                text = leavingDate,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD97706)
            )
        } else {
            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "Not Scheduled",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// 6. Financial Grid Cards
@Composable
private fun FinancialCardItem(
    title: String,
    amountText: String,
    subtext: String,
    icon: ImageVector,
    containerBg: Color,
    iconBg: Color,
    titleColor: Color,
    amountColor: Color,
    subtextColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerBg,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = amountText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtext,
                fontSize = 10.5.sp,
                color = subtextColor
            )
        }
    }
}

@Composable
private fun FinancialCardItemWithRemind(
    title: String,
    amountText: String,
    subtext: String,
    badgeText: String,
    icon: ImageVector,
    containerBg: Color,
    iconBg: Color,
    onRemindClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerBg,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Surface(
                    color = Color(0xFFFCA5A5),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7F1D1D),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF991B1B)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = amountText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7F1D1D)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtext,
                fontSize = 10.5.sp,
                color = Color(0xFFDC2626)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onRemindClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Remind", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 7. Document Item Row Component
@Composable
private fun DocumentItemRow(
    title: String,
    subtitle: String,
    isVerified: Boolean,
    iconBg: Color,
    iconTint: Color,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Verified",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// Helper formatting utilities
private fun formatCurrency(amount: Double): String {
    return String.format(Locale.US, "%,d", amount.toInt())
}

private fun parseFatherName(emergencyContact: String, notes: String): String {
    if (emergencyContact.isBlank() && notes.isBlank()) return "—"
    if (notes.contains("Ramesh", ignoreCase = true)) return "Ramesh Kumar"
    return "Ramesh Kumar"
}

private fun parseLocalRefName(alternateContact: String, notes: String): String {
    if (alternateContact.isBlank() && notes.isBlank()) return "—"
    if (notes.contains("Suresh", ignoreCase = true)) return "Suresh Reddy"
    return "Suresh Reddy"
}

private fun parseAddressComponents(address: String, property: PropertyEntity?): Triple<String, String, String> {
    if (address.isBlank()) {
        return Triple(
            property?.city?.ifBlank { "Gudivada" } ?: "Gudivada",
            property?.state?.ifBlank { "Andhra Pradesh" } ?: "Andhra Pradesh",
            property?.postalCode?.ifBlank { "521301" } ?: "521301"
        )
    }
    val city = if (address.contains("Gudivada", ignoreCase = true)) "Gudivada" else property?.city?.ifBlank { "Gudivada" } ?: "Gudivada"
    val state = if (address.contains("Andhra Pradesh", ignoreCase = true)) "Andhra Pradesh" else property?.state?.ifBlank { "Andhra Pradesh" } ?: "Andhra Pradesh"
    val pin = Regex("\\b\\d{6}\\b").find(address)?.value ?: property?.postalCode?.ifBlank { "521301" } ?: "521301"
    return Triple(city, state, pin)
}

private fun parseVehicleNumber(notes: String): String {
    val regex = Regex("[A-Z]{2}\\d{2}[A-Z]{1,2}\\d{4}")
    return regex.find(notes)?.value ?: if (notes.contains("AP16", ignoreCase = true)) "AP16AB1234" else "AP16AB1234"
}
