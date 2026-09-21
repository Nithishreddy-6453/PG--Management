package com.example.features.tenants

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.LocalSpacing
import com.example.data.database.TenantEntity
import com.example.features.tenants.ui.viewmodel.TenantDetailsUiEffect
import com.example.features.tenants.ui.viewmodel.TenantDetailsUiState
import com.example.features.tenants.ui.viewmodel.TenantDetailsViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantDetailsScreen(
    viewModel: TenantDetailsViewModel,
    onBackClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var showVacateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = true) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is TenantDetailsUiEffect.NavigateBack -> {
                    onBackClick()
                }
                is TenantDetailsUiEffect.ShowToast -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    // Refresh details when entering screen
    LaunchedEffect(key1 = true) {
        viewModel.loadTenantDetails()
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

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // 1. Hero Header Card
                        ProfileHeroHeader(tenant = tenant, spacing = spacing, isVacated = isVacated)

                        // 2. Action Controls Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium, vertical = spacing.small),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            if (!isVacated) {
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
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                                    Spacer(modifier = Modifier.width(spacing.small))
                                    Text("Vacate Room", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Button(
                                onClick = { showDeleteDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("details_delete_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(spacing.small))
                                Text("Delete Tenant", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // 3. Rental Information Section
                        Card(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.medium)
                        ) {
                            Column(modifier = Modifier.padding(spacing.large)) {
                                Text(
                                    text = "Rent & Deposit Setup",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = spacing.small)
                                )
                                HorizontalDivider(modifier = Modifier.padding(bottom = spacing.small))

                                InfoDetailRow(label = "Monthly Rent", value = "₹${tenant.monthlyRent}", icon = Icons.Default.CurrencyRupee)
                                InfoDetailRow(label = "Security Deposit", value = "₹${tenant.securityDeposit}", icon = Icons.Default.Lock)
                                InfoDetailRow(label = "Advance Paid", value = "₹${tenant.advancePaid}", icon = Icons.Default.Payments)
                                InfoDetailRow(label = "Move-in Date", value = tenant.moveInDate, icon = Icons.Default.CalendarToday)
                                if (isVacated) {
                                    InfoDetailRow(label = "Occupancy Status", value = "Vacated (Archived)", icon = Icons.Default.Info, valueColor = MaterialTheme.colorScheme.error)
                                } else {
                                    InfoDetailRow(label = "Occupancy Status", value = "Active Resident", icon = Icons.Default.Info, valueColor = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        // 4. Personal & Contact Information Section
                        Card(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium)
                        ) {
                            Column(modifier = Modifier.padding(spacing.large)) {
                                Text(
                                    text = "Contact & Personal Information",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = spacing.small)
                                )
                                HorizontalDivider(modifier = Modifier.padding(bottom = spacing.small))

                                InfoDetailRow(label = "Mobile Number", value = tenant.phone, icon = Icons.Default.Phone)
                                if (tenant.alternateContact.isNotBlank()) {
                                    InfoDetailRow(label = "Alt Contact", value = tenant.alternateContact, icon = Icons.Default.PhoneAndroid)
                                }
                                if (tenant.email.isNotBlank()) {
                                    InfoDetailRow(label = "Email Address", value = tenant.email, icon = Icons.Default.Email)
                                }
                                InfoDetailRow(label = "Emergency Contact", value = tenant.emergencyContact, icon = Icons.Default.ContactPhone)
                                if (tenant.dob.isNotBlank()) {
                                    InfoDetailRow(label = "Date of Birth", value = tenant.dob, icon = Icons.Default.Cake)
                                }
                                InfoDetailRow(label = "Gender", value = tenant.gender, icon = Icons.Default.Person)
                                if (tenant.occupation.isNotBlank()) {
                                    InfoDetailRow(label = "Occupation", value = tenant.occupation, icon = Icons.Default.Work)
                                }
                                if (tenant.companyOrCollege.isNotBlank()) {
                                    InfoDetailRow(label = "Company / College", value = tenant.companyOrCollege, icon = Icons.Default.School)
                                }
                                InfoDetailRow(label = "KYC Uploaded", value = if (tenant.isKycUploaded) "Yes (${tenant.kycDocType})" else "No", icon = Icons.Default.Verified, valueColor = if (tenant.isKycUploaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                if (tenant.address.isNotBlank()) {
                                    InfoDetailRow(label = "Address", value = tenant.address, icon = Icons.Default.LocationOn)
                                }
                            }
                        }

                        // 5. Notes Section
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

            // Vacate Dialog Confirmation
            if (showVacateDialog) {
                AlertDialog(
                    onDismissRequest = { showVacateDialog = false },
                    title = { Text("Vacate Tenant?", fontWeight = FontWeight.Bold) },
                    text = { Text("This will release the bed assignment and set the occupancy status of the tenant to Vacated. The historical rent logs and tenant personal history will be preserved.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.vacateTenant()
                                showVacateDialog = false
                            },
                            modifier = Modifier.testTag("confirm_vacate_button")
                        ) {
                            Text("Confirm Vacate", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showVacateDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Delete Dialog Confirmation
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete Tenant Permanently?", fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure you want to delete this tenant permanently? This action will completely erase the resident profile and all past payment history logs from the system and cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteTenant()
                                showDeleteDialog = false
                            },
                            modifier = Modifier.testTag("confirm_delete_button")
                        ) {
                            Text("Delete Permanently", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileHeroHeader(
    tenant: TenantEntity,
    spacing: com.example.core.designsystem.PgSpacing,
    isVacated: Boolean
) {
    val initials = if (tenant.name.isNotBlank()) {
        tenant.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
    } else "T"

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
            // Circle Photo Avatar
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
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
            modifier = Modifier.size(80.dp)
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
