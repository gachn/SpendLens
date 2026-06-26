package com.spendlens.app.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Pure (Android-free) request/response helpers for the OpenRouter chat-completions API.
 *
 * OpenRouter exposes a single OpenAI-compatible endpoint that fronts 400+ models from many
 * providers; the model is selected purely by the `model` string, which is why the model is a
 * user-changeable setting. Network I/O lives in [OpenRouterClient]; this object only builds the
 * JSON body and extracts the assistant text so both can be unit-tested without a device.
 */
object OpenRouter {

    const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"

    /** Build the chat-completions request body for [model] with a single user [prompt]. */
    fun buildRequestBody(model: String, prompt: String): String {
        val message = JSONObject()
            .put("role", "user")
            .put("content", prompt)
        return JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(message))
            .toString()
    }

    /**
     * Extract the assistant message content from a chat-completions response body, or null if the
     * payload is an error / malformed / empty. Never throws.
     */
    fun parseContent(responseJson: String?): String? {
        if (responseJson.isNullOrBlank()) return null
        return try {
            val root = JSONObject(responseJson)
            // An error envelope has no usable content.
            if (root.has("error") && !root.has("choices")) return null
            val choices = root.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val message = choices.getJSONObject(0).optJSONObject("message") ?: return null
            message.optString("content").takeIf { it.isNotBlank() }
        } catch (_: JSONException) {
            null
        }
    }
}
