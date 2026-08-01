package com.spendlens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.spendlens.app.SpendLensApp
import com.spendlens.app.util.AppLog

/**
 * Background job for the AI fallback categoriser ([com.spendlens.app.ai.AiCategorizer]).
 *
 * Enqueued automatically once the inbox import finishes ([SmsSyncWorker]) and after each
 * newly-parsed SMS, and manually when the user asks for a re-run. Registered as unique work with
 * [ExistingWorkPolicy.KEEP] so a burst of incoming SMS coalesces into a single run instead of
 * firing one off-device call per message.
 *
 * The categoriser itself no-ops when AI is disabled or unconfigured, so enqueueing is always safe.
 */
class AiCategorizeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendLensApp).container
        val force = inputData.getBoolean(KEY_FORCE, false)

        val categorized = runCatching {
            if (force) {
                // User re-run: drain the whole backlog in capped passes (throttle bypassed).
                var total = 0
                var iterations = 0
                do {
                    val outcome = container.aiCategorizer.run(force = true)
                    total += outcome.categorized
                    iterations++
                } while (outcome.attempted > 0 && iterations < MAX_FORCE_PASSES)
                total
            } else {
                container.aiCategorizer.run(force = false).categorized
            }
        }
            .onFailure { AppLog.e("AiCategorizeWorker failed", "worker", it) }
            .getOrDefault(0)

        AppLog.i("AiCategorizeWorker: done force=$force categorized=$categorized")
        if (categorized > 0) WidgetRefreshWorker.enqueue(applicationContext)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "ai_categorize"
        private const val KEY_FORCE = "force"

        /** Safety cap on capped passes in a single forced run, so a bad loop can't spin forever. */
        private const val MAX_FORCE_PASSES = 50

        /**
         * Enqueue the auto-categorise job, coalescing with any already-pending run ([KEEP]). Cheap
         * to call after every SMS — the worker is throttled and exits early when there's nothing to do.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<AiCategorizeWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Force a full re-run, bypassing the throttle and draining the whole backlog. Replaces any
         * pending auto run — used by the user-triggered "Auto-categorise now".
         */
        fun enqueueReplace(context: Context) {
            val request = OneTimeWorkRequestBuilder<AiCategorizeWorker>()
                .setInputData(workDataOf(KEY_FORCE to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
