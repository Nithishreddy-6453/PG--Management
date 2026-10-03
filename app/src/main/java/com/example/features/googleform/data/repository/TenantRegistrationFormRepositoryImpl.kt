package com.example.features.googleform.data.repository

import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.database.TenantRegistrationFormDao
import com.example.data.database.TenantRegistrationFormEntity
import com.example.data.firestore.model.TenantRegistrationFormDto
import com.example.data.firestore.repository.FirestoreTenantRegistrationFormRepository
import com.example.features.googleform.data.api.BatchUpdateRequest
import com.example.features.googleform.data.api.CreateFormInfo
import com.example.features.googleform.data.api.CreateFormRequest
import com.example.features.googleform.data.api.CreateItemLocation
import com.example.features.googleform.data.api.CreateItemRequest
import com.example.features.googleform.data.api.FormInfo
import com.example.features.googleform.data.api.FormItem
import com.example.features.googleform.data.api.FormRequestItem
import com.example.features.googleform.data.api.GoogleFormsApiService
import com.example.features.googleform.data.api.Question
import com.example.features.googleform.data.api.QuestionItem
import com.example.features.googleform.data.api.TextQuestion
import com.example.features.googleform.data.auth.GoogleFormsAuthManager
import com.example.features.googleform.domain.model.QuestionKeys
import com.example.features.googleform.domain.model.TenantRegistrationForm
import com.example.features.googleform.domain.repository.TenantRegistrationFormRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TenantRegistrationFormRepositoryImpl @Inject constructor(
    private val formDao: TenantRegistrationFormDao,
    private val firestoreRepository: FirestoreTenantRegistrationFormRepository,
    private val apiService: GoogleFormsApiService,
    private val authManager: GoogleFormsAuthManager,
    private val firebaseAuth: FirebaseAuth
) : TenantRegistrationFormRepository {

    private val currentOwnerId: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    override fun getFormForPropertyFlow(propertyId: String): Flow<TenantRegistrationForm?> {
        return formDao.getFormForPropertyFlow(propertyId).map { entity ->
            entity?.toDomainModel()
        }
    }

    override suspend fun getFormForProperty(propertyId: String): PgResult<TenantRegistrationForm?> = withContext(Dispatchers.IO) {
        try {
            val local = formDao.getFormForProperty(propertyId)
            if (local != null) {
                return@withContext PgResult.Success(local.toDomainModel())
            }

            // Fallback to Firestore if local cache is empty
            val firestoreResult = firestoreRepository.getFormForProperty(propertyId)
            if (firestoreResult is PgResult.Success && firestoreResult.data != null) {
                val dto = firestoreResult.data
                val entity = dto.toEntity()
                formDao.insertOrUpdateForm(entity)
                return@withContext PgResult.Success(entity.toDomainModel())
            }

            PgResult.Success(null)
        } catch (e: Exception) {
            PgResult.Failure(PgError.DatabaseError("Failed to load registration form: ${e.localizedMessage}"))
        }
    }

    override suspend fun createRegistrationForm(
        propertyId: String,
        propertyName: String
    ): PgResult<TenantRegistrationForm> = withContext(Dispatchers.IO) {
        try {
            // 1. Authorize Google Forms API
            val authHeaderResult = authManager.getAuthorizationHeader(requireResponsesScope = false)
            val authHeader = when (authHeaderResult) {
                is PgResult.Success -> authHeaderResult.data
                is PgResult.Failure -> return@withContext PgResult.Failure(authHeaderResult.error)
            }

            val googleEmail = authManager.accountInfoFlow.value.email
            val formTitle = "$propertyName – Tenant Registration"

            // 2. Create form on Google Forms (The initial POST /v1/forms request MUST contain ONLY title)
            val createRequest = CreateFormRequest(
                info = CreateFormInfo(
                    title = formTitle
                )
            )

            val createResponse = try {
                apiService.createForm(authHeader, createRequest)
            } catch (e: Exception) {
                return@withContext PgResult.Failure(PgError.NetworkError("Failed to reach Google Forms API: ${e.localizedMessage}"))
            }

            if (!createResponse.isSuccessful || createResponse.body() == null) {
                val errorBody = createResponse.errorBody()?.string() ?: "HTTP ${createResponse.code()}"
                return@withContext PgResult.Failure(PgError.NetworkError("Google Forms creation failed ($errorBody)"))
            }

            val createdForm = createResponse.body()!!
            val formId = createdForm.formId

            // 3. Add required and optional questions in batch
            val questions = QuestionKeys.ALL_QUESTIONS
            val requests = questions.mapIndexed { index, q ->
                FormRequestItem(
                    createItem = CreateItemRequest(
                        item = FormItem(
                            title = q.title,
                            description = q.description,
                            questionItem = QuestionItem(
                                question = Question(
                                    required = q.isRequired,
                                    textQuestion = TextQuestion(paragraph = q.isParagraph)
                                )
                            )
                        ),
                        location = CreateItemLocation(index = index)
                    )
                )
            }

            val batchResponse = try {
                apiService.batchUpdateForm(
                    authorization = authHeader,
                    formId = formId,
                    request = BatchUpdateRequest(requests = requests, includeFormInResponse = true)
                )
            } catch (e: Exception) {
                // Record failed creation status without marking published/active
                val now = System.currentTimeMillis()
                val draftEntity = TenantRegistrationFormEntity(
                    propertyId = propertyId,
                    cloudId = UUID.randomUUID().toString(),
                    ownerId = currentOwnerId,
                    formId = formId,
                    formTitle = formTitle,
                    responderUri = "",
                    editUri = "https://docs.google.com/forms/d/${formId}/edit",
                    googleAccountEmail = googleEmail,
                    formVersion = 1,
                    published = false,
                    active = false,
                    questionMapping = "{}",
                    lastCheckedAt = now,
                    lastSuccessfulCheckAt = 0L,
                    lastError = "Failed to add form questions: ${e.localizedMessage}",
                    createdAt = now,
                    updatedAt = now,
                    deleted = false,
                    syncStatus = "LOCAL_ONLY"
                )
                formDao.insertOrUpdateForm(draftEntity)
                return@withContext PgResult.Failure(PgError.NetworkError("Failed to populate Google Form questions: ${e.localizedMessage}"))
            }

            if (!batchResponse.isSuccessful || batchResponse.body() == null) {
                val errorBody = batchResponse.errorBody()?.string() ?: "HTTP ${batchResponse.code()}"
                val now = System.currentTimeMillis()
                val draftEntity = TenantRegistrationFormEntity(
                    propertyId = propertyId,
                    cloudId = UUID.randomUUID().toString(),
                    ownerId = currentOwnerId,
                    formId = formId,
                    formTitle = formTitle,
                    responderUri = "",
                    editUri = "https://docs.google.com/forms/d/${formId}/edit",
                    googleAccountEmail = googleEmail,
                    formVersion = 1,
                    published = false,
                    active = false,
                    questionMapping = "{}",
                    lastCheckedAt = now,
                    lastSuccessfulCheckAt = 0L,
                    lastError = "Questions update failed ($errorBody)",
                    createdAt = now,
                    updatedAt = now,
                    deleted = false,
                    syncStatus = "LOCAL_ONLY"
                )
                formDao.insertOrUpdateForm(draftEntity)
                return@withContext PgResult.Failure(PgError.NetworkError("Google Forms batchUpdate failed ($errorBody)"))
            }

            // 4. Extract Google Question IDs and construct mapping
            val questionMapping = mutableMapOf<String, String>()
            val replies = batchResponse.body()?.replies
            val items = batchResponse.body()?.form?.items

            if (!replies.isNullOrEmpty()) {
                questions.forEachIndexed { index, q ->
                    val qId = replies.getOrNull(index)?.createItem?.questionId?.firstOrNull()
                        ?: items?.getOrNull(index)?.questionItem?.question?.questionId
                        ?: "qid_$index"
                    questionMapping[q.key] = qId
                }
            } else if (!items.isNullOrEmpty()) {
                questions.forEachIndexed { index, q ->
                    val matchingItem = items.find { it.title.equals(q.title, ignoreCase = true) } ?: items.getOrNull(index)
                    val qId = matchingItem?.questionItem?.question?.questionId ?: "qid_$index"
                    questionMapping[q.key] = qId
                }
            } else {
                questions.forEachIndexed { index, q ->
                    questionMapping[q.key] = "qid_$index"
                }
            }

            // 5. Fetch and verify final form metadata
            val getFormResponse = try {
                apiService.getForm(authHeader, formId)
            } catch (_: Exception) { null }

            val verifiedForm = getFormResponse?.body() ?: batchResponse.body()?.form ?: createdForm
            val responderUri = verifiedForm.responderUri
                ?: "https://docs.google.com/forms/d/e/${formId}/viewform"
            val editUri = "https://docs.google.com/forms/d/${formId}/edit"

            val mappingJson = JSONObject(questionMapping as Map<*, *>).toString()
            val now = System.currentTimeMillis()
            val cloudId = UUID.randomUUID().toString()

            val formEntity = TenantRegistrationFormEntity(
                propertyId = propertyId,
                cloudId = cloudId,
                ownerId = currentOwnerId,
                formId = formId,
                formTitle = formTitle,
                responderUri = responderUri,
                editUri = editUri,
                googleAccountEmail = googleEmail,
                formVersion = 1,
                published = true,
                active = true,
                questionMapping = mappingJson,
                lastCheckedAt = now,
                lastSuccessfulCheckAt = now,
                lastError = null,
                createdAt = now,
                updatedAt = now,
                deleted = false,
                syncStatus = "SYNCED",
                lastSyncedAt = now
            )

            // Save to Room and Firestore
            formDao.insertOrUpdateForm(formEntity)
            firestoreRepository.saveForm(formEntity.toDto())

            PgResult.Success(formEntity.toDomainModel())
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to setup Google Form: ${e.localizedMessage}", e))
        }
    }

    override suspend fun checkFormStatus(propertyId: String): PgResult<TenantRegistrationForm> = withContext(Dispatchers.IO) {
        try {
            val existing = formDao.getFormForProperty(propertyId)
                ?: return@withContext PgResult.Failure(PgError.ValidationError("No form configuration found for property $propertyId"))

            val authHeaderResult = authManager.getAuthorizationHeader(requireResponsesScope = false)
            val authHeader = when (authHeaderResult) {
                is PgResult.Success -> authHeaderResult.data
                is PgResult.Failure -> {
                    // Update lastCheckedAt and auth error
                    val now = System.currentTimeMillis()
                    val updated = existing.copy(
                        lastCheckedAt = now,
                        lastError = "Google authorization expired. Please reconnect.",
                        updatedAt = now
                    )
                    formDao.insertOrUpdateForm(updated)
                    return@withContext PgResult.Success(updated.toDomainModel())
                }
            }

            val now = System.currentTimeMillis()
            val response = try {
                apiService.getForm(authHeader, existing.formId)
            } catch (e: Exception) {
                val updated = existing.copy(
                    lastCheckedAt = now,
                    lastError = "Network error: ${e.localizedMessage}",
                    updatedAt = now
                )
                formDao.insertOrUpdateForm(updated)
                return@withContext PgResult.Success(updated.toDomainModel())
            }

            if (response.code() == 404 || response.code() == 410) {
                // Form was deleted or not found
                val updated = existing.copy(
                    active = false,
                    lastCheckedAt = now,
                    lastError = "Registration form unavailable (Google Form not found or deleted).",
                    updatedAt = now
                )
                formDao.insertOrUpdateForm(updated)
                firestoreRepository.saveForm(updated.toDto())
                return@withContext PgResult.Success(updated.toDomainModel())
            }

            if (response.isSuccessful && response.body() != null) {
                val formBody = response.body()!!
                val responderUri = formBody.responderUri ?: existing.responderUri
                val updated = existing.copy(
                    active = true,
                    published = true,
                    responderUri = responderUri,
                    lastCheckedAt = now,
                    lastSuccessfulCheckAt = now,
                    lastError = null,
                    updatedAt = now
                )
                formDao.insertOrUpdateForm(updated)
                firestoreRepository.saveForm(updated.toDto())
                return@withContext PgResult.Success(updated.toDomainModel())
            } else {
                val updated = existing.copy(
                    lastCheckedAt = now,
                    lastError = "Google Forms API returned code ${response.code()}",
                    updatedAt = now
                )
                formDao.insertOrUpdateForm(updated)
                return@withContext PgResult.Success(updated.toDomainModel())
            }
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to check form status: ${e.localizedMessage}", e))
        }
    }

    override suspend fun createReplacementForm(
        propertyId: String,
        propertyName: String
    ): PgResult<TenantRegistrationForm> = withContext(Dispatchers.IO) {
        // Explicitly create a new form and replace the configuration for this property
        createRegistrationForm(propertyId, propertyName)
    }

    override suspend fun deleteForm(propertyId: String): PgResult<Unit> = withContext(Dispatchers.IO) {
        try {
            formDao.softDeleteForm(propertyId)
            firestoreRepository.deleteForm(propertyId)
            PgResult.Success(Unit)
        } catch (e: Exception) {
            PgResult.Failure(PgError.DatabaseError("Failed to delete form: ${e.localizedMessage}"))
        }
    }
}

