package com.spendlens.app.ai

import com.spendlens.app.data.db.RawSmsEntity
import com.spendlens.app.data.db.RawStatus
import com.spendlens.app.data.prefs.AiConfigStore
import com.spendlens.app.parser.model.GeneratedPattern
import com.spendlens.app.util.AppLog

/**
 * Premium pattern learner: asks the configured OpenRouter model (a stronger model than the
 * Free-tier default, see [AiConfig.PREMIUM_DEFAULT_MODEL]) to propose a parsing regex for an SMS
 * format no existing pattern matched. Returns null (never calls the network) unless the plan is
 * Premium, AI is enabled, and a key is configured — see [AiConfigStore.isUsable].
 *
 * Used via [LayeredPatternGenerator], which falls back to [HeuristicPatternGenerator] whenever
 * this returns null, so Premium never parses worse than Free.
 */
class AiPatternGenerator(
    private val client: OpenRouterClient,
    private val aiConfigStore: AiConfigStore,
) : PatternGenerator {

    override val requiresMasking: Boolean get() = true

    override suspend fun generate(body: String, sender: String): GeneratedPattern? {
        if (!aiConfigStore.isUsable()) return null
        val key = aiConfigStore.effectiveKey() ?: return null

        val prompt = PromptGenerator.generate(
            listOf(
                RawSmsEntity(
                    sender = sender,
                    body = body,
                    receivedAt = 0L,
                    contentHash = "",
                    status = RawStatus.UNPARSED,
                ),
            ),
        )

        val model = aiConfigStore.effectiveModel()
        val content = when (val r = client.complete(key, model, prompt, operation = "pattern_generate_premium")) {
            is OpenRouterClient.Result.Success -> r.content
            is OpenRouterClient.Result.Failure -> {
                AppLog.aiSkipped("pattern_generate_premium", "call_failed: ${r.message}")
                return null
            }
        }

        return parse(content, model)
    }

    private fun parse(raw: String, model: String): GeneratedPattern? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        val json = runCatching { org.json.JSONObject(raw.substring(start, end + 1)) }.getOrNull() ?: return null

        val bodyRegex = json.optString("bodyRegex").takeIf { it.isNotBlank() } ?: return null
        runCatching { Regex(bodyRegex) }.getOrNull() ?: return null
        val senderRegex = json.optString("senderRegex").takeIf { it.isNotBlank() && it != "null" }
        val name = json.optString("name").takeIf { it.isNotBlank() } ?: "AI-learned pattern"

        return GeneratedPattern(
            name = name,
            bodyRegex = bodyRegex,
            senderRegex = senderRegex,
            fieldNotes = "premium AI ($model)",
            viaAi = true,
        )
    }
}
