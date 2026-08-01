package online.velozen.spendvault.data.repository

import online.velozen.spendvault.data.DefaultCategories
import online.velozen.spendvault.data.db.CategoryDao
import online.velozen.spendvault.data.db.CategoryEntity
import online.velozen.spendvault.data.db.CategoryRuleEntity
import online.velozen.spendvault.parser.Categorizer
import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val dao: CategoryDao) {

    suspend fun seedIfEmpty() {
        if (dao.categoryCount() == 0) {
            dao.insertCategories(DefaultCategories.categories)
            DefaultCategories.rules.forEach { (matcher, categoryId) ->
                dao.insertRule(
                    CategoryRuleEntity(matcher = matcher, categoryId = categoryId, source = "BUILTIN"),
                )
            }
        }
    }

    /**
     * Ensure the built-in "Card Payment" category row exists. Insert is IGNORE-on-conflict, so this
     * is a no-op for fresh installs (already seeded) and back-fills the row for upgraded users whose
     * DB was seeded before this category existed.
     */
    suspend fun ensureCardPaymentCategory() {
        dao.insertCategories(
            DefaultCategories.categories.filter { it.id == DefaultCategories.CARD_PAYMENT_ID },
        )
    }

    suspend fun categorizer(): Categorizer =
        Categorizer(dao.allRules().map { Categorizer.Rule(it.matcher, it.categoryId) })

    suspend fun addUserRule(matcher: String, categoryId: Long) {
        val m = matcher.lowercase()
        dao.deleteUserRule(m) // replace any prior USER rule for this merchant so the latest pick wins
        dao.insertRule(
            CategoryRuleEntity(matcher = m, categoryId = categoryId, source = "USER"),
        )
    }

    /**
     * Remember an AI-suggested merchant→category mapping as a rule so future transactions from the
     * same merchant categorise offline (no repeat AI call). Inserted with IGNORE on conflict, so a
     * pre-existing rule for this matcher (BUILTIN or USER) always wins.
     */
    suspend fun addAiRule(matcher: String, categoryId: Long) {
        dao.insertRule(
            CategoryRuleEntity(matcher = matcher.lowercase().trim(), categoryId = categoryId, source = "AI"),
        )
    }

    /** The category id of an AI-sourced rule for [matcher], or null if none — used by the debug view. */
    suspend fun aiRuleCategory(matcher: String): Long? {
        val m = matcher.lowercase().trim()
        return dao.allRules().firstOrNull { it.source == "AI" && it.matcher == m }?.categoryId
    }

    /** Create a user category; returns its new id. */
    suspend fun createCategory(name: String, icon: String, color: Long): Long =
        dao.insertCategory(CategoryEntity(name = name.trim(), icon = icon, color = color))

    fun observeCategories(): Flow<List<CategoryEntity>> = dao.observeCategories()
    fun observeRules(): Flow<List<CategoryRuleEntity>> = dao.observeRules()
    suspend fun all(): List<CategoryEntity> = dao.allCategories()
}
