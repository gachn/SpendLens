package com.spendlens.app.data.repository

import com.spendlens.app.ai.MerchantResolver
import com.spendlens.app.data.MerchantDictionary
import com.spendlens.app.data.db.MerchantAliasEntity
import com.spendlens.app.data.db.MerchantDao
import com.spendlens.app.parser.MerchantNormalizer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.map

/**
 * Resolves a raw merchant token to a canonical display name and caches the result as
 * metadata (in-memory + [MerchantDao] table) so each merchant is resolved once and reused.
 * Resolution order: USER/cached alias → bundled dictionary → web resolver → normalized
 * fallback. docs/DESIGN.md §3.2.
 */
class MerchantRepository(
    private val dao: MerchantDao,
    private val resolver: MerchantResolver,
) {
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun resolveDisplay(rawMerchant: String): String {
        val key = MerchantNormalizer.key(rawMerchant)
        if (key.isBlank()) return MerchantNormalizer.display(rawMerchant)

        cache[key]?.let { return it }
        dao.getByKey(key)?.let { cache[key] = it.displayName; return it.displayName }

        MerchantDictionary.lookup(key)?.let { return save(key, it, "DICTIONARY") }

        // Resolver returns null when offline / no-network impl, falling back to normalization.
        val web = runCatching { resolver.resolve(MerchantNormalizer.display(rawMerchant)) }.getOrNull()
        return if (web != null) save(key, web, "WEB")
        else save(key, MerchantNormalizer.display(rawMerchant), "NORMALIZED")
    }

    /** User correction — wins over everything and is remembered for this merchant. */
    suspend fun setUserName(rawMerchant: String, name: String): String {
        val key = MerchantNormalizer.key(rawMerchant)
        return save(key.ifBlank { name.lowercase().filter { it.isLetterOrDigit() } }, name.trim(), "USER")
    }

    /** Remember user tags for a merchant so future parsed transactions inherit them. */
    suspend fun setUserTags(merchant: String, tags: String?) {
        val key = keyFor(merchant)
        val existing = dao.getByKey(key)
        dao.upsert(
            MerchantAliasEntity(
                rawKey = key,
                displayName = existing?.displayName ?: merchant.trim(),
                source = existing?.source ?: "USER",
                tags = tags,
                logoEmoji = existing?.logoEmoji,
                excludedFromExpense = existing?.excludedFromExpense ?: false,
            ),
        )
    }

    /**
     * Remember that a merchant is (not) excluded from spend/income totals, so future parsed
     * transactions for it inherit the flag. The caller is responsible for updating existing rows.
     */
    suspend fun setExcluded(merchant: String, excluded: Boolean) {
        val key = keyFor(merchant)
        val existing = dao.getByKey(key)
        dao.upsert(
            MerchantAliasEntity(
                rawKey = key,
                displayName = existing?.displayName ?: merchant.trim(),
                source = existing?.source ?: "USER",
                tags = existing?.tags,
                logoEmoji = existing?.logoEmoji,
                excludedFromExpense = excluded,
            ),
        )
    }

    /** Whether new transactions for this merchant should be excluded from totals. */
    suspend fun isExcluded(merchant: String): Boolean {
        val key = MerchantNormalizer.key(merchant)
        if (key.isBlank()) return false
        return dao.getByKey(key)?.excludedFromExpense ?: false
    }

    fun observeExcluded(merchant: String): kotlinx.coroutines.flow.Flow<Boolean> =
        dao.observeByKey(keyFor(merchant)).map { it?.excludedFromExpense ?: false }

    /** Tags previously remembered for this merchant, or null. */
    suspend fun tagsFor(merchant: String): String? {
        val key = MerchantNormalizer.key(merchant)
        if (key.isBlank()) return null
        return dao.getByKey(key)?.tags
    }

    private fun keyFor(merchant: String): String =
        MerchantNormalizer.key(merchant).ifBlank { merchant.lowercase().filter { it.isLetterOrDigit() } }

    private suspend fun save(key: String, display: String, source: String): String {
        // Preserve any user tags already remembered for this merchant across re-resolution.
        val existing = dao.getByKey(key)
        val existingTags = existing?.tags
        val existingLogo = existing?.logoEmoji
        dao.upsert(MerchantAliasEntity(rawKey = key, displayName = display, source = source, tags = existingTags, logoEmoji = existingLogo, excludedFromExpense = existing?.excludedFromExpense ?: false))
        cache[key] = display
        return display
    }

    suspend fun setMerchantEmoji(merchant: String, emoji: String?) {
        val key = keyFor(merchant)
        val existing = dao.getByKey(key)
        dao.upsert(
            MerchantAliasEntity(
                rawKey = key,
                displayName = existing?.displayName ?: merchant.trim(),
                source = existing?.source ?: "USER",
                tags = existing?.tags,
                logoEmoji = emoji,
                excludedFromExpense = existing?.excludedFromExpense ?: false,
            )
        )
    }

    suspend fun getMerchantEmoji(merchant: String): String? {
        val key = keyFor(merchant)
        return dao.getByKey(key)?.logoEmoji
    }

    fun observeAll(): kotlinx.coroutines.flow.Flow<List<MerchantAliasEntity>> = dao.observeAll()

    /** Distinct merchant display names, sorted — backs the type-ahead suggestion list. */
    fun observeDisplayNames(): kotlinx.coroutines.flow.Flow<List<String>> =
        dao.observeAll().map { aliases ->
            aliases.map { it.displayName }.distinct().sortedBy { it.lowercase() }
        }

    /** Raw tokens (alias keys) that resolve to [name] — the "patterns" for this merchant. */
    suspend fun aliasesForDisplay(name: String): List<MerchantAliasEntity> = dao.getByDisplayName(name)

    /** True if [name] is an already-known merchant (has at least one alias row). */
    suspend fun isKnownMerchant(name: String): Boolean = dao.getByDisplayName(name.trim()).isNotEmpty()

    /** Rename a merchant: move every alias from [old] display to [new], keeping its metadata. */
    suspend fun renameDisplay(old: String, new: String) {
        val trimmed = new.trim()
        if (trimmed.isBlank() || trimmed == old) return
        dao.renameDisplayName(old, trimmed)
        // Guarantee an alias keyed by the new name exists so it round-trips for future edits.
        val key = keyFor(trimmed)
        if (dao.getByKey(key) == null) {
            dao.upsert(MerchantAliasEntity(rawKey = key, displayName = trimmed, source = "USER"))
        }
        cache.clear()
    }

    /** Remove one raw-token → merchant mapping (delete a "pattern"). */
    suspend fun deleteAlias(rawKey: String) {
        dao.deleteByKey(rawKey)
        cache.remove(rawKey)
    }
}
