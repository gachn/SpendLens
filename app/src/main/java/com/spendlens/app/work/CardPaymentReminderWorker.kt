package com.spendlens.app.work

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
import com.spendlens.app.SpendLensApp
import com.spendlens.app.ui.util.Money
import com.spendlens.app.util.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * Daily check that posts a local notification for each credit-card statement that is due soon (or
 * already overdue) and has not been paid. Complements the in-screen reminder banner so the user is
 * nudged even without opening the app. Reminders only — SpendLens never moves money.
 */
class CardPaymentReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendLensApp).container
        if (!NotificationHelper.canPost(applicationContext)) return Result.success()

        val now = System.currentTimeMillis()
        val names = container.settingsStore.accountNames.value

        container.cardBillDao.all().forEach { bill ->
            val dueDate = bill.dueDate ?: return@forEach
            if (bill.paidAt != null) return@forEach
            val days = (dueDate - now) / 86_400_000L
            // Remind from 7 days before the due date through 3 days overdue.
            if (days !in -3L..REMIND_WITHIN_DAYS) return@forEach

            val name = names[bill.cardKey] ?: bill.cardKey
            val whenText = when {
                days < 0L -> "overdue by ${-days} day${if (days == -1L) "" else "s"}"
                days == 0L -> "due today"
                days == 1L -> "due tomorrow"
                else -> "due in $days days"
            }
            val intent = Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                applicationContext,
                bill.cardKey.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(applicationContext, SpendLensApp.CHANNEL_BILLS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Card payment: $name")
                .setContentText("${Money.format(bill.totalDueMinor, bill.currency)} $whenText")
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationHelper.notify(applicationContext, NOTIF_BASE + bill.cardKey.hashCode(), notification)
        }
        return Result.success()
    }

    companion object {
        const val WORK = "card_payment_reminders"
        private const val NOTIF_BASE = 30_000
        private const val REMIND_WITHIN_DAYS = 7L

        /** Schedule the daily reminder check. Safe to call repeatedly (KEEP). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CardPaymentReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
