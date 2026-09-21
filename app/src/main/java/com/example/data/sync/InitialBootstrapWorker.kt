package com.example.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.core.common.PgLogger
import com.example.core.common.PgResult
import com.example.core.device.DeviceIdentityManager
import com.example.data.database.SyncQueueDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * InitialBootstrapWorker runs once upon first login on a new device when the local database
 * is empty or unbootstrapped. It fetches the full dataset from Firestore and inserts it
 * into Room before enabling regular SyncWorker periodic/immediate sync operations,
 * strictly preventing empty local databases from overwriting existing cloud data.
 */
@HiltWorker
class InitialBootstrapWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncEngine: SyncEngine,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val realtimeSyncManager: RealtimeSyncManager,
    private val syncQueueDao: SyncQueueDao,
    private val logger: PgLogger
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        logger.i(TAG, "InitialBootstrapWorker started execution")

        val ownerId = inputData.getString(KEY_OWNER_ID)
            ?: getAuthenticatedOwnerId()
            ?: run {
                logger.w(TAG, "User not authenticated. Cancelling InitialBootstrapWorker.")
                return Result.failure()
            }

        // If this device is already marked as bootstrapped for this owner, ensure sync is active and finish
        if (deviceIdentityManager.isDeviceBootstrapCompleted(ownerId)) {
            logger.i(TAG, "Device already bootstrapped for $ownerId. Enabling regular sync operations.")
            enableRegularSyncOperations(ownerId)
            return Result.success()
        }

        return try {
            val isLocalEmpty = syncEngine.isLocalDatabaseEmpty()
            val hasCloudData = syncEngine.hasCloudData(ownerId)

            logger.i(TAG, "InitialBootstrapWorker status check for $ownerId: isLocalEmpty=$isLocalEmpty, hasCloudData=$hasCloudData")

            if (hasCloudData) {
                logger.i(TAG, "Cloud data exists for $ownerId. Fetching full dataset from Firestore into Room...")

                // Clear any local pending queue operations to avoid overwriting cloud data
                syncQueueDao.clearAll()

                when (val bootstrapResult = syncEngine.performInitialCloudBootstrap(ownerId)) {
                    is PgResult.Success -> {
                        logger.i(TAG, "Initial bootstrap successfully hydrated Room database with Firestore data.")
                        deviceIdentityManager.setDeviceBootstrapCompleted(ownerId, true)
                        enableRegularSyncOperations(ownerId)
                        Result.success()
                    }
                    is PgResult.Failure -> {
                        val errorMsg = bootstrapResult.error.message ?: "Unknown bootstrap error"
                        logger.e(TAG, "Initial bootstrap failed: $errorMsg")
                        if (runAttemptCount < MAX_RETRIES) {
                            Result.retry()
                        } else {
                            Result.failure()
                        }
                    }
                }
            } else {
                logger.i(TAG, "No cloud data exists for $ownerId (new account). Marking bootstrap as complete.")
                deviceIdentityManager.setDeviceBootstrapCompleted(ownerId, true)
                enableRegularSyncOperations(ownerId)
                Result.success()
            }
        } catch (e: Exception) {
            logger.e(TAG, "Exception during initial bootstrap execution: ${e.message}", e)
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun enableRegularSyncOperations(ownerId: String) {
        try {
            logger.i(TAG, "Enabling regular SyncWorker operations and real-time listeners for $ownerId")
            realtimeSyncManager.startListening(ownerId)
            SyncWorker.schedulePeriodicSync(applicationContext)
            SyncWorker.enqueueImmediateSync(applicationContext)
        } catch (e: Exception) {
            logger.w(TAG, "Failed to enable regular sync operations: ${e.message}")
        }
    }

    private fun getAuthenticatedOwnerId(): String? {
        return try {
            if (com.google.firebase.FirebaseApp.getApps(applicationContext).isNotEmpty()) {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val TAG = "InitialBootstrapWorker"
        const val INITIAL_BOOTSTRAP_WORK_NAME = "PGManager_InitialBootstrapWorker"
        const val KEY_OWNER_ID = "owner_id"
        private const val MAX_RETRIES = 3

        fun enqueueInitialBootstrap(context: Context, ownerId: String? = null) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val dataBuilder = Data.Builder()
                if (!ownerId.isNullOrBlank()) {
                    dataBuilder.putString(KEY_OWNER_ID, ownerId)
                }

                val bootstrapRequest = OneTimeWorkRequestBuilder<InitialBootstrapWorker>()
                    .setConstraints(constraints)
                    .setInputData(dataBuilder.build())
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    INITIAL_BOOTSTRAP_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    bootstrapRequest
                )
                android.util.Log.i(TAG, "Enqueued InitialBootstrapWorker for owner: $ownerId")
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed enqueuing InitialBootstrapWorker: ${e.message}")
            }
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(INITIAL_BOOTSTRAP_WORK_NAME)
            } catch (e: Exception) {
                // Ignore failure
            }
        }
    }
}
