package com.spendlens.app.ai

import com.spendlens.app.data.db.PromotionalExclusionDao
import com.spendlens.app.data.db.PromotionalExclusionEntity
import com.spendlens.app.data.db.RawSmsDao
import com.spendlens.app.data.db.RawStatus
import com.spendlens.app.data.prefs.AiConfigStore
import com.spendlens.app.data.repository.TransactionRepository
import com.spendlens.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Guards the SMS pipeline against promotional messages (loan offers, credit-limit raises, etc.)
 * that pass [com.spendlens.app.parser.FinancialSmsFilter] because they mention an amount + verb.
 *
 * **Inline path** (zero latency, called from [com.spendlens.app.sms.SmsProcessor]):
 *   [isKnownPromotional] — checks the in-memory cache of DB-saved exclusion regexes. No network.
 *
 * **Batch path** (background, called from [com.spendlens.app.work.PromotionalCheckWorker]):
 *   [runBatch] — SQL pre-filters PARSED raw SMS by promotional keywords, applies the full
 *   [PROMO_CUE] regex, then sends batches to the AI. Each batch is sized to stay within 60 % of
 *   the model's context window. For each promotional verdict, the transaction is deleted, the raw
 *   SMS is marked IGNORED, and the AI-supplied exclusion regex is persisted so future identical-
 *   pattern messages never reach the AI.
 */
