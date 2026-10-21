package com.auto.rr.data.repository

import com.auto.rr.data.local.CandleDao
import com.auto.rr.data.local.CandleEntity
import com.auto.rr.data.remote.ApiResult
import com.auto.rr.data.remote.ForexRestApi
import com.auto.rr.domain.model.MarketCalendar
import com.auto.rr.domain.model.Timeframe
import com.auto.rr.data.sanitize.CandleSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class CandleRepository(
    private val dao: CandleDao,
    private val api: ForexRestApi
) {

    /** Candles ready to draw: stored rows with bad prints removed (see [CandleSanitizer]). */
    fun observe(timeframe: Timeframe): Flow<List<CandleEntity>> =
        dao.observe(timeframe.wire)
            .map { CandleSanitizer.sanitize(MarketCalendar.collapseWeekends(it, timeframe)) }
            .flowOn(Dispatchers.Default)

    fun find(timeframe: Timeframe, timestamp: Long): CandleEntity? =
        dao.find(timeframe.wire, timestamp)

    fun latest(timeframe: Timeframe): CandleEntity? = dao.latest(timeframe.wire)

    suspend fun upsert(candle: CandleEntity) = dao.upsert(candle)

    suspend fun refresh(
        timeframe: Timeframe,
        limit: Int = ForexRestApi.DEFAULT_LIMIT
    ): ApiResult<Int> = when (val r = api.fetchCandles(timeframe, limit)) {
        is ApiResult.Success -> {
            val rows = r.data.map { it.toEntity(timeframe.wire) }
            dao.upsertAll(rows)
            ApiResult.Success(rows.size)
        }
        is ApiResult.Failure -> r
    }

    suspend fun fillGap(timeframe: Timeframe): ApiResult<Int> {
        val since = dao.latestTimestamp(timeframe.wire) ?: return refresh(timeframe)
        return when (val r = api.fetchCandles(timeframe, ForexRestApi.DEFAULT_LIMIT)) {
            is ApiResult.Success -> {
                val fresh = r.data
                    .map { it.toEntity(timeframe.wire) }
                    .filter { it.timestamp >= since } // >= so the candle that was still forming gets its final values
                if (fresh.isEmpty()) {
                    ApiResult.Success(0)
                } else {
                    dao.upsertAll(fresh)
                    ApiResult.Success(fresh.size)
                }
            }
            is ApiResult.Failure -> r
        }
    }

    fun latestTimestamp(timeframe: Timeframe): Long? =
        dao.latestTimestamp(timeframe.wire)

    fun count(timeframe: Timeframe): Int =
        dao.count(timeframe.wire)
}
