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

class SyncTenantDriveFolderUseCase @Inject constructor(
    private val repository: TenantMediaRepository
) {
    suspend operator fun invoke(tenantCloudId: String): PgResult<Unit> {
        return repository.syncTenantDriveFolder(tenantCloudId)
    }
}
