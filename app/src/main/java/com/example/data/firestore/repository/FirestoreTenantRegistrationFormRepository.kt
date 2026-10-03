package com.example.data.firestore.repository

import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.firestore.model.TenantRegistrationFormDto
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
class FirestoreTenantRegistrationFormRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val collection = firestore.collection("tenant_registration_forms")

    private val currentOwnerId: String
        get() = auth.currentUser?.uid ?: "default_owner"

    suspend fun saveForm(formDto: TenantRegistrationFormDto): PgResult<Unit> {
        return try {
            val docId = if (formDto.propertyId.isNotBlank()) formDto.propertyId else "form_${formDto.cloudId}"
            val dtoToSave = formDto.copy(
                propertyId = docId,
                ownerId = if (formDto.ownerId.isNotBlank()) formDto.ownerId else currentOwnerId,
                updatedAt = System.currentTimeMillis()
            )
            collection.document(docId).set(dtoToSave).await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to save registration form to Firestore", e))
        }
    }

    suspend fun getFormForProperty(propertyId: String): PgResult<TenantRegistrationFormDto?> {
        return try {
            val snapshot = collection.document(propertyId).get().await()
            if (snapshot.exists()) {
                val dto = snapshot.toObject(TenantRegistrationFormDto::class.java)
                PgResult.Success(dto)
            } else {
                PgResult.Success(null)
            }
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to fetch registration form from Firestore", e))
        }
    }

    fun observeFormForProperty(propertyId: String): Flow<PgResult<TenantRegistrationFormDto?>> = callbackFlow {
        val listener = collection.document(propertyId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(PgResult.Failure(mapFirestoreException(error)))
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val dto = snapshot.toObject(TenantRegistrationFormDto::class.java)
                trySend(PgResult.Success(dto))
            } else {
                trySend(PgResult.Success(null))
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun deleteForm(propertyId: String, deviceId: String = ""): PgResult<Unit> {
        return try {
            collection.document(propertyId).update(
                mapOf(
                    "deleted" to true,
                    "active" to false,
                    "updatedAt" to System.currentTimeMillis(),
                    "lastModifiedByDeviceId" to deviceId
                )
            ).await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to delete form in Firestore", e))
        }
    }
}
