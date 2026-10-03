package com.example.features.dashboard.domain.usecase

import com.example.features.dashboard.data.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class UpcomingVacancyItem(
    val tenantId: Int,
    val tenantName: String,
    val phone: String = "",
    val roomNumber: String,
    val bedId: String = "",
    val leavingDate: String,
    val daysRemaining: Int = 0
)

class UpcomingVacanciesUseCase @Inject constructor(
    private val repository: DashboardRepository
) {
    operator fun invoke(): Flow<List<UpcomingVacancyItem>> {
        return repository.getTenantsFlow().map { tenants ->
            tenants
                .filter { !it.deleted && it.roomNumber.isNotBlank() && it.leavingDate.isNotBlank() }
                .sortedBy { it.leavingDate }
                .map {
                    UpcomingVacancyItem(
                        tenantId = it.id,
                        tenantName = it.name,
                        phone = it.phone,
                        roomNumber = it.roomNumber,
                        bedId = it.bedId,
                        leavingDate = it.leavingDate
                    )
                }
        }
    }
}
