package com.example.features.settings.domain.repository

import android.net.Uri

interface BackupRepository {
    suspend fun createBackup(uri: Uri): Result<Unit>
    suspend fun restoreBackup(uri: Uri): Result<Unit>
}
