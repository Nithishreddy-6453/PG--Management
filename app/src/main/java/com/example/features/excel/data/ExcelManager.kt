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
        val allTenants = pgRepository.allTenants.firstOrNull().orEmpty()
        val activeTenants = allTenants.filter { !it.deleted }
        val allPayments = pgRepository.allPayments.firstOrNull().orEmpty().filter { !it.deleted }
        val allExpenses = pgRepository.allExpenses.firstOrNull().orEmpty().filter { !it.deleted }

        val activePgName = currentProperty?.propertyName ?: ownerProfile?.pgName ?: "PG Manager"
        val location = listOfNotNull(currentProperty?.address, currentProperty?.city, currentProperty?.state)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { "Not Specified" }

        val (periodLabel, filteredPayments, filteredExpenses) = filterDataByPeriod(config, allPayments, allExpenses)

        // Summary Calculations
        val totalRooms = allRooms.size
        val totalBeds = allRooms.sumOf { it.capacity }
        val occupiedBeds = activeTenants.size.coerceAtMost(totalBeds)
        val availableBeds = (totalBeds - occupiedBeds).coerceAtLeast(0)
        val totalActiveTenants = activeTenants.size

        val totalRent = filteredPayments.sumOf { it.amount }
        val rentCollected = filteredPayments.sumOf { it.amountPaid }
        val rentPending = filteredPayments.sumOf { (it.amount - it.amountPaid).coerceAtLeast(0.0) }
        val totalExpenses = filteredExpenses.sumOf { it.amount }
        val netAmount = rentCollected - totalExpenses

        // 1. Sheet: Summary
        val summarySheet = ExcelSheetData(
            title = "Summary",
            headers = listOf("Metric / Field", "Value"),
            rows = listOf(
                listOf("PG Name", activePgName),
                listOf("Location", location),
                listOf("Report Period", periodLabel),
                listOf("Total Rooms", totalRooms),
                listOf("Total Beds", totalBeds),
                listOf("Occupied Beds", occupiedBeds),
                listOf("Available Beds", availableBeds),
                listOf("Total Active Tenants", totalActiveTenants),
                listOf("Total Rent", totalRent),
                listOf("Rent Collected", rentCollected),
                listOf("Rent Pending", rentPending),
                listOf("Total Expenses", totalExpenses),
                listOf("Net Amount", netAmount)
            )
        )

        // 2. Sheet: Tenants
        val tenantRows = allTenants.map { t ->
            listOf(
                t.name,
                t.roomNumber,
                t.phone.ifBlank { "N/A" },
                t.monthlyRent,
                t.moveInDate,
                if (t.deleted) "Vacated" else "N/A",
                if (t.deleted) "Vacated" else "Active"
            )
        }
        val tenantsSheet = ExcelSheetData(
            title = "Tenants",
            headers = listOf("Tenant Name", "Room Number", "Phone Number", "Monthly Rent", "Join Date", "Vacate Date", "Current Status"),
            rows = tenantRows
        )

        // 3. Sheet: Rent / Payments
        val paymentRows = filteredPayments.map { p ->
            val pendingAmount = (p.amount - p.amountPaid).coerceAtLeast(0.0)
            listOf(
                p.tenantName,
                p.roomNumber,
                p.billingMonth,
                p.amount,
                p.amountPaid,
                pendingAmount,
                p.paymentDate ?: p.dueDate,
                p.status
            )
        }
        val paymentsSheet = ExcelSheetData(
            title = "Rent & Payments",
            headers = listOf("Tenant Name", "Room Number", "Billing Month", "Rent Amount", "Amount Paid", "Pending Amount", "Payment Date", "Payment Status"),
            rows = paymentRows
        )

        // 4. Sheet: Expenses
        val expenseRows = filteredExpenses.map { e ->
            val desc = if (e.title.isNotBlank()) "${e.title} - ${e.notes}".trim(' ', '-') else e.notes
            listOf(
                e.date,
                e.category,
                desc,
                e.amount
            )
        }
        val expensesSheet = ExcelSheetData(
            title = "Expenses",
            headers = listOf("Date", "Category", "Description", "Amount"),
            rows = expenseRows
        )

        // 5. Sheet: Rooms
        val roomRows = allRooms.map { r ->
            val occ = activeTenants.count { it.roomNumber.equals(r.roomNumber, ignoreCase = true) }
            val avail = (r.capacity - occ).coerceAtLeast(0)
            listOf(
                r.roomNumber,
                r.capacity,
                occ,
                avail,
                r.ratePerBed
            )
        }
        val roomsSheet = ExcelSheetData(
            title = "Rooms",
            headers = listOf("Room Number", "Total Beds", "Occupied Beds", "Available Beds", "Room Rent"),
            rows = roomRows
        )

        val sheets = listOf(summarySheet, tenantsSheet, paymentsSheet, expensesSheet, roomsSheet)

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
                    row.vacateDate.isNotBlank()

            val tenantEntity = TenantEntity(
                name = row.name,
                phone = row.phone,
                email = "",
                emergencyContact = "",
                roomNumber = row.roomNumber,
                bedId = "Bed A",
                monthlyRent = row.monthlyRent,
                securityDeposit = 0.0,
                moveInDate = row.joinDate,
                isKycUploaded = false,
                kycDocType = "None",
                notes = if (row.vacateDate.isNotBlank()) "Vacate Date: ${row.vacateDate}" else "",
                propertyId = currentPropId,
                deleted = isVacated
            )

            tenantRepository.insertTenant(tenantEntity)
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
