package com.spendlens.app.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendlens.app.MainActivity
import com.spendlens.app.R
import com.spendlens.app.SpendLensApp
import java.util.concurrent.TimeUnit

/**
 * Periodic check that nudges the user to make an encrypted backup if it has been more than
 * [REMINDER_AFTER_MS] (30 days) since the last one and there is data worth backing up (issue #13).
 * Fully local — it never uploads anything, it only posts a notification.
 */
class BackupReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendLensApp).container
        val last = container.settingsStore.lastBackupAtMillis()
        val overdue = System.currentTimeMillis() - last >= REMINDER_AFTER_MS
        if (overdue && container.database.transactionDao().count() > 0) {
            notifyReminder(applicationContext, last == 0L)
        }
        return Result.success()
    }

    private fun notifyReminder(context: Context, neverBackedUp: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val title = "Back up your SpendLens data"
        val text = if (neverBackedUp)
            "You haven't backed up yet. Create an encrypted backup so a reinstall won't lose your history."
        else
            "It's been over 30 days since your last backup. Tap to create a new encrypted backup."
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIF_ID, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, SpendLensApp.CHANNEL_TRANSACTIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_ID, notification)
    }

    companion object {
        private const val WORK_NAME = "backup_reminder"
        private const val NOTIF_ID = 778899
        private val REMINDER_AFTER_MS = TimeUnit.DAYS.toMillis(30)

        /** Schedule the daily overdue-backup check (idempotent — keeps any existing schedule). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }
    }
}
