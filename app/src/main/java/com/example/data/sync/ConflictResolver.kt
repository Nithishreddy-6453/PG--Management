package com.example.data.sync

import com.example.core.common.PgLogger
import javax.inject.Inject
import javax.inject.Singleton

sealed class ConflictResult<out T> {
    data class UseRemote<out T>(val remoteData: T) : ConflictResult<T>()
    data class UseLocal<out T>(val localData: T) : ConflictResult<T>()
    data class ConflictDetected<out T>(
        val localData: T,
        val remoteData: T,
        val reason: String,
        val localVersion: Int = 1,
        val remoteVersion: Int = 1,
        val localUpdatedAt: Long = 0L,
        val remoteUpdatedAt: Long = 0L,
        val remoteDeviceId: String = ""
    ) : ConflictResult<T>()
}

@Singleton
class ConflictResolver @Inject constructor(
    private val logger: PgLogger
) {
    /**
     * Resolves conflict between local entity metadata and remote Firestore metadata.
     *
     * Rules:
     * 1. If local is clean (SYNCED / LOCAL_ONLY) -> accept remote update.
     * 2. If local has pending mutations (PENDING_UPLOAD / CONFLICT):
     *    - If remote change is from another device or remote version >= local version or remote time > local time:
     *      Flag genuine concurrent conflict, return ConflictDetected so both versions
     *      are preserved in Room and conflict records.
     *    - If local is strictly newer with higher version, use local.
     */
    fun <T> resolve(
        localUpdatedAt: Long,
        localVersion: Int,
        localStatus: String,
        remoteUpdatedAt: Long,
        remoteVersion: Int,
        localData: T,
        remoteData: T,
        entityName: String,
        remoteDeviceId: String = "",
        localDeviceId: String = ""
    ): ConflictResult<T> {
        if (localStatus != "PENDING_UPLOAD" && localStatus != "CONFLICT") {
            // Local is clean, apply remote update
            return ConflictResult.UseRemote(remoteData)
        }

        val isFromOtherDevice = remoteDeviceId.isNotBlank() && localDeviceId.isNotBlank() && remoteDeviceId != localDeviceId

        // If remote has concurrent edits (from another device, or higher/equal version with recent timestamp)
        if (remoteVersion >= localVersion || remoteUpdatedAt > localUpdatedAt || isFromOtherDevice) {
            logger.w(
                TAG,
                "Concurrent conflict detected for $entityName: " +
                "Local(v=$localVersion, t=$localUpdatedAt, dev=$localDeviceId) vs " +
                "Remote(v=$remoteVersion, t=$remoteUpdatedAt, dev=$remoteDeviceId)"
            )
            return ConflictResult.ConflictDetected(
                localData = localData,
                remoteData = remoteData,
                reason = "Concurrent modifications detected between local ($localDeviceId) and remote ($remoteDeviceId)",
                localVersion = localVersion,
                remoteVersion = remoteVersion,
                localUpdatedAt = localUpdatedAt,
                remoteUpdatedAt = remoteUpdatedAt,
                remoteDeviceId = remoteDeviceId
            )
        } else {
            logger.i(TAG, "Local changes preferred for $entityName: Local v$localVersion ($localUpdatedAt) >= Remote v$remoteVersion ($remoteUpdatedAt)")
            return ConflictResult.UseLocal(localData)
        }
    }

    companion object {
        private const val TAG = "ConflictResolver"
    }
}
