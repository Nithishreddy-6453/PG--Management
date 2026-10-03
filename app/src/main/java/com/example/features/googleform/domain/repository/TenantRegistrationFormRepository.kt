package com.example.features.googleform.domain.repository

import com.example.core.common.PgResult
import com.example.features.googleform.domain.model.TenantRegistrationForm
import kotlinx.coroutines.flow.Flow

interface TenantRegistrationFormRepository {
    fun getFormForPropertyFlow(propertyId: String): Flow<TenantRegistrationForm?>
    suspend fun getFormForProperty(propertyId: String): PgResult<TenantRegistrationForm?>
    suspend fun createRegistrationForm(propertyId: String, propertyName: String): PgResult<TenantRegistrationForm>
    suspend fun checkFormStatus(propertyId: String): PgResult<TenantRegistrationForm>
    suspend fun createReplacementForm(propertyId: String, propertyName: String): PgResult<TenantRegistrationForm>
    suspend fun deleteForm(propertyId: String): PgResult<Unit>
}
