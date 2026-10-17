package com.example.data.firestore.repository

import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.firestore.model.PaymentDto
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
class FirestorePaymentRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val collection = firestore.collection("payments")

    private val currentOwnerId: String
        get() = auth.currentUser?.uid ?: "default_owner"

    suspend fun savePayment(paymentDto: PaymentDto): PgResult<Unit> {
        return try {
            val docId = when {
                paymentDto.cloudId.isNotBlank() -> paymentDto.cloudId
                paymentDto.id.isNotBlank() -> paymentDto.id
                paymentDto.localId > 0 -> "payment_${paymentDto.localId}"
                else -> "payment_${paymentDto.tenantId}_${paymentDto.billingMonth.hashCode().toUInt()}"
            }
            val now = if (paymentDto.updatedAt > 0) paymentDto.updatedAt else System.currentTimeMillis()
            val dtoToSave = paymentDto.copy(
                id = docId,
                cloudId = docId,
                ownerId = if (paymentDto.ownerId.isNotBlank()) paymentDto.ownerId else currentOwnerId,
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
            PgResult.Failure(PgError.UnknownError("Failed to save payment to Firestore", e))
        }
    }

    suspend fun getPayment(id: String): PgResult<PaymentDto?> {
        return try {
            val snapshot = collection.document(id).get().await()
            if (snapshot.exists()) {
                val dto = snapshot.toObject(PaymentDto::class.java)
                PgResult.Success(dto)
            } else {
                PgResult.Success(null)
            }
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to get payment from Firestore", e))
        }
    }

    suspend fun getAllPayments(ownerId: String = currentOwnerId, includeDeleted: Boolean = true): PgResult<List<PaymentDto>> {
        return try {
            val query = if (includeDeleted) {
                collection.whereEqualTo("ownerId", ownerId)
            } else {
                collection.whereEqualTo("ownerId", ownerId).whereEqualTo("deleted", false)
            }
            val snapshot = query.get().await()
            val list = snapshot.toObjects(PaymentDto::class.java)
            PgResult.Success(list)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to fetch payments from Firestore", e))
        }
    }

    fun observePayments(ownerId: String = currentOwnerId, includeDeleted: Boolean = true): Flow<PgResult<List<PaymentDto>>> = callbackFlow {
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
                val list = snapshot.toObjects(PaymentDto::class.java)
                trySend(PgResult.Success(list))
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun deletePayment(id: String, deviceId: String = ""): PgResult<Unit> {
        return try {
            val docRef = collection.document(id)
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
            PgResult.Failure(PgError.UnknownError("Failed to delete payment in Firestore", e))
        }
    }
}
