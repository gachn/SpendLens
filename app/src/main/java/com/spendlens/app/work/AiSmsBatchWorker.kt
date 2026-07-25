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

        smsProcessor.beginExternalProgress(pending.size)
        val resolvedCount = AtomicInteger(0)
        try {
            val key = aiConfigStore.effectiveKey()
            if (!aiConfigStore.isUsable() || key == null) {
                // Plan/key/enabled flag changed while these were queued — don't leave them stuck,
                // resolve them through the same regex pipeline Free users use.
                pending.forEach {
                    smsProcessor.applyAiBatchResult(it, null)
                    smsProcessor.advanceExternalProgress(resolvedCount.incrementAndGet())
                }
                return Result.success()
            }

            val model = aiConfigStore.effectiveModel()
            val maxTokens = aiConfigStore.maxTokensPerRequest()
            val concurrency = aiConfigStore.concurrentRequests()
            // Bounds how many batch calls (including retry halves, see resolveBatch) may be in
            // flight at once; serialises the DB/apply side of each call so concurrent network
            // responses never race on duplicate-detection or progress bookkeeping.
            val semaphore = Semaphore(concurrency)
            val applyMutex = Mutex()

            // A large backlog can pack into hundreds of batches (see MAX_ITEMS_PER_BATCH); launching
            // one coroutine per batch up front — with only the network call itself gated by the
            // semaphore — meant every batch's prompt/result data stayed alive at once and blew the
            // heap. A fixed pool of [concurrency] workers pulling from a channel instead keeps only
            // that many batches (plus their retry halves) resolving — and therefore held in memory —
            // at any moment.
            val batches = Channel<List<RawSmsEntity>>(Channel.UNLIMITED)
            for (batch in packBatches(pending, maxTokens)) batches.trySend(batch)
            batches.close()

            coroutineScope {
                repeat(concurrency) {
                    launch {
                        for (batch in batches) {
                            resolveBatch(batch, container.openRouterClient, key, model, rawDao, smsProcessor, semaphore, applyMutex, resolvedCount)
                        }
                    }
                }
            }

            return Result.success()
        } finally {
            smsProcessor.endExternalProgress()
        }
    }

    /**
     * Calls the AI for [batch] (bounded by [semaphore]) and applies whatever results come back. A
     * whole-batch [Failure][OpenRouterClient.Result.Failure] or a truncated/malformed response
     * (some indices missing from [AiBatchResult.parseBatch]) commonly means the batch didn't fit
     * the model's context window — rather than dumping the unresolved rows straight to the regex
     * fallback, retry just those rows in two smaller halves, run concurrently, so the retried
     * prompt (and expected response) is meaningfully smaller. Recurses down to batch size 1, which
     * is the final fallback: [SmsProcessor.applyAiBatchResult] with a null result runs that one SMS
     * through the same regex pipeline Free users use.
     *
     * [applyMutex] serialises the "apply results to the DB" section across every concurrent call
     * (top-level batches and retry halves alike) — the network call itself is the slow, safely
     * parallel part; duplicate-detection and [smsProcessor]'s progress counter are not safe to
     * touch from more than one coroutine at a time.
     */
    private suspend fun resolveBatch(
        batch: List<RawSmsEntity>,
        client: OpenRouterClient,
        key: String,
        model: String,
        rawDao: RawSmsDao,
        smsProcessor: SmsProcessor,
        semaphore: Semaphore,
        applyMutex: Mutex,
        resolvedCount: AtomicInteger,
    ) {
        if (batch.isEmpty()) return

        val prompt = PromptGenerator.generate(batch)
        val response = semaphore.withPermit {
            client.complete(key, model, prompt, operation = "sms_batch_classify")
        }
        val (responseText, results) = when (response) {
            is OpenRouterClient.Result.Success ->
                response.content to AiBatchResult.parseBatch(batch.size, response.content)
            is OpenRouterClient.Result.Failure -> {
                AppLog.aiSkipped("sms_batch_classify", "call_failed: ${response.message}")
                "ERROR: ${response.message}" to List(batch.size) { null }
            }
        }

        val unresolved = mutableListOf<RawSmsEntity>()
        applyMutex.withLock {
            batch.forEachIndexed { i, raw ->
                // Same batched prompt/response is shared by every SMS in this call — each row's
                // debug section shows exactly what was actually sent for it.
                rawDao.updateAiDebug(raw.id, prompt, responseText)
                val result = results.getOrNull(i)
                if (result != null) {
                    smsProcessor.applyAiBatchResult(raw, result)
                    smsProcessor.advanceExternalProgress(resolvedCount.incrementAndGet())
                } else {
                    unresolved += raw
                }
            }
        }

        if (unresolved.isEmpty()) return

        if (batch.size == 1) {
            // Already at the smallest possible context and still no usable result — give up on
            // the AI for this one SMS; it falls back through the regex pipeline like Free plan.
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
                resolveBatch(unresolved.subList(0, mid), client, key, model, rawDao, smsProcessor, semaphore, applyMutex, resolvedCount)
            }
            launch {
                resolveBatch(unresolved.subList(mid, unresolved.size), client, key, model, rawDao, smsProcessor, semaphore, applyMutex, resolvedCount)
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
    private fun packBatches(pending: List<RawSmsEntity>, maxTokens: Int): List<List<RawSmsEntity>> {
        val batches = mutableListOf<MutableList<RawSmsEntity>>()
        var currentTokens = 0
        for (raw in pending.sortedByDescending { it.receivedAt }) {
            val smsTokens = TokenEstimator.estimate(raw.sender + raw.body)
            val current = batches.lastOrNull()
            val fits = current != null && current.isNotEmpty() &&
                currentTokens + smsTokens <= maxTokens && current.size < MAX_ITEMS_PER_BATCH
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
        private const val MAX_ITEMS_PER_BATCH = 15

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
