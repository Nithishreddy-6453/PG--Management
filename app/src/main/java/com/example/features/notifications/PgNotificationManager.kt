package com.example.features.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PgNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID_RENT = "channel_rent"
        const val CHANNEL_ID_INVOICES = "channel_invoices"
        const val CHANNEL_ID_BACKUP = "channel_backup"
        const val CHANNEL_ID_GENERAL = "channel_general"
    }

    fun initialize() {
        createNotificationChannels()
        scheduleWorkers()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val rentChannel = NotificationChannel(
                CHANNEL_ID_RENT,
                "Rent Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifications for rent due and overdue" }

            val invoiceChannel = NotificationChannel(
                CHANNEL_ID_INVOICES,
                "Invoices",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications for monthly invoices" }

            val backupChannel = NotificationChannel(
                CHANNEL_ID_BACKUP,
                "Backup Reminders",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Periodic backup reminders" }

            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "General app notifications" }

            notificationManager.createNotificationChannels(
                listOf(rentChannel, invoiceChannel, backupChannel, generalChannel)
            )
        }
    }

    private fun scheduleWorkers() {
        val workManager = WorkManager.getInstance(context)

        // Schedule a daily check for rent due and overdue
        val dailyCheckRequest = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "DailyReminderCheck",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            dailyCheckRequest
        )
    }

    @android.annotation.SuppressLint("MissingPermission", "NotificationPermission")
    fun showNotification(channelId: String, title: String, content: String, notificationId: Int) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Fallback icon
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }
}
