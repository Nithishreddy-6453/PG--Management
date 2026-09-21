package com.example.features.expenses.data.repository

import com.example.data.database.ExpenseDao
import com.example.data.database.ExpenseEntity
import com.example.data.sync.SyncCoordinator
import com.example.features.expenses.domain.model.Expense
import com.example.features.expenses.domain.repository.ExpenseRepository
import com.example.features.properties.data.CurrentPropertyManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val currentPropertyManager: CurrentPropertyManager,
    private val syncCoordinator: SyncCoordinator? = null
) : ExpenseRepository {

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
        val entity = expense.toEntity().copy(
            cloudId = "expense_${java.util.UUID.randomUUID()}",
            propertyId = currentPropId,
            ownerId = ownerId,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_UPLOAD"
        )
        val insertedId = expenseDao.insertExpense(entity)
        val finalId = if (entity.id > 0) entity.id else insertedId.toInt()
        syncCoordinator?.enqueueOperation("EXPENSE", finalId.toString(), "CREATE")
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

    private fun ExpenseEntity.toDomain(): Expense {
        return Expense(
            id = id,
            title = if (title.isEmpty()) category else title,
            category = category,
            amount = amount,
            date = date,
            paymentMethod = paymentMethod,
            vendor = vendor,
            notes = notes
        )
    }

    private fun Expense.toEntity(): ExpenseEntity {
        return ExpenseEntity(
            id = id,
            amount = amount,
            category = category,
            date = date,
            notes = notes,
            title = title,
            paymentMethod = paymentMethod,
            vendor = vendor
        )
    }
}
