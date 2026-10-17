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
    val fileUrl: String = "",
    val verificationStatus: String = "VERIFIED",
    val createdAt: Long,
    val updatedAt: Long
) {
    val hasLocalFile: Boolean
        get() = localFilePath.isNotBlank() && java.io.File(localFilePath).exists()

    val isDocument: Boolean
        get() = mediaType != "PROFILE_PHOTO"

    val formattedSize: String
        get() = when {
            sizeBytes <= 0 -> "0 B"
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", sizeBytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
        }
}

enum class DocumentUploadStatus {
    READY,
    UPLOADING,
    UPLOADED,
    PENDING_OFFLINE,
    FAILED
}

data class PendingDocumentItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: android.net.Uri,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: DocumentUploadStatus = DocumentUploadStatus.READY,
    val localFilePath: String = "",
    val driveFileId: String = "",
    val error: String? = null
) {
    val formattedSize: String
        get() = when {
            sizeBytes <= 0 -> "0 B"
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", sizeBytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
        }
}

sealed interface UploadPhotoProgress {
    object Idle : UploadPhotoProgress
    data class ProcessingImage(val stage: String) : UploadPhotoProgress
    data class UploadingToDrive(val percent: Int = 0) : UploadPhotoProgress
    data class Success(val media: TenantMedia) : UploadPhotoProgress
    data class Error(val message: String, val isAuthError: Boolean = false) : UploadPhotoProgress
}

