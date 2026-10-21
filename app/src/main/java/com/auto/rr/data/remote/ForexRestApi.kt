package com.auto.rr.data.remote

import com.auto.rr.data.ApiKeyStore
import com.auto.rr.data.remote.dto.CandleDto
import com.auto.rr.domain.model.Timeframe
import com.auto.rr.ui.status.StatusBus
import com.auto.rr.ui.status.StatusCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ForexRestApi(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val apiKeyProvider: () -> String? = ApiKeyStore::current,
    private val symbol: String = ApiConfig.SYMBOL
) {

    suspend fun fetchCandles(
        timeframe: Timeframe,
        limit: Int = DEFAULT_LIMIT
    ): ApiResult<List<CandleDto>> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
            ?: return@withContext ApiResult.Failure("No API key set", kind = FailureKind.NO_KEY)
        try {
            val url = (baseUrl + "/time_series").toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("symbol", symbol)
                ?.addQueryParameter("interval", timeframe.providerValue)
                ?.addQueryParameter("outputsize", limit.toString())
                ?.addQueryParameter("apikey", apiKey)
                ?.addQueryParameter("format", "JSON")
                // Candle timestamps are parsed as UTC, so make the provider return UTC explicitly.
                ?.addQueryParameter("timezone", "UTC")
                ?.build()
                ?: return@withContext ApiResult.Failure("Invalid base URL: " + baseUrl)

            val request = Request.Builder().url(url).get().build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val kind = kindForCode(response.code)
                    return@withContext ApiResult.Failure(
                        "HTTP " + response.code + ": " + scrub(body.take(200), apiKey),
                        kind = kind
                    )
                }

                val json = try {
                    JSONObject(body)
                } catch (t: Throwable) {
                    return@withContext ApiResult.Failure("Malformed JSON: " + t.message, t)
                }

                if (json.optString("status") == "error") {
                    val msg = json.optString("message", "unknown provider error")
                    return@withContext ApiResult.Failure(
                        "Provider: " + scrub(msg, apiKey),
                        kind = kindForCode(json.optInt("code", 0))
                    )
                }

                val arr = json.optJSONArray("values")
                    ?: return@withContext ApiResult.Failure("No 'values' array in response")

                var rejected = 0
                val out = ArrayList<CandleDto>(arr.length())
                for (i in 0 until arr.length()) {
                    val dto = CandleDto.fromJsonObject(arr.getJSONObject(i))
                    if (dto == null) rejected++
                    else out += dto
                }

                if (rejected > 0) {
                    StatusBus.warn(
                        "rest " + timeframe.wire + ": rejected " + rejected + " invalid rows",
                        title = "Skipped " + rejected + " unusable " + timeframe.wire + " candles from the provider",
                        category = StatusCategory.DATA
                    )
                }

                out.reverse()
                ApiResult.Success(out)
            }
        } catch (t: Throwable) {
            ApiResult.Failure(scrub(t.message ?: t.javaClass.simpleName, apiKey), t)
        }
    }

    /**
     * Cheap check used by the key-entry screen: asks for a single candle with the given key.
     * Costs one API credit on the user's own plan.
     */
    suspend fun verifyKey(candidate: String): KeyCheck = withContext(Dispatchers.IO) {
        try {
            val url = (baseUrl + "/time_series").toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("symbol", symbol)
                ?.addQueryParameter("interval", "1day")
                ?.addQueryParameter("outputsize", "1")
                ?.addQueryParameter("apikey", candidate)
                ?.build()
                ?: return@withContext KeyCheck.Unverified("Invalid base URL")
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = try { JSONObject(body) } catch (_: Throwable) { null }
                val code = json?.optInt("code", 0) ?: 0
                val msg = scrub(json?.optString("message").orEmpty(), candidate)
                when {
                    json?.optString("status") == "ok" -> KeyCheck.Valid
                    response.code == 401 || response.code == 403 || code == 401 || code == 403 ->
                        KeyCheck.Invalid(msg.ifBlank { "The provider rejected this key." })
                    response.code == 429 || code == 429 ->
                        // Key itself is fine, the plan is just out of credits right now.
                        KeyCheck.Valid
                    json?.has("values") == true -> KeyCheck.Valid
                    else -> KeyCheck.Unverified(msg.ifBlank { "Unexpected response (HTTP " + response.code + ")" })
                }
            }
        } catch (t: Throwable) {
            KeyCheck.Unverified(scrub(t.message ?: t.javaClass.simpleName, candidate))
        }
    }

    sealed interface KeyCheck {
        data object Valid : KeyCheck
        data class Invalid(val message: String) : KeyCheck
        data class Unverified(val message: String) : KeyCheck
    }

    companion object {
        const val DEFAULT_LIMIT = 500

        private fun kindForCode(code: Int): FailureKind = when (code) {
            401, 403 -> FailureKind.AUTH
            429 -> FailureKind.RATE_LIMIT
            else -> FailureKind.OTHER
        }

        /** Never let the user's key leak into the status log / screenshots. */
        internal fun scrub(text: String, key: String): String =
            if (key.isEmpty()) text else text.replace(key, "***")

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
