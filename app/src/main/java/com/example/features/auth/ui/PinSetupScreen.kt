package com.example.features.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.LocalSpacing
import com.example.ui.viewmodel.PgViewModel

/**
 * First-Run onboarding configuration screen.
 * Gathers profile data and initial security PIN code.
 */
@Composable
fun PinSetupScreen(
    viewModel: PgViewModel,
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    var pgName by remember { mutableStateOf("Emerald Stays") }
    var ownerName by remember { mutableStateOf("Nithish Prasad") }
    var phone by remember { mutableStateOf("+91 98765 43210") }
    var upiId by remember { mutableStateOf("nithish@okaxis") }
    var pinCode by remember { mutableStateOf("1234") }

    var pgNameError by remember { mutableStateOf(false) }
    var ownerNameError by remember { mutableStateOf(false) }
    var pinCodeError by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("pin_setup_screen_container"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.extraLarge)
                .widthIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            Spacer(modifier = Modifier.height(spacing.large))

            // Brand Header
            Text(
                text = "Workspace Setup",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier.testTag("setup_title")
            )

            Text(
                text = "Register your Paying Guest (PG) details and configure your secure owner login PIN.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.testTag("setup_subtitle")
            )

            Spacer(modifier = Modifier.height(spacing.large))

            // 1. PG Name Input
            OutlinedTextField(
                value = pgName,
                onValueChange = {
                    pgName = it
                    pgNameError = it.isBlank()
                },
                label = { Text("PG Accommodation Name") },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                isError = pgNameError,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_pg_name_field")
            )

            // 2. Owner Name Input
            OutlinedTextField(
                value = ownerName,
                onValueChange = {
                    ownerName = it
                    ownerNameError = it.isBlank()
                },
                label = { Text("Owner Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                isError = ownerNameError,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_owner_name_field")
            )

            // 3. Phone Input
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Mobile Number") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_phone_field")
            )

            // 4. UPI ID Input
            OutlinedTextField(
                value = upiId,
                onValueChange = { upiId = it },
                label = { Text("UPI ID for Rent Collection") },
                leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_upi_field")
            )

            // 5. PIN Code Input (4 digits)
            OutlinedTextField(
                value = pinCode,
                onValueChange = {
                    if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                        pinCode = it
                        pinCodeError = it.length != 4
                    }
                },
                label = { Text("Set 4-Digit Security PIN") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                isError = pinCodeError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_pin_field")
            )

            Spacer(modifier = Modifier.height(spacing.large))

            // CTA Submit Button
            Button(
                onClick = {
                    val isPgBlank = pgName.isBlank()
                    val isOwnerBlank = ownerName.isBlank()
                    val isPinInvalid = pinCode.length != 4

                    pgNameError = isPgBlank
                    ownerNameError = isOwnerBlank
                    pinCodeError = isPinInvalid

                    if (!isPgBlank && !isOwnerBlank && !isPinInvalid) {
                        viewModel.updateProfile(
                            pgName = pgName,
                            ownerName = ownerName,
                            phone = phone,
                            upiId = upiId,
                            pinCode = pinCode
                        )
                        // Unlock app instantly on setup complete
                        viewModel.unlock(pinCode)
                        onSetupComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("setup_submit_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "Confirm Setup & Unlock",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
