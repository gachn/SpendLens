package online.velozen.spendvault.ai

/**
 * Rough token estimate for sizing AI batch requests — no real tokenizer dependency. The
 * chars/4 rule of thumb is conservative enough for English-plus-numbers SMS text.
 */
object TokenEstimator {
    fun estimate(text: String): Int = (text.length + 3) / 4
}
