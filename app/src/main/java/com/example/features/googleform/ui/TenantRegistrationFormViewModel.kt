package com.example.features.googleform.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.PgResult
import com.example.features.googleform.data.auth.GoogleFormsAuthManager
import com.example.features.googleform.domain.model.FormAccessRequest
import com.example.features.googleform.domain.model.FormVersionHistory
import com.example.features.googleform.domain.model.GoogleAccountInfo
import com.example.features.googleform.domain.model.TenantRegistrationForm
import com.example.features.googleform.domain.repository.FormAccessRequestRepository
import com.example.features.googleform.domain.usecase.CheckFormStatusUseCase
import com.example.features.googleform.domain.usecase.CreateRegistrationFormUseCase
import com.example.features.googleform.domain.usecase.CreateReplacementFormUseCase
import com.example.features.googleform.domain.usecase.GetRegistrationFormUseCase
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.properties.domain.repository.PropertyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TenantRegistrationUiState(
    val propertyId: String = "",
    val propertyName: String = "",
    val googleAccount: GoogleAccountInfo = GoogleAccountInfo(),
    val form: TenantRegistrationForm? = null,
    val pendingResponsesCount: Int = 0,
    val accessRequests: List<FormAccessRequest> = emptyList(),
    val myPendingRequest: FormAccessRequest? = null,
    val incomingPendingRequests: List<FormAccessRequest> = emptyList(),
    val formHistory: List<FormVersionHistory> = emptyList(),
    val isCreating: Boolean = false,
    val isCheckingStatus: Boolean = false,
    val isSyncingResponses: Boolean = false,
    val isSubmittingRequest: Boolean = false,
    val isApprovingRequest: Boolean = false,
    val isRejectingRequest: Boolean = false,
    val showReplaceConfirmDialog: Boolean = false,
    val actionMessage: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TenantRegistrationFormViewModel @Inject constructor(
    private val currentPropertyManager: CurrentPropertyManager,
    private val propertyRepository: PropertyRepository,
    private val authManager: GoogleFormsAuthManager,
    private val getFormUseCase: GetRegistrationFormUseCase,
    private val createFormUseCase: CreateRegistrationFormUseCase,
    private val checkStatusUseCase: CheckFormStatusUseCase,
    private val createReplacementUseCase: CreateReplacementFormUseCase,
    private val getPendingCountUseCase: com.example.features.googleform.domain.usecase.GetPendingCountUseCase,
    private val syncResponsesUseCase: com.example.features.googleform.domain.usecase.SyncFormResponsesUseCase,
    private val accessRequestRepository: FormAccessRequestRepository,
    private val firebaseAuth: com.google.firebase.auth.FirebaseAuth
) : ViewModel() {

    private val _isCreating = MutableStateFlow(false)
    private val _isCheckingStatus = MutableStateFlow(false)
    private val _isSyncingResponses = MutableStateFlow(false)
    private val _isSubmittingRequest = MutableStateFlow(false)
    private val _isApprovingRequest = MutableStateFlow(false)
    private val _isRejectingRequest = MutableStateFlow(false)
    private val _showReplaceConfirmDialog = MutableStateFlow(false)
    private val _actionMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private data class StatusState(
        val isCreating: Boolean,
        val isChecking: Boolean,
        val isSyncing: Boolean,
        val isSubmitting: Boolean,
        val isApproving: Boolean,
        val isRejecting: Boolean,
        val showReplaceConfirm: Boolean,
        val actionMsg: String?,
        val errorMsg: String?
    )

    private val localStatusFlow = combine(
        _isCreating,
        _isCheckingStatus,
        _isSyncingResponses,
        _isSubmittingRequest,
        _isApprovingRequest,
        _isRejectingRequest,
        _showReplaceConfirmDialog,
        _actionMessage,
        _errorMessage
    ) { params ->
        StatusState(
            isCreating = params[0] as Boolean,
            isChecking = params[1] as Boolean,
            isSyncing = params[2] as Boolean,
            isSubmitting = params[3] as Boolean,
            isApproving = params[4] as Boolean,
            isRejecting = params[5] as Boolean,
            showReplaceConfirm = params[6] as Boolean,
            actionMsg = params[7] as String?,
            errorMsg = params[8] as String?
        )
    }

    val googleAccountState: StateFlow<GoogleAccountInfo> = authManager.accountInfoFlow

    val uiState: StateFlow<TenantRegistrationUiState> = currentPropertyManager.currentPropertyIdFlow
        .flatMapLatest { propId ->
            combine(
                listOf(
                    propertyRepository.getPropertyFlow(propId),
                    getFormUseCase(propId),
                    getPendingCountUseCase(propId),
                    authManager.accountInfoFlow,
                    accessRequestRepository.getRequestsForPropertyFlow(propId),
                    accessRequestRepository.getPendingIncomingRequestsFlow(),
                    accessRequestRepository.getFormHistoryForPropertyFlow(propId),
                    localStatusFlow
                )
            ) { flowsArray ->
                val prop = flowsArray[0] as com.example.data.database.PropertyEntity?
                val form = flowsArray[1] as TenantRegistrationForm?
                val pendingCount = flowsArray[2] as Int
                val googleAccount = flowsArray[3] as GoogleAccountInfo
                @Suppress("UNCHECKED_CAST")
                val requests = flowsArray[4] as List<FormAccessRequest>
                @Suppress("UNCHECKED_CAST")
                val incomingReqs = flowsArray[5] as List<FormAccessRequest>
                @Suppress("UNCHECKED_CAST")
                val history = flowsArray[6] as List<FormVersionHistory>
                val status = flowsArray[7] as StatusState

                val connectedEmail = googleAccount.email.trim()
                val formOwnerEmail = form?.googleAccountEmail?.trim() ?: ""

                val filteredIncomingReqs = incomingReqs.filter { req ->
                    val isNotMyOwnRequest = !req.requesterGoogleEmail.equals(connectedEmail, ignoreCase = true) &&
                            !req.requesterPgUid.equals(firebaseAuth.currentUser?.uid ?: "", ignoreCase = true)

                    val isConnectedFormOwner = connectedEmail.isNotBlank() && (
                        formOwnerEmail.isBlank() || connectedEmail.equals(formOwnerEmail, ignoreCase = true)
                    )

                    isNotMyOwnRequest && isConnectedFormOwner
                }

                val name = prop?.propertyName?.ifBlank { "Emerald Stays" } ?: "Emerald Stays"
                val currentUid = firebaseAuth.currentUser?.uid ?: ""
                val myReq = requests
                    .filter { req ->
                        (connectedEmail.isNotBlank() && req.requesterGoogleEmail.equals(connectedEmail, ignoreCase = true)) ||
                        (currentUid.isNotBlank() && req.requesterPgUid.equals(currentUid, ignoreCase = true))
                    }
                    .maxByOrNull { it.updatedAt }
                    ?: requests.firstOrNull { it.requesterGoogleEmail.equals(connectedEmail, ignoreCase = true) }
                TenantRegistrationUiState(
                    propertyId = propId,
                    propertyName = name,
                    googleAccount = googleAccount,
                    form = form,
                    pendingResponsesCount = pendingCount,
                    accessRequests = requests,
                    myPendingRequest = myReq,
                    incomingPendingRequests = filteredIncomingReqs,
                    formHistory = history,
                    isCreating = status.isCreating,
                    isCheckingStatus = status.isChecking,
                    isSyncingResponses = status.isSyncing,
                    isSubmittingRequest = status.isSubmitting,
                    isApprovingRequest = status.isApproving,
                    isRejectingRequest = status.isRejecting,
                    showReplaceConfirmDialog = status.showReplaceConfirm,
                    actionMessage = status.actionMsg,
                    errorMessage = status.errorMsg
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TenantRegistrationUiState()
        )

    fun getGoogleSignInIntent(): Intent {
        return authManager.getSignInIntent()
    }

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _actionMessage.value = null
            _errorMessage.value = null
            val result = authManager.handleSignInResult(data)
            when (result) {
                is PgResult.Success -> {
                    _actionMessage.value = "Google account connected successfully"
                }
                is PgResult.Failure -> {
                    _errorMessage.value = result.error.message
                }
            }
        }
    }

    fun disconnectGoogle() {
        viewModelScope.launch {
            authManager.disconnect()
            _actionMessage.value = "Google account disconnected"
        }
    }

    fun createRegistrationForm() {
        val currentState = uiState.value
        if (currentState.propertyId.isBlank()) return

        viewModelScope.launch {
            _isCreating.value = true
            _errorMessage.value = null
            _actionMessage.value = null

            val result = createFormUseCase(
                propertyId = currentState.propertyId,
                propertyName = currentState.propertyName
            )

            _isCreating.value = false
            when (result) {
                is PgResult.Success -> {
                    _actionMessage.value = "Tenant registration form created and published successfully!"
                }
                is PgResult.Failure -> {
                    _errorMessage.value = result.error.message
                }
            }
        }
    }

    fun checkStatus() {
        val currentState = uiState.value
        if (currentState.propertyId.isBlank()) return

        viewModelScope.launch {
            _isCheckingStatus.value = true
            _errorMessage.value = null
            _actionMessage.value = null

            val result = checkStatusUseCase(currentState.propertyId)
            _isCheckingStatus.value = false

            when (result) {
                is PgResult.Success -> {
                    if (result.data.isAvailable) {
                        _actionMessage.value = "Form verified: Active and ready for responses"
                    } else {
                        _errorMessage.value = result.data.lastError ?: "Registration form unavailable."
                    }
                }
                is PgResult.Failure -> {
                    _errorMessage.value = result.error.message
                }
            }
        }
    }

    fun checkFormAccessRequestStatus() {
        val currentState = uiState.value
        if (currentState.propertyId.isBlank()) return

        viewModelScope.launch {
            _isCheckingStatus.value = true
            _errorMessage.value = null
            _actionMessage.value = null

            val result = accessRequestRepository.checkRequestStatus(currentState.propertyId)
            _isCheckingStatus.value = false

            when (result) {
                is PgResult.Success -> {
                    val req = result.data
                    if (req != null && req.isApproved) {
                        _actionMessage.value = "Access Approved: Access to this registration form has been verified."
                    } else if (req != null && req.isRejected) {
                        _errorMessage.value = "Access Request Rejected: ${req.rejectionReason ?: "Denied by form owner."}"
                    } else if (req != null && req.isPending) {
                        _actionMessage.value = "Access Request Pending: Waiting for approval from ${req.ownerGoogleEmail.ifBlank { "form owner" }}."
                    } else {
                        _actionMessage.value = "No pending access request found."
                    }
                }
                is PgResult.Failure -> {
                    _errorMessage.value = "Unable to verify request status: ${result.error.message}"
                }
            }
        }
    }

    fun setShowReplaceConfirmDialog(show: Boolean) {
        _showReplaceConfirmDialog.value = show
    }

    fun confirmCreateReplacementForm() {
        _showReplaceConfirmDialog.value = false
        val currentState = uiState.value
        if (currentState.propertyId.isBlank()) return

        viewModelScope.launch {
            _isCreating.value = true
            _errorMessage.value = null
            _actionMessage.value = null

            val result = createReplacementUseCase(
                propertyId = currentState.propertyId,
                propertyName = currentState.propertyName
            )

            _isCreating.value = false
            when (result) {
                is PgResult.Success -> {
                    _actionMessage.value = "Replacement registration form created successfully!"
                }
                is PgResult.Failure -> {
                    _errorMessage.value = result.error.message
                }
            }
        }
    }

    fun requestFormAccess(requestNote: String = "") {
        val currentState = uiState.value
        if (currentState.propertyId.isBlank()) return

        val formId = currentState.form?.formId ?: ""
        val formTitle = currentState.form?.formTitle ?: "${currentState.propertyName} – Registration Form"

        viewModelScope.launch {
            _isSubmittingRequest.value = true
            _errorMessage.value = null
            _actionMessage.value = null

            val result = accessRequestRepository.createAccessRequest(
                propertyId = currentState.propertyId,
                formId = formId,
                formTitle = formTitle,
                requestNote = requestNote
            )

            _isSubmittingRequest.value = false
            when (result) {
                is PgResult.Success -> {
                    _actionMessage.value = "Form access request submitted to owner successfully!"
                }
                is PgResult.Failure -> {
                    _errorMessage.value = result.error.message
                }
            }
        }
    }

    fun approveFormAccessRequest(requestId: String) {
        viewModelScope.launch {
            _isApprovingRequest.value = true
            _errorMessage.value = null
            _actionMessage.value = null

            val result = accessRequestRepository.approveAccessRequest(requestId)
            _isApprovingRequest.value = false

            when (result) {
                is PgResult.Success -> {
                    _actionMessage.value = "Access granted! Google permission notification sent to requester."
                }
                is PgResult.Failure -> {
                    _errorMessage.value = result.error.message
                }
            }
        }
    }

    fun rejectFormAccessRequest(requestId: String, reason: String = "") {
        viewModelScope.launch {
            _isRejectingRequest.value = true
            _errorMessage.value = null
            _actionMessage.value = null

            val result = accessRequestRepository.rejectAccessRequest(requestId, reason)
            _isRejectingRequest.value = false

            when (result) {
                is PgResult.Success -> {
                    _actionMessage.value = "Access request rejected."
                }
                is PgResult.Failure -> {
                    _errorMessage.value = result.error.message
                }
            }
        }
    }

    fun syncResponses() {
        val currentState = uiState.value
        if (currentState.propertyId.isBlank()) return

        viewModelScope.launch {
            _isSyncingResponses.value = true
            _errorMessage.value = null
            _actionMessage.value = null

            val result = syncResponsesUseCase(currentState.propertyId)
            _isSyncingResponses.value = false

            when (result) {
                is PgResult.Success -> {
                    val count = result.data
                    if (count > 0) {
                        _actionMessage.value = "Imported $count new response${if (count > 1) "s" else ""} successfully!"
                    } else {
                        _actionMessage.value = "Responses up to date (no new submissions)."
                    }
                }
                is PgResult.Failure -> {
                    _errorMessage.value = result.error.message
                }
            }
        }
    }

    fun clearMessages() {
        _actionMessage.value = null
        _errorMessage.value = null
    }
}
