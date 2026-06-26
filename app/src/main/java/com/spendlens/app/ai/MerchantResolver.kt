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

/**
 * Delegates to [delegate] only while [enabled] returns true; otherwise behaves as a no-op
 * (offline) resolver. [enabled] is read on every call so a Settings toggle takes effect at
 * runtime without recreating the object graph.
 */
class GatedMerchantResolver(
    private val delegate: MerchantResolver,
    private val enabled: () -> Boolean,
) : MerchantResolver {
    override val requiresNetwork: Boolean get() = delegate.requiresNetwork
    override suspend fun resolve(query: String): String? =
        if (enabled()) delegate.resolve(query) else null
}
