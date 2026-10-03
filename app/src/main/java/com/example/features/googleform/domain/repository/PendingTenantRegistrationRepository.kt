package com.example.features.googleform.domain.repository

import com.example.core.common.PgResult
import com.example.data.database.TenantEntity
import com.example.features.googleform.domain.model.PendingTenantRegistration
import kotlinx.coroutines.flow.Flow

interface PendingTenantRegistrationRepository {
    fun getRegistrationsForPropertyFlow(propertyId: String): Flow<List<PendingTenantRegistration>>
    fun getPendingCountFlow(propertyId: String): Flow<Int>
    suspend fun getRegistrationById(cloudId: String): PgResult<PendingTenantRegistration?>
    suspend fun syncResponses(propertyId: String): PgResult<Int>
    suspend fun updateRegistration(registration: PendingTenantRegistration): PgResult<Unit>
    suspend fun rejectRegistration(cloudId: String, reason: String): PgResult<Unit>
    suspend fun acceptRegistration(
        cloudId: String,
        roomNumber: String,
        bedId: String,
        monthlyRent: Double,
        securityDeposit: Double,
        moveInDate: String,
        gender: String = "Male",
        kycDocType: String = "Aadhaar Card",
        notes: String = ""
    ): PgResult<TenantEntity>
}
