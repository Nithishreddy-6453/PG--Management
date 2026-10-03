package com.example.features.tenants.data.repository

import android.content.Context
import android.net.Uri
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
    private val deviceIdentityManager: DeviceIdentityManager
) : TenantMediaRepository {

    private val currentOwnerId: String
        get() = auth.currentUser?.uid ?: ""

    override fun getProfilePhotoFlow(tenantCloudId: String): Flow<TenantMedia?> {
        return tenantMediaDao.getProfilePhotoFlow(tenantCloudId).map { it?.toDomain() }
    }

    override suspend fun getProfilePhoto(tenantCloudId: String): TenantMedia? {
        return tenantMediaDao.getProfilePhoto(tenantCloudId)?.toDomain()
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
            val effectiveOwnerId = if (currentOwnerId.isNotBlank()) currentOwnerId
                else tenantDao.getTenantByCloudId(tenantCloudId)?.ownerId
                ?: propertyDao.getProperty(propertyId)?.ownerId
                ?: "default_owner"
            val myDeviceId = deviceIdentityManager.getDeviceId()

            val entity = TenantMediaEntity(
                cloudId = canonicalCloudId,
                ownerId = effectiveOwnerId,
                propertyId = propertyId,
                tenantCloudId = tenantCloudId,
                driveFileId = newDriveFileId,
                driveTenantFolderId = folders.tenantFolderId,
                drivePhotosFolderId = folders.photosFolderId,
                fileName = driveFile.name.ifBlank { "Profile Photo.jpg" },
                mimeType = "image/jpeg",
                sizeBytes = processed.sizeBytes,
                mediaType = "PROFILE_PHOTO",
                status = "ACTIVE",
                localFilePath = processed.file.absolutePath,
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
            firestoreRepository.saveMediaMetadata(dto)
            firestoreRepository.cleanUpLegacyMediaDocuments(tenantCloudId, canonicalCloudId)

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

            // Check if Room has record, else check Firestore
            if (media == null) {
                val firestoreRes = firestoreRepository.getProfilePhotoMetadata(tenantCloudId)
                if (firestoreRes is PgResult.Success && firestoreRes.data != null) {
                    val dto = firestoreRes.data!!
                    val entity = dto.toEntity().copy(cloudId = canonicalCloudId)
                    tenantMediaDao.deleteNonCanonicalProfilePhotos(tenantCloudId, canonicalCloudId)
                    tenantMediaDao.insertOrUpdate(entity)
                    media = entity
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
                    tenantMediaDao.updateLocalFilePath(media.cloudId, targetFile.absolutePath)
                }
                return@withContext PgResult.Success(targetFile)
            }

            // 2. If entity's current localFilePath exists and matches, return it
            if (media.localFilePath.isNotBlank()) {
                val localFile = File(media.localFilePath)
                if (localFile.exists() && localFile.length() > 0) {
                    return@withContext PgResult.Success(localFile)
                }
            }

            // 3. Otherwise download from Google Drive
            val authResult = googleAuthManager.getDriveAuthorizationHeader()
            if (authResult is PgResult.Failure) {
                return@withContext PgResult.Failure(authResult.error)
            }
            val authHeader = (authResult as PgResult.Success).data

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

                tenantMediaDao.updateLocalFilePath(media.cloudId, targetFile.absolutePath)
                return@withContext PgResult.Success(targetFile)
            } else {
                val errCode = response.code()
                val errBody = response.errorBody()?.string() ?: ""
                return@withContext PgResult.Failure(
                    PgError.NetworkError("Failed to download photo from Google Drive ($errCode): $errBody")
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
            resolveTenantFolders(authResult.data, tenant.propertyId, tenantCloudId)
            PgResult.Success(Unit)
        } catch (e: Exception) {
            PgResult.Success(Unit)
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
        val canonicalCloudId = "${tenantCloudId}_PROFILE_PHOTO"
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
    ): String {
        // 1. Check local Room cache
        var folderId = driveFolderDao.getFolderMapping(mappingKey)?.driveFolderId

        // 2. Multi-device sync: If missing in local Room, check Firestore
        if (folderId.isNullOrBlank()) {
            val firestoreRes = firestoreRepository.getFolderMapping(mappingKey)
            if (firestoreRes is PgResult.Success && !firestoreRes.data.isNullOrBlank()) {
                folderId = firestoreRes.data
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
                            } catch (_: Exception) {}
                        }
                        return folderId
                    }
                }
            } catch (_: Exception) {
                // If getFile failed, proceed to search & recovery
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
                        // Found existing folder in Drive!
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
                        return existing.id
                    }
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
            val newFolderId = createResponse.body()!!.id
            driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(mappingKey, newFolderId))
            firestoreRepository.saveFolderMapping(mappingKey, newFolderId)
            return newFolderId
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
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun TenantMediaEntity.toDto(): TenantMediaDto = TenantMediaDto(
        cloudId = cloudId,
        ownerId = ownerId,
        propertyId = propertyId,
        tenantCloudId = tenantCloudId,
        driveFileId = driveFileId,
        driveTenantFolderId = driveTenantFolderId,
        drivePhotosFolderId = drivePhotosFolderId.ifBlank { driveFolderId },
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        mediaType = mediaType,
        status = status,
        driveFolderId = driveFolderId.ifBlank { drivePhotosFolderId },
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )

    private fun TenantMediaDto.toEntity(): TenantMediaEntity = TenantMediaEntity(
        cloudId = cloudId,
        ownerId = ownerId,
        propertyId = propertyId,
        tenantCloudId = tenantCloudId,
        driveFileId = driveFileId,
        driveTenantFolderId = driveTenantFolderId,
        drivePhotosFolderId = drivePhotosFolderId.ifBlank { driveFolderId },
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        mediaType = mediaType,
        status = status,
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
