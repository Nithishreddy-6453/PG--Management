package com.example.features.rent.domain.repository

import com.example.data.database.RentPaymentEntity
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
    suspend fun updatePayment(payment: RentPaymentEntity)
    suspend fun deletePayment(id: Int)
    suspend fun generateMonthlyInvoices(dueDateStr: String, billingMonthStr: String)
    suspend fun getPendingPayments(): List<RentPaymentEntity>
    suspend fun getOverduePayments(): List<RentPaymentEntity>
    fun getRentSummary(): Flow<RentSummary>
    fun getTenantLedger(tenantId: Int): Flow<List<RentPaymentEntity>>
    fun getLedger(): Flow<List<RentPaymentEntity>>
}
