package com.spendlens.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** Call [model] with [prompt] using [apiKey]. Never throws; network errors map to [Result.Failure]. */
    suspend fun complete(apiKey: String, model: String, prompt: String): Result =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val body = OpenRouter.buildRequestBody(model, prompt).toByteArray(Charsets.UTF_8)
                conn = (URL(OpenRouter.BASE_URL).openConnection() as HttpURLConnection).apply {
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
                conn.outputStream.use { it.write(body) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }

                if (code !in 200..299) {
                    val detail = OpenRouterErrorMessage(text) ?: "HTTP $code"
                    return@withContext Result.Failure(detail)
                }
                val content = OpenRouter.parseContent(text)
                if (content.isNullOrBlank()) {
                    Result.Failure("Empty AI response")
                } else {
                    Result.Success(content)
                }
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Network error")
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
}
