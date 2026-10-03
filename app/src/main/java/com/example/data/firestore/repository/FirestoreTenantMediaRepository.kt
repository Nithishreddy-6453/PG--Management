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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreTenantMediaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val collection = firestore.collection("tenant_media")

    private val currentOwnerId: String
        get() = auth.currentUser?.uid ?: "default_owner"

    suspend fun saveMediaMetadata(dto: TenantMediaDto): PgResult<Unit> {
        return try {
            val docId = if (dto.cloudId.isNotBlank()) dto.cloudId else "${dto.tenantCloudId}_PROFILE_PHOTO"
            val toSave = dto.copy(
                cloudId = docId,
                ownerId = if (dto.ownerId.isNotBlank()) dto.ownerId else currentOwnerId,
                updatedAt = System.currentTimeMillis()
            )
            collection.document(docId).set(toSave).await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
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
            PgResult.Success(Unit)
        } catch (_: Exception) {
            PgResult.Success(Unit)
        }
    }

    suspend fun getAllTenantMedia(ownerId: String, includeDeleted: Boolean = true): PgResult<List<TenantMediaDto>> {
        return try {
            val query = if (ownerId.isNotBlank()) {
                collection.whereEqualTo("ownerId", ownerId)
            } else {
                collection
            }
            val snapshot = query.get().await()
            val list = snapshot.toObjects(TenantMediaDto::class.java)
            val filtered = if (!includeDeleted) list.filter { !it.deleted } else list
            PgResult.Success(filtered)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to fetch tenant media list from Firestore", e))
        }
    }

    fun observeTenantMedia(ownerId: String, includeDeleted: Boolean = true): Flow<PgResult<List<TenantMediaDto>>> = callbackFlow {
        val query = if (ownerId.isNotBlank()) {
            collection.whereEqualTo("ownerId", ownerId)
        } else {
            collection
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(PgResult.Failure(mapFirestoreException(error)))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.toObjects(TenantMediaDto::class.java)
                val filtered = if (!includeDeleted) list.filter { !it.deleted } else list
                trySend(PgResult.Success(filtered))
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun deleteMediaMetadata(cloudId: String): PgResult<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "deleted" to true,
                "status" to "DELETED",
                "updatedAt" to System.currentTimeMillis()
            )
            collection.document(cloudId).update(updates).await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to delete media metadata from Firestore", e))
        }
    }

    private val folderMappingsCollection = firestore.collection("drive_folder_mappings")

    suspend fun saveFolderMapping(mappingKey: String, folderId: String): PgResult<Unit> {
        return try {
            val data = mapOf(
                "folderPathKey" to mappingKey,
                "driveFolderId" to folderId,
                "ownerId" to currentOwnerId,
                "updatedAt" to System.currentTimeMillis()
            )
            folderMappingsCollection.document(mappingKey).set(data).await()
            PgResult.Success(Unit)
        } catch (e: Exception) {
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
}
