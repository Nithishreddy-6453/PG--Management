package com.example.features.googleform.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.features.googleform.data.auth.GoogleFormsAuthManager
import com.example.features.googleform.domain.model.GoogleAccountInfo
import com.example.features.googleform.domain.model.PendingRegistrationStatus
import com.example.features.googleform.domain.model.PendingTenantRegistration
import com.example.features.googleform.domain.usecase.GetPendingCountUseCase
import com.example.features.googleform.domain.usecase.GetPendingRegistrationsUseCase
import com.example.features.googleform.domain.usecase.RejectPendingTenantUseCase
import com.example.features.googleform.domain.usecase.SyncFormResponsesUseCase
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.properties.domain.repository.PropertyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RegistrationFilterTab {
    PENDING_REVIEW,
    ALL,
    ACCEPTED,
    REJECTED
}

data class PendingRegistrationsUiState(
    val propertyId: String = "",
    val propertyName: String = "",
    val googleAccount: GoogleAccountInfo = GoogleAccountInfo(),
    val registrations: List<PendingTenantRegistration> = emptyList(),
    val filteredRegistrations: List<PendingTenantRegistration> = emptyList(),
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val acceptedCount: Int = 0,
    val rejectedCount: Int = 0,
    val selectedTab: RegistrationFilterTab = RegistrationFilterTab.PENDING_REVIEW,
    val searchQuery: String = "",
    val isSyncing: Boolean = false,
    val needsConsent: Boolean = false,
    val consentIntent: Intent? = null,
    val actionMessage: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PendingRegistrationsViewModel @Inject constructor(
    private val currentPropertyManager: CurrentPropertyManager,
    private val propertyRepository: PropertyRepository,
    private val authManager: GoogleFormsAuthManager,
    private val getPendingRegistrationsUseCase: GetPendingRegistrationsUseCase,
    private val getPendingCountUseCase: GetPendingCountUseCase,
    private val syncFormResponsesUseCase: SyncFormResponsesUseCase,
    private val rejectPendingTenantUseCase: RejectPendingTenantUseCase
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(RegistrationFilterTab.PENDING_REVIEW)
    private val _searchQuery = MutableStateFlow("")
    private val _isSyncing = MutableStateFlow(false)
    private val _needsConsent = MutableStateFlow(false)
    private val _consentIntent = MutableStateFlow<Intent?>(null)
    private val _actionMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private data class QueryFilter(
        val tab: RegistrationFilterTab,
        val query: String
    )

    private data class ActionStatus(
        val isSyncing: Boolean,
        val needsConsent: Boolean,
        val consentIntent: Intent?,
        val actionMsg: String?,
        val errorMsg: String?
    )

    private val queryFilterFlow = combine(_selectedTab, _searchQuery) { tab, query ->
        QueryFilter(tab, query)
    }

    private val actionStatusFlow = combine(
        _isSyncing,
        _needsConsent,
        _consentIntent,
        _actionMessage
    ) { syncing, needsConsent, consentIntent, action ->
        ActionStatus(syncing, needsConsent, consentIntent, action, _errorMessage.value)
    }

    val uiState: StateFlow<PendingRegistrationsUiState> = currentPropertyManager.currentPropertyIdFlow
        .flatMapLatest { propId ->
            combine(
                propertyRepository.getPropertyFlow(propId),
                getPendingRegistrationsUseCase(propId),
                authManager.accountInfoFlow,
                queryFilterFlow,
                combine(actionStatusFlow, _errorMessage) { action, error -> action.copy(errorMsg = error) }
            ) { prop, allRegistrations, googleAccount, filter, status ->
                val propName = prop?.propertyName?.ifBlank { "Emerald Stays" } ?: "Emerald Stays"

                val total = allRegistrations.size
                val pending = allRegistrations.count { it.isPending }
                val accepted = allRegistrations.count { it.status == PendingRegistrationStatus.ACCEPTED }
                val rejected = allRegistrations.count { it.status == PendingRegistrationStatus.REJECTED }

                val filteredByTab = when (filter.tab) {
                    RegistrationFilterTab.PENDING_REVIEW -> allRegistrations.filter { it.isPending }
                    RegistrationFilterTab.ALL -> allRegistrations
                    RegistrationFilterTab.ACCEPTED -> allRegistrations.filter { it.status == PendingRegistrationStatus.ACCEPTED }
                    RegistrationFilterTab.REJECTED -> allRegistrations.filter { it.status == PendingRegistrationStatus.REJECTED }
                }

                val query = filter.query.trim().lowercase()
                val finalFiltered = if (query.isBlank()) {
                    filteredByTab
                } else {
                    filteredByTab.filter {
                        it.fullName.lowercase().contains(query) ||
                        it.phone.contains(query) ||
                        it.email.lowercase().contains(query) ||
                        it.occupation.lowercase().contains(query) ||
                        it.organization.lowercase().contains(query)
                    }
                }

                val requiresConsent = status.needsConsent || (googleAccount.isConnected && !googleAccount.hasResponsesReadonlyScope)

                PendingRegistrationsUiState(
                    propertyId = propId,
                    propertyName = propName,
                    googleAccount = googleAccount,
                    registrations = allRegistrations,
                    filteredRegistrations = finalFiltered,
                    totalCount = total,
                    pendingCount = pending,
                    acceptedCount = accepted,
                    rejectedCount = rejected,
                    selectedTab = filter.tab,
                    searchQuery = filter.query,
                    isSyncing = status.isSyncing,
                    needsConsent = requiresConsent,
                    consentIntent = status.consentIntent,
                    actionMessage = status.actionMsg,
                    errorMessage = status.errorMsg
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PendingRegistrationsUiState()
        )

    fun onSelectTab(tab: RegistrationFilterTab) {
        _selectedTab.value = tab
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun requestConsent() {
        _errorMessage.value = null
        _actionMessage.value = null
        _consentIntent.value = authManager.getConsentIntent()
    }

    fun clearConsentIntent() {
        _consentIntent.value = null
    }

    fun handleConsentResult(data: Intent?) {
        viewModelScope.launch {
            _isSyncing.value = true
            _consentIntent.value = null

            // Update auth state with newly granted permissions
            val authResult = authManager.handleSignInResult(data)
            val updatedAccount = authManager.refreshAccountState()

            if (authManager.hasResponsesScope() || updatedAccount.hasResponsesReadonlyScope) {
                _needsConsent.value = false
                _actionMessage.value = "Google Forms response access granted! Syncing responses..."

                // Auto-retry response synchronization once after successful consent
                val propId = uiState.value.propertyId
                if (propId.isNotBlank()) {
                    val result = syncFormResponsesUseCase(propId)
                    _isSyncing.value = false
                    when (result) {
                        is PgResult.Success -> {
                            val count = result.data
                            if (count > 0) {
                                _actionMessage.value = "Imported $count new response${if (count > 1) "s" else ""} successfully!"
                            } else {
                                _actionMessage.value = "Responses are up to date (no new submissions found)."
                            }
                        }
                        is PgResult.Failure -> {
                            _errorMessage.value = result.error.message
                        }
                    }
                } else {
                    _isSyncing.value = false
                }
            } else {
                _isSyncing.value = false
                _needsConsent.value = true
                _errorMessage.value = "Google response permission was not granted. Tap 'Authorize Google' to grant access."
            }
        }
    }

    fun handleConsentDismissed() {
        _consentIntent.value = null
        viewModelScope.launch {
            authManager.refreshAccountState()
            if (!authManager.hasResponsesScope()) {
                _needsConsent.value = true
                _errorMessage.value = "Authorization cancelled. Tap 'Authorize Google' when you are ready to import responses."
            }
        }
    }

    fun syncResponses() {
        val propId = uiState.value.propertyId
        if (propId.isBlank()) return

        viewModelScope.launch {
            _isSyncing.value = true
            _actionMessage.value = null
            _errorMessage.value = null

            // Check if responses scope is granted
            if (!authManager.hasResponsesScope()) {
                _isSyncing.value = false
                _needsConsent.value = true
                _consentIntent.value = authManager.getConsentIntent()
                return@launch
            }

            val result = syncFormResponsesUseCase(propId)
            _isSyncing.value = false

            when (result) {
                is PgResult.Success -> {
                    val count = result.data
                    _needsConsent.value = false
                    if (count > 0) {
                        _actionMessage.value = "Imported $count new response${if (count > 1) "s" else ""} successfully!"
                    } else {
                        _actionMessage.value = "Responses are up to date (no new submissions found)."
                    }
                }
                is PgResult.Failure -> {
                    if (result.error is PgError.ConsentRequiredError) {
                        _needsConsent.value = true
                        _consentIntent.value = result.error.consentIntent ?: authManager.getConsentIntent()
                        _errorMessage.value = "Additional Google permission required to read form responses."
                    } else {
                        _errorMessage.value = result.error.message
                    }
                }
            }
        }
    }

    fun rejectRegistration(cloudId: String, reason: String = "") {
        viewModelScope.launch {
            _actionMessage.value = null
            _errorMessage.value = null

            val result = rejectPendingTenantUseCase(cloudId, reason.ifBlank { "Rejected by owner" })
            when (result) {
                is PgResult.Success -> {
                    _actionMessage.value = "Registration marked as rejected."
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
