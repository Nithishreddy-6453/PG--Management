package com.example.features.tenants.domain.repository

import android.net.Uri
import com.example.core.common.PgResult
import com.example.features.tenants.domain.model.TenantMedia
import kotlinx.coroutines.flow.Flow
import java.io.File

interface TenantMediaRepository {
    fun getProfilePhotoFlow(tenantCloudId: String): Flow<TenantMedia?>
    suspend fun getProfilePhoto(tenantCloudId: String): TenantMedia?
    fun getDocumentsFlow(tenantCloudId: String): Flow<List<TenantMedia>>
    suspend fun getDocuments(tenantCloudId: String): List<TenantMedia>

    suspend fun uploadProfilePhoto(
        propertyId: String,
        tenantCloudId: String,
        imageUri: Uri
    ): PgResult<TenantMedia>

    suspend fun uploadDocument(
        propertyId: String,
        tenantCloudId: String,
        documentUri: Uri,
        fileName: String,
        mimeType: String
    ): PgResult<TenantMedia>

    suspend fun queueDocumentForUpload(
        propertyId: String,
        tenantCloudId: String,
        documentUri: Uri,
        fileName: String,
        mimeType: String
    ): PgResult<TenantMedia>

    suspend fun queueProfilePhotoForUpload(
        propertyId: String,
        tenantCloudId: String,
        imageUri: Uri
    ): PgResult<TenantMedia>

    suspend fun deleteProfilePhoto(tenantCloudId: String): PgResult<Unit>
    suspend fun deleteMedia(cloudId: String): PgResult<Unit>
    suspend fun fetchAndCachePhotoIfMissing(tenantCloudId: String): PgResult<File?>
    suspend fun fetchAndCacheDocumentIfMissing(cloudId: String): PgResult<File?>
    suspend fun syncPendingMedia(cloudId: String): PgResult<Unit>
    suspend fun syncTenantDriveFolder(tenantCloudId: String): PgResult<Unit>
}

