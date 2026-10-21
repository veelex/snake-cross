package com.auto.rr.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_CANDLES)
        db.execSQL(CREATE_CANDLES_INDEX)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CANDLES)
        onCreate(db)
    }

    companion object {
        const val DB_NAME = "auto_rr.db"
        const val DB_VERSION = 1
        const val TABLE_CANDLES = "candles"

        private const val CREATE_CANDLES =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CANDLES + " (" +
            "timestamp INTEGER NOT NULL, " +
            "timeframe TEXT NOT NULL, " +
            "open REAL NOT NULL, " +
            "high REAL NOT NULL, " +
            "low REAL NOT NULL, " +
            "close REAL NOT NULL, " +
            "volume REAL NOT NULL, " +
            "PRIMARY KEY (timestamp, timeframe))"

        private const val CREATE_CANDLES_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_candles_tf_ts ON " +
            TABLE_CANDLES + "(timeframe, timestamp)"
    }
}
