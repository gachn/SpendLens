package com.spendlens.app.ai

import org.json.JSONArray
import org.json.JSONException

/** One canonical brand and the variant merchant names that should be merged into it. */
data class MerchantMergeGroup(
    val canonical: String,
    val aliases: List<String>,
)

/**
 * Pure (Android-free) prompt builder + response parser for the AI "consolidate merchant names"
 * flow. The model is given every known merchant display name and asked to group obvious
 * variants of the same brand under one canonical name; applying the result merges merchants on
 * the existing Merchants screen (rename collapses variants that share a display name).
 */
object MerchantConsolidation {

    /** Build the consolidation prompt listing all [names]. */
    fun buildPrompt(names: List<String>): String = buildString {
        append("You are a merchant-name consolidation assistant for SpendLens, a personal expense app.\n")
        append("Below is the list of distinct merchant names currently stored. Many are variants of the ")
        append("same real brand (different casing, branch suffixes, payment-gateway prefixes, typos, etc.).\n\n")
        append("Merchant names:\n")
        names.forEach { append("- ").append(it).append('\n') }
        append('\n')
        append("Group ONLY the names that clearly refer to the same real-world brand. For each group ")
        append("choose the cleanest canonical brand name (prefer an existing name from the list).\n")
        append("Do NOT merge names that might be different businesses. Ignore names that have no variants.\n\n")
        append("Respond ONLY with a valid JSON array, no markdown or prose, matching this schema:\n")
        append("[\n")
        append("  { \"canonical\": \"Swiggy\", \"aliases\": [\"SWIGGY\", \"Swiggy*Delhi\", \"BUNDL TECH\"] }\n")
        append("]\n")
        append("Each \"aliases\" entry MUST be one of the names exactly as listed above. Only include ")
        append("groups that have at least one alias different from the canonical name.")
    }

    /**
     * Parse the model response into merge groups. Tolerates surrounding prose / markdown fences by
     * extracting the outermost JSON array. Groups with a blank canonical are dropped; alias entries
     * are trimmed and de-blanked. Never throws — returns an empty list on any failure.
     */
    fun parse(text: String?): List<MerchantMergeGroup> {
        val json = extractArray(text) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val out = mutableListOf<MerchantMergeGroup>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val canonical = obj.optString("canonical").trim()
                if (canonical.isBlank()) continue
                val aliasArr = obj.optJSONArray("aliases")
                val aliases = buildList {
                    if (aliasArr != null) {
                        for (j in 0 until aliasArr.length()) {
                            val a = aliasArr.optString(j).trim()
                            if (a.isNotBlank()) add(a)
                        }
                    }
                }
                out.add(MerchantMergeGroup(canonical, aliases))
            }
            out
        } catch (_: JSONException) {
            emptyList()
        }
    }

    /** Extract the substring from the first '[' to the last ']' so wrapped responses still parse. */
    private fun extractArray(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val first = input.indexOf('[')
        val last = input.lastIndexOf(']')
        if (first == -1 || last == -1 || last <= first) return null
        return input.substring(first, last + 1)
    }
}
