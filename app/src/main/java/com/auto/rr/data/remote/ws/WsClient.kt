package com.auto.rr.data.remote.ws

import com.auto.rr.data.ApiKeyStore
import com.auto.rr.data.remote.ApiConfig
import com.auto.rr.ui.status.StatusBus
import com.auto.rr.ui.status.StatusCategory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class ForexWebSocketClient(
    private val wsUrl: String = DEFAULT_WS_URL,
    private val apiKeyProvider: () -> String? = ApiKeyStore::current,
    private val client: OkHttpClient = defaultClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val _state = MutableStateFlow<WsConnectionState>(WsConnectionState.Disconnected)
    val state: StateFlow<WsConnectionState> = _state.asStateFlow()

    private val _ticks = MutableSharedFlow<Tick>(
        replay = 0,
        extraBufferCapacity = TICK_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val ticks: SharedFlow<Tick> = _ticks.asSharedFlow()

    private var supervisorJob: Job? = null

    @Volatile
    private var currentSocket: WebSocket? = null

    fun start() {
        if (supervisorJob?.isActive == true) return
        supervisorJob = scope.launch { runLoop() }
    }

    fun stop() {
        supervisorJob?.cancel()
        supervisorJob = null
        currentSocket?.close(1000, "shutdown")
        currentSocket = null
        _state.value = WsConnectionState.Disconnected
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            if (apiKeyProvider().isNullOrBlank()) break // no key: stay idle until AppLifecycle restarts us
            _state.value = WsConnectionState.Connecting
            val outcome = attemptConnection()
            if (outcome is ConnectOutcome.Failed) {
                if (!currentCoroutineContext().isActive) break
                if (outcome.authRejected) {
                    StatusBus.error(
                        "API key rejected by live feed - check it in Settings",
                        title = "Live feed rejected your API key",
                        hint = "Open Settings and check that the key is correct and still active.",
                        category = StatusCategory.CONNECTION
                    )
                    break // retrying with a bad key only burns the user's quota
                }
                val delayMs = jitteredBackoffMs(attempt)
                _state.value = WsConnectionState.BackingOff(
                    attempt = attempt + 1,
                    nextAttemptInMs = delayMs,
                    lastError = outcome.cause.message ?: outcome.cause.javaClass.simpleName
                )
                attempt++
                delay(delayMs)
                continue
            }
            attempt = 0
            _state.value = WsConnectionState.DrainingQueue
            _state.value = WsConnectionState.Connected
            val openedOutcome = outcome as ConnectOutcome.Opened
            openedOutcome.closed.await()
            if (!currentCoroutineContext().isActive) break
            val delayMs = jitteredBackoffMs(0)
            _state.value = WsConnectionState.BackingOff(
                attempt = 1,
                nextAttemptInMs = delayMs,
                lastError = "link closed"
            )
            delay(delayMs)
        }
        _state.value = WsConnectionState.Disconnected
    }

    private sealed interface ConnectOutcome {
        data class Opened(val closed: CompletableDeferred<Unit>) : ConnectOutcome
        data class Failed(val cause: Throwable, val authRejected: Boolean = false) : ConnectOutcome
    }

    private suspend fun attemptConnection(): ConnectOutcome {
        val apiKey = apiKeyProvider()
            ?: return ConnectOutcome.Failed(IllegalStateException("No API key set"))
        val url = wsUrl + "?apikey=" + apiKey
        val request = Request.Builder().url(url).get().build()
        val opened = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        var authRejected = false

        val listener = object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                StatusBus.syncing("ws subscribing", title = "Requesting " + ApiConfig.SYMBOL + " prices\u2026", category = StatusCategory.CONNECTION)
                val payload = WsProtocol.subscribe(ApiConfig.SYMBOL)
                val queued = webSocket.send(payload)
                if (queued) StatusBus.ok("ws subscribe queued=true", title = "Price request sent", category = StatusCategory.CONNECTION)
                else StatusBus.warn(
                    "ws subscribe queued=false",
                    title = "Could not send the price request",
                    hint = "The connection will retry automatically.",
                    category = StatusCategory.CONNECTION
                )
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when (val evt = WsProtocol.classify(text)) {
                    null -> Unit
                    is WsProtocol.Event.Price -> {
                        _ticks.tryEmit(evt.tick)
                    }
                    is WsProtocol.Event.SubscribeOk -> {
                        StatusBus.ok(
                            "ws subscribed: " + evt.symbols.joinToString(","),
                            title = "Receiving live " + evt.symbols.joinToString(", ") + " prices",
                            category = StatusCategory.CONNECTION
                        )
                    }
                    is WsProtocol.Event.SubscribeFail -> {
                        StatusBus.warn(
                            "ws subscribe failed: " + evt.reason.replace(apiKey, "***"),
                            title = "Live price request was declined",
                            hint = "Check that your data plan includes live prices for " + ApiConfig.SYMBOL + ".",
                            category = StatusCategory.CONNECTION
                        )
                    }
                    is WsProtocol.Event.Raw -> {
                        StatusBus.ok("ws frame: " + evt.preview, title = "Message from the live feed", category = StatusCategory.CONNECTION)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                if (code == 401 || code == 403) authRejected = true
                if (!opened.isCompleted) opened.completeExceptionally(t)
                closed.complete(Unit)
            }
        }

        val socket = client.newWebSocket(request, listener)
        currentSocket = socket

        return try {
            withTimeout(OPEN_TIMEOUT_MS) { opened.await() }
            ConnectOutcome.Opened(closed)
        } catch (t: Throwable) {
            socket.cancel()
            currentSocket = null
            ConnectOutcome.Failed(t, authRejected)
        }
    }

    private fun jitteredBackoffMs(attempt: Int): Long {
        val shift = attempt.coerceAtMost(MAX_BACKOFF_SHIFT)
        val base = (INITIAL_BACKOFF_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
        val factor = 0.5 + Math.random() * 0.5
        return (base * factor).toLong().coerceAtLeast(MIN_DELAY_MS)
    }

    companion object {
        const val DEFAULT_WS_URL = "wss://ws.twelvedata.com/v1/quotes/price"

        private const val OPEN_TIMEOUT_MS = 15000L
        private const val INITIAL_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 30000L
        private const val MAX_BACKOFF_SHIFT = 6
        private const val MIN_DELAY_MS = 250L
        private const val TICK_BUFFER = 256

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }
}
