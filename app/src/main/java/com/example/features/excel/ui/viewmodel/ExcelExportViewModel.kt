package com.example.features.excel.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.excel.data.ExcelManager
import com.example.features.excel.domain.model.ExcelExportConfig
import com.example.features.excel.domain.model.ExportPeriodType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class ExportUiState {
    object Idle : ExportUiState()
    object Exporting : ExportUiState()
    data class Success(val file: File, val shareUri: Uri, val message: String) : ExportUiState()
    data class Error(val message: String) : ExportUiState()
}

@HiltViewModel
class ExcelExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val excelManager: ExcelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private val _config = MutableStateFlow(
        ExcelExportConfig(
            periodType = ExportPeriodType.THIS_MONTH,
            customStartDate = SimpleDateFormat("yyyy-MM-01", Locale.getDefault()).format(Date()),
            customEndDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
    )
    val config: StateFlow<ExcelExportConfig> = _config.asStateFlow()

    private val _shareIntentEvent = MutableSharedFlow<Intent>()
    val shareIntentEvent: SharedFlow<Intent> = _shareIntentEvent.asSharedFlow()

    fun setPeriodType(type: ExportPeriodType) {
        _config.value = _config.value.copy(periodType = type)
    }

    fun setCustomStartDate(date: String) {
        _config.value = _config.value.copy(customStartDate = date)
    }

    fun setCustomEndDate(date: String) {
        _config.value = _config.value.copy(customEndDate = date)
    }

    fun exportExcel() {
        viewModelScope.launch {
            _uiState.value = ExportUiState.Exporting
            try {
                val file = excelManager.generateExportFile(_config.value)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                _uiState.value = ExportUiState.Success(
                    file = file,
                    shareUri = uri,
                    message = "Excel report exported successfully."
                )

                // Prepare system share intent
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, file.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Save or Share Excel Report").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                _shareIntentEvent.emit(chooser)
            } catch (e: Exception) {
                _uiState.value = ExportUiState.Error(e.message ?: "Failed to export Excel report.")
            }
        }
    }

    fun resetState() {
        _uiState.value = ExportUiState.Idle
    }
}
