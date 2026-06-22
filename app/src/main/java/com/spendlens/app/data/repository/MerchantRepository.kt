package com.spendlens.app.data.repository

import com.spendlens.app.ai.MerchantResolver
import com.spendlens.app.data.MerchantDictionary
import com.spendlens.app.data.db.MerchantAliasEntity
import com.spendlens.app.data.db.MerchantDao
import com.spendlens.app.parser.MerchantNormalizer
import java.util.concurrent.ConcurrentHashMap

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
            ),
        )
    }

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
        val existingTags = dao.getByKey(key)?.tags
        dao.upsert(MerchantAliasEntity(rawKey = key, displayName = display, source = source, tags = existingTags))
        cache[key] = display
        return display
    }
}
