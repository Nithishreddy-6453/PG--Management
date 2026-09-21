package com.example.features.excel.domain.model

enum class DuplicateAction {
    SKIP,
    IMPORT_AS_NEW
}

data class TenantImportRow(
    val rowNumber: Int,
    val name: String,
    val roomNumber: String,
    val phone: String,
    val monthlyRent: Double,
    val joinDate: String,
    val vacateDate: String,
    val status: String,
    val isDuplicate: Boolean = false,
    val duplicateReason: String = "",
    val roomExists: Boolean = true,
    val isSelected: Boolean = true,
    val duplicateAction: DuplicateAction = if (isDuplicate) DuplicateAction.SKIP else DuplicateAction.IMPORT_AS_NEW
)

data class ImportPreviewResult(
    val fileName: String,
    val totalFound: Int,
    val newCount: Int,
    val duplicateCount: Int,
    val missingRoomCount: Int,
    val rows: List<TenantImportRow>,
    val validationErrors: List<String> = emptyList()
)

class ImportValidationException(message: String) : Exception(message)
