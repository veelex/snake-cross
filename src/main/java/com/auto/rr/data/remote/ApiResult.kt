package com.auto.rr.data.remote

enum class FailureKind {
    /** No key entered yet. */
    NO_KEY,
    /** Provider rejected the key (invalid, revoked, wrong plan for the endpoint). */
    AUTH,
    /** Per-minute / daily credit limit of the user's plan. */
    RATE_LIMIT,
    /** Offline, timeout, bad JSON, anything else. */
    OTHER
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(
        val message: String,
        val cause: Throwable? = null,
        val kind: FailureKind = FailureKind.OTHER
    ) : ApiResult<Nothing>
}
