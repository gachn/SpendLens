package com.spendlens.app.data.fx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Fetches FX rates expressed as currency → rate-to-INR. */
interface FxProvider {
    suspend fun fetchRatesToInr(): Map<String, Double>?
}

/**
 * Live rates from open.er-api.com (free, no key). Returns USD→X rates; we derive
 * X→INR = (USD→INR) / (USD→X). Failures return null so the caller keeps cached/bundled rates.
 */
class WebFxProvider : FxProvider {

    override suspend fun fetchRatesToInr(): Map<String, Double>? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            if (obj.optString("result") != "success") return@withContext null
            val rates = obj.getJSONObject("rates")
            val usdToInr = rates.optDouble("INR", 0.0)
            if (usdToInr <= 0.0) return@withContext null

            val out = mutableMapOf("INR" to 1.0)
            for (cur in SUPPORTED) {
                val usdToCur = if (cur == "USD") 1.0 else rates.optDouble(cur, 0.0)
                if (usdToCur > 0.0) out[cur] = usdToInr / usdToCur
            }
            out
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private companion object {
        const val ENDPOINT = "https://open.er-api.com/v6/latest/USD"
        val SUPPORTED = listOf("USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD", "JPY")
    }
}
