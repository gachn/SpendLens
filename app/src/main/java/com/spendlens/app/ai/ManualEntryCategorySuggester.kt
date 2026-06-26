package com.spendlens.app.ai

import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.TransactionEntity

data class CategorySuggestion(
    val categoryId: Long,
    val name: String,
    val icon: String,
    val confidence: Float,
)

/**
 * Suggest categories for manually-entered merchants in real-time.
 * Uses fast local matching first (no AI call), with optional AI fallback.
 */
object ManualEntryCategorySuggester {

    /**
     * Get category suggestions for a merchant name.
     * Returns up to 3 suggestions sorted by confidence.
     * Works entirely offline using pattern matching and user history.
     */
    fun suggestLocally(
        merchantName: String,
        recentTransactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
    ): List<CategorySuggestion> {
        if (merchantName.isBlank()) return emptyList()

        val merchant = merchantName.lowercase()
        val categoryScores = mutableMapOf<Long, Float>()

        categories.forEach { cat ->
            var score = 0f

            // Pattern matching against category keywords
            val patterns = getCategoryPatterns(cat.name)
            patterns.forEach { pattern ->
                if (merchant.contains(pattern)) {
                    score = maxOf(score, 0.95f)
                }
            }

            // Check against user's historical transactions
            val historicalMerchants = recentTransactions
                .filter { it.categoryId == cat.id }
                .map { it.counterparty.lowercase() }
                .distinct()

            historicalMerchants.forEach { existing ->
                val similarity = stringSimilarity(merchant, existing)
                if (similarity > 0.6f) {
                    score = maxOf(score, similarity * 0.8f)
                }
            }

            if (score > 0.5f) {
                categoryScores[cat.id] = score
            }
        }

        return categoryScores.entries
            .sortedByDescending { it.value }
            .take(3)
            .mapNotNull { (catId, score) ->
                categories.find { it.id == catId }?.let { cat ->
                    CategorySuggestion(
                        categoryId = catId,
                        name = cat.name,
                        icon = cat.icon,
                        confidence = score.coerceIn(0f, 1f),
                    )
                }
            }
    }

    private fun getCategoryPatterns(categoryName: String): List<String> {
        return when {
            "food" in categoryName.lowercase() || "restaurant" in categoryName.lowercase() ->
                listOf("swiggy", "zomato", "pizza", "burger", "cafe", "coffee", "restaurant",
                    "diner", "eatery", "bistro", "bakery", "bar", "pub", "mcdonalds", "kfc")

            "transport" in categoryName.lowercase() || "travel" in categoryName.lowercase() ->
                listOf("uber", "ola", "taxi", "auto", "bus", "train", "flight", "airline",
                    "railway", "petrol", "fuel", "parking", "toll", "gas station")

            "shopping" in categoryName.lowercase() || "retail" in categoryName.lowercase() ->
                listOf("amazon", "flipkart", "mall", "store", "shop", "retail", "market",
                    "bazaar", "emporium", "walmart", "target", "costco")

            "entertainment" in categoryName.lowercase() ->
                listOf("movie", "cinema", "theater", "concert", "netflix", "spotify", "game",
                    "gaming", "entertainment", "streaming", "hbo", "disney", "youtube")

            "health" in categoryName.lowercase() || "medical" in categoryName.lowercase() ||
            "gym" in categoryName.lowercase() ->
                listOf("hospital", "clinic", "doctor", "pharmacy", "medicine", "health",
                    "gym", "fitness", "yoga", "ayurveda", "dental", "physio", "lab")

            "bills" in categoryName.lowercase() || "utilities" in categoryName.lowercase() ->
                listOf("electricity", "water", "internet", "mobile", "phone", "bill",
                    "utility", "provider", "broadband", "gas", "heating")

            "groceries" in categoryName.lowercase() ->
                listOf("supermarket", "grocery", "market", "store", "fresh", "whole foods",
                    "trader joes", "dmart", "bigbasket", "blinkit")

            else -> emptyList()
        }
    }

    private fun stringSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1f
        if (s1.isEmpty() || s2.isEmpty()) return 0f

        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1

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
