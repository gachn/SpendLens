package com.spendlens.app.ai

/**
 * Pure (Android-free) resolution rules for the AI configuration so they can be unit-tested.
 *
 * The user picked "BuildConfig default, overridable in Settings": a key entered in Settings wins
 * over the build-baked default; if neither is present the AI features are unavailable and the app
 * falls back to its existing on-device / clipboard flows.
 */
object AiConfig {

    /**
     * Default model slug used when the user hasn't chosen one. A free OpenRouter slug
     * (`:free` variant) keeps cost at zero. Changeable from Settings.
     */
    const val DEFAULT_MODEL = "deepseek/deepseek-chat-v3-0324:free"

    /**
     * Effective API key: a non-blank Settings override wins; otherwise the build-baked default;
     * otherwise null (AI disabled — caller must fall back).
     */
    fun effectiveKey(override: String?, buildDefault: String?): String? {
        override?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return buildDefault?.trim()?.takeIf { it.isNotBlank() }
    }

    /** Effective model slug: a non-blank stored value wins; otherwise [DEFAULT_MODEL]. */
    fun effectiveModel(stored: String?): String =
        stored?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
}
