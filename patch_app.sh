sed -i 's/class PgApplication : Application()/class PgApplication : Application(), androidx.work.Configuration.Provider/g' app/src/main/java/com/example/PgApplication.kt
sed -i '/import dagger.hilt.android.HiltAndroidApp/a \
import androidx.hilt.work.HiltWorkerFactory\nimport javax.inject.Inject' app/src/main/java/com/example/PgApplication.kt
sed -i '/lateinit var logger: PgLogger/a \
\n    @Inject\n    lateinit var workerFactory: HiltWorkerFactory\n\n    @Inject\n    lateinit var notificationManager: com.example.features.notifications.PgNotificationManager\n\n    override val workManagerConfiguration: androidx.work.Configuration\n        get() = androidx.work.Configuration.Builder()\n            .setWorkerFactory(workerFactory)\n            .build()' app/src/main/java/com/example/PgApplication.kt
sed -i '/logger.i(TAG, "Application container initialized via Dagger Hilt.")/a \
        notificationManager.initialize()' app/src/main/java/com/example/PgApplication.kt
