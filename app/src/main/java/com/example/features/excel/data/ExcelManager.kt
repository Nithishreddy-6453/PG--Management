package com.example.features.excel.data

import android.content.Context
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.repository.PgRepository
import com.example.features.excel.domain.model.DuplicateAction
import com.example.features.excel.domain.model.ExcelExportConfig
import com.example.features.excel.domain.model.ExportPeriodType
import com.example.features.excel.domain.model.ImportPreviewResult
import com.example.features.excel.domain.model.ImportValidationException
import com.example.features.excel.domain.model.TenantImportRow
import com.example.features.rent.domain.util.RentBillingEngine
import com.example.features.tenants.domain.repository.TenantRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pgRepository: PgRepository,
    private val tenantRepository: TenantRepository
) {

    suspend fun generateExportFile(config: ExcelExportConfig): File = withContext(Dispatchers.IO) {
        val currentProperty = pgRepository.currentProperty.firstOrNull()
        val ownerProfile = pgRepository.ownerProfile.firstOrNull()
        val allRooms = pgRepository.allRooms.firstOrNull().orEmpty().filter { !it.deleted }
        val allBeds = pgRepository.allBeds.firstOrNull().orEmpty().filter { !it.deleted }
        val allTenants = pgRepository.allTenants.firstOrNull().orEmpty()
        val allAssignments = pgRepository.allAssignments.firstOrNull().orEmpty().filter { !it.deleted }
        val allPayments = pgRepository.allPayments.firstOrNull().orEmpty().filter { !it.deleted }
        val allExpenses = pgRepository.allExpenses.firstOrNull().orEmpty().filter { !it.deleted }

        val activePgName = currentProperty?.propertyName ?: ownerProfile?.pgName ?: "Emerald Stays"

        // Resolve Report Period
        val cal = Calendar.getInstance()
        val reportPeriod: com.example.features.reports.domain.model.ReportPeriod = when (config.periodType) {
            ExportPeriodType.THIS_MONTH -> {
                val currentMonthStr = RentBillingEngine.formatCanonicalBillingMonth(cal.time)
                com.example.features.reports.domain.model.ReportPeriod.Monthly(currentMonthStr)
            }
            ExportPeriodType.PREVIOUS_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                val prevMonthStr = RentBillingEngine.formatCanonicalBillingMonth(cal.time)
                com.example.features.reports.domain.model.ReportPeriod.Monthly(prevMonthStr)
            }
            ExportPeriodType.CUSTOM_RANGE -> {
                val start = config.customStartDate.ifBlank { "1970-01-01" }
                val end = config.customEndDate.ifBlank { "2099-12-31" }
                com.example.features.reports.domain.model.ReportPeriod.CustomRange(start, end)
            }
            ExportPeriodType.ALL_DATA -> {
                val currentMonthStr = RentBillingEngine.formatCanonicalBillingMonth(cal.time)
                com.example.features.reports.domain.model.ReportPeriod.Monthly(currentMonthStr)
            }
        }

        val report = com.example.features.reports.domain.engine.ReportCalculationEngine.calculateReport(
            property = currentProperty,
            period = reportPeriod,
            rooms = allRooms,
            beds = allBeds,
            tenants = allTenants,
            assignments = allAssignments,
            payments = allPayments,
            expenses = allExpenses
        )

        // 1. Sheet: Owner Overview
        val summarySheet = ExcelSheetData(
            title = "Owner Overview",
            headers = listOf("Field", "Value"),
            rows = listOf(
                listOf("Property Name", report.overview.propertyName),
                listOf("Report Month", report.overview.reportMonth),
                listOf("Report Period", report.overview.reportPeriod),
                listOf("Generated At", report.overview.generatedAt),
                listOf("Last Updated", report.overview.lastUpdated),
                listOf("", ""),
                listOf("OCCUPANCY", ""),
                listOf("Tenants / PG Capacity", report.overview.tenantsCapacityRatioText),
                listOf("Occupancy Rate", "${String.format(Locale.US, "%.1f", report.overview.occupancyPercentage)}%"),
                listOf("Vacancies", "${report.overview.vacanciesCount} Vacanc${if (report.overview.vacanciesCount == 1) "y" else "ies"}"),
                listOf("", ""),
                listOf("FINANCIAL SUMMARY (MONEY)", ""),
                listOf("Rent Due", report.overview.rentDue),
                listOf("Rent Received for This Month", report.overview.rentReceivedThisMonth),
                listOf("Rent Still to Collect", report.overview.rentStillToCollect),
                listOf("Previous Dues Received", report.overview.previousDuesReceived),
                listOf("Advance / Credit", report.overview.advanceCredit),
                listOf("Total Rent Collected", report.overview.totalRentCollected),
                listOf("Total Expenses", report.overview.expenses),
                listOf("Money Left After Expenses", report.overview.moneyLeftAfterExpenses)
            )
        )

        // 2. Sheet: Monthly History
        val monthlyHistorySheet = ExcelSheetData(
            title = "Monthly History",
            headers = listOf("Month", "Rent Due", "Rent Collected", "Previous Dues Received", "Advance / Credit", "Rent Still to Collect", "Expenses", "Money Left After Expenses", "Active Tenants", "Total Capacity", "Occupancy %", "Available Beds"),
            rows = report.monthlyHistory.map { h ->
                listOf(
                    h.month,
                    h.rentDue,
                    h.rentCollected,
                    h.previousDuesReceived,
                    h.advanceCredit,
                    h.rentStillToCollect,
                    h.expenses,
                    h.moneyLeftAfterExpenses,
                    h.activeTenants,
                    h.totalCapacity,
                    "${String.format(Locale.US, "%.1f", h.occupancyPercentage)}%",
                    h.availableBeds
                )
            }
        )

        // 3. Sheet: Rent Collection
        val rentCollectionSheet = ExcelSheetData(
            title = "Rent Collection",
            headers = listOf("Tenant Name", "Room", "Bed", "Monthly Rent", "Rent Due", "Rent Collected", "Rent Still to Collect", "Advance / Credit", "Status", "Due Date", "Payment Date", "Basis / Proration"),
            rows = report.rentMetrics.tenantRentRecords.map { r ->
                listOf(
                    r.tenantName,
                    r.roomNumber,
                    r.bedId,
                    r.standardMonthlyRent,
                    r.rentDue,
                    r.rentCollected,
                    r.rentStillToCollect,
                    r.advanceCredit,
                    r.status,
                    r.dueDate,
                    r.paymentDate ?: "N/A",
                    r.basisExplanation
                )
            }
        )

        // 4. Sheet: Expenses
        val expensesSheet = ExcelSheetData(
            title = "Expenses",
            headers = listOf("Date", "Category", "Title / Description", "Amount", "Payment Method", "Vendor", "Notes"),
            rows = report.expenseReport.expenseRecords.map { e ->
                val desc = if (e.title.isNotBlank()) e.title else e.notes
                listOf(
                    e.date,
                    e.category,
                    desc,
                    e.amount,
                    e.paymentMethod,
                    e.vendor ?: "N/A",
                    e.notes
                )
            }
        )

        // 5. Sheet: Room Status
        val roomsSheet = ExcelSheetData(
            title = "Room Status",
            headers = listOf("Room Number", "Floor", "Capacity", "Occupied Beds", "Available Beds", "Blocked Beds", "Occupancy %", "Status", "Active Tenants"),
            rows = report.bedOccupancy.roomsDetail.map { r ->
                listOf(
                    r.roomNumber,
                    r.floor,
                    r.capacity,
                    r.occupiedBeds,
                    r.availableBeds,
                    r.blockedBeds,
                    "${String.format(Locale.US, "%.1f", r.occupancyPercentage)}%",
                    r.status,
                    r.activeTenants.joinToString(", ")
                )
            }
        )

        // 6. Sheet: Tenant Statements
        val tenantStatementsSheet = ExcelSheetData(
            title = "Tenant Statements",
            headers = listOf("Tenant Name", "Room", "Phone", "Previous Outstanding", "Current Month Balance", "Total Currently Due", "Advance / Credit"),
            rows = report.tenantStatements.map { ts ->
                listOf(
                    ts.tenantName,
                    ts.roomNumber,
                    ts.phone,
                    ts.previousOutstanding,
                    ts.currentMonthBalance,
                    ts.totalCurrentlyDue,
                    ts.advanceCredit
                )
            }
        )

        // 7. Sheet: Raw Rent Ledger
        val rawRentLedgerSheet = ExcelSheetData(
            title = "Raw Rent Ledger",
            headers = listOf("Payment ID", "Cloud ID", "Tenant ID", "Tenant Name", "Room Number", "Billing Month", "Amount Expected", "Amount Paid", "Due Date", "Payment Date", "Status"),
            rows = allPayments.map { p ->
                listOf(
                    p.id,
                    p.cloudId,
                    p.tenantId,
                    p.tenantName,
                    p.roomNumber,
                    p.billingMonth,
                    p.amount,
                    p.amountPaid,
                    p.dueDate,
                    p.paymentDate ?: "N/A",
                    p.status
                )
            }
        )

        // 8. Sheet: Payments
        val paymentsSheet = ExcelSheetData(
            title = "Payments",
            headers = listOf("Payment Date", "Tenant Name", "Room", "Bed", "Billing Month", "Amount", "Payment Method", "Reference", "Allocation Type", "Notes"),
            rows = report.paymentHistory.map { p ->
                listOf(
                    p.paymentDate,
                    p.tenantName,
                    p.roomNumber,
                    p.bedId,
                    p.billingMonth,
                    p.amount,
                    p.paymentMethod,
                    p.reference,
                    p.allocationType,
                    p.notes
                )
            }
        )

        // 9. Sheet: Raw Expense Ledger
        val rawExpenseSheet = ExcelSheetData(
            title = "Raw Expense Ledger",
            headers = listOf("Expense ID", "Date", "Title", "Category", "Amount", "Payment Method", "Vendor", "Notes", "Sync Status"),
            rows = allExpenses.map { e ->
                listOf(
                    e.id,
                    e.date,
                    e.title,
                    e.category,
                    e.amount,
                    e.paymentMethod,
                    e.vendor ?: "N/A",
                    e.notes,
                    e.syncStatus
                )
            }
        )

        // 10. Sheet: Expense Payments
        val expensePaymentsSheet = ExcelSheetData(
            title = "Expense Payments",
            headers = listOf("Date", "Category", "Title", "Amount Paid", "Payment Method", "Vendor"),
            rows = allExpenses.filter { !it.paymentMethod.equals("Pending", true) && !it.paymentMethod.equals("Unpaid", true) }.map { e ->
                listOf(
                    e.date,
                    e.category,
                    e.title,
                    e.amount,
                    e.paymentMethod,
                    e.vendor ?: "N/A"
                )
            }
        )

        // 11. Sheet: Backup Log
        val backupLogSheet = ExcelSheetData(
            title = "Backup Log",
            headers = listOf("Timestamp", "Operation", "Property Name", "Billing Month", "Status", "Details"),
            rows = listOf(
                listOf(report.overview.generatedAt, "Master Excel Export", report.overview.propertyName, report.overview.reportMonth, "Success", "Exported 11 worksheets with complete owner-friendly model")
            )
        )

        val sheets = listOf(
            summarySheet,
            monthlyHistorySheet,
            rentCollectionSheet,
            expensesSheet,
            roomsSheet,
            tenantStatementsSheet,
            rawRentLedgerSheet,
            paymentsSheet,
            rawExpenseSheet,
            expensePaymentsSheet,
            backupLogSheet
        )

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanPgName = activePgName.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val exportFile = File(context.cacheDir, "${cleanPgName}_Export_${timeStamp}.xlsx")
        FileOutputStream(exportFile).use { fos ->
            ExcelWriter.writeWorkbook(sheets, fos)
        }
        exportFile
    }

    suspend fun generateTemplateFile(): File = withContext(Dispatchers.IO) {
        val headers = listOf("Name", "Room", "Phone", "Monthly Rent", "Join Date", "Vacate Date", "Status")
        val sampleRows = listOf(
            listOf("Rahul Sharma", "101", "9876543210", 8500, "2026-08-01", "", "Active"),
            listOf("Priya Patel", "102", "9876543211", 9000, "2026-08-15", "", "Active"),
            listOf("Amit Kumar", "103", "9876543212", 7500, "2026-09-01", "", "Active")
        )
        val templateSheet = ExcelSheetData(
            title = "Tenants",
            headers = headers,
            rows = sampleRows
        )

        val templateFile = File(context.cacheDir, "Tenant_Import_Template.xlsx")
        FileOutputStream(templateFile).use { fos ->
            ExcelWriter.writeWorkbook(listOf(templateSheet), fos)
        }
        templateFile
    }

    suspend fun parseAndValidateImportFile(inputStream: InputStream, fileName: String): ImportPreviewResult = withContext(Dispatchers.IO) {
        val rawRows = try {
            ExcelReader.readFirstSheet(inputStream)
        } catch (e: Exception) {
            throw ImportValidationException(e.message ?: "Failed to read Excel (.xlsx) file.")
        }

        if (rawRows.isEmpty()) {
            throw ImportValidationException("The selected Excel file is empty.")
        }

        // Find header row
        var headerRowIndex = -1
        var nameCol = -1
        var roomCol = -1
        var bedCol = -1
        var phoneCol = -1
        var rentCol = -1
        var joinDateCol = -1
        var vacateDateCol = -1
        var statusCol = -1

        for (i in 0 until minOf(5, rawRows.size)) {
            val row = rawRows[i]
            for (j in row.indices) {
                val cell = row[j].trim().lowercase(Locale.getDefault())
                if (cell.contains("name") && !cell.contains("room")) nameCol = j
                if (cell.contains("room")) roomCol = j
                if (cell.contains("bed") && !cell.contains("room")) bedCol = j
                if (cell.contains("phone") || cell.contains("mobile") || cell.contains("contact")) phoneCol = j
                if (cell.contains("rent") || cell.contains("amount") || cell.contains("fee")) rentCol = j
                if (cell.contains("join") || cell.contains("move") || cell.contains("start")) joinDateCol = j
                if (cell.contains("vacat") || cell.contains("end") || cell.contains("leave")) vacateDateCol = j
                if (cell.contains("status")) statusCol = j
            }
            if (nameCol != -1 && roomCol != -1) {
                headerRowIndex = i
                break
            }
        }

        if (headerRowIndex == -1 || nameCol == -1 || roomCol == -1) {
            throw ImportValidationException("Required columns 'Name' and 'Room' were not found in the Excel file.")
        }

        val existingTenants = pgRepository.allTenants.firstOrNull().orEmpty()
        val existingRooms = pgRepository.allRooms.firstOrNull().orEmpty().filter { !it.deleted }

        val parsedRows = mutableListOf<TenantImportRow>()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        for (rowIndex in (headerRowIndex + 1) until rawRows.size) {
            val row = rawRows[rowIndex]
            val rowDisplayNumber = rowIndex + 1

            val name = row.getOrNull(nameCol)?.trim().orEmpty()
            val room = row.getOrNull(roomCol)?.trim().orEmpty()

            // Skip empty spacer rows
            if (name.isBlank() && room.isBlank()) {
                continue
            }

            if (name.isBlank()) {
                throw ImportValidationException("Tenant Name is missing on row $rowDisplayNumber.")
            }
            if (room.isBlank()) {
                throw ImportValidationException("Room number is missing for '$name' on row $rowDisplayNumber.")
            }

            val bedId = if (bedCol != -1) row.getOrNull(bedCol)?.trim().orEmpty() else ""
            val phone = if (phoneCol != -1) row.getOrNull(phoneCol)?.trim().orEmpty() else ""
            val rentStr = if (rentCol != -1) row.getOrNull(rentCol)?.trim().orEmpty() else ""
            val monthlyRent = if (rentStr.isNotBlank()) {
                rentStr.replace(",", "").toDoubleOrNull()
                    ?: throw ImportValidationException("Invalid monthly rent value '$rentStr' on row $rowDisplayNumber.")
            } else {
                0.0
            }

            val joinDate = if (joinDateCol != -1) row.getOrNull(joinDateCol)?.trim().orEmpty() else ""
            val vacateDate = if (vacateDateCol != -1) row.getOrNull(vacateDateCol)?.trim().orEmpty() else ""
            val status = if (statusCol != -1) row.getOrNull(statusCol)?.trim().orEmpty() else "Active"

            // Duplicate detection
            var isDuplicate = false
            var duplicateReason = ""

            val matchingPhoneTenant = if (phone.isNotBlank()) {
                existingTenants.firstOrNull { it.phone.isNotBlank() && it.phone.filter { c -> c.isDigit() } == phone.filter { c -> c.isDigit() } }
            } else null

            if (matchingPhoneTenant != null) {
                isDuplicate = true
                duplicateReason = "Matches existing tenant '${matchingPhoneTenant.name}' with phone $phone"
            } else {
                val matchingNameRoomTenant = existingTenants.firstOrNull {
                    it.name.equals(name, ignoreCase = true) && it.roomNumber.equals(room, ignoreCase = true)
                }
                if (matchingNameRoomTenant != null) {
                    isDuplicate = true
                    duplicateReason = "Matches existing tenant '$name' in Room $room"
                }
            }

            // Check if room exists
            val roomExists = existingRooms.any { it.roomNumber.equals(room, ignoreCase = true) }

            parsedRows.add(
                TenantImportRow(
                    rowNumber = rowDisplayNumber,
                    name = name,
                    roomNumber = room,
                    bedId = bedId,
                    phone = phone,
                    monthlyRent = monthlyRent,
                    joinDate = joinDate.ifBlank { todayStr },
                    vacateDate = vacateDate,
                    status = status.ifBlank { "Active" },
                    isDuplicate = isDuplicate,
                    duplicateReason = duplicateReason,
                    roomExists = roomExists,
                    isSelected = true,
                    duplicateAction = if (isDuplicate) DuplicateAction.SKIP else DuplicateAction.IMPORT_AS_NEW
                )
            )
        }

        if (parsedRows.isEmpty()) {
            throw ImportValidationException("No valid tenant records found after the header row.")
        }

        val totalFound = parsedRows.size
        val duplicateCount = parsedRows.count { it.isDuplicate }
        val newCount = totalFound - duplicateCount
        val missingRoomCount = parsedRows.count { !it.roomExists }

        ImportPreviewResult(
            fileName = fileName,
            totalFound = totalFound,
            newCount = newCount,
            duplicateCount = duplicateCount,
            missingRoomCount = missingRoomCount,
            rows = parsedRows
        )
    }

    suspend fun executeImport(rows: List<TenantImportRow>): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        val currentPropId = tenantRepository.getCurrentPropertyId()
        val existingRooms = tenantRepository.getAllRooms()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        for (row in rows) {
            // Check if excluded or duplicate set to SKIP
            if (!row.isSelected) continue
            if (row.isDuplicate && row.duplicateAction == DuplicateAction.SKIP) continue

            // Ensure referenced room exists in Room table so foreign keys/occupancy calculations work
            val roomExists = existingRooms.any { it.roomNumber.equals(row.roomNumber, ignoreCase = true) }
            if (!roomExists) {
                val newRoom = RoomEntity(
                    roomNumber = row.roomNumber,
                    floor = "Ground",
                    capacity = 2,
                    ratePerBed = if (row.monthlyRent > 0) row.monthlyRent else 5000.0,
                    roomType = "Non-AC",
                    propertyId = currentPropId
                )
                tenantRepository.insertRoom(newRoom)
            }

            val isVacated = row.status.equals("Vacated", ignoreCase = true) ||
                    row.status.equals("Inactive", ignoreCase = true) ||
                    (row.vacateDate.isNotBlank() && row.vacateDate <= todayStr)

            // Determine bed safely
            val roomBeds = tenantRepository.getBedsForRoom(row.roomNumber).filter { !it.deleted }
            val explicitBed = row.bedId.trim()
            val targetBed = if (explicitBed.isNotBlank()) {
                roomBeds.firstOrNull { it.bedId.equals(explicitBed, ignoreCase = true) }
                    ?: run {
                        val newBed = com.example.data.database.BedEntity(
                            roomNumber = row.roomNumber,
                            bedId = explicitBed,
                            status = "AVAILABLE",
                            propertyId = currentPropId
                        )
                        tenantRepository.insertBed(newBed)
                        newBed
                    }
            } else {
                // Pick first available bed
                roomBeds.firstOrNull { it.status == "AVAILABLE" }
                    ?: run {
                        val nextNum = roomBeds.size + 1
                        val newBedId = "Bed $nextNum"
                        val newBed = com.example.data.database.BedEntity(
                            roomNumber = row.roomNumber,
                            bedId = newBedId,
                            status = "AVAILABLE",
                            propertyId = currentPropId
                        )
                        tenantRepository.insertBed(newBed)
                        newBed
                    }
            }

            val tenantEntity = TenantEntity(
                name = row.name,
                phone = row.phone,
                email = "",
                emergencyContact = "",
                roomNumber = if (isVacated) "" else row.roomNumber,
                bedId = if (isVacated) "" else targetBed.bedId,
                monthlyRent = row.monthlyRent,
                securityDeposit = 0.0,
                moveInDate = row.joinDate,
                isKycUploaded = false,
                kycDocType = "None",
                leavingDate = row.vacateDate,
                notes = if (row.vacateDate.isNotBlank()) "Vacate Date: ${row.vacateDate}" else "",
                propertyId = currentPropId,
                deleted = isVacated
            )

            val newId = tenantRepository.insertTenant(tenantEntity).toInt()

            if (!isVacated && targetBed.status != "BLOCKED") {
                val assignment = com.example.data.database.BedAssignmentEntity(
                    tenantId = newId,
                    roomNumber = row.roomNumber,
                    bedId = targetBed.bedId,
                    startDate = row.joinDate.ifBlank { todayStr },
                    endDate = if (row.vacateDate.isNotBlank()) row.vacateDate else null,
                    agreedRent = row.monthlyRent,
                    propertyId = currentPropId
                )
                tenantRepository.insertBedAssignment(assignment)
                tenantRepository.updateBed(targetBed.copy(status = "OCCUPIED"))
            }

            importedCount++
        }

        importedCount
    }

    private fun filterDataByPeriod(
        config: ExcelExportConfig,
        payments: List<com.example.data.database.RentPaymentEntity>,
        expenses: List<com.example.data.database.ExpenseEntity>
    ): Triple<String, List<com.example.data.database.RentPaymentEntity>, List<com.example.data.database.ExpenseEntity>> {
        val cal = Calendar.getInstance()
        val currentMonthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val isoMonthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        when (config.periodType) {
            ExportPeriodType.THIS_MONTH -> {
                val currentMonthStr = currentMonthFormat.format(cal.time) // e.g. "September 2026"
                val currentIsoMonth = isoMonthFormat.format(cal.time) // e.g. "2026-09"
                val periodLabel = "This Month ($currentMonthStr)"

                val filteredP = payments.filter { p ->
                    p.billingMonth.equals(currentMonthStr, ignoreCase = true) ||
                            (p.paymentDate?.startsWith(currentIsoMonth) == true) ||
                            p.dueDate.startsWith(currentIsoMonth)
                }
                val filteredE = expenses.filter { e ->
                    e.date.startsWith(currentIsoMonth)
                }
                return Triple(periodLabel, filteredP, filteredE)
            }
            ExportPeriodType.PREVIOUS_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                val prevMonthStr = currentMonthFormat.format(cal.time)
                val prevIsoMonth = isoMonthFormat.format(cal.time)
                val periodLabel = "Previous Month ($prevMonthStr)"

                val filteredP = payments.filter { p ->
                    p.billingMonth.equals(prevMonthStr, ignoreCase = true) ||
                            (p.paymentDate?.startsWith(prevIsoMonth) == true) ||
                            p.dueDate.startsWith(prevIsoMonth)
                }
                val filteredE = expenses.filter { e ->
                    e.date.startsWith(prevIsoMonth)
                }
                return Triple(periodLabel, filteredP, filteredE)
            }
            ExportPeriodType.CUSTOM_RANGE -> {
                val start = config.customStartDate.ifBlank { "1970-01-01" }
                val end = config.customEndDate.ifBlank { "2099-12-31" }
                val periodLabel = "Custom Range ($start to $end)"

                val filteredP = payments.filter { p ->
                    val pDate = p.paymentDate ?: p.dueDate
                    pDate in start..end
                }
                val filteredE = expenses.filter { e ->
                    e.date in start..end
                }
                return Triple(periodLabel, filteredP, filteredE)
            }
            ExportPeriodType.ALL_DATA -> {
                return Triple("All Historical Data", payments, expenses)
            }
        }
    }
}
