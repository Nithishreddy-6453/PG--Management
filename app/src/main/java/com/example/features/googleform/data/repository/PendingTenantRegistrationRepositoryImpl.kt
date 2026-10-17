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
import com.example.core.device.DeviceIdentityManager
import com.example.features.googleform.domain.model.PendingTenantRegistration
import com.example.features.googleform.domain.model.QuestionKeys
import com.example.features.googleform.domain.repository.PendingTenantRegistrationRepository
import com.example.features.tenants.domain.usecase.AddTenantUseCase
import com.example.features.tenants.domain.usecase.TenantValidationResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val firebaseAuth: FirebaseAuth,
    private val deviceIdentityManager: DeviceIdentityManager
) : PendingTenantRegistrationRepository {

    private val myDeviceId: String
        get() = deviceIdentityManager.getDeviceId()

    private val currentOwnerId: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    private val currentOwnerEmail: String
        get() = firebaseAuth.currentUser?.email ?: authManager.accountInfoFlow.value.email

    override fun getRegistrationsForPropertyFlow(propertyId: String): Flow<List<PendingTenantRegistration>> {
        return pendingDao.getRegistrationsForPropertyFlow(propertyId).map { list ->
            val allTenants = try { tenantDao.getAllTenants().filter { !it.deleted } } catch (_: Exception) { emptyList() }
            list.map { entity ->
                // Automatic reconciliation: If registration is in PENDING/NEEDS_REVIEW/DUPLICATE/ACCEPTING,
                // but an active tenant already exists in the system that corresponds to this registration,
                // reconcile it to ACCEPTED so it doesn't stay in "Needs Approval" state!
                if (entity.status != "ACCEPTED" && entity.status != "REJECTED") {
                    val cleanPhone = entity.phone.filter { it.isDigit() }
                    val matchedTenant = allTenants.firstOrNull { t ->
                        (entity.createdTenantCloudId.isNotBlank() && t.cloudId == entity.createdTenantCloudId) ||
                        (entity.cloudId.isNotBlank() && t.cloudId == "tenant_${entity.cloudId.removePrefix("reg_")}") ||
                        (cleanPhone.length >= 7 && t.phone.filter { it.isDigit() }.endsWith(cleanPhone.takeLast(minOf(10, cleanPhone.length)))) ||
                        (cleanPhone.length >= 7 && cleanPhone.endsWith(t.phone.filter { it.isDigit() }.takeLast(minOf(10, t.phone.length)))) ||
                        (entity.fullName.isNotBlank() && t.name.trim().equals(entity.fullName.trim(), ignoreCase = true))
                    }
                    if (matchedTenant != null) {
                        val now = System.currentTimeMillis()
                        val tenantId = matchedTenant.cloudId.ifBlank { "tenant_${entity.cloudId.removePrefix("reg_")}" }
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                pendingDao.updateReviewStatus(
                                    cloudId = entity.cloudId,
                                    status = "ACCEPTED",
                                    reviewedAt = now,
                                    reviewedBy = "Owner",
                                    rejectionReason = "",
                                    tenantCloudId = tenantId,
                                    updatedAt = now
                                )
                                if (entity.responseId.isNotBlank() && entity.responseId != entity.cloudId) {
                                    pendingDao.updateReviewStatus(
                                        cloudId = entity.responseId,
                                        status = "ACCEPTED",
                                        reviewedAt = now,
                                        reviewedBy = "Owner",
                                        rejectionReason = "",
                                        tenantCloudId = tenantId,
                                        updatedAt = now
                                    )
                                }
                                firestorePendingRepo.finalizeAcceptance(
                                    cloudId = entity.cloudId,
                                    claimantDeviceId = myDeviceId,
                                    createdTenantCloudId = tenantId,
                                    reviewedBy = "Owner"
                                )
                            } catch (_: Exception) {}
                        }
                        return@map entity.copy(
                            status = "ACCEPTED",
                            createdTenantCloudId = tenantId,
                            reviewedAt = now,
                            reviewedBy = "Owner",
                            duplicateMatchedTenantRoom = matchedTenant.roomNumber
                        ).toDomain()
                    }
                }
                entity.toDomain()
            }
        }
    }

    override fun getPendingCountFlow(propertyId: String): Flow<Int> {
        return pendingDao.getPendingCountFlow(propertyId)
    }

    override suspend fun getRegistrationById(cloudId: String): PgResult<PendingTenantRegistration?> = withContext(Dispatchers.IO) {
        try {
            val cleanTarget = cloudId.removePrefix("reg_")
            var local = pendingDao.getRegistrationByCloudId(cloudId)
                ?: pendingDao.getRegistrationByCloudId(cleanTarget)
                ?: pendingDao.getRegistrationByCloudId("reg_$cleanTarget")
                ?: pendingDao.getRegistrationByResponseIdOnly(cloudId)
                ?: pendingDao.getRegistrationByResponseIdOnly(cleanTarget)

            if (local == null) {
                val propertyId = formDao.getAllForms().firstOrNull()?.propertyId ?: ""
                val allLocal = pendingDao.getAllForProperty(propertyId)
                local = allLocal.find { 
                    it.cloudId == cloudId || 
                    it.responseId == cloudId || 
                    it.cloudId.removePrefix("reg_") == cleanTarget || 
                    it.responseId.removePrefix("reg_") == cleanTarget 
                }
            }
            if (local != null) {
                if (local.status != "ACCEPTED" && local.status != "REJECTED") {
                    val allTenants = try { tenantDao.getAllTenants().filter { !it.deleted } } catch (_: Exception) { emptyList() }
                    val cleanPhone = local.phone.filter { it.isDigit() }
                    val matchedTenant = allTenants.firstOrNull { t ->
                        (local.createdTenantCloudId.isNotBlank() && t.cloudId == local.createdTenantCloudId) ||
                        (local.cloudId.isNotBlank() && t.cloudId == "tenant_${local.cloudId.removePrefix("reg_")}") ||
                        (cleanPhone.length >= 7 && t.phone.filter { it.isDigit() }.endsWith(cleanPhone.takeLast(minOf(10, cleanPhone.length)))) ||
                        (cleanPhone.length >= 7 && cleanPhone.endsWith(t.phone.filter { it.isDigit() }.takeLast(minOf(10, t.phone.length)))) ||
                        (local.fullName.isNotBlank() && t.name.trim().equals(local.fullName.trim(), ignoreCase = true))
                    }
                    if (matchedTenant != null) {
                        val now = System.currentTimeMillis()
                        val tenantId = matchedTenant.cloudId.ifBlank { "tenant_${local.cloudId.removePrefix("reg_")}" }
                        pendingDao.updateReviewStatus(
                            cloudId = local.cloudId,
                            status = "ACCEPTED",
                            reviewedAt = now,
                            reviewedBy = "Owner",
                            rejectionReason = "",
                            tenantCloudId = tenantId,
                            updatedAt = now
                        )
                        local = local.copy(
                            status = "ACCEPTED",
                            createdTenantCloudId = tenantId,
                            reviewedAt = now,
                            reviewedBy = "Owner",
                            duplicateMatchedTenantRoom = matchedTenant.roomNumber
                        )
                    }
                }
                PgResult.Success(local.toDomain())
            } else {
                // Query Firestore directly as authoritative cloud fallback
                when (val remoteResult = firestorePendingRepo.getRegistrationByCloudId(cloudId)) {
                    is PgResult.Success -> {
                        val dto = remoteResult.data
                        if (dto != null) {
                            val entity = dto.toEntity()
                            pendingDao.insertOrUpdate(entity)
                            PgResult.Success(entity.toDomain())
                        } else {
                            PgResult.Success(null)
                        }
                    }
                    is PgResult.Failure -> {
                        PgResult.Success(null)
                    }
                }
            }
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

            val qIdToKey = mutableMapOf<String, String>()
            var formItemsList: List<com.example.features.googleform.data.api.FormItem>? = null

            try {
                val formResponse = apiService.getForm(authHeader, formEntity.formId)
                if (formResponse.isSuccessful && formResponse.body() != null) {
                    val formDetails = formResponse.body()!!
                    formItemsList = formDetails.items
                    formItemsList?.forEachIndexed { index, item ->
                        val qId = item.questionItem?.question?.questionId
                        val itemId = item.itemId
                        val title = item.title ?: ""
                        val matchedKey = QuestionKeys.resolveQuestionKey(title, index)
                        if (matchedKey != null) {
                            if (!qId.isNullOrBlank()) {
                                qIdToKey[qId] = matchedKey
                                invertedMapping[qId] = matchedKey
                                questionMapping[matchedKey] = qId
                            }
                            if (!itemId.isNullOrBlank()) {
                                qIdToKey[itemId] = matchedKey
                                invertedMapping[itemId] = matchedKey
                            }
                        }
                        item.questionGroupItem?.questions?.forEach { gq ->
                            val gqId = gq.questionId
                            if (!gqId.isNullOrBlank() && matchedKey != null) {
                                qIdToKey[gqId] = matchedKey
                                invertedMapping[gqId] = matchedKey
                                questionMapping[matchedKey] = gqId
                            }
                        }
                    }

                    // Persist discovered mapping into Room so offline/subsequent reads are always in sync
                    val updatedMappingJson = JSONObject(questionMapping as Map<*, *>).toString()
                    val updatedFormEntity = formEntity.copy(
                        questionMapping = updatedMappingJson,
                        updatedAt = System.currentTimeMillis()
                    )
                    formDao.insertOrUpdateForm(updatedFormEntity)
                }
            } catch (_: Exception) {}

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

                    // Extract answers keyed by QuestionKey with robust resolution
                    val answersMap = mutableMapOf<String, String>()
                    val answersEntries = sub.answers?.entries?.toList() ?: emptyList()
                    answersEntries.forEach { (qId, answerObj) ->
                        val textValue = extractAnswerValue(answerObj)
                        if (textValue.isNotBlank()) {
                            var matchedKey = invertedMapping[qId] ?: qIdToKey[qId]

                            // If not mapped directly, search formItems by questionId or itemId or in questionGroup
                            if (matchedKey == null && formItemsList != null) {
                                val item = formItemsList.find { item ->
                                    item.questionItem?.question?.questionId == qId ||
                                    item.itemId == qId ||
                                    item.questionGroupItem?.questions?.any { it.questionId == qId } == true
                                }
                                if (item != null) {
                                    matchedKey = QuestionKeys.resolveQuestionKey(item.title ?: "")
                                }
                            }

                            if (matchedKey != null) {
                                answersMap[matchedKey] = textValue
                            }
                        }
                    }

                    val fullName = answersMap[QuestionKeys.FULL_NAME] ?: ""
                    val phone = answersMap[QuestionKeys.PHONE] ?: ""
                    val email = answersMap[QuestionKeys.EMAIL] ?: ""
                    val dob = answersMap[QuestionKeys.DOB] ?: ""
                    val gender = answersMap[QuestionKeys.GENDER] ?: ""
                    
                    val permanentAddress = answersMap[QuestionKeys.PERMANENT_ADDRESS] ?: ""
                    val currentAddress = answersMap[QuestionKeys.CURRENT_ADDRESS] ?: ""
                    val occupation = answersMap[QuestionKeys.OCCUPATION] ?: ""
                    val occupationOther = answersMap[QuestionKeys.OCCUPATION_OTHER] ?: ""
                    val organization = answersMap[QuestionKeys.ORGANIZATION] ?: ""
                    val notes = answersMap[QuestionKeys.NOTES] ?: ""
                    val foodPreference = answersMap[QuestionKeys.FOOD_PREFERENCE] ?: ""
                    val expectedJoiningDate = answersMap[QuestionKeys.EXPECTED_JOINING_DATE] ?: answersMap["EXPECTED_JOINING_DATE"] ?: ""
                    val expectedVacateDate = answersMap[QuestionKeys.EXPECTED_VACATE_DATE] ?: answersMap["EXPECTED_VACATE_DATE"] ?: ""
                    val refundableDeposit = answersMap[QuestionKeys.REFUNDABLE_DEPOSIT] ?: answersMap["REFUNDABLE_DEPOSIT"] ?: ""
                    val fatherName = answersMap[QuestionKeys.FATHER_NAME] ?: ""
                    val fatherPhone = answersMap[QuestionKeys.FATHER_PHONE] ?: ""
                    val rawGuardianName = answersMap[QuestionKeys.GUARDIAN_NAME] ?: ""
                    val rawGuardianRel = answersMap[QuestionKeys.GUARDIAN_RELATIONSHIP] ?: ""
                    val rawGuardianPhone = answersMap[QuestionKeys.GUARDIAN_PHONE] ?: ""

                    val emergencyNameFromForm = answersMap[QuestionKeys.EMERGENCY_NAME] ?: ""
                    val emergencyPhoneFromForm = answersMap[QuestionKeys.EMERGENCY_PHONE] ?: ""
                    val emergencyRelFromForm = answersMap[QuestionKeys.EMERGENCY_RELATION] ?: ""

                    val guardianName = rawGuardianName.ifBlank { emergencyNameFromForm }
                    val guardianRelationship = rawGuardianRel.ifBlank { emergencyRelFromForm.ifBlank { "Parent" } }
                    val guardianPhone = rawGuardianPhone.ifBlank { emergencyPhoneFromForm }

                    val localRefName = answersMap[QuestionKeys.LOCAL_REF_NAME] ?: ""
                    val localRefPhone = answersMap[QuestionKeys.LOCAL_REF_PHONE] ?: ""
                    val officeName = organization.ifBlank { answersMap["OFFICE_NAME"] ?: "" }
                    val officeAddress = answersMap[QuestionKeys.OFFICE_ADDRESS] ?: ""
                    val officePhone = answersMap[QuestionKeys.OFFICE_PHONE] ?: ""
                    val vehicleNumber = answersMap[QuestionKeys.VEHICLE_NUMBER] ?: ""
                    
                    val emergencyName = emergencyNameFromForm.ifBlank { guardianName.ifBlank { fatherName.ifBlank { localRefName } } }
                    val emergencyPhone = emergencyPhoneFromForm.ifBlank { guardianPhone.ifBlank { fatherPhone.ifBlank { localRefPhone } } }
                    val emergencyRelation = emergencyRelFromForm.ifBlank { guardianRelationship.ifBlank { "Parent" } }

                    // Parse submittedAt timestamp
                    val rawTime = sub.lastSubmittedTime ?: sub.createTime ?: ""
                    val submittedAt = parseRfc3339(rawTime)

                    // Check duplicate phone or name against existing active or past tenants
                    val cleanPhone = phone.filter { it.isDigit() }
                    val matchedTenant = if (cleanPhone.length >= 7) {
                        val suffix = cleanPhone.takeLast(minOf(10, cleanPhone.length))
                        existingTenants.find { t: TenantEntity ->
                            val tClean = t.phone.filter { it.isDigit() }
                            !t.deleted && (tClean.endsWith(suffix) || suffix.endsWith(tClean.takeLast(minOf(10, tClean.length))))
                        } ?: existingTenants.find { t: TenantEntity ->
                            !t.deleted && fullName.isNotBlank() && t.name.trim().equals(fullName.trim(), ignoreCase = true)
                        }
                    } else {
                        existingTenants.find { t: TenantEntity ->
                            !t.deleted && fullName.isNotBlank() && t.name.trim().equals(fullName.trim(), ignoreCase = true)
                        }
                    }

                    val isDuplicate = matchedTenant != null
                    val duplicateName = matchedTenant?.name ?: ""
                    val duplicateRoom = matchedTenant?.roomNumber ?: ""
                    val duplicateStatus = if (matchedTenant != null) {
                        if (matchedTenant.deleted) "Past Tenant" else "Active (Room ${matchedTenant.roomNumber}, Bed ${matchedTenant.bedId})"
                    } else ""

                    // If existing local was already ACCEPTED or if an active tenant already exists for this registration, mark as ACCEPTED
                    val status = when {
                        existingLocal?.status == "ACCEPTED" -> "ACCEPTED"
                        existingLocal?.status == "REJECTED" -> "REJECTED"
                        matchedTenant != null && !matchedTenant.deleted -> "ACCEPTED"
                        isDuplicate -> "DUPLICATE"
                        else -> "PENDING"
                    }
                    val now = System.currentTimeMillis()

                    val entity = if (existingLocal != null) {
                        val effectiveStatus = when {
                            existingLocal.status == "ACCEPTED" -> "ACCEPTED"
                            existingLocal.status == "REJECTED" -> "REJECTED"
                            matchedTenant != null && !matchedTenant.deleted -> "ACCEPTED"
                            else -> existingLocal.status
                        }
                        val createdTenantId = existingLocal.createdTenantCloudId.ifBlank {
                            if (matchedTenant != null && !matchedTenant.deleted) matchedTenant.cloudId else ""
                        }
                        existingLocal.copy(
                            fullName = fullName.ifBlank { existingLocal.fullName },
                            phone = phone.ifBlank { existingLocal.phone },
                            email = email.ifBlank { existingLocal.email },
                            dob = dob.ifBlank { existingLocal.dob },
                            gender = gender.ifBlank { existingLocal.gender.ifBlank { "Male" } },
                            emergencyName = emergencyName.ifBlank { existingLocal.emergencyName },
                            emergencyPhone = emergencyPhone.ifBlank { existingLocal.emergencyPhone },
                            emergencyRelation = emergencyRelation.ifBlank { existingLocal.emergencyRelation.ifBlank { "Parent" } },
                            permanentAddress = permanentAddress.ifBlank { existingLocal.permanentAddress },
                            currentAddress = currentAddress.ifBlank { existingLocal.currentAddress },
                            occupation = occupation.ifBlank { existingLocal.occupation },
                            occupationOther = occupationOther.ifBlank { existingLocal.occupationOther },
                            organization = organization.ifBlank { existingLocal.organization },
                            officeName = officeName.ifBlank { existingLocal.officeName },
                            officeAddress = officeAddress.ifBlank { existingLocal.officeAddress },
                            officePhone = officePhone.ifBlank { existingLocal.officePhone },
                            vehicleNumber = vehicleNumber.ifBlank { existingLocal.vehicleNumber },
                            foodPreference = foodPreference.ifBlank { existingLocal.foodPreference.ifBlank { "Veg" } },
                            expectedJoiningDate = expectedJoiningDate.ifBlank { existingLocal.expectedJoiningDate },
                            expectedVacateDate = expectedVacateDate.ifBlank { existingLocal.expectedVacateDate },
                            refundableDeposit = refundableDeposit.ifBlank { existingLocal.refundableDeposit },
                            fatherName = fatherName.ifBlank { existingLocal.fatherName },
                            fatherPhone = fatherPhone.ifBlank { existingLocal.fatherPhone },
                            guardianName = guardianName.ifBlank { existingLocal.guardianName },
                            guardianRelationship = guardianRelationship.ifBlank { existingLocal.guardianRelationship.ifBlank { "Parent" } },
                            guardianPhone = guardianPhone.ifBlank { existingLocal.guardianPhone },
                            localRefName = localRefName.ifBlank { existingLocal.localRefName },
                            localRefPhone = localRefPhone.ifBlank { existingLocal.localRefPhone },
                            notes = notes.ifBlank { existingLocal.notes },
                            status = effectiveStatus,
                            createdTenantCloudId = createdTenantId,
                            reviewedAt = if (effectiveStatus == "ACCEPTED" && existingLocal.reviewedAt == 0L) now else existingLocal.reviewedAt,
                            reviewedBy = if (effectiveStatus == "ACCEPTED" && existingLocal.reviewedBy.isBlank()) "Owner" else existingLocal.reviewedBy,
                            duplicateMatchedTenantName = duplicateName,
                            duplicateMatchedTenantRoom = duplicateRoom,
                            duplicateMatchedTenantStatus = duplicateStatus,
                            updatedAt = now
                        )
                    } else {
                        // SEC-3D2A: Query remote Firestore for existing matching registration before creating deterministic ID
                        val remoteLookupResult = firestorePendingRepo.findExistingRegistrationsForResponse(
                            formId = formEntity.formId,
                            responseId = respId,
                            propertyId = propertyId
                        )

                        val remoteMatches = when (remoteLookupResult) {
                            is PgResult.Failure -> {
                                return@withContext PgResult.Failure(remoteLookupResult.error)
                            }
                            is PgResult.Success -> remoteLookupResult.data
                        }

                        if (remoteMatches.size > 1) {
                            return@withContext PgResult.Failure(
                                PgError.DatabaseError(
                                    "Conflict: Multiple (${remoteMatches.size}) existing registrations found in Firestore for response $respId"
                                )
                            )
                        }

                        val matchedRemote = remoteMatches.firstOrNull()

                        if (matchedRemote != null) {
                            val legacyCloudId = if (matchedRemote.cloudId.isNotBlank()) matchedRemote.cloudId else generateRegistrationCloudId(formEntity.formId, respId)

                            if (matchedRemote.status == "ACCEPTED" || matchedRemote.status == "REJECTED") {
                                val reviewedEntity = PendingTenantRegistrationEntity(
                                    cloudId = legacyCloudId,
                                    ownerId = if (matchedRemote.ownerId.isNotBlank()) matchedRemote.ownerId else currentOwnerId,
                                    propertyId = if (matchedRemote.propertyId.isNotBlank()) matchedRemote.propertyId else propertyId,
                                    formId = formEntity.formId,
                                    responseId = respId,
                                    submittedAt = if (matchedRemote.submittedAt > 0L) matchedRemote.submittedAt else (if (submittedAt > 0L) submittedAt else now),
                                    formVersion = if (matchedRemote.formVersion > 0) matchedRemote.formVersion else formEntity.formVersion,
                                    fullName = if (matchedRemote.fullName.isNotBlank()) matchedRemote.fullName else fullName,
                                    phone = if (matchedRemote.phone.isNotBlank()) matchedRemote.phone else phone,
                                    email = if (matchedRemote.email.isNotBlank()) matchedRemote.email else email,
                                    dob = if (matchedRemote.dob.isNotBlank()) matchedRemote.dob else dob,
                                    gender = if (matchedRemote.gender.isNotBlank()) matchedRemote.gender else gender,
                                    emergencyName = if (matchedRemote.emergencyName.isNotBlank()) matchedRemote.emergencyName else emergencyName,
                                    emergencyPhone = if (matchedRemote.emergencyPhone.isNotBlank()) matchedRemote.emergencyPhone else emergencyPhone,
                                    emergencyRelation = if (matchedRemote.emergencyRelation.isNotBlank()) matchedRemote.emergencyRelation else emergencyRelation,
                                    permanentAddress = if (matchedRemote.permanentAddress.isNotBlank()) matchedRemote.permanentAddress else permanentAddress,
                                    currentAddress = if (matchedRemote.currentAddress.isNotBlank()) matchedRemote.currentAddress else currentAddress,
                                    occupation = if (matchedRemote.occupation.isNotBlank()) matchedRemote.occupation else occupation,
                                    occupationOther = if (matchedRemote.occupationOther.isNotBlank()) matchedRemote.occupationOther else occupationOther,
                                    organization = if (matchedRemote.organization.isNotBlank()) matchedRemote.organization else organization,
                                    officeName = if (matchedRemote.officeName.isNotBlank()) matchedRemote.officeName else officeName,
                                    officeAddress = if (matchedRemote.officeAddress.isNotBlank()) matchedRemote.officeAddress else officeAddress,
                                    officePhone = if (matchedRemote.officePhone.isNotBlank()) matchedRemote.officePhone else officePhone,
                                    vehicleNumber = if (matchedRemote.vehicleNumber.isNotBlank()) matchedRemote.vehicleNumber else vehicleNumber,
                                    foodPreference = if (matchedRemote.foodPreference.isNotBlank()) matchedRemote.foodPreference else foodPreference,
                                    expectedJoiningDate = if (matchedRemote.expectedJoiningDate.isNotBlank()) matchedRemote.expectedJoiningDate else expectedJoiningDate,
                                    expectedVacateDate = if (matchedRemote.expectedVacateDate.isNotBlank()) matchedRemote.expectedVacateDate else expectedVacateDate,
                                    refundableDeposit = if (matchedRemote.refundableDeposit.isNotBlank()) matchedRemote.refundableDeposit else refundableDeposit,
                                    fatherName = if (matchedRemote.fatherName.isNotBlank()) matchedRemote.fatherName else fatherName,
                                    fatherPhone = if (matchedRemote.fatherPhone.isNotBlank()) matchedRemote.fatherPhone else fatherPhone,
                                    guardianName = if (matchedRemote.guardianName.isNotBlank()) matchedRemote.guardianName else guardianName,
                                    guardianRelationship = if (matchedRemote.guardianRelationship.isNotBlank()) matchedRemote.guardianRelationship else guardianRelationship,
                                    guardianPhone = if (matchedRemote.guardianPhone.isNotBlank()) matchedRemote.guardianPhone else guardianPhone,
                                    localRefName = if (matchedRemote.localRefName.isNotBlank()) matchedRemote.localRefName else localRefName,
                                    localRefPhone = if (matchedRemote.localRefPhone.isNotBlank()) matchedRemote.localRefPhone else localRefPhone,
                                    notes = if (matchedRemote.notes.isNotBlank()) matchedRemote.notes else notes,
                                    status = matchedRemote.status,
                                    createdTenantCloudId = matchedRemote.createdTenantCloudId,
                                    reviewedAt = matchedRemote.reviewedAt,
                                    reviewedBy = matchedRemote.reviewedBy,
                                    rejectionReason = matchedRemote.rejectionReason,
                                    claimedByDeviceId = matchedRemote.claimedByDeviceId,
                                    claimedAt = matchedRemote.claimedAt,
                                    duplicateMatchedTenantName = if (matchedRemote.duplicateMatchedTenantName.isNotBlank()) matchedRemote.duplicateMatchedTenantName else duplicateName,
                                    duplicateMatchedTenantRoom = if (matchedRemote.duplicateMatchedTenantRoom.isNotBlank()) matchedRemote.duplicateMatchedTenantRoom else duplicateRoom,
                                    duplicateMatchedTenantStatus = if (matchedRemote.duplicateMatchedTenantStatus.isNotBlank()) matchedRemote.duplicateMatchedTenantStatus else duplicateStatus,
                                    createdAt = if (matchedRemote.createdAt > 0L) matchedRemote.createdAt else now,
                                    updatedAt = if (matchedRemote.updatedAt > 0L) matchedRemote.updatedAt else now,
                                    deleted = matchedRemote.deleted,
                                    syncStatus = "SYNCED",
                                    lastSyncedAt = now,
                                    lastModifiedByDeviceId = matchedRemote.lastModifiedByDeviceId
                                )
                                pendingDao.insertOrUpdate(reviewedEntity)
                                continue
                            }

                            // Active / pending legacy registration: Reuse legacy document ID and preserve review/claim metadata
                            PendingTenantRegistrationEntity(
                                cloudId = legacyCloudId,
                                ownerId = if (matchedRemote.ownerId.isNotBlank()) matchedRemote.ownerId else currentOwnerId,
                                propertyId = if (matchedRemote.propertyId.isNotBlank()) matchedRemote.propertyId else propertyId,
                                formId = formEntity.formId,
                                responseId = respId,
                                submittedAt = if (matchedRemote.submittedAt > 0L) matchedRemote.submittedAt else (if (submittedAt > 0L) submittedAt else now),
                                formVersion = if (matchedRemote.formVersion > 0) matchedRemote.formVersion else formEntity.formVersion,
                                fullName = if (fullName.isNotBlank()) fullName else matchedRemote.fullName,
                                phone = if (phone.isNotBlank()) phone else matchedRemote.phone,
                                email = if (email.isNotBlank()) email else matchedRemote.email,
                                dob = if (dob.isNotBlank()) dob else matchedRemote.dob,
                                gender = if (gender.isNotBlank()) gender else matchedRemote.gender.ifBlank { "Male" },
                                emergencyName = if (emergencyName.isNotBlank()) emergencyName else matchedRemote.emergencyName,
                                emergencyPhone = if (emergencyPhone.isNotBlank()) emergencyPhone else matchedRemote.emergencyPhone,
                                emergencyRelation = if (emergencyRelation.isNotBlank()) emergencyRelation else matchedRemote.emergencyRelation.ifBlank { "Parent" },
                                permanentAddress = if (permanentAddress.isNotBlank()) permanentAddress else matchedRemote.permanentAddress,
                                currentAddress = if (currentAddress.isNotBlank()) currentAddress else matchedRemote.currentAddress,
                                occupation = if (occupation.isNotBlank()) occupation else matchedRemote.occupation,
                                occupationOther = if (occupationOther.isNotBlank()) occupationOther else matchedRemote.occupationOther,
                                organization = if (organization.isNotBlank()) organization else matchedRemote.organization,
                                officeName = if (officeName.isNotBlank()) officeName else matchedRemote.officeName,
                                officeAddress = if (officeAddress.isNotBlank()) officeAddress else matchedRemote.officeAddress,
                                officePhone = if (officePhone.isNotBlank()) officePhone else matchedRemote.officePhone,
                                vehicleNumber = if (vehicleNumber.isNotBlank()) vehicleNumber else matchedRemote.vehicleNumber,
                                foodPreference = if (foodPreference.isNotBlank()) foodPreference else matchedRemote.foodPreference.ifBlank { "Veg" },
                                expectedJoiningDate = if (expectedJoiningDate.isNotBlank()) expectedJoiningDate else matchedRemote.expectedJoiningDate,
                                expectedVacateDate = if (expectedVacateDate.isNotBlank()) expectedVacateDate else matchedRemote.expectedVacateDate,
                                refundableDeposit = if (refundableDeposit.isNotBlank()) refundableDeposit else matchedRemote.refundableDeposit,
                                fatherName = if (fatherName.isNotBlank()) fatherName else matchedRemote.fatherName,
                                fatherPhone = if (fatherPhone.isNotBlank()) fatherPhone else matchedRemote.fatherPhone,
                                guardianName = if (guardianName.isNotBlank()) guardianName else matchedRemote.guardianName,
                                guardianRelationship = if (guardianRelationship.isNotBlank()) guardianRelationship else matchedRemote.guardianRelationship.ifBlank { "Parent" },
                                guardianPhone = if (guardianPhone.isNotBlank()) guardianPhone else matchedRemote.guardianPhone,
                                localRefName = if (localRefName.isNotBlank()) localRefName else matchedRemote.localRefName,
                                localRefPhone = if (localRefPhone.isNotBlank()) localRefPhone else matchedRemote.localRefPhone,
                                notes = if (notes.isNotBlank()) notes else matchedRemote.notes,
                                status = if (matchedRemote.status.isNotBlank()) matchedRemote.status else status,
                                createdTenantCloudId = matchedRemote.createdTenantCloudId,
                                reviewedAt = matchedRemote.reviewedAt,
                                reviewedBy = matchedRemote.reviewedBy,
                                rejectionReason = matchedRemote.rejectionReason,
                                claimedByDeviceId = matchedRemote.claimedByDeviceId,
                                claimedAt = matchedRemote.claimedAt,
                                duplicateMatchedTenantName = if (duplicateName.isNotBlank()) duplicateName else matchedRemote.duplicateMatchedTenantName,
                                duplicateMatchedTenantRoom = if (duplicateRoom.isNotBlank()) duplicateRoom else matchedRemote.duplicateMatchedTenantRoom,
                                duplicateMatchedTenantStatus = if (duplicateStatus.isNotBlank()) duplicateStatus else matchedRemote.duplicateMatchedTenantStatus,
                                createdAt = if (matchedRemote.createdAt > 0L) matchedRemote.createdAt else now,
                                updatedAt = now,
                                deleted = matchedRemote.deleted,
                                syncStatus = "SYNCED",
                                lastSyncedAt = now,
                                lastModifiedByDeviceId = matchedRemote.lastModifiedByDeviceId
                            )
                        } else {
                            // Requirement 6: Confirmed absence. Use deterministic cloudId.
                            newImportedCount++
                            PendingTenantRegistrationEntity(
                                cloudId = generateRegistrationCloudId(formEntity.formId, respId),
                                ownerId = currentOwnerId,
                                propertyId = propertyId,
                                formId = formEntity.formId,
                                responseId = respId,
                                submittedAt = if (submittedAt > 0L) submittedAt else now,
                                formVersion = formEntity.formVersion,
                                fullName = fullName,
                                phone = phone,
                                email = email,
                                dob = dob,
                                gender = gender.ifBlank { "Male" },
                                emergencyName = emergencyName,
                                emergencyPhone = emergencyPhone,
                                emergencyRelation = emergencyRelation.ifBlank { "Parent" },
                                permanentAddress = permanentAddress,
                                currentAddress = currentAddress,
                                occupation = occupation,
                                occupationOther = occupationOther,
                                organization = organization,
                                officeName = officeName,
                                officeAddress = officeAddress,
                                officePhone = officePhone,
                                vehicleNumber = vehicleNumber,
                                foodPreference = foodPreference.ifBlank { "Veg" },
                                expectedJoiningDate = expectedJoiningDate,
                                expectedVacateDate = expectedVacateDate,
                                refundableDeposit = refundableDeposit,
                                fatherName = fatherName,
                                fatherPhone = fatherPhone,
                                guardianName = guardianName,
                                guardianRelationship = guardianRelationship.ifBlank { "Parent" },
                                guardianPhone = guardianPhone,
                                localRefName = localRefName,
                                localRefPhone = localRefPhone,
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
            val reviewer = currentOwnerEmail.ifBlank { "Owner" }
            val effectiveReason = reason.ifBlank { "Rejected by owner" }

            // 1. Authoritative conditional rejection in Firestore
            val remoteResult = firestorePendingRepo.rejectRegistration(
                cloudId = cloudId,
                deviceId = myDeviceId,
                reviewedBy = reviewer,
                reason = effectiveReason
            )
            if (remoteResult is PgResult.Failure) {
                return@withContext remoteResult
            }

            // 2. Reflect final state in local Room
            val now = System.currentTimeMillis()
            pendingDao.updateReviewStatus(
                cloudId = cloudId,
                status = "REJECTED",
                reviewedAt = now,
                reviewedBy = reviewer,
                rejectionReason = effectiveReason,
                tenantCloudId = "",
                updatedAt = now
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
            val cleanTarget = cloudId.removePrefix("reg_")
            val regEntity = pendingDao.getRegistrationByCloudId(cloudId)
                ?: pendingDao.getRegistrationByCloudId(cleanTarget)
                ?: pendingDao.getRegistrationByCloudId("reg_$cleanTarget")
                ?: pendingDao.getRegistrationByResponseIdOnly(cloudId)
                ?: pendingDao.getRegistrationByResponseIdOnly(cleanTarget)
                ?: run {
                    val propertyId = formDao.getAllForms().firstOrNull()?.propertyId ?: ""
                    val allLocal = pendingDao.getAllForProperty(propertyId)
                    allLocal.find { 
                        it.cloudId == cloudId || 
                        it.responseId == cloudId || 
                        it.cloudId.removePrefix("reg_") == cleanTarget || 
                        it.responseId.removePrefix("reg_") == cleanTarget 
                    }
                }
                ?: return@withContext PgResult.Failure(PgError.ValidationError("Pending registration not found."))

            val effectiveCloudId = regEntity.cloudId.ifBlank { cloudId }

            // Step 1: Check existing local tenant or create
            val stableTenantCloudId = if (regEntity.createdTenantCloudId.isNotBlank()) {
                regEntity.createdTenantCloudId
            } else {
                "tenant_${effectiveCloudId.removePrefix("reg_")}"
            }

            val allActiveTenants = tenantDao.getAllTenants().filter { !it.deleted }
            val cleanPhone = regEntity.phone.filter { it.isDigit() }

            val existingTenantByStableId = tenantDao.getTenantByCloudId(stableTenantCloudId)?.takeIf { !it.deleted }
            val existingTenantByCreatedId = if (regEntity.createdTenantCloudId.isNotBlank()) {
                tenantDao.getTenantByCloudId(regEntity.createdTenantCloudId)?.takeIf { !it.deleted }
            } else null

            val existingTenantInBed = if (roomNumber.isNotBlank() && bedId.isNotBlank()) {
                allActiveTenants.firstOrNull {
                    it.roomNumber.trim().equals(roomNumber.trim(), ignoreCase = true) &&
                    com.example.core.util.BedIdentityUtil.matchesSameBed(it.bedId, bedId.trim())
                }
            } else null

            val existingTenantByPhone = if (cleanPhone.length >= 7) {
                val suffix = cleanPhone.takeLast(minOf(10, cleanPhone.length))
                allActiveTenants.firstOrNull {
                    val tClean = it.phone.filter { p -> p.isDigit() }
                    tClean.endsWith(suffix) || suffix.endsWith(tClean.takeLast(minOf(10, tClean.length)))
                }
            } else null

            val existingTenantByName = if (regEntity.fullName.isNotBlank()) {
                allActiveTenants.firstOrNull { it.name.trim().equals(regEntity.fullName.trim(), ignoreCase = true) }
            } else null

            val existingTenant = existingTenantByCreatedId
                ?: existingTenantByStableId
                ?: existingTenantInBed
                ?: existingTenantByPhone
                ?: existingTenantByName

            val tenantToReturn: TenantEntity

            if (existingTenant != null && !existingTenant.deleted) {
                tenantToReturn = existingTenant
            } else {
                val combinedNotes = buildString {
                    if (notes.isNotBlank()) append(notes)
                    if (regEntity.notes.isNotBlank()) {
                        if (isNotEmpty()) append(" | ")
                        append("Google Form Notes: ${regEntity.notes}")
                    }
                }

                val addResult = addTenantUseCase.execute(
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
                    dob = regEntity.dob,
                    gender = if (gender.isNotBlank()) gender else regEntity.gender.ifBlank { "Male" },
                    address = regEntity.permanentAddress.ifBlank { regEntity.currentAddress },
                    occupation = regEntity.occupation,
                    companyOrCollege = regEntity.organization,
                    leavingDate = "",
                    notes = combinedNotes,
                    targetCloudId = stableTenantCloudId
                )

                when (addResult) {
                    is TenantValidationResult.Success -> {
                        val created = tenantDao.getTenantByCloudId(stableTenantCloudId)
                            ?: run {
                                val all = tenantDao.getAllTenants()
                                val cp = regEntity.phone.filter { it.isDigit() }
                                all.find { !it.deleted && it.roomNumber == roomNumber.trim() && it.phone.filter { p -> p.isDigit() } == cp }
                                    ?: all.lastOrNull()
                            }
                        if (created == null) {
                            return@withContext PgResult.Failure(PgError.DatabaseError("Tenant was created but could not be retrieved."))
                        }
                        tenantToReturn = created
                    }
                    is TenantValidationResult.Error -> {
                        return@withContext PgResult.Failure(PgError.ValidationError(addResult.message))
                    }
                }
            }

            // Step 2: GUARANTEED LOCAL ACCEPTANCE IN ROOM (Offline-First)
            val reviewer = currentOwnerEmail.ifBlank { "Owner" }
            val finalNow = System.currentTimeMillis()
            val tenantCloudIdToLink = tenantToReturn.cloudId.ifBlank { stableTenantCloudId }

            pendingDao.updateReviewStatus(
                cloudId = effectiveCloudId,
                status = "ACCEPTED",
                reviewedAt = finalNow,
                reviewedBy = reviewer,
                rejectionReason = "",
                tenantCloudId = tenantCloudIdToLink,
                updatedAt = finalNow
            )

            if (regEntity.responseId.isNotBlank() && regEntity.responseId != effectiveCloudId) {
                pendingDao.updateReviewStatus(
                    cloudId = regEntity.responseId,
                    status = "ACCEPTED",
                    reviewedAt = finalNow,
                    reviewedBy = reviewer,
                    rejectionReason = "",
                    tenantCloudId = tenantCloudIdToLink,
                    updatedAt = finalNow
                )
            }

            // Step 3: Best-effort Firestore Remote Sync (Non-blocking / Background)
            try {
                firestorePendingRepo.claimForAcceptance(
                    cloudId = effectiveCloudId,
                    claimantDeviceId = myDeviceId
                )
                firestorePendingRepo.finalizeAcceptance(
                    cloudId = effectiveCloudId,
                    claimantDeviceId = myDeviceId,
                    createdTenantCloudId = tenantCloudIdToLink,
                    reviewedBy = reviewer
                )
            } catch (e: Exception) {
                android.util.Log.w("PendingTenantRepo", "Remote firestore acceptance sync deferred: ${e.message}")
            }

            PgResult.Success(tenantToReturn)
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

    companion object {
        fun generateRegistrationCloudId(formId: String, responseId: String): String {
            val key = "${formId.trim()}_${responseId.trim()}"
            val uuid = UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8))
            return "reg_${uuid}"
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
        dob = dob,
        gender = gender,
        emergencyName = emergencyName,
        emergencyPhone = emergencyPhone,
        emergencyRelation = emergencyRelation,
        permanentAddress = permanentAddress,
        currentAddress = currentAddress,
        occupation = occupation,
        organization = organization,
        expectedJoiningDate = expectedJoiningDate,
        notes = notes,
        foodPreference = foodPreference,
        expectedVacateDate = expectedVacateDate,
        refundableDeposit = refundableDeposit,
        fatherName = fatherName,
        fatherPhone = fatherPhone,
        guardianName = guardianName,
        guardianRelationship = guardianRelationship,
        guardianPhone = guardianPhone,
        localRefName = localRefName,
        localRefPhone = localRefPhone,
        occupationOther = occupationOther,
        officeName = officeName,
        officeAddress = officeAddress,
        officePhone = officePhone,
        vehicleNumber = vehicleNumber,
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
        dob = dob,
        gender = gender,
        emergencyName = emergencyName,
        emergencyPhone = emergencyPhone,
        emergencyRelation = emergencyRelation,
        permanentAddress = permanentAddress,
        currentAddress = currentAddress,
        occupation = occupation,
        organization = organization,
        expectedJoiningDate = expectedJoiningDate,
        notes = notes,
        foodPreference = foodPreference,
        expectedVacateDate = expectedVacateDate,
        refundableDeposit = refundableDeposit,
        fatherName = fatherName,
        fatherPhone = fatherPhone,
        guardianName = guardianName,
        guardianRelationship = guardianRelationship,
        guardianPhone = guardianPhone,
        localRefName = localRefName,
        localRefPhone = localRefPhone,
        occupationOther = occupationOther,
        officeName = officeName,
        officeAddress = officeAddress,
        officePhone = officePhone,
        vehicleNumber = vehicleNumber,
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
        dob = dob,
        gender = gender,
        emergencyName = emergencyName,
        emergencyPhone = emergencyPhone,
        emergencyRelation = emergencyRelation,
        permanentAddress = permanentAddress,
        currentAddress = currentAddress,
        occupation = occupation,
        organization = organization,
        expectedJoiningDate = expectedJoiningDate,
        notes = notes,
        foodPreference = foodPreference,
        expectedVacateDate = expectedVacateDate,
        refundableDeposit = refundableDeposit,
        fatherName = fatherName,
        fatherPhone = fatherPhone,
        guardianName = guardianName,
        guardianRelationship = guardianRelationship,
        guardianPhone = guardianPhone,
        localRefName = localRefName,
        localRefPhone = localRefPhone,
        occupationOther = occupationOther,
        officeName = officeName,
        officeAddress = officeAddress,
        officePhone = officePhone,
        vehicleNumber = vehicleNumber,
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

fun PendingTenantRegistrationDto.toEntity(): PendingTenantRegistrationEntity {
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
        dob = dob,
        gender = gender,
        emergencyName = emergencyName,
        emergencyPhone = emergencyPhone,
        emergencyRelation = emergencyRelation,
        permanentAddress = permanentAddress,
        currentAddress = currentAddress,
        occupation = occupation,
        organization = organization,
        expectedJoiningDate = expectedJoiningDate,
        notes = notes,
        foodPreference = foodPreference,
        expectedVacateDate = expectedVacateDate,
        refundableDeposit = refundableDeposit,
        fatherName = fatherName,
        fatherPhone = fatherPhone,
        guardianName = guardianName,
        guardianRelationship = guardianRelationship,
        guardianPhone = guardianPhone,
        localRefName = localRefName,
        localRefPhone = localRefPhone,
        occupationOther = occupationOther,
        officeName = officeName,
        officeAddress = officeAddress,
        officePhone = officePhone,
        vehicleNumber = vehicleNumber,
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

private fun extractAnswerValue(answerObj: Any?): String {
    if (answerObj == null) return ""
    if (answerObj is String) return answerObj.trim()
    if (answerObj is Number || answerObj is Boolean) return answerObj.toString()
    if (answerObj is List<*>) {
        return answerObj.mapNotNull { extractAnswerValue(it) }.filter { it.isNotBlank() }.joinToString(", ")
    }
    if (answerObj is Map<*, *>) {
        val textAnswers = answerObj["textAnswers"] as? Map<*, *>
        val tVal = extractFromAnswersList(textAnswers)
        if (tVal.isNotBlank()) return tVal

        val choiceAnswers = answerObj["choiceAnswers"] as? Map<*, *>
        val cVal = extractFromAnswersList(choiceAnswers)
        if (cVal.isNotBlank()) return cVal

        val directAnswers = answerObj["answers"] as? List<*>
        if (directAnswers != null) {
            val dVal = extractFromAnswersList(mapOf("answers" to directAnswers))
            if (dVal.isNotBlank()) return dVal
        }

        val fileUploadAnswers = answerObj["fileUploadAnswers"] as? Map<*, *>
        if (fileUploadAnswers != null) {
            val fList = fileUploadAnswers["answers"] as? List<*>
            val fileNames = fList?.mapNotNull { (it as? Map<*, *>)?.get("fileName")?.toString()?.trim() }?.filter { it.isNotBlank() }
            if (!fileNames.isNullOrEmpty()) return fileNames.joinToString(", ")
        }

        val dateAnswers = answerObj["dateAnswers"] as? Map<*, *>
        val dateVal = extractFromAnswersList(dateAnswers)
        if (dateVal.isNotBlank()) return dateVal

        val directVal = answerObj["value"] ?: answerObj["text"] ?: answerObj["response"]
        if (directVal != null) {
            return directVal.toString().trim()
        }

        // Generic fallback: check any nested answer maps
        for ((_, v) in answerObj) {
            val extracted = extractAnswerValue(v)
            if (extracted.isNotBlank()) return extracted
        }
    }
    return ""
}

private fun extractFromAnswersList(container: Map<*, *>?): String {
    if (container == null) return ""
    val answersList = container["answers"] as? List<*> ?: return ""
    val values = answersList.mapNotNull { (it as? Map<*, *>)?.get("value")?.toString()?.trim() }.filter { it.isNotBlank() }
    return values.joinToString(", ")
}


