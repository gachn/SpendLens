package com.spendlens.app.data.repository

import com.spendlens.app.data.db.PatternDao
import com.spendlens.app.data.db.PatternSource
import com.spendlens.app.data.db.SmsPatternEntity
import com.spendlens.app.parser.BuiltinPatterns
import com.spendlens.app.parser.model.CompiledPattern
import kotlinx.coroutines.flow.Flow

/**
 * Owns the pattern store and a compiled-pattern cache. Seeds built-ins on first run;
 * caches compiled regexes and invalidates whenever the set changes (learning).
 */
class PatternRepository(private val dao: PatternDao) {

    @Volatile
    private var cache: List<CompiledPattern>? = null

    suspend fun seedIfEmpty() {
        if (dao.count() == 0) {
            BuiltinPatterns.seeds.forEach { seed ->
                dao.insert(
                    SmsPatternEntity(
                        name = seed.name,
                        senderRegex = seed.senderRegex,
                        bodyRegex = seed.bodyRegex,
                        priority = seed.priority,
                        source = PatternSource.BUILTIN,
                    ),
                )
            }
            invalidate()
        }
    }

    suspend fun compiled(): List<CompiledPattern> {
        cache?.let { return it }
        val list = dao.enabledOrdered().mapNotNull { it.compileOrNull() }
        cache = list
        return list
    }

    suspend fun savePattern(entity: SmsPatternEntity): Long {
        val id = dao.insert(entity)
        invalidate()
        return id
    }

    suspend fun incrementMatch(id: Long) = dao.incrementMatch(id, System.currentTimeMillis())

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        invalidate()
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        invalidate()
    }

    suspend fun clearAll() {
        dao.clear()
        invalidate()
    }

    fun observeAll(): Flow<List<SmsPatternEntity>> = dao.observeAll()

    private fun invalidate() {
        cache = null
    }

    private fun SmsPatternEntity.compileOrNull(): CompiledPattern? = runCatching {
        CompiledPattern(
            id = id,
            priority = priority,
            body = Regex(bodyRegex),
            sender = senderRegex?.let { Regex(it) },
        )
    }.getOrNull()
}
