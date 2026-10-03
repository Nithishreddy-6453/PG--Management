package com.example.features.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.LocalSpacing
import com.example.core.language.GlobalLanguageToggle
import com.example.core.language.rememberTranslation
import com.example.ui.viewmodel.PgViewModel

/**
 * Redesigned clean, modern Workspace Setup screen.
 * All input fields start completely blank.
 */
@Composable
fun PinSetupScreen(
    viewModel: PgViewModel,
    onSetupComplete: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    var pgName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var pinCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var pgNameError by remember { mutableStateOf(false) }
    var ownerNameError by remember { mutableStateOf(false) }
    var pinCodeError by remember { mutableStateOf(false) }

    val isFormValid = pgName.isNotBlank() &&
            ownerName.isNotBlank() &&
            phone.isNotBlank() &&
            upiId.isNotBlank() &&
            pinCode.length == 4

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
                .padding(spacing.large)
                .widthIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            // Compact Header Row: Back button, Title/Subtitle, and Global Language Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("setup_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = rememberTranslation("Back"),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(48.dp))
                }

                GlobalLanguageToggle()
            }

            // Title & Subtitle
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = rememberTranslation("Workspace Setup"),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.testTag("setup_title")
                )

                Text(
                    text = rememberTranslation("Set up your PG details and owner access"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.testTag("setup_subtitle")
                )
            }

            Spacer(modifier = Modifier.height(spacing.small))

            // 1. PG Name Input
            OutlinedTextField(
                value = pgName,
                onValueChange = {
                    pgName = it
                    pgNameError = it.isBlank()
                },
                label = { Text(rememberTranslation("PG Accommodation Name")) },
                placeholder = { Text(rememberTranslation("Enter PG name")) },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                isError = pgNameError,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
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
                label = { Text(rememberTranslation("Owner Name")) },
                placeholder = { Text(rememberTranslation("Enter owner name")) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                isError = ownerNameError,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_owner_name_field")
            )

            // 3. Phone Input
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(rememberTranslation("Mobile Number")) },
                placeholder = { Text(rememberTranslation("Enter mobile number")) },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_phone_field")
            )

            // 4. UPI ID Input
            OutlinedTextField(
                value = upiId,
                onValueChange = { upiId = it },
                label = { Text(rememberTranslation("UPI ID for Rent Collection")) },
                placeholder = { Text(rememberTranslation("Enter UPI ID")) },
                leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
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
                        pinCodeError = it.length != 4 && it.isNotEmpty()
                    }
                },
                label = { Text(rememberTranslation("Set 4-Digit Security PIN")) },
                placeholder = { Text(rememberTranslation("Create 4-digit PIN")) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide PIN" else "Show PIN"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = pinCodeError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_pin_field")
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            // Primary CTA Button ("Confirm Setup")
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
                        viewModel.unlock(pinCode)
                        onSetupComplete()
                    }
                },
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("setup_submit_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = rememberTranslation("Confirm Setup"),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(spacing.large))
        }
    }
}
