package com.example.features.settings.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.settings.domain.model.SecuritySettings
import com.example.features.settings.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SecuritySettingsUiState {
    object Loading : SecuritySettingsUiState
    data class Success(val settings: SecuritySettings) : SecuritySettingsUiState
}

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<SecuritySettingsUiState> = repository.getSecuritySettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SecuritySettingsUiState.Loading
        )

    fun updateSettings(settings: SecuritySettings) {
        viewModelScope.launch {
            repository.updateSecuritySettings(settings)
        }
    }
}
