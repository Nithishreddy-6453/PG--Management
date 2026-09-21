package com.example.features.settings.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.settings.domain.model.NotificationSettings
import com.example.features.settings.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NotificationSettingsUiState {
    object Loading : NotificationSettingsUiState
    data class Success(val settings: NotificationSettings) : NotificationSettingsUiState
}

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<NotificationSettingsUiState> = repository.getNotificationSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NotificationSettingsUiState.Loading
        )

    fun updateSettings(settings: NotificationSettings) {
        viewModelScope.launch {
            repository.updateNotificationSettings(settings)
        }
    }
}
