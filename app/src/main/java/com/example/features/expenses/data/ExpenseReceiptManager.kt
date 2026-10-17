package com.example.features.expenses.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.database.DriveFolderDao
import com.example.data.database.DriveFolderMappingEntity
import com.example.data.database.PropertyDao
import com.example.data.firestore.repository.FirestoreTenantMediaRepository
import com.example.features.expenses.domain.model.ExpenseAttachment
import com.example.features.tenants.data.api.CreateFolderRequest
import com.example.features.tenants.data.api.FileMetadataRequest
import com.example.features.tenants.data.api.GoogleDriveApiService
import com.example.features.googleform.data.auth.GoogleFormsAuthManager
import com.example.features.properties.data.CurrentPropertyManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseReceiptManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val googleAuthManager: GoogleFormsAuthManager,
    private val driveApiService: GoogleDriveApiService,
    private val driveFolderDao: DriveFolderDao,
    private val propertyDao: PropertyDao,
    private val currentPropertyManager: CurrentPropertyManager,
    private val firestoreRepository: FirestoreTenantMediaRepository
) {
    companion object {
        private const val TAG = "ExpenseReceiptManager"
        const val FOLDER_NAME = "Expense Receipts"
    }

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val receiptsDir: File
        get() {
            val dir = File(context.filesDir, "expense_receipts")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * Copies a user-selected Uri (image or PDF) into app-private internal storage,
     * generates a standardized filename, and returns a new ExpenseAttachment metadata object.
     */
    suspend fun prepareLocalAttachment(
        uri: Uri,
        category: String,
        mimeTypeInput: String? = null,
        source: String = "UPLOAD"
    ): PgResult<ExpenseAttachment> = withContext(Dispatchers.IO) {
        try {
            val resolvedMimeType = mimeTypeInput ?: context.contentResolver.getType(uri) ?: "application/pdf"
            val extension = when {
                resolvedMimeType.contains("pdf", ignoreCase = true) -> "pdf"
                resolvedMimeType.contains("png", ignoreCase = true) -> "png"
                resolvedMimeType.contains("jpeg", ignoreCase = true) || resolvedMimeType.contains("jpg", ignoreCase = true) -> "jpg"
                else -> "pdf"
            }

            val sanitizedCategory = category.trim().replace(Regex("[^a-zA-Z0-9]"), "_").take(24).ifBlank { "Expense" }
            val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val targetFileName = "${sanitizedCategory}_${timeStamp}.${extension}"

            val targetFile = File(receiptsDir, targetFileName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext PgResult.Failure(PgError.UnknownError("Failed to open input stream for URI: $uri"))

            val currentPropId = currentPropertyManager.getCurrentPropertyId()
            val ownerId = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }

            val attachment = ExpenseAttachment(
                id = UUID.randomUUID().toString(),
                propertyId = currentPropId,
                ownerId = ownerId,
                fileName = targetFileName,
                mimeType = resolvedMimeType,
                fileSize = targetFile.length(),
                uploadStatus = "PENDING_UPLOAD",
                localFilePath = targetFile.absolutePath,
                createdAt = System.currentTimeMillis(),
                source = source
            )

            Log.i(TAG, "Prepared local attachment: ${attachment.fileName}, size: ${attachment.fileSize} bytes")
            PgResult.Success(attachment)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing local attachment: ${e.message}", e)
            PgResult.Failure(PgError.UnknownError("Failed to save attachment locally", e))
        }
    }

    /**
     * Resolves or creates the dedicated 'Expense Receipts' Google Drive folder.
     */
    suspend fun resolveExpenseReceiptsFolder(authHeader: String, propertyId: String): String? = withContext(Dispatchers.IO) {
        val mappingKey = "PROP_${propertyId}_EXPENSE_RECEIPTS"

        // 1. Check Room cache
        var folderId = driveFolderDao.getFolderMapping(mappingKey)?.driveFolderId

        // 2. Multi-device sync: Check Firestore mapping if missing in local Room
        if (folderId.isNullOrBlank()) {
            val firestoreRes = firestoreRepository.getFolderMapping(mappingKey)
            if (firestoreRes is PgResult.Success && !firestoreRes.data.isNullOrBlank()) {
                folderId = firestoreRes.data
                driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(mappingKey, folderId!!))
            }
        }

        // 3. Verify cached folderId in Drive
        if (!folderId.isNullOrBlank()) {
            try {
                val check = driveApiService.getFile(authHeader, folderId)
                if (check.isSuccessful && check.body()?.trashed != true) {
                    return@withContext folderId
                }
            } catch (e: Exception) {
                Log.w(TAG, "Verification of cached folder $folderId failed: ${e.message}")
            }
        }

        // 4. Search Drive under root
        try {
            val query = "name = '$FOLDER_NAME' and 'root' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val searchResp = driveApiService.listFiles(authHeader, query = query, pageSize = 5)
            if (searchResp.isSuccessful) {
                val existing = searchResp.body()?.files?.firstOrNull()
                if (existing != null && existing.id.isNotBlank()) {
                    driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(mappingKey, existing.id))
                    firestoreRepository.saveFolderMapping(mappingKey, existing.id)
                    return@withContext existing.id
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error searching for Expense Receipts folder in Drive: ${e.message}")
        }

        // 5. Create 'Expense Receipts' folder if not found
        try {
            val createReq = CreateFolderRequest(name = FOLDER_NAME, parents = listOf("root"))
            val createResp = driveApiService.createFolder(authHeader, createReq)
            if (createResp.isSuccessful && createResp.body() != null) {
                val newFolderId = createResp.body()!!.id
                driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(mappingKey, newFolderId))
                firestoreRepository.saveFolderMapping(mappingKey, newFolderId)
                Log.i(TAG, "Created new 'Expense Receipts' folder in Drive: $newFolderId")
                return@withContext newFolderId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create 'Expense Receipts' folder: ${e.message}")
        }

        null
    }

    /**
     * Uploads an ExpenseAttachment file to Google Drive in the 'Expense Receipts' folder.
     */
    suspend fun uploadReceiptToDrive(
        attachment: ExpenseAttachment,
        propertyId: String,
        expenseCategory: String,
        expenseDateStr: String
    ): PgResult<ExpenseAttachment> = withContext(Dispatchers.IO) {
        val file = File(attachment.localFilePath)
        if (!file.exists()) {
            val updated = attachment.copy(
                uploadStatus = "FAILED",
                lastError = "Local file not found at ${attachment.localFilePath}"
            )
            return@withContext PgResult.Failure(PgError.UnknownError("Local receipt file missing", Exception("File not found")))
        }

        val authResult = googleAuthManager.getDriveAuthorizationHeader()
        if (authResult is PgResult.Failure) {
            val updated = attachment.copy(
                uploadStatus = "FAILED",
                lastError = authResult.error.message
            )
            return@withContext PgResult.Failure(authResult.error)
        }
        val authHeader = (authResult as PgResult.Success).data

        val folderId = resolveExpenseReceiptsFolder(authHeader, propertyId)
            ?: return@withContext PgResult.Failure(PgError.NetworkError("Failed to resolve Expense Receipts folder on Google Drive"))

        try {
            // Prepare file name if missing
            val cleanCategory = expenseCategory.trim().replace(Regex("[^a-zA-Z0-9]"), "_").take(24).ifBlank { "Expense" }
            val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val extension = if (attachment.mimeType.contains("pdf", true)) "pdf" else "jpg"
            val targetFileName = if (attachment.fileName.isNotBlank()) attachment.fileName else "${cleanCategory}_${timeStamp}.${extension}"

            val metadata = FileMetadataRequest(
                name = targetFileName,
                mimeType = attachment.mimeType,
                parents = listOf(folderId)
            )

            val metadataJson = moshi.adapter(FileMetadataRequest::class.java).toJson(metadata)

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaTypeOrNull() ?: MultipartBody.FORM)
                .addPart(
                    metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull())
                )
                .addPart(
                    file.asRequestBody(attachment.mimeType.toMediaTypeOrNull())
                )
                .build()

            val response = driveApiService.uploadFileMultipart(
                authHeader = authHeader,
                contentType = "multipart/related; boundary=${multipartBody.boundary}",
                body = multipartBody
            )

            if (response.isSuccessful && response.body() != null) {
                val driveItem = response.body()!!
                val updatedAttachment = attachment.copy(
                    driveFileId = driveItem.id,
                    fileName = targetFileName,
                    fileSize = file.length(),
                    uploadStatus = "UPLOADED",
                    lastError = null
                )
                Log.i(TAG, "Receipt uploaded successfully to Drive: fileId=${driveItem.id}, name=$targetFileName")
                PgResult.Success(updatedAttachment)
            } else {
                val errMsg = "Drive upload failed with HTTP code ${response.code()}"
                Log.w(TAG, errMsg)
                val updated = attachment.copy(uploadStatus = "FAILED", lastError = errMsg)
                PgResult.Failure(PgError.NetworkError(errMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Drive receipt upload: ${e.message}", e)
            PgResult.Failure(PgError.UnknownError("Drive receipt upload error", e))
        }
    }

    /**
     * Downloads a receipt media file from Google Drive if not locally present.
     */
    suspend fun downloadReceiptFile(driveFileId: String, fileName: String): PgResult<File> = withContext(Dispatchers.IO) {
        val localFile = File(receiptsDir, fileName)
        if (localFile.exists() && localFile.length() > 0) {
            return@withContext PgResult.Success(localFile)
        }

        if (driveFileId.isBlank()) {
            return@withContext PgResult.Failure(PgError.UnknownError("Drive file ID is empty"))
        }

        val authResult = googleAuthManager.getDriveAuthorizationHeader()
        if (authResult is PgResult.Failure) {
            return@withContext PgResult.Failure(authResult.error)
        }
        val authHeader = (authResult as PgResult.Success).data

        try {
            val response = driveApiService.downloadFileMedia(authHeader, driveFileId)
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.byteStream().use { inputStream ->
                    FileOutputStream(localFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.i(TAG, "Downloaded receipt from Drive to $localFile")
                PgResult.Success(localFile)
            } else {
                PgResult.Failure(PgError.NetworkError("Failed to download file from Drive (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Download error", e))
        }
    }
}
