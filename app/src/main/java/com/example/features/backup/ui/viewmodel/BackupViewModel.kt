package com.example.features.backup.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.backup.domain.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

enum class BackupState {
    IDLE,
    EXPORTING,
    IMPORTING,
    SUCCESS,
    FAILURE
}

data class BackupUiState(
    val state: BackupState = BackupState.IDLE,
    val errorMessage: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    fun createBackup(outputStream: OutputStream) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(state = BackupState.EXPORTING, errorMessage = null)
            val result = backupRepository.createBackup(outputStream)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(state = BackupState.SUCCESS)
                _messageEvent.emit("Backup created successfully")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                _uiState.value = _uiState.value.copy(state = BackupState.FAILURE, errorMessage = error)
                _messageEvent.emit("Backup failed: $error")
            }
        }
    }

    fun restoreBackup(inputStream: InputStream) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(state = BackupState.IMPORTING, errorMessage = null)
            val result = backupRepository.restoreBackup(inputStream)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(state = BackupState.SUCCESS)
                _messageEvent.emit("Data restored successfully")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                _uiState.value = _uiState.value.copy(state = BackupState.FAILURE, errorMessage = error)
                _messageEvent.emit("Restore failed: $error")
            }
        }
    }
    
    fun resetState() {
        _uiState.value = BackupUiState()
    }
}
