package com.example.data.firestore.repository

import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.firestore.model.FormAccessRequestDto
import com.example.data.firestore.model.FormVersionHistoryDto
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
class FirestoreFormAccessRequestRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val requestsCollection = firestore.collection("form_access_requests")
    private val historyCollection = firestore.collection("form_version_history")

    private val currentOwnerId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun saveRequest(dto: FormAccessRequestDto): PgResult<Unit> {
        return try {
            requestsCollection.document(dto.requestId).set(dto).await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to save form access request to Firestore: ${e.localizedMessage}", e))
        }
    }

    suspend fun getRequestsForProperty(propertyId: String): PgResult<List<FormAccessRequestDto>> {
        return try {
            val snapshot = requestsCollection
                .whereEqualTo("propertyId", propertyId)
                .get()
                .await()
            val list = snapshot.documents.mapNotNull { it.toObject(FormAccessRequestDto::class.java) }
            PgResult.Success(list)
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to fetch form access requests from Firestore: ${e.localizedMessage}", e))
        }
    }

    fun observeRequestsForProperty(propertyId: String): Flow<PgResult<List<FormAccessRequestDto>>> = callbackFlow {
        val listener = requestsCollection
            .whereEqualTo("propertyId", propertyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(PgResult.Failure(mapFirestoreException(error)))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(FormAccessRequestDto::class.java) }
                    trySend(PgResult.Success(list))
                } else {
                    trySend(PgResult.Success(emptyList()))
                }
            }
        awaitClose { listener.remove() }
    }

    fun observePendingRequestsForOwner(ownerPgUid: String): Flow<PgResult<List<FormAccessRequestDto>>> = callbackFlow {
        val listener = requestsCollection
            .whereEqualTo("ownerPgUid", ownerPgUid)
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(PgResult.Failure(mapFirestoreException(error)))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(FormAccessRequestDto::class.java) }
                    trySend(PgResult.Success(list))
                } else {
                    trySend(PgResult.Success(emptyList()))
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveHistory(dto: FormVersionHistoryDto): PgResult<Unit> {
        return try {
            historyCollection.document(dto.id).set(dto).await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to save form history to Firestore: ${e.localizedMessage}", e))
        }
    }

    fun observeHistoryForProperty(propertyId: String): Flow<PgResult<List<FormVersionHistoryDto>>> = callbackFlow {
        val listener = historyCollection
            .whereEqualTo("propertyId", propertyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(PgResult.Failure(mapFirestoreException(error)))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(FormVersionHistoryDto::class.java) }
                    trySend(PgResult.Success(list))
                } else {
                    trySend(PgResult.Success(emptyList()))
                }
            }
        awaitClose { listener.remove() }
    }
}
