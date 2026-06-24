package com.spendlens.app.data.repository

import com.spendlens.app.data.DefaultCategories
import com.spendlens.app.data.db.CategoryDao
import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.CategoryRuleEntity
import com.spendlens.app.parser.Categorizer
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

    suspend fun categorizer(): Categorizer =
        Categorizer(dao.allRules().map { Categorizer.Rule(it.matcher, it.categoryId) })

    suspend fun addUserRule(matcher: String, categoryId: Long) {
        val m = matcher.lowercase()
        dao.deleteUserRule(m) // replace any prior USER rule for this merchant so the latest pick wins
        dao.insertRule(
            CategoryRuleEntity(matcher = m, categoryId = categoryId, source = "USER"),
        )
    }

    /** Create a user category; returns its new id. */
    suspend fun createCategory(name: String, icon: String, color: Long): Long =
        dao.insertCategory(CategoryEntity(name = name.trim(), icon = icon, color = color))

    fun observeCategories(): Flow<List<CategoryEntity>> = dao.observeCategories()
    fun observeRules(): Flow<List<CategoryRuleEntity>> = dao.observeRules()
    suspend fun all(): List<CategoryEntity> = dao.allCategories()
}
