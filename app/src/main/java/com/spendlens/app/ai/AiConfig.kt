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
     * Default model slug used on the Free plan when the user hasn't chosen one. A free OpenRouter
     * slug (`:free` variant) keeps cost at zero. Changeable from Settings.
     */
    const val DEFAULT_MODEL = "deepseek/deepseek-chat-v3-0324:free"

    /**
     * Default model slug used on the Premium plan when the user hasn't chosen one — a stronger
     * paid model, since Premium's pitch is better message-pattern recognition. Changeable from
     * Settings like any other model slug.
     */
    const val PREMIUM_DEFAULT_MODEL = "openai/gpt-4o-mini"

    /**
     * Effective API key: a non-blank Settings override wins; otherwise the build-baked default;
     * otherwise null (AI disabled — caller must fall back).
     */
    fun effectiveKey(override: String?, buildDefault: String?): String? {
        override?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return buildDefault?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Effective model slug: a non-blank stored value wins; otherwise [PREMIUM_DEFAULT_MODEL] or
     * [DEFAULT_MODEL] depending on [isPremium].
     */
    fun effectiveModel(stored: String?, isPremium: Boolean): String =
        stored?.trim()?.takeIf { it.isNotBlank() } ?: if (isPremium) PREMIUM_DEFAULT_MODEL else DEFAULT_MODEL
}
