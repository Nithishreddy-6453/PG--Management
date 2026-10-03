package com.example.features.rent.domain.usecase

import com.example.data.database.RentPaymentEntity
import com.example.features.rent.domain.model.MonthGenerationPreview
import com.example.features.rent.domain.repository.RentRepository
import com.example.features.rent.domain.repository.RentSummary
import com.example.features.rent.domain.util.RentBillingEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RecordPaymentUseCase @Inject constructor(
    private val repository: RentRepository
) {
    suspend operator fun invoke(payment: RentPaymentEntity) {
        val calculatedStatus = RentBillingEngine.calculatePaymentStatus(
            expectedAmount = payment.amount,
            amountPaid = payment.amountPaid,
            dueDateStr = payment.dueDate
        )
        val updatedPayment = payment.copy(status = calculatedStatus)
        repository.recordPayment(updatedPayment)
    }
}

class RecordPaymentTransactionUseCase @Inject constructor(
    private val repository: RentRepository
) {
    suspend operator fun invoke(
        paymentId: Int,
        paidAmountDelta: Double,
        paymentDate: String,
        paymentMode: String,
        transactionReference: String? = null,
        remarks: String? = null
    ) {
        repository.recordPaymentTransaction(
            paymentId = paymentId,
            paidAmountDelta = paidAmountDelta,
            paymentDate = paymentDate,
            paymentMode = paymentMode,
            transactionReference = transactionReference,
            remarks = remarks
        )
    }
}

class UpdatePaymentUseCase @Inject constructor(
    private val repository: RentRepository
) {
    suspend operator fun invoke(payment: RentPaymentEntity) {
        val calculatedStatus = RentBillingEngine.calculatePaymentStatus(
            expectedAmount = payment.amount,
            amountPaid = payment.amountPaid,
            dueDateStr = payment.dueDate
        )
        val updatedPayment = payment.copy(status = calculatedStatus)
        repository.updatePayment(updatedPayment)
    }
}

class GetMonthPreviewUseCase @Inject constructor(
    private val repository: RentRepository
) {
    suspend operator fun invoke(billingMonthStr: String): MonthGenerationPreview {
        return repository.getMonthGenerationPreview(billingMonthStr)
    }
}

class GenerateMonthRentUseCase @Inject constructor(
    private val repository: RentRepository
) {
    suspend operator fun invoke(
        billingMonthStr: String,
        dueDateStr: String = "",
        backupPreviousMonth: Boolean = true
    ): MonthGenerationPreview {
        return repository.generateMonthRent(billingMonthStr, dueDateStr, backupPreviousMonth)
    }
}

class GenerateMonthlyInvoicesUseCase @Inject constructor(
    private val repository: RentRepository
) {
    suspend operator fun invoke(dueDateStr: String, billingMonthStr: String) {
        repository.generateMonthlyInvoices(dueDateStr, billingMonthStr)
    }
}

class EnsureTenantCurrentMonthRentUseCase @Inject constructor(
    private val repository: RentRepository
) {
    suspend operator fun invoke(tenantId: Int) {
        repository.ensureTenantRentForCurrentMonth(tenantId)
    }
}

class AdjustTenantRentForLeavingUseCase @Inject constructor(
    private val repository: RentRepository
) {
    suspend operator fun invoke(tenantId: Int, leavingDateStr: String) {
        repository.adjustTenantRentForLeaving(tenantId, leavingDateStr)
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
        return if (expected > paid) ((expected - paid) * 100.0).toLong() / 100.0 else 0.0
    }
}
