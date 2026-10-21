package com.auto.rr.data.sync

import com.auto.rr.data.remote.ws.Tick
import com.auto.rr.data.remote.ws.TickFilter
import com.auto.rr.data.repository.CandleRepository
import com.auto.rr.domain.model.Timeframe
import com.auto.rr.ui.status.StatusBus
import com.auto.rr.ui.status.StatusCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Turns the live tick stream into M1 candles.
 *
 *  - stray ticks are dropped by [TickFilter] so they cannot create needle wicks;
 *  - late / out-of-order ticks never touch the current bar;
 *  - the forming bar is written (throttled) so the chart moves live, and is merged
 *    with whatever is already stored for that minute instead of overwriting it.
 */
class M1Aggregator(
    private val ticks: SharedFlow<Tick>,
    private val repo: CandleRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val filter: TickFilter = TickFilter(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var job: Job? = null
    private val builder = M1BarBuilder()
    private var lastPersistAt = 0L
    private var lastReportedRejects = 0L

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            repo.latest(Timeframe.M1)?.let { filter.seed(it.close) }
            ticks.collect { tick -> onTick(tick) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        val bar = builder.current
        builder.reset()
        if (bar != null) scope.launch { repo.upsert(bar) }
    }

    private suspend fun onTick(tick: Tick) {
        val now = clock()
        if (!filter.accept(tick, now)) {
            reportRejects()
            return
        }
        val update = builder.add(tick.timestamp, tick.price) ?: return

        update.closed?.let { repo.upsert(it) }
        if (update.isNewBar) {
            repo.find(Timeframe.M1, update.current.timestamp)?.let { builder.mergeStored(it) }
        }

        val current = builder.current ?: return
        if (update.isNewBar || now - lastPersistAt >= PERSIST_INTERVAL_MS) {
            repo.upsert(current)
            lastPersistAt = now
        }
    }

    private fun reportRejects() {
        val total = filter.rejected
        if (total - lastReportedRejects >= REJECT_REPORT_EVERY) {
            lastReportedRejects = total
            StatusBus.warn(
                "ticks: $total stray prints dropped",
                title = "Ignored $total odd price ticks",
                hint = "Out-of-range live prices are filtered so they don't distort the chart.",
                category = StatusCategory.DATA
            )
        }
    }

    private companion object {
        const val PERSIST_INTERVAL_MS = 750L
        const val REJECT_REPORT_EVERY = 10L
    }
}
