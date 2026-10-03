package com.example.features.googleform.data.repository

import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.database.PendingTenantRegistrationDao
import com.example.data.database.PendingTenantRegistrationEntity
import com.example.data.database.TenantDao
import com.example.data.database.TenantEntity
import com.example.data.database.TenantRegistrationFormDao
import com.example.data.firestore.model.PendingTenantRegistrationDto
import com.example.data.firestore.repository.FirestorePendingTenantRegistrationRepository
import com.example.features.googleform.data.api.GoogleFormsApiService
import com.example.features.googleform.data.auth.GoogleFormsAuthManager
import com.example.features.googleform.domain.model.PendingRegistrationStatus
import com.example.features.googleform.domain.model.PendingTenantRegistration
import com.example.features.googleform.domain.model.QuestionKeys
import com.example.features.googleform.domain.repository.PendingTenantRegistrationRepository
import com.example.features.tenants.domain.usecase.AddTenantUseCase
import com.example.features.tenants.domain.usecase.TenantValidationResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingTenantRegistrationRepositoryImpl @Inject constructor(
    private val pendingDao: PendingTenantRegistrationDao,
    private val formDao: TenantRegistrationFormDao,
    private val tenantDao: TenantDao,
    private val firestorePendingRepo: FirestorePendingTenantRegistrationRepository,
    private val apiService: GoogleFormsApiService,
    private val authManager: GoogleFormsAuthManager,
    private val addTenantUseCase: AddTenantUseCase,
    private val firebaseAuth: FirebaseAuth
) : PendingTenantRegistrationRepository {

    private val currentOwnerId: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    private val currentOwnerEmail: String
        get() = firebaseAuth.currentUser?.email ?: authManager.accountInfoFlow.value.email

    override fun getRegistrationsForPropertyFlow(propertyId: String): Flow<List<PendingTenantRegistration>> {
        return pendingDao.getRegistrationsForPropertyFlow(propertyId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getPendingCountFlow(propertyId: String): Flow<Int> {
        return pendingDao.getPendingCountFlow(propertyId)
    }

    override suspend fun getRegistrationById(cloudId: String): PgResult<PendingTenantRegistration?> = withContext(Dispatchers.IO) {
        try {
            val local = pendingDao.getRegistrationByCloudId(cloudId)
            PgResult.Success(local?.toDomain())
        } catch (e: Exception) {
            PgResult.Failure(PgError.DatabaseError("Failed to load registration: ${e.localizedMessage}"))
        }
    }

    override suspend fun syncResponses(propertyId: String): PgResult<Int> = withContext(Dispatchers.IO) {
        try {
            // 1. Get active Form for property
            val formEntity = formDao.getFormForProperty(propertyId)
                ?: return@withContext PgResult.Failure(PgError.ValidationError("No registration form found for this property. Please setup Google Form first."))

            if (formEntity.formId.isBlank()) {
                return@withContext PgResult.Failure(PgError.ValidationError("Google Form ID is missing. Please create or publish form first."))
            }

            // 2. Authorize Google Forms API
            val authHeaderResult = authManager.getAuthorizationHeader()
            val authHeader = when (authHeaderResult) {
                is PgResult.Success -> authHeaderResult.data
                is PgResult.Failure -> return@withContext PgResult.Failure(authHeaderResult.error)
            }

            // 3. Build inverted question mapping: questionId -> QuestionKey
            val questionMapping = mutableMapOf<String, String>() // QuestionKey -> QuestionId
            val invertedMapping = mutableMapOf<String, String>() // QuestionId -> QuestionKey
            try {
                if (formEntity.questionMapping.isNotBlank()) {
                    val json = JSONObject(formEntity.questionMapping)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val qId = json.optString(k, "")
                        if (qId.isNotBlank()) {
                            questionMapping[k] = qId
                            invertedMapping[qId] = k
                        }
                    }
                }
            } catch (_: Exception) {}

            // Also prepare fallback title-based mapping from known questions if needed
            val questionKeyByTitle = QuestionKeys.ALL_QUESTIONS.associate { it.title.lowercase(Locale.ROOT) to it.key }

            // 4. Fetch all active tenants to detect duplicates
            val existingTenants: List<TenantEntity> = tenantDao.getAllTenants()

            // 5. Fetch responses with pagination
            var pageToken: String? = null
            var newImportedCount = 0

            do {
                val response = try {
                    apiService.listResponses(
                        authorization = authHeader,
                        formId = formEntity.formId,
                        pageToken = pageToken,
                        pageSize = 50
                    )
                } catch (e: Exception) {
                    return@withContext PgResult.Failure(PgError.NetworkError("Failed to fetch Google Form responses: ${e.localizedMessage}"))
                }

                if (!response.isSuccessful || response.body() == null) {
                    val errorBody = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                    return@withContext PgResult.Failure(PgError.NetworkError("Google Forms response fetch failed: $errorBody"))
                }

                val listBody = response.body()!!
                val submissions = listBody.responses ?: emptyList()

                for (sub in submissions) {
                    val respId = sub.responseId
                    val existingLocal = pendingDao.getRegistrationByResponseId(formEntity.formId, respId)

                    // If already reviewed (ACCEPTED or REJECTED), skip to preserve owner review
                    if (existingLocal != null && (existingLocal.status == "ACCEPTED" || existingLocal.status == "REJECTED")) {
                        continue
                    }

                    // Extract answers keyed by QuestionKey
                    val answersMap = mutableMapOf<String, String>()
                    sub.answers?.forEach { (qId, answer) ->
                        val matchedKey = invertedMapping[qId]
                        val textValue = answer.textAnswers?.answers?.firstOrNull()?.value?.trim() ?: ""
                        if (matchedKey != null && textValue.isNotBlank()) {
                            answersMap[matchedKey] = textValue
                        }
                    }

                    val fullName = answersMap[QuestionKeys.FULL_NAME] ?: ""
                    val phone = answersMap[QuestionKeys.PHONE] ?: ""
                    val email = answersMap[QuestionKeys.EMAIL] ?: ""
                    val emergencyName = answersMap[QuestionKeys.EMERGENCY_NAME] ?: ""
                    val emergencyPhone = answersMap[QuestionKeys.EMERGENCY_PHONE] ?: ""
                    val emergencyRelation = answersMap[QuestionKeys.EMERGENCY_RELATION] ?: ""
                    val permanentAddress = answersMap[QuestionKeys.PERMANENT_ADDRESS] ?: ""
                    val currentAddress = answersMap[QuestionKeys.CURRENT_ADDRESS] ?: ""
                    val occupation = answersMap[QuestionKeys.OCCUPATION] ?: ""
                    val organization = answersMap[QuestionKeys.ORGANIZATION] ?: ""
                    val expectedJoiningDate = answersMap[QuestionKeys.EXPECTED_JOINING_DATE] ?: ""
                    val notes = answersMap[QuestionKeys.NOTES] ?: ""

                    // Parse submittedAt timestamp
                    val rawTime = sub.lastSubmittedTime ?: sub.createTime ?: ""
                    val submittedAt = parseRfc3339(rawTime)

                    // Check duplicate phone against existing active or past tenants
                    val cleanPhone = phone.filter { it.isDigit() }
                    val matchedTenant = if (cleanPhone.length >= 10) {
                        val suffix = cleanPhone.takeLast(10)
                        existingTenants.find { t: TenantEntity ->
                            val tClean = t.phone.filter { it.isDigit() }
                            !t.deleted && tClean.endsWith(suffix)
                        }
                    } else null

                    val isDuplicate = matchedTenant != null
                    val duplicateName = matchedTenant?.name ?: ""
                    val duplicateRoom = matchedTenant?.roomNumber ?: ""
                    val duplicateStatus = if (matchedTenant != null) {
                        if (matchedTenant.deleted) "Past Tenant" else "Active (Room ${matchedTenant.roomNumber}, Bed ${matchedTenant.bedId})"
                    } else ""

                    val status = if (isDuplicate) "DUPLICATE" else "PENDING"
                    val now = System.currentTimeMillis()

                    val entity = if (existingLocal != null) {
                        existingLocal.copy(
                            fullName = if (existingLocal.fullName.isNotBlank()) existingLocal.fullName else fullName,
                            phone = if (existingLocal.phone.isNotBlank()) existingLocal.phone else phone,
                            email = if (existingLocal.email.isNotBlank()) existingLocal.email else email,
                            emergencyName = if (existingLocal.emergencyName.isNotBlank()) existingLocal.emergencyName else emergencyName,
                            emergencyPhone = if (existingLocal.emergencyPhone.isNotBlank()) existingLocal.emergencyPhone else emergencyPhone,
                            emergencyRelation = if (existingLocal.emergencyRelation.isNotBlank()) existingLocal.emergencyRelation else emergencyRelation,
                            permanentAddress = if (existingLocal.permanentAddress.isNotBlank()) existingLocal.permanentAddress else permanentAddress,
                            currentAddress = if (existingLocal.currentAddress.isNotBlank()) existingLocal.currentAddress else currentAddress,
                            occupation = if (existingLocal.occupation.isNotBlank()) existingLocal.occupation else occupation,
                            organization = if (existingLocal.organization.isNotBlank()) existingLocal.organization else organization,
                            expectedJoiningDate = if (existingLocal.expectedJoiningDate.isNotBlank()) existingLocal.expectedJoiningDate else expectedJoiningDate,
                            notes = if (existingLocal.notes.isNotBlank()) existingLocal.notes else notes,
                            duplicateMatchedTenantName = duplicateName,
                            duplicateMatchedTenantRoom = duplicateRoom,
                            duplicateMatchedTenantStatus = duplicateStatus,
                            updatedAt = now
                        )
                    } else {
                        newImportedCount++
                        PendingTenantRegistrationEntity(
                            cloudId = UUID.randomUUID().toString(),
                            ownerId = currentOwnerId,
                            propertyId = propertyId,
                            formId = formEntity.formId,
                            responseId = respId,
                            submittedAt = if (submittedAt > 0L) submittedAt else now,
                            formVersion = formEntity.formVersion,
                            fullName = fullName,
                            phone = phone,
                            email = email,
                            emergencyName = emergencyName,
                            emergencyPhone = emergencyPhone,
                            emergencyRelation = emergencyRelation,
                            permanentAddress = permanentAddress,
                            currentAddress = currentAddress,
                            occupation = occupation,
                            organization = organization,
                            expectedJoiningDate = expectedJoiningDate,
                            notes = notes,
                            status = status,
                            createdTenantCloudId = "",
                            reviewedAt = 0L,
                            reviewedBy = "",
                            rejectionReason = "",
                            duplicateMatchedTenantName = duplicateName,
                            duplicateMatchedTenantRoom = duplicateRoom,
                            duplicateMatchedTenantStatus = duplicateStatus,
                            createdAt = now,
                            updatedAt = now,
                            deleted = false,
                            syncStatus = "SYNCED",
                            lastSyncedAt = now
                        )
                    }

                    pendingDao.insertOrUpdate(entity)
                    firestorePendingRepo.saveRegistration(entity.toDto())
                }

                pageToken = listBody.nextPageToken
            } while (!pageToken.isNullOrBlank())

            PgResult.Success(newImportedCount)
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to sync responses: ${e.localizedMessage}", e))
        }
    }

    override suspend fun updateRegistration(registration: PendingTenantRegistration): PgResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = registration.toEntity().copy(updatedAt = System.currentTimeMillis())
            pendingDao.update(entity)
            firestorePendingRepo.saveRegistration(entity.toDto())
            PgResult.Success(Unit)
        } catch (e: Exception) {
            PgResult.Failure(PgError.DatabaseError("Failed to update registration: ${e.localizedMessage}"))
        }
    }

    override suspend fun rejectRegistration(cloudId: String, reason: String): PgResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val reviewer = currentOwnerEmail.ifBlank { "Owner" }
            pendingDao.updateReviewStatus(
                cloudId = cloudId,
                status = "REJECTED",
                reviewedAt = now,
                reviewedBy = reviewer,
                rejectionReason = reason,
                tenantCloudId = "",
                updatedAt = now
            )
            firestorePendingRepo.updateReviewStatus(
                cloudId = cloudId,
                status = "REJECTED",
                reviewedBy = reviewer,
                rejectionReason = reason,
                createdTenantCloudId = ""
            )
            PgResult.Success(Unit)
        } catch (e: Exception) {
            PgResult.Failure(PgError.DatabaseError("Failed to reject registration: ${e.localizedMessage}"))
        }
    }

    override suspend fun acceptRegistration(
        cloudId: String,
        roomNumber: String,
        bedId: String,
        monthlyRent: Double,
        securityDeposit: Double,
        moveInDate: String,
        gender: String,
        kycDocType: String,
        notes: String
    ): PgResult<TenantEntity> = withContext(Dispatchers.IO) {
        try {
            val regEntity = pendingDao.getRegistrationByCloudId(cloudId)
                ?: return@withContext PgResult.Failure(PgError.ValidationError("Pending registration not found."))

            if (regEntity.status == "ACCEPTED") {
                return@withContext PgResult.Failure(PgError.ValidationError("This registration has already been approved."))
            }

            // 1. Create tenant using standard AddTenantUseCase
            val combinedNotes = buildString {
                if (notes.isNotBlank()) append(notes)
                if (regEntity.notes.isNotBlank()) {
                    if (isNotEmpty()) append(" | ")
                    append("Google Form Notes: ${regEntity.notes}")
                }
            }

            val result = addTenantUseCase.execute(
                name = regEntity.fullName,
                phone = regEntity.phone,
                email = regEntity.email,
                emergencyContact = regEntity.emergencyPhone.ifBlank { regEntity.emergencyName },
                roomNumber = roomNumber,
                bedId = bedId,
                monthlyRent = monthlyRent,
                securityDeposit = securityDeposit,
                advancePaid = 0.0,
                moveInDate = moveInDate,
                kycDocType = kycDocType,
                alternateContact = regEntity.emergencyPhone,
                dob = "",
                gender = gender,
                address = regEntity.permanentAddress.ifBlank { regEntity.currentAddress },
                occupation = regEntity.occupation,
                companyOrCollege = regEntity.organization,
                leavingDate = "",
                notes = combinedNotes
            )

            when (result) {
                is TenantValidationResult.Success -> {
                    // Fetch the created tenant
                    val allTenants: List<TenantEntity> = tenantDao.getAllTenants()
                    val cleanPhone = regEntity.phone.filter { it.isDigit() }
                    val createdTenant = allTenants.find { t: TenantEntity -> !t.deleted && t.roomNumber == roomNumber.trim() && t.phone.filter { p -> p.isDigit() } == cleanPhone }
                        ?: allTenants.lastOrNull()

                    val tenantCloudId = createdTenant?.cloudId ?: "tenant_${UUID.randomUUID()}"
                    val now = System.currentTimeMillis()
                    val reviewer = currentOwnerEmail.ifBlank { "Owner" }

                    // Mark pending registration as ACCEPTED
                    pendingDao.updateReviewStatus(
                        cloudId = cloudId,
                        status = "ACCEPTED",
                        reviewedAt = now,
                        reviewedBy = reviewer,
                        rejectionReason = "",
                        tenantCloudId = tenantCloudId,
                        updatedAt = now
                    )
                    firestorePendingRepo.updateReviewStatus(
                        cloudId = cloudId,
                        status = "ACCEPTED",
                        reviewedBy = reviewer,
                        rejectionReason = "",
                        createdTenantCloudId = tenantCloudId
                    )

                    if (createdTenant != null) {
                        PgResult.Success(createdTenant)
                    } else {
                        PgResult.Failure(PgError.DatabaseError("Tenant was created but could not be retrieved."))
                    }
                }
                is TenantValidationResult.Error -> {
                    PgResult.Failure(PgError.ValidationError(result.message))
                }
            }
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to accept tenant registration: ${e.localizedMessage}", e))
        }
    }

    private fun parseRfc3339(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(dateStr)?.time ?: run {
                val fallbackSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                fallbackSdf.parse(dateStr)?.time ?: 0L
            }
        } catch (_: Exception) {
            try {
                val simple = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                simple.parse(dateStr)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }
}

