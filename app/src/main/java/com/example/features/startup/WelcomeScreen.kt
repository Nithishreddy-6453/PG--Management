package com.example.features.startup

import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MapsHomeWork
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.LocalSpacing
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import kotlinx.coroutines.launch
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

@Composable
fun WelcomeScreen(
    onContinueWithEmail: () -> Unit,
    onGoogleSignInSuccess: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Web OAuth client ID from google-services.json / strings.xml
    val fallbackClientId = "660420696992-12ihv29oqr5i89tqdbdei02pj5kvmct8.apps.googleusercontent.com"
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    val webClientId = if (resId != 0 && context.getString(resId).isNotBlank()) context.getString(resId) else fallbackClientId

    var isLoading by remember { mutableStateOf(false) }
    var showNoAccountDialog by remember { mutableStateOf(false) }

    if (showNoAccountDialog) {
        AlertDialog(
            onDismissRequest = { showNoAccountDialog = false },
            title = { Text("No Google Account Found") },
            text = { Text("No Google account is currently signed in on this device. You can add a Google account in Android Settings, or sign in using Email.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNoAccountDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                                putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                            }
                            context.startActivity(intent)
                        } catch (ex: Exception) {
                            Toast.makeText(context, "Please add a Google account in Settings", Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("Add Account")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNoAccountDialog = false
                        onContinueWithEmail()
                    }
                ) {
                    Text("Use Email")
                }
            }
        )
    }

    fun doGoogleSignIn() {
        if (webClientId.isEmpty() || webClientId == "null") {
            Log.e("GoogleSignIn", "Web Client ID is missing or null.")
            Toast.makeText(context, "Google Sign In not configured properly (missing web_client_id)", Toast.LENGTH_LONG).show()
            return
        }
        
        coroutineScope.launch {
            isLoading = true
            Log.d("GoogleSignIn", "Initiating CredentialManager request. ServerClientId: $webClientId")
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            try {
                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential
                Log.d("GoogleSignIn", "Credential returned successfully of type: ${credential.type}")
                if (credential is androidx.credentials.CustomCredential &&
                    (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                     credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    Log.d("GoogleSignIn", "GoogleIdTokenCredential created successfully. ID Token present.")
                    onGoogleSignInSuccess(googleIdTokenCredential.idToken)
                } else {
                    Log.e("GoogleSignIn", "Unexpected credential type received: ${credential.type}")
                    Toast.makeText(context, "Unexpected credential type: ${credential.type}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: GetCredentialCancellationException) {
                Log.i("GoogleSignIn", "Google Sign-In flow cancelled by user.")
                Toast.makeText(context, "Sign in cancelled", Toast.LENGTH_SHORT).show()
            } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                Log.w("GoogleSignIn", "NoCredentialException caught: No Google account on device. Showing account prompt.")
                showNoAccountDialog = true
            } catch (e: GetCredentialException) {
                Log.w("GoogleSignIn", "GetCredentialException caught: [Type: ${e.type}] Message: ${e.message}")
                if (e.type.contains("TYPE_NO_CREDENTIAL")) {
                    showNoAccountDialog = true
                } else if (e.message?.contains("DEVELOPER_ERROR") == true || e.type.contains("DEVELOPER_ERROR") || e.message?.contains("10:") == true) {
                    Toast.makeText(context, "Developer Error (10): Ensure SHA-1 fingerprint is registered in Firebase Console", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Google Sign In Failed: ${e.message ?: e.type}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.w("GoogleSignIn", "Unexpected exception during Google Sign-In: [Class: ${e.javaClass.simpleName}] Message: ${e.message}")
                Toast.makeText(context, "Google Sign In Exception: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("welcome_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.extraLarge)
                .widthIn(max = 600.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(1.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.MapsHomeWork,
                    contentDescription = "Guest House Management Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(80.dp)
                        .testTag("welcome_brand_icon")
                )
                Spacer(modifier = Modifier.height(spacing.large))
                Text(
                    text = "Welcome to PG Manager",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.testTag("welcome_header_title")
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = "Secure, Offline-first, Elite PG Management",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier
                        .padding(horizontal = spacing.medium)
                        .testTag("welcome_header_subtitle")
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(spacing.medium))
                }
                
                Button(
                    onClick = { doGoogleSignIn() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("welcome_google_button"),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                OutlinedButton(
                    onClick = onContinueWithEmail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("welcome_email_button"),
                    enabled = !isLoading,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email"
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text(
                            text = "Continue with Email",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}
