package com.example.features.googleform.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.language.rememberTranslation
import com.example.features.googleform.domain.model.PendingRegistrationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingRegistrationDetailScreen(
    viewModel: PendingRegistrationDetailViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectionReasonText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearMessages()
        }
    }

    val titleText = rememberTranslation("Registration Details")
    val personalInfoText = rememberTranslation("Personal Information")
    val emergencyContactText = rememberTranslation("Emergency Contact")
    val addressInfoText = rememberTranslation("Address Details")
    val professionalInfoText = rememberTranslation("Occupation & Profile")
    val additionalNotesText = rememberTranslation("Additional Information / Notes")
    val duplicateWarningTitle = rememberTranslation("Duplicate Warning")
    val editDetailsText = rememberTranslation("Edit Details")
    val saveEditsText = rememberTranslation("Save Changes")
    val cancelEditText = rememberTranslation("Cancel")
    val rejectText = rememberTranslation("Reject Registration")
    val acceptAssignText = rememberTranslation("Accept & Assign Room")
    val assignRoomTitle = rememberTranslation("Assign Room & Bed")
    val confirmOnboardText = rememberTranslation("Confirm & Onboard Tenant")

    val formattedDate = remember(uiState.registration?.submittedAt) {
        try {
            val ts = uiState.registration?.submittedAt ?: 0L
            if (ts > 0) {
                SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(ts))
            } else "N/A"
        } catch (_: Exception) { "N/A" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titleText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = Color(0xFF0F172A)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    val reg = uiState.registration
                    if (reg != null && reg.isPending) {
                        if (uiState.isEditMode) {
                            IconButton(
                                onClick = { viewModel.saveEdits() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_edits_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.toggleEditMode() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("edit_registration_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    com.example.core.language.GlobalLanguageToggle(
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                modifier = Modifier.testTag("registration_detail_top_bar")
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.fillMaxSize().testTag("pending_registration_detail_screen")
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val reg = uiState.registration
            if (reg == null) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text("Registration not found", color = Color(0xFF64748B))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Status & Header Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RegistrationStatusBadge(status = reg.status)
                                Text(
                                    text = formattedDate,
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(
                                            Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = reg.fullName.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = reg.fullName.ifBlank { "Tenant Submitter" },
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = reg.phone,
                                        fontSize = 14.sp,
                                        color = Color(0xFF475569),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (reg.status == PendingRegistrationStatus.ACCEPTED && reg.createdTenantCloudId.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Onboarded as Active Tenant in PG Management",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D)
                                        )
                                    }
                                }
                            }

                            if (reg.status == PendingRegistrationStatus.REJECTED && reg.rejectionReason.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = Color(0xFFFEE2E2),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Reason: ${reg.rejectionReason}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF991B1B)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Duplicate Warning Alert
                    if (reg.hasDuplicateWarning) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = duplicateWarningTitle,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "A tenant matching this mobile number already exists in the system: ${reg.duplicateMatchedTenantName} (${reg.duplicateMatchedTenantStatus}). Please verify before approving.",
                                        fontSize = 13.sp,
                                        color = Color(0xFFB45309),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }

                    // 3. Personal Information Card
                    SectionDetailCard(
                        title = personalInfoText,
                        icon = Icons.Default.Person
                    ) {
                        if (uiState.isEditMode) {
                            OutlinedTextField(
                                value = uiState.fullName,
                                onValueChange = { viewModel.onFullNameChanged(it) },
                                label = { Text("Full Name *") },
                                modifier = Modifier.fillMaxWidth().testTag("edit_full_name")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = uiState.phone,
                                onValueChange = { viewModel.onPhoneChanged(it) },
                                label = { Text("Mobile Number *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth().testTag("edit_phone")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = uiState.email,
                                onValueChange = { viewModel.onEmailChanged(it) },
                                label = { Text("Email Address") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth().testTag("edit_email")
                            )
                        } else {
                            DetailRow(label = "Full Name", value = reg.fullName)
                            DetailRow(label = "Mobile Number", value = reg.phone)
                            DetailRow(label = "Email Address", value = reg.email.ifBlank { "Not provided" })
                        }
                    }

                    // 4. Emergency Contact Card
                    SectionDetailCard(
                        title = emergencyContactText,
                        icon = Icons.Default.ContactPhone
                    ) {
                        if (uiState.isEditMode) {
                            OutlinedTextField(
                                value = uiState.emergencyName,
                                onValueChange = { viewModel.onEmergencyNameChanged(it) },
                                label = { Text("Emergency Contact Name *") },
                                modifier = Modifier.fillMaxWidth().testTag("edit_emergency_name")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = uiState.emergencyPhone,
                                onValueChange = { viewModel.onEmergencyPhoneChanged(it) },
                                label = { Text("Emergency Contact Mobile *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth().testTag("edit_emergency_phone")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = uiState.emergencyRelation,
                                onValueChange = { viewModel.onEmergencyRelationChanged(it) },
                                label = { Text("Relationship *") },
                                modifier = Modifier.fillMaxWidth().testTag("edit_emergency_relation")
                            )
                        } else {
                            DetailRow(label = "Emergency Contact Name", value = reg.emergencyName)
                            DetailRow(label = "Emergency Mobile", value = reg.emergencyPhone)
                            DetailRow(label = "Relationship", value = reg.emergencyRelation)
                        }
                    }

                    // 5. Address Details Card
                    SectionDetailCard(
                        title = addressInfoText,
                        icon = Icons.Default.Home
                    ) {
                        if (uiState.isEditMode) {
                            OutlinedTextField(
                                value = uiState.permanentAddress,
                                onValueChange = { viewModel.onPermanentAddressChanged(it) },
                                label = { Text("Permanent Address *") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth().testTag("edit_permanent_address")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = uiState.currentAddress,
                                onValueChange = { viewModel.onCurrentAddressChanged(it) },
                                label = { Text("Current / Local Address") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth().testTag("edit_current_address")
                            )
                        } else {
                            DetailRow(label = "Permanent Address", value = reg.permanentAddress)
                            DetailRow(label = "Current / Local Address", value = reg.currentAddress.ifBlank { "Same as permanent" })
                        }
                    }

                    // 6. Professional & Profile Card
                    SectionDetailCard(
                        title = professionalInfoText,
                        icon = Icons.Default.Business
                    ) {
                        if (uiState.isEditMode) {
                            OutlinedTextField(
                                value = uiState.occupation,
                                onValueChange = { viewModel.onOccupationChanged(it) },
                                label = { Text("Occupation / Profile") },
                                modifier = Modifier.fillMaxWidth().testTag("edit_occupation")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = uiState.organization,
                                onValueChange = { viewModel.onOrganizationChanged(it) },
                                label = { Text("Company / College Name") },
                                modifier = Modifier.fillMaxWidth().testTag("edit_organization")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = uiState.expectedJoiningDate,
                                onValueChange = { viewModel.onExpectedJoiningDateChanged(it) },
                                label = { Text("Expected Joining Date (YYYY-MM-DD)") },
                                modifier = Modifier.fillMaxWidth().testTag("edit_joining_date")
                            )
                        } else {
                            DetailRow(label = "Occupation", value = reg.occupation.ifBlank { "Not provided" })
                            DetailRow(label = "Company / College", value = reg.organization.ifBlank { "Not provided" })
                            DetailRow(label = "Expected Joining Date", value = reg.expectedJoiningDate.ifBlank { "Immediate" })
                        }
                    }

                    // 7. Additional Notes Card
                    SectionDetailCard(
                        title = additionalNotesText,
                        icon = Icons.Default.Notes
                    ) {
                        if (uiState.isEditMode) {
                            OutlinedTextField(
                                value = uiState.notes,
                                onValueChange = { viewModel.onNotesChanged(it) },
                                label = { Text("Additional Notes") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth().testTag("edit_notes")
                            )
                        } else {
                            DetailRow(label = "Notes & Preferences", value = reg.notes.ifBlank { "None" })
                        }
                    }

                    // 8. Action Buttons (Accept / Reject / Save Edits)
                    if (reg.isPending) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (uiState.isEditMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.toggleEditMode() },
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text(cancelEditText)
                                }
                                Button(
                                    onClick = { viewModel.saveEdits() },
                                    enabled = !uiState.isSubmitting,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("save_changes_button")
                                ) {
                                    if (uiState.isSubmitting) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                    } else {
                                        Text(saveEditsText)
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showRejectDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("detail_reject_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reject", fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = { viewModel.openAssignDialog() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(2f)
                                        .height(50.dp)
                                        .testTag("detail_accept_assign_button")
                                ) {
                                    Icon(imageVector = Icons.Default.MeetingRoom, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(acceptAssignText, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Assign Room & Onboard Dialog
    // -------------------------------------------------------------
    if (uiState.showAssignDialog) {
        var roomExpanded by remember { mutableStateOf(false) }
        var bedExpanded by remember { mutableStateOf(false) }
        var genderExpanded by remember { mutableStateOf(false) }
        var kycExpanded by remember { mutableStateOf(false) }

        val genderOptions = listOf("Male", "Female", "Other")
        val kycOptions = listOf("Aadhaar Card", "PAN Card", "Driving License", "Passport", "Voter ID", "None")

        AlertDialog(
            onDismissRequest = { viewModel.closeAssignDialog() },
            title = {
                Text(text = assignRoomTitle, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Assign room and bed for ${uiState.registration?.fullName}",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    // Room Dropdown
                    ExposedDropdownMenuBox(
                        expanded = roomExpanded,
                        onExpandedChange = { roomExpanded = !roomExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = if (uiState.selectedRoomNumber.isNotBlank()) "Room ${uiState.selectedRoomNumber}" else "Select Room",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Room *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roomExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("assign_room_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = roomExpanded,
                            onDismissRequest = { roomExpanded = false }
                        ) {
                            uiState.rooms.forEach { room ->
                                DropdownMenuItem(
                                    text = { Text("Room ${room.roomNumber} (${room.floor}, ${room.capacity} beds)") },
                                    onClick = {
                                        viewModel.onRoomSelected(room.roomNumber)
                                        roomExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Bed Dropdown
                    ExposedDropdownMenuBox(
                        expanded = bedExpanded,
                        onExpandedChange = { bedExpanded = !bedExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = uiState.selectedBedId.ifBlank { "Select Bed" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Bed Assignment *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bedExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("assign_bed_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = bedExpanded,
                            onDismissRequest = { bedExpanded = false }
                        ) {
                            uiState.availableBeds.forEach { bed ->
                                DropdownMenuItem(
                                    text = { Text(bed) },
                                    onClick = {
                                        viewModel.onBedSelected(bed)
                                        bedExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Monthly Rent & Security Deposit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.monthlyRent,
                            onValueChange = { viewModel.onMonthlyRentChanged(it) },
                            label = { Text("Monthly Rent (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("assign_monthly_rent")
                        )
                        OutlinedTextField(
                            value = uiState.securityDeposit,
                            onValueChange = { viewModel.onSecurityDepositChanged(it) },
                            label = { Text("Deposit (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("assign_security_deposit")
                        )
                    }

                    // Move-in Date
                    OutlinedTextField(
                        value = uiState.moveInDate,
                        onValueChange = { viewModel.onMoveInDateChanged(it) },
                        label = { Text("Move-in Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth().testTag("assign_move_in_date")
                    )

                    // Gender Dropdown
                    ExposedDropdownMenuBox(
                        expanded = genderExpanded,
                        onExpandedChange = { genderExpanded = !genderExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = uiState.gender,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Gender") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = genderExpanded,
                            onDismissRequest = { genderExpanded = false }
                        ) {
                            genderOptions.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(g) },
                                    onClick = {
                                        viewModel.onGenderChanged(g)
                                        genderExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // KYC Document Type
                    ExposedDropdownMenuBox(
                        expanded = kycExpanded,
                        onExpandedChange = { kycExpanded = !kycExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = uiState.kycDocType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("KYC Document") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kycExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = kycExpanded,
                            onDismissRequest = { kycExpanded = false }
                        ) {
                            kycOptions.forEach { doc ->
                                DropdownMenuItem(
                                    text = { Text(doc) },
                                    onClick = {
                                        viewModel.onKycDocTypeChanged(doc)
                                        kycExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.acceptRegistration() },
                    enabled = !uiState.isSubmitting,
                    modifier = Modifier.testTag("confirm_assign_button")
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    } else {
                        Text(confirmOnboardText)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeAssignDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // -------------------------------------------------------------
    // Rejection Dialog
    // -------------------------------------------------------------
    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = {
                showRejectDialog = false
                rejectionReasonText = ""
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF4444)
                )
            },
            title = {
                Text("Reject Registration", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Enter a reason for rejecting this registration submission:", fontSize = 14.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rejectionReasonText,
                        onValueChange = { rejectionReasonText = it },
                        placeholder = { Text("e.g. No beds available, incomplete details") },
                        modifier = Modifier.fillMaxWidth().testTag("detail_rejection_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rejectRegistration(rejectionReasonText)
                        showRejectDialog = false
                        rejectionReasonText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier.testTag("confirm_detail_rejection_button")
                ) {
                    Text("Reject", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SectionDetailCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color(0xFF1E293B),
            fontWeight = FontWeight.SemiBold
        )
    }
}
