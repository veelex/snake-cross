package com.auto.rr.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.auto.rr.data.ApiKeyStore
import com.auto.rr.data.remote.ApiResult
import com.auto.rr.data.remote.FailureKind
import com.auto.rr.di.ServiceLocator
import com.auto.rr.domain.model.Timeframe
import com.auto.rr.ui.status.StatusBus
import com.auto.rr.ui.status.StatusCategory

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ServiceLocator.init(applicationContext)
        if (!ApiKeyStore.hasKey()) return Result.success() // nothing to do until the user adds a key
        StatusBus.syncing("sync starting", title = "Updating price history\u2026", category = StatusCategory.DATA)
        val repo = ServiceLocator.candleRepository
        var totalWritten = 0
        var failures = 0
        for (tf in Timeframe.values()) {
            when (val r = repo.fillGap(tf)) {
                is ApiResult.Success -> totalWritten += r.data
                is ApiResult.Failure -> {
                    failures++
                    when (r.kind) {
                        FailureKind.AUTH -> {
                            StatusBus.error(
                                "API key rejected - check it in Settings",
                                title = "Data provider rejected your API key",
                                hint = "Open Settings and check that the key is correct and still active.",
                                category = StatusCategory.DATA
                            )
                            return Result.success() // retrying cannot fix a bad key
                        }
                        FailureKind.NO_KEY -> return Result.success()
                        FailureKind.RATE_LIMIT -> {
                            StatusBus.warn(
                                "sync: plan rate limit reached, will retry later",
                                title = "Data provider request limit reached",
                                hint = "History will update automatically a little later.",
                                category = StatusCategory.DATA
                            )
                            return Result.retry()
                        }
                        FailureKind.OTHER -> StatusBus.warn(
                            "sync " + tf.wire + ": " + r.message,
                            title = "Couldn't update " + tf.wire + " history",
                            hint = "Will retry automatically.",
                            category = StatusCategory.DATA
                        )
                    }
                }
            }
        }
        if (failures == Timeframe.values().size) return Result.retry()
        StatusBus.ok(
            "sync wrote " + totalWritten + " candles",
            title = if (totalWritten == 0) "Price history is up to date" else "Price history updated (" + totalWritten + " new candles)",
            category = StatusCategory.DATA
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC = "auto_rr_periodic_sync"
        const val UNIQUE_ONE_SHOT = "auto_rr_one_shot_sync"
    }
}
