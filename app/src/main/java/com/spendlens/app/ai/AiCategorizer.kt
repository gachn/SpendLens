package com.spendlens.app.ai

import com.spendlens.app.di.AppContainer
import com.spendlens.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Orchestrates the AI fallback that categorises transactions no keyword rule could classify.
 *
 * Triggered automatically after the inbox finishes importing and after each newly-parsed SMS (see
 * [com.spendlens.app.work.AiCategorizeWorker]). Every transaction it sends to the model is stamped
 * [com.spendlens.app.data.db.TransactionEntity.aiCategorizeAttempted] = true — whether or not the
 * AI managed to classify it — so an SMS the AI couldn't place is never re-sent off-device on the
 * next sync. The flag is cleared in bulk only when the user explicitly requests a re-run.
 *
 * Each merchant the AI does classify is also remembered as a category rule
 * ([com.spendlens.app.data.repository.CategoryRepository.addAiRule]) so future transactions from
 * the same merchant categorise offline, with no repeat API call.
 *
 * Only merchant names leave the device — never the SMS body or amounts.
 */
class AiCategorizer(private val container: AppContainer) {

    /** Outcome of one [run] pass. [throttled] means the per-minute auto-run gate skipped the call. */
    data class Outcome(val categorized: Int, val attempted: Int, val throttled: Boolean = false)

    /** True while an off-device categorisation call is in flight — drives the UI "analysing" banner. */
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** True when the AI flag is on and a usable key exists. */
    fun aiUsable(): Boolean = container.aiConfigStore.isUsable()

    /**
     * Categorise one capped batch of uncategorised, not-yet-attempted transactions.
     *
     * Auto-triggered calls (`force = false`) are rate-limited to one off-device request per
     * [AUTO_MIN_INTERVAL_MS] so a burst of incoming SMS can't fan out into many API calls. Each
     * pass sends at most [MERCHANT_CAP] distinct merchant names to bound prompt size and cost; the
     * remaining backlog is picked up by the next pass. A user-requested re-run passes `force = true`
     * to bypass the throttle (and is looped to completion by the worker).
     *
     * @return an [Outcome] with how many rows were categorised / attempted, or [Outcome.throttled].
     */
    suspend fun run(force: Boolean = false): Outcome = withContext(Dispatchers.IO) {
        val store = container.aiConfigStore
        val key = store.effectiveKey()
        if (!store.isEnabled() || key == null) {
            AppLog.aiSkipped("auto_categorize", "ai_disabled_or_no_key")
            return@withContext Outcome(0, 0)
        }

        if (!force) {
            val sinceLast = System.currentTimeMillis() - store.lastAutoCategorizeAt()
            if (sinceLast < AUTO_MIN_INTERVAL_MS) {
                AppLog.aiSkipped("auto_categorize", "throttled_${sinceLast}ms_since_last")
                return@withContext Outcome(0, 0, throttled = true)
            }
        }

        val txns = container.transactionRepository.listForAiCategorize()
        if (txns.isEmpty()) {
            AppLog.aiSkipped("auto_categorize", "no_uncategorized_transactions")
            return@withContext Outcome(0, 0)
        }

        // Distinct, classifiable merchant names, capped per pass. "Unknown" rows can't be helped by
        // the model, so they are excluded from the prompt but still retired (marked attempted) below.
        val merchants = txns.asSequence()
            .map { it.counterparty }
            .filter { it.isNotBlank() && it != "Unknown" }
            .distinct()
            .take(MERCHANT_CAP)
            .toList()

        if (merchants.isEmpty()) {
            // Only blank/Unknown rows remain — retire them so they aren't reconsidered every sync.
            val unknownIds = txns.filter { it.counterparty.isBlank() || it.counterparty == "Unknown" }.map { it.id }
            container.transactionRepository.markAiCategorizeAttempted(unknownIds)
            AppLog.aiSkipped("auto_categorize", "no_named_merchants")
            return@withContext Outcome(0, unknownIds.size)
        }

        val selected = merchants.map { it.lowercase() }.toSet()
        // Only this pass's merchants (plus the always-unhelpable blank/Unknown rows) are touched;
        // the rest stay not-attempted for the next pass.
        val batch = txns.filter {
            it.counterparty.lowercase() in selected || it.counterparty.isBlank() || it.counterparty == "Unknown"
        }

        val categories = container.categoryRepository.all().map { it.id to it.name }
        val validIds = categories.map { it.first }.toSet()
        val prompt = CategorySuggester.buildPrompt(merchants, categories)

        // Stamp the throttle clock on every completed call attempt (success or failure) so repeated
        // failures can't hammer the endpoint faster than the interval.
        store.setLastAutoCategorizeAt(System.currentTimeMillis())

        _running.value = true
        try {
            val content = when (
                val r = container.openRouterClient.complete(key, store.effectiveModel(), prompt, "auto_categorize")
            ) {
                is OpenRouterClient.Result.Failure -> {
                    // Don't mark attempted on a transient failure — let a later pass retry.
                    return@withContext Outcome(0, 0)
                }
                is OpenRouterClient.Result.Success -> r.content
            }

            val assignments = CategorySuggester.parse(content, validIds)
            // Case-insensitive lookup so a minor casing drift in the model's echo still matches.
            val byMerchant = assignments.associateBy { it.merchant.lowercase() }

            var categorized = 0
            val attemptedOnly = mutableListOf<Long>()
            val rulesAdded = mutableSetOf<String>()

            for (txn in batch) {
                val assignment = byMerchant[txn.counterparty.lowercase()]
                if (assignment != null) {
                    container.transactionRepository.setCategoryAndAiAttempted(txn.id, assignment.categoryId)
                    categorized++
                    if (rulesAdded.add(txn.counterparty.lowercase())) {
                        container.categoryRepository.addAiRule(txn.counterparty, assignment.categoryId)
                    }
                } else {
                    attemptedOnly += txn.id
                }
            }
            if (attemptedOnly.isNotEmpty()) {
                container.transactionRepository.markAiCategorizeAttempted(attemptedOnly)
            }

            AppLog.aiApplied(
                "auto_categorize",
                "categorized=$categorized attempted=${batch.size} merchants=${merchants.size} rules=${rulesAdded.size}",
            )
            return@withContext Outcome(categorized, batch.size)
        } finally {
            _running.value = false
        }
    }

    /**
     * Clear the attempted flag on still-uncategorised transactions so a user-requested re-run
     * reconsiders rows the AI previously couldn't (or wasn't allowed to) classify. Returns how many
     * rows were reset.
     */
    suspend fun resetAttempts(): Int = container.transactionRepository.resetAiCategorizeAttempted()

    companion object {
        /** Minimum gap between auto-triggered AI calls — coalesces SMS bursts into one request. */
        const val AUTO_MIN_INTERVAL_MS = 60_000L

        /** Max distinct merchant names per AI call — bounds prompt size and per-call cost. */
        const val MERCHANT_CAP = 40
    }
}