class PromotionalChecker(
    private val exclusionDao: PromotionalExclusionDao,
    private val rawSmsDao: RawSmsDao,
    private val txnRepo: TransactionRepository,
    private val client: OpenRouterClient,
    private val aiConfigStore: AiConfigStore,
) {
    @Volatile private var exclusions: List<Regex> = emptyList()

    /** Load (or reload) saved exclusion patterns from DB into the in-memory cache. */
    suspend fun loadExclusions() = withContext(Dispatchers.IO) {
        exclusions = exclusionDao.getAll().mapNotNull {
            runCatching { Regex(it.bodyRegex, setOf(RegexOption.IGNORE_CASE)) }.getOrNull()
        }
    }

    /** True if [body] matches a previously saved exclusion pattern. No AI call, no blocking. */
    fun isKnownPromotional(body: String): Boolean = exclusions.any { it.containsMatchIn(body) }

    /**
     * Background batch run. Finds all PARSED raw SMS not yet checked ([RawSmsEntity.promoChecked]
     * = false) that match promotional keywords, groups them into context-budget-limited batches,
     * asks the AI per batch, and acts on the verdicts.
     *
     * Returns the number of SMS removed as promotional.
     */
    suspend fun runBatch(): Int = withContext(Dispatchers.IO) {
        if (!aiConfigStore.isUsable()) return@withContext 0
        val key = aiConfigStore.effectiveKey() ?: return@withContext 0
        val model = aiConfigStore.effectiveModel()

        val candidates = rawSmsDao.listPromoCandidates()
        if (candidates.isEmpty()) return@withContext 0

        // Kotlin regex further narrows the SQL LIKE pre-filter.
        val matches = candidates.filter { PROMO_CUE.containsMatchIn(it.body) }

        AppLog.i("PromotionalChecker: ${matches.size} candidates from ${candidates.size} pre-filtered rows")

        val budget = contextBudgetChars(model)
        var removed = 0

        // Build batches that stay within the context budget.
        val batches = buildBatches(matches.map { it.id to Pii.mask(it.body) }, budget)
        val processedIds = mutableSetOf<Long>()

        for (batch in batches) {
            val verdict = queryAi(batch, key, model)
            if (verdict == null) {
                // AI failed for this batch — skip marking so it retries next run.
                AppLog.w("PromotionalChecker: batch failed, will retry ${batch.size} items")
                continue
            }

            for ((rawId, isTransactional, exclusionRegex) in verdict) {
                processedIds += rawId
                if (!isTransactional) {
                    txnRepo.deleteByRawSmsId(rawId)
                    rawSmsDao.updateStatus(rawId, RawStatus.IGNORED, null)
                    removed++
                    if (exclusionRegex != null && runCatching { Regex(exclusionRegex) }.isSuccess) {
                        val original = matches.firstOrNull { it.id == rawId }?.body
                        exclusionDao.insert(
                            PromotionalExclusionEntity(
                                bodyRegex = exclusionRegex,
                                sampleSms = original,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                        AppLog.aiApplied("promotional_check", "saved_exclusion regex=$exclusionRegex")
                    }
                }
            }
        }

        // Mark all SQL-pre-filtered candidates as checked (including non-heuristic rows and
        // successful AI verdicts), so they are never re-queued. Failed batches are excluded
        // above and will be retried by the next worker run.
        val toMark = candidates.map { it.id }.filter { it in processedIds || !matches.any { m -> m.id == it } }
        if (toMark.isNotEmpty()) rawSmsDao.markPromoChecked(toMark)

        if (removed > 0) loadExclusions()

        AppLog.i("PromotionalChecker: batch done removed=$removed checked=${toMark.size}")
        return@withContext removed
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private data class AiVerdict(val rawId: Long, val isTransactional: Boolean, val exclusionRegex: String?)

    /** Split [items] (rawId, maskedBody) into batches that each fit within [budgetChars]. */
    private fun buildBatches(items: List<Pair<Long, String>>, budgetChars: Int): List<List<Pair<Long, String>>> {
        val batches = mutableListOf<List<Pair<Long, String>>>()
        val current = mutableListOf<Pair<Long, String>>()
        var used = 0

        for (item in items) {
            val cost = item.second.length + ENTRY_OVERHEAD
            if (current.isNotEmpty() && used + cost > budgetChars) {
                batches += current.toList()
                current.clear()
                used = 0
            }
            current += item
            used += cost
        }
        if (current.isNotEmpty()) batches += current

        return batches
    }

    private suspend fun queryAi(
        batch: List<Pair<Long, String>>,
        apiKey: String,
        model: String,
    ): List<AiVerdict>? {
        val prompt = buildBatchPrompt(batch)
        val content = when (val r = client.complete(apiKey, model, prompt, "promotional_check")) {
            is OpenRouterClient.Result.Failure -> {
                AppLog.aiSkipped("promotional_check", "ai_failure: ${r.message}")
                return null
            }
            is OpenRouterClient.Result.Success -> r.content
        }
        return parseBatchResponse(content, batch)
    }

    private fun parseBatchResponse(
        content: String,
        batch: List<Pair<Long, String>>,
    ): List<AiVerdict>? {
        return try {
            val raw = content.trim()
            val start = raw.indexOf('[')
            val end = raw.lastIndexOf(']')
            if (start < 0 || end <= start) return null

            val arr = JSONArray(raw.substring(start, end + 1))
            val verdicts = mutableListOf<AiVerdict>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val idx = obj.getInt("idx") - 1 // 1-based in prompt → 0-based index
                if (idx < 0 || idx >= batch.size) continue
                val rawId = batch[idx].first
                val isTransactional = obj.optBoolean("isTransactional", true)
                val regex = obj.optString("exclusionRegex").takeIf { it.isNotBlank() }
                verdicts += AiVerdict(rawId, isTransactional, regex)
            }
            verdicts
        } catch (e: Exception) {
            AppLog.aiSkipped("promotional_check", "parse_error: ${e.message}")
            null
        }
    }

    private fun buildBatchPrompt(batch: List<Pair<Long, String>>): String = buildString {
        append("You are a financial SMS classifier for a personal-finance app.\n\n")
        append("For each numbered SMS body below, decide:\n")
        append("- TRANSACTION: money HAS already moved (debited or credited right now, past tense).\n")
        append("- PROMOTIONAL: offers a loan, credit-limit increase, invites to apply, or similar marketing.\n\n")
        batch.forEachIndexed { i, (_, body) ->
            append("[${i + 1}] \"$body\"\n")
        }
        append("\nRespond ONLY with a JSON array, same count and order, no markdown:\n")
        append("[{\"idx\":1,\"isTransactional\":true}, {\"idx\":2,\"isTransactional\":false,\"exclusionRegex\":\"<java regex>\"}]\n\n")
        append("For promotional items, exclusionRegex must match the core promotional phrase ")
        append("(NOT the amount or account number) and be broad enough to catch variants.")
    }

    companion object {
        // Phrases that appear in promotional/marketing SMS but not in real transaction alerts.
        val PROMO_CUE: Regex = Regex(
            """(?i)\b(pre[- ]?approved|pre[- ]?qualified|you(?:'re| are) eligible|""" +
            """loan offer|personal loan offer|apply (?:now|today)|avail now|""" +
            """get (?:up to|upto)|click (?:here|below)|tap here|call (?:us|now|\d{5,})|""" +
            """exclusive offer|limited (?:time )?offer|offer valid|no[- ]cost emi|""" +
            """0%\s*(?:interest|emi)|interest rate of \d|upgrade.{0,20}limit|""" +
            """increase.{0,20}(?:your\s+)?(?:credit\s+)?limit|""" +
            """credit limit (?:increased|raised|enhanced)|instant (?:loan|credit) offer)\b""",
        )

        /**
         * Estimated context window (tokens) per model slug prefix. 60 % of this is the input
         * budget; the remaining 40 % is left for the AI response and system overhead.
         * Defaults to 8 192 (conservative) for unknown models.
         */
        private fun contextTokensFor(model: String): Int = when {
            model.contains("deepseek") -> 64_000
            model.contains("claude") -> 200_000
            model.contains("gemini-1.5") -> 1_000_000
            model.contains("gemini") -> 32_000
            model.contains("gpt-4o") -> 128_000
            model.contains("gpt-4") -> 8_192
            model.contains("llama-3") -> 128_000
            model.contains("mistral") -> 32_000
            else -> 8_192
        }

        /** Max chars for all SMS bodies in one batch prompt. */
        fun contextBudgetChars(model: String): Int {
            val tokens = contextTokensFor(model)
            // 60 % budget, ~4 chars/token, minus fixed template overhead + response reserve.
            return (tokens * 0.60 * 4 - PROMPT_TEMPLATE_CHARS - RESPONSE_RESERVE_CHARS).toInt()
                .coerceAtLeast(2_000)
        }

        private const val PROMPT_TEMPLATE_CHARS = 800   // fixed header + footer
        private const val RESPONSE_RESERVE_CHARS = 4_000 // tokens for AI reply (~1 000 tokens)
        private const val ENTRY_OVERHEAD = 20            // index tag + quotes + newline per entry
    }
}
