package com.example.features.excel.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.features.excel.data.ExcelManager
import com.example.features.excel.domain.model.DuplicateAction
import com.example.features.excel.domain.model.ImportPreviewResult
import com.example.features.excel.domain.model.ImportValidationException
import com.example.features.excel.domain.model.TenantImportRow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

sealed class ImportUiState {
    object Idle : ImportUiState()
    data class Loading(val message: String) : ImportUiState()
    data class PreviewReady(val preview: ImportPreviewResult) : ImportUiState()
    data class ImportSuccess(val count: Int, val message: String) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}

@HiltViewModel
class ExcelImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val excelManager: ExcelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _shareIntentEvent = MutableSharedFlow<Intent>()
    val shareIntentEvent: SharedFlow<Intent> = _shareIntentEvent.asSharedFlow()

    fun downloadTemplate() {
        viewModelScope.launch {
            try {
                val file = excelManager.generateTemplateFile()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Tenant_Import_Template.xlsx")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Save or Share Excel Template").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                _shareIntentEvent.emit(chooser)
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("Failed to generate Excel template: ${e.message}")
            }
        }
    }

    fun onFileSelected(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Loading("Reading and validating Excel file...")
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.value = ImportUiState.Error("Could not open the selected file.")
                    return@launch
                }
                val preview = inputStream.use { stream ->
                    excelManager.parseAndValidateImportFile(stream, fileName)
                }
                _uiState.value = ImportUiState.PreviewReady(preview)
            } catch (e: ImportValidationException) {
                _uiState.value = ImportUiState.Error(e.message ?: "Validation error in Excel file.")
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("Failed to parse Excel file: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun toggleRowSelection(rowNumber: Int) {
        val current = _uiState.value
        if (current is ImportUiState.PreviewReady) {
            val updatedRows = current.preview.rows.map { row ->
                if (row.rowNumber == rowNumber) row.copy(isSelected = !row.isSelected) else row
            }
            _uiState.value = ImportUiState.PreviewReady(current.preview.copy(rows = updatedRows))
        }
    }

    fun setDuplicateAction(rowNumber: Int, action: DuplicateAction) {
        val current = _uiState.value
        if (current is ImportUiState.PreviewReady) {
            val updatedRows = current.preview.rows.map { row ->
                if (row.rowNumber == rowNumber) row.copy(duplicateAction = action) else row
            }
            _uiState.value = ImportUiState.PreviewReady(current.preview.copy(rows = updatedRows))
        }
    }

    fun setAllDuplicatesAction(action: DuplicateAction) {
        val current = _uiState.value
        if (current is ImportUiState.PreviewReady) {
            val updatedRows = current.preview.rows.map { row ->
                if (row.isDuplicate) row.copy(duplicateAction = action) else row
            }
            _uiState.value = ImportUiState.PreviewReady(current.preview.copy(rows = updatedRows))
        }
    }

    fun confirmImport() {
        val current = _uiState.value
        if (current is ImportUiState.PreviewReady) {
            val selectedRows = current.preview.rows.filter { it.isSelected }
            if (selectedRows.isEmpty()) {
                _uiState.value = ImportUiState.Error("No tenants selected for import.")
                return
            }

            viewModelScope.launch {
                _uiState.value = ImportUiState.Loading("Importing tenants into local database and sync queue...")
                try {
                    val count = excelManager.executeImport(selectedRows)
                    _uiState.value = ImportUiState.ImportSuccess(
                        count = count,
                        message = "Successfully imported $count tenant${if (count != 1) "s" else ""}."
                    )
                } catch (e: Exception) {
                    _uiState.value = ImportUiState.Error("Import failed: ${e.message}")
                }
            }
        }
    }

    fun reset() {
        _uiState.value = ImportUiState.Idle
    }
}
