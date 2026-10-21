package com.auto.rr.data.local

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.auto.rr.data.local.AppDatabase.Companion.TABLE_CANDLES
import com.auto.rr.ui.status.StatusBus
import com.auto.rr.ui.status.StatusCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

class CandleDao(private val db: AppDatabase) {

    /** One change counter per timeframe, so a write to M1 does not reload M5/H1/... */
    private val changeTokens = ConcurrentHashMap<String, MutableStateFlow<Long>>()

    private fun token(timeframe: String): MutableStateFlow<Long> =
        changeTokens.getOrPut(timeframe) { MutableStateFlow(0L) }

    fun observe(timeframe: String): Flow<List<CandleEntity>> =
        token(timeframe).map { load(timeframe) }.flowOn(Dispatchers.IO)

    fun upsert(candle: CandleEntity) = upsertAll(listOf(candle))

    fun upsertAll(candles: List<CandleEntity>) {
        if (candles.isEmpty()) return
        try {
            val sqlite = db.writableDatabase
            sqlite.beginTransaction()
            try {
                for (c in candles) {
                    sqlite.insertWithOnConflict(
                        TABLE_CANDLES, null, c.toContentValues(),
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                sqlite.setTransactionSuccessful()
            } finally {
                sqlite.endTransaction()
            }
            for (tf in candles.mapTo(HashSet()) { it.timeframe }) token(tf).update { it + 1 }
        } catch (t: Throwable) {
            StatusBus.warn(
                "dao upsert: " + (t.message ?: t.javaClass.simpleName),
                title = "Couldn't save price data to this device",
                hint = "If this keeps happening, free up storage space or restart the app.",
                category = StatusCategory.DATA
            )
        }
    }

    fun load(timeframe: String, limit: Int = DEFAULT_LIMIT): List<CandleEntity> {
        return try {
            val out = ArrayList<CandleEntity>(limit)
            db.readableDatabase.rawQuery(
                "SELECT timestamp, timeframe, open, high, low, close, volume " +
                "FROM " + TABLE_CANDLES + " WHERE timeframe = ? " +
                "ORDER BY timestamp DESC LIMIT ?",
                arrayOf(timeframe, limit.toString())
            ).use { c -> while (c.moveToNext()) out += c.toCandle() }
            out.reverse()
            out
        } catch (t: Throwable) {
            StatusBus.warn(
                "dao load: " + (t.message ?: t.javaClass.simpleName),
                title = "Couldn't read saved price data",
                hint = "If this keeps happening, free up storage space or restart the app.",
                category = StatusCategory.DATA
            )
            emptyList()
        }
    }

    /** The stored candle for exactly this bucket, if any. */
    fun find(timeframe: String, timestamp: Long): CandleEntity? = try {
        db.readableDatabase.rawQuery(
            "SELECT timestamp, timeframe, open, high, low, close, volume " +
            "FROM " + TABLE_CANDLES + " WHERE timeframe = ? AND timestamp = ?",
            arrayOf(timeframe, timestamp.toString())
        ).use { c -> if (c.moveToFirst()) c.toCandle() else null }
    } catch (t: Throwable) {
        StatusBus.warn(
                "dao find: " + (t.message ?: t.javaClass.simpleName),
                title = "Couldn't read saved price data",
                hint = "If this keeps happening, free up storage space or restart the app.",
                category = StatusCategory.DATA
            )
        null
    }

    fun latest(timeframe: String): CandleEntity? = load(timeframe, 1).firstOrNull()

    fun latestTimestamp(timeframe: String): Long? = try {
        db.readableDatabase.rawQuery(
            "SELECT MAX(timestamp) FROM " + TABLE_CANDLES + " WHERE timeframe = ?",
            arrayOf(timeframe)
        ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
    } catch (t: Throwable) {
        StatusBus.warn(
                "dao latest: " + (t.message ?: t.javaClass.simpleName),
                title = "Couldn't read saved price data",
                hint = "If this keeps happening, free up storage space or restart the app.",
                category = StatusCategory.DATA
            )
        null
    }

    fun count(timeframe: String): Int = try {
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM " + TABLE_CANDLES + " WHERE timeframe = ?",
            arrayOf(timeframe)
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    } catch (t: Throwable) {
        0
    }

    fun countAll(): Map<String, Int> = try {
        val out = linkedMapOf<String, Int>()
        db.readableDatabase.rawQuery(
            "SELECT timeframe, COUNT(*) FROM " + TABLE_CANDLES + " GROUP BY timeframe",
            null
        ).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getInt(1)
        }
        out
    } catch (t: Throwable) {
        emptyMap()
    }

    private fun CandleEntity.toContentValues() = ContentValues().apply {
        put("timestamp", timestamp)
        put("timeframe", timeframe)
        put("open", open); put("high", high); put("low", low)
        put("close", close); put("volume", volume)
    }

    private fun Cursor.toCandle() = CandleEntity(
        timestamp = getLong(0),
        timeframe = getString(1),
        open = getDouble(2),
        high = getDouble(3),
        low = getDouble(4),
        close = getDouble(5),
        volume = getDouble(6)
    )

    companion object {
        const val DEFAULT_LIMIT = 500
    }
}
