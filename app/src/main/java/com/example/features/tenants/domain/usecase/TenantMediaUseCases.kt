package com.example.features.tenants.domain.usecase

import android.net.Uri
import com.example.core.common.PgResult
import com.example.features.tenants.domain.model.TenantMedia
import com.example.features.tenants.domain.repository.TenantMediaRepository
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject

class GetTenantProfilePhotoUseCase @Inject constructor(
    private val repository: TenantMediaRepository
) {
    operator fun invoke(tenantCloudId: String): Flow<TenantMedia?> {
        return repository.getProfilePhotoFlow(tenantCloudId)
    }

    suspend fun getDirect(tenantCloudId: String): TenantMedia? {
        return repository.getProfilePhoto(tenantCloudId)
    }
}

class UploadTenantProfilePhotoUseCase @Inject constructor(
    private val repository: TenantMediaRepository
) {
    suspend operator fun invoke(
        propertyId: String,
        tenantCloudId: String,
        imageUri: Uri
    ): PgResult<TenantMedia> {
        return repository.uploadProfilePhoto(propertyId, tenantCloudId, imageUri)
    }
}

class DeleteTenantProfilePhotoUseCase @Inject constructor(
    private val repository: TenantMediaRepository
) {
    suspend operator fun invoke(tenantCloudId: String): PgResult<Unit> {
        return repository.deleteProfilePhoto(tenantCloudId)
    }
}

class FetchAndCacheProfilePhotoUseCase @Inject constructor(
    private val repository: TenantMediaRepository
) {
    suspend operator fun invoke(tenantCloudId: String): PgResult<File?> {
        return repository.fetchAndCachePhotoIfMissing(tenantCloudId)
    }
}

class GetTenantDocumentsUseCase @Inject constructor(
    private val repository: TenantMediaRepository
) {
    operator fun invoke(tenantCloudId: String): Flow<List<TenantMedia>> {
        return repository.getDocumentsFlow(tenantCloudId)
    }

    suspend fun getDirect(tenantCloudId: String): List<TenantMedia> {
        return repository.getDocuments(tenantCloudId)
    }
}

class UploadTenantDocumentUseCase @Inject constructor(
    private val repository: TenantMediaRepository
) {
    suspend operator fun invoke(
        propertyId: String,
        tenantCloudId: String,
        documentUri: Uri,
        fileName: String,
        mimeType: String
    ): PgResult<TenantMedia> {
        return repository.uploadDocument(propertyId, tenantCloudId, documentUri, fileName, mimeType)
    }
}

class QueueDocumentUploadUseCase @Inject constructor(
    private val repository: TenantMediaRepository
) {
    suspend operator fun invoke(
        propertyId: String,
        tenantCloudId: String,
        documentUri: Uri,
        fileName: String,
        mimeType: String
    ): PgResult<TenantMedia> {
        return repository.queueDocumentForUpload(propertyId, tenantCloudId, documentUri, fileName, mimeType)
    }
}

class QueueProfilePhotoUploadUseCase @Inject constructor(
    private val repository: TenantMediaRepository
) {
    suspend operator fun invoke(
        propertyId: String,
        tenantCloudId: String,
        imageUri: Uri
    ): PgResult<TenantMedia> {
        return repository.queueProfilePhotoForUpload(propertyId, tenantCloudId, imageUri)
    }
}

class DeleteTenantMediaUseCase @Inject constructor(
    private val repository: TenantMediaRepository
) {
    suspend operator fun invoke(cloudId: String): PgResult<Unit> {
        return repository.deleteMedia(cloudId)
    }
}

class FetchAndCacheDocumentUseCase @Inject constructor(
    private val repository: TenantMediaRepository
) {
    suspend operator fun invoke(cloudId: String): PgResult<File?> {
        return repository.fetchAndCacheDocumentIfMissing(cloudId)
    }
}

class SyncTenantDriveFolderUseCase @Inject constructor(
    private val repository: TenantMediaRepository
) {
    suspend operator fun invoke(tenantCloudId: String): PgResult<Unit> {
        return repository.syncTenantDriveFolder(tenantCloudId)
    }
}

