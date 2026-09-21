package com.example.features.tenants

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.LocalSpacing
import com.example.features.tenants.ui.viewmodel.AddTenantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTenantScreen(
    viewModel: AddTenantViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showGenderMenu by remember { mutableStateOf(false) }
    var showRoomMenu by remember { mutableStateOf(false) }
    var showBedMenu by remember { mutableStateOf(false) }
    var showKycMenu by remember { mutableStateOf(false) }
    var isCustomRoom by remember { mutableStateOf(false) }

    // Trigger action on success
    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onBackClick()
        }
    }

    // Show snackbar and scroll up on error
    LaunchedEffect(state.error) {
        state.error?.let { msg ->
            scrollState.animateScrollTo(0)
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Onboard New Tenant", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("add_tenant_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.testTag("add_tenant_top_bar")
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize().testTag("add_tenant_screen_container")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(spacing.medium)
            ) {
                // Error Alert
                state.error?.let { errorMsg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = spacing.medium)
                            .testTag("onboard_error_alert")
                    ) {
                        Row(
                            modifier = Modifier.padding(spacing.large),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(spacing.medium))
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // CATEGORY 1: PERSONAL DETAILS
                Text(
                    text = "Personal Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = spacing.small)
                )

                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.onNameChanged(it) },
                    label = { Text("Full Name *") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small).testTag("onboard_name_input"),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    OutlinedTextField(
                        value = state.phone,
                        onValueChange = { viewModel.onPhoneChanged(it) },
                        label = { Text("Mobile Number *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f).testTag("onboard_phone_input"),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = state.alternateContact,
                        onValueChange = { viewModel.onAlternateContactChanged(it) },
                        label = { Text("Alt Contact") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f).testTag("onboard_alt_phone_input"),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    OutlinedTextField(
                        value = state.emergencyContact,
                        onValueChange = { viewModel.onEmergencyContactChanged(it) },
                        label = { Text("Emergency Contact") },
                        placeholder = { Text("10-digit number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f).testTag("onboard_emergency_contact_input"),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = { viewModel.onEmailChanged(it) },
                        label = { Text("Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.weight(1f).testTag("onboard_email_input"),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    OutlinedTextField(
                        value = state.dob,
                        onValueChange = { viewModel.onDobChanged(it) },
                        label = { Text("DOB (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f).testTag("onboard_dob_input"),
                        singleLine = true
                    )

                    // Gender Selector Dropdown
                    ExposedDropdownMenuBox(
                        expanded = showGenderMenu,
                        onExpandedChange = { showGenderMenu = !showGenderMenu },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = state.gender,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Gender") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showGenderMenu) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag("onboard_gender_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = showGenderMenu,
                            onDismissRequest = { showGenderMenu = false }
                        ) {
                            listOf("Male", "Female", "Other").forEach { genderOption ->
                                DropdownMenuItem(
                                    text = { Text(genderOption) },
                                    onClick = {
                                        viewModel.onGenderChanged(genderOption)
                                        showGenderMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    OutlinedTextField(
                        value = state.occupation,
                        onValueChange = { viewModel.onOccupationChanged(it) },
                        label = { Text("Occupation") },
                        modifier = Modifier.weight(1f).testTag("onboard_occupation_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.companyOrCollege,
                        onValueChange = { viewModel.onCompanyOrCollegeChanged(it) },
                        label = { Text("Company / College") },
                        modifier = Modifier.weight(1f).testTag("onboard_company_input"),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = state.address,
                    onValueChange = { viewModel.onAddressChanged(it) },
                    label = { Text("Permanent Address") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.large).testTag("onboard_address_input"),
                    maxLines = 3
                )

                // CATEGORY 2: RENTAL SETUP & ALLOCATION
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rental Setup & Room Allocation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (state.rooms.isNotEmpty()) {
                        TextButton(
                            onClick = { isCustomRoom = !isCustomRoom },
                            modifier = Modifier.testTag("toggle_custom_room_button")
                        ) {
                            Text(if (isCustomRoom) "Select from List" else "Custom Room")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    // Room Assignment
                    if (state.rooms.isEmpty() || isCustomRoom) {
                        OutlinedTextField(
                            value = state.roomNumber,
                            onValueChange = { viewModel.onRoomChanged(it) },
                            label = { Text("Room No. *") },
                            placeholder = { Text("e.g. 101") },
                            modifier = Modifier.weight(1f).testTag("onboard_room_input"),
                            singleLine = true
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = showRoomMenu,
                            onExpandedChange = { showRoomMenu = !showRoomMenu },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = if (state.roomNumber.isBlank()) "Select Room" else "Room ${state.roomNumber}",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Room Assignment *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRoomMenu) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag("onboard_room_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = showRoomMenu,
                                onDismissRequest = { showRoomMenu = false }
                            ) {
                                state.rooms.forEach { room ->
                                    DropdownMenuItem(
                                        text = { Text("Room ${room.roomNumber} (${room.floor} - Capacity: ${room.capacity})") },
                                        onClick = {
                                            viewModel.onRoomChanged(room.roomNumber)
                                            showRoomMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Bed Assignment
                    if (state.availableBeds.isEmpty()) {
                        OutlinedTextField(
                            value = state.bedId,
                            onValueChange = { viewModel.onBedChanged(it) },
                            label = { Text("Bed No. *") },
                            placeholder = { Text("e.g. Bed A") },
                            modifier = Modifier.weight(1f).testTag("onboard_bed_input"),
                            singleLine = true
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = showBedMenu,
                            onExpandedChange = { if (state.roomNumber.isNotBlank()) showBedMenu = !showBedMenu },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = if (state.bedId.isBlank()) "Select Bed" else state.bedId,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Bed Assignment *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showBedMenu) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag("onboard_bed_dropdown"),
                                enabled = state.roomNumber.isNotBlank()
                            )
                            ExposedDropdownMenu(
                                expanded = showBedMenu,
                                onDismissRequest = { showBedMenu = false }
                            ) {
                                state.availableBeds.forEach { bed ->
                                    DropdownMenuItem(
                                        text = { Text(bed) },
                                        onClick = {
                                            viewModel.onBedChanged(bed)
                                            showBedMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    OutlinedTextField(
                        value = state.monthlyRent,
                        onValueChange = { viewModel.onMonthlyRentChanged(it) },
                        label = { Text("Monthly Rent (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("onboard_rent_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.securityDeposit,
                        onValueChange = { viewModel.onSecurityDepositChanged(it) },
                        label = { Text("Deposit (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("onboard_deposit_input"),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.large),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    OutlinedTextField(
                        value = state.advancePaid,
                        onValueChange = { viewModel.onAdvancePaidChanged(it) },
                        label = { Text("Advance Paid (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("onboard_advance_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.moveInDate,
                        onValueChange = { viewModel.onMoveInDateChanged(it) },
                        label = { Text("Move-in Date *") },
                        modifier = Modifier.weight(1f).testTag("onboard_move_in_date_input"),
                        singleLine = true
                    )
                }

                // CATEGORY 3: DOCUMENTS, IDENTIFICATION & NOTES
                Text(
                    text = "Identification & Documents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = spacing.small)
                )

                // KYC Document Selector
                ExposedDropdownMenuBox(
                    expanded = showKycMenu,
                    onExpandedChange = { showKycMenu = !showKycMenu },
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small)
                ) {
                    OutlinedTextField(
                        value = state.kycDocType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("KYC Document Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showKycMenu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag("onboard_kyc_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = showKycMenu,
                        onDismissRequest = { showKycMenu = false }
                    ) {
                        listOf("Aadhaar Card", "PAN Card", "Driving License", "Passport", "None").forEach { doc ->
                            DropdownMenuItem(
                                text = { Text(doc) },
                                onClick = {
                                    viewModel.onKycDocTypeChanged(doc)
                                    showKycMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { viewModel.onNotesChanged(it) },
                    label = { Text("Additional Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.large).testTag("onboard_notes_input"),
                    maxLines = 4
                )

                // Save Action Button
                Button(
                    onClick = { viewModel.saveTenant() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("onboard_save_button"),
                    enabled = !state.isLoading,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(80.dp)
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text("Register & Allocate Bed", fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
