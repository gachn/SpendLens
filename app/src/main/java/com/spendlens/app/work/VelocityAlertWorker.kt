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
import com.spendlens.app.parser.SpendingVelocity
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.ui.util.Money
import java.util.concurrent.TimeUnit

/**
 * Daily check that warns when a budgeted category is on pace to overshoot its monthly limit.
 * Projects month-end spend from the current run-rate and posts a local notification when the
 * projection exceeds the budget — at most once per category per week. Reminders only;
 * SpendLens never moves money. See issue #3 (spending velocity alert).
 */
class VelocityAlertWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendLensApp).container
        val nm = NotificationManagerCompat.from(applicationContext)
        if (!nm.areNotificationsEnabled()) return Result.success()

        val now = System.currentTimeMillis()
        val (from, to) = Dates.currentMonth()

        val spentByCat = container.transactionRepository.categoryTotalsBetween(from, to)
            .associate { it.categoryId to it.total }
        val categoriesById = container.categoryRepository.all().associateBy { it.id }
        val store = container.velocityAlertStore

        container.budgetRepository.all().forEach { budget ->
            val limit = budget.monthlyLimitMinor
            val spent = spentByCat[budget.categoryId] ?: 0L
            if (!SpendingVelocity.isOnPaceToExceed(spent, limit, now)) return@forEach
            if (!store.canAlert(budget.categoryId, now)) return@forEach

            val category = categoriesById[budget.categoryId] ?: return@forEach
            val projected = SpendingVelocity.projectMonthEnd(spent, now)
            postAlert(nm, budget.categoryId, category.icon, category.name, spent, projected, limit)
            store.markAlerted(budget.categoryId, now)
        }
        return Result.success()
    }

    private fun postAlert(
        nm: NotificationManagerCompat,
        categoryId: Long,
        icon: String,
        name: String,
        spentMinor: Long,
        projectedMinor: Long,
        limitMinor: Long,
    ) {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext,
            NOTIF_BASE + categoryId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, SpendLensApp.CHANNEL_BUDGETS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("$icon $name budget at risk")
            .setContentText(
                "Spent ${Money.format(spentMinor, "INR")} so far — on pace for " +
                    "~${Money.format(projectedMinor, "INR")} vs ${Money.format(limitMinor, "INR")} limit.",
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "At your current pace you're projected to spend about " +
                        "${Money.format(projectedMinor, "INR")} on $name this month, over your " +
                        "${Money.format(limitMinor, "INR")} budget. ${Money.format(spentMinor, "INR")} spent so far.",
                ),
            )
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { nm.notify(NOTIF_BASE + categoryId.toInt(), notification) }
    }

    companion object {
        const val WORK = "velocity_alerts"
        private const val NOTIF_BASE = 30_000

        /** Schedule the daily velocity check. Safe to call repeatedly (KEEP). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<VelocityAlertWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
