package com.example.features.profile

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.designsystem.LocalSpacing
import com.example.ui.viewmodel.PgViewModel
import com.example.features.auth.ui.viewmodel.AuthViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: PgViewModel,
    onBackClick: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val spacing = LocalSpacing.current
    val currentProfile by viewModel.profile.collectAsState()
    
    var pgName by remember(currentProfile) { mutableStateOf(currentProfile?.pgName ?: "Emerald Stays") }
    var ownerName by remember(currentProfile) { mutableStateOf(currentProfile?.ownerName ?: "Nithish Prasad") }
    var phone by remember(currentProfile) { mutableStateOf(currentProfile?.phone ?: "+91 98765 43210") }
    var upiId by remember(currentProfile) { mutableStateOf(currentProfile?.upiId ?: "nithish@okaxis") }
    var pinCode by remember(currentProfile) { mutableStateOf(currentProfile?.pinCode ?: "1234") }
    
    val firebaseUser = authViewModel.getCurrentUser()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back to Dashboard"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        authViewModel.signOut()
                        onLogout() 
                    }) {
                        Icon(imageVector = Icons.Default.Logout, contentDescription = "Logout")
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.testTag("profile_top_bar")
            )
        },
        modifier = modifier.testTag("profile_screen_container")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            
            // Firebase Account Details Section
            if (firebaseUser != null) {
                Text("Firebase Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.large),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.width(spacing.medium))
                            Column {
                                Text(text = firebaseUser.displayName ?: "No Display Name", fontWeight = FontWeight.Bold)
                                Text(text = firebaseUser.email ?: "No Email", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = spacing.small))
                        
                        val providerId = firebaseUser.providerData.firstOrNull()?.providerId ?: "unknown"
                        Text(text = "Provider: $providerId", style = MaterialTheme.typography.bodyMedium)
                        
                        val createdDate = firebaseUser.metadata?.creationTimestamp?.let {
                            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it))
                        } ?: "Unknown"
                        Text(text = "Account Created: $createdDate", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(spacing.medium))
            }
            
            Text("Update PG Profile Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(spacing.large), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    OutlinedTextField(
                        value = pgName,
                        onValueChange = { pgName = it },
                        label = { Text("Paying Guest (PG) Name") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_pg_name_input")
                    )
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Owner Full Name") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_owner_name_input")
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Registered Phone") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_phone_input")
                    )
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("UPI Merchant ID") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_upi_input")
                    )
                    OutlinedTextField(
                        value = pinCode,
                        onValueChange = { pinCode = it },
                        label = { Text("Security Access PIN (4 digits)") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_pin_input")
                    )
                    
                    Spacer(modifier = Modifier.height(spacing.medium))
                    
                    Button(
                        onClick = {
                            if (pgName.isNotBlank() && ownerName.isNotBlank() && pinCode.isNotBlank()) {
                                viewModel.updateProfile(
                                    pgName = pgName,
                                    ownerName = ownerName,
                                    phone = phone,
                                    upiId = upiId,
                                    pinCode = pinCode
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_profile_button")
                    ) {
                        Text("Save Profile Changes")
                    }
                }
            }
        }
    }
}
