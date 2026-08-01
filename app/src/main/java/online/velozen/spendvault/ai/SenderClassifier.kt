package com.spendlens.app.ai

import com.spendlens.app.data.prefs.AiConfigStore
import com.spendlens.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Classifies SMS sender IDs as financial (bank / wallet / NBFC that sends transaction alerts) or
 * non-financial (marketing, government info, EPFO, OTP, e-commerce) using the configured LLM.
 *
 * Callers pass a batch of raw sender addresses (e.g. ["VK-HDFCBK", "TM-EPFOHO", "AD-FLIPKRT"]);
 * the model returns a yes/no for each. Unparseable lines are dropped — callers should fall back
 * to treating the sender as unknown and re-trying later.
 *
 * Only sender IDs — never SMS body text or amounts — are sent off-device.
 */
class SenderClassifier(
    private val client: OpenRouterClient,
    private val aiConfigStore: AiConfigStore,
) {

    /**
     * Classify [senders] in one LLM call. Returns a map of sender → isFinancial for every sender
     * the model returned a parseable answer for. Senders the model skipped are absent from the map.
     *
     * Returns an empty map when AI is disabled or no API key is configured.
     */
    suspend fun classify(senders: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        if (senders.isEmpty()) return@withContext emptyMap()

        if (!aiConfigStore.isUsable()) {
            AppLog.aiSkipped("sender_classify", "ai_disabled_or_no_key_or_not_premium")
            return@withContext emptyMap()
        }
        val key = aiConfigStore.effectiveKey() ?: return@withContext emptyMap()

        val numberedList = senders.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        val prompt = """
You are classifying Indian SMS sender IDs to determine if they send actual bank/payment transaction alerts.

Financial (answer yes): banks, UPI apps, payment wallets, credit card issuers, NBFCs, insurance companies, stockbrokers — anything that sends debit/credit transaction notifications.
Non-financial (answer no): EPFO/government services, loan marketing, e-commerce delivery, OTP providers, telecom offers, promotional messages, bill payment reminders from non-banks.

For each sender below respond ONLY with the line number and yes or no, one per line. No other text.

$numberedList
        """.trimIndent()

        val result = client.complete(key, aiConfigStore.effectiveModel(), prompt, "sender_classify")
        val content = when (result) {
            is OpenRouterClient.Result.Success -> result.content
            is OpenRouterClient.Result.Failure -> return@withContext emptyMap()
        }

        parseResponse(senders, content)
    }

    private fun parseResponse(senders: List<String>, content: String): Map<String, Boolean> {
        val out = mutableMapOf<String, Boolean>()
        val lineRe = Regex("""^(\d+)\.\s*(yes|no)\s*$""", RegexOption.IGNORE_CASE)
        content.lines().forEach { line ->
            val m = lineRe.find(line.trim()) ?: return@forEach
            val idx = m.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEach
            val sender = senders.getOrNull(idx) ?: return@forEach
            out[sender] = m.groupValues[2].equals("yes", ignoreCase = true)
        }
        return out
    }
}
