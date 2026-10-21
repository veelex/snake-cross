package com.auto.rr.data.remote

object ApiConfig {
    /** Twelve Data base URL. Swap if you move to a different provider. */
    const val BASE_URL: String = "https://api.twelvedata.com"

    /** Instrument. Twelve Data wants EUR/USD with a slash. */
    const val SYMBOL: String = "EUR/USD"

    /** Where users create their own (free) key. */
    const val SIGNUP_URL: String = "https://twelvedata.com/account/api-keys"

    // No API key here on purpose: every user supplies their own, see ApiKeyStore.
}
