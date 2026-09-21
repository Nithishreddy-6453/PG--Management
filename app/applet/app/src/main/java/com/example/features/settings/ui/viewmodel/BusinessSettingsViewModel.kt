package com.example.features.settings.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.settings.domain.model.BusinessSettings
import com.example.features.settings.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BusinessSettingsUiState {
    object Loading : BusinessSettingsUiState
    data class Success(val settings: BusinessSettings) : BusinessSettingsUiState
}

@HiltViewModel
class BusinessSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<BusinessSettingsUiState> = repository.getBusinessSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BusinessSettingsUiState.Loading
        )

    fun updateSettings(settings: BusinessSettings) {
        viewModelScope.launch {
            repository.updateBusinessSettings(settings)
        }
    }
}
