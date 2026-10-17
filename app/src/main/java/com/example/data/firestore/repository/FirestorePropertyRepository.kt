package com.example.data.firestore.repository

import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.firestore.model.PropertyDto
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
class FirestorePropertyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val collection = firestore.collection("properties")

    private val currentOwnerId: String
        get() = auth.currentUser?.uid ?: "default_owner"

    suspend fun saveProperty(propertyDto: PropertyDto): PgResult<Unit> {
        return try {
            val docId = if (propertyDto.id.isNotBlank()) propertyDto.id else "prop_${currentOwnerId}"
            val now = if (propertyDto.updatedAt > 0) propertyDto.updatedAt else System.currentTimeMillis()
            val dtoToSave = propertyDto.copy(
                id = docId,
                ownerId = if (propertyDto.ownerId.isNotBlank()) propertyDto.ownerId else currentOwnerId,
                updatedAt = now
            )
            val docRef = collection.document(docId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val remoteVersion = snapshot.getLong("version")?.toInt() ?: 1
                    val remoteUpdatedAt = snapshot.getLong("updatedAt") ?: 0L
                    val remoteDeleted = snapshot.getBoolean("deleted") ?: false
                    val remoteDeviceId = snapshot.getString("lastModifiedByDeviceId") ?: ""

                    // Idempotency: Reject stale write that would resurrect a deleted record
                    if (remoteDeleted && !dtoToSave.deleted) {
                        return@runTransaction
                    }
                    // Idempotency: Reject stale write if remote document is strictly newer
                    if (remoteVersion > dtoToSave.version) {
                        return@runTransaction
                    }
                    // Idempotency: Reject stale write if remote has equal version but newer timestamp from another device
                    if (remoteVersion == dtoToSave.version && remoteUpdatedAt > dtoToSave.updatedAt && remoteDeviceId.isNotBlank() && remoteDeviceId != dtoToSave.lastModifiedByDeviceId) {
                        return@runTransaction
                    }
                }
                transaction.set(docRef, dtoToSave)
            }.await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to save property to Firestore", e))
        }
    }

    suspend fun deleteProperty(propertyId: String, deviceId: String = ""): PgResult<Unit> {
        return try {
            val docRef = collection.document(propertyId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val currentVersion = snapshot.getLong("version")?.toInt() ?: 1
                    val updates = mutableMapOf<String, Any>(
                        "deleted" to true,
                        "version" to currentVersion + 1,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    if (deviceId.isNotBlank()) {
                        updates["lastModifiedByDeviceId"] = deviceId
                    }
                    transaction.update(docRef, updates)
                }
            }.await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                PgResult.Success(Unit)
            } else {
                PgResult.Failure(mapFirestoreException(e))
            }
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to delete property in Firestore", e))
        }
    }

    suspend fun getProperty(id: String): PgResult<PropertyDto?> {
        return try {
            val snapshot = collection.document(id).get().await()
            if (snapshot.exists()) {
                val dto = snapshot.toObject(PropertyDto::class.java)
                PgResult.Success(dto)
            } else {
                PgResult.Success(null)
            }
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to get property from Firestore", e))
        }
    }

    suspend fun getAllProperties(ownerId: String = currentOwnerId, includeDeleted: Boolean = false): PgResult<List<PropertyDto>> {
        return try {
            var query = collection.whereEqualTo("ownerId", ownerId)
            if (!includeDeleted) {
                query = query.whereEqualTo("deleted", false)
            }
            val snapshot = query.get().await()
            val list = snapshot.toObjects(PropertyDto::class.java)
            PgResult.Success(list)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to fetch properties from Firestore", e))
        }
    }

    fun observeProperties(ownerId: String = currentOwnerId, includeDeleted: Boolean = false): Flow<PgResult<List<PropertyDto>>> = callbackFlow {
        var query = collection.whereEqualTo("ownerId", ownerId)
        if (!includeDeleted) {
            query = query.whereEqualTo("deleted", false)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(PgResult.Failure(mapFirestoreException(error)))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.toObjects(PropertyDto::class.java)
                trySend(PgResult.Success(list))
            }
        }
        awaitClose { listener.remove() }
    }
}
