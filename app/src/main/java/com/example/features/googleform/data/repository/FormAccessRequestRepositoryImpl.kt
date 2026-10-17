package com.example.features.googleform.data.repository

import android.util.Log
import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.database.FormAccessRequestDao
import com.example.data.database.FormAccessRequestEntity
import com.example.data.database.FormVersionHistoryDao
import com.example.data.database.FormVersionHistoryEntity
import com.example.data.database.TenantRegistrationFormDao
import com.example.data.database.PropertyDao
import com.example.data.firestore.model.toDto
import com.example.data.firestore.model.toEntity
import com.example.data.firestore.model.toDomainModel
import com.example.data.firestore.repository.FirestoreFormAccessRequestRepository
import com.example.data.firestore.repository.FirestoreTenantRegistrationFormRepository
import com.example.features.googleform.data.auth.GoogleFormsAuthManager
import com.example.features.googleform.data.notification.FormAccessNotificationManager
import com.example.features.googleform.domain.model.FormAccessRequest
import com.example.features.googleform.data.api.GoogleFormsApiService
import com.example.features.googleform.domain.model.FormVersionHistory
import com.example.features.googleform.domain.model.TenantRegistrationForm
import com.example.features.googleform.domain.repository.FormAccessRequestRepository
import com.example.features.tenants.data.api.CreatePermissionRequest
import com.example.features.tenants.data.api.GoogleDriveApiService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormAccessRequestRepositoryImpl @Inject constructor(
    private val requestDao: FormAccessRequestDao,
    private val historyDao: FormVersionHistoryDao,
    private val formDao: TenantRegistrationFormDao,
    private val propertyDao: PropertyDao,
    private val firestoreRepository: FirestoreFormAccessRequestRepository,
    private val firestoreTenantFormRepository: FirestoreTenantRegistrationFormRepository,
    private val driveApiService: GoogleDriveApiService,
    private val formsApiService: GoogleFormsApiService,
    private val authManager: GoogleFormsAuthManager,
    private val notificationManager: FormAccessNotificationManager,
    private val firebaseAuth: FirebaseAuth
) : FormAccessRequestRepository {

    companion object {
        private const val TAG = "FormAccessRequestRepo"
    }

    private val currentPgUid: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    override fun getRequestsForPropertyFlow(propertyId: String): Flow<List<FormAccessRequest>> = callbackFlow {
        val roomJob = launch {
            requestDao.getRequestsForPropertyFlow(propertyId).collect { entities ->
                trySend(entities.map { it.toDomainModel() })
            }
        }

        val firestoreJob = launch {
            firestoreRepository.observeRequestsForProperty(propertyId).collect { result ->
                if (result is PgResult.Success) {
                    result.data.forEach { dto ->
                        requestDao.insertOrUpdate(dto.toEntity())
                    }
                }
            }
        }

        awaitClose {
            roomJob.cancel()
            firestoreJob.cancel()
        }
    }

    override fun getPendingIncomingRequestsFlow(): Flow<List<FormAccessRequest>> = callbackFlow {
        val uid = currentPgUid
        val googleEmail = authManager.accountInfoFlow.value.email
        val roomJob = launch {
            requestDao.getPendingIncomingRequestsFlow(uid, googleEmail).collect { entities ->
                trySend(entities.map { it.toDomainModel() })
            }
        }

        val firestoreJob = launch {
            if (uid.isNotBlank()) {
                firestoreRepository.observePendingRequestsForOwner(uid).collect { result ->
                    if (result is PgResult.Success) {
                        result.data.forEach { dto ->
                            requestDao.insertOrUpdate(dto.toEntity())
                        }
                    }
                }
            }
            val propertyId = propertyDao.getAllProperties().firstOrNull()?.propertyId ?: ""
            if (propertyId.isNotBlank()) {
                firestoreRepository.observeRequestsForProperty(propertyId).collect { result ->
                    if (result is PgResult.Success) {
                        result.data.forEach { dto ->
                            requestDao.insertOrUpdate(dto.toEntity())
                        }
                    }
                }
            }
        }

        awaitClose {
            roomJob.cancel()
            firestoreJob.cancel()
        }
    }

    override suspend fun createAccessRequest(
        propertyId: String,
        formId: String,
        formTitle: String,
        requestNote: String
    ): PgResult<FormAccessRequest> = withContext(Dispatchers.IO) {
        try {
            val requesterPgUid = currentPgUid
            val requesterGoogleEmail = authManager.accountInfoFlow.value.email
            val requesterName = authManager.accountInfoFlow.value.displayName.ifBlank {
                firebaseAuth.currentUser?.displayName ?: firebaseAuth.currentUser?.email?.substringBefore("@") ?: "Team Member"
            }

            if (requesterGoogleEmail.isBlank()) {
                return@withContext PgResult.Failure(PgError.ValidationError("Please connect your Google account before requesting form access."))
            }

            // Check if there is already a PENDING request from this user for this property
            val existingPending = requestDao.getPendingRequest(propertyId, requesterPgUid, requesterGoogleEmail)
            if (existingPending != null) {
                return@withContext PgResult.Failure(
                    PgError.ValidationError("You already have a pending access request for this form. Please wait for owner approval.")
                )
            }

            // Find form details to get form owner PG UID and owner Google Email
            var formEntity = formDao.getFormForProperty(propertyId)
            if (formEntity == null || formEntity.googleAccountEmail.isBlank()) {
                val firestoreFormRes = firestoreTenantFormRepository.getFormForProperty(propertyId)
                if (firestoreFormRes is PgResult.Success && firestoreFormRes.data != null) {
                    val entity = firestoreFormRes.data.toEntity()
                    formDao.insertOrUpdateForm(entity)
                    formEntity = entity
                }
            }

            val ownerPgUid = formEntity?.ownerId?.ifBlank { "" } ?: ""
            val ownerGoogleEmail = formEntity?.googleAccountEmail?.ifBlank { "" } ?: ""
            val propertyName = propertyDao.getProperty(propertyId)?.propertyName ?: "PG Workspace"

            val requestId = "req_${UUID.randomUUID()}"
            val now = System.currentTimeMillis()

            val initialRequest = FormAccessRequest(
                requestId = requestId,
                propertyId = propertyId,
                formId = formId.ifBlank { formEntity?.formId ?: "" },
                formTitle = formTitle.ifBlank { formEntity?.formTitle ?: "Tenant Registration Form" },
                requesterPgUid = requesterPgUid,
                requesterGoogleEmail = requesterGoogleEmail,
                requesterName = requesterName,
                ownerPgUid = ownerPgUid,
                ownerGoogleEmail = ownerGoogleEmail,
                requestedRole = "EDITOR",
                status = "PENDING",
                requestNote = requestNote,
                notificationStatus = "PENDING",
                createdAt = now,
                updatedAt = now
            )

            // Save initial pending request
            requestDao.insertOrUpdate(initialRequest.toEntity())
            firestoreRepository.saveRequest(initialRequest.toDto())

            // Attempt email notification dispatch
            val notifResult = notificationManager.sendAccessRequestNotification(initialRequest, propertyName)
            val updatedRequest = when (notifResult) {
                is PgResult.Success -> {
                    initialRequest.copy(
                        notificationStatus = "SENT",
                        notificationSentAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                }
                is PgResult.Failure -> {
                    initialRequest.copy(
                        notificationStatus = "FAILED",
                        notificationError = notifResult.error.message,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }

            // Update request record with notification dispatch status
            requestDao.insertOrUpdate(updatedRequest.toEntity())
            firestoreRepository.saveRequest(updatedRequest.toDto())

            Log.i(TAG, "Access request $requestId created for property $propertyName by $requesterGoogleEmail (Notification: ${updatedRequest.notificationStatus})")
            PgResult.Success(updatedRequest)
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to create access request: ${e.localizedMessage}", e))
        }
    }

    override suspend fun approveAccessRequest(requestId: String): PgResult<FormAccessRequest> = withContext(Dispatchers.IO) {
        try {
            val existingEntity = requestDao.getRequestById(requestId)
                ?: return@withContext PgResult.Failure(PgError.ValidationError("Access request not found."))

            val request = existingEntity.toDomainModel()
            if (!request.isPending) {
                return@withContext PgResult.Failure(PgError.ValidationError("Request is already ${request.status}."))
            }

            // Obtain owner Google Drive auth header
            val authHeaderResult = authManager.getDriveAuthorizationHeader()
            val authHeader = when (authHeaderResult) {
                is PgResult.Success -> authHeaderResult.data
                is PgResult.Failure -> {
                    val now = System.currentTimeMillis()
                    val failedEntity = existingEntity.copy(
                        status = "FAILED",
                        rejectionReason = "Google Drive authorization missing on owner device: ${authHeaderResult.error.message}",
                        updatedAt = now,
                        resolvedAt = now
                    )
                    requestDao.insertOrUpdate(failedEntity)
                    firestoreRepository.saveRequest(failedEntity.toDto())
                    return@withContext PgResult.Failure(
                        PgError.ConsentRequiredError(
                            message = "Owner Google Drive authorization required to grant form permissions. ${authHeaderResult.error.message}",
                            consentIntent = (authHeaderResult.error as? PgError.ConsentRequiredError)?.consentIntent
                        )
                    )
                }
            }

            // Call Google Drive Permissions API to grant real Editor ("writer") access to requesterGoogleEmail
            val permReq = CreatePermissionRequest(
                role = "writer",
                type = "user",
                emailAddress = request.requesterGoogleEmail
            )

            val permResponse = try {
                driveApiService.createPermission(
                    authHeader = authHeader,
                    fileId = request.formId,
                    sendNotificationEmail = true,
                    request = permReq
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed Google Drive createPermission API call: ${e.message}")
                null
            }

            val now = System.currentTimeMillis()
            if (permResponse != null && (permResponse.isSuccessful || permResponse.code() == 200 || permResponse.code() == 201)) {
                val approvedEntity = existingEntity.copy(
                    status = "APPROVED",
                    rejectionReason = null,
                    updatedAt = now,
                    resolvedAt = now,
                    syncStatus = "SYNCED"
                )
                requestDao.insertOrUpdate(approvedEntity)
                firestoreRepository.saveRequest(approvedEntity.toDto())

                Log.i(TAG, "Successfully granted Google Drive editor access to ${request.requesterGoogleEmail} for form ${request.formId}")
                PgResult.Success(approvedEntity.toDomainModel())
            } else {
                val errText = permResponse?.errorBody()?.string() ?: "HTTP ${permResponse?.code() ?: "Unknown"}"
                Log.e(TAG, "Google Drive permission granting failed: $errText")
                val failedEntity = existingEntity.copy(
                    status = "FAILED",
                    rejectionReason = "Failed to grant Google permission ($errText)",
                    updatedAt = now,
                    resolvedAt = now
                )
                requestDao.insertOrUpdate(failedEntity)
                firestoreRepository.saveRequest(failedEntity.toDto())
                PgResult.Failure(PgError.NetworkError("Failed to grant Google Form permission: $errText"))
            }
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to approve access request: ${e.localizedMessage}", e))
        }
    }

    override suspend fun rejectAccessRequest(requestId: String, reason: String): PgResult<FormAccessRequest> = withContext(Dispatchers.IO) {
        try {
            val existingEntity = requestDao.getRequestById(requestId)
                ?: return@withContext PgResult.Failure(PgError.ValidationError("Access request not found."))

            val now = System.currentTimeMillis()
            val rejectedEntity = existingEntity.copy(
                status = "REJECTED",
                rejectionReason = reason.ifBlank { "Rejected by form owner." },
                updatedAt = now,
                resolvedAt = now,
                syncStatus = "SYNCED"
            )
            requestDao.insertOrUpdate(rejectedEntity)
            firestoreRepository.saveRequest(rejectedEntity.toDto())

            Log.i(TAG, "Access request $requestId rejected by owner")
            PgResult.Success(rejectedEntity.toDomainModel())
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to reject access request: ${e.localizedMessage}", e))
        }
    }

    override suspend fun checkRequestStatus(propertyId: String): PgResult<FormAccessRequest?> = withContext(Dispatchers.IO) {
        try {
            val requesterEmail = authManager.accountInfoFlow.value.email.trim()

            // 1. Sync authoritative status from Firestore first
            when (val remoteResult = firestoreRepository.getRequestsForProperty(propertyId)) {
                is PgResult.Success -> {
                    remoteResult.data.forEach { dto ->
                        requestDao.insertOrUpdate(dto.toEntity())
                    }
                }
                is PgResult.Failure -> {
                    Log.w(TAG, "Failed to refresh requests from Firestore: ${remoteResult.error.message}")
                }
            }

            var pendingEntity = requestDao.getPendingRequest(propertyId, currentPgUid, requesterEmail)
                ?: requestDao.getRequestsForPropertyFlow(propertyId).firstOrNull()?.firstOrNull {
                    it.requesterPgUid == currentPgUid || (requesterEmail.isNotBlank() && it.requesterGoogleEmail.equals(requesterEmail, ignoreCase = true))
                }

            // If already marked approved/accepted in database, return immediately
            if (pendingEntity != null && (pendingEntity.status.equals("APPROVED", ignoreCase = true) || pendingEntity.status.equals("ACCEPTED", ignoreCase = true))) {
                return@withContext PgResult.Success(pendingEntity.toDomainModel())
            }

            // Find target form details
            val formEntity = formDao.getFormForProperty(propertyId)
            val formId = pendingEntity?.formId?.ifBlank { null }
                ?: formEntity?.formId?.ifBlank { null }
                ?: (firestoreTenantFormRepository.getFormForProperty(propertyId) as? PgResult.Success)?.data?.formId?.ifBlank { null }
                ?: ""

            // 2. Verify actual Google permissions
            var verifiedAccess = false

            // Check 2A: Google Forms API (Primary & Authoritative for Google Forms)
            // When the owner manually adds the user in Chrome, the user is granted collaborator/editor permission
            if (formId.isNotBlank()) {
                val formsAuthResult = authManager.getAuthorizationHeader(requireResponsesScope = false)
                if (formsAuthResult is PgResult.Success) {
                    try {
                        val getFormResp = formsApiService.getForm(formsAuthResult.data, formId)
                        if (getFormResp.isSuccessful && getFormResp.body() != null) {
                            verifiedAccess = true
                            Log.i(TAG, "Access verified via Google Forms API for form $formId")
                        } else {
                            Log.w(TAG, "Google Forms API getForm returned HTTP ${getFormResp.code()}: ${getFormResp.errorBody()?.string()}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Google Forms API getForm failed: ${e.message}")
                    }
                } else {
                    Log.w(TAG, "Could not get Google Forms auth token: ${(formsAuthResult as? PgResult.Failure)?.error?.message}")
                }
            }

            // Check 2B: Google Drive API (Fallback if Drive scope is granted and file is accessible)
            if (!verifiedAccess && formId.isNotBlank()) {
                val driveAuthResult = authManager.getDriveAuthorizationHeader()
                if (driveAuthResult is PgResult.Success) {
                    try {
                        val getFileResp = driveApiService.getFile(driveAuthResult.data, formId)
                        if (getFileResp.isSuccessful && getFileResp.body() != null) {
                            verifiedAccess = true
                            Log.i(TAG, "Access verified via Google Drive API for file $formId")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Google Drive API getFile failed: ${e.message}")
                    }
                }
            }

            if (verifiedAccess) {
                val now = System.currentTimeMillis()
                val targetOwnerPgUid = pendingEntity?.ownerPgUid?.ifBlank { null } ?: formEntity?.ownerId ?: ""
                val targetOwnerEmail = pendingEntity?.ownerGoogleEmail?.ifBlank { null } ?: formEntity?.googleAccountEmail ?: ""
                val targetTitle = pendingEntity?.formTitle?.ifBlank { null } ?: formEntity?.formTitle ?: "Tenant Registration"

                val approvedEntity = (pendingEntity ?: FormAccessRequestEntity(
                    requestId = "req_${UUID.randomUUID()}",
                    propertyId = propertyId,
                    formId = formId,
                    formTitle = targetTitle,
                    requesterPgUid = currentPgUid,
                    requesterGoogleEmail = requesterEmail,
                    requesterName = authManager.accountInfoFlow.value.displayName.ifBlank { "Team Member" },
                    ownerPgUid = targetOwnerPgUid,
                    ownerGoogleEmail = targetOwnerEmail,
                    requestNote = "Verified via Google Forms permission",
                    status = "APPROVED",
                    rejectionReason = null,
                    createdAt = now,
                    updatedAt = now,
                    resolvedAt = now,
                    syncStatus = "SYNCED"
                )).copy(
                    formId = formId,
                    status = "APPROVED",
                    rejectionReason = null,
                    updatedAt = now,
                    resolvedAt = now,
                    syncStatus = "SYNCED"
                )

                requestDao.insertOrUpdate(approvedEntity)
                firestoreRepository.saveRequest(approvedEntity.toDto())
                Log.i(TAG, "Access request verified and updated to APPROVED in local Room & remote Firestore")
                return@withContext PgResult.Success(approvedEntity.toDomainModel())
            }

            if (pendingEntity == null) {
                return@withContext PgResult.Success(null)
            }

            PgResult.Success(pendingEntity.toDomainModel())
        } catch (e: Exception) {
            PgResult.Failure(PgError.DatabaseError("Failed to check request status: ${e.localizedMessage}"))
        }
    }

    override fun getFormHistoryForPropertyFlow(propertyId: String): Flow<List<FormVersionHistory>> {
        return historyDao.getHistoryForPropertyFlow(propertyId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun recordFormReplacement(
        propertyId: String,
        oldForm: TenantRegistrationForm,
        newForm: TenantRegistrationForm
    ): PgResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val replacedBy = authManager.accountInfoFlow.value.email

            val historyEntity = FormVersionHistoryEntity(
                id = "hist_${UUID.randomUUID()}",
                propertyId = propertyId,
                formId = oldForm.formId,
                formTitle = oldForm.formTitle,
                responderUri = oldForm.responderUri,
                editUri = oldForm.editUri,
                googleAccountEmail = oldForm.googleAccountEmail,
                formVersion = oldForm.formVersion,
                replacedAt = now,
                replacedByEmail = replacedBy,
                createdAt = oldForm.createdAt
            )

            historyDao.insert(historyEntity)
            firestoreRepository.saveHistory(historyEntity.toDto())

            Log.i(TAG, "Recorded form replacement history for property $propertyId: ${oldForm.formId} -> ${newForm.formId}")
            PgResult.Success(Unit)
        } catch (e: Exception) {
            PgResult.Failure(PgError.DatabaseError("Failed to record form replacement history: ${e.localizedMessage}"))
        }
    }
}
