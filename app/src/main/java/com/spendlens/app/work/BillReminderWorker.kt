package com.spendlens.app.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendlens.app.MainActivity
import com.spendlens.app.SpendLensApp
import com.spendlens.app.parser.BillReminders
import com.spendlens.app.ui.util.Money
import java.util.concurrent.TimeUnit

/**
 * Daily check that posts a local notification for each recurring bill that is due soon and
 * not yet paid this cycle. Reminders only — SpendLens never moves money. docs/DESIGN.md §6.
 */
class BillReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendLensApp).container
        val nm = NotificationManagerCompat.from(applicationContext)
        if (!nm.areNotificationsEnabled()) return Result.success()

        val now = System.currentTimeMillis()
        container.billRepository.allWithReminders().forEach { bill ->
            if (!BillReminders.shouldRemind(bill.dayOfMonth, bill.lastPaidAt, now)) return@forEach
            val days = BillReminders.daysUntilDue(bill.dayOfMonth, now)
            val whenText = when {
                days <= 0L -> "due today"
                days == 1L -> "due tomorrow"
                else -> "due in $days days"
            }
            val intent = Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                applicationContext,
                bill.id.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(applicationContext, SpendLensApp.CHANNEL_BILLS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Upcoming bill: ${bill.counterparty}")
                .setContentText("~${Money.format(bill.typicalAmountMinor, "INR")} $whenText")
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            runCatching { nm.notify(NOTIF_BASE + bill.id.toInt(), notification) }
        }
        return Result.success()
    }

    companion object {
        const val WORK = "bill_reminders"
        private const val NOTIF_BASE = 20_000

        /** Schedule the daily reminder check. Safe to call repeatedly (KEEP). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
