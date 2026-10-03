package com.example.features.googleform.domain.usecase

import com.example.core.common.PgResult
import com.example.data.database.TenantEntity
import com.example.features.googleform.domain.model.PendingTenantRegistration
import com.example.features.googleform.domain.repository.PendingTenantRegistrationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPendingRegistrationsUseCase @Inject constructor(
    private val repository: PendingTenantRegistrationRepository
) {
    operator fun invoke(propertyId: String): Flow<List<PendingTenantRegistration>> {
        return repository.getRegistrationsForPropertyFlow(propertyId)
    }
}

class GetPendingCountUseCase @Inject constructor(
    private val repository: PendingTenantRegistrationRepository
) {
    operator fun invoke(propertyId: String): Flow<Int> {
        return repository.getPendingCountFlow(propertyId)
    }
}

class GetPendingRegistrationDetailsUseCase @Inject constructor(
    private val repository: PendingTenantRegistrationRepository
) {
    suspend operator fun invoke(cloudId: String): PgResult<PendingTenantRegistration?> {
        return repository.getRegistrationById(cloudId)
    }
}

class SyncFormResponsesUseCase @Inject constructor(
    private val repository: PendingTenantRegistrationRepository
) {
    suspend operator fun invoke(propertyId: String): PgResult<Int> {
        return repository.syncResponses(propertyId)
    }
}

class AcceptPendingTenantUseCase @Inject constructor(
    private val repository: PendingTenantRegistrationRepository
) {
    suspend operator fun invoke(
        cloudId: String,
        roomNumber: String,
        bedId: String,
        monthlyRent: Double,
        securityDeposit: Double,
        moveInDate: String,
        gender: String = "Male",
        kycDocType: String = "Aadhaar Card",
        notes: String = ""
    ): PgResult<TenantEntity> {
        return repository.acceptRegistration(
            cloudId = cloudId,
            roomNumber = roomNumber,
            bedId = bedId,
            monthlyRent = monthlyRent,
            securityDeposit = securityDeposit,
            moveInDate = moveInDate,
            gender = gender,
            kycDocType = kycDocType,
            notes = notes
        )
    }
}

class RejectPendingTenantUseCase @Inject constructor(
    private val repository: PendingTenantRegistrationRepository
) {
    suspend operator fun invoke(cloudId: String, reason: String): PgResult<Unit> {
        return repository.rejectRegistration(cloudId, reason)
    }
}

class UpdatePendingRegistrationUseCase @Inject constructor(
    private val repository: PendingTenantRegistrationRepository
) {
    suspend operator fun invoke(registration: PendingTenantRegistration): PgResult<Unit> {
        return repository.updateRegistration(registration)
    }
}