// -------------------------------------------------------------
// Model Mappers
// -------------------------------------------------------------

fun PendingTenantRegistrationEntity.toDomain(): PendingTenantRegistration {
    return PendingTenantRegistration(
        cloudId = cloudId,
        ownerId = ownerId,
        propertyId = propertyId,
        formId = formId,
        responseId = responseId,
        submittedAt = submittedAt,
        formVersion = formVersion,
        fullName = fullName,
        phone = phone,
        email = email,
        emergencyName = emergencyName,
        emergencyPhone = emergencyPhone,
        emergencyRelation = emergencyRelation,
        permanentAddress = permanentAddress,
        currentAddress = currentAddress,
        occupation = occupation,
        organization = organization,
        expectedJoiningDate = expectedJoiningDate,
        notes = notes,
        status = PendingRegistrationStatus.fromString(status),
        createdTenantCloudId = createdTenantCloudId,
        reviewedAt = reviewedAt,
        reviewedBy = reviewedBy,
        rejectionReason = rejectionReason,
        duplicateMatchedTenantName = duplicateMatchedTenantName,
        duplicateMatchedTenantRoom = duplicateMatchedTenantRoom,
        duplicateMatchedTenantStatus = duplicateMatchedTenantStatus,
        claimedByDeviceId = claimedByDeviceId,
        claimedAt = claimedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun PendingTenantRegistration.toEntity(): PendingTenantRegistrationEntity {
    return PendingTenantRegistrationEntity(
        cloudId = cloudId,
        ownerId = ownerId,
        propertyId = propertyId,
        formId = formId,
        responseId = responseId,
        submittedAt = submittedAt,
        formVersion = formVersion,
        fullName = fullName,
        phone = phone,
        email = email,
        emergencyName = emergencyName,
        emergencyPhone = emergencyPhone,
        emergencyRelation = emergencyRelation,
        permanentAddress = permanentAddress,
        currentAddress = currentAddress,
        occupation = occupation,
        organization = organization,
        expectedJoiningDate = expectedJoiningDate,
        notes = notes,
        status = status.dbValue,
        createdTenantCloudId = createdTenantCloudId,
        reviewedAt = reviewedAt,
        reviewedBy = reviewedBy,
        rejectionReason = rejectionReason,
        duplicateMatchedTenantName = duplicateMatchedTenantName,
        duplicateMatchedTenantRoom = duplicateMatchedTenantRoom,
        duplicateMatchedTenantStatus = duplicateMatchedTenantStatus,
        claimedByDeviceId = claimedByDeviceId,
        claimedAt = claimedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = false,
        syncStatus = "SYNCED",
        lastSyncedAt = updatedAt
    )
}

fun PendingTenantRegistrationEntity.toDto(): PendingTenantRegistrationDto {
    return PendingTenantRegistrationDto(
        cloudId = cloudId,
        ownerId = ownerId,
        propertyId = propertyId,
        formId = formId,
        responseId = responseId,
        submittedAt = submittedAt,
        formVersion = formVersion,
        fullName = fullName,
        phone = phone,
        email = email,
        emergencyName = emergencyName,
        emergencyPhone = emergencyPhone,
        emergencyRelation = emergencyRelation,
        permanentAddress = permanentAddress,
        currentAddress = currentAddress,
        occupation = occupation,
        organization = organization,
        expectedJoiningDate = expectedJoiningDate,
        notes = notes,
        status = status,
        createdTenantCloudId = createdTenantCloudId,
        reviewedAt = reviewedAt,
        reviewedBy = reviewedBy,
        rejectionReason = rejectionReason,
        duplicateMatchedTenantName = duplicateMatchedTenantName,
        duplicateMatchedTenantRoom = duplicateMatchedTenantRoom,
        duplicateMatchedTenantStatus = duplicateMatchedTenantStatus,
        claimedByDeviceId = claimedByDeviceId,
        claimedAt = claimedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        syncStatus = syncStatus,
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
}
