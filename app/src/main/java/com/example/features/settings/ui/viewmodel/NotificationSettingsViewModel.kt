package com.example.features.settings.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.settings.domain.model.NotificationSettings
import com.example.features.settings.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationSettingsUiState(
    val settings: NotificationSettings = NotificationSettings(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val currentSettings = repository.getNotificationSettings().first()
                _uiState.value = NotificationSettingsUiState(settings = currentSettings, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = NotificationSettingsUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun updateRentDueReminder(enabled: Boolean) {
        updateSettings { it.copy(rentDueReminder = enabled) }
    }

    fun updateOverdueRentReminder(enabled: Boolean) {
        updateSettings { it.copy(overdueRentReminder = enabled) }
    }

    fun updateMonthlyInvoiceReminder(enabled: Boolean) {
        updateSettings { it.copy(monthlyInvoiceReminder = enabled) }
    }

    fun updateBackupReminder(enabled: Boolean) {
        updateSettings { it.copy(backupReminder = enabled) }
    }

    fun updateAppUpdates(enabled: Boolean) {
        updateSettings { it.copy(appUpdates = enabled) }
    }

    private fun updateSettings(updateParams: (NotificationSettings) -> NotificationSettings) {
        viewModelScope.launch {
            try {
                val current = _uiState.value.settings
                val updated = updateParams(current)
                repository.updateNotificationSettings(updated)
                _uiState.value = _uiState.value.copy(settings = updated)
            } catch (e: Exception) {
                _messageEvent.emit(e.message ?: "Failed to update settings")
            }
        }
    }
}
