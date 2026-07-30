package com.spendlens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendlens.app.SpendLensApp
import com.spendlens.app.ai.AiBatchResult
import com.spendlens.app.ai.AiSmsResult
import com.spendlens.app.ai.OpenRouterClient
import com.spendlens.app.ai.PromptGenerator
import com.spendlens.app.ai.TokenEstimator
import com.spendlens.app.data.db.RawSmsDao
import com.spendlens.app.data.db.RawSmsEntity
import com.spendlens.app.data.db.RawStatus
import com.spendlens.app.sms.SmsProcessor
import com.spendlens.app.util.AppLog
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

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

        val startedAt = System.currentTimeMillis()
        smsProcessor.beginExternalProgress(pending.size)
        val resolvedCount = AtomicInteger(0)
        try {
            val key = aiConfigStore.effectiveKey()
            if (!aiConfigStore.isUsable() || key == null) {
                pending.forEach {
                    smsProcessor.applyAiBatchResult(it, null)
                    smsProcessor.advanceExternalProgress(resolvedCount.incrementAndGet())
                }
                return Result.success()
            }

            val model = aiConfigStore.effectiveModel()
            val maxTokens = aiConfigStore.maxTokensPerRequest()
            val maxItemsPerBatch = aiConfigStore.maxItemsPerBatch()
            val concurrency = aiConfigStore.concurrentRequests()
            val semaphore = Semaphore(concurrency)
            val applyMutex = Mutex()

            val batches = Channel<List<RawSmsEntity>>(Channel.UNLIMITED)
            for (batch in packBatches(pending, maxTokens, maxItemsPerBatch)) batches.trySend(batch)
            batches.close()

            coroutineScope {
                repeat(concurrency) {
                    launch {
                        for (batch in batches) {
                            val (responseText, results) = callAi(batch, container.openRouterClient, key, model, semaphore)
                            applyResults(batch, responseText, results, rawDao, smsProcessor, applyMutex, resolvedCount)
                            handleUnresolved(batch, results, container.openRouterClient, key, model, rawDao, smsProcessor, semaphore, applyMutex, resolvedCount)
                        }
                    }
                }
            }

            return Result.success()
        } finally {
            smsProcessor.endExternalProgress()
            smsProcessor.recordAiBatchRun(System.currentTimeMillis() - startedAt, resolvedCount.get())
        }
    }

    private suspend fun callAi(
        batch: List<RawSmsEntity>,
        client: OpenRouterClient,
        key: String,
        model: String,
        semaphore: Semaphore,
    ): Pair<String, List<AiSmsResult?>> {
        val prompt = PromptGenerator.generate(batch)
        val response = semaphore.withPermit {
            client.complete(key, model, prompt, operation = "sms_batch_classify")
        }
        return when (response) {
            is OpenRouterClient.Result.Success ->
                response.content to AiBatchResult.parseBatch(batch.size, response.content)
            is OpenRouterClient.Result.Failure -> {
                AppLog.aiSkipped("sms_batch_classify", "call_failed: ${response.message}")
                "ERROR: ${response.message}" to List(batch.size) { null }
            }
        }
    }

    private suspend fun applyResults(
        batch: List<RawSmsEntity>,
        responseText: String,
        results: List<AiSmsResult?>,
        rawDao: RawSmsDao,
        smsProcessor: SmsProcessor,
        applyMutex: Mutex,
        resolvedCount: AtomicInteger,
    ) {
        applyMutex.withLock {
            batch.forEachIndexed { i, raw ->
                rawDao.updateAiDebug(raw.id, PromptGenerator.generate(batch), responseText)
                val result = results.getOrNull(i)
                if (result != null) {
                    smsProcessor.applyAiBatchResult(raw, result)
                    smsProcessor.advanceExternalProgress(resolvedCount.incrementAndGet())
                }
            }
        }
    }

    private suspend fun handleUnresolved(
        batch: List<RawSmsEntity>,
        results: List<AiSmsResult?>,
        client: OpenRouterClient,
        key: String,
        model: String,
        rawDao: RawSmsDao,
        smsProcessor: SmsProcessor,
        semaphore: Semaphore,
        applyMutex: Mutex,
        resolvedCount: AtomicInteger,
    ) {
        val unresolved = mutableListOf<RawSmsEntity>()
        batch.forEachIndexed { i, raw ->
            if (results.getOrNull(i) == null) unresolved += raw
        }

        if (unresolved.isEmpty()) return

        if (batch.size == 1) {
            applyMutex.withLock {
                smsProcessor.applyAiBatchResult(unresolved.single(), null)
                smsProcessor.advanceExternalProgress(resolvedCount.incrementAndGet())
            }
            return
        }

        AppLog.aiSkipped(
            "sms_batch_classify",
            "retrying_smaller_context: ${unresolved.size}/${batch.size} unresolved",
        )
        val mid = (unresolved.size + 1) / 2
        coroutineScope {
            launch {
                val (responseText, results) = callAi(unresolved.subList(0, mid), client, key, model, semaphore)
                applyResults(unresolved.subList(0, mid), responseText, results, rawDao, smsProcessor, applyMutex, resolvedCount)
                handleUnresolved(unresolved.subList(0, mid), results, client, key, model, rawDao, smsProcessor, semaphore, applyMutex, resolvedCount)
            }
            launch {
                val (responseText, results) = callAi(unresolved.subList(mid, unresolved.size), client, key, model, semaphore)
                applyResults(unresolved.subList(mid, unresolved.size), responseText, results, rawDao, smsProcessor, applyMutex, resolvedCount)
                handleUnresolved(unresolved.subList(mid, unresolved.size), results, client, key, model, rawDao, smsProcessor, semaphore, applyMutex, resolvedCount)
            }
        }
    }

    /**
     * Greedily packs [pending] (newest first, so a backlog surfaces recent spend before old) into
     * token-budget-sized batches, also capped at [MAX_ITEMS_PER_BATCH] regardless of remaining
     * token budget. [TokenEstimator] only sizes the *input* prompt — for short SMS (typical Indian
     * bank alerts run ~150-250 chars) that budget alone lets 100+ messages into one batch, but the
     * model also has to *generate* one JSON object per message; that output is what actually blows
     * past a request's time budget, not the input tokens. Without this cap, batches were routinely
     * timing out at 90s and repeatedly recursing through [resolveBatch]'s halving before reaching a
     * size the model could answer in time.
     */
    private fun packBatches(pending: List<RawSmsEntity>, maxTokens: Int, maxItemsPerBatch: Int): List<List<RawSmsEntity>> {
        val batches = mutableListOf<MutableList<RawSmsEntity>>()
        var currentTokens = 0
        for (raw in pending.sortedByDescending { it.receivedAt }) {
            val smsTokens = TokenEstimator.estimate(raw.sender + raw.body)
            val current = batches.lastOrNull()
            val fits = current != null && current.isNotEmpty() &&
                currentTokens + smsTokens <= maxTokens && current.size < maxItemsPerBatch
            if (fits) {
                current!! += raw
                currentTokens += smsTokens
            } else {
                batches += mutableListOf(raw)
                currentTokens = smsTokens
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
