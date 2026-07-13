package com.spendlens.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Free: SMS parsing, categorisation and sender/promo checks run entirely on-device via
 * heuristics/regex — no network call is ever made. Premium: the same flows first try an
 * AI model (a stronger OpenRouter model than the Free-tier default) for better recognition,
 * falling back to the same heuristics Free uses.
 */
enum class Plan(val label: String) {
    FREE("Free"),
    PREMIUM("Premium"),
}

/**
 * The user's plan tier, surfaced in Settings → Plan as a plain switch — no payment wall today.
 * [setPlan] is the one seam a future billing integration (Play Billing) would call into, e.g.
 * only after a verified purchase/subscription callback, without touching any of the call sites
 * that read [isPremium] or observe [plan].
 */
class PlanStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("spendlens_plan", Context.MODE_PRIVATE)

    private val _plan = MutableStateFlow(load())
    val plan: StateFlow<Plan> = _plan.asStateFlow()

    private fun load(): Plan =
        prefs.getString(KEY_PLAN, null)
            ?.let { runCatching { Plan.valueOf(it) }.getOrNull() }
            ?: Plan.FREE

    /** Synchronous read used off the main thread when deciding whether an AI call may run. */
    fun isPremium(): Boolean = load() == Plan.PREMIUM

    fun setPlan(plan: Plan) {
        prefs.edit().putString(KEY_PLAN, plan.name).apply()
        _plan.value = plan
    }

    private companion object {
        const val KEY_PLAN = "plan"
    }
}
