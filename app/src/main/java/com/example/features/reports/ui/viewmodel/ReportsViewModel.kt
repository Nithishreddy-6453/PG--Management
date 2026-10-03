package com.example.features.reports.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.ExpenseEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.features.dashboard.data.DashboardRepository
import com.example.features.excel.data.ExcelManager
import com.example.features.excel.domain.model.ExcelExportConfig
import com.example.features.excel.domain.model.ExportPeriodType
import com.example.features.rent.domain.util.CurrentBillingMonthManager
import com.example.features.rent.domain.util.RentBillingEngine
import com.example.features.reports.data.PdfReportGenerator
import com.example.features.reports.domain.engine.ReportCalculationEngine
import com.example.features.reports.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

sealed interface ReportsUiState {
    object Loading : ReportsUiState
    data class Success(
        val report: CompleteOwnerReport,
        val isCustomRange: Boolean = false,
        val metrics: FinancialMetrics,
        val revenueData: RevenueReportData,
        val expenseData: ExpenseReportData,
        val rentData: RentAnalyticsData,
        val occupancyData: OccupancyAnalyticsData,
        val filterMonth: String,
        val filterYear: String,
        val isExporting: Boolean = false,
        val exportMessage: String? = null
    ) : ReportsUiState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: DashboardRepository,
    private val pdfGenerator: PdfReportGenerator,
    private val excelManager: ExcelManager,
    private val currentBillingMonthManager: CurrentBillingMonthManager
) : ViewModel() {

    private val initialMonth = currentBillingMonthManager.getCurrentBillingMonth()
    private val _selectedPeriod = MutableStateFlow<ReportPeriod>(ReportPeriod.Monthly(initialMonth))
    val selectedPeriod: StateFlow<ReportPeriod> = _selectedPeriod.asStateFlow()

    private val _filterMonth = MutableStateFlow("All")
    private val _filterYear = MutableStateFlow("All")
    private val _isExporting = MutableStateFlow(false)
    private val _exportMessage = MutableStateFlow<String?>(null)

    fun selectMonth(month: String) {
        val canonical = RentBillingEngine.parseBillingMonth(month).canonicalName
        _selectedPeriod.value = ReportPeriod.Monthly(canonical)
        currentBillingMonthManager.setCurrentBillingMonth(canonical)
        _filterMonth.value = canonical.substringBefore(" ")
        _filterYear.value = canonical.substringAfter(" ")
    }

    fun goToPreviousMonth() {
        val current = when (val p = _selectedPeriod.value) {
            is ReportPeriod.Monthly -> p.billingMonth
            is ReportPeriod.CustomRange -> currentBillingMonthManager.getCurrentBillingMonth()
        }
        val prev = RentBillingEngine.getPreviousBillingMonth(current)
        selectMonth(prev)
    }

    fun goToNextMonth() {
        val current = when (val p = _selectedPeriod.value) {
            is ReportPeriod.Monthly -> p.billingMonth
            is ReportPeriod.CustomRange -> currentBillingMonthManager.getCurrentBillingMonth()
        }
        val next = RentBillingEngine.getNextBillingMonth(current)
        selectMonth(next)
    }

    fun selectCustomRange(startDate: String, endDate: String) {
        if (startDate.isNotBlank() && endDate.isNotBlank()) {
            _selectedPeriod.value = ReportPeriod.CustomRange(startDate, endDate)
        }
    }

    fun setFilterMonth(month: String) {
        _filterMonth.value = month
        if (month != "All") {
            val yr = if (_filterYear.value != "All") _filterYear.value else Calendar.getInstance().get(Calendar.YEAR).toString()
            selectMonth("$month $yr")
        }
    }

    fun setFilterYear(year: String) {
        _filterYear.value = year
        if (year != "All") {
            val mo = if (_filterMonth.value != "All") _filterMonth.value else "October"
            selectMonth("$mo $year")
        }
    }

    fun exportPdf(onReady: (File) -> Unit, onError: (String) -> Unit) {
        val state = uiState.value
        if (state !is ReportsUiState.Success) {
            onError("Report is not ready yet")
            return
        }
        viewModelScope.launch {
            try {
                _isExporting.value = true
                val file = pdfGenerator.generatePdf(state.report)
                _exportMessage.value = "PDF report generated successfully"
                onReady(file)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to generate PDF")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportExcel(onReady: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _isExporting.value = true
                val period = _selectedPeriod.value
                val config = when (period) {
                    is ReportPeriod.Monthly -> ExcelExportConfig(periodType = ExportPeriodType.THIS_MONTH)
                    is ReportPeriod.CustomRange -> ExcelExportConfig(
                        periodType = ExportPeriodType.CUSTOM_RANGE,
                        customStartDate = period.startDate,
                        customEndDate = period.endDate
                    )
                }
                val file = excelManager.generateExportFile(config)
                _exportMessage.value = "Excel report exported successfully"
                onReady(file)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to export Excel")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    val uiState: StateFlow<ReportsUiState> = combine(
        repository.getCurrentPropertyFlow(),
        repository.getRoomsFlow(),
        repository.getBedsFlow(),
        repository.getTenantsFlow(),
        repository.getAssignmentsFlow(),
        repository.getPaymentsFlow(),
        repository.getExpensesFlow(),
        _selectedPeriod,
        _isExporting,
        _exportMessage
    ) { args: Array<Any?> ->
        val property = args[0] as? com.example.data.database.PropertyEntity
        @Suppress("UNCHECKED_CAST")
        val rooms = args[1] as List<RoomEntity>
        @Suppress("UNCHECKED_CAST")
        val beds = args[2] as List<com.example.data.database.BedEntity>
        @Suppress("UNCHECKED_CAST")
        val tenants = args[3] as List<TenantEntity>
        @Suppress("UNCHECKED_CAST")
        val assignments = args[4] as List<com.example.data.database.BedAssignmentEntity>
        @Suppress("UNCHECKED_CAST")
        val payments = args[5] as List<RentPaymentEntity>
        @Suppress("UNCHECKED_CAST")
        val expenses = args[6] as List<ExpenseEntity>
        val period = args[7] as ReportPeriod
        val exporting = args[8] as Boolean
        val exportMsg = args[9] as? String

        val report = ReportCalculationEngine.calculateReport(
            property = property,
            period = period,
            rooms = rooms,
            beds = beds,
            tenants = tenants,
            assignments = assignments,
            payments = payments,
            expenses = expenses
        )

        // Compatibility objects
        val metrics = FinancialMetrics(
            totalRevenue = report.overview.totalRentCollected,
            totalExpenses = report.overview.expenses,
            netProfit = report.overview.moneyLeftAfterExpenses,
            outstandingRent = report.overview.rentStillToCollect,
            collectionRate = report.rentMetrics.collectionRate / 100.0,
            totalRooms = report.bedOccupancy.totalRooms,
            occupiedRooms = report.bedOccupancy.occupiedBeds,
            vacantRooms = report.bedOccupancy.availableBeds,
            occupancyRate = report.overview.occupancyPercentage / 100.0,
            vacancyRate = if (report.overview.totalPgCapacity > 0) (report.overview.vacanciesCount.toDouble() / report.overview.totalPgCapacity.toDouble()) else 0.0
        )

        val monthlyRevPoints = report.monthlyHistory.map {
            ChartPoint(it.month, it.rentCollected)
        }
        val revenueData = RevenueReportData(monthlyRevenue = monthlyRevPoints)

        val categoryPoints = report.expenseReport.categoryBreakdown.map {
            ChartPoint(it.category, it.amount)
        }
        val expenseData = ExpenseReportData(
            categoryBreakdown = categoryPoints,
            highestCategories = categoryPoints.take(3)
        )

        val rentData = RentAnalyticsData(
            paidRent = report.rentMetrics.rentCollectedCurrentMonth,
            pendingRent = report.rentMetrics.rentStillToCollect,
            partialPayments = report.rentMetrics.previousDuesReceived,
            collectionPercentage = report.rentMetrics.collectionRate / 100.0
        )

        val occupancyData = OccupancyAnalyticsData(
            totalRooms = report.bedOccupancy.totalRooms,
            occupiedRooms = report.bedOccupancy.occupiedBeds,
            vacantRooms = report.bedOccupancy.availableBeds,
            occupancyPercentage = report.overview.occupancyPercentage / 100.0
        )

        val currentMonthStr = when (period) {
            is ReportPeriod.Monthly -> period.billingMonth
            is ReportPeriod.CustomRange -> period.startDate
        }
        val parsedMonth = RentBillingEngine.parseBillingMonth(currentMonthStr)

        ReportsUiState.Success(
            report = report,
            isCustomRange = period is ReportPeriod.CustomRange,
            metrics = metrics,
            revenueData = revenueData,
            expenseData = expenseData,
            rentData = rentData,
            occupancyData = occupancyData,
            filterMonth = parsedMonth.canonicalName.substringBefore(" "),
            filterYear = parsedMonth.year.toString(),
            isExporting = exporting,
            exportMessage = exportMsg
        )
    }
    .debounce(50L)
    .distinctUntilChanged()
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportsUiState.Loading
    )
}
