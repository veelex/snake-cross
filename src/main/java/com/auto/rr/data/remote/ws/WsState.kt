package com.auto.rr.data.remote.ws

sealed interface WsConnectionState {
    data object Disconnected : WsConnectionState
    data object Connecting : WsConnectionState
    data object Connected : WsConnectionState
    data object DrainingQueue : WsConnectionState
    data class BackingOff(
        val attempt: Int,
        val nextAttemptInMs: Long,
        val lastError: String?
    ) : WsConnectionState
}
