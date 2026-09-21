package com.example.features.settings.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.settings.domain.model.BusinessSettings
import com.example.features.settings.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BusinessSettingsUiState(
    val pgName: String = "",
    val ownerName: String = "",
    val contactNumber: String = "",
    val emailAddress: String = "",
    val address: String = "",
    val defaultMonthlyRent: String = "",
    val defaultSecurityDeposit: String = "",
    val defaultRentDueDay: String = "1",
    val currency: String = "₹",
    val pgNameError: String? = null,
    val ownerNameError: String? = null,
    val contactNumberError: String? = null,
    val emailAddressError: String? = null,
    val monthlyRentError: String? = null,
    val securityDepositError: String? = null,
    val dueDayError: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class BusinessSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BusinessSettingsUiState())
    val uiState: StateFlow<BusinessSettingsUiState> = _uiState.asStateFlow()

    private val _saveEvent = MutableSharedFlow<Boolean>()
    val saveEvent: SharedFlow<Boolean> = _saveEvent.asSharedFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.getBusinessSettings().collect { settings ->
                _uiState.value = _uiState.value.copy(
                    pgName = settings.pgName,
                    ownerName = settings.ownerName,
                    contactNumber = settings.contactNumber,
                    emailAddress = settings.emailAddress,
                    address = settings.address,
                    defaultMonthlyRent = if (settings.defaultMonthlyRent > 0) settings.defaultMonthlyRent.toString() else "",
                    defaultSecurityDeposit = if (settings.defaultSecurityDeposit > 0) settings.defaultSecurityDeposit.toString() else "",
                    defaultRentDueDay = settings.defaultRentDueDay.toString(),
                    currency = settings.currency
                )
            }
        }
    }

    fun onPgNameChange(value: String) {
        _uiState.value = _uiState.value.copy(pgName = value, pgNameError = null)
    }

    fun onOwnerNameChange(value: String) {
        _uiState.value = _uiState.value.copy(ownerName = value, ownerNameError = null)
    }

    fun onContactNumberChange(value: String) {
        _uiState.value = _uiState.value.copy(contactNumber = value, contactNumberError = null)
    }

    fun onEmailAddressChange(value: String) {
        _uiState.value = _uiState.value.copy(emailAddress = value, emailAddressError = null)
    }

    fun onAddressChange(value: String) {
        _uiState.value = _uiState.value.copy(address = value)
    }

    fun onMonthlyRentChange(value: String) {
        _uiState.value = _uiState.value.copy(defaultMonthlyRent = value, monthlyRentError = null)
    }

    fun onSecurityDepositChange(value: String) {
        _uiState.value = _uiState.value.copy(defaultSecurityDeposit = value, securityDepositError = null)
    }

    fun onDueDayChange(value: String) {
        _uiState.value = _uiState.value.copy(defaultRentDueDay = value, dueDayError = null)
    }

    fun onCurrencyChange(value: String) {
        _uiState.value = _uiState.value.copy(currency = value)
    }

    fun saveSettings() {
        val currentState = _uiState.value
        
        var isValid = true
        var pgNameError: String? = null
        var ownerNameError: String? = null
        var contactNumberError: String? = null
        var emailError: String? = null
        var rentError: String? = null
        var depositError: String? = null
        var dueDayError: String? = null

        if (currentState.pgName.isBlank()) {
            pgNameError = "PG Name cannot be empty"
            isValid = false
        }

        if (currentState.ownerName.isBlank()) {
            ownerNameError = "Owner Name cannot be empty"
            isValid = false
        }

        if (currentState.contactNumber.isNotBlank() && !currentState.contactNumber.replace("+", "").replace("-", "").replace(" ", "").all { it.isDigit() }) {
            contactNumberError = "Must contain valid digits"
            isValid = false
        }

        if (currentState.emailAddress.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(currentState.emailAddress).matches()) {
            emailError = "Invalid email format"
            isValid = false
        }

        val rent = currentState.defaultMonthlyRent.toDoubleOrNull() ?: 0.0
        if (rent <= 0) {
            rentError = "Monthly Rent must be greater than zero"
            isValid = false
        }

        val deposit = currentState.defaultSecurityDeposit.toDoubleOrNull() ?: 0.0
        if (currentState.defaultSecurityDeposit.isNotBlank() && deposit < 0) {
            depositError = "Security Deposit cannot be negative"
            isValid = false
        }

        val dueDay = currentState.defaultRentDueDay.toIntOrNull() ?: 0
        if (dueDay !in 1..31) {
            dueDayError = "Due Day must be between 1 and 31"
            isValid = false
        }

        if (!isValid) {
            _uiState.value = currentState.copy(
                pgNameError = pgNameError,
                ownerNameError = ownerNameError,
                contactNumberError = contactNumberError,
                emailAddressError = emailError,
                monthlyRentError = rentError,
                securityDepositError = depositError,
                dueDayError = dueDayError
            )
            return
        }

        _uiState.value = currentState.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val settings = BusinessSettings(
                    pgName = currentState.pgName.trim(),
                    ownerName = currentState.ownerName.trim(),
                    contactNumber = currentState.contactNumber.trim(),
                    emailAddress = currentState.emailAddress.trim(),
                    address = currentState.address.trim(),
                    defaultMonthlyRent = rent,
                    defaultSecurityDeposit = deposit,
                    defaultRentDueDay = dueDay,
                    currency = currentState.currency
                )
                settingsRepository.updateBusinessSettings(settings)
                _saveEvent.emit(true)
            } catch (e: Exception) {
                _saveEvent.emit(false)
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }
}
