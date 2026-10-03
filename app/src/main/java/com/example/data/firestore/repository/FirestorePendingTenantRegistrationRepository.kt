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
        return try {
            val docId = if (dto.cloudId.isNotBlank()) dto.cloudId else "${dto.formId}_${dto.responseId}"
            val dtoToSave = dto.copy(
                cloudId = docId,
                ownerId = if (dto.ownerId.isNotBlank()) dto.ownerId else currentOwnerId,
                updatedAt = System.currentTimeMillis()
            )
            collection.document(docId).set(dtoToSave).await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to save pending registration to Firestore", e))
        }
    }

    suspend fun getRegistrationsForProperty(propertyId: String): PgResult<List<PendingTenantRegistrationDto>> {
        return try {
            val snapshot = collection
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

    suspend fun updateReviewStatus(
        cloudId: String,
        status: String,
        reviewedBy: String,
        rejectionReason: String,
        createdTenantCloudId: String,
        deviceId: String = ""
    ): PgResult<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "reviewedAt" to System.currentTimeMillis(),
                "reviewedBy" to reviewedBy,
                "rejectionReason" to rejectionReason,
                "createdTenantCloudId" to createdTenantCloudId,
                "updatedAt" to System.currentTimeMillis(),
                "lastModifiedByDeviceId" to deviceId
            )
            collection.document(cloudId).update(updates).await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to update registration status in Firestore", e))
        }
    }
}
