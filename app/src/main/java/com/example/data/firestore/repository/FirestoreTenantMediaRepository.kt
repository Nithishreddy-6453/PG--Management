package com.example.data.firestore.repository

import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.firestore.model.TenantMediaDto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreTenantMediaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val collection = firestore.collection("tenant_media")

    private val currentOwnerId: String?
        get() = auth.currentUser?.uid

    suspend fun saveMediaMetadata(dto: TenantMediaDto): PgResult<Unit> {
        return try {
            val authUid = currentOwnerId
            if (authUid.isNullOrBlank()) {
                Log.e("TenantMedia", "[saveMediaMetadata] FAILED: FirebaseAuth.currentUser.uid is null or blank")
                return PgResult.Failure(PgError.SecurityError("User is not authenticated"))
            }

            val outgoingOwnerId = dto.ownerId
            if (authUid != outgoingOwnerId) {
                Log.e("TenantMedia", "[saveMediaMetadata] EXPLICIT FAILURE: Owner ID mismatch! authenticatedUid='$authUid' vs outgoingOwnerId='$outgoingOwnerId'")
                return PgResult.Failure(PgError.SecurityError("Owner ID mismatch: authenticated user '$authUid' does not match media ownerId '$outgoingOwnerId'"))
            }

            val docId = if (dto.cloudId.isNotBlank()) dto.cloudId else "${dto.tenantCloudId}_PROFILE_PHOTO"
            val collectionPath = "tenant_media/$docId"
            val now = if (dto.updatedAt > 0) dto.updatedAt else System.currentTimeMillis()

            val toSave = dto.copy(
                cloudId = docId,
                updatedAt = now
            )
            val docRef = collection.document(docId)

            Log.d(
                "TenantMedia",
                "[saveMediaMetadata] PRE-WRITE LOG: authUid='$authUid', outgoingOwnerId='$outgoingOwnerId', path='$collectionPath', operationType='transaction_set', tenantCloudId='${toSave.tenantCloudId}', mediaType='${toSave.mediaType}'"
            )

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val remoteUpdatedAt = snapshot.getLong("updatedAt") ?: 0L
                    if (remoteUpdatedAt > toSave.updatedAt) {
                        Log.d("TenantMedia", "[saveMediaMetadata] SKIPPED write for docId='$docId': remote doc is newer (remoteUpdatedAt=$remoteUpdatedAt > localUpdatedAt=${toSave.updatedAt})")
                        return@runTransaction
                    }
                }
                transaction.set(docRef, toSave)
            }.await()

            Log.d("TenantMedia", "[saveMediaMetadata] SUCCESS: path='$collectionPath', ownerId='$outgoingOwnerId'")
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Log.e("TenantMedia", "[saveMediaMetadata] FIRESTORE EXCEPTION for collection='tenant_media', docId='${dto.cloudId}': code=${e.code}, message=${e.message}", e)
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            Log.e("TenantMedia", "[saveMediaMetadata] UNKNOWN EXCEPTION for collection='tenant_media', docId='${dto.cloudId}': message=${e.message}", e)
            PgResult.Failure(PgError.UnknownError("Failed to save media metadata to Firestore", e))
        }
    }

    suspend fun getProfilePhotoMetadata(tenantCloudId: String): PgResult<TenantMediaDto?> {
        return try {
            val canonicalDocId = "${tenantCloudId}_PROFILE_PHOTO"
            // 1. Direct fetch using canonical document ID (fastest, requires no indexes)
            val doc = collection.document(canonicalDocId).get().await()
            if (doc.exists()) {
                val dto = doc.toObject(TenantMediaDto::class.java)
                if (dto != null && !dto.deleted && dto.status == "ACTIVE" && dto.mediaType == "PROFILE_PHOTO") {
                    return PgResult.Success(dto)
                }
            }

            // 2. Fallback: Search collection for any active profile photo for this tenant
            val snapshot = collection
                .whereEqualTo("tenantCloudId", tenantCloudId)
                .get()
                .await()

            val dto = snapshot.toObjects(TenantMediaDto::class.java)
                .filter { !it.deleted && it.mediaType == "PROFILE_PHOTO" && it.status == "ACTIVE" }
                .maxByOrNull { it.updatedAt }

            PgResult.Success(dto)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to fetch tenant media from Firestore", e))
        }
    }

    suspend fun cleanUpLegacyMediaDocuments(tenantCloudId: String, canonicalDocId: String): PgResult<Unit> {
        return try {
            val snapshot = collection
                .whereEqualTo("tenantCloudId", tenantCloudId)
                .get()
                .await()

            for (doc in snapshot.documents) {
                if (doc.id != canonicalDocId) {
                    val mediaType = doc.getString("mediaType") ?: ""
                    // CRITICAL: Only soft-delete non-canonical PROFILE_PHOTO records, never touch TENANT_DOCUMENT
                    if (mediaType == "PROFILE_PHOTO") {
                        try {
                            collection.document(doc.id).update(
                                mapOf<String, Any>(
                                    "deleted" to true,
                                    "status" to "DELETED",
                                    "updatedAt" to System.currentTimeMillis()
                                )
                            ).await()
                        } catch (_: Exception) {}
                    }
                }
            }
            PgResult.Success(Unit)
        } catch (_: Exception) {
            PgResult.Success(Unit)
        }
    }

    suspend fun getAllTenantMedia(ownerId: String, includeDeleted: Boolean = true): PgResult<List<TenantMediaDto>> {
        return try {
            val effectiveOwnerId = if (ownerId.isNotBlank() && ownerId != "default_owner") ownerId else currentOwnerId ?: ""
            if (effectiveOwnerId.isBlank()) {
                Log.e("TenantMedia", "getAllTenantMedia error: User is not authenticated")
                return PgResult.Failure(PgError.SecurityError("User is not authenticated"))
            }
            Log.d("TenantMedia", "getAllTenantMedia query for ownerId=$effectiveOwnerId")
            val snapshot = collection.whereEqualTo("ownerId", effectiveOwnerId).get().await()
            val list = snapshot.toObjects(TenantMediaDto::class.java)
            val filtered = if (!includeDeleted) list.filter { !it.deleted } else list
            PgResult.Success(filtered)
        } catch (e: FirebaseFirestoreException) {
            Log.e("TenantMedia", "getAllTenantMedia failure: ${e.message}", e)
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            Log.e("TenantMedia", "getAllTenantMedia failure: ${e.message}", e)
            PgResult.Failure(PgError.UnknownError("Failed to fetch tenant media list from Firestore", e))
        }
    }

    fun observeTenantMedia(ownerId: String, includeDeleted: Boolean = true): Flow<PgResult<List<TenantMediaDto>>> = callbackFlow {
        val effectiveOwnerId = if (ownerId.isNotBlank() && ownerId != "default_owner") ownerId else currentOwnerId ?: ""
        if (effectiveOwnerId.isBlank()) {
            Log.e("TenantMedia", "observeTenantMedia error: User is not authenticated")
            trySend(PgResult.Failure(PgError.SecurityError("User is not authenticated")))
            close()
            return@callbackFlow
        }
        Log.d("TenantMedia", "observeTenantMedia subscribing for ownerId=$effectiveOwnerId")
        val listener = collection.whereEqualTo("ownerId", effectiveOwnerId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("TenantMedia", "observeTenantMedia snapshot error: ${error.message}", error)
                trySend(PgResult.Failure(mapFirestoreException(error)))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.toObjects(TenantMediaDto::class.java)
                val filtered = if (!includeDeleted) list.filter { !it.deleted } else list
                Log.d("TenantMedia", "observeTenantMedia received ${filtered.size} records")
                trySend(PgResult.Success(filtered))
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun deleteMediaMetadata(cloudId: String): PgResult<Unit> {
        return try {
            val docRef = collection.document(cloudId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val updates = mapOf<String, Any>(
                        "deleted" to true,
                        "status" to "DELETED",
                        "updatedAt" to System.currentTimeMillis()
                    )
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
            PgResult.Failure(PgError.UnknownError("Failed to delete media metadata from Firestore", e))
        }
    }

    private val folderMappingsCollection = firestore.collection("drive_folder_mappings")

    suspend fun saveFolderMapping(mappingKey: String, folderId: String): PgResult<Unit> {
        return try {
            val authUid = currentOwnerId
            if (authUid.isNullOrBlank()) {
                Log.e("TenantMedia", "[saveFolderMapping] FAILED: FirebaseAuth.currentUser.uid is null or blank")
                return PgResult.Failure(PgError.SecurityError("User is not authenticated"))
            }
            val collectionPath = "drive_folder_mappings/$mappingKey"
            val data = mapOf(
                "folderPathKey" to mappingKey,
                "driveFolderId" to folderId,
                "ownerId" to authUid,
                "updatedAt" to System.currentTimeMillis()
            )
            Log.d(
                "TenantMedia",
                "[saveFolderMapping] PRE-WRITE LOG: authUid='$authUid', outgoingOwnerId='$authUid', path='$collectionPath', operationType='set', folderPathKey='$mappingKey', driveFolderId='$folderId'"
            )
            folderMappingsCollection.document(mappingKey).set(data).await()
            Log.d("TenantMedia", "[saveFolderMapping] SUCCESS: path='$collectionPath', folderId='$folderId', ownerId='$authUid'")
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Log.e("TenantMedia", "[saveFolderMapping] FIRESTORE EXCEPTION for collection='drive_folder_mappings', mappingKey='$mappingKey': code=${e.code}, message=${e.message}", e)
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            Log.e("TenantMedia", "[saveFolderMapping] UNKNOWN EXCEPTION for collection='drive_folder_mappings', mappingKey='$mappingKey': message=${e.message}", e)
            PgResult.Failure(PgError.UnknownError("Failed to save drive folder mapping", e))
        }
    }

    suspend fun getFolderMapping(mappingKey: String): PgResult<String?> {
        return try {
            val doc = folderMappingsCollection.document(mappingKey).get().await()
            if (doc.exists()) {
                val folderId = doc.getString("driveFolderId")
                PgResult.Success(folderId)
            } else {
                PgResult.Success(null)
            }
        } catch (_: Exception) {
            PgResult.Success(null)
        }
    }

    fun observeFolderMappings(ownerId: String): Flow<PgResult<Map<String, String>>> = callbackFlow {
        val effectiveOwnerId = if (ownerId.isNotBlank() && ownerId != "default_owner") ownerId else currentOwnerId ?: ""
        if (effectiveOwnerId.isBlank()) {
            Log.e("TenantMedia", "observeFolderMappings error: User is not authenticated")
            trySend(PgResult.Failure(PgError.SecurityError("User is not authenticated")))
            close()
            return@callbackFlow
        }
        val listener = folderMappingsCollection.whereEqualTo("ownerId", effectiveOwnerId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(PgResult.Failure(mapFirestoreException(error)))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val map = mutableMapOf<String, String>()
                for (doc in snapshot.documents) {
                    val key = doc.getString("folderPathKey") ?: doc.id
                    val folderId = doc.getString("driveFolderId") ?: ""
                    if (key.isNotBlank() && folderId.isNotBlank()) {
                        map[key] = folderId
                    }
                }
                trySend(PgResult.Success(map))
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun getAllFolderMappings(ownerId: String): PgResult<Map<String, String>> {
        return try {
            val effectiveOwnerId = if (ownerId.isNotBlank() && ownerId != "default_owner") ownerId else currentOwnerId ?: ""
            if (effectiveOwnerId.isBlank()) {
                return PgResult.Failure(PgError.SecurityError("User is not authenticated"))
            }
            val snapshot = folderMappingsCollection.whereEqualTo("ownerId", effectiveOwnerId).get().await()
            val map = mutableMapOf<String, String>()
            for (doc in snapshot.documents) {
                val key = doc.getString("folderPathKey") ?: doc.id
                val folderId = doc.getString("driveFolderId") ?: ""
                if (key.isNotBlank() && folderId.isNotBlank()) {
                    map[key] = folderId
                }
            }
            PgResult.Success(map)
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to fetch drive folder mappings", e))
        }
    }
}
