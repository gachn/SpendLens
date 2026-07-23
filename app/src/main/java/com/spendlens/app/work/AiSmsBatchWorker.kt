package com.spendlens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendlens.app.SpendLensApp
import com.spendlens.app.ai.AiBatchResult
import com.spendlens.app.ai.OpenRouterClient
import com.spendlens.app.ai.PromptGenerator
import com.spendlens.app.ai.TokenEstimator
import com.spendlens.app.data.db.RawSmsEntity
import com.spendlens.app.data.db.RawStatus
import com.spendlens.app.util.AppLog
import java.util.concurrent.TimeUnit

/**
 * Premium's debounced batch AI call: packs every PENDING_AI raw SMS into one or more prompts
 * (sized by [com.spendlens.app.data.prefs.AiConfigStore.maxTokensPerRequest], via [TokenEstimator])
 * and resolves each row through [com.spendlens.app.sms.SmsProcessor.applyAiBatchResult].
 *
 * Enqueued with [ExistingWorkPolicy.REPLACE] plus a fixed initial delay — unlike
 * [SenderClassifyWorker]'s `KEEP` (coalesce into one run at a fixed time), `REPLACE` means each new
 * SMS arrival cancels the previously scheduled run and reschedules it, giving a true debounce
 * window: the batch only fires once SMS stop arriving for [DEBOUNCE_SECONDS].
 */
class AiSmsBatchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendLensApp).container
        val aiConfigStore = container.aiConfigStore
        val rawDao = container.rawSmsDao
        val smsProcessor = container.smsProcessor

        val pending = rawDao.listByStatus(RawStatus.PENDING_AI)
        if (pending.isEmpty()) return Result.success()

        smsProcessor.beginExternalProgress(pending.size)
        var resolved = 0
        try {
            val key = aiConfigStore.effectiveKey()
            if (!aiConfigStore.isUsable() || key == null) {
                // Plan/key/enabled flag changed while these were queued — don't leave them stuck,
                // resolve them through the same regex pipeline Free users use.
                pending.forEach {
                    smsProcessor.applyAiBatchResult(it, null)
                    smsProcessor.advanceExternalProgress(++resolved)
                }
                return Result.success()
            }

            val model = aiConfigStore.effectiveModel()
            val maxTokens = aiConfigStore.maxTokensPerRequest()

            for (batch in packBatches(pending, maxTokens)) {
                val prompt = PromptGenerator.generate(batch)
                val response = container.openRouterClient.complete(key, model, prompt, operation = "sms_batch_classify")
                val responseText = when (response) {
                    is OpenRouterClient.Result.Success -> response.content
                    is OpenRouterClient.Result.Failure -> "ERROR: ${response.message}"
                }
                val results = when (response) {
                    is OpenRouterClient.Result.Success -> AiBatchResult.parseBatch(batch.size, response.content)
                    is OpenRouterClient.Result.Failure -> {
                        AppLog.aiSkipped("sms_batch_classify", "call_failed: ${response.message}")
                        List(batch.size) { null }
                    }
                }
                batch.forEachIndexed { i, raw ->
                    // Same batched prompt/response is shared by every SMS in this batch — each
                    // row's debug section shows exactly what was actually sent for it.
                    rawDao.updateAiDebug(raw.id, prompt, responseText)
                    smsProcessor.applyAiBatchResult(raw, results.getOrNull(i))
                    smsProcessor.advanceExternalProgress(++resolved)
                }
            }

            return Result.success()
        } finally {
            smsProcessor.endExternalProgress()
        }
    }

    /** Greedily packs [pending] (oldest first) into token-budget-sized batches. */
    private fun packBatches(pending: List<RawSmsEntity>, maxTokens: Int): List<List<RawSmsEntity>> {
        val batches = mutableListOf<MutableList<RawSmsEntity>>()
        var currentTokens = 0
        for (raw in pending.sortedBy { it.receivedAt }) {
            val smsTokens = TokenEstimator.estimate(raw.sender + raw.body)
            val current = batches.lastOrNull()
            if (current == null || (current.isNotEmpty() && currentTokens + smsTokens > maxTokens)) {
                batches += mutableListOf(raw)
                currentTokens = smsTokens
            } else {
                current += raw
                currentTokens += smsTokens
            }
        }
        return batches
    }

    companion object {
        const val WORK_NAME = "ai_sms_batch"
        private const val DEBOUNCE_SECONDS = 8L

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<AiSmsBatchWorker>()
                .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
