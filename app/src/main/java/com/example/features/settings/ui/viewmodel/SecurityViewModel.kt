package com.example.features.settings.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.OwnerProfileEntity
import com.example.data.repository.PgRepository
import com.example.features.settings.domain.model.SecuritySettings
import com.example.features.settings.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SecurityUiState(
    val biometricEnabled: Boolean = false,
    val autoLockTimeout: Long = 0L,
    val requirePinOnResume: Boolean = true,
    val currentPinError: String? = null,
    val newPinError: String? = null,
    val confirmPinError: String? = null
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pgRepository: PgRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getSecuritySettings().collect { settings ->
                _uiState.value = _uiState.value.copy(
                    biometricEnabled = settings.biometricEnabled,
                    autoLockTimeout = settings.autoLockTimeout,
                    requirePinOnResume = settings.requirePinOnResume
                )
            }
        }
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSecuritySettings(
                SecuritySettings(
                    biometricEnabled = enabled,
                    autoLockTimeout = _uiState.value.autoLockTimeout,
                    requirePinOnResume = _uiState.value.requirePinOnResume
                )
            )
        }
    }

    fun updateAutoLockTimeout(timeout: Long) {
        viewModelScope.launch {
            settingsRepository.updateSecuritySettings(
                SecuritySettings(
                    biometricEnabled = _uiState.value.biometricEnabled,
                    autoLockTimeout = timeout,
                    requirePinOnResume = _uiState.value.requirePinOnResume
                )
            )
        }
    }

    fun updateRequirePinOnResume(require: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSecuritySettings(
                SecuritySettings(
                    biometricEnabled = _uiState.value.biometricEnabled,
                    autoLockTimeout = _uiState.value.autoLockTimeout,
                    requirePinOnResume = require
                )
            )
        }
    }

    fun changePin(currentPin: String, newPin: String, confirmPin: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentPinError = null,
                newPinError = null,
                confirmPinError = null
            )

            val profile = pgRepository.getProfile()
            val expectedPin = profile?.pinCode?.trim() ?: "1234"
            var isValid = true
            val cleanCurrentPin = currentPin.trim()
            val isMatch = withContext(Dispatchers.Default) {
                if (expectedPin.startsWith("$2")) {
                    try {
                        at.favre.lib.crypto.bcrypt.BCrypt.verifyer().verify(cleanCurrentPin.toCharArray(), expectedPin).verified
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    cleanCurrentPin == expectedPin
                }
            }

            if (!isMatch) {
                _uiState.value = _uiState.value.copy(currentPinError = "Incorrect current PIN")
                isValid = false
            }

            if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
                _uiState.value = _uiState.value.copy(newPinError = "PIN must be exactly 4 digits")
                isValid = false
            }

            if (newPin != confirmPin) {
                _uiState.value = _uiState.value.copy(confirmPinError = "PINs do not match")
                isValid = false
            }

            if (isValid) {
                val newHash = withContext(Dispatchers.Default) {
                    at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, newPin.toCharArray())
                }
                val updatedProfile = profile?.copy(pinCode = newHash) ?: OwnerProfileEntity(id = 1, pgName = "My PG", ownerName = "Owner", phone = "", upiId = "", pinCode = newHash)
                pgRepository.insertProfile(updatedProfile)
                _messageEvent.emit("PIN changed successfully")
            }
        }
    }
}
