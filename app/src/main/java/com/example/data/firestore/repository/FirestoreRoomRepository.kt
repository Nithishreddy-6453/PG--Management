package com.example.data.firestore.repository

import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.firestore.model.RoomDto
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
class FirestoreRoomRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val collection = firestore.collection("rooms")

    private val currentOwnerId: String
        get() = auth.currentUser?.uid ?: "default_owner"

    suspend fun saveRoom(roomDto: RoomDto): PgResult<Unit> {
        return try {
            val docId = if (roomDto.id.isNotBlank()) roomDto.id else roomDto.roomNumber
            val dtoToSave = roomDto.copy(
                id = docId,
                ownerId = if (roomDto.ownerId.isNotBlank()) roomDto.ownerId else currentOwnerId,
                updatedAt = System.currentTimeMillis()
            )
            collection.document(docId).set(dtoToSave).await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to save room to Firestore", e))
        }
    }

    suspend fun getRoom(id: String): PgResult<RoomDto?> {
        return try {
            val snapshot = collection.document(id).get().await()
            if (snapshot.exists()) {
                val dto = snapshot.toObject(RoomDto::class.java)
                PgResult.Success(dto)
            } else {
                PgResult.Success(null)
            }
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to get room from Firestore", e))
        }
    }

    suspend fun getAllRooms(ownerId: String = currentOwnerId, includeDeleted: Boolean = true): PgResult<List<RoomDto>> {
        return try {
            val query = if (includeDeleted) {
                collection.whereEqualTo("ownerId", ownerId)
            } else {
                collection.whereEqualTo("ownerId", ownerId).whereEqualTo("deleted", false)
            }
            val snapshot = query.get().await()
            val list = snapshot.toObjects(RoomDto::class.java)
            PgResult.Success(list)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to fetch rooms from Firestore", e))
        }
    }

    fun observeRooms(ownerId: String = currentOwnerId, includeDeleted: Boolean = true): Flow<PgResult<List<RoomDto>>> = callbackFlow {
        val query = if (includeDeleted) {
            collection.whereEqualTo("ownerId", ownerId)
        } else {
            collection.whereEqualTo("ownerId", ownerId).whereEqualTo("deleted", false)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(PgResult.Failure(mapFirestoreException(error)))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.toObjects(RoomDto::class.java)
                trySend(PgResult.Success(list))
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun deleteRoom(id: String, deviceId: String = ""): PgResult<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "deleted" to true,
                "updatedAt" to System.currentTimeMillis()
            )
            if (deviceId.isNotBlank()) {
                updates["lastModifiedByDeviceId"] = deviceId
            }
            collection.document(id).update(updates).await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to delete room in Firestore", e))
        }
    }
}
