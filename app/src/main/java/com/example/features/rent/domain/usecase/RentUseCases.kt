package com.example.features.rent.domain.usecase

import com.example.data.database.RentPaymentEntity
import com.example.features.rent.domain.repository.RentRepository
import com.example.features.rent.domain.repository.RentSummary
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RecordPaymentUseCase @Inject constructor(
    private val repository: RentRepository
) {
    suspend operator fun invoke(payment: RentPaymentEntity) {
        val updatedPayment = payment.copy(
            status = calculateStatus(payment.amount, payment.amountPaid)
        )
        repository.recordPayment(updatedPayment)
    }

    private fun calculateStatus(amount: Double, amountPaid: Double): String {
        return when {
            amountPaid >= amount -> "Paid"
            amountPaid > 0 -> "Partial"
            else -> "Pending"
        }
    }
}

class UpdatePaymentUseCase @Inject constructor(
    private val repository: RentRepository
) {
    suspend operator fun invoke(payment: RentPaymentEntity) {
        val updatedPayment = payment.copy(
            status = calculateStatus(payment.amount, payment.amountPaid)
        )
        repository.updatePayment(updatedPayment)
    }

    private fun calculateStatus(amount: Double, amountPaid: Double): String {
        return when {
            amountPaid >= amount -> "Paid"
            amountPaid > 0 -> "Partial"
            else -> "Pending"
        }
    }
}

class GenerateMonthlyInvoicesUseCase @Inject constructor(
    private val repository: RentRepository
) {
    suspend operator fun invoke(dueDateStr: String, billingMonthStr: String) {
        repository.generateMonthlyInvoices(dueDateStr, billingMonthStr)
    }
}

class GetRentSummaryUseCase @Inject constructor(
    private val repository: RentRepository
) {
    operator fun invoke(): Flow<RentSummary> {
        return repository.getRentSummary()
    }
}

class GetLedgerUseCase @Inject constructor(
    private val repository: RentRepository
) {
    operator fun invoke(): Flow<List<RentPaymentEntity>> {
        return repository.getLedger()
    }
}

class GetTenantLedgerUseCase @Inject constructor(
    private val repository: RentRepository
) {
    operator fun invoke(tenantId: Int): Flow<List<RentPaymentEntity>> {
        return repository.getTenantLedger(tenantId)
    }
}

class CalculateOutstandingUseCase @Inject constructor() {
    operator fun invoke(expected: Double, paid: Double): Double {
        return if (expected > paid) expected - paid else 0.0
    }
}
