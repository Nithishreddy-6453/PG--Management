package com.example.features.googleform.domain.usecase

import com.example.core.common.PgResult
import com.example.features.googleform.domain.model.TenantRegistrationForm
import com.example.features.googleform.domain.repository.TenantRegistrationFormRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRegistrationFormUseCase @Inject constructor(
    private val repository: TenantRegistrationFormRepository
) {
    operator fun invoke(propertyId: String): Flow<TenantRegistrationForm?> {
        return repository.getFormForPropertyFlow(propertyId)
    }

    suspend fun getOnce(propertyId: String): PgResult<TenantRegistrationForm?> {
        return repository.getFormForProperty(propertyId)
    }
}

class CreateRegistrationFormUseCase @Inject constructor(
    private val repository: TenantRegistrationFormRepository
) {
    suspend operator fun invoke(propertyId: String, propertyName: String): PgResult<TenantRegistrationForm> {
        return repository.createRegistrationForm(propertyId, propertyName)
    }
}

class CheckFormStatusUseCase @Inject constructor(
    private val repository: TenantRegistrationFormRepository
) {
    suspend operator fun invoke(propertyId: String): PgResult<TenantRegistrationForm> {
        return repository.checkFormStatus(propertyId)
    }
}

class CreateReplacementFormUseCase @Inject constructor(
    private val repository: TenantRegistrationFormRepository
) {
    suspend operator fun invoke(propertyId: String, propertyName: String): PgResult<TenantRegistrationForm> {
        return repository.createReplacementForm(propertyId, propertyName)
    }
}
