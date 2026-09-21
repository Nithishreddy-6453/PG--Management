package com.example.data.sync

enum class SyncStatus {
    LOCAL_ONLY,
    PENDING_UPLOAD,
    SYNCING,
    SYNCED,
    FAILED,
    CONFLICT
}

data class SyncDiagnostics(
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val conflictCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val lastError: String? = null
)
