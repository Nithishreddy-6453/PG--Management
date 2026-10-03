package com.example.features.googleform.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.PgResult
import com.example.features.googleform.data.auth.GoogleFormsAuthManager
import com.example.features.googleform.domain.model.GoogleAccountInfo
import com.example.features.googleform.domain.model.TenantRegistrationForm
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
    val isCreating: Boolean = false,
    val isCheckingStatus: Boolean = false,
    val isSyncingResponses: Boolean = false,
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
    private val syncResponsesUseCase: com.example.features.googleform.domain.usecase.SyncFormResponsesUseCase
) : ViewModel() {

    private val _isCreating = MutableStateFlow(false)
    private val _isCheckingStatus = MutableStateFlow(false)
    private val _isSyncingResponses = MutableStateFlow(false)
    private val _actionMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private data class StatusState(
        val isCreating: Boolean,
        val isChecking: Boolean,
        val isSyncing: Boolean,
        val actionMsg: String?,
        val errorMsg: String?
    )

    private val localStatusFlow = combine(
        _isCreating,
        _isCheckingStatus,
        _isSyncingResponses,
        _actionMessage,
        _errorMessage
    ) { isCreating, isChecking, isSyncing, actionMsg, errorMsg ->
        StatusState(isCreating, isChecking, isSyncing, actionMsg, errorMsg)
    }

    val googleAccountState: StateFlow<GoogleAccountInfo> = authManager.accountInfoFlow

    val uiState: StateFlow<TenantRegistrationUiState> = currentPropertyManager.currentPropertyIdFlow
        .flatMapLatest { propId ->
            combine(
                propertyRepository.getPropertyFlow(propId),
                getFormUseCase(propId),
                getPendingCountUseCase(propId),
                authManager.accountInfoFlow,
                localStatusFlow
            ) { prop, form, pendingCount, googleAccount, status ->
                val name = prop?.propertyName?.ifBlank { "Emerald Stays" } ?: "Emerald Stays"
                TenantRegistrationUiState(
                    propertyId = propId,
                    propertyName = name,
                    googleAccount = googleAccount,
                    form = form,
                    pendingResponsesCount = pendingCount,
                    isCreating = status.isCreating,
                    isCheckingStatus = status.isChecking,
                    isSyncingResponses = status.isSyncing,
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

    fun createReplacementForm() {
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
