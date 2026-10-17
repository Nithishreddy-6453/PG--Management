package com.example.features.dashboard.domain.usecase

import androidx.compose.runtime.Immutable
import com.example.data.database.ExpenseEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.TenantEntity
import com.example.features.dashboard.data.DashboardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

enum class ActivityType {
    TENANT_ADDED,
    RENT_PAID,
    EXPENSE_ADDED,
    ROOM_VACATED
}

@Immutable
data class RecentActivity(
    val id: String,
    val date: String, // YYYY-MM-DD
    val timestamp: Long = 0L,
    val type: ActivityType,
    val param1: String = "", // e.g. tenantName
    val param2: String = "", // e.g. roomNumber
    val param3: String = "", // e.g. bedId or category
    val amount: Double = 0.0,
    val status: String = "",
    val fallbackTitle: String = "",
    val fallbackDescription: String = "",
    val recordId: Int = 0,
    val propertyId: String = ""
)

class RecentActivityUseCase @Inject constructor(
    private val repository: DashboardRepository
) {
    fun computeActivities(
        tenants: List<TenantEntity>,
        payments: List<RentPaymentEntity>,
        expenses: List<ExpenseEntity>,
        referenceDate: String? = null
    ): List<RecentActivity> {
        val activities = mutableListOf<RecentActivity>()

        // 1. Tenants added (eligible if not deleted and move-in date has occurred)
        tenants.filter { !it.deleted && com.example.core.util.PgDateUtil.isDatePastOrToday(it.moveInDate, referenceDate) }.forEach { tenant ->
            val dateMs = com.example.core.util.PgDateUtil.parseDate(tenant.moveInDate)?.time ?: 0L
            val timeOfDayMs = if (tenant.createdAt > 0) (Math.abs(tenant.createdAt) % 86400000L) else 0L
            val activityTimestamp = dateMs + timeOfDayMs
            activities.add(
                RecentActivity(
                    id = "tenant_${tenant.id}",
                    date = tenant.moveInDate,
                    timestamp = activityTimestamp,
                    type = ActivityType.TENANT_ADDED,
                    param1 = tenant.name,
                    param2 = tenant.roomNumber,
                    param3 = tenant.bedId,
                    fallbackTitle = "Tenant Checked In",
                    fallbackDescription = "${tenant.name} moved into Room ${tenant.roomNumber} (${tenant.bedId})",
                    recordId = tenant.id,
                    propertyId = tenant.propertyId
                )
            )
        }

        // 2. Rent paid (eligible if not deleted, payment recorded, and date has occurred)
        payments.filter { !it.deleted && it.amountPaid > 0 && com.example.core.util.PgDateUtil.isDatePastOrToday(it.paymentDate ?: it.dueDate, referenceDate) }.forEach { payment ->
            val paymentDateStr = payment.paymentDate ?: payment.dueDate
            val dateMs = com.example.core.util.PgDateUtil.parseDate(paymentDateStr)?.time ?: 0L
            val timeOfDayMs = if (payment.createdAt > 0) (Math.abs(payment.createdAt) % 86400000L) else 0L
            val activityTimestamp = dateMs + timeOfDayMs
            activities.add(
                RecentActivity(
                    id = "payment_${payment.id}",
                    date = paymentDateStr,
                    timestamp = activityTimestamp,
                    type = ActivityType.RENT_PAID,
                    param1 = payment.tenantName,
                    param2 = payment.roomNumber,
                    amount = payment.amountPaid,
                    status = payment.status,
                    fallbackTitle = if (payment.status == "Partial") "Rent Partially Paid" else "Rent Paid",
                    fallbackDescription = "₹${String.format("%,.0f", payment.amountPaid)} collected from ${payment.tenantName} (Room ${payment.roomNumber})",
                    recordId = payment.id,
                    propertyId = payment.propertyId
                )
            )
        }

        // 3. Expense added (eligible if not deleted and transaction date has occurred)
        expenses.filter { !it.deleted && com.example.core.util.PgDateUtil.isDatePastOrToday(it.date, referenceDate) }.forEach { expense ->
            val dateMs = com.example.core.util.PgDateUtil.parseDate(expense.date)?.time ?: 0L
            val timeOfDayMs = if (expense.createdAt > 0) (Math.abs(expense.createdAt) % 86400000L) else 0L
            val activityTimestamp = dateMs + timeOfDayMs
            activities.add(
                RecentActivity(
                    id = "expense_${expense.id}",
                    date = expense.date,
                    timestamp = activityTimestamp,
                    type = ActivityType.EXPENSE_ADDED,
                    param1 = expense.category,
                    param2 = expense.notes,
                    amount = expense.amount,
                    fallbackTitle = "Expense Recorded",
                    fallbackDescription = "₹${String.format("%,.0f", expense.amount)} for ${expense.category} - ${expense.notes}",
                    recordId = expense.id,
                    propertyId = expense.propertyId
                )
            )
        }

        // Sort activities newest first using reliable timestamp, with numeric recordId tie-breaker.
        activities.sortWith(
            compareByDescending<RecentActivity> { activity ->
                activity.timestamp
            }.thenByDescending { it.recordId }
             .thenByDescending { it.id }
        )

        // Limit to top 15 recent activities
        return activities.take(15)
    }

    operator fun invoke(): Flow<List<RecentActivity>> {
        return combine(
            repository.getTenantsFlow(),
            repository.getPaymentsFlow(),
            repository.getExpensesFlow()
        ) { tenants, payments, expenses ->
            computeActivities(tenants, payments, expenses)
        }.flowOn(Dispatchers.Default)
    }
}
