package com.example

import android.app.Application
import com.example.core.common.PgLogger
import com.example.features.startup.StartupManager
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject
import kotlin.system.exitProcess

/**
 * Custom Application class bootstrapping core dependency graphs and process lifecycles.
 */
@HiltAndroidApp
class PgApplication : Application(), androidx.work.Configuration.Provider {

    // Main system startup manager coordinator
    @Inject
    lateinit var startupManager: StartupManager

    @Inject
    lateinit var logger: PgLogger

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationManager: com.example.features.notifications.PgNotificationManager

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase safely
        if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
            try {
                com.google.firebase.FirebaseApp.initializeApp(this)
            } catch (e: Exception) {
                // Ignore if initialized concurrently
            }
        }
        
        // 1. Initialize Dependency Graph - Hilt automatically handles instantiation of startupManager and logger!
        logger.i(TAG, "Application container initialized via Dagger Hilt.")
        notificationManager.initialize()

        // 2. Register Global Uncaught Exception Handler
        setupGlobalExceptionHandler(logger)
    }

    private fun setupGlobalExceptionHandler(logger: PgLogger) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logger.e(TAG, "Uncaught Exception detected in Thread: ${thread.name}", throwable)
            
            // Allow default android crash handling or custom telemetry/restart logs
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(1)
            }
        }
        logger.i(TAG, "Uncaught Exception boundaries established.")
    }

    companion object {
        private const val TAG = "PgApplication"
    }
}
