package com.example.features.dashboard.domain.usecase

import com.example.features.dashboard.data.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

enum class ActivityType {
    TENANT_ADDED,
    RENT_PAID,
    EXPENSE_ADDED,
    ROOM_VACATED
}

data class RecentActivity(
    val id: String,
    val title: String,
    val description: String,
    val date: String, // YYYY-MM-DD
    val type: ActivityType
)

class RecentActivityUseCase @Inject constructor(
    private val repository: DashboardRepository
) {
    operator fun invoke(): Flow<List<RecentActivity>> {
        return combine(
            repository.getTenantsFlow(),
            repository.getPaymentsFlow(),
            repository.getExpensesFlow()
        ) { tenants, payments, expenses ->
            val activities = mutableListOf<RecentActivity>()

            // 1. Tenants added
            tenants.forEach { tenant ->
                activities.add(
                    RecentActivity(
                        id = "tenant_${tenant.id}",
                        title = "Tenant Checked In",
                        description = "${tenant.name} moved into Room ${tenant.roomNumber} (${tenant.bedId})",
                        date = tenant.moveInDate,
                        type = ActivityType.TENANT_ADDED
                    )
                )
            }

            // 2. Rent paid
            payments.filter { it.amountPaid > 0 }.forEach { payment ->
                activities.add(
                    RecentActivity(
                        id = "payment_${payment.id}",
                        title = if (payment.status == "Partial") "Rent Partially Paid" else "Rent Paid",
                        description = "₹${String.format("%,.0f", payment.amountPaid)} collected from ${payment.tenantName} (Room ${payment.roomNumber})",
                        date = payment.paymentDate ?: payment.dueDate,
                        type = ActivityType.RENT_PAID
                    )
                )
            }

            // 3. Expense added
            expenses.forEach { expense ->
                activities.add(
                    RecentActivity(
                        id = "expense_${expense.id}",
                        title = "Expense Recorded",
                        description = "₹${String.format("%,.0f", expense.amount)} for ${expense.category} - ${expense.notes}",
                        date = expense.date,
                        type = ActivityType.EXPENSE_ADDED
                    )
                )
            }

            // Sort activities newest first by date string.
            activities.sortByDescending { it.date }
            
            // Limit to top 15 recent activities
            activities.take(15)
        }
    }
}
