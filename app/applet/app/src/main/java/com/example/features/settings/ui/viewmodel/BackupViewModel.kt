package com.example.features.settings.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.settings.domain.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BackupUiState {
    object Idle : BackupUiState
    object Loading : BackupUiState
    object Success : BackupUiState
    data class Error(val message: String) : BackupUiState
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Loading
            repository.createBackup(uri)
                .onSuccess { _uiState.value = BackupUiState.Success }
                .onFailure { _uiState.value = BackupUiState.Error(it.message ?: "Backup failed") }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Loading
            repository.restoreBackup(uri)
                .onSuccess { _uiState.value = BackupUiState.Success }
                .onFailure { _uiState.value = BackupUiState.Error(it.message ?: "Restore failed") }
        }
    }
    
    fun resetState() {
        _uiState.value = BackupUiState.Idle
    }
}
