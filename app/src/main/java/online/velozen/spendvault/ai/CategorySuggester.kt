package com.spendlens.app.ai

import org.json.JSONArray
import org.json.JSONException

/** One merchant name mapped by the AI to an existing category id. */
data class CategoryAssignment(
    val merchant: String,
    val categoryId: Long,
)

/**
 * Pure (Android-free) prompt builder + response parser for the AI "auto-categorise unrecognised
 * transactions" flow. The model is given the app's existing category list and the merchant names of
 * transactions that no keyword rule could classify, and asked to assign the best-fitting category
 * to each merchant it confidently recognises. Only the merchant name leaves the device — never the
 * SMS body or amounts — mirroring the privacy posture of [MerchantConsolidation].
 */
object CategorySuggester {

    /**
     * Build the categorisation prompt listing every [categories] (id → name) the model may choose
     * from and every uncategorised [merchants] name. The model must answer with merchant→categoryId
     * pairs, choosing a category id strictly from the supplied list and omitting merchants it cannot
     * confidently place.
     */
    fun buildPrompt(merchants: List<String>, categories: List<Pair<Long, String>>): String = buildString {
        append("You are a transaction-categorisation assistant for SpendLens, a personal expense app.\n")
        append("Assign each merchant below to the single best-fitting category.\n\n")
        append("Categories (use the numeric id, never invent new ones):\n")
        categories.forEach { (id, name) -> append("- ").append(id).append(": ").append(name).append('\n') }
        append('\n')
        append("Merchant names to categorise:\n")
        merchants.forEach { append("- ").append(it).append('\n') }
        append('\n')
        append("Rules:\n")
        append("- Pick a categoryId ONLY from the list above.\n")
        append("- Omit any merchant you cannot confidently categorise — do not guess.\n")
        append("- Each \"merchant\" value MUST be one of the names exactly as listed above.\n\n")
        append("Respond ONLY with a valid JSON array, no markdown or prose, matching this schema:\n")
        append("[\n")
        append("  { \"merchant\": \"Swiggy\", \"categoryId\": 1 }\n")
        append("]")
    }

    /**
     * Parse the model response into assignments. Tolerates surrounding prose / markdown fences by
     * extracting the outermost JSON array. Entries with a blank merchant or a categoryId not present
     * in [validIds] are dropped. Never throws — returns an empty list on any failure.
     */
    fun parse(text: String?, validIds: Set<Long>): List<CategoryAssignment> {
        val json = extractArray(text) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val out = mutableListOf<CategoryAssignment>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val merchant = obj.optString("merchant").trim()
                if (merchant.isBlank()) continue
                val categoryId = obj.optLong("categoryId", -1L)
                if (categoryId !in validIds) continue
                out.add(CategoryAssignment(merchant, categoryId))
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
