package com.spendlens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendlens.app.SpendLensApp
import com.spendlens.app.util.AppLog

/**
 * Background job that detects promotional SMS (loan offers, credit-limit raises, etc.) that
 * passed the financial filter at parse time but are not real transactions.
 *
 * Reads PARSED raw SMS pre-filtered by promotional SQL keywords, applies the full
 * [com.spendlens.app.ai.PromotionalChecker.PROMO_CUE] regex, then batches them into AI calls
 * sized to 60 % of the model's context window. Promotional verdicts delete the transaction,
 * mark the raw SMS as IGNORED, and persist an exclusion regex so the same pattern is caught
 * offline next time. Registered as unique work with [ExistingWorkPolicy.KEEP] so a burst of
 * incoming SMS coalesces into a single run.
 */
class PromotionalCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendLensApp).container
        val removed = runCatching {
            container.promotionalChecker.runBatch()
        }
            .onFailure { AppLog.e("PromotionalCheckWorker failed", "worker", it) }
            .getOrDefault(0)

        if (removed > 0) {
            AppLog.i("PromotionalCheckWorker: removed $removed promotional SMS")
            WidgetRefreshWorker.enqueue(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "promotional_check"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PromotionalCheckWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
