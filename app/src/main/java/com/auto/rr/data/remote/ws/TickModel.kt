package com.auto.rr.data.remote.ws

data class Tick(
    val symbol: String,
    val price: Double,
    val timestamp: Long
)
