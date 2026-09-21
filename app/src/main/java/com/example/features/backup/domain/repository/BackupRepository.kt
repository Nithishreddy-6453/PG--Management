package com.example.features.backup.domain.repository

import java.io.InputStream
import java.io.OutputStream

interface BackupRepository {
    suspend fun createBackup(outputStream: OutputStream): Result<Unit>
    suspend fun restoreBackup(inputStream: InputStream): Result<Unit>
}
