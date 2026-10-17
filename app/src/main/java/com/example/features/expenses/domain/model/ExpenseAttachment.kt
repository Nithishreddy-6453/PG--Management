package com.example.features.expenses.domain.model

import java.util.UUID

/**
 * Metadata for a bill/receipt attachment linked to an Expense record.
 * Uploaded to Google Drive under folder "Expense Receipts".
 */
data class ExpenseAttachment(
    val id: String = UUID.randomUUID().toString(),
    val expenseCloudId: String = "",
    val propertyId: String = "",
    val ownerId: String = "",
    val driveFileId: String = "",
    val fileName: String = "",
    val mimeType: String = "application/pdf",
    val fileSize: Long = 0L,
    val uploadStatus: String = "PENDING_UPLOAD", // "PENDING_UPLOAD", "UPLOADING", "UPLOADED", "FAILED"
    val localFilePath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "UPLOAD", // "UPLOAD" or "SCAN"
    val lastError: String? = null
)
