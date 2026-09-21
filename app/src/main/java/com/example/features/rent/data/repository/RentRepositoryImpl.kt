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
        val updated = payment.copy(
            propertyId = if (payment.propertyId.isNotBlank() && payment.propertyId != "property_default") payment.propertyId else currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        rentPaymentDao.insertPayment(updated)
        syncCoordinator?.enqueueOperation("PAYMENT", updated.id.toString(), "CREATE")
    }

    override suspend fun updatePayment(payment: RentPaymentEntity) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val updated = payment.copy(
            propertyId = if (payment.propertyId.isNotBlank() && payment.propertyId != "property_default") payment.propertyId else currentPropId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        rentPaymentDao.updatePayment(updated)
        syncCoordinator?.enqueueOperation("PAYMENT", updated.id.toString(), "UPDATE")
    }

    override suspend fun deletePayment(id: Int) {
        rentPaymentDao.softDeletePayment(id)
        syncCoordinator?.enqueueOperation("PAYMENT", id.toString(), "DELETE")
    }

    override suspend fun generateMonthlyInvoices(dueDateStr: String, billingMonthStr: String) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val tenants = tenantDao.getAllTenants(currentPropId)
        for (tenant in tenants) {
            // Check if invoice already exists for this month and tenant
            val existingPayments = rentPaymentDao.getPaymentsForTenantSync(tenant.id)
            if (existingPayments.none { it.billingMonth == billingMonthStr }) {
                val newInvoice = RentPaymentEntity(
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
                    propertyId = currentPropId
                )
                rentPaymentDao.insertPayment(newInvoice)
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
