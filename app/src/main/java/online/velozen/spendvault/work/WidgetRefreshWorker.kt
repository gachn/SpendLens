package com.spendlens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendlens.app.SpendLensApp
import com.spendlens.app.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * Updates both home-screen widgets. Runs every 30 minutes via a periodic chain and is also
 * enqueued immediately after each new transaction is parsed (issue #8).
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendLensApp).container
        runCatching {
            WidgetUpdater.update(
                context = applicationContext,
                txnRepo = container.transactionRepository,
                budgetRepo = container.budgetRepository,
                categoryRepo = container.categoryRepository,
            )
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK = "widget_refresh_periodic"
        private const val ONE_SHOT_WORK = "widget_refresh_once"

        /** Schedule a repeating 30-minute refresh. Safe to call repeatedly (KEEP policy). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Trigger an immediate one-shot refresh (e.g. after a new transaction). */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
