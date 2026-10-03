package com.example.features.expenses.data.repository

import com.example.data.database.ExpenseDao
import com.example.data.database.ExpenseEntity
import com.example.data.sync.SyncCoordinator
import com.example.features.expenses.domain.model.Expense
import com.example.features.expenses.domain.model.ExpensePayment
import com.example.features.expenses.domain.repository.ExpenseRepository
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rent.domain.util.RentBillingEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val currentPropertyManager: CurrentPropertyManager,
    private val syncCoordinator: SyncCoordinator? = null
) : ExpenseRepository {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun getAllExpenses(): Flow<List<Expense>> {
        return currentPropertyManager.currentPropertyIdFlow.flatMapLatest { propId ->
            expenseDao.getAllExpensesForPropertyFlow(propId).map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    override suspend fun getExpenseById(id: Int): Expense? {
        return expenseDao.getExpenseById(id)?.toDomain()
    }

    override suspend fun insertExpense(expense: Expense) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
        val generatedCloudId = "expense_${UUID.randomUUID()}"
        val entity = expense.toEntity().copy(
            cloudId = generatedCloudId,
            propertyId = currentPropId,
            ownerId = ownerId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        val insertedId = expenseDao.insertExpense(entity)
        val finalId = if (entity.id > 0) entity.id else insertedId.toInt()
        syncCoordinator?.enqueueOperation("EXPENSE", finalId.toString(), "CREATE")

        // If this expense is recurring, generate separate monthly records for subsequent periods
        if (expense.isRecurring) {
            val recurringId = expense.recurringExpenseId ?: "rec_${UUID.randomUUID()}"
            generateFutureRecurringInstances(expense.copy(id = finalId, recurringExpenseId = recurringId), currentPropId, ownerId)
        }
    }

    override suspend fun updateExpense(expense: Expense) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
        val existing = expenseDao.getExpenseByIdIncludingDeleted(expense.id)
        val cloudId = existing?.cloudId?.ifBlank { "expense_${expense.id}" } ?: "expense_${expense.id}"
        val entity = expense.toEntity().copy(
            cloudId = cloudId,
            propertyId = currentPropId,
            ownerId = if (existing?.ownerId?.isNotBlank() == true) existing.ownerId else ownerId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        expenseDao.updateExpense(entity)
        syncCoordinator?.enqueueOperation("EXPENSE", entity.id.toString(), "UPDATE")
    }

    override suspend fun deleteExpense(id: Int) {
        expenseDao.softDeleteExpense(id)
        syncCoordinator?.enqueueOperation("EXPENSE", id.toString(), "DELETE")
    }

    override suspend fun recordPayment(expenseId: Int, payment: ExpensePayment) {
        val expense = getExpenseById(expenseId) ?: return
        val updatedPayments = expense.payments.toMutableList()
        updatedPayments.add(payment)

        val newPaidAmount = (expense.paidAmount + payment.amount).coerceAtLeast(payment.amount)
        val newRemaining = (expense.amount - newPaidAmount).coerceAtLeast(0.0)
        val newStatus = if (newRemaining <= 0.001) "Paid" else "Partially Paid"

        val updatedExpense = expense.copy(
            paidAmount = newPaidAmount,
            remainingAmount = newRemaining,
            status = newStatus,
            paymentDate = payment.paymentDate,
            paymentMethod = payment.paymentMethod,
            payments = updatedPayments
        )
        updateExpense(updatedExpense)
    }

    override suspend fun generateRecurringExpenses(expense: Expense, monthsCount: Int) {
        val currentPropId = currentPropertyManager.getCurrentPropertyId()
        val ownerId = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
        val recurringId = expense.recurringExpenseId ?: "rec_${UUID.randomUUID()}"
        generateFutureRecurringInstances(expense.copy(recurringExpenseId = recurringId), currentPropId, ownerId, monthsCount)
    }

    private suspend fun generateFutureRecurringInstances(
        baseExpense: Expense,
        propertyId: String,
        ownerId: String,
        count: Int = 3
    ) {
        val baseDate = try { dateFormat.parse(baseExpense.date) ?: Date() } catch (_: Exception) { Date() }
        val cal = Calendar.getInstance()
        cal.time = baseDate

        val isYearly = baseExpense.recurringFrequency.equals("Yearly", ignoreCase = true)
        val existingExpenses = expenseDao.getAllExpensesForPropertySync(propertyId)

        for (i in 1..count) {
            if (isYearly) {
                cal.add(Calendar.YEAR, 1)
            } else {
                cal.add(Calendar.MONTH, 1)
            }

            // Check if end date reached
            if (!baseExpense.recurringEndDate.isNullOrBlank()) {
                val endDate = try { dateFormat.parse(baseExpense.recurringEndDate) } catch (_: Exception) { null }
                if (endDate != null && cal.time.after(endDate)) {
                    break
                }
            }

            val nextDateStr = dateFormat.format(cal.time)
            val nextBillingMonth = RentBillingEngine.formatCanonicalBillingMonth(cal.time)

            // Avoid duplicate generation for the same recurring ID and month
            val duplicate = existingExpenses.any {
                !it.deleted && it.title == baseExpense.title && it.date == nextDateStr
            }
            if (duplicate) continue

            val futureExpense = baseExpense.copy(
                id = 0,
                date = nextDateStr,
                paidAmount = 0.0,
                remainingAmount = baseExpense.amount,
                status = "Unpaid",
                paymentDate = null,
                payments = emptyList(),
                recurringExpenseId = baseExpense.recurringExpenseId
            )

            val entity = futureExpense.toEntity().copy(
                cloudId = "expense_${UUID.randomUUID()}",
                propertyId = propertyId,
                ownerId = ownerId,
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_UPLOAD"
            )
            val insertedId = expenseDao.insertExpense(entity)
            syncCoordinator?.enqueueOperation("EXPENSE", insertedId.toString(), "CREATE")
        }
    }

    private fun ExpenseEntity.toDomain(): Expense {
        val meta = Expense.unpackNotes(
            rawNotes = notes,
            baseAmount = amount,
            baseDate = date,
            basePaymentMethod = paymentMethod
        )

        return Expense(
            id = id,
            title = if (title.isEmpty()) category else title,
            category = category,
            amount = amount,
            paidAmount = meta.paidAmount,
            remainingAmount = meta.remainingAmount,
            status = meta.status,
            date = date,
            paymentDate = meta.paymentDate,
            paymentMethod = paymentMethod,
            vendor = vendor,
            notes = meta.userNotes,
            isRecurring = meta.isRecurring,
            recurringFrequency = meta.recurringFrequency,
            recurringStartDate = meta.recurringStartDate,
            recurringEndDate = meta.recurringEndDate,
            recurringExpenseId = meta.recurringExpenseId,
            payments = meta.payments
        )
    }

    private fun Expense.toEntity(): ExpenseEntity {
        val packedNotes = Expense.packNotesWithMetadata(
            userNotes = notes,
            status = status,
            paidAmount = paidAmount,
            remainingAmount = remainingAmount,
            paymentDate = paymentDate,
            isRecurring = isRecurring,
            recurringFrequency = recurringFrequency,
            recurringStartDate = recurringStartDate,
            recurringEndDate = recurringEndDate,
            recurringExpenseId = recurringExpenseId,
            payments = payments
        )

        return ExpenseEntity(
            id = id,
            amount = amount,
            category = category,
            date = date,
            notes = packedNotes,
            title = title,
            paymentMethod = paymentMethod,
            vendor = vendor
        )
    }
}
