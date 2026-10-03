package com.example.features.tenants.domain.model

data class TenantMedia(
    val cloudId: String,
    val ownerId: String,
    val propertyId: String,
    val tenantCloudId: String,
    val driveFileId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val mediaType: String, // PROFILE_PHOTO
    val status: String, // ACTIVE, DELETED
    val localFilePath: String,
    val driveFolderId: String,
    val driveTenantFolderId: String = "",
    val drivePhotosFolderId: String = "",
    val createdAt: Long,
    val updatedAt: Long
) {
    val hasLocalFile: Boolean
        get() = localFilePath.isNotBlank() && java.io.File(localFilePath).exists()
}

sealed interface UploadPhotoProgress {
    object Idle : UploadPhotoProgress
    data class ProcessingImage(val stage: String) : UploadPhotoProgress
    data class UploadingToDrive(val percent: Int = 0) : UploadPhotoProgress
    data class Success(val media: TenantMedia) : UploadPhotoProgress
    data class Error(val message: String, val isAuthError: Boolean = false) : UploadPhotoProgress
}
