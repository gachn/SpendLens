package online.velozen.spendvault.data.repository

import online.velozen.spendvault.data.db.PatternDao
import online.velozen.spendvault.data.db.PatternSource
import online.velozen.spendvault.data.db.SmsPatternEntity
import online.velozen.spendvault.parser.BuiltinPatterns
import online.velozen.spendvault.parser.model.CompiledPattern
import kotlinx.coroutines.flow.Flow

/**
 * Owns the pattern store and a compiled-pattern cache. Seeds built-ins on first run;
 * caches compiled regexes and invalidates whenever the set changes (learning).
 */
class PatternRepository(private val dao: PatternDao) {

    @Volatile
    private var cache: List<CompiledPattern>? = null

    /**
     * Insert any builtin seed not already present (matched by name). Runs every launch so seeds
     * added in app updates land on existing installs, not just fresh ones. User-disabled patterns
     * keep their name, so they are not re-inserted.
     */
    suspend fun seedIfEmpty() {
        val existing = dao.names().toSet()
        val missing = BuiltinPatterns.seeds.filter { it.name !in existing }
        if (missing.isEmpty()) return
        missing.forEach { seed ->
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

    /** Count of patterns from a given [PatternSource] — surfaced on the Developer-options screen. */
    suspend fun countBySource(source: String): Int = dao.countBySource(source)

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
