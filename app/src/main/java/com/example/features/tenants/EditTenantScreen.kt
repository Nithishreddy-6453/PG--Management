package com.example.features.tenants

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.core.language.GlobalLanguageToggle
import com.example.core.language.rememberTranslation
import com.example.features.tenants.ui.viewmodel.EditTenantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTenantScreen(
    viewModel: EditTenantViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var attachedIdProof by remember { mutableStateOf<String?>(null) }
    var attachedAddressProof by remember { mutableStateOf<String?>(null) }
    var attachedPhotoDoc by remember { mutableStateOf<String?>(null) }
    var attachedOtherDoc by remember { mutableStateOf<String?>(null) }

    var roomExpanded by remember { mutableStateOf(false) }
    var bedExpanded by remember { mutableStateOf(false) }
    var guardianRelExpanded by remember { mutableStateOf(false) }
    var occupationExpanded by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedPhotoUri = uri
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            attachedIdProof = "Aadhaar_Verified.pdf"
        }
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onBackClick()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { msg ->
            scrollState.animateScrollTo(0)
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                .testTag("edit_tenant_back_button")
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
                                text = rememberTranslation("Edit Tenant Details"),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                            Text(
                                text = "Sri Lakshmi Residency, Hyderabad",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                    GlobalLanguageToggle()
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
                            .height(50.dp)
                            .testTag("edit_tenant_cancel_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = rememberTranslation("Cancel"),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = { viewModel.saveTenant() },
                        modifier = Modifier
                            .weight(2f)
                            .height(50.dp)
                            .testTag("edit_tenant_save_button"),
                        enabled = !state.isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0866FF))
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = rememberTranslation("Save Changes"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.fillMaxSize().testTag("edit_tenant_screen")
    ) { innerPadding ->
        if (state.isLoading && state.name.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("edit_tenant_loading"))
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
                // ==========================================
                // 1. TENANT INFORMATION CARD
                // ==========================================
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
                        SectionHeader("1", "Tenant Information", "Basic details of the tenant")

                        OutlinedCard(
                            onClick = {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                            colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFF8FAFC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color(0xFFE8F0FE), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF0866FF), modifier = Modifier.size(24.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Change Photo", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF0866FF)))
                                    Text("JPG, PNG (Max 2 MB)", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.name,
                            onValueChange = { viewModel.onNameChanged(it) },
                            label = { Text("Full Name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("edit_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.phone,
                            onValueChange = { viewModel.onPhoneChanged(it) },
                            label = { Text("Phone Number *") },
                            prefix = { Text("+91 ", fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("edit_phone_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.email,
                            onValueChange = { viewModel.onEmailChanged(it) },
                            label = { Text("Email Address (Optional)") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF64748B)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("edit_email_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = state.dob,
                                onValueChange = { viewModel.onDobChanged(it) },
                                label = { Text("Date of Birth *") },
                                placeholder = { Text("YYYY-MM-DD") },
                                trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF64748B)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("edit_dob_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Text("Gender", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF475569)))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Male", "Female", "Other").forEach { g ->
                                val isSelected = state.gender.trim().equals(g, ignoreCase = true)
                                Surface(
                                    onClick = { viewModel.onGenderChanged(g) },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) Color(0xFF0866FF) else Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF0866FF) else Color(0xFFE2E8F0)),
                                    modifier = Modifier.weight(1f).testTag("edit_gender_chip_$g")
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = g,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = if (isSelected) Color.White else Color(0xFF475569))
                                        )
                                    }
                                }
                            }
                        }

                        Text("Food Preference (Optional)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF475569)))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Veg", "Non-Veg", "Both / Any").forEach { fp ->
                                val isSelected = state.foodPreference.trim().equals(fp, ignoreCase = true)
                                Surface(
                                    onClick = { viewModel.onFoodPreferenceChanged(fp) },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) Color(0xFF0866FF) else Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF0866FF) else Color(0xFFE2E8F0)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = fp,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = if (isSelected) Color.White else Color(0xFF475569))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 2. STAY DETAILS CARD
                // ==========================================
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
                        SectionHeader("2", "Stay Details", "Property, room and stay information")

                        OutlinedCard(
                            onClick = { },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Property *", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B)))
                                    Text("Sri Lakshmi Residency", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B)))
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = roomExpanded,
                                onExpandedChange = { roomExpanded = !roomExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = state.roomNumber,
                                    onValueChange = { viewModel.onRoomChanged(it) },
                                    label = { Text("Room Number *") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roomExpanded) },
                                    singleLine = true,
                                    modifier = Modifier.menuAnchor().fillMaxWidth().testTag("edit_room_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                if (state.rooms.isNotEmpty()) {
                                    ExposedDropdownMenu(
                                        expanded = roomExpanded,
                                        onDismissRequest = { roomExpanded = false }
                                    ) {
                                        state.rooms.forEach { room ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(room.roomNumber, fontWeight = FontWeight.Bold)
                                                        Text("₹${room.ratePerBed.toInt()}/bed • ${room.roomType}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.onRoomChanged(room.roomNumber)
                                                    roomExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            ExposedDropdownMenuBox(
                                expanded = bedExpanded,
                                onExpandedChange = { bedExpanded = !bedExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = state.bedId,
                                    onValueChange = { viewModel.onBedChanged(it) },
                                    label = { Text("Bed (Optional)") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bedExpanded) },
                                    singleLine = true,
                                    modifier = Modifier.menuAnchor().fillMaxWidth().testTag("edit_bed_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                if (state.availableBeds.isNotEmpty()) {
                                    ExposedDropdownMenu(
                                        expanded = bedExpanded,
                                        onDismissRequest = { bedExpanded = false }
                                    ) {
                                        state.availableBeds.forEach { bed ->
                                            DropdownMenuItem(
                                                text = { Text(bed, fontWeight = FontWeight.SemiBold) },
                                                onClick = {
                                                    viewModel.onBedChanged(bed)
                                                    bedExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = state.moveInDate,
                                onValueChange = { viewModel.onMoveInDateChanged(it) },
                                label = { Text("Date of Joining *") },
                                trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = state.leavingDate,
                                onValueChange = { viewModel.onLeavingDateChanged(it) },
                                label = { Text("Leaving Date") },
                                placeholder = { Text("(Optional)") },
                                trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                // ==========================================
                // 3. RENT & DEPOSIT CARD
                // ==========================================
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
                        SectionHeader("3", "Rent & Deposit", "Monthly rent and advance details")

                        OutlinedTextField(
                            value = state.monthlyRent,
                            onValueChange = { viewModel.onMonthlyRentChanged(it) },
                            label = { Text("Monthly Rent *") },
                            prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = state.advancePaid,
                                onValueChange = { viewModel.onAdvancePaidChanged(it) },
                                label = { Text("Advance Amount") },
                                prefix = { Text("₹ ") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = state.refundableDeposit,
                                onValueChange = { viewModel.onRefundableDepositChanged(it) },
                                label = { Text("Refundable Deposit") },
                                prefix = { Text("₹ ") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                // ==========================================
                // 4. FAMILY & GUARDIAN DETAILS CARD
                // ==========================================
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
                        SectionHeader("4", "Family & Guardian Details", "Parent / guardian information")

                        OutlinedTextField(
                            value = state.fatherName,
                            onValueChange = { viewModel.onFatherNameChanged(it) },
                            label = { Text("Father's Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.fatherPhone,
                            onValueChange = { viewModel.onFatherPhoneChanged(it) },
                            label = { Text("Father's Phone Number") },
                            prefix = { Text("+91 ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Text("Guardian Details (Optional)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF475569)))

                        OutlinedTextField(
                            value = state.guardianName,
                            onValueChange = { viewModel.onGuardianNameChanged(it) },
                            label = { Text("Guardian Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenuBox(
                            expanded = guardianRelExpanded,
                            onExpandedChange = { guardianRelExpanded = !guardianRelExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = state.guardianRelationship,
                                onValueChange = { viewModel.onGuardianRelationshipChanged(it) },
                                label = { Text("Guardian Relationship") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = guardianRelExpanded) },
                                singleLine = true,
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = guardianRelExpanded,
                                onDismissRequest = { guardianRelExpanded = false }
                            ) {
                                listOf("Parent", "Father", "Mother", "Sibling", "Relative", "Friend", "Guardian", "Other").forEach { rel ->
                                    DropdownMenuItem(
                                        text = { Text(rel) },
                                        onClick = {
                                            viewModel.onGuardianRelationshipChanged(rel)
                                            guardianRelExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.guardianPhone,
                            onValueChange = { viewModel.onGuardianPhoneChanged(it) },
                            label = { Text("Guardian Phone Number") },
                            prefix = { Text("+91 ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Text("Local Reference (Optional)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF475569)))

                        OutlinedTextField(
                            value = state.localRefName,
                            onValueChange = { viewModel.onLocalRefNameChanged(it) },
                            label = { Text("Local Reference Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.localRefPhone,
                            onValueChange = { viewModel.onLocalRefPhoneChanged(it) },
                            label = { Text("Local Reference Phone Number") },
                            prefix = { Text("+91 ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // ==========================================
                // 5. ADDRESS & OCCUPATION CARD
                // ==========================================
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
                        SectionHeader("5", "Address & Occupation", "Residential address and professional details")

                        OutlinedTextField(
                            value = state.address,
                            onValueChange = { viewModel.onAddressChanged(it) },
                            label = { Text("Residential Address") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = occupationExpanded,
                                onExpandedChange = { occupationExpanded = !occupationExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = state.occupation,
                                    onValueChange = { viewModel.onOccupationChanged(it) },
                                    label = { Text("Occupation") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = occupationExpanded) },
                                    singleLine = true,
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = occupationExpanded,
                                    onDismissRequest = { occupationExpanded = false }
                                ) {
                                    listOf("IT / Software", "Student", "Business / Self-Employed", "Corporate / Private", "Government / PSU", "Healthcare", "Teacher / Education", "Other").forEach { occ ->
                                        DropdownMenuItem(
                                            text = { Text(occ) },
                                            onClick = {
                                                viewModel.onOccupationChanged(occ)
                                                occupationExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = state.occupationOther,
                                onValueChange = { viewModel.onOccupationOtherChanged(it) },
                                label = { Text("If Other, specify") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Text("Office / College Information (Optional)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF475569)))

                        OutlinedTextField(
                            value = state.officeName,
                            onValueChange = { viewModel.onOfficeNameChanged(it) },
                            label = { Text("Name of Office / College") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.officeAddress,
                            onValueChange = { viewModel.onOfficeAddressChanged(it) },
                            label = { Text("Office / College Address") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.officePhone,
                            onValueChange = { viewModel.onOfficePhoneChanged(it) },
                            label = { Text("Office / College Phone Number") },
                            prefix = { Text("+91 ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Text("Vehicle Details (Optional)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF475569)))

                        OutlinedTextField(
                            value = state.vehicleNumber,
                            onValueChange = { viewModel.onVehicleNumberChanged(it) },
                            label = { Text("Vehicle Registration Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Text("Notes & Remarks (Optional)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF475569)))

                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = { viewModel.onNotesChanged(it) },
                            label = { Text("Special Requests / Notes") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // ==========================================
                // 6. DOCUMENTS CARD
                // ==========================================
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader("6", "Documents", "Upload ID proof and documents")
                            TextButton(onClick = { }) {
                                Text("View All", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF0866FF)))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DocUploadCard(
                                title = "ID Proof",
                                subtitle = "Aadhaar, PAN",
                                isAttached = attachedIdProof != null,
                                onClick = { documentPickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                                modifier = Modifier.weight(1f)
                            )
                            DocUploadCard(
                                title = "Address",
                                subtitle = "Current addr",
                                isAttached = attachedAddressProof != null,
                                onClick = { documentPickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DocUploadCard(
                                title = "Photo",
                                subtitle = "Tenant photo",
                                isAttached = attachedPhotoDoc != null,
                                onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f)
                            )
                            DocUploadCard(
                                title = "Other",
                                subtitle = "Any document",
                                isAttached = attachedOtherDoc != null,
                                onClick = { documentPickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
