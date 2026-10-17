package com.example.features.tenants.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.core.device.DeviceIdentityManager
import com.example.core.util.ImageProcessingUtils
import com.example.data.database.DriveFolderDao
import com.example.data.database.DriveFolderMappingEntity
import com.example.data.database.PropertyDao
import com.example.data.database.TenantDao
import com.example.data.database.TenantMediaDao
import com.example.data.database.TenantMediaEntity
import com.example.data.firestore.mapper.toEntity
import com.example.data.firestore.model.TenantMediaDto
import com.example.data.firestore.repository.FirestoreTenantMediaRepository
import com.example.features.googleform.data.auth.GoogleFormsAuthManager
import com.example.features.tenants.data.api.CreateFolderRequest
import com.example.features.tenants.data.api.FileMetadataRequest
import com.example.features.tenants.data.api.GoogleDriveApiService
import com.example.features.tenants.data.api.UpdateMetadataRequest
import com.example.features.tenants.domain.model.TenantMedia
import com.example.features.tenants.domain.repository.TenantMediaRepository
import com.google.firebase.auth.FirebaseAuth
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TenantMediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tenantMediaDao: TenantMediaDao,
    private val driveFolderDao: DriveFolderDao,
    private val propertyDao: PropertyDao,
    private val tenantDao: TenantDao,
    private val firestoreRepository: FirestoreTenantMediaRepository,
    private val googleAuthManager: GoogleFormsAuthManager,
    private val driveApiService: GoogleDriveApiService,
    private val moshi: Moshi,
    private val auth: FirebaseAuth,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val syncCoordinatorProvider: javax.inject.Provider<com.example.data.sync.SyncCoordinator>
) : TenantMediaRepository {

    private val currentOwnerId: String
        get() = auth.currentUser?.uid ?: ""

    private val folderMutex = Mutex()

    override fun getProfilePhotoFlow(tenantCloudId: String): Flow<TenantMedia?> {
        return tenantMediaDao.getProfilePhotoFlow(tenantCloudId).map { it?.toDomain() }
    }

    override suspend fun getProfilePhoto(tenantCloudId: String): TenantMedia? {
        return tenantMediaDao.getProfilePhoto(tenantCloudId)?.toDomain()
    }

    override fun getDocumentsFlow(tenantCloudId: String): Flow<List<TenantMedia>> {
        return tenantMediaDao.getDocumentsForTenantFlow(tenantCloudId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getDocuments(tenantCloudId: String): List<TenantMedia> {
        return tenantMediaDao.getDocumentsForTenant(tenantCloudId).map { it.toDomain() }
    }

    data class TenantFolderStructure(
        val tenantFolderId: String,
        val photosFolderId: String,
        val docsFolderId: String
    )

    override suspend fun uploadProfilePhoto(
        propertyId: String,
        tenantCloudId: String,
        imageUri: Uri
    ): PgResult<TenantMedia> = withContext(Dispatchers.IO) {
        try {
            // Canonical stable ID for this tenant's profile photo across all devices
            val canonicalCloudId = "${tenantCloudId}_PROFILE_PHOTO"

            // 1. Get Drive Auth Token
            val authResult = googleAuthManager.getDriveAuthorizationHeader()
            if (authResult is PgResult.Failure) {
                return@withContext PgResult.Failure(authResult.error)
            }
            val authHeader = (authResult as PgResult.Success).data

            // 2. Process & Compress Image (rotate EXIF, resize max 1600px, JPEG 85%)
            val processed = try {
                ImageProcessingUtils.processAndCompressImage(context, imageUri, maxDimension = 1600, quality = 85)
            } catch (e: Exception) {
                return@withContext PgResult.Failure(PgError.ValidationError("Failed to process photo: ${e.message}"))
            }

            // 3. Look up existing canonical media record (Room first, then Firestore)
            var existingMedia = tenantMediaDao.getMediaByCloudId(canonicalCloudId)
                ?: tenantMediaDao.getProfilePhoto(tenantCloudId)
            if (existingMedia == null) {
                val firestoreRes = firestoreRepository.getProfilePhotoMetadata(tenantCloudId)
                if (firestoreRes is PgResult.Success && firestoreRes.data != null) {
                    existingMedia = firestoreRes.data!!.toEntity().copy(cloudId = canonicalCloudId)
                    tenantMediaDao.deleteNonCanonicalProfilePhotos(tenantCloudId, canonicalCloudId)
                    tenantMediaDao.insertOrUpdate(existingMedia)
                }
            }

            // 4. Resolve Folder Hierarchy & Reuse Existing Folders
            // Check if existingMedia already has a valid drivePhotosFolderId
            var photosFolderId = existingMedia?.drivePhotosFolderId ?: ""
            var tenantFolderId = existingMedia?.driveTenantFolderId ?: ""
            if (photosFolderId.isNotBlank()) {
                try {
                    val check = driveApiService.getFile(authHeader, photosFolderId)
                    if (!check.isSuccessful || check.body()?.trashed == true) {
                        photosFolderId = ""
                    }
                } catch (_: Exception) {
                    photosFolderId = ""
                }
            }

            val folders = if (photosFolderId.isNotBlank()) {
                TenantFolderStructure(
                    tenantFolderId = tenantFolderId,
                    photosFolderId = photosFolderId,
                    docsFolderId = ""
                )
            } else {
                try {
                    resolveTenantFolders(authHeader, propertyId, tenantCloudId)
                } catch (e: Exception) {
                    return@withContext PgResult.Failure(PgError.NetworkError("Failed to initialize Google Drive folder structure: ${e.message}"))
                }
            }

            // 5. Query any existing files in photosFolderId to delete duplicates later
            val existingFilesToDelete = mutableListOf<String>()
            try {
                val listResp = driveApiService.listFiles(
                    authHeader = authHeader,
                    query = "'${folders.photosFolderId}' in parents and trashed = false"
                )
                if (listResp.isSuccessful && listResp.body() != null) {
                    for (file in listResp.body()!!.files) {
                        if (file.id.isNotBlank()) {
                            existingFilesToDelete.add(file.id)
                        }
                    }
                }
            } catch (_: Exception) {}

            if (existingMedia != null && existingMedia.driveFileId.isNotBlank() && !existingFilesToDelete.contains(existingMedia.driveFileId)) {
                existingFilesToDelete.add(existingMedia.driveFileId)
            }

            // 6. Upload photo to Google Drive inside the Photos/ subfolder
            val metadata = FileMetadataRequest(
                name = "Profile Photo.jpg",
                mimeType = "image/jpeg",
                parents = listOf(folders.photosFolderId)
            )
            val metadataJson = moshi.adapter(FileMetadataRequest::class.java).toJson(metadata)

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaTypeOrNull() ?: MultipartBody.FORM)
                .addPart(
                    metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull())
                )
                .addPart(
                    processed.file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val uploadResponse = driveApiService.uploadFileMultipart(
                authHeader = authHeader,
                contentType = "multipart/related; boundary=${multipartBody.boundary}",
                body = multipartBody
            )

            if (!uploadResponse.isSuccessful || uploadResponse.body() == null) {
                val errorBody = uploadResponse.errorBody()?.string() ?: ""
                return@withContext PgResult.Failure(
                    PgError.NetworkError("Google Drive upload failed (${uploadResponse.code()}): $errorBody")
                )
            }

            val driveFile = uploadResponse.body()!!
            val newDriveFileId = driveFile.id
            Log.d("TenantMedia", "Drive upload success: driveFileId=$newDriveFileId")

            // Ensure photo is accessible across devices with different Drive accounts by setting permission
            try {
                driveApiService.createPermission(
                    authHeader = authHeader,
                    fileId = newDriveFileId,
                    request = com.example.features.tenants.data.api.CreatePermissionRequest(role = "reader", type = "anyone")
                )
            } catch (e: Exception) {
                Log.w("TenantMedia", "Failed to set public read permission on profile photo '$newDriveFileId': ${e.message}")
            }

            // 7. Enforce ONE active profile photo: delete previous files in photosFolderId
            for (oldFileId in existingFilesToDelete) {
                if (oldFileId != newDriveFileId) {
                    try {
                        driveApiService.deleteFile(authHeader, oldFileId)
                    } catch (_: Exception) {}
                }
            }

            // 8. Delete any non-canonical profile photo records in local Room
            tenantMediaDao.deleteNonCanonicalProfilePhotos(tenantCloudId, canonicalCloudId)

            // 9. Save Canonical record in Room Database
            val now = System.currentTimeMillis()
            val authUid = auth.currentUser?.uid
            if (authUid.isNullOrBlank()) {
                Log.e("TenantMedia", "Upload profile photo aborted: FirebaseAuth.currentUser.uid is null")
                return@withContext PgResult.Failure(PgError.SecurityError("User is not authenticated"))
            }
            val effectiveOwnerId = authUid
            Log.d("TenantMedia", "uploadProfilePhoto: authUid=$authUid, effectiveOwnerId=$effectiveOwnerId, tenantCloudId=$tenantCloudId")
            val myDeviceId = deviceIdentityManager.getDeviceId()

            val cacheDir = File(context.cacheDir, "tenant_photos").apply { mkdirs() }
            val targetCacheFile = File(cacheDir, "${tenantCloudId}_${newDriveFileId}.jpg")
            try {
                processed.file.copyTo(targetCacheFile, overwrite = true)
            } catch (_: Exception) {}

            val canonicalLocalPath = if (targetCacheFile.exists() && targetCacheFile.length() > 0) targetCacheFile.absolutePath else processed.file.absolutePath

            val entity = TenantMediaEntity(
                cloudId = canonicalCloudId,
                ownerId = effectiveOwnerId,
                propertyId = propertyId,
                tenantCloudId = tenantCloudId,
                driveFileId = newDriveFileId,
                fileUrl = "https://drive.google.com/uc?export=view&id=$newDriveFileId",
                driveTenantFolderId = folders.tenantFolderId,
                drivePhotosFolderId = folders.photosFolderId,
                fileName = driveFile.name.ifBlank { "Profile Photo.jpg" },
                mimeType = "image/jpeg",
                sizeBytes = processed.sizeBytes,
                mediaType = "PROFILE_PHOTO",
                status = "ACTIVE",
                verificationStatus = "VERIFIED",
                localFilePath = canonicalLocalPath,
                driveFolderId = folders.photosFolderId,
                createdAt = existingMedia?.createdAt ?: now,
                updatedAt = now,
                deleted = false,
                syncStatus = "SYNCED",
                lastSyncedAt = now,
                lastModifiedByDeviceId = myDeviceId
            )
            tenantMediaDao.insertOrUpdate(entity)

            // 10. Save Canonical metadata in Firestore & cleanup legacy documents
            val dto = entity.toDto()
            val firestoreResult = firestoreRepository.saveMediaMetadata(dto)
            if (firestoreResult is PgResult.Failure) {
                Log.e("TenantMedia", "Firestore tenant_media write failed for docId=$canonicalCloudId: ${firestoreResult.error.message}")
                return@withContext PgResult.Failure(
                    PgError.DatabaseError("Failed to save tenant_media metadata to Firestore: ${firestoreResult.error.message}")
                )
            }
            Log.d("TenantMedia", "Firestore tenant_media write success: docId=$canonicalCloudId")
            firestoreRepository.cleanUpLegacyMediaDocuments(tenantCloudId, canonicalCloudId)
            try {
                syncCoordinatorProvider.get().enqueueOperation("MEDIA", canonicalCloudId, "UPDATE")
            } catch (_: Exception) {}

            PgResult.Success(entity.toDomain())
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to upload profile photo: ${e.localizedMessage}", e))
        }
    }

    override suspend fun deleteProfilePhoto(tenantCloudId: String): PgResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val canonicalCloudId = "${tenantCloudId}_PROFILE_PHOTO"
            val existing = tenantMediaDao.getMediaByCloudId(canonicalCloudId)
                ?: tenantMediaDao.getProfilePhoto(tenantCloudId)
                ?: return@withContext PgResult.Success(Unit)

            val now = System.currentTimeMillis()
            val myDeviceId = deviceIdentityManager.getDeviceId()

            // 1. Soft-delete canonical record in Room
            val deletedEntity = existing.copy(
                driveFileId = "",
                localFilePath = "",
                deleted = true,
                status = "DELETED",
                updatedAt = now,
                syncStatus = "SYNCED",
                lastSyncedAt = now,
                lastModifiedByDeviceId = myDeviceId
            )
            tenantMediaDao.insertOrUpdate(deletedEntity)
            tenantMediaDao.deleteNonCanonicalProfilePhotos(tenantCloudId, canonicalCloudId)

            // 2. Soft-delete in Firestore
            firestoreRepository.saveMediaMetadata(deletedEntity.toDto())
            firestoreRepository.cleanUpLegacyMediaDocuments(tenantCloudId, canonicalCloudId)
            try {
                syncCoordinatorProvider.get().enqueueOperation("MEDIA", canonicalCloudId, "DELETE")
            } catch (_: Exception) {}

            // 3. Delete from Google Drive in background
            val authResult = googleAuthManager.getDriveAuthorizationHeader()
            if (authResult is PgResult.Success) {
                val authHeader = authResult.data
                if (existing.driveFileId.isNotBlank()) {
                    try {
                        driveApiService.deleteFile(authHeader, existing.driveFileId)
                    } catch (_: Exception) {}
                }
                if (existing.drivePhotosFolderId.isNotBlank()) {
                    try {
                        val listResp = driveApiService.listFiles(
                            authHeader = authHeader,
                            query = "'${existing.drivePhotosFolderId}' in parents and trashed = false"
                        )
                        if (listResp.isSuccessful && listResp.body() != null) {
                            for (file in listResp.body()!!.files) {
                                try { driveApiService.deleteFile(authHeader, file.id) } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // 4. Delete local cached file
            if (existing.localFilePath.isNotBlank()) {
                try {
                    File(existing.localFilePath).delete()
                } catch (_: Exception) {}
            }

            PgResult.Success(Unit)
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to remove profile photo: ${e.localizedMessage}", e))
        }
    }

    override suspend fun fetchAndCachePhotoIfMissing(tenantCloudId: String): PgResult<File?> = withContext(Dispatchers.IO) {
        try {
            val canonicalCloudId = "${tenantCloudId}_PROFILE_PHOTO"
            var media = tenantMediaDao.getMediaByCloudId(canonicalCloudId)
                ?: tenantMediaDao.getProfilePhoto(tenantCloudId)

            // Always check Firestore for the latest media metadata to catch cross-device updates!
            val firestoreRes = firestoreRepository.getProfilePhotoMetadata(tenantCloudId)
            if (firestoreRes is PgResult.Success && firestoreRes.data != null) {
                val dto = firestoreRes.data!!
                if (dto.driveFileId.isNotBlank()) {
                    if (media == null || dto.driveFileId != media.driveFileId || dto.updatedAt > media.updatedAt) {
                        val cacheDir = File(context.cacheDir, "tenant_photos").apply { mkdirs() }
                        val targetFile = File(cacheDir, "${tenantCloudId}_${dto.driveFileId}.jpg")
                        val existingPath = if (targetFile.exists() && targetFile.length() > 0) targetFile.absolutePath else ""

                        val entity = dto.toEntity(localFilePath = existingPath).copy(cloudId = canonicalCloudId)
                        tenantMediaDao.deleteNonCanonicalProfilePhotos(tenantCloudId, canonicalCloudId)
                        tenantMediaDao.insertOrUpdate(entity)
                        media = entity
                    }
                }
            }

            if (media == null || media.driveFileId.isBlank()) {
                return@withContext PgResult.Success(null)
            }

            // 1. Check if designated cache file already exists on disk
            val cacheDir = File(context.cacheDir, "tenant_photos").apply { mkdirs() }
            val targetFile = File(cacheDir, "${tenantCloudId}_${media.driveFileId}.jpg")
            if (targetFile.exists() && targetFile.length() > 0) {
                if (media.localFilePath != targetFile.absolutePath) {
                    tenantMediaDao.updateLocalFilePathOnly(media.cloudId, targetFile.absolutePath)
                }
                return@withContext PgResult.Success(targetFile)
            }

            // 2. If entity's current localFilePath exists and matches current driveFileId, return it
            if (media.localFilePath.isNotBlank()) {
                val localFile = File(media.localFilePath)
                if (localFile.exists() && localFile.length() > 0 && localFile.name.contains(media.driveFileId)) {
                    return@withContext PgResult.Success(localFile)
                }
            }

            // 3. Otherwise download from Google Drive using authenticated Google Drive API with cross-account fallback
            val authResult = googleAuthManager.getDriveAuthorizationHeader()
            if (authResult is PgResult.Failure) {
                return@withContext PgResult.Failure(authResult.error)
            }
            val authHeader = (authResult as PgResult.Success).data

            var downloaded = false
            try {
                val response = driveApiService.downloadFileMedia(
                    authHeader = authHeader,
                    fileId = media.driveFileId
                )

                if (response.isSuccessful && response.body() != null) {
                    response.body()!!.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (targetFile.exists() && targetFile.length() > 0) {
                        downloaded = true
                    }
                }
            } catch (e: Exception) {
                Log.w("TenantMedia", "API download failed for photo fileId=${media.driveFileId}: ${e.message}")
            }

            // Fallback: If Drive API download returned 404/403 or failed due to cross-account Drive ownership, attempt direct stream download
            if (!downloaded) {
                val fallbackUrls = listOf(
                    "https://drive.google.com/uc?export=download&id=${media.driveFileId}",
                    "https://lh3.googleusercontent.com/d/${media.driveFileId}"
                )
                for (fallbackUrl in fallbackUrls) {
                    try {
                        val client = okhttp3.OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
                        val req = okhttp3.Request.Builder().url(fallbackUrl).get().build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful && resp.body != null) {
                            resp.body!!.byteStream().use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (targetFile.exists() && targetFile.length() > 0) {
                                downloaded = true
                                Log.d("TenantMedia", "Photo downloaded via public fallback URL: ${media.driveFileId}")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("TenantMedia", "Fallback photo download failed for url '$fallbackUrl': ${e.message}")
                    }
                }
            }

            if (downloaded) {
                val now = System.currentTimeMillis()
                tenantMediaDao.updateLocalFilePath(media.cloudId, targetFile.absolutePath, now)
                if (media.cloudId != canonicalCloudId) {
                    tenantMediaDao.updateLocalFilePath(canonicalCloudId, targetFile.absolutePath, now)
                }
                Log.d("TenantMedia", "Photo downloaded successfully for tenantCloudId=$tenantCloudId, fileId=${media.driveFileId}, path=${targetFile.absolutePath}, bytes=${targetFile.length()}")
                return@withContext PgResult.Success(targetFile)
            } else {
                Log.e("TenantMedia", "Google Drive download failed for photo: tenantCloudId=$tenantCloudId, fileId=${media.driveFileId}")
                return@withContext PgResult.Failure(
                    PgError.NetworkError("Failed to download photo from Google Drive (404/403)")
                )
            }
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to fetch tenant photo: ${e.localizedMessage}", e))
        }
    }

    override suspend fun syncTenantDriveFolder(tenantCloudId: String): PgResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val tenant = tenantDao.getTenantByCloudId(tenantCloudId) ?: return@withContext PgResult.Success(Unit)
            val authResult = googleAuthManager.getDriveAuthorizationHeader()
            if (authResult !is PgResult.Success) {
                return@withContext PgResult.Success(Unit)
            }
            val authHeader = authResult.data
            val folders = resolveTenantFolders(authHeader, tenant.propertyId, tenantCloudId)

            val canonicalCloudId = "${tenantCloudId}_PROFILE_PHOTO"
            var mediaRecord = tenantMediaDao.getMediaByCloudId(canonicalCloudId) ?: tenantMediaDao.getProfilePhoto(tenantCloudId)

            // 1. Scan Drive Photos folder if local photo is missing or invalid
            val photoTargetFile = if (mediaRecord != null && mediaRecord.driveFileId.isNotBlank()) {
                File(context.cacheDir, "tenant_photos/${tenantCloudId}_${mediaRecord.driveFileId}.jpg")
            } else null

            if (mediaRecord == null || mediaRecord.driveFileId.isBlank() || photoTargetFile == null || !photoTargetFile.exists() || photoTargetFile.length() == 0L) {
                try {
                    val query = "'${folders.photosFolderId}' in parents and trashed = false"
                    val response = driveApiService.listFiles(authHeader, query = query, pageSize = 10, fields = "files(id, name, mimeType, size, createdTime)")
                    if (response.isSuccessful && response.body() != null) {
                        val photoFile = response.body()!!.files.firstOrNull { it.mimeType.startsWith("image/") }
                        if (photoFile != null) {
                            val now = System.currentTimeMillis()
                            val authUid = auth.currentUser?.uid ?: ""
                            val myDeviceId = deviceIdentityManager.getDeviceId()
                            val newEntity = TenantMediaEntity(
                                cloudId = canonicalCloudId,
                                ownerId = authUid,
                                propertyId = tenant.propertyId,
                                tenantCloudId = tenantCloudId,
                                driveFileId = photoFile.id,
                                driveTenantFolderId = folders.tenantFolderId,
                                drivePhotosFolderId = folders.photosFolderId,
                                fileName = photoFile.name,
                                mimeType = photoFile.mimeType,
                                sizeBytes = photoFile.size?.toLongOrNull() ?: 0L,
                                mediaType = "PROFILE_PHOTO",
                                status = "ACTIVE",
                                localFilePath = "",
                                createdAt = now,
                                updatedAt = now,
                                syncStatus = "SYNCED",
                                lastModifiedByDeviceId = myDeviceId
                            )
                            tenantMediaDao.deleteNonCanonicalProfilePhotos(tenantCloudId, canonicalCloudId)
                            tenantMediaDao.insertOrUpdate(newEntity)
                            firestoreRepository.saveMediaMetadata(newEntity.toDto())

                            // Fetch & cache file
                            fetchAndCachePhotoIfMissing(tenantCloudId)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TenantMedia", "Photo drive scan failed: ${e.message}")
                }
            }

            // 2. Scan Drive Documents folder for any missing document files
            try {
                val docQuery = "'${folders.docsFolderId}' in parents and trashed = false"
                val docResponse = driveApiService.listFiles(authHeader, query = docQuery, pageSize = 20, fields = "files(id, name, mimeType, size, createdTime)")
                if (docResponse.isSuccessful && docResponse.body() != null) {
                    val docFiles = docResponse.body()!!.files
                    val existingDocs = tenantMediaDao.getDocumentsForTenant(tenantCloudId)
                    val existingDriveIds = existingDocs.map { it.driveFileId }.toSet()

                    val authUid = auth.currentUser?.uid ?: ""
                    val myDeviceId = deviceIdentityManager.getDeviceId()

                    docFiles.forEach { docFile ->
                        if (!existingDriveIds.contains(docFile.id)) {
                            val docCloudId = "${tenantCloudId}_DOC_${UUID.randomUUID().toString().take(8)}"
                            val now = System.currentTimeMillis()
                            val newDocEntity = TenantMediaEntity(
                                cloudId = docCloudId,
                                ownerId = authUid,
                                propertyId = tenant.propertyId,
                                tenantCloudId = tenantCloudId,
                                driveFileId = docFile.id,
                                driveTenantFolderId = folders.tenantFolderId,
                                fileName = docFile.name,
                                mimeType = docFile.mimeType,
                                sizeBytes = docFile.size?.toLongOrNull() ?: 0L,
                                mediaType = "TENANT_DOCUMENT",
                                status = "ACTIVE",
                                localFilePath = "",
                                createdAt = now,
                                updatedAt = now,
                                syncStatus = "SYNCED",
                                lastModifiedByDeviceId = myDeviceId
                            )
                            tenantMediaDao.insertOrUpdate(newDocEntity)
                            firestoreRepository.saveMediaMetadata(newDocEntity.toDto())
                            fetchAndCacheDocumentIfMissing(docCloudId)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("TenantMedia", "Docs drive scan failed: ${e.message}")
            }

            PgResult.Success(Unit)
        } catch (e: Exception) {
            Log.e("TenantMedia", "syncTenantDriveFolder error: ${e.message}", e)
            PgResult.Success(Unit)
        }
    }

    override suspend fun uploadDocument(
        propertyId: String,
        tenantCloudId: String,
        documentUri: Uri,
        fileName: String,
        mimeType: String
    ): PgResult<TenantMedia> = withContext(Dispatchers.IO) {
        try {
            val docId = "${tenantCloudId}_DOC_${UUID.randomUUID().toString().take(8)}"
            val safeName = fileName.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim().ifBlank { "document.pdf" }
            val docsDir = File(context.cacheDir, "tenant_docs").apply { mkdirs() }
            val localFile = File(docsDir, "${docId}_$safeName")

            context.contentResolver.openInputStream(documentUri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext PgResult.Failure(PgError.ValidationError("Could not read document"))

            val sizeBytes = localFile.length()
            val now = System.currentTimeMillis()
            val authUid = auth.currentUser?.uid
            if (authUid.isNullOrBlank()) {
                Log.e("TenantMedia", "Upload document aborted: FirebaseAuth.currentUser.uid is null")
                return@withContext PgResult.Failure(PgError.SecurityError("User is not authenticated"))
            }
            val effectiveOwnerId = authUid
            Log.d("TenantMedia", "uploadDocument: authUid=$authUid, effectiveOwnerId=$effectiveOwnerId, tenantCloudId=$tenantCloudId, docId=$docId")
            val myDeviceId = deviceIdentityManager.getDeviceId()

            val authResult = googleAuthManager.getDriveAuthorizationHeader()
            if (authResult is PgResult.Failure) {
                val pendingEntity = TenantMediaEntity(
                    cloudId = docId,
                    ownerId = effectiveOwnerId,
                    propertyId = propertyId,
                    tenantCloudId = tenantCloudId,
                    driveFileId = "",
                    fileName = safeName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    mediaType = "TENANT_DOCUMENT",
                    status = "ACTIVE",
                    localFilePath = localFile.absolutePath,
                    createdAt = now,
                    updatedAt = now,
                    deleted = false,
                    syncStatus = "PENDING_UPLOAD",
                    lastModifiedByDeviceId = myDeviceId
                )
                tenantMediaDao.insertOrUpdate(pendingEntity)
                try {
                    syncCoordinatorProvider.get().enqueueOperation("MEDIA", docId, "CREATE")
                } catch (_: Exception) {}
                return@withContext PgResult.Failure(authResult.error)
            }
            val authHeader = (authResult as PgResult.Success).data

            val folders = resolveTenantFolders(authHeader, propertyId, tenantCloudId)

            val metadata = FileMetadataRequest(
                name = safeName,
                mimeType = mimeType,
                parents = listOf(folders.docsFolderId)
            )
            val metadataJson = moshi.adapter(FileMetadataRequest::class.java).toJson(metadata)

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaTypeOrNull() ?: MultipartBody.FORM)
                .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                .addPart(localFile.asRequestBody(mimeType.toMediaTypeOrNull()))
                .build()

            val uploadResponse = driveApiService.uploadFileMultipart(
                authHeader = authHeader,
                contentType = "multipart/related; boundary=${multipartBody.boundary}",
                body = multipartBody
            )

            if (!uploadResponse.isSuccessful || uploadResponse.body() == null) {
                val errorBody = uploadResponse.errorBody()?.string() ?: ""
                val pendingEntity = TenantMediaEntity(
                    cloudId = docId,
                    ownerId = effectiveOwnerId,
                    propertyId = propertyId,
                    tenantCloudId = tenantCloudId,
                    driveFileId = "",
                    fileName = safeName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    mediaType = "TENANT_DOCUMENT",
                    status = "ACTIVE",
                    localFilePath = localFile.absolutePath,
                    createdAt = now,
                    updatedAt = now,
                    deleted = false,
                    syncStatus = "PENDING_UPLOAD",
                    lastModifiedByDeviceId = myDeviceId
                )
                tenantMediaDao.insertOrUpdate(pendingEntity)
                try {
                    syncCoordinatorProvider.get().enqueueOperation("MEDIA", docId, "CREATE")
                } catch (_: Exception) {}
                return@withContext PgResult.Failure(PgError.NetworkError("Upload failed: $errorBody"))
            }

            val driveFile = uploadResponse.body()!!
            val newDriveFileId = driveFile.id
            Log.d("TenantMedia", "Drive upload success: driveFileId=$newDriveFileId")

            val entity = TenantMediaEntity(
                cloudId = docId,
                ownerId = effectiveOwnerId,
                propertyId = propertyId,
                tenantCloudId = tenantCloudId,
                driveFileId = newDriveFileId,
                fileUrl = "https://drive.google.com/uc?export=view&id=$newDriveFileId",
                driveTenantFolderId = folders.tenantFolderId,
                drivePhotosFolderId = folders.photosFolderId,
                driveFolderId = folders.docsFolderId,
                fileName = driveFile.name.ifBlank { safeName },
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                mediaType = "TENANT_DOCUMENT",
                status = "ACTIVE",
                verificationStatus = "VERIFIED",
                localFilePath = localFile.absolutePath,
                createdAt = now,
                updatedAt = now,
                deleted = false,
                syncStatus = "SYNCED",
                lastSyncedAt = now,
                lastModifiedByDeviceId = myDeviceId
            )
            tenantMediaDao.insertOrUpdate(entity)
            val firestoreResult = firestoreRepository.saveMediaMetadata(entity.toDto())
            if (firestoreResult is PgResult.Failure) {
                Log.e("TenantMedia", "Firestore tenant_media write failed for docId=$docId: ${firestoreResult.error.message}")
                return@withContext PgResult.Failure(
                    PgError.DatabaseError("Failed to save tenant_media metadata to Firestore: ${firestoreResult.error.message}")
                )
            }
            Log.d("TenantMedia", "Firestore tenant_media write success: docId=$docId")
            try {
                syncCoordinatorProvider.get().enqueueOperation("MEDIA", docId, "UPDATE")
            } catch (_: Exception) {}

            PgResult.Success(entity.toDomain())
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to upload document: ${e.localizedMessage}", e))
        }
    }

    override suspend fun queueDocumentForUpload(
        propertyId: String,
        tenantCloudId: String,
        documentUri: Uri,
        fileName: String,
        mimeType: String
    ): PgResult<TenantMedia> = withContext(Dispatchers.IO) {
        try {
            val docId = "${tenantCloudId}_DOC_${UUID.randomUUID().toString().take(8)}"
            val safeName = fileName.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim().ifBlank { "document.pdf" }
            val docsDir = File(context.cacheDir, "tenant_docs").apply { mkdirs() }
            val localFile = File(docsDir, "${docId}_$safeName")

            context.contentResolver.openInputStream(documentUri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext PgResult.Failure(PgError.ValidationError("Could not read document"))

            val sizeBytes = localFile.length()
            val now = System.currentTimeMillis()
            val authUid = auth.currentUser?.uid
            if (authUid.isNullOrBlank()) {
                Log.e("TenantMedia", "Queue document aborted: FirebaseAuth.currentUser.uid is null")
                return@withContext PgResult.Failure(PgError.SecurityError("User is not authenticated"))
            }
            val effectiveOwnerId = authUid
            Log.d("TenantMedia", "queueDocumentForUpload: authUid=$authUid, effectiveOwnerId=$effectiveOwnerId, tenantCloudId=$tenantCloudId")
            val myDeviceId = deviceIdentityManager.getDeviceId()

            val entity = TenantMediaEntity(
                cloudId = docId,
                ownerId = effectiveOwnerId,
                propertyId = propertyId,
                tenantCloudId = tenantCloudId,
                driveFileId = "",
                fileName = safeName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                mediaType = "TENANT_DOCUMENT",
                status = "ACTIVE",
                localFilePath = localFile.absolutePath,
                createdAt = now,
                updatedAt = now,
                deleted = false,
                syncStatus = "PENDING_UPLOAD",
                lastModifiedByDeviceId = myDeviceId
            )
            tenantMediaDao.insertOrUpdate(entity)
            try {
                syncCoordinatorProvider.get().enqueueOperation("MEDIA", docId, "CREATE")
            } catch (_: Exception) {}

            PgResult.Success(entity.toDomain())
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to queue document: ${e.localizedMessage}", e))
        }
    }

    override suspend fun queueProfilePhotoForUpload(
        propertyId: String,
        tenantCloudId: String,
        imageUri: Uri
    ): PgResult<TenantMedia> = withContext(Dispatchers.IO) {
        try {
            val canonicalCloudId = "${tenantCloudId}_PROFILE_PHOTO"
            val processed = try {
                ImageProcessingUtils.processAndCompressImage(context, imageUri, maxDimension = 1600, quality = 85)
            } catch (e: Exception) {
                return@withContext PgResult.Failure(PgError.ValidationError("Failed to process photo: ${e.message}"))
            }

            val now = System.currentTimeMillis()
            val authUid = auth.currentUser?.uid
            if (authUid.isNullOrBlank()) {
                Log.e("TenantMedia", "Queue photo aborted: FirebaseAuth.currentUser.uid is null")
                return@withContext PgResult.Failure(PgError.SecurityError("User is not authenticated"))
            }
            val effectiveOwnerId = authUid
            Log.d("TenantMedia", "queueProfilePhotoForUpload: authUid=$authUid, effectiveOwnerId=$effectiveOwnerId, tenantCloudId=$tenantCloudId")
            val myDeviceId = deviceIdentityManager.getDeviceId()

            tenantMediaDao.deleteNonCanonicalProfilePhotos(tenantCloudId, canonicalCloudId)
            val entity = TenantMediaEntity(
                cloudId = canonicalCloudId,
                ownerId = effectiveOwnerId,
                propertyId = propertyId,
                tenantCloudId = tenantCloudId,
                driveFileId = "",
                fileName = "Profile Photo.jpg",
                mimeType = "image/jpeg",
                sizeBytes = processed.sizeBytes,
                mediaType = "PROFILE_PHOTO",
                status = "ACTIVE",
                localFilePath = processed.file.absolutePath,
                createdAt = now,
                updatedAt = now,
                deleted = false,
                syncStatus = "PENDING_UPLOAD",
                lastModifiedByDeviceId = myDeviceId
            )
            tenantMediaDao.insertOrUpdate(entity)
            try {
                syncCoordinatorProvider.get().enqueueOperation("MEDIA", canonicalCloudId, "CREATE")
            } catch (_: Exception) {}

            PgResult.Success(entity.toDomain())
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to queue photo: ${e.localizedMessage}", e))
        }
    }

    override suspend fun deleteMedia(cloudId: String): PgResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val existing = tenantMediaDao.getMediaByCloudId(cloudId)
                ?: return@withContext PgResult.Success(Unit)

            val now = System.currentTimeMillis()
            val myDeviceId = deviceIdentityManager.getDeviceId()

            val deleted = existing.copy(
                deleted = true,
                status = "DELETED",
                updatedAt = now,
                syncStatus = "SYNCED",
                lastSyncedAt = now,
                lastModifiedByDeviceId = myDeviceId
            )
            tenantMediaDao.insertOrUpdate(deleted)
            firestoreRepository.saveMediaMetadata(deleted.toDto())
            try {
                syncCoordinatorProvider.get().enqueueOperation("MEDIA", cloudId, "DELETE")
            } catch (_: Exception) {}

            if (existing.driveFileId.isNotBlank()) {
                val authResult = googleAuthManager.getDriveAuthorizationHeader()
                if (authResult is PgResult.Success) {
                    try { driveApiService.deleteFile(authResult.data, existing.driveFileId) } catch (_: Exception) {}
                }
            }

            if (existing.localFilePath.isNotBlank()) {
                try { File(existing.localFilePath).delete() } catch (_: Exception) {}
            }

            PgResult.Success(Unit)
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to delete media: ${e.localizedMessage}", e))
        }
    }

    override suspend fun fetchAndCacheDocumentIfMissing(cloudId: String): PgResult<File?> = withContext(Dispatchers.IO) {
        try {
            var media = tenantMediaDao.getMediaByCloudId(cloudId)
            if (media == null) {
                val allRes = firestoreRepository.getAllTenantMedia(currentOwnerId)
                if (allRes is PgResult.Success) {
                    val match = allRes.data.firstOrNull { it.cloudId == cloudId }
                    if (match != null) {
                        val entity = match.toEntity()
                        tenantMediaDao.insertOrUpdate(entity)
                        media = entity
                    }
                }
            }

            if (media == null || media.driveFileId.isBlank()) {
                return@withContext PgResult.Success(null)
            }

            val docsDir = File(context.cacheDir, "tenant_docs").apply { mkdirs() }
            val safeName = media.fileName.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim().ifBlank { "doc.pdf" }
            val targetFile = File(docsDir, "${media.cloudId}_${media.driveFileId}_$safeName")

            if (targetFile.exists() && targetFile.length() > 0) {
                if (media.localFilePath != targetFile.absolutePath) {
                    tenantMediaDao.updateLocalFilePathOnly(media.cloudId, targetFile.absolutePath)
                }
                return@withContext PgResult.Success(targetFile)
            }

            if (media.localFilePath.isNotBlank()) {
                val local = File(media.localFilePath)
                if (local.exists() && local.length() > 0) {
                    return@withContext PgResult.Success(local)
                }
            }

            val authResult = googleAuthManager.getDriveAuthorizationHeader()
            if (authResult is PgResult.Failure) {
                return@withContext PgResult.Failure(authResult.error)
            }
            val authHeader = (authResult as PgResult.Success).data

            val response = driveApiService.downloadFileMedia(authHeader, media.driveFileId)
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tenantMediaDao.updateLocalFilePathOnly(media.cloudId, targetFile.absolutePath)
                return@withContext PgResult.Success(targetFile)
            } else {
                return@withContext PgResult.Failure(
                    PgError.NetworkError("Failed to download document from Drive (${response.code()})")
                )
            }
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to download document: ${e.localizedMessage}", e))
        }
    }

    override suspend fun syncPendingMedia(cloudId: String): PgResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val media = tenantMediaDao.getMediaByCloudId(cloudId)
                ?: return@withContext PgResult.Success(Unit)

            if (media.deleted) {
                if (media.driveFileId.isNotBlank()) {
                    val authResult = googleAuthManager.getDriveAuthorizationHeader()
                    if (authResult is PgResult.Success) {
                        try { driveApiService.deleteFile(authResult.data, media.driveFileId) } catch (_: Exception) {}
                    }
                }
                firestoreRepository.deleteMediaMetadata(media.cloudId)
                return@withContext PgResult.Success(Unit)
            }

            var mediaToSave = media
            if (mediaToSave.driveFileId.isBlank() && mediaToSave.localFilePath.isNotBlank() && File(mediaToSave.localFilePath).exists()) {
                val authResult = googleAuthManager.getDriveAuthorizationHeader()
                if (authResult is PgResult.Failure) {
                    return@withContext PgResult.Failure(authResult.error)
                }
                val authHeader = (authResult as PgResult.Success).data
                val folders = resolveTenantFolders(authHeader, mediaToSave.propertyId, mediaToSave.tenantCloudId)
                val targetFolderId = if (mediaToSave.mediaType == "PROFILE_PHOTO") folders.photosFolderId else folders.docsFolderId
                val localFile = File(mediaToSave.localFilePath)

                val metadata = FileMetadataRequest(
                    name = mediaToSave.fileName.ifBlank { if (mediaToSave.mediaType == "PROFILE_PHOTO") "Profile Photo.jpg" else "document.pdf" },
                    mimeType = mediaToSave.mimeType,
                    parents = listOf(targetFolderId)
                )
                val metadataJson = moshi.adapter(FileMetadataRequest::class.java).toJson(metadata)
                val multipartBody = MultipartBody.Builder()
                    .setType("multipart/related".toMediaTypeOrNull() ?: MultipartBody.FORM)
                    .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                    .addPart(localFile.asRequestBody(mediaToSave.mimeType.toMediaTypeOrNull()))
                    .build()

                val uploadResponse = driveApiService.uploadFileMultipart(
                    authHeader = authHeader,
                    contentType = "multipart/related; boundary=${multipartBody.boundary}",
                    body = multipartBody
                )

                if (uploadResponse.isSuccessful && uploadResponse.body() != null) {
                    val newDriveFileId = uploadResponse.body()!!.id
                    if (mediaToSave.mediaType == "PROFILE_PHOTO") {
                        try {
                            driveApiService.createPermission(
                                authHeader = authHeader,
                                fileId = newDriveFileId,
                                request = com.example.features.tenants.data.api.CreatePermissionRequest(role = "reader", type = "anyone")
                            )
                        } catch (_: Exception) {}
                    }
                    mediaToSave = mediaToSave.copy(
                        driveFileId = newDriveFileId,
                        driveFolderId = targetFolderId,
                        driveTenantFolderId = folders.tenantFolderId,
                        drivePhotosFolderId = folders.photosFolderId
                    )
                    tenantMediaDao.insertOrUpdate(mediaToSave)
                } else {
                    return@withContext PgResult.Failure(PgError.NetworkError("Drive upload failed"))
                }
            }

            // Save to Firestore
            val res = firestoreRepository.saveMediaMetadata(mediaToSave.toDto())
            if (res is PgResult.Success) {
                tenantMediaDao.insertOrUpdate(
                    mediaToSave.copy(
                        syncStatus = "SYNCED",
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
                PgResult.Success(Unit)
            } else {
                res
            }
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to sync pending media: ${e.localizedMessage}", e))
        }
    }


    /**
     * Resolves the complete human-readable folder hierarchy:
     * PG Manager/
     * └── <Property Name>/
     *     └── Tenants/
     *         └── <Tenant Name> - <Last 4 Phone Digits> - <Short Tenant ID>/
     *             ├── Photos/
     *             └── Documents/
     */
    private suspend fun resolveTenantFolders(
        authHeader: String,
        propertyId: String,
        tenantCloudId: String
    ): TenantFolderStructure {
        // 0. Pre-check: Check if canonical media record in Room or Firestore already specifies valid driveTenantFolderId and drivePhotosFolderId
        val canonicalCloudId = "${tenantCloudId}_PROFILE_PHOTO"
        var mediaRecord = tenantMediaDao.getMediaByCloudId(canonicalCloudId)
            ?: tenantMediaDao.getProfilePhoto(tenantCloudId)
        if (mediaRecord == null) {
            val existingDoc = tenantMediaDao.getDocumentsForTenant(tenantCloudId).firstOrNull {
                it.driveTenantFolderId.isNotBlank() && it.drivePhotosFolderId.isNotBlank()
            }
            if (existingDoc != null) {
                mediaRecord = existingDoc
            }
        }
        if (mediaRecord == null) {
            val firestoreRes = firestoreRepository.getProfilePhotoMetadata(tenantCloudId)
            if (firestoreRes is PgResult.Success && firestoreRes.data != null) {
                mediaRecord = firestoreRes.data!!.toEntity().copy(cloudId = canonicalCloudId)
            }
        }

        if (mediaRecord != null && mediaRecord.driveTenantFolderId.isNotBlank() && mediaRecord.drivePhotosFolderId.isNotBlank()) {
            try {
                val checkTenant = driveApiService.getFile(authHeader, mediaRecord.driveTenantFolderId)
                val checkPhotos = driveApiService.getFile(authHeader, mediaRecord.drivePhotosFolderId)
                if (checkTenant.isSuccessful && checkTenant.body()?.trashed != true &&
                    checkPhotos.isSuccessful && checkPhotos.body()?.trashed != true) {
                    
                    val tenantKey = "TENANT_$tenantCloudId"
                    val photosKey = "TENANT_${tenantCloudId}_PHOTOS"
                    val docsKey = "TENANT_${tenantCloudId}_DOCS"

                    driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(tenantKey, mediaRecord.driveTenantFolderId))
                    driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(photosKey, mediaRecord.drivePhotosFolderId))
                    firestoreRepository.saveFolderMapping(tenantKey, mediaRecord.driveTenantFolderId)
                    firestoreRepository.saveFolderMapping(photosKey, mediaRecord.drivePhotosFolderId)

                    val docsFolderId = getOrCreateOrRenameFolder(
                        authHeader = authHeader,
                        mappingKey = docsKey,
                        desiredName = "Documents",
                        parentId = mediaRecord.driveTenantFolderId
                    )

                    Log.d("TenantMedia", "[resolveTenantFolders] FAST-PATH REUSED canonical folder IDs from tenant_media: tenantFolderId='${mediaRecord.driveTenantFolderId}', photosFolderId='${mediaRecord.drivePhotosFolderId}', docsFolderId='$docsFolderId'")
                    return TenantFolderStructure(
                        tenantFolderId = mediaRecord.driveTenantFolderId,
                        photosFolderId = mediaRecord.drivePhotosFolderId,
                        docsFolderId = docsFolderId
                    )
                }
            } catch (e: Exception) {
                Log.d("TenantMedia", "[resolveTenantFolders] Canonical folder verification error: ${e.message}, falling back to hierarchy resolution")
            }
        }

        // 1. Root: PG Manager
        val rootKey = "ROOT_PG_MANAGER"
        val rootFolderId = getOrCreateOrRenameFolder(
            authHeader = authHeader,
            mappingKey = rootKey,
            desiredName = "PG Manager",
            parentId = null
        )

        // 2. Property folder: <Property Name>
        val prop = propertyDao.getProperty(propertyId)
        val propertyDisplayName = sanitizeFolderName(prop?.propertyName ?: propertyId, propertyId)
        val propKey = "PROP_$propertyId"
        val propFolderId = getOrCreateOrRenameFolder(
            authHeader = authHeader,
            mappingKey = propKey,
            desiredName = propertyDisplayName,
            parentId = rootFolderId,
            legacyNames = listOf(propertyId)
        )

        // 3. Tenants folder: Tenants
        val tenantsKey = "PROP_${propertyId}_TENANTS"
        val tenantsFolderId = getOrCreateOrRenameFolder(
            authHeader = authHeader,
            mappingKey = tenantsKey,
            desiredName = "Tenants",
            parentId = propFolderId
        )

        // 4. Tenant specific folder: <Tenant Name> - <Last 4 Phone Digits> - <Short Tenant ID>
        val tenant = tenantDao.getTenantByCloudId(tenantCloudId)
        val tenantName = tenant?.name ?: "Tenant"
        val tenantPhone = tenant?.phone ?: ""
        val tenantFolderDesiredName = generateTenantFolderName(tenantName, tenantPhone, tenantCloudId)
        val tenantKey = "TENANT_$tenantCloudId"

        val tenantFolderId = getOrCreateOrRenameFolder(
            authHeader = authHeader,
            mappingKey = tenantKey,
            desiredName = tenantFolderDesiredName,
            parentId = tenantsFolderId,
            legacyNames = listOf(tenantCloudId)
        )

        // 5. Photos/ subfolder inside Tenant folder
        val photosKey = "TENANT_${tenantCloudId}_PHOTOS"
        val photosFolderId = getOrCreateOrRenameFolder(
            authHeader = authHeader,
            mappingKey = photosKey,
            desiredName = "Photos",
            parentId = tenantFolderId
        )

        // 6. Documents/ subfolder inside Tenant folder
        val docsKey = "TENANT_${tenantCloudId}_DOCS"
        val docsFolderId = getOrCreateOrRenameFolder(
            authHeader = authHeader,
            mappingKey = docsKey,
            desiredName = "Documents",
            parentId = tenantFolderId
        )

        // Ensure canonical media record has latest folder IDs in Room and Firestore
        val existingMedia = tenantMediaDao.getMediaByCloudId(canonicalCloudId)
        if (existingMedia != null && (existingMedia.driveTenantFolderId.isBlank() || existingMedia.drivePhotosFolderId.isBlank())) {
            val updated = existingMedia.copy(
                driveTenantFolderId = tenantFolderId,
                drivePhotosFolderId = photosFolderId,
                driveFolderId = photosFolderId
            )
            tenantMediaDao.insertOrUpdate(updated)
            firestoreRepository.saveMediaMetadata(updated.toDto())
        }

        return TenantFolderStructure(
            tenantFolderId = tenantFolderId,
            photosFolderId = photosFolderId,
            docsFolderId = docsFolderId
        )
    }

    /**
     * Finds, renames or creates a Google Drive folder.
     * Guarantees:
     * - Reuses existing folder IDs without creating duplicates.
     * - Preserves the ID when renaming (e.g. from legacy ID to human-readable name, or when name is edited).
     * - Syncs folder mappings across devices via Firestore.
     * - Safely recovers existing folders if Room cache is empty or ID is not found.
     */
    private suspend fun getOrCreateOrRenameFolder(
        authHeader: String,
        mappingKey: String,
        desiredName: String,
        parentId: String?,
        legacyNames: List<String> = emptyList()
    ): String = folderMutex.withLock {
        Log.d("TenantMedia", "[getOrCreateOrRenameFolder] LOOKUP START: mappingKey='$mappingKey', desiredName='$desiredName', parentId='$parentId'")

        // 1. Check local Room cache
        var folderId = driveFolderDao.getFolderMapping(mappingKey)?.driveFolderId
        var source = if (!folderId.isNullOrBlank()) "ROOM_CACHE" else ""

        // 2. Multi-device sync: If missing in local Room, check Firestore
        if (folderId.isNullOrBlank()) {
            val firestoreRes = firestoreRepository.getFolderMapping(mappingKey)
            if (firestoreRes is PgResult.Success && !firestoreRes.data.isNullOrBlank()) {
                folderId = firestoreRes.data
                source = "FIRESTORE_MAPPING"
                driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(mappingKey, folderId!!))
            }
        }

        // 3. If folder ID is known, verify in Google Drive and rename if needed
        if (!folderId.isNullOrBlank()) {
            try {
                val getResp = driveApiService.getFile(authHeader, folderId)
                if (getResp.isSuccessful && getResp.body() != null) {
                    val driveFile = getResp.body()!!
                    if (driveFile.trashed != true) {
                        // Folder exists and is active!
                        if (driveFile.name != desiredName) {
                            try {
                                driveApiService.updateFileMetadata(
                                    authHeader = authHeader,
                                    fileId = folderId,
                                    metadata = UpdateMetadataRequest(name = desiredName)
                                )
                                Log.d("TenantMedia", "[getOrCreateOrRenameFolder] RENAMED existing folder: id='$folderId', oldName='${driveFile.name}', newName='$desiredName'")
                            } catch (e: Exception) {
                                Log.w("TenantMedia", "[getOrCreateOrRenameFolder] Failed to rename folder '$folderId': ${e.message}")
                            }
                        }
                        Log.d("TenantMedia", "[getOrCreateOrRenameFolder] RESOLVED via $source: mappingKey='$mappingKey', folderId='$folderId', name='$desiredName'")
                        return folderId
                    }
                }
            } catch (e: Exception) {
                Log.w("TenantMedia", "[getOrCreateOrRenameFolder] Verification of cached folderId '$folderId' failed: ${e.message}")
            }
        }

        // 4. Safe recovery: Search Google Drive under parentId before creating
        val parentClause = if (parentId != null) "'$parentId' in parents" else "'root' in parents"
        val candidateNames = mutableListOf(desiredName)
        for (legacy in legacyNames) {
            if (legacy.isNotBlank() && legacy != desiredName && !candidateNames.contains(legacy)) {
                candidateNames.add(legacy)
            }
        }

        for (candidate in candidateNames) {
            val query = "name = '$candidate' and $parentClause and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            try {
                val searchResponse = driveApiService.listFiles(
                    authHeader = authHeader,
                    query = query,
                    pageSize = 5
                )

                if (searchResponse.isSuccessful && searchResponse.body() != null) {
                    val existing = searchResponse.body()!!.files.firstOrNull()
                    if (existing != null && existing.id.isNotBlank()) {
                        if (existing.name != desiredName) {
                            try {
                                driveApiService.updateFileMetadata(
                                    authHeader = authHeader,
                                    fileId = existing.id,
                                    metadata = UpdateMetadataRequest(name = desiredName)
                                )
                            } catch (_: Exception) {}
                        }

                        // Persist to Room & Firestore
                        driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(mappingKey, existing.id))
                        firestoreRepository.saveFolderMapping(mappingKey, existing.id)
                        Log.d("TenantMedia", "[getOrCreateOrRenameFolder] RESOLVED via DRIVE_SEARCH: mappingKey='$mappingKey', folderId='${existing.id}', name='$desiredName'")
                        return existing.id
                    }
                }
            } catch (e: Exception) {
                Log.w("TenantMedia", "[getOrCreateOrRenameFolder] Drive search error for candidate '$candidate': ${e.message}")
            }
        }

        // 4b. Deep recovery: List all subfolders under parentId and match by tenant identifier / shortId
        try {
            val listAllQuery = "$parentClause and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val listResp = driveApiService.listFiles(authHeader = authHeader, query = listAllQuery, pageSize = 100)
            if (listResp.isSuccessful && listResp.body() != null) {
                val keyIdentifier = if (mappingKey.startsWith("TENANT_")) {
                    mappingKey.removePrefix("TENANT_").removeSuffix("_PHOTOS").removeSuffix("_DOCS")
                } else ""

                val shortIdTail = if (keyIdentifier.length >= 4) keyIdentifier.takeLast(4).uppercase() else ""

                val matched = listResp.body()!!.files.firstOrNull { folder ->
                    folder.name.equals(desiredName, ignoreCase = true) ||
                    (keyIdentifier.isNotBlank() && folder.name.contains(keyIdentifier, ignoreCase = true)) ||
                    (shortIdTail.isNotBlank() && folder.name.uppercase().contains(shortIdTail)) ||
                    legacyNames.any { legacy -> legacy.isNotBlank() && folder.name.contains(legacy, ignoreCase = true) }
                }

                if (matched != null && matched.id.isNotBlank()) {
                    if (matched.name != desiredName) {
                        try {
                            driveApiService.updateFileMetadata(
                                authHeader = authHeader,
                                fileId = matched.id,
                                metadata = UpdateMetadataRequest(name = desiredName)
                            )
                        } catch (_: Exception) {}
                    }
                    driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(mappingKey, matched.id))
                    firestoreRepository.saveFolderMapping(mappingKey, matched.id)
                    Log.d("TenantMedia", "[getOrCreateOrRenameFolder] RESOLVED via DEEP_MATCH: mappingKey='$mappingKey', folderId='${matched.id}', matchedName='${matched.name}', desiredName='$desiredName'")
                    return matched.id
                }
            }
        } catch (e: Exception) {
            Log.w("TenantMedia", "[getOrCreateOrRenameFolder] Deep recovery search error: ${e.message}")
        }

        // 4c. Pre-creation server check: Check Firestore right before creating in case another device finished during our Drive search
        val secondFirestoreCheck = firestoreRepository.getFolderMapping(mappingKey)
        if (secondFirestoreCheck is PgResult.Success && !secondFirestoreCheck.data.isNullOrBlank()) {
            val existingFolderId = secondFirestoreCheck.data!!
            try {
                val verifyResp = driveApiService.getFile(authHeader, existingFolderId)
                if (verifyResp.isSuccessful && verifyResp.body()?.trashed != true) {
                    driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(mappingKey, existingFolderId))
                    Log.d("TenantMedia", "[getOrCreateOrRenameFolder] RESOLVED via PRE_CREATE_CHECK: mappingKey='$mappingKey', folderId='$existingFolderId'")
                    return existingFolderId
                }
            } catch (_: Exception) {}
        }

        // 5. Create folder only if neither cached nor found in Drive
        val createRequest = CreateFolderRequest(
            name = desiredName,
            parents = if (parentId != null) listOf(parentId) else listOf("root")
        )
        val createResponse = driveApiService.createFolder(
            authHeader = authHeader,
            request = createRequest
        )

        if (createResponse.isSuccessful && createResponse.body() != null) {
            val candidateFolderId = createResponse.body()!!.id

            // Post-creation race resolution:
            // Check if another device published a folder mapping to Firestore during our Drive create call
            val postCheck = firestoreRepository.getFolderMapping(mappingKey)
            val finalFolderId = if (postCheck is PgResult.Success && !postCheck.data.isNullOrBlank() && postCheck.data != candidateFolderId) {
                val winningFolderId = postCheck.data!!
                Log.w("TenantMedia", "[getOrCreateOrRenameFolder] RACE DETECTED on '$mappingKey': remote winner '$winningFolderId'. Cleaning up redundant folder '$candidateFolderId'")
                try {
                    driveApiService.deleteFile(authHeader, candidateFolderId)
                } catch (delErr: Exception) {
                    Log.w("TenantMedia", "Failed to delete redundant folder '$candidateFolderId': ${delErr.message}")
                }
                winningFolderId
            } else {
                firestoreRepository.saveFolderMapping(mappingKey, candidateFolderId)
                candidateFolderId
            }

            driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(mappingKey, finalFolderId))
            Log.d("TenantMedia", "[getOrCreateOrRenameFolder] RESOLVED via CREATED_NEW: mappingKey='$mappingKey', finalFolderId='$finalFolderId', name='$desiredName'")
            return finalFolderId
        }

        throw IllegalStateException("Failed to find or create Google Drive folder: $desiredName")
    }

    private fun generateTenantFolderName(tenantName: String, phone: String?, tenantCloudId: String): String {
        val cleanName = tenantName
            .replace(Regex("[/\\\\:*?\"<>|]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Tenant" }

        val digitsOnly = (phone ?: "").filter { it.isDigit() }
        val last4Phone = if (digitsOnly.length >= 4) digitsOnly.takeLast(4) else null

        // Deterministic short ID: 4 characters (e.g. T7A4)
        val rawIdAlnum = tenantCloudId.filter { it.isLetterOrDigit() }.uppercase()
        val shortId = if (rawIdAlnum.length >= 4) {
            val tail = rawIdAlnum.takeLast(4)
            if (tail.startsWith("T")) tail else "T" + tail.takeLast(3)
        } else {
            val hash = (tenantCloudId.hashCode() and 0xFFF).toString(16).uppercase().padStart(3, '0')
            "T$hash"
        }

        return if (last4Phone != null) {
            "$cleanName - $last4Phone - $shortId"
        } else {
            "$cleanName - $shortId"
        }
    }

    private fun sanitizeFolderName(name: String, fallback: String): String {
        return name
            .replace(Regex("[/\\\\:*?\"<>|]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { fallback }
    }

    private fun TenantMediaEntity.toDomain(): TenantMedia = TenantMedia(
        cloudId = cloudId,
        ownerId = ownerId,
        propertyId = propertyId,
        tenantCloudId = tenantCloudId,
        driveFileId = driveFileId,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        mediaType = mediaType,
        status = status,
        localFilePath = localFilePath,
        driveFolderId = driveFolderId.ifBlank { drivePhotosFolderId },
        driveTenantFolderId = driveTenantFolderId,
        drivePhotosFolderId = drivePhotosFolderId.ifBlank { driveFolderId },
        fileUrl = fileUrl.ifBlank { if (driveFileId.isNotBlank()) "https://drive.google.com/uc?export=view&id=$driveFileId" else "" },
        verificationStatus = verificationStatus,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun TenantMediaEntity.toDto(): TenantMediaDto {
        val authUid = auth.currentUser?.uid ?: ""
        val finalOwnerId = if (ownerId.isNotBlank() && ownerId != "default_owner") ownerId else authUid
        return TenantMediaDto(
            cloudId = cloudId,
            ownerId = finalOwnerId,
            propertyId = propertyId,
            tenantCloudId = tenantCloudId,
        driveFileId = driveFileId,
        fileUrl = fileUrl.ifBlank { if (driveFileId.isNotBlank()) "https://drive.google.com/uc?export=view&id=$driveFileId" else "" },
        driveTenantFolderId = driveTenantFolderId,
        drivePhotosFolderId = drivePhotosFolderId.ifBlank { driveFolderId },
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        mediaType = mediaType,
        status = status,
        verificationStatus = verificationStatus,
        driveFolderId = driveFolderId.ifBlank { drivePhotosFolderId },
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        lastModifiedByDeviceId = lastModifiedByDeviceId
        )
    }

    private fun TenantMediaDto.toEntity(): TenantMediaEntity = TenantMediaEntity(
        cloudId = cloudId,
        ownerId = ownerId,
        propertyId = propertyId,
        tenantCloudId = tenantCloudId,
        driveFileId = driveFileId,
        fileUrl = fileUrl.ifBlank { if (driveFileId.isNotBlank()) "https://drive.google.com/uc?export=view&id=$driveFileId" else "" },
        driveTenantFolderId = driveTenantFolderId,
        drivePhotosFolderId = drivePhotosFolderId.ifBlank { driveFolderId },
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        mediaType = mediaType,
        status = status,
        verificationStatus = verificationStatus,
        localFilePath = "",
        driveFolderId = driveFolderId.ifBlank { drivePhotosFolderId },
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        syncStatus = "SYNCED",
        lastSyncedAt = System.currentTimeMillis(),
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
}
