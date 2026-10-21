package com.auto.rr.di

import android.content.Context
import com.auto.rr.data.ApiKeyStore
import com.auto.rr.data.local.AppDatabase
import com.auto.rr.data.local.CandleDao
import com.auto.rr.data.remote.ForexRestApi
import com.auto.rr.data.remote.ws.ForexWebSocketClient
import com.auto.rr.data.repository.CandleRepository
import com.auto.rr.data.sync.M1Aggregator

object ServiceLocator {

    @Volatile private var initialized = false

    lateinit var database: AppDatabase
        private set

    lateinit var candleDao: CandleDao
        private set

    lateinit var restApi: ForexRestApi
        private set

    lateinit var candleRepository: CandleRepository
        private set

    lateinit var wsClient: ForexWebSocketClient
        private set

    lateinit var m1Aggregator: M1Aggregator
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            ApiKeyStore.init(app)
            database = AppDatabase(app)
            candleDao = CandleDao(database)
            restApi = ForexRestApi()
            candleRepository = CandleRepository(candleDao, restApi)
            wsClient = ForexWebSocketClient()
            m1Aggregator = M1Aggregator(wsClient.ticks, candleRepository)
            initialized = true
        }
    }
}
