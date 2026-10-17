package com.example.data.firestore.repository

import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.firestore.model.PendingTenantRegistrationDto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestorePendingTenantRegistrationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val collection = firestore.collection("pending_tenant_registrations")

    private val currentOwnerId: String
        get() = auth.currentUser?.uid ?: "default_owner"

    suspend fun saveRegistration(dto: PendingTenantRegistrationDto): PgResult<Unit> {
        val ownerId = if (dto.ownerId.isNotBlank()) dto.ownerId else currentOwnerId
        return try {
            val docId = if (dto.cloudId.isNotBlank()) {
                dto.cloudId
            } else {
                com.example.features.googleform.data.repository.PendingTenantRegistrationRepositoryImpl.generateRegistrationCloudId(
                    dto.formId,
                    dto.responseId
                )
            }
            val dtoToSave = dto.copy(
                cloudId = docId,
                ownerId = ownerId,
                updatedAt = System.currentTimeMillis()
            )
            val docRef = collection.document(docId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val remoteStatus = snapshot.getString("status") ?: ""
                    val remoteDeleted = snapshot.getBoolean("deleted") ?: false
                    val remoteReviewedBy = snapshot.getString("reviewedBy") ?: ""
                    val remoteReviewedAt = snapshot.getLong("reviewedAt") ?: 0L
                    val remoteRejectionReason = snapshot.getString("rejectionReason") ?: ""
                    val remoteTenantCloudId = snapshot.getString("createdTenantCloudId") ?: ""
                    val remoteClaimedDeviceId = snapshot.getString("claimedByDeviceId") ?: ""
                    val remoteClaimedAt = snapshot.getLong("claimedAt") ?: 0L

                    // 1. Never resurrect a deleted record
                    if (remoteDeleted && !dtoToSave.deleted) {
                        return@runTransaction
                    }

                    // 2. Terminal review protection: Never overwrite ACCEPTED or REJECTED status with unreviewed data
                    if ((remoteStatus == "ACCEPTED" || remoteStatus == "REJECTED") &&
                        (dtoToSave.status != remoteStatus)
                    ) {
                        return@runTransaction
                    }

                    // 3. Claim protection: If remote is ACCEPTING and incoming write is not terminal, preserve claim
                    val effectiveStatus = if (remoteStatus == "ACCEPTING" && dtoToSave.status != "ACCEPTED" && dtoToSave.status != "REJECTED") {
                        remoteStatus
                    } else {
                        dtoToSave.status
                    }

                    val effectiveClaimedDeviceId = if (remoteClaimedDeviceId.isNotBlank() && dtoToSave.claimedByDeviceId.isBlank()) {
                        remoteClaimedDeviceId
                    } else {
                        dtoToSave.claimedByDeviceId
                    }

                    val effectiveClaimedAt = if (remoteClaimedAt > 0L && dtoToSave.claimedAt == 0L) {
                        remoteClaimedAt
                    } else {
                        dtoToSave.claimedAt
                    }

                    val effectiveReviewedBy = if (remoteReviewedBy.isNotBlank() && dtoToSave.reviewedBy.isBlank()) {
                        remoteReviewedBy
                    } else {
                        dtoToSave.reviewedBy
                    }

                    val effectiveReviewedAt = if (remoteReviewedAt > 0L && dtoToSave.reviewedAt == 0L) {
                        remoteReviewedAt
                    } else {
                        dtoToSave.reviewedAt
                    }

                    val effectiveRejectionReason = if (remoteRejectionReason.isNotBlank() && dtoToSave.rejectionReason.isBlank()) {
                        remoteRejectionReason
                    } else {
                        dtoToSave.rejectionReason
                    }

                    val effectiveTenantCloudId = if (remoteTenantCloudId.isNotBlank() && dtoToSave.createdTenantCloudId.isBlank()) {
                        remoteTenantCloudId
                    } else {
                        dtoToSave.createdTenantCloudId
                    }

                    val mergedDto = dtoToSave.copy(
                        status = effectiveStatus,
                        claimedByDeviceId = effectiveClaimedDeviceId,
                        claimedAt = effectiveClaimedAt,
                        reviewedBy = effectiveReviewedBy,
                        reviewedAt = effectiveReviewedAt,
                        rejectionReason = effectiveRejectionReason,
                        createdTenantCloudId = effectiveTenantCloudId
                    )
                    transaction.set(docRef, mergedDto)
                } else {
                    transaction.set(docRef, dtoToSave)
                }
            }.await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to save pending registration to Firestore", e))
        }
    }

    suspend fun findExistingRegistrationsForResponse(
        formId: String,
        responseId: String,
        propertyId: String? = null
    ): PgResult<List<PendingTenantRegistrationDto>> {
        val ownerId = auth.currentUser?.uid
        if (ownerId.isNullOrBlank()) {
            return PgResult.Failure(PgError.SecurityError("Cannot query registrations without authenticated owner"))
        }
        return try {
            var query = collection
                .whereEqualTo("ownerId", ownerId)
                .whereEqualTo("formId", formId.trim())
                .whereEqualTo("responseId", responseId.trim())

            if (!propertyId.isNullOrBlank()) {
                query = query.whereEqualTo("propertyId", propertyId.trim())
            }

            val snapshot = query.get().await()
            val list = snapshot.documents.mapNotNull { doc ->
                val dto = doc.toObject(PendingTenantRegistrationDto::class.java) ?: return@mapNotNull null
                val effectiveCloudId = if (dto.cloudId.isNotBlank()) dto.cloudId else doc.id
                dto.copy(cloudId = effectiveCloudId)
            }.filter { !it.deleted }
            PgResult.Success(list)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to query existing registrations from Firestore", e))
        }
    }

    suspend fun getRegistrationsForProperty(propertyId: String): PgResult<List<PendingTenantRegistrationDto>> {
        return try {
            val snapshot = collection
                .whereEqualTo("ownerId", currentOwnerId)
                .whereEqualTo("propertyId", propertyId)
                .whereEqualTo("deleted", false)
                .get()
                .await()
            val list = snapshot.documents.mapNotNull { it.toObject(PendingTenantRegistrationDto::class.java) }
            PgResult.Success(list)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to fetch pending registrations from Firestore", e))
        }
    }

    fun observeRegistrationsForProperty(propertyId: String): Flow<PgResult<List<PendingTenantRegistrationDto>>> = callbackFlow {
        val listener = collection
            .whereEqualTo("ownerId", currentOwnerId)
            .whereEqualTo("propertyId", propertyId)
            .whereEqualTo("deleted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(PgResult.Failure(mapFirestoreException(error)))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(PendingTenantRegistrationDto::class.java) }
                    trySend(PgResult.Success(list))
                } else {
                    trySend(PgResult.Success(emptyList()))
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun getRegistrationByCloudId(cloudId: String): PgResult<PendingTenantRegistrationDto?> {
        return try {
            val snapshot = collection.document(cloudId).get().await()
            if (snapshot.exists()) {
                val dto = snapshot.toObject(PendingTenantRegistrationDto::class.java)
                val effectiveCloudId = if (!dto?.cloudId.isNullOrBlank()) dto!!.cloudId else snapshot.id
                PgResult.Success(dto?.copy(cloudId = effectiveCloudId))
            } else {
                // Also search by responseId
                val querySnapshot = collection
                    .whereEqualTo("responseId", cloudId.trim())
                    .limit(1)
                    .get()
                    .await()
                val doc = querySnapshot.documents.firstOrNull()
                if (doc != null && doc.exists()) {
                    val dto = doc.toObject(PendingTenantRegistrationDto::class.java)
                    val effectiveCloudId = if (!dto?.cloudId.isNullOrBlank()) dto!!.cloudId else doc.id
                    PgResult.Success(dto?.copy(cloudId = effectiveCloudId))
                } else {
                    PgResult.Success(null)
                }
            }
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to fetch pending registration from Firestore", e))
        }
    }

    sealed class ClaimResult {
        data class Claimed(val registration: PendingTenantRegistrationDto) : ClaimResult()
        data class AlreadyAccepted(val createdTenantCloudId: String, val registration: PendingTenantRegistrationDto) : ClaimResult()
        data class AlreadyRejected(val reason: String) : ClaimResult()
        data class Conflict(val message: String) : ClaimResult()
    }

    suspend fun claimForAcceptance(
        cloudId: String,
        claimantDeviceId: String,
        leaseDurationMs: Long = 300_000L
    ): PgResult<ClaimResult> {
        val ownerId = auth.currentUser?.uid
        if (ownerId.isNullOrBlank()) {
            return PgResult.Failure(PgError.SecurityError("Cannot claim registration without authenticated user"))
        }
        val docRef = collection.document(cloudId)
        return try {
            val result = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("REGISTRATION_NOT_FOUND")
                }
                val docOwnerId = snapshot.getString("ownerId") ?: ""
                if (docOwnerId.isNotBlank() && docOwnerId != ownerId) {
                    throw IllegalStateException("PERMISSION_DENIED_OWNER")
                }
                val status = snapshot.getString("status") ?: "PENDING"
                val currentClaimedDeviceId = snapshot.getString("claimedByDeviceId") ?: ""
                val currentClaimedAt = snapshot.getLong("claimedAt") ?: 0L
                val createdTenantCloudId = snapshot.getString("createdTenantCloudId") ?: ""
                val rejectionReason = snapshot.getString("rejectionReason") ?: ""
                val dto = snapshot.toObject(PendingTenantRegistrationDto::class.java)
                    ?: PendingTenantRegistrationDto(cloudId = cloudId)

                if (status == "ACCEPTED") {
                    return@runTransaction ClaimResult.AlreadyAccepted(createdTenantCloudId, dto)
                }
                if (status == "REJECTED") {
                    return@runTransaction ClaimResult.AlreadyRejected(rejectionReason)
                }

                val now = System.currentTimeMillis()
                if (status == "ACCEPTING") {
                    val isMyClaim = currentClaimedDeviceId == claimantDeviceId
                    val isExpired = (now - currentClaimedAt) > leaseDurationMs
                    if (!isMyClaim && !isExpired) {
                        return@runTransaction ClaimResult.Conflict(
                            "Registration is currently being processed by device $currentClaimedDeviceId."
                        )
                    }
                }

                // Status must be PENDING, NEEDS_REVIEW, DUPLICATE, or expired/own ACCEPTING
                if (status !in listOf("PENDING", "NEEDS_REVIEW", "DUPLICATE", "ACCEPTING")) {
                    return@runTransaction ClaimResult.Conflict("Registration in invalid state for acceptance: $status")
                }

                // Atomic transition to ACCEPTING
                transaction.update(
                    docRef,
                    mapOf(
                        "status" to "ACCEPTING",
                        "claimedByDeviceId" to claimantDeviceId,
                        "claimedAt" to now,
                        "updatedAt" to now,
                        "lastModifiedByDeviceId" to claimantDeviceId
                    )
                )

                ClaimResult.Claimed(dto.copy(
                    status = "ACCEPTING",
                    claimedByDeviceId = claimantDeviceId,
                    claimedAt = now
                ))
            }.await()
            PgResult.Success(result)
        } catch (e: IllegalStateException) {
            when (e.message) {
                "REGISTRATION_NOT_FOUND" -> PgResult.Failure(PgError.ValidationError("Pending registration not found in Firestore."))
                "PERMISSION_DENIED_OWNER" -> PgResult.Failure(PgError.SecurityError("Permission denied: Registration belongs to another owner."))
                else -> PgResult.Failure(PgError.UnknownError(e.message ?: "Transaction failed", e))
            }
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to claim registration: ${e.localizedMessage}", e))
        }
    }

    suspend fun finalizeAcceptance(
        cloudId: String,
        claimantDeviceId: String,
        createdTenantCloudId: String,
        reviewedBy: String
    ): PgResult<Unit> {
        val ownerId = auth.currentUser?.uid
        if (ownerId.isNullOrBlank()) {
            return PgResult.Failure(PgError.SecurityError("Cannot finalize acceptance without authenticated user"))
        }
        val docRef = collection.document(cloudId)
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("REGISTRATION_NOT_FOUND")
                }
                val status = snapshot.getString("status") ?: ""
                val currentClaimedDeviceId = snapshot.getString("claimedByDeviceId") ?: ""

                // If already ACCEPTED with the same or valid tenant cloud ID, idempotency succeeds
                if (status == "ACCEPTED") {
                    return@runTransaction
                }
                if (status == "REJECTED") {
                    throw IllegalStateException("CANNOT_ACCEPT_REJECTED")
                }
                if (status != "ACCEPTING") {
                    throw IllegalStateException("INVALID_STATUS_FOR_FINALIZE: $status")
                }
                if (currentClaimedDeviceId.isNotBlank() && currentClaimedDeviceId != claimantDeviceId) {
                    throw IllegalStateException("CLAIMANT_MISMATCH")
                }

                val now = System.currentTimeMillis()
                transaction.update(
                    docRef,
                    mapOf(
                        "status" to "ACCEPTED",
                        "createdTenantCloudId" to createdTenantCloudId,
                        "reviewedBy" to reviewedBy,
                        "reviewedAt" to now,
                        "rejectionReason" to "",
                        "claimedByDeviceId" to "",
                        "claimedAt" to 0L,
                        "updatedAt" to now,
                        "lastModifiedByDeviceId" to claimantDeviceId
                    )
                )
            }.await()
            PgResult.Success(Unit)
        } catch (e: IllegalStateException) {
            when (e.message) {
                "REGISTRATION_NOT_FOUND" -> PgResult.Failure(PgError.ValidationError("Registration not found"))
                "CANNOT_ACCEPT_REJECTED" -> PgResult.Failure(PgError.ValidationError("Cannot accept a registration that was already rejected."))
                "CLAIMANT_MISMATCH" -> PgResult.Failure(PgError.ValidationError("Another device holds the acceptance claim."))
                else -> PgResult.Failure(PgError.ValidationError(e.message ?: "Failed to finalize acceptance"))
            }
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to finalize acceptance: ${e.localizedMessage}", e))
        }
    }

    suspend fun rejectRegistration(
        cloudId: String,
        deviceId: String,
        reviewedBy: String,
        reason: String,
        leaseDurationMs: Long = 300_000L
    ): PgResult<Unit> {
        val ownerId = auth.currentUser?.uid
        if (ownerId.isNullOrBlank()) {
            return PgResult.Failure(PgError.SecurityError("Cannot reject registration without authenticated user"))
        }
        val docRef = collection.document(cloudId)
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("REGISTRATION_NOT_FOUND")
                }
                val status = snapshot.getString("status") ?: ""
                val currentClaimedDeviceId = snapshot.getString("claimedByDeviceId") ?: ""
                val currentClaimedAt = snapshot.getLong("claimedAt") ?: 0L

                if (status == "REJECTED") {
                    return@runTransaction // Idempotent
                }
                if (status == "ACCEPTED") {
                    throw IllegalStateException("CANNOT_REJECT_ACCEPTED")
                }
                if (status == "ACCEPTING") {
                    val now = System.currentTimeMillis()
                    val isExpired = (now - currentClaimedAt) > leaseDurationMs
                    if (currentClaimedDeviceId != deviceId && !isExpired) {
                        throw IllegalStateException("CLAIM_IN_PROGRESS_BY_ANOTHER_DEVICE")
                    }
                }

                val now = System.currentTimeMillis()
                transaction.update(
                    docRef,
                    mapOf(
                        "status" to "REJECTED",
                        "rejectionReason" to reason,
                        "reviewedBy" to reviewedBy,
                        "reviewedAt" to now,
                        "createdTenantCloudId" to "",
                        "claimedByDeviceId" to "",
                        "claimedAt" to 0L,
                        "updatedAt" to now,
                        "lastModifiedByDeviceId" to deviceId
                    )
                )
            }.await()
            PgResult.Success(Unit)
        } catch (e: IllegalStateException) {
            when (e.message) {
                "REGISTRATION_NOT_FOUND" -> PgResult.Failure(PgError.ValidationError("Registration not found"))
                "CANNOT_REJECT_ACCEPTED" -> PgResult.Failure(PgError.ValidationError("Cannot reject a registration that has already been accepted."))
                "CLAIM_IN_PROGRESS_BY_ANOTHER_DEVICE" -> PgResult.Failure(PgError.ValidationError("Registration is currently being processed for acceptance on another device."))
                else -> PgResult.Failure(PgError.ValidationError(e.message ?: "Failed to reject registration"))
            }
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to reject registration: ${e.localizedMessage}", e))
        }
    }

    suspend fun releaseClaim(
        cloudId: String,
        claimantDeviceId: String,
        restoreStatus: String = "PENDING"
    ): PgResult<Unit> {
        val docRef = collection.document(cloudId)
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) return@runTransaction
                val status = snapshot.getString("status") ?: ""
                val currentClaimedDeviceId = snapshot.getString("claimedByDeviceId") ?: ""

                // Only release if in ACCEPTING and claimed by this device
                if (status == "ACCEPTING" && currentClaimedDeviceId == claimantDeviceId) {
                    val now = System.currentTimeMillis()
                    transaction.update(
                        docRef,
                        mapOf(
                            "status" to restoreStatus,
                            "claimedByDeviceId" to "",
                            "claimedAt" to 0L,
                            "updatedAt" to now,
                            "lastModifiedByDeviceId" to claimantDeviceId
                        )
                    )
                }
            }.await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to release claim: ${e.localizedMessage}", e))
        }
    }

    suspend fun updateReviewStatus(
        cloudId: String,
        status: String,
        reviewedBy: String,
        rejectionReason: String,
        createdTenantCloudId: String,
        deviceId: String = ""
    ): PgResult<Unit> {
        return if (status == "REJECTED") {
            rejectRegistration(
                cloudId = cloudId,
                deviceId = deviceId,
                reviewedBy = reviewedBy,
                reason = rejectionReason
            )
        } else if (status == "ACCEPTED") {
            finalizeAcceptance(
                cloudId = cloudId,
                claimantDeviceId = deviceId,
                createdTenantCloudId = createdTenantCloudId,
                reviewedBy = reviewedBy
            )
        } else {
            PgResult.Failure(PgError.ValidationError("Unsupported review status transition: $status"))
        }
    }
}
