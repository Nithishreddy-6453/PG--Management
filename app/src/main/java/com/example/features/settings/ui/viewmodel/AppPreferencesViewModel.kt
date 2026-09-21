package com.example.features.settings.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.settings.domain.model.AppSettings
import com.example.features.settings.domain.model.AppTheme
import com.example.features.settings.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppPreferencesViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSettings())
    val uiState: StateFlow<AppSettings> = _uiState.asStateFlow()

    private val _messageEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val messageEvent: kotlinx.coroutines.flow.SharedFlow<String> = _messageEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getAppSettings().collect { settings ->
                _uiState.value = settings
            }
        }
    }

    fun updateTheme(theme: AppTheme) {
        val newState = _uiState.value.copy(theme = theme)
        _uiState.value = newState
        saveSettings(newState)
    }

    fun updateDynamicColor(enabled: Boolean) {
        val newState = _uiState.value.copy(dynamicColor = enabled)
        _uiState.value = newState
        saveSettings(newState)
    }

    fun updateDateFormat(format: String) {
        val newState = _uiState.value.copy(dateFormat = format)
        _uiState.value = newState
        saveSettings(newState)
    }

    fun updateNumberFormat(format: String) {
        val newState = _uiState.value.copy(numberFormat = format)
        _uiState.value = newState
        saveSettings(newState)
    }

    fun updateLanguage(language: String) {
        val newState = _uiState.value.copy(language = language)
        _uiState.value = newState
        saveSettings(newState)
    }

    private fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateAppSettings(settings)
            _messageEvent.emit("Preferences saved")
        }
    }
}
