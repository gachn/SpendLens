package com.spendlens.app.ai

import org.json.JSONArray
import org.json.JSONObject

/** One SMS's worth of the batched AI response — see [PromptGenerator]'s schema. */
data class AiSmsResult(
    val isFinancial: Boolean,
    val bodyRegex: String?,
    val senderRegex: String?,
    val name: String?,
)

/**
 * Parses the AI's response to a (possibly batched) [PromptGenerator] prompt. Tolerant of a short,
 * truncated, or malformed array — missing/unparseable indices come back null so the caller can
 * fall back to the regex pipeline for just that item rather than discarding the whole batch,
 * mirroring how [SenderClassifier] tolerates partial answers.
 */
object AiBatchResult {

    fun parseBatch(count: Int, raw: String): List<AiSmsResult?> {
        if (count == 1) {
            parseSingleObject(raw)?.let { return listOf(it) }
        }

        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start == -1 || end == -1 || end <= start) return List(count) { null }
        val array = runCatching { JSONArray(raw.substring(start, end + 1)) }.getOrNull()
            ?: return List(count) { null }

        return (0 until count).map { i -> array.optJSONObject(i)?.let(::parseOne) }
    }

    private fun parseSingleObject(raw: String): AiSmsResult? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        val json = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull() ?: return null
        return parseOne(json)
    }

    private fun parseOne(json: JSONObject): AiSmsResult {
        val isFinancial = json.optBoolean("isFinancial", false)
        val bodyRegex = json.optString("bodyRegex")
            .takeIf { it.isNotBlank() && it != "null" }
            ?.takeIf { runCatching { Regex(it) }.isSuccess }
        val senderRegex = json.optString("senderRegex").takeIf { it.isNotBlank() && it != "null" }
        val name = json.optString("name").takeIf { it.isNotBlank() && it != "null" }
        return AiSmsResult(isFinancial = isFinancial, bodyRegex = bodyRegex, senderRegex = senderRegex, name = name)
    }
}
