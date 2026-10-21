package com.auto.rr.data.remote.ws

import com.auto.rr.data.remote.ApiConfig
import org.json.JSONObject

object WsProtocol {

    sealed interface Event {
        data class SubscribeOk(val symbols: List<String>) : Event
        data class SubscribeFail(val reason: String) : Event
        data class Price(val tick: Tick) : Event
        data class Raw(val preview: String) : Event
    }

    fun subscribe(symbol: String): String {
        val q = "\u0022"
        return "{" + q + "action" + q + ":" + q + "subscribe" + q + "," +
               q + "params" + q + ":{" + q + "symbols" + q + ":" + q + symbol + q + "}}"
    }

    fun classify(text: String): Event {
        return try {
            val json = JSONObject(text)
            val event = json.optString("event", "")
            when (event) {
                "subscribe-status" -> {
                    val status = json.optString("status", "")
                    if (status == "ok" || status == "success") {
                        val arr = json.optJSONArray("success")
                        val syms = ArrayList<String>()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val el = arr.opt(i)
                                when (el) {
                                    is JSONObject -> syms += el.optString("symbol", el.toString())
                                    is String -> syms += el
                                    else -> if (el != null) syms += el.toString()
                                }
                            }
                        }
                        Event.SubscribeOk(syms)
                    } else {
                        val fails = json.optString("fails", json.toString().take(160))
                        Event.SubscribeFail(fails)
                    }
                }
                "price" -> {
                    val p = json.optDouble("price", Double.NaN)
                    if (p.isNaN()) Event.Raw(text.take(160))
                    else {
                        val t = json.optLong("timestamp", 0L)
                        val ts = if (t > 0) t * 1000L else System.currentTimeMillis()
                        Event.Price(
                            Tick(
                                symbol = json.optString("symbol", ApiConfig.SYMBOL),
                                price = p,
                                timestamp = ts
                            )
                        )
                    }
                }
                "" -> Event.Raw(text.take(160))
                else -> Event.Raw("[" + event + "] " + text.take(140))
            }
        } catch (_: Throwable) {
            Event.Raw(text.take(160))
        }
    }
}
