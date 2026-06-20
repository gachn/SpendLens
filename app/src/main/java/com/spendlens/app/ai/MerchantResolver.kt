package com.spendlens.app.ai

/**
 * Pluggable merchant-name resolver. Given a cleaned query, return the canonical brand
 * name or null. [requiresNetwork] documents whether the implementation leaves the device.
 * docs/DESIGN.md §3.2.
 */
interface MerchantResolver {
    val requiresNetwork: Boolean get() = false
    suspend fun resolve(query: String): String?
}

/** No-op resolver (keeps the app fully offline if web lookup is disabled). */
class NoNetworkMerchantResolver : MerchantResolver {
    override suspend fun resolve(query: String): String? = null
}
