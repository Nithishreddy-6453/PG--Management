package com.example.features.rent.data.repository

import com.example.data.database.PropertyDao
import com.example.data.database.RentPaymentDao
import com.example.data.database.RentPaymentEntity
import com.example.data.database.TenantDao
import com.example.data.sync.SyncCoordinator
import com.example.features.excel.data.ExcelDriveBackupManager
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rent.domain.model.MonthGenerationPreview
import com.example.features.rent.domain.repository.RentRepository
import com.example.features.rent.domain.repository.RentSummary
import com.example.features.rent.domain.util.CurrentBillingMonthManager
import com.example.features.rent.domain.util.RentBillingEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RentRepositoryImpl @Inject constructor(
    private val rentPaymentDao: RentPaymentDao,
    private val tenantDao: TenantDao,
    private val propertyDao: PropertyDao,
    private val currentPropertyManager: CurrentPropertyManager,
    private val currentBillingMonthManager: CurrentBillingMonthManager,
    private val syncCoordinator: SyncCoordinator? = null,
    private val excelDriveBackupManager: ExcelDriveBackupManager? = null
) : RentRepository {

    private fun getCurrentOwnerId(): String {
        return try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    override suspend fun recordPayment(payment: RentPaymentEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = getCurrentOwnerId()

        val parsedMonth = RentBillingEngine.parseBillingMonth(payment.billingMonth)
        val canonicalMonth = parsedMonth.canonicalName

        // Find if an existing active payment already exists using stable identity
        val existingPayment: RentPaymentEntity? = when {
            payment.id > 0 -> rentPaymentDao.getPaymentByIdIncludingDeleted(payment.id)
            payment.cloudId.isNotBlank() -> rentPaymentDao.getPaymentByCloudIdIncludingDeleted(payment.cloudId)
            payment.tenantId > 0 && canonicalMonth.isNotBlank() -> {
                rentPaymentDao.getPaymentForTenantPropertyAndMonth(payment.tenantId, currentPropId, canonicalMonth)
                    ?: rentPaymentDao.getPaymentForTenantAndMonth(payment.tenantId, canonicalMonth)
            }
            payment.tenantName.isNotBlank() && canonicalMonth.isNotBlank() -> {
                rentPaymentDao.getPaymentForTenantNamePropertyAndMonth(payment.tenantName, currentPropId, canonicalMonth)
            }
            else -> null
        }

        if (existingPayment != null && !existingPayment.deleted) {
            val expectedAmount = if (payment.amount > 0) payment.amount else existingPayment.amount
            val amountPaid = payment.amountPaid
            val calculatedStatus = RentBillingEngine.calculatePaymentStatus(
                expectedAmount = expectedAmount,
                amountPaid = amountPaid,
                dueDateStr = payment.dueDate.ifBlank { existingPayment.dueDate }
            )

            val updated = existingPayment.copy(
                tenantName = payment.tenantName.ifBlank { existingPayment.tenantName },
                roomNumber = payment.roomNumber.ifBlank { existingPayment.roomNumber },
                billingMonth = canonicalMonth,
                amount = expectedAmount,
                amountPaid = amountPaid,
                dueDate = payment.dueDate.ifBlank { existingPayment.dueDate },
                paymentDate = payment.paymentDate ?: existingPayment.paymentDate,
                paymentMode = payment.paymentMode ?: existingPayment.paymentMode,
                transactionReference = payment.transactionReference ?: existingPayment.transactionReference,
                remarks = payment.remarks ?: existingPayment.remarks,
                status = calculatedStatus,
                ownerId = if (existingPayment.ownerId.isNotBlank()) existingPayment.ownerId else ownerId,
                propertyId = if (existingPayment.propertyId.isNotBlank() && existingPayment.propertyId != "property_default") existingPayment.propertyId else currentPropId,
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
            rentPaymentDao.updatePayment(updated)
            syncCoordinator?.enqueueOperation("PAYMENT", updated.id.toString(), "UPDATE")
        } else {
            val calculatedStatus = RentBillingEngine.calculatePaymentStatus(
                expectedAmount = payment.amount,
                amountPaid = payment.amountPaid,
                dueDateStr = payment.dueDate
            )

            val deterministicCloudId = if (payment.cloudId.isNotBlank()) {
                payment.cloudId
            } else {
                RentBillingEngine.generateDeterministicCloudId(
                    propertyId = currentPropId,
                    tenantId = payment.tenantId,
                    billingMonth = canonicalMonth
                )
            }

            val toInsert = payment.copy(
                cloudId = deterministicCloudId,
                billingMonth = canonicalMonth,
                status = calculatedStatus,
                ownerId = if (payment.ownerId.isNotBlank()) payment.ownerId else ownerId,
                propertyId = if (payment.propertyId.isNotBlank() && payment.propertyId != "property_default") payment.propertyId else currentPropId,
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
            val insertedId = rentPaymentDao.insertPayment(toInsert)
            syncCoordinator?.enqueueOperation("PAYMENT", insertedId.toString(), "CREATE")
        }

        excelDriveBackupManager?.triggerAsyncBackup(currentPropId, "payment_recorded")
    }

    override suspend fun recordPaymentTransaction(
        paymentId: Int,
        paidAmountDelta: Double,
        paymentDate: String,
        paymentMode: String,
        transactionReference: String?,
        remarks: String?
    ) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = getCurrentOwnerId()
        val existing = rentPaymentDao.getPaymentByIdIncludingDeleted(paymentId) ?: return

        val newTotalPaid = existing.amountPaid + paidAmountDelta
        val calculatedStatus = RentBillingEngine.calculatePaymentStatus(
            expectedAmount = existing.amount,
            amountPaid = newTotalPaid,
            dueDateStr = existing.dueDate
        )

        val updated = existing.copy(
            amountPaid = newTotalPaid,
            paymentDate = paymentDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
            paymentMode = paymentMode.ifBlank { "UPI" },
            transactionReference = transactionReference ?: existing.transactionReference,
            remarks = remarks ?: existing.remarks,
            status = calculatedStatus,
            ownerId = if (existing.ownerId.isNotBlank()) existing.ownerId else ownerId,
            propertyId = if (existing.propertyId.isNotBlank() && existing.propertyId != "property_default") existing.propertyId else currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        rentPaymentDao.updatePayment(updated)
        syncCoordinator?.enqueueOperation("PAYMENT", updated.id.toString(), "UPDATE")
        excelDriveBackupManager?.triggerAsyncBackup(currentPropId, "payment_transaction")
    }

    override suspend fun updatePayment(payment: RentPaymentEntity) {
        recordPayment(payment)
    }

    override suspend fun deletePayment(id: Int) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        rentPaymentDao.softDeletePayment(id)
        syncCoordinator?.enqueueOperation("PAYMENT", id.toString(), "DELETE")
        excelDriveBackupManager?.triggerAsyncBackup(currentPropId, "payment_deleted")
    }

    override suspend fun getMonthGenerationPreview(billingMonthStr: String): MonthGenerationPreview {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val property = propertyDao.getProperty(currentPropId)
        val propertyName = property?.propertyName?.ifBlank { "Emerald Stays" } ?: "Emerald Stays"

        val parsedMonth = RentBillingEngine.parseBillingMonth(billingMonthStr)
        val tenants = tenantDao.getAllTenants(currentPropId)
        val existingPayments = rentPaymentDao.getPaymentsForPropertyAndMonth(currentPropId, parsedMonth.canonicalName)

        return RentBillingEngine.generateMonthPreview(
            propertyId = currentPropId,
            propertyName = propertyName,
            billingMonthStr = parsedMonth.canonicalName,
            allTenants = tenants,
            existingMonthPayments = existingPayments
        )
    }

    override suspend fun generateMonthRent(
        billingMonthStr: String,
        dueDateStr: String,
        backupPreviousMonth: Boolean
    ): MonthGenerationPreview {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = getCurrentOwnerId()
        val property = propertyDao.getProperty(currentPropId)
        val propertyName = property?.propertyName?.ifBlank { "Emerald Stays" } ?: "Emerald Stays"

        val parsedMonth = RentBillingEngine.parseBillingMonth(billingMonthStr)
        val canonicalMonth = parsedMonth.canonicalName

        // 1. If backup requested, perform previous month Excel backup to Google Drive
        if (backupPreviousMonth) {
            val previousMonth = RentBillingEngine.getPreviousBillingMonth(canonicalMonth)
            try {
                excelDriveBackupManager?.backupMonthLedgerToDrive(currentPropId, previousMonth)
            } catch (_: Exception) {}
        }

        val tenants = tenantDao.getAllTenants(currentPropId)
        val existingPayments = rentPaymentDao.getPaymentsForPropertyAndMonth(currentPropId, canonicalMonth)
        val existingTenantIds = existingPayments.filter { !it.deleted }.map { it.tenantId }.toSet()

        val finalDueDate = if (dueDateStr.isNotBlank()) {
            dueDateStr
        } else {
            // Default due date: 5th of the billing month
            val cal = Calendar.getInstance(Locale.US).apply {
                set(Calendar.YEAR, parsedMonth.year)
                set(Calendar.MONTH, parsedMonth.month1Based - 1)
                set(Calendar.DAY_OF_MONTH, minOf(5, parsedMonth.daysInMonth))
            }
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        }

        for (tenant in tenants.filter { !it.deleted && it.roomNumber.isNotBlank() }) {
            // Check if tenant already has an active record for this billing month
            if (existingTenantIds.contains(tenant.id)) {
                continue
            }

            val proration = RentBillingEngine.calculateProrationDetails(
                moveInDateStr = tenant.moveInDate,
                leavingDateStr = tenant.leavingDate,
                billingMonthStr = canonicalMonth
            )

            // Only generate record if tenant has applicable days this month
            if (proration.applicableDays > 0) {
                val expectedRent = RentBillingEngine.calculateExpectedRent(
                    monthlyRent = tenant.monthlyRent,
                    applicableDays = proration.applicableDays,
                    daysInMonth = proration.daysInMonth
                )

                val deterministicCloudId = RentBillingEngine.generateDeterministicCloudId(
                    propertyId = currentPropId,
                    tenantId = tenant.id,
                    billingMonth = canonicalMonth
                )

                val newInvoice = RentPaymentEntity(
                    cloudId = deterministicCloudId,
                    tenantId = tenant.id,
                    tenantName = tenant.name,
                    roomNumber = tenant.roomNumber,
                    billingMonth = canonicalMonth,
                    amount = expectedRent,
                    amountPaid = 0.0,
                    dueDate = finalDueDate,
                    paymentDate = null,
                    paymentMode = null,
                    status = "Pending",
                    ownerId = ownerId,
                    propertyId = currentPropId,
                    syncStatus = "PENDING_UPLOAD",
                    updatedAt = System.currentTimeMillis()
                )

                val insertedId = rentPaymentDao.insertPayment(newInvoice)
                syncCoordinator?.enqueueOperation("PAYMENT", insertedId.toString(), "CREATE")
            }
        }

        // Set the active billing month context to the newly generated month
        currentBillingMonthManager.setCurrentBillingMonth(canonicalMonth)
        excelDriveBackupManager?.triggerAsyncBackup(currentPropId, "month_generated")

        // Return refreshed preview
        val refreshedPayments = rentPaymentDao.getPaymentsForPropertyAndMonth(currentPropId, canonicalMonth)
        return RentBillingEngine.generateMonthPreview(
            propertyId = currentPropId,
            propertyName = propertyName,
            billingMonthStr = canonicalMonth,
            allTenants = tenants,
            existingMonthPayments = refreshedPayments
        )
    }

    override suspend fun generateMonthlyInvoices(dueDateStr: String, billingMonthStr: String) {
        generateMonthRent(billingMonthStr, dueDateStr, backupPreviousMonth = true)
    }

    override suspend fun ensureTenantRentForCurrentMonth(tenantId: Int) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = getCurrentOwnerId()
        val tenant = tenantDao.getTenantById(tenantId) ?: return
        if (tenant.deleted || tenant.roomNumber.isBlank()) return

        val currentMonth = currentBillingMonthManager.getCurrentBillingMonth()
        val parsedMonth = RentBillingEngine.parseBillingMonth(currentMonth)
        val canonicalMonth = parsedMonth.canonicalName

        // Check if current month rent already exists for any tenant in this property
        val existingMonthRecords = rentPaymentDao.getPaymentsForPropertyAndMonth(currentPropId, canonicalMonth)
        val isMonthActive = existingMonthRecords.any { !it.deleted }

        // If month is already generated, ensure this tenant has their record
        if (isMonthActive) {
            val tenantExisting = existingMonthRecords.firstOrNull { it.tenantId == tenant.id && !it.deleted }
            if (tenantExisting == null) {
                val proration = RentBillingEngine.calculateProrationDetails(
                    moveInDateStr = tenant.moveInDate,
                    leavingDateStr = tenant.leavingDate,
                    billingMonthStr = canonicalMonth
                )

                if (proration.applicableDays > 0) {
                    val expectedRent = RentBillingEngine.calculateExpectedRent(
                        monthlyRent = tenant.monthlyRent,
                        applicableDays = proration.applicableDays,
                        daysInMonth = proration.daysInMonth
                    )

                    val cal = Calendar.getInstance(Locale.US).apply {
                        set(Calendar.YEAR, parsedMonth.year)
                        set(Calendar.MONTH, parsedMonth.month1Based - 1)
                        set(Calendar.DAY_OF_MONTH, minOf(5, parsedMonth.daysInMonth))
                    }
                    val defaultDueDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

                    val deterministicCloudId = RentBillingEngine.generateDeterministicCloudId(
                        propertyId = currentPropId,
                        tenantId = tenant.id,
                        billingMonth = canonicalMonth
                    )

                    val record = RentPaymentEntity(
                        cloudId = deterministicCloudId,
                        tenantId = tenant.id,
                        tenantName = tenant.name,
                        roomNumber = tenant.roomNumber,
                        billingMonth = canonicalMonth,
                        amount = expectedRent,
                        amountPaid = 0.0,
                        dueDate = defaultDueDate,
                        paymentDate = null,
                        paymentMode = null,
                        status = "Pending",
                        ownerId = ownerId,
                        propertyId = currentPropId,
                        syncStatus = "PENDING_UPLOAD",
                        updatedAt = System.currentTimeMillis()
                    )

                    val insertedId = rentPaymentDao.insertPayment(record)
                    syncCoordinator?.enqueueOperation("PAYMENT", insertedId.toString(), "CREATE")
                    excelDriveBackupManager?.triggerAsyncBackup(currentPropId, "new_tenant_month_inserted")
                }
            }
        }
    }

    override suspend fun adjustTenantRentForLeaving(tenantId: Int, leavingDateStr: String) {
        val propId = currentPropertyManager.getCurrentPropertyId()
        val tenant = tenantDao.getTenantById(tenantId) ?: return

        if (leavingDateStr.isBlank()) return
        val leavingCal = RentBillingEngine.parseDate(leavingDateStr) ?: return
        val leavingMonthStr = RentBillingEngine.formatCanonicalBillingMonth(leavingCal.time)
        val parsedMonth = RentBillingEngine.parseBillingMonth(leavingMonthStr)

        val existingPayment = rentPaymentDao.getPaymentForTenantPropertyAndMonth(tenant.id, propId, parsedMonth.canonicalName)
            ?: rentPaymentDao.getPaymentForTenantAndMonth(tenant.id, parsedMonth.canonicalName)

        if (existingPayment != null && !existingPayment.deleted) {
            val proration = RentBillingEngine.calculateProrationDetails(
                moveInDateStr = tenant.moveInDate,
                leavingDateStr = leavingDateStr,
                billingMonthStr = parsedMonth.canonicalName
            )

            val newExpectedRent = RentBillingEngine.calculateExpectedRent(
                monthlyRent = tenant.monthlyRent,
                applicableDays = proration.applicableDays,
                daysInMonth = proration.daysInMonth
            )

            val newStatus = RentBillingEngine.calculatePaymentStatus(
                expectedAmount = newExpectedRent,
                amountPaid = existingPayment.amountPaid,
                dueDateStr = existingPayment.dueDate
            )

            val updated = existingPayment.copy(
                amount = newExpectedRent,
                status = newStatus,
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )

            rentPaymentDao.updatePayment(updated)
            syncCoordinator?.enqueueOperation("PAYMENT", updated.id.toString(), "UPDATE")
            excelDriveBackupManager?.triggerAsyncBackup(propId, "tenant_leaving_date_adjusted")
        }
    }

    override suspend fun getPendingPayments(): List<RentPaymentEntity> {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        return rentPaymentDao.getAllPaymentsForPropertySync(currentPropId)
            .filter { !it.deleted && (it.status == "Pending" || it.status == "Partially Paid" || it.status == "Partial" || it.status == "Overdue") }
    }

    override suspend fun getOverduePayments(): List<RentPaymentEntity> {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return rentPaymentDao.getAllPaymentsForPropertySync(currentPropId).filter { 
            !it.deleted && (it.status == "Pending" || it.status == "Partially Paid" || it.status == "Partial" || it.status == "Overdue") && it.dueDate < today 
        }
    }

    override fun getRentSummary(): Flow<RentSummary> {
        return combine(
            currentPropertyManager.currentPropertyIdFlow,
            currentBillingMonthManager.currentBillingMonthFlow
        ) { propId, billingMonth ->
            Pair(propId, billingMonth)
        }.flatMapLatest { (propId, billingMonth) ->
            rentPaymentDao.getPaymentsForPropertyAndMonthFlow(propId, billingMonth).map { payments ->
                val activePayments = payments.filter { !it.deleted }
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

                val expected = activePayments.sumOf { it.amount }
                val collected = activePayments.sumOf { it.amountPaid }
                val pending = activePayments.sumOf { (it.amount - it.amountPaid).coerceAtLeast(0.0) }
                val overdue = activePayments
                    .filter { (it.status == "Pending" || it.status == "Partially Paid" || it.status == "Partial" || it.status == "Overdue") && it.dueDate < today }
                    .sumOf { (it.amount - it.amountPaid).coerceAtLeast(0.0) }
                val todays = activePayments.filter { it.paymentDate == today }.sumOf { it.amountPaid }

                RentSummary(
                    expectedMonthlyRent = (expected * 100.0).roundToLong() / 100.0,
                    collectedRent = (collected * 100.0).roundToLong() / 100.0,
                    pendingRent = (pending * 100.0).roundToLong() / 100.0,
                    overdueRent = (overdue * 100.0).roundToLong() / 100.0,
                    todaysCollections = (todays * 100.0).roundToLong() / 100.0
                )
            }
        }
    }

    override fun getTenantLedger(tenantId: Int): Flow<List<RentPaymentEntity>> {
        return rentPaymentDao.getPaymentsForTenantFlow(tenantId).map { list -> list.filter { !it.deleted } }
    }

    override fun getLedger(): Flow<List<RentPaymentEntity>> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            rentPaymentDao.getAllPaymentsForPropertyFlow(propId).map { list -> list.filter { !it.deleted } }
        }
    }

    override fun getLedgerForMonth(billingMonth: String): Flow<List<RentPaymentEntity>> {
        val parsed = RentBillingEngine.parseBillingMonth(billingMonth)
        return getLedger().map { payments ->
            payments.filter { it.billingMonth.trim().equals(parsed.canonicalName.trim(), ignoreCase = true) }
        }
    }
}
