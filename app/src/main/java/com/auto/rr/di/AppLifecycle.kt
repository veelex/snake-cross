package com.auto.rr.di

import android.content.Context
import com.auto.rr.data.ApiKeyStore
import com.auto.rr.data.remote.ws.WsConnectionState
import com.auto.rr.data.sync.SyncScheduler
import com.auto.rr.ui.status.StatusBus
import com.auto.rr.ui.status.StatusCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppLifecycle {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true

            ServiceLocator.init(context)
            val counts = ServiceLocator.candleDao.countAll()
            val summary = if (counts.isEmpty()) "db empty" else counts.entries.joinToString(", ") { it.key + "=" + it.value }
            StatusBus.ok(
                "db: " + summary,
                title = if (counts.isEmpty()) "No saved price history yet" else "Loaded saved price history",
                category = StatusCategory.DATA
            )

            scope.launch {
                ServiceLocator.wsClient.state.collect { st ->
                    when (st) {
                        is WsConnectionState.Disconnected ->
                            if (ApiKeyStore.hasKey()) StatusBus.warn(
                                "ws disconnected",
                                title = "Live price feed disconnected",
                                hint = "Reconnecting automatically. If this continues, check your internet connection.",
                                category = StatusCategory.CONNECTION
                            ) else Unit
                        is WsConnectionState.Connecting ->
                            StatusBus.syncing("ws connecting", title = "Connecting to live prices\u2026", category = StatusCategory.CONNECTION)
                        is WsConnectionState.Connected ->
                            StatusBus.ok("ws connected", title = "Live price feed connected", category = StatusCategory.CONNECTION)
                        is WsConnectionState.DrainingQueue ->
                            StatusBus.syncing("ws draining", title = "Catching up on queued prices\u2026", category = StatusCategory.CONNECTION)
                        is WsConnectionState.BackingOff ->
                            StatusBus.warn(
                                "ws retry " + st.attempt + " in " +
                                    st.nextAttemptInMs + "ms: " +
                                    (st.lastError ?: "unknown"),
                                title = "Connection lost, retrying (attempt " + st.attempt + ")",
                                hint = "Next try in about " + ((st.nextAttemptInMs + 999) / 1000) + " s. This happens automatically.",
                                category = StatusCategory.CONNECTION
                            )
                    }
                }
            }

            // Live feed and sync follow the user's key: start when one is entered, restart when it
            // changes, stay idle when it is removed.
            scope.launch {
                ApiKeyStore.key.collect { key ->
                    ServiceLocator.wsClient.stop()
                    if (key.isNullOrBlank()) {
                        StatusBus.warn(
                            "no API key - open Settings to add yours",
                            title = "No API key set",
                            hint = "Add your data-provider key in Settings to get prices.",
                            category = StatusCategory.CONNECTION
                        )
                    } else {
                        ServiceLocator.wsClient.start()
                        ServiceLocator.m1Aggregator.start()
                        SyncScheduler.scheduleOneShot(context)
                    }
                }
            }
            StatusBus.ok("app started", title = "Snake Cross started")
        }
    }
}
