package com.example.features.rent.domain.repository

import com.example.data.database.RentPaymentEntity
import com.example.features.rent.domain.model.MonthGenerationPreview
import kotlinx.coroutines.flow.Flow

data class RentSummary(
    val expectedMonthlyRent: Double,
    val collectedRent: Double,
    val pendingRent: Double,
    val overdueRent: Double,
    val todaysCollections: Double
)

interface RentRepository {
    suspend fun recordPayment(payment: RentPaymentEntity)
    suspend fun recordPaymentTransaction(
        paymentId: Int,
        paidAmountDelta: Double,
        paymentDate: String,
        paymentMode: String,
        transactionReference: String?,
        remarks: String?
    )
    suspend fun updatePayment(payment: RentPaymentEntity)
    suspend fun deletePayment(id: Int)
    suspend fun generateMonthlyInvoices(dueDateStr: String, billingMonthStr: String)
    suspend fun getMonthGenerationPreview(billingMonthStr: String): MonthGenerationPreview
    suspend fun generateMonthRent(
        billingMonthStr: String,
        dueDateStr: String = "",
        backupPreviousMonth: Boolean = true
    ): MonthGenerationPreview
    suspend fun ensureTenantRentForCurrentMonth(tenantId: Int)
    suspend fun adjustTenantRentForLeaving(tenantId: Int, leavingDateStr: String)
    suspend fun getPendingPayments(): List<RentPaymentEntity>
    suspend fun getOverduePayments(): List<RentPaymentEntity>
    fun getRentSummary(): Flow<RentSummary>
    fun getTenantLedger(tenantId: Int): Flow<List<RentPaymentEntity>>
    fun getLedger(): Flow<List<RentPaymentEntity>>
    fun getLedgerForMonth(billingMonth: String): Flow<List<RentPaymentEntity>>
}
