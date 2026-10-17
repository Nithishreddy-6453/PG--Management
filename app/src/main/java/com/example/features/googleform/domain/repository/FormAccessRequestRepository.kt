package com.example.features.googleform.domain.repository

import com.example.core.common.PgResult
import com.example.features.googleform.domain.model.FormAccessRequest
import com.example.features.googleform.domain.model.FormVersionHistory
import com.example.features.googleform.domain.model.TenantRegistrationForm
import kotlinx.coroutines.flow.Flow

interface FormAccessRequestRepository {
    fun getRequestsForPropertyFlow(propertyId: String): Flow<List<FormAccessRequest>>
    fun getPendingIncomingRequestsFlow(): Flow<List<FormAccessRequest>>
    suspend fun createAccessRequest(propertyId: String, formId: String, formTitle: String, requestNote: String = ""): PgResult<FormAccessRequest>
    suspend fun approveAccessRequest(requestId: String): PgResult<FormAccessRequest>
    suspend fun rejectAccessRequest(requestId: String, reason: String = ""): PgResult<FormAccessRequest>
    suspend fun checkRequestStatus(propertyId: String): PgResult<FormAccessRequest?>
    fun getFormHistoryForPropertyFlow(propertyId: String): Flow<List<FormVersionHistory>>
    suspend fun recordFormReplacement(propertyId: String, oldForm: TenantRegistrationForm, newForm: TenantRegistrationForm): PgResult<Unit>
}
