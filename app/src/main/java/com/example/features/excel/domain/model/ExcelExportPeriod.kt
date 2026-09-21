package com.example.features.excel.domain.model

enum class ExportPeriodType(val label: String) {
    THIS_MONTH("This Month"),
    PREVIOUS_MONTH("Previous Month"),
    CUSTOM_RANGE("Custom Date Range"),
    ALL_DATA("All Data")
}

data class ExcelExportConfig(
    val periodType: ExportPeriodType = ExportPeriodType.THIS_MONTH,
    val customStartDate: String = "",
    val customEndDate: String = ""
)
