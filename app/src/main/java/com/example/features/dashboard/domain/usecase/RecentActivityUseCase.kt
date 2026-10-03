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
    val type: ActivityType,
    val param1: String = "", // e.g. tenantName
    val param2: String = "", // e.g. roomNumber
    val param3: String = "", // e.g. bedId or category
    val amount: Double = 0.0,
    val status: String = "",
    val fallbackTitle: String = "",
    val fallbackDescription: String = ""
)

class RecentActivityUseCase @Inject constructor(
    private val repository: DashboardRepository
) {
    fun computeActivities(
        tenants: List<TenantEntity>,
        payments: List<RentPaymentEntity>,
        expenses: List<ExpenseEntity>
    ): List<RecentActivity> {
        val activities = mutableListOf<RecentActivity>()

        // 1. Tenants added
        tenants.forEach { tenant ->
            activities.add(
                RecentActivity(
                    id = "tenant_${tenant.id}",
                    date = tenant.moveInDate,
                    type = ActivityType.TENANT_ADDED,
                    param1 = tenant.name,
                    param2 = tenant.roomNumber,
                    param3 = tenant.bedId,
                    fallbackTitle = "Tenant Checked In",
                    fallbackDescription = "${tenant.name} moved into Room ${tenant.roomNumber} (${tenant.bedId})"
                )
            )
        }

        // 2. Rent paid
        payments.filter { it.amountPaid > 0 }.forEach { payment ->
            activities.add(
                RecentActivity(
                    id = "payment_${payment.id}",
                    date = payment.paymentDate ?: payment.dueDate,
                    type = ActivityType.RENT_PAID,
                    param1 = payment.tenantName,
                    param2 = payment.roomNumber,
                    amount = payment.amountPaid,
                    status = payment.status,
                    fallbackTitle = if (payment.status == "Partial") "Rent Partially Paid" else "Rent Paid",
                    fallbackDescription = "₹${String.format("%,.0f", payment.amountPaid)} collected from ${payment.tenantName} (Room ${payment.roomNumber})"
                )
            )
        }

        // 3. Expense added
        expenses.forEach { expense ->
            activities.add(
                RecentActivity(
                    id = "expense_${expense.id}",
                    date = expense.date,
                    type = ActivityType.EXPENSE_ADDED,
                    param1 = expense.category,
                    param2 = expense.notes,
                    amount = expense.amount,
                    fallbackTitle = "Expense Recorded",
                    fallbackDescription = "₹${String.format("%,.0f", expense.amount)} for ${expense.category} - ${expense.notes}"
                )
            )
        }

        // Sort activities newest first by date string.
        activities.sortByDescending { it.date }

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
