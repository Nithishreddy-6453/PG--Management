package com.example.features.googleform.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.language.rememberTranslation
import com.example.features.googleform.domain.model.FormAccessRequest
import com.example.features.googleform.domain.model.FormVersionHistory
import com.example.features.googleform.domain.model.QuestionKeys
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantRegistrationFormScreen(
    viewModel: TenantRegistrationFormViewModel,
    onBackClick: () -> Unit,
    onNavigateToPending: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showRequestDialog by remember { mutableStateOf(false) }
    var requestNoteInput by remember { mutableStateOf("") }

    // Activity launcher for Google Sign-In with Forms Scope
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleGoogleSignInResult(result.data)
        }
    }

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

    // Auto-refresh request status on screen launch or when property/account changes if there is a pending request or non-owner connected
    LaunchedEffect(uiState.propertyId, uiState.googleAccount.email, uiState.myPendingRequest?.status) {
        if (uiState.propertyId.isNotBlank() && uiState.googleAccount.isConnected) {
            val isOriginalOwner = uiState.googleAccount.email.isNotBlank() &&
                    uiState.form?.googleAccountEmail?.isNotBlank() == true &&
                    uiState.googleAccount.email.equals(uiState.form?.googleAccountEmail, ignoreCase = true)
            if (!isOriginalOwner && (uiState.myPendingRequest == null || uiState.myPendingRequest?.isPending == true)) {
                viewModel.checkFormAccessRequestStatus()
            }
        }
    }

    val titleText = rememberTranslation("Tenant Registration Form")
    val shareFormText = rememberTranslation("Share Form")
    val copyLinkText = rememberTranslation("Copy Link")
    val openFormText = rememberTranslation("Open Form")
    val linkCopiedText = rememberTranslation("Link copied to clipboard")
    val connectGoogleText = rememberTranslation("Connect Google")
    val createFormText = rememberTranslation("Create Registration Form")
    val creatingFormText = rememberTranslation("Creating Registration Form...")
    val checkStatusText = rememberTranslation("Check Status")
    val unavailableText = rememberTranslation("Registration form unavailable.")
    val createReplacementText = rememberTranslation("Create Replacement Form")

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
                            .testTag("google_form_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    com.example.core.language.GlobalLanguageToggle(
                        modifier = Modifier.padding(end = 6.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF8FAFC)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.testTag("tenant_registration_form_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Property Header Card
            item {
                PropertyBannerCard(propertyName = uiState.propertyName)
            }

            // 2. Google Account Connection Card
            item {
                GoogleAccountConnectionCard(
                    isConnected = uiState.googleAccount.isConnected,
                    accountEmail = uiState.googleAccount.email,
                    displayName = uiState.googleAccount.displayName,
                    formOwnerEmail = uiState.form?.googleAccountEmail ?: "",
                    myRequest = uiState.myPendingRequest,
                    isSubmittingRequest = uiState.isSubmittingRequest,
                    onRequestAccessClick = { showRequestDialog = true },
                    onRegenerateClick = { viewModel.setShowReplaceConfirmDialog(true) },
                    onConnectClick = {
                        val intent = viewModel.getGoogleSignInIntent()
                        googleSignInLauncher.launch(intent)
                    },
                    onDisconnectClick = {
                        viewModel.disconnectGoogle()
                    }
                )
            }

            // 3. Incoming Pending Access Requests Card (For Form Owner)
            if (uiState.incomingPendingRequests.isNotEmpty()) {
                item {
                    IncomingAccessRequestsCard(
                        requests = uiState.incomingPendingRequests,
                        isApproving = uiState.isApprovingRequest,
                        isRejecting = uiState.isRejectingRequest,
                        onApproveClick = { requestId -> viewModel.approveFormAccessRequest(requestId) },
                        onRejectClick = { requestId -> viewModel.rejectFormAccessRequest(requestId) }
                    )
                }
            }

            // 4. Form Status & Action Section
            val form = uiState.form
            val isGoogleConnected = uiState.googleAccount.isConnected
            val myGoogleEmail = uiState.googleAccount.email
            val formOwnerEmail = form?.googleAccountEmail ?: ""
            val isOriginalOwner = myGoogleEmail.isNotBlank() && formOwnerEmail.isNotBlank() && myGoogleEmail.equals(formOwnerEmail, ignoreCase = true)
            val myReq = uiState.myPendingRequest
            val isApprovedEditor = myReq?.isApproved == true

            if (isGoogleConnected) {
                if (form == null || (!form.published && !form.active)) {
                    // State A: Form not created yet
                    item {
                        FormNotCreatedCard(
                            propertyName = uiState.propertyName,
                            isCreating = uiState.isCreating,
                            onCreateFormClick = { viewModel.createRegistrationForm() },
                            createButtonText = if (uiState.isCreating) creatingFormText else createFormText
                        )
                    }
                } else if (!form.isAvailable) {
                    // State B: Form was deleted or unavailable
                    item {
                        FormUnavailableCard(
                            lastError = form.lastError ?: unavailableText,
                            isCreating = uiState.isCreating,
                            onCreateReplacementClick = { viewModel.setShowReplaceConfirmDialog(true) },
                            buttonText = createReplacementText
                        )
                    }
                } else {
                    // State C: Form is Active
                    item {
                        ActiveFormCard(
                            formTitle = form.formTitle,
                            responderUri = form.responderUri,
                            editUri = form.editUri,
                            formOwnerEmail = formOwnerEmail,
                            connectedEmail = myGoogleEmail,
                            isOriginalOwner = isOriginalOwner,
                            isApprovedEditor = isApprovedEditor,
                            myRequest = myReq,
                            lastCheckedAt = form.lastCheckedAt,
                            isCheckingStatus = uiState.isCheckingStatus,
                            isCreating = uiState.isCreating,
                            isSubmittingRequest = uiState.isSubmittingRequest,
                            onRequestAccessClick = { showRequestDialog = true },
                            onCheckRequestStatusClick = { viewModel.checkFormAccessRequestStatus() },
                            onShareClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, form.formTitle)
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Please fill out the tenant registration form for ${uiState.propertyName}:\n\n${form.responderUri}"
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Registration Form"))
                            },
                            onCopyClick = {
                                clipboardManager.setText(AnnotatedString(form.responderUri))
                                scope.launch {
                                    snackbarHostState.showSnackbar(linkCopiedText)
                                }
                            },
                            onOpenClick = {
                                try {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(form.responderUri))
                                    context.startActivity(browserIntent)
                                } catch (_: Exception) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Unable to open browser")
                                    }
                                }
                            },
                            onEditClick = {
                                try {
                                    val uriToOpen = if (form.editUri.isNotBlank()) form.editUri else "https://docs.google.com/forms/d/${form.formId}/edit"
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriToOpen))
                                    context.startActivity(browserIntent)
                                } catch (_: Exception) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Unable to open Google Forms editor")
                                    }
                                }
                            },
                            onCheckStatusClick = { viewModel.checkStatus() },
                            onRegenerateClick = { viewModel.setShowReplaceConfirmDialog(true) }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Submissions & Responses Action Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth().testTag("form_submissions_card")
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(Color(0xFFEFF6FF), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Assignment,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Form Submissions",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = if (uiState.pendingResponsesCount > 0) "${uiState.pendingResponsesCount} pending review" else "No new submissions pending",
                                                fontSize = 12.sp,
                                                color = if (uiState.pendingResponsesCount > 0) Color(0xFFD97706) else Color(0xFF64748B),
                                                fontWeight = if (uiState.pendingResponsesCount > 0) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }

                                    if (uiState.pendingResponsesCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFF59E0B), CircleShape)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${uiState.pendingResponsesCount}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.syncResponses() },
                                        enabled = !uiState.isSyncingResponses,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(44.dp).testTag("form_screen_sync_button")
                                    ) {
                                        if (uiState.isSyncingResponses) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Sync", fontSize = 13.sp)
                                        }
                                    }

                                    Button(
                                        onClick = onNavigateToPending,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(2f).height(44.dp).testTag("view_submissions_button")
                                    ) {
                                        Text("View Submissions", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // If Google is not connected yet, show prompt
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Connect Google Account First",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1E40AF)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "To automatically create, publish, and manage tenant registration Google Forms for ${uiState.propertyName}, please connect your Google account above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF3B82F6),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // 5. Form History Card
            if (uiState.formHistory.isNotEmpty()) {
                item {
                    FormHistoryCard(history = uiState.formHistory)
                }
            }

            // 6. Questions Structure Preview Card
            item {
                QuestionsPreviewCard()
            }
        }
    }

    // Dialog for requesting access
    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            title = { Text("Request Form Access", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Request Editor permissions from form owner (${uiState.form?.googleAccountEmail ?: "Form Owner"}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = requestNoteInput,
                        onValueChange = { requestNoteInput = it },
                        label = { Text("Optional Note for Owner") },
                        placeholder = { Text("e.g. Need editor access to manage registrations") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.requestFormAccess(requestNoteInput)
                        showRequestDialog = false
                        requestNoteInput = ""
                    }
                ) {
                    Text("Submit Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRequestDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for confirming form replacement
    if (uiState.showReplaceConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowReplaceConfirmDialog(false) },
            title = { Text("Replace Registration Form?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Creating a replacement registration form will generate a new active Google Form link for prospective tenants.\n\nThe current form link will change, but all previous form links, versions, and imported tenant submissions will be preserved in form history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155)
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmCreateReplacementForm() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Confirm & Create Replacement")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowReplaceConfirmDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PropertyBannerCard(propertyName: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEFF6FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Apartment,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Property / PG",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF64748B),
                    fontSize = 11.5.sp
                )
                Text(
                    text = propertyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun GoogleAccountConnectionCard(
    isConnected: Boolean,
    accountEmail: String,
    displayName: String,
    formOwnerEmail: String,
    myRequest: FormAccessRequest? = null,
    isSubmittingRequest: Boolean = false,
    onRequestAccessClick: () -> Unit = {},
    onRegenerateClick: () -> Unit = {},
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val googleFormsText = rememberTranslation("Google Forms")
    val connectedText = rememberTranslation("Connected")
    val notConnectedText = rememberTranslation("Not Connected")
    val connectGoogleText = rememberTranslation("Connect Google")
    val disconnectText = rememberTranslation("Disconnect Google")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = if (isConnected) Color(0xFF16A34A) else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = googleFormsText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                // Connection Status Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isConnected) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                    border = BorderStroke(
                        1.dp,
                        if (isConnected) Color(0xFF86EFAC) else Color(0xFFCBD5E1)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF16A34A) else Color(0xFF94A3B8))
                        )
                        Text(
                            text = if (isConnected) connectedText else notConnectedText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isConnected) Color(0xFF166534) else Color(0xFF475569)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (displayName.isNotBlank()) {
                            Text(
                                text = displayName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                        Text(
                            text = accountEmail.ifBlank { "Account authorized" },
                            fontSize = 12.5.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (formOwnerEmail.isNotBlank() && !accountEmail.equals(formOwnerEmail, ignoreCase = true)) {
                            Text(
                                text = "Original Form Owner: $formOwnerEmail",
                                fontSize = 11.5.sp,
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onDisconnectClick,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFEF4444)
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("google_disconnect_button")
                    ) {
                        Text(
                            text = disconnectText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // If connected account is not the form owner, present request permission action button
                if (formOwnerEmail.isNotBlank() && !accountEmail.equals(formOwnerEmail, ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (myRequest?.isApproved == true) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Editor Access Granted & Verified by $formOwnerEmail",
                                    fontSize = 12.sp,
                                    color = Color(0xFF166534),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else if (myRequest?.isPending == true) {
                        // Pending status with action buttons is displayed in the Active Form section below
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onRequestAccessClick,
                                enabled = !isSubmittingRequest,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .testTag("request_permission_button")
                            ) {
                                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Request Permission from Owner", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onRegenerateClick,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("set_active_form_button")
                            ) {
                                Text("Set Active Form with Connected Account ($accountEmail)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Authorize Google Forms API to automatically generate and host your tenant registration links.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onConnectClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("connect_google_button")
                ) {
                    Text(
                        text = connectGoogleText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomingAccessRequestsCard(
    requests: List<FormAccessRequest>,
    isApproving: Boolean,
    isRejecting: Boolean,
    onApproveClick: (String) -> Unit,
    onRejectClick: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        modifier = Modifier.fillMaxWidth().testTag("incoming_access_requests_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Incoming Form Access Requests (${requests.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF92400E)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            requests.forEach { req ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFFEF3C7), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "${req.requesterName} (${req.requesterGoogleEmail})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "Requested Editor access for: ${req.formTitle}",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (req.requestNote.isNotBlank()) {
                        Text(
                            text = "Note: \"${req.requestNote}\"",
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onRejectClick(req.requestId) },
                            enabled = !isRejecting,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Reject", fontSize = 12.sp, color = Color(0xFFEF4444))
                        }

                        Button(
                            onClick = { onApproveClick(req.requestId) },
                            enabled = !isApproving,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier.weight(2f).height(38.dp)
                        ) {
                            if (isApproving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve & Grant Google Access", fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ActiveFormCard(
    formTitle: String,
    responderUri: String,
    editUri: String,
    formOwnerEmail: String,
    connectedEmail: String,
    isOriginalOwner: Boolean,
    isApprovedEditor: Boolean = false,
    myRequest: FormAccessRequest?,
    lastCheckedAt: Long,
    isCheckingStatus: Boolean,
    isCreating: Boolean,
    isSubmittingRequest: Boolean,
    onRequestAccessClick: () -> Unit,
    onCheckRequestStatusClick: () -> Unit,
    onShareClick: () -> Unit,
    onCopyClick: () -> Unit,
    onOpenClick: () -> Unit,
    onEditClick: () -> Unit,
    onCheckStatusClick: () -> Unit,
    onRegenerateClick: () -> Unit
) {
    val registrationFormText = rememberTranslation("Registration Form")
    val activeText = rememberTranslation("Active")
    val shareFormText = rememberTranslation("Share Form")
    val copyLinkText = rememberTranslation("Copy Link")
    val openFormText = rememberTranslation("Open Form")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = registrationFormText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFDCFCE7),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF16A34A))
                        )
                        Text(
                            text = activeText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                fontSize = 17.sp
            )

            if (formOwnerEmail.isNotBlank()) {
                Text(
                    text = "Owner: $formOwnerEmail",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Non-owner / Shared Access Banner Section
            if (!isOriginalOwner) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                myRequest?.isApproved == true -> Color(0xFFECFDF5)
                                myRequest?.isPending == true -> Color(0xFFFFFBEB)
                                myRequest?.isRejected == true -> Color(0xFFFEF2F2)
                                else -> Color(0xFFEFF6FF)
                            },
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    when {
                        myRequest?.isApproved == true || isApprovedEditor -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Access Approved", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF166534))
                            }
                            Text("Access to this registration form has been verified. Your account ($connectedEmail) has Editor access.", fontSize = 11.5.sp, color = Color(0xFF15803D), modifier = Modifier.padding(top = 2.dp))
                        }
                        myRequest?.isPending == true -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Access Request Pending Owner Approval", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF92400E))
                            }
                            Text("Request submitted for $connectedEmail. Waiting for approval from $formOwnerEmail.", fontSize = 11.5.sp, color = Color(0xFFB45309), modifier = Modifier.padding(top = 2.dp))
                        }
                        myRequest?.isRejected == true -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Access Request Rejected", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF991B1B))
                            }
                            Text("Reason: ${myRequest.rejectionReason ?: "Rejected by form owner."}", fontSize = 11.5.sp, color = Color(0xFFB91C1C), modifier = Modifier.padding(top = 2.dp))
                        }
                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Form Owned by Another Google Account", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E40AF))
                            }
                            Text("Connected as $connectedEmail. Request access from $formOwnerEmail to edit or replace this form.", fontSize = 11.5.sp, color = Color(0xFF1D4ED8), modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    if (myRequest?.isApproved != true && !isApprovedEditor) {
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (myRequest?.isPending == true) {
                                Button(
                                    onClick = onCheckRequestStatusClick,
                                    enabled = !isCheckingStatus,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(38.dp).testTag("check_request_status_button")
                                ) {
                                    if (isCheckingStatus) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Checking Status...", fontSize = 12.sp)
                                    } else {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Check Request Status", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = onRequestAccessClick,
                                    enabled = !isSubmittingRequest,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Text("Request Form Access", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Public Form URL Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9))
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = responderUri,
                    fontSize = 12.sp,
                    color = Color(0xFF334155),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row (Share / Copy / Open)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onShareClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(42.dp)
                        .testTag("share_form_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(shareFormText, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCopyClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("copy_link_button")
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(copyLinkText, fontSize = 12.5.sp)
                }

                OutlinedButton(
                    onClick = onOpenClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("open_form_button")
                ) {
                    Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(openFormText, fontSize = 12.5.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Management Buttons (Edit / Replace / Verify)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isOriginalOwner || isApprovedEditor || myRequest?.isApproved == true) {
                    OutlinedButton(
                        onClick = onEditClick,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Form", fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = onRegenerateClick,
                        enabled = !isCreating,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("Replace Form", fontSize = 11.5.sp)
                    }
                }

                OutlinedButton(
                    onClick = onCheckStatusClick,
                    enabled = !isCheckingStatus,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    if (isCheckingStatus) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                    } else {
                        Text("Verify Status", fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormNotCreatedCard(
    propertyName: String,
    isCreating: Boolean,
    onCreateFormClick: () -> Unit,
    createButtonText: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEFF6FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "No Registration Form Created Yet",
                fontWeight = FontWeight.Bold,
                fontSize = 16.5.sp,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Generate an official Google Form for $propertyName to collect tenant registrations directly into PG Manager.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onCreateFormClick,
                enabled = !isCreating,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("create_registration_form_button")
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = createButtonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
            }
        }
    }
}

@Composable
private fun FormUnavailableCard(
    lastError: String,
    isCreating: Boolean,
    onCreateReplacementClick: () -> Unit,
    buttonText: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        border = BorderStroke(1.dp, Color(0xFFFECACA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Registration Form Unavailable",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF991B1B)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = lastError,
                fontSize = 13.sp,
                color = Color(0xFFB91C1C),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onCreateReplacementClick,
                enabled = !isCreating,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("create_replacement_form_button")
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(text = buttonText, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }
    }
}

@Composable
private fun FormHistoryCard(history: List<FormVersionHistory>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth().testTag("form_history_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Form Version History (${history.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1E293B)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            history.forEach { item ->
                val dateStr = remember(item.replacedAt) {
                    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(item.replacedAt))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "v${item.formVersion} • ${item.formTitle}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Replaced on $dateStr ${if (item.replacedByEmail.isNotBlank()) "by ${item.replacedByEmail}" else ""}",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionsPreviewCard() {
    val questions = remember { QuestionKeys.ALL_QUESTIONS }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Form Questions (${questions.size} Fields)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(10.dp))

            questions.forEach { q ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = q.title,
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        fontWeight = FontWeight.Medium
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (q.isRequired) Color(0xFFEFF6FF) else Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = if (q.isRequired) "Required" else "Optional",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (q.isRequired) Color(0xFF2563EB) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
