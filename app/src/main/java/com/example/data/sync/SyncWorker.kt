package com.example.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.core.common.PgResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncEngine: SyncEngine
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!isUserAuthenticated()) {
            cancelAllSyncWork(applicationContext)
            return Result.success()
        }
        return try {
            var result = syncEngine.syncNow()
            if (result is PgResult.Failure) {
                if (runAttemptCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun isUserAuthenticated(): Boolean {
        return try {
            com.google.firebase.FirebaseApp.getApps(applicationContext).isNotEmpty() &&
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "PGManager_PeriodicSyncWorker"
        const val ONE_TIME_WORK_NAME = "PGManager_ImmediateSyncWorker"
        private const val MAX_RETRIES = 3

        fun cancelAllSyncWork(context: Context) {
            try {
                val workManager = WorkManager.getInstance(context)
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
            } catch (e: Exception) {
                // Ignore failure
            }
        }

        fun schedulePeriodicSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicRequest
                )
            } catch (e: Exception) {
                // Ignore background work scheduling failure on unsupported emulator environments
            }
        }

        fun enqueueImmediateSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONE_TIME_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    oneTimeRequest
                )
            } catch (e: Exception) {
                // Ignore background work scheduling failure on unsupported emulator environments
            }
        }
    }
}
