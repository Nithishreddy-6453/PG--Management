package com.example.features.excel.data

import android.content.Context
import com.example.core.common.PgLogger
import com.example.core.common.PgResult
import com.example.data.database.DriveFolderDao
import com.example.data.database.DriveFolderMappingEntity
import com.example.data.database.PropertyDao
import com.example.features.excel.domain.model.ExcelExportConfig
import com.example.features.excel.domain.model.ExportPeriodType
import com.example.features.googleform.data.auth.GoogleFormsAuthManager
import com.example.features.tenants.data.api.CreateFolderRequest
import com.example.features.tenants.data.api.FileMetadataRequest
import com.example.features.tenants.data.api.GoogleDriveApiService
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcelDriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val excelManager: ExcelManager,
    private val driveApiService: GoogleDriveApiService,
    private val driveFolderDao: DriveFolderDao,
    private val propertyDao: PropertyDao,
    private val googleAuthManager: GoogleFormsAuthManager,
    private val moshi: Moshi,
    private val logger: PgLogger
) {
    private val TAG = "ExcelDriveBackupManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Triggers an asynchronous backup of the property's master Excel ledger to Google Drive.
     * Safe to call from any repository/use-case without blocking callers.
     */
    fun triggerAsyncBackup(propertyId: String, reason: String = "financial_update") {
        scope.launch {
            try {
                backupPropertyLedgerToDrive(propertyId)
            } catch (e: Exception) {
                logger.w(TAG, "Async Excel backup deferred: ${e.message}")
            }
        }
    }

    /**
     * Triggers an asynchronous backup of a specific billing month's Excel ledger to Google Drive.
     */
    fun triggerAsyncMonthBackup(propertyId: String, billingMonthStr: String) {
        scope.launch {
            try {
                backupMonthLedgerToDrive(propertyId, billingMonthStr)
            } catch (e: Exception) {
                logger.w(TAG, "Async Month Excel backup deferred: ${e.message}")
            }
        }
    }

    /**
     * Uploads or updates a specific month's Excel ledger in Google Drive:
     * PG Manager / [PropertyName] / Finance / PG_Rent_Ledger_[Month_Year].xlsx
     */
    suspend fun backupMonthLedgerToDrive(propertyId: String, billingMonthStr: String): PgResult<String> = withContext(Dispatchers.IO) {
        try {
            val authResult = googleAuthManager.getDriveAuthorizationHeader()
            if (authResult is PgResult.Failure) {
                return@withContext PgResult.Failure(authResult.error)
            }
            val authHeader = (authResult as PgResult.Success).data

            val property = propertyDao.getProperty(propertyId)
            val propertyName = property?.propertyName?.ifBlank { "Emerald Stays" } ?: "Emerald Stays"

            // 1. Resolve / Create Folder Hierarchy
            val financeFolderId = resolveFinanceFolder(authHeader, propertyId, propertyName)
                ?: return@withContext PgResult.Failure(com.example.core.common.PgError.NetworkError("Failed to resolve Finance folder in Google Drive"))

            // 2. Generate month-specific Excel file
            val parsedMonth = com.example.features.rent.domain.util.RentBillingEngine.parseBillingMonth(billingMonthStr)
            val monthStart = String.format("%04d-%02d-01", parsedMonth.year, parsedMonth.month1Based)
            val monthEnd = String.format("%04d-%02d-%02d", parsedMonth.year, parsedMonth.month1Based, parsedMonth.daysInMonth)
            
            val config = ExcelExportConfig(
                periodType = ExportPeriodType.CUSTOM_RANGE,
                customStartDate = monthStart,
                customEndDate = monthEnd
            )
            val excelFile = excelManager.generateExportFile(config)
            val cleanMonthFileName = "PG_Rent_Ledger_${parsedMonth.canonicalName.replace(" ", "_")}.xlsx"

            // 3. Upload or update file in Finance folder
            val fileMappingKey = "PROP_${propertyId}_EXCEL_LEDGER_${parsedMonth.year}_${parsedMonth.month1Based}"
            var existingDriveFileId = driveFolderDao.getFolderMapping(fileMappingKey)?.driveFolderId.orEmpty()

            if (existingDriveFileId.isNotBlank()) {
                try {
                    val fileCheck = driveApiService.getFile(authHeader, existingDriveFileId)
                    if (!fileCheck.isSuccessful || fileCheck.body()?.trashed == true) {
                        existingDriveFileId = ""
                    }
                } catch (_: Exception) {
                    existingDriveFileId = ""
                }
            }

            if (existingDriveFileId.isBlank()) {
                try {
                    val query = "name = '$cleanMonthFileName' and '$financeFolderId' in parents and trashed = false"
                    val listResp = driveApiService.listFiles(authHeader, query = query, pageSize = 5)
                    if (listResp.isSuccessful) {
                        val file = listResp.body()?.files?.firstOrNull()
                        if (file != null) {
                            existingDriveFileId = file.id
                        }
                    }
                } catch (_: Exception) {}
            }

            val finalFileId: String
            if (existingDriveFileId.isNotBlank()) {
                val metadata = FileMetadataRequest(
                    name = cleanMonthFileName,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
                val metadataJson = moshi.adapter(FileMetadataRequest::class.java).toJson(metadata)
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("metadata", null, metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                    .addFormDataPart("file", excelFile.name, excelFile.asRequestBody("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".toMediaTypeOrNull()))
                    .build()

                val patchResp = driveApiService.updateFileMultipart(
                    authHeader = authHeader,
                    fileId = existingDriveFileId,
                    contentType = "multipart/related; boundary=" + multipartBody.boundary,
                    body = multipartBody
                )

                finalFileId = if (patchResp.isSuccessful && patchResp.body()?.id != null) {
                    patchResp.body()!!.id
                } else {
                    existingDriveFileId
                }
            } else {
                val metadata = FileMetadataRequest(
                    name = cleanMonthFileName,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    parents = listOf(financeFolderId)
                )
                val metadataJson = moshi.adapter(FileMetadataRequest::class.java).toJson(metadata)
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("metadata", null, metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                    .addFormDataPart("file", excelFile.name, excelFile.asRequestBody("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".toMediaTypeOrNull()))
                    .build()

                val uploadResp = driveApiService.uploadFileMultipart(
                    authHeader = authHeader,
                    contentType = "multipart/related; boundary=" + multipartBody.boundary,
                    body = multipartBody
                )

                if (!uploadResp.isSuccessful || uploadResp.body()?.id == null) {
                    return@withContext PgResult.Failure(com.example.core.common.PgError.NetworkError("Failed to upload month Excel ledger to Google Drive"))
                }
                finalFileId = uploadResp.body()!!.id
            }

            driveFolderDao.insertFolderMapping(
                DriveFolderMappingEntity(
                    folderPathKey = fileMappingKey,
                    driveFolderId = finalFileId,
                    updatedAt = System.currentTimeMillis()
                )
            )

            // Also synchronize the master ledger
            backupPropertyLedgerToDrive(propertyId)

            try { excelFile.delete() } catch (_: Exception) {}

            logger.i(TAG, "Month $billingMonthStr Excel ledger successfully backed up to Google Drive: fileId=$finalFileId")
            PgResult.Success(finalFileId)
        } catch (e: Exception) {
            logger.w(TAG, "Failed to backup month $billingMonthStr Excel ledger: ${e.message}")
            PgResult.Failure(com.example.core.common.PgError.UnknownError("Month Excel backup failed", e))
        }
    }

    /**
     * Uploads or updates the single master Excel file in Google Drive:
     * PG Manager / [PropertyName] / Finance / PG_Rent_Ledger.xlsx
     */
    suspend fun backupPropertyLedgerToDrive(propertyId: String): PgResult<String> = withContext(Dispatchers.IO) {
        try {
            val authResult = googleAuthManager.getDriveAuthorizationHeader()
            if (authResult is PgResult.Failure) {
                return@withContext PgResult.Failure(authResult.error)
            }
            val authHeader = (authResult as PgResult.Success).data

            val property = propertyDao.getProperty(propertyId)
            val propertyName = property?.propertyName?.ifBlank { "Emerald Stays" } ?: "Emerald Stays"

            // 1. Resolve / Create Folder Hierarchy
            val financeFolderId = resolveFinanceFolder(authHeader, propertyId, propertyName)
                ?: return@withContext PgResult.Failure(com.example.core.common.PgError.NetworkError("Failed to resolve Finance folder in Google Drive"))

            // 2. Generate local Master Excel file
            val config = ExcelExportConfig(
                periodType = ExportPeriodType.ALL_DATA
            )
            val excelFile = excelManager.generateExportFile(config)

            // 3. Check if Master Ledger file already exists in Drive
            val masterFileMappingKey = "PROP_${propertyId}_EXCEL_LEDGER"
            var existingDriveFileId = driveFolderDao.getFolderMapping(masterFileMappingKey)?.driveFolderId.orEmpty()

            if (existingDriveFileId.isNotBlank()) {
                // Verify file still exists and not trashed
                try {
                    val fileCheck = driveApiService.getFile(authHeader, existingDriveFileId)
                    if (!fileCheck.isSuccessful || fileCheck.body()?.trashed == true) {
                        existingDriveFileId = ""
                    }
                } catch (_: Exception) {
                    existingDriveFileId = ""
                }
            }

            if (existingDriveFileId.isBlank()) {
                // Query Drive to see if PG_Rent_Ledger.xlsx already exists in the finance folder
                try {
                    val query = "name = 'PG_Rent_Ledger.xlsx' and '$financeFolderId' in parents and trashed = false"
                    val listResp = driveApiService.listFiles(authHeader, query = query, pageSize = 5)
                    if (listResp.isSuccessful) {
                        val file = listResp.body()?.files?.firstOrNull()
                        if (file != null) {
                            existingDriveFileId = file.id
                        }
                    }
                } catch (_: Exception) {}
            }

            val finalFileId: String
            if (existingDriveFileId.isNotBlank()) {
                // Update existing file
                val metadata = FileMetadataRequest(
                    name = "PG_Rent_Ledger.xlsx",
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
                val metadataJson = moshi.adapter(FileMetadataRequest::class.java).toJson(metadata)
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("metadata", null, metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                    .addFormDataPart("file", excelFile.name, excelFile.asRequestBody("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".toMediaTypeOrNull()))
                    .build()

                val patchResp = driveApiService.updateFileMultipart(
                    authHeader = authHeader,
                    fileId = existingDriveFileId,
                    contentType = "multipart/related; boundary=" + multipartBody.boundary,
                    body = multipartBody
                )

                finalFileId = if (patchResp.isSuccessful && patchResp.body()?.id != null) {
                    patchResp.body()!!.id
                } else {
                    existingDriveFileId
                }
            } else {
                // Create new file in finance folder
                val metadata = FileMetadataRequest(
                    name = "PG_Rent_Ledger.xlsx",
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    parents = listOf(financeFolderId)
                )
                val metadataJson = moshi.adapter(FileMetadataRequest::class.java).toJson(metadata)
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("metadata", null, metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                    .addFormDataPart("file", excelFile.name, excelFile.asRequestBody("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".toMediaTypeOrNull()))
                    .build()

                val uploadResp = driveApiService.uploadFileMultipart(
                    authHeader = authHeader,
                    contentType = "multipart/related; boundary=" + multipartBody.boundary,
                    body = multipartBody
                )

                if (!uploadResp.isSuccessful || uploadResp.body()?.id == null) {
                    return@withContext PgResult.Failure(com.example.core.common.PgError.NetworkError("Failed to upload Excel ledger to Google Drive: ${uploadResp.code()}"))
                }
                finalFileId = uploadResp.body()!!.id
            }

            // Save mapping
            driveFolderDao.insertFolderMapping(
                DriveFolderMappingEntity(
                    folderPathKey = masterFileMappingKey,
                    driveFolderId = finalFileId,
                    updatedAt = System.currentTimeMillis()
                )
            )

            // Clean up temp file
            try { excelFile.delete() } catch (_: Exception) {}

            logger.i(TAG, "Master Excel ledger synchronized with Google Drive: fileId=$finalFileId")
            PgResult.Success(finalFileId)
        } catch (e: Exception) {
            logger.w(TAG, "Failed to backup Excel ledger to Google Drive: ${e.message}")
            PgResult.Failure(com.example.core.common.PgError.UnknownError("Drive Excel backup failed", e))
        }
    }

    private suspend fun resolveFinanceFolder(
        authHeader: String,
        propertyId: String,
        propertyName: String
    ): String? {
        val rootKey = "ROOT_PG_MANAGER"
        val propKey = "PROP_${propertyId}"
        val financeKey = "PROP_${propertyId}_FINANCE"

        // 1. Root Folder: "PG Manager"
        var rootId = driveFolderDao.getFolderMapping(rootKey)?.driveFolderId
        if (rootId.isNullOrBlank() || !isFolderValid(authHeader, rootId)) {
            rootId = findOrCreateFolder(authHeader, "PG Manager", null)
            if (rootId != null) {
                driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(rootKey, rootId))
            }
        }
        if (rootId == null) return null

        // 2. Property Folder: e.g. "Emerald Stays"
        var propFolderId = driveFolderDao.getFolderMapping(propKey)?.driveFolderId
        if (propFolderId.isNullOrBlank() || !isFolderValid(authHeader, propFolderId)) {
            propFolderId = findOrCreateFolder(authHeader, propertyName, rootId)
            if (propFolderId != null) {
                driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(propKey, propFolderId))
            }
        }
        if (propFolderId == null) return null

        // 3. Finance Folder: "Finance"
        var financeFolderId = driveFolderDao.getFolderMapping(financeKey)?.driveFolderId
        if (financeFolderId.isNullOrBlank() || !isFolderValid(authHeader, financeFolderId)) {
            financeFolderId = findOrCreateFolder(authHeader, "Finance", propFolderId)
            if (financeFolderId != null) {
                driveFolderDao.insertFolderMapping(DriveFolderMappingEntity(financeKey, financeFolderId))
            }
        }
        return financeFolderId
    }

    private suspend fun isFolderValid(authHeader: String, folderId: String): Boolean {
        return try {
            val res = driveApiService.getFile(authHeader, folderId)
            res.isSuccessful && res.body()?.trashed != true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun findOrCreateFolder(authHeader: String, folderName: String, parentId: String?): String? {
        try {
            val query = if (parentId != null) {
                "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and '$parentId' in parents and trashed = false"
            } else {
                "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            }
            val listResp = driveApiService.listFiles(authHeader, query = query, pageSize = 5)
            if (listResp.isSuccessful) {
                val existing = listResp.body()?.files?.firstOrNull()
                if (existing != null) return existing.id
            }

            val createReq = CreateFolderRequest(
                name = folderName,
                parents = if (parentId != null) listOf(parentId) else emptyList()
            )
            val createResp = driveApiService.createFolder(authHeader, createReq)
            if (createResp.isSuccessful && createResp.body()?.id != null) {
                return createResp.body()!!.id
            }
        } catch (e: Exception) {
            logger.w(TAG, "Error finding or creating folder $folderName: ${e.message}")
        }
        return null
    }
}
