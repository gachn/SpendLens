package com.spendlens.app.util

import android.util.Log
import com.spendlens.app.ai.AiLogSanitizer

/**
 * Central logging facade. All calls go through [android.util.Log] so the New Relic Gradle plugin
 * can instrument them at build time and forward to New Relic Logs in debug and release builds.
 */
object AppLog {

    const val TAG = "SpendLens"
    const val TAG_AI = "SpendLens/AI"

    fun d(message: String, tag: String = TAG) = Log.d(tag, message)

    fun i(message: String, tag: String = TAG) = Log.i(tag, message)

    fun w(message: String, tag: String = TAG, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    fun e(message: String, tag: String = TAG, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }

    // --- AI agent observability (prompt → request → response → apply) ---

    /** AI flow started; [operation] identifies the caller (e.g. pattern_teach). */
    fun aiStart(operation: String, model: String, extra: Map<String, Any?> = emptyMap()) {
        Log.i(TAG_AI, "start operation=$operation model=$model ${formatAttrs(extra)}")
    }

    /** Prompt built and about to be sent (PII-masked, length-capped). */
    fun aiPrompt(operation: String, model: String, prompt: String) {
        val body = AiLogSanitizer.sanitizePrompt(prompt)
        Log.d(
            TAG_AI,
            "prompt operation=$operation model=$model chars=${prompt.length} text=$body",
        )
    }

    /** In-flight stage while waiting on the model or applying results. */
    fun aiProcessing(operation: String, stage: String, extra: Map<String, Any?> = emptyMap()) {
        Log.d(TAG_AI, "processing operation=$operation stage=$stage ${formatAttrs(extra)}")
    }

    /** HTTP request dispatched to OpenRouter. */
    fun aiRequestSent(operation: String, model: String, bodyBytes: Int) {
        Log.d(TAG_AI, "request_sent operation=$operation model=$model body_bytes=$bodyBytes")
    }

    /** Model replied successfully. */
    fun aiResponse(operation: String, model: String, httpCode: Int, content: String, elapsedMs: Long) {
        val preview = AiLogSanitizer.sanitizeResponse(content)
        Log.i(
            TAG_AI,
            "response operation=$operation model=$model http=$httpCode elapsed_ms=$elapsedMs " +
                "chars=${content.length} preview=$preview",
        )
    }

    /** Model or network failure. */
    fun aiFailure(operation: String, model: String, httpCode: Int?, message: String, elapsedMs: Long) {
        val safe = AiLogSanitizer.redactSecrets(message)
        Log.e(
            TAG_AI,
            "failure operation=$operation model=$model http=${httpCode ?: "n/a"} " +
                "elapsed_ms=$elapsedMs error=$safe",
        )
    }

    /** Post-processing of model JSON (pattern apply, merchant merge, etc.). */
    fun aiApplied(operation: String, summary: String) {
        Log.i(TAG_AI, "applied operation=$operation $summary")
    }

    /** AI path skipped (flag off, missing key, clipboard fallback). */
    fun aiSkipped(operation: String, reason: String) {
        Log.d(TAG_AI, "skipped operation=$operation reason=$reason")
    }

    private fun formatAttrs(attrs: Map<String, Any?>): String =
        if (attrs.isEmpty()) "" else attrs.entries.joinToString(" ") { (k, v) -> "$k=$v" }
}
