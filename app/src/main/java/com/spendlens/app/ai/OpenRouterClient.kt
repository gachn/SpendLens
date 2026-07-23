package com.spendlens.app.ai

import com.spendlens.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends a single prompt to the OpenRouter chat-completions endpoint and returns the assistant's
 * text, or a typed failure. Uses [HttpURLConnection] (matching the rest of the app's networking
 * in [WebMerchantResolver]) so no HTTP-client dependency is added. Body building / response
 * parsing live in [OpenRouter] and are unit-tested separately.
 *
 * This is the only path that sends (PII-masked) SMS templates and merchant names off-device; it is
 * gated behind the AI flag + a configured API key (see [com.spendlens.app.data.prefs.AiConfigStore]).
 */
class OpenRouterClient {

    sealed interface Result {
        data class Success(val content: String) : Result
        data class Failure(val message: String) : Result
    }

    /**
     * Call [model] with [prompt] using [apiKey]. Never throws; network errors map to [Result.Failure].
     *
     * @param operation Short label for logs (e.g. `pattern_teach`, `merchant_consolidation`).
     */
    suspend fun complete(
        apiKey: String,
        model: String,
        prompt: String,
        operation: String = "openrouter_chat",
    ): Result = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        AppLog.aiStart(operation, model, mapOf("prompt_chars" to prompt.length))
        AppLog.aiPrompt(operation, model, prompt)
        AppLog.aiProcessing(operation, "building_request")

        // HttpURLConnection's connectTimeout/readTimeout bound individual socket operations, but
        // not every hang (DNS stalls, or a connection left half-open while the process was
        // backgrounded/frozen and resumes to a dead socket). The call runs on its own child
        // coroutine so a hard wall-clock ceiling below can abandon it outright — callers (e.g. the
        // Premium batch worker) must never be stuck waiting indefinitely on one request.
        var conn: HttpURLConnection? = null
        val call = async {
            try {
                val body = OpenRouter.buildRequestBody(model, prompt).toByteArray(Charsets.UTF_8)
                AppLog.aiRequestSent(operation, model, body.size)
                AppLog.aiProcessing(operation, "awaiting_model")

                val localConn = (URL(OpenRouter.BASE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    // Optional attribution headers OpenRouter uses for ranking.
                    setRequestProperty("HTTP-Referer", "https://spendlens.app")
                    setRequestProperty("X-Title", "SpendLens")
                }
                conn = localConn
                localConn.outputStream.use { it.write(body) }

                val code = localConn.responseCode
                val elapsedMs = System.currentTimeMillis() - startedAt
                val stream = if (code in 200..299) localConn.inputStream else localConn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }

                if (code !in 200..299) {
                    val detail = OpenRouterErrorMessage(text) ?: "HTTP $code"
                    AppLog.aiFailure(operation, model, code, detail, elapsedMs)
                    Result.Failure(detail) as Result
                } else {
                    AppLog.aiProcessing(operation, "parsing_response")
                    val content = OpenRouter.parseContent(text)
                    if (content.isNullOrBlank()) {
                        AppLog.aiFailure(operation, model, code, "Empty AI response", elapsedMs)
                        Result.Failure("Empty AI response")
                    } else {
                        AppLog.aiResponse(operation, model, code, content, elapsedMs)
                        Result.Success(content)
                    }
                }
            } catch (e: Exception) {
                val elapsedMs = System.currentTimeMillis() - startedAt
                AppLog.aiFailure(operation, model, null, e.message ?: "Network error", elapsedMs)
                Result.Failure(e.message ?: "Network error")
            } finally {
                conn?.disconnect()
            }
        }

        withTimeoutOrNull(HARD_TIMEOUT_MS) { call.await() } ?: run {
            val elapsedMs = System.currentTimeMillis() - startedAt
            AppLog.aiFailure(operation, model, null, "No response within ${HARD_TIMEOUT_MS}ms — abandoned", elapsedMs)
            call.cancel()
            runCatching { conn?.disconnect() } // best-effort: may unblock the abandoned call's thread
            Result.Failure("AI request timed out after ${HARD_TIMEOUT_MS / 1000}s")
        }
    }

    /**
     * Fetch the slugs of every model OpenRouter currently fronts, for model-field autocomplete.
     * The `/models` catalogue is public, so [apiKey] is optional (sent only for attribution when
     * present). Never throws; any failure maps to an empty list.
     */
    suspend fun listModels(apiKey: String? = null): List<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(OpenRouter.MODELS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
                apiKey?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
                setRequestProperty("HTTP-Referer", "https://spendlens.app")
                setRequestProperty("X-Title", "SpendLens")
            }
            val code = conn.responseCode
            if (code !in 200..299) return@withContext emptyList()
            val text = conn.inputStream?.bufferedReader()?.use { it.readText() }
            OpenRouter.parseModels(text)
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    private fun OpenRouterErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            org.json.JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        /** Absolute wall-clock ceiling for [complete] — comfortably above connectTimeout+readTimeout. */
        const val HARD_TIMEOUT_MS = 90_000L
    }
}
