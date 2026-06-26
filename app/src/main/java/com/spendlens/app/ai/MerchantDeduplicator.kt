package com.spendlens.app.ai

import org.json.JSONException
import org.json.JSONObject

data class MerchantWarning(
    val inputMerchant: String,
    val existingMerchant: String,
    val confidence: Float,
    val transactionCount: Int,
)

/**
 * Warn users when entering merchants that are likely duplicates of existing ones.
 * Integrates with existing merchant consolidation logic.
 */
object MerchantDeduplicator {

    /**
     * Check if merchant name matches existing ones.
     * Returns warning if likely duplicate found.
     */
    fun checkDuplicates(
        inputMerchant: String,
        existingMerchants: Map<String, Int>, // merchant name to count
    ): MerchantWarning? {
        if (inputMerchant.isBlank()) return null

        val input = inputMerchant.lowercase().trim()
        val bestMatch = mutableMapOf<String, Float>()

        existingMerchants.forEach { (existing, count) ->
            val similarity = stringSimilarity(input, existing.lowercase())
            if (similarity > 0.7f) {
                bestMatch[existing] = similarity
            }
        }

        return bestMatch
            .maxByOrNull { it.value }
            ?.let { (existing, similarity) ->
                MerchantWarning(
                    inputMerchant = inputMerchant,
                    existingMerchant = existing,
                    confidence = similarity.coerceIn(0f, 1f),
                    transactionCount = existingMerchants[existing] ?: 0,
                )
            }
    }

    /**
     * Build prompt for AI-assisted merchant deduplication.
     * Only used if user opts for AI help.
     */
    fun buildAiPrompt(
        inputMerchant: String,
        existingMerchants: List<String>,
    ): String = buildString {
        append("You are a merchant name matcher for SpendLens, a personal expense app.\n")
        append("The user entered: \"$inputMerchant\"\n\n")
        append("Existing merchants:\n")
        existingMerchants.take(20).forEach { append("- $it\n") }
        append("\n")
        append("If \"$inputMerchant\" matches one of the existing merchants (same brand, likely variant), ")
        append("respond with ONLY the matched name exactly as listed above.\n")
        append("Otherwise, respond with ONLY \"NO_MATCH\".\n")
    }

    fun parseAiMatch(text: String?): String? {
        val result = text?.trim() ?: return null
        return if (result.equals("NO_MATCH", ignoreCase = true)) null else result.takeIf { it.isNotBlank() }
    }

    private fun stringSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1f
        if (s1.isEmpty() || s2.isEmpty()) return 0f

        // Exact substring match with high score
        if (s1.contains(s2) || s2.contains(s1)) {
            return 0.95f
        }

        // Tokenized comparison (better for multi-word merchants)
        val tokens1 = s1.split(Regex("\\W+")).filter { it.isNotBlank() }
        val tokens2 = s2.split(Regex("\\W+")).filter { it.isNotBlank() }

        val matchingTokens = tokens1.intersect(tokens2.toSet()).size
        val totalTokens = maxOf(tokens1.size, tokens2.size)

        if (totalTokens > 0 && matchingTokens > 0) {
            return maxOf((matchingTokens.toFloat() / totalTokens), editDistanceSimilarity(s1, s2))
        }

        // Levenshtein distance fallback
        return editDistanceSimilarity(s1, s2)
    }

    private fun editDistanceSimilarity(s1: String, s2: String): Float {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1

        if (longer.length == 0) return 1f
        val editDistance = getEditDistance(longer, shorter)
        return (longer.length - editDistance) / longer.length.toFloat()
    }

    private fun getEditDistance(s1: String, s2: String): Int {
        val dp = Array(s2.length + 1) { IntArray(s1.length + 1) }
        for (i in 0..s1.length) dp[0][i] = i
        for (j in 0..s2.length) dp[j][0] = j

        for (i in 1..s2.length) {
            for (j in 1..s1.length) {
                val cost = if (s2[i - 1] == s1[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[s2.length][s1.length]
    }
}
