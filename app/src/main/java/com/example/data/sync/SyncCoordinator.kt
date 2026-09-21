package com.example.data.sync

import android.content.Context
import com.example.core.common.PgLogger
import com.example.core.common.PgResult
import com.example.core.device.DeviceIdentityManager
import com.example.data.database.ConflictRecordEntity
import com.example.data.database.SyncOperationEntity
import com.example.data.database.SyncQueueDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

sealed class SignInResult {
    object ExistingAccountBootstrapped : SignInResult()
    object NewAccount : SignInResult()
    object AlreadyBootstrapped : SignInResult()
}

@Singleton
class SyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncQueueDao: SyncQueueDao,
    private val syncEngine: SyncEngine,
    private val realtimeSyncManager: RealtimeSyncManager,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val logger: PgLogger
) {
    val syncDiagnosticsFlow: Flow<SyncDiagnostics> = combine(
        syncQueueDao.getPendingCountFlow(),
        syncQueueDao.getFailedCountFlow(),
        syncEngine.conflictCountFlow,
        syncEngine.isSyncing,
        syncEngine.lastSyncedAt
    ) { pending, failed, conflict, syncing, lastSynced ->
        SyncDiagnostics(
            pendingCount = pending,
            failedCount = failed,
            conflictCount = conflict,
            isSyncing = syncing,
            lastSyncedAt = lastSynced,
            lastError = syncEngine.lastError.value
        )
    }

    val conflictRecordsFlow: Flow<List<ConflictRecordEntity>> = syncEngine.conflictRecordsFlow

    val syncOperationsFlow: Flow<List<SyncOperationEntity>> = syncQueueDao.getAllOperationsFlow()

    suspend fun enqueueOperation(
        entityType: String,
        entityId: String,
        operationType: String,
        payloadJson: String = ""
    ) {
        logger.i(TAG, "Enqueuing local sync operation: $operationType on $entityType ($entityId)")
        val op = SyncOperationEntity(
            entityType = entityType,
            entityId = entityId,
            operationType = operationType,
            payloadJson = payloadJson,
            createdAt = System.currentTimeMillis(),
            status = "PENDING_UPLOAD"
        )
        syncQueueDao.enqueue(op)
        triggerImmediateSync()
    }

    fun triggerImmediateSync() {
        if (!isUserAuthenticated()) {
            logger.i(TAG, "Skipping immediate sync: User is not authenticated")
            return
        }
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (!uid.isNullOrBlank() && !deviceIdentityManager.isDeviceBootstrapCompleted(uid)) {
            logger.w(TAG, "Skipping immediate sync: Initial bootstrap has not completed for UID $uid")
            return
        }
        try {
            SyncWorker.enqueueImmediateSync(context)
        } catch (e: Exception) {
            logger.w(TAG, "Failed scheduling immediate WorkManager sync: ${e.message}")
        }
    }

    fun initializePeriodicSync() {
        if (!isUserAuthenticated()) {
            logger.i(TAG, "Skipping periodic sync: User is not authenticated")
            return
        }
        try {
            SyncWorker.schedulePeriodicSync(context)
            logger.i(TAG, "Periodic 15-minute background sync scheduled")
        } catch (e: Exception) {
            logger.w(TAG, "Failed scheduling periodic sync: ${e.message}")
        }

        // Also start realtime listeners
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (!uid.isNullOrBlank()) {
            realtimeSyncManager.startListening(uid)
        }
    }

    suspend fun handleUserSignIn(newOwnerId: String): SignInResult {
        logger.i(TAG, "Handling sign in for user: $newOwnerId")
        val previousOwner = deviceIdentityManager.getLastActiveOwnerId()
        if (!previousOwner.isNullOrBlank() && previousOwner != newOwnerId) {
            logger.i(TAG, "Different account detected (old: $previousOwner, new: $newOwnerId). Purging local data.")
            syncEngine.clearLocalUserData()
            deviceIdentityManager.clearDeviceBootstrap(previousOwner)
        }
        deviceIdentityManager.setLastActiveOwnerId(newOwnerId)

        val isBootstrapped = deviceIdentityManager.isDeviceBootstrapCompleted(newOwnerId)
        if (!isBootstrapped) {
            // Check if cloud data exists for this Firebase UID
            val hasCloudData = syncEngine.hasCloudData(newOwnerId)
            if (hasCloudData) {
                logger.i(TAG, "Existing account detected on new device for UID: $newOwnerId. Starting initial cloud bootstrap.")
                val bootstrapResult = syncEngine.performInitialCloudBootstrap(newOwnerId)
                if (bootstrapResult is PgResult.Success) {
                    logger.i(TAG, "Initial bootstrap complete. Enabling real-time and periodic sync.")
                    realtimeSyncManager.startListening(newOwnerId)
                    initializePeriodicSync()
                    return SignInResult.ExistingAccountBootstrapped
                } else {
                    logger.e(TAG, "Initial bootstrap encountered error: ${(bootstrapResult as PgResult.Failure).error.message}")
                    realtimeSyncManager.startListening(newOwnerId)
                    initializePeriodicSync()
                    return SignInResult.ExistingAccountBootstrapped
                }
            } else {
                logger.i(TAG, "No existing cloud data found for UID: $newOwnerId. Treating as new account.")
                deviceIdentityManager.setDeviceBootstrapCompleted(newOwnerId, true)
                syncEngine.clearLocalUserData()
                return SignInResult.NewAccount
            }
        } else {
            logger.i(TAG, "Device already bootstrapped for UID: $newOwnerId. Resuming normal sync.")
            realtimeSyncManager.startListening(newOwnerId)
            initializePeriodicSync()
            triggerImmediateSync()
            return SignInResult.AlreadyBootstrapped
        }
    }

    suspend fun handleUserSignOut() {
        val lastOwner = deviceIdentityManager.getLastActiveOwnerId()
        logger.i(TAG, "User signed out. Purging session and local data.")
        realtimeSyncManager.stopListening()
        cancelPeriodicSync()
        if (!lastOwner.isNullOrBlank()) {
            deviceIdentityManager.clearDeviceBootstrap(lastOwner)
        }
        deviceIdentityManager.setLastActiveOwnerId(null)
        syncEngine.clearLocalUserData()
    }

    private fun isUserAuthenticated(): Boolean {
        return try {
            com.google.firebase.FirebaseApp.getApps(context).isNotEmpty() &&
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
        } catch (e: Exception) {
            false
        }
    }

    fun cancelPeriodicSync() {
        try {
            SyncWorker.cancelAllSyncWork(context)
            InitialBootstrapWorker.cancel(context)
            logger.i(TAG, "Background sync cancelled")
        } catch (e: Exception) {
            logger.w(TAG, "Failed cancelling background sync: ${e.message}")
        }
    }

    suspend fun syncNow() = syncEngine.syncNow()

    suspend fun resolveConflict(conflictId: String, strategy: ConflictResolutionStrategy): PgResult<Unit> {
        val result = syncEngine.resolveConflict(conflictId, strategy)
        if (result is PgResult.Success) {
            triggerImmediateSync()
        }
        return result
    }

    fun getDeviceId(): String = deviceIdentityManager.getDeviceId()

    companion object {
        private const val TAG = "SyncCoordinator"
    }
}
