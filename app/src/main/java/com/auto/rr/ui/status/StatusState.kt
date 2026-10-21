package com.auto.rr.ui.status

sealed interface StatusState {
    data object Ok : StatusState
    data object Syncing : StatusState
    /** [msg] is the plain-language title; [hint] says what to do. */
    data class Warning(val msg: String, val hint: String? = null) : StatusState
    data class Error(val msg: String, val hint: String? = null) : StatusState
    data class Testing(val name: String) : StatusState
}
