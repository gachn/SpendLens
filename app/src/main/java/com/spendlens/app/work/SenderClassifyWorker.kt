package com.spendlens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendlens.app.SpendLensApp
import com.spendlens.app.data.db.SenderClassificationEntity
import com.spendlens.app.data.db.SenderSource
import com.spendlens.app.parser.FinancialSenderFilter
import com.spendlens.app.util.AppLog

/**
 * Background job that classifies SMS senders as financial or non-financial, building a persistent
 * DB cache so each sender is evaluated at most once.
 *
 * **Flow:**
 * 1. Collect all senders in raw_sms that have no entry in sender_classifications.
 * 2. Senders that match the built-in [FinancialSenderFilter] list are saved immediately as STATIC
 *    without an AI call.
 * 3. Remaining unknown senders are sent to the LLM in batches of [BATCH_SIZE].
 * 4. Non-financial senders: mark their raw_sms as IGNORED and delete their parsed transactions.
 * 5. Financial senders classified by AI: re-process any IGNORED raw_sms from that sender so their
 *    transactions are recovered (handles the case where the static filter missed a legit bank).
 *
 * Registered as unique work with [ExistingWorkPolicy.KEEP] so a burst of SMS coalesces into one
 * run. No-ops when AI is unconfigured — static-filter entries are still written without a network
 * call.
 */
class SenderClassifyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendLensApp).container

        val unclassified = container.senderClassificationDao.unclassifiedSenders()
        if (unclassified.isEmpty()) return Result.success()

        AppLog.i("SenderClassifyWorker: classifying ${unclassified.size} unclassified senders")

        val now = System.currentTimeMillis()

        // Static filter first — no AI call needed for known senders.
        val (staticKnown, needsAi) = unclassified.partition { FinancialSenderFilter.isFinancialSender(it) }
        staticKnown.forEach { sender ->
            container.senderClassificationDao.insertIgnore(
                SenderClassificationEntity(sender, true, SenderSource.STATIC, now),
            )
        }

        // AI classification for the rest, in batches.
        val classifier = container.senderClassifier
        val newlyFinancial = mutableListOf<String>()

        needsAi.chunked(BATCH_SIZE).forEach { batch ->
            val results = classifier.classify(batch)

            results.forEach { (sender, isFinancial) ->
                container.senderClassificationDao.insertIgnore(
                    SenderClassificationEntity(sender, isFinancial, SenderSource.AI, now),
                )
            }

            // Clean up non-financial senders — remove their parsed transactions and mark IGNORED.
            results.filter { !it.value }.keys.forEach { sender ->
                container.rawSmsDao.ignoreAllForSender(sender)
                container.transactionRepository.deleteAllForSender(sender)
                AppLog.i("SenderClassifyWorker: non-financial sender purged: $sender")
            }

            // Collect financial senders so we can recover any IGNORED SMS for them below.
            newlyFinancial += results.filter { it.value }.keys
        }

        // Re-process IGNORED raw SMS from senders now confirmed financial (e.g. regional banks
        // not in the static list that were blocked while waiting for classification).
        if (newlyFinancial.isNotEmpty()) {
            val recovered = container.smsProcessor.reprocessIgnoredForSenders(newlyFinancial)
            if (recovered > 0) {
                AppLog.i("SenderClassifyWorker: recovered $recovered SMS from ${newlyFinancial.size} newly-financial senders")
                WidgetRefreshWorker.enqueue(applicationContext)
            }
        }

        return Result.success()
    }

    companion object {
        private const val BATCH_SIZE = 20
        const val WORK_NAME = "sender_classify"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SenderClassifyWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
