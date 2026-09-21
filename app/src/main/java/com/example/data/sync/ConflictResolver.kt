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

enum class ConflictResolutionStrategy {
    KEEP_LOCAL,
    KEEP_REMOTE
}

@Singleton
class ConflictResolver @Inject constructor(
    private val logger: PgLogger
) {
    /**
     * Resolves conflict between local entity metadata and remote Firestore metadata.
     *
     * Rules:
     * 1. If local is clean (SYNCED / LOCAL_ONLY) or does not exist -> accept remote update.
     * 2. If local and remote content are identical -> no conflict, accept remote metadata.
     * 3. If remote change was authored by this device (self-echo / confirmed upload) -> accept remote.
     * 4. If this exact conflict was already resolved for this remote version -> do not recreate conflict.
     * 5. If local has pending mutations (PENDING_UPLOAD / CONFLICT) and remote from another device differs:
     *    -> Flag genuine concurrent conflict.
     */
    fun <T> resolve(
        localUpdatedAt: Long,
        localVersion: Int,
        localStatus: String,
        remoteUpdatedAt: Long,
        remoteVersion: Int,
        localData: T?,
        remoteData: T,
        entityName: String,
        remoteDeviceId: String = "",
        localDeviceId: String = "",
        isContentIdentical: Boolean = false,
        isConflictAlreadyResolved: Boolean = false
    ): ConflictResult<T> {
        // Rule 4: If local does not exist or is clean (SYNCED / LOCAL_ONLY), apply remote update directly
        if (localData == null || (localStatus != "PENDING_UPLOAD" && localStatus != "CONFLICT")) {
            return ConflictResult.UseRemote(remoteData)
        }

        // Rule 6: Never create a conflict when local and remote entity data are identical
        if (isContentIdentical) {
            logger.d(TAG, "Identical content detected for $entityName; accepting remote version without conflict.")
            return ConflictResult.UseRemote(remoteData)
        }

        // Rule 2: Recognize self-echoes / device's own uploads
        val isFromSameDevice = remoteDeviceId.isNotBlank() && localDeviceId.isNotBlank() && remoteDeviceId == localDeviceId
        if (isFromSameDevice && remoteVersion >= localVersion) {
            logger.d(TAG, "Self-echo acknowledged for $entityName (dev=$localDeviceId, v=$remoteVersion).")
            return ConflictResult.UseRemote(remoteData)
        }

        // Rule 8: If conflict was already resolved with Keep Mine or Accept Remote for this remote version/revision
        if (isConflictAlreadyResolved) {
            logger.d(TAG, "Conflict already resolved previously for $entityName (remote v=$remoteVersion). Skipping re-flag.")
            return if (localStatus == "PENDING_UPLOAD") {
                ConflictResult.UseLocal(localData)
            } else {
                ConflictResult.UseRemote(remoteData)
            }
        }

        // Rule 5: Genuine concurrent conflict - both devices modified independently
        val isFromOtherDevice = remoteDeviceId.isNotBlank() && localDeviceId.isNotBlank() && remoteDeviceId != localDeviceId
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
            logger.i(TAG, "Local changes retained for $entityName: Local v$localVersion ($localUpdatedAt) > Remote v$remoteVersion ($remoteUpdatedAt)")
            return ConflictResult.UseLocal(localData)
        }
    }

    companion object {
        private const val TAG = "ConflictResolver"
    }
}