// -------------------------------------------------------------
// Extension Mappers
// -------------------------------------------------------------

fun TenantRegistrationFormEntity.toDomainModel(): TenantRegistrationForm {
    val map = mutableMapOf<String, String>()
    try {
        if (questionMapping.isNotBlank()) {
            val json = JSONObject(questionMapping)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.optString(k, "")
            }
        }
    } catch (_: Exception) {}

    return TenantRegistrationForm(
        propertyId = propertyId,
        cloudId = cloudId,
        ownerId = ownerId,
        formId = formId,
        formTitle = formTitle,
        responderUri = responderUri,
        editUri = editUri,
        googleAccountEmail = googleAccountEmail,
        formVersion = formVersion,
        published = published,
        active = active,
        questionMapping = map,
        lastCheckedAt = lastCheckedAt,
        lastSuccessfulCheckAt = lastSuccessfulCheckAt,
        lastError = lastError,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun TenantRegistrationFormEntity.toDto(): TenantRegistrationFormDto {
    return TenantRegistrationFormDto(
        propertyId = propertyId,
        cloudId = cloudId,
        ownerId = ownerId,
        formId = formId,
        formTitle = formTitle,
        responderUri = responderUri,
        editUri = editUri,
        googleAccountEmail = googleAccountEmail,
        formVersion = formVersion,
        published = published,
        active = active,
        questionMapping = questionMapping,
        lastCheckedAt = lastCheckedAt,
        lastSuccessfulCheckAt = lastSuccessfulCheckAt,
        lastError = lastError,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        syncStatus = syncStatus,
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
}

fun TenantRegistrationFormDto.toEntity(): TenantRegistrationFormEntity {
    return TenantRegistrationFormEntity(
        propertyId = propertyId,
        cloudId = cloudId,
        ownerId = ownerId,
        formId = formId,
        formTitle = formTitle,
        responderUri = responderUri,
        editUri = editUri,
        googleAccountEmail = googleAccountEmail,
        formVersion = formVersion,
        published = published,
        active = active,
        questionMapping = questionMapping,
        lastCheckedAt = lastCheckedAt,
        lastSuccessfulCheckAt = lastSuccessfulCheckAt,
        lastError = lastError,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        syncStatus = syncStatus,
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
}
