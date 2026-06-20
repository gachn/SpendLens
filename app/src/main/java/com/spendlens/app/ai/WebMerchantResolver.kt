package com.spendlens.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Resolves a merchant query to a canonical brand name using Clearbit's free company
 * autocomplete endpoint (no API key). Network failures degrade gracefully to null so the
 * caller falls back to on-device normalization.
 *
 * NOTE: this sends the merchant query off-device — it is the reason the app declares the
 * INTERNET permission. Only the cleaned merchant token is sent (never amounts/accounts).
 */
class WebMerchantResolver : MerchantResolver {

    override val requiresNetwork: Boolean get() = true

    override suspend fun resolve(query: String): String? = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext null
        var conn: HttpURLConnection? = null
        try {
            val url = URL(ENDPOINT + URLEncoder.encode(q, "UTF-8"))
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            if (arr.length() == 0) return@withContext null
            arr.getJSONObject(0).optString("name").trim().ifBlank { null }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private companion object {
        const val ENDPOINT = "https://autocomplete.clearbit.com/v1/companies/suggest?query="
    }
}
