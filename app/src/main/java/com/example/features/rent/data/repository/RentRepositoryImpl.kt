package com.example.features.rent.data.repository

import com.example.data.database.RentPaymentDao
import com.example.data.database.RentPaymentEntity
import com.example.data.database.TenantDao
import com.example.data.sync.SyncCoordinator
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rent.domain.repository.RentRepository
import com.example.features.rent.domain.repository.RentSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RentRepositoryImpl @Inject constructor(
    private val rentPaymentDao: RentPaymentDao,
    private val tenantDao: TenantDao,
    private val currentPropertyManager: CurrentPropertyManager,
    private val syncCoordinator: SyncCoordinator? = null
) : RentRepository {

    override suspend fun recordPayment(payment: RentPaymentEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }

        // Find if an existing active payment already exists using stable identity
        val existingPayment: RentPaymentEntity? = when {
            payment.id > 0 -> rentPaymentDao.getPaymentByIdIncludingDeleted(payment.id)
            payment.cloudId.isNotBlank() -> rentPaymentDao.getPaymentByCloudIdIncludingDeleted(payment.cloudId)
            payment.tenantId > 0 && payment.billingMonth.isNotBlank() -> {
                rentPaymentDao.getPaymentForTenantPropertyAndMonth(payment.tenantId, currentPropId, payment.billingMonth)
                    ?: rentPaymentDao.getPaymentForTenantAndMonth(payment.tenantId, payment.billingMonth)
            }
            payment.tenantName.isNotBlank() && payment.billingMonth.isNotBlank() -> {
                rentPaymentDao.getPaymentForTenantNamePropertyAndMonth(payment.tenantName, currentPropId, payment.billingMonth)
            }
            else -> null
        }

        if (existingPayment != null && !existingPayment.deleted) {
            val expectedAmount = if (payment.amount > 0) payment.amount else existingPayment.amount
            val amountPaid = payment.amountPaid
            val calculatedStatus = when {
                amountPaid >= expectedAmount -> "Paid"
                amountPaid > 0 -> "Partial"
                else -> "Pending"
            }
            val paymentDate = payment.paymentDate ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            
            val updated = existingPayment.copy(
                tenantName = payment.tenantName.ifBlank { existingPayment.tenantName },
                roomNumber = payment.roomNumber.ifBlank { existingPayment.roomNumber },
                billingMonth = payment.billingMonth.ifBlank { existingPayment.billingMonth },
                amount = expectedAmount,
                amountPaid = amountPaid,
                dueDate = payment.dueDate.ifBlank { existingPayment.dueDate },
                paymentDate = if (amountPaid > 0) paymentDate else existingPayment.paymentDate,
                paymentMode = payment.paymentMode ?: existingPayment.paymentMode ?: "UPI",
                transactionReference = payment.transactionReference ?: existingPayment.transactionReference,
                remarks = payment.remarks ?: existingPayment.remarks,
                status = calculatedStatus,
                propertyId = if (existingPayment.propertyId.isNotBlank() && existingPayment.propertyId != "property_default") existingPayment.propertyId else currentPropId,
                ownerId = if (existingPayment.ownerId.isNotBlank()) existingPayment.ownerId else ownerId,
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
            rentPaymentDao.updatePayment(updated)
            syncCoordinator?.enqueueOperation("PAYMENT", updated.id.toString(), "UPDATE")
        } else {
            val cloudId = if (payment.cloudId.isNotBlank()) payment.cloudId else "payment_${java.util.UUID.randomUUID()}"
            val calculatedStatus = when {
                payment.amountPaid >= payment.amount -> "Paid"
                payment.amountPaid > 0 -> "Partial"
                else -> "Pending"
            }
            val updated = payment.copy(
                cloudId = cloudId,
                propertyId = if (payment.propertyId.isNotBlank() && payment.propertyId != "property_default") payment.propertyId else currentPropId,
                ownerId = if (payment.ownerId.isNotBlank()) payment.ownerId else ownerId,
                status = calculatedStatus,
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
            val insertedId = rentPaymentDao.insertPayment(updated)
            val finalId = if (updated.id > 0) updated.id else insertedId.toInt()
            syncCoordinator?.enqueueOperation("PAYMENT", finalId.toString(), "CREATE")
        }
    }

    override suspend fun updatePayment(payment: RentPaymentEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }

        // Find existing record by ID or by stable identity
        val existingPayment: RentPaymentEntity? = when {
            payment.id > 0 -> rentPaymentDao.getPaymentByIdIncludingDeleted(payment.id)
            payment.cloudId.isNotBlank() -> rentPaymentDao.getPaymentByCloudIdIncludingDeleted(payment.cloudId)
            payment.tenantId > 0 && payment.billingMonth.isNotBlank() -> {
                rentPaymentDao.getPaymentForTenantPropertyAndMonth(payment.tenantId, currentPropId, payment.billingMonth)
                    ?: rentPaymentDao.getPaymentForTenantAndMonth(payment.tenantId, payment.billingMonth)
            }
            payment.tenantName.isNotBlank() && payment.billingMonth.isNotBlank() -> {
                rentPaymentDao.getPaymentForTenantNamePropertyAndMonth(payment.tenantName, currentPropId, payment.billingMonth)
            }
            else -> null
        }

        if (existingPayment != null && !existingPayment.deleted) {
            val expectedAmount = if (payment.amount > 0) payment.amount else existingPayment.amount
            val amountPaid = payment.amountPaid
            val calculatedStatus = when {
                amountPaid >= expectedAmount -> "Paid"
                amountPaid > 0 -> "Partial"
                else -> "Pending"
            }
            val paymentDate = payment.paymentDate ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

            val updated = existingPayment.copy(
                tenantName = payment.tenantName.ifBlank { existingPayment.tenantName },
                roomNumber = payment.roomNumber.ifBlank { existingPayment.roomNumber },
                billingMonth = payment.billingMonth.ifBlank { existingPayment.billingMonth },
                amount = expectedAmount,
                amountPaid = amountPaid,
                dueDate = payment.dueDate.ifBlank { existingPayment.dueDate },
                paymentDate = if (amountPaid > 0) paymentDate else existingPayment.paymentDate,
                paymentMode = payment.paymentMode ?: existingPayment.paymentMode ?: "UPI",
                transactionReference = payment.transactionReference ?: existingPayment.transactionReference,
                remarks = payment.remarks ?: existingPayment.remarks,
                status = calculatedStatus,
                propertyId = if (existingPayment.propertyId.isNotBlank() && existingPayment.propertyId != "property_default") existingPayment.propertyId else currentPropId,
                ownerId = if (existingPayment.ownerId.isNotBlank()) existingPayment.ownerId else ownerId,
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
            rentPaymentDao.updatePayment(updated)
            syncCoordinator?.enqueueOperation("PAYMENT", updated.id.toString(), "UPDATE")
        } else {
            recordPayment(payment)
        }
    }

    override suspend fun deletePayment(id: Int) {
        rentPaymentDao.softDeletePayment(id)
        syncCoordinator?.enqueueOperation("PAYMENT", id.toString(), "DELETE")
    }

    override suspend fun generateMonthlyInvoices(dueDateStr: String, billingMonthStr: String) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
        val tenants = tenantDao.getAllTenants(currentPropId)
        for (tenant in tenants) {
            // Check if invoice already exists for this month and tenant
            val existingPayments = rentPaymentDao.getPaymentsForTenantSync(tenant.id)
            if (existingPayments.none { !it.deleted && it.billingMonth.trim().equals(billingMonthStr.trim(), ignoreCase = true) }) {
                val newInvoice = RentPaymentEntity(
                    cloudId = "payment_${java.util.UUID.randomUUID()}",
                    tenantId = tenant.id,
                    tenantName = tenant.name,
                    roomNumber = tenant.roomNumber,
                    billingMonth = billingMonthStr,
                    amount = tenant.monthlyRent,
                    amountPaid = 0.0,
                    dueDate = dueDateStr,
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
    }

    override suspend fun getPendingPayments(): List<RentPaymentEntity> {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        return rentPaymentDao.getAllPaymentsForPropertySync(currentPropId).filter { it.status == "Pending" || it.status == "Partial" }
    }

    override suspend fun getOverduePayments(): List<RentPaymentEntity> {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        return rentPaymentDao.getAllPaymentsForPropertySync(currentPropId).filter { 
            (it.status == "Pending" || it.status == "Partial") && it.dueDate < today 
        }
    }

    override fun getRentSummary(): Flow<RentSummary> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            rentPaymentDao.getAllPaymentsForPropertyFlow(propId).map { payments ->
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                
                val expected = payments.sumOf { it.amount }
                val collected = payments.sumOf { it.amountPaid }
                val pending = payments.filter { it.status == "Pending" || it.status == "Partial" }.sumOf { it.amount - it.amountPaid }
                val overdue = payments.filter { (it.status == "Pending" || it.status == "Partial") && it.dueDate < today }.sumOf { it.amount - it.amountPaid }
                val todays = payments.filter { it.paymentDate == today }.sumOf { it.amountPaid }
                
                RentSummary(expected, collected, pending, overdue, todays)
            }
        }
    }

    override fun getTenantLedger(tenantId: Int): Flow<List<RentPaymentEntity>> {
        return rentPaymentDao.getPaymentsForTenantFlow(tenantId)
    }

    override fun getLedger(): Flow<List<RentPaymentEntity>> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            rentPaymentDao.getAllPaymentsForPropertyFlow(propId)
        }
    }
}
