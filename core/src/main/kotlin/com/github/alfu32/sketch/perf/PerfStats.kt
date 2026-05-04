package com.github.alfu32.sketch.perf

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder
import kotlin.math.max

object PerfStats {
    data class Sample(
        val name: String,
        val calls: Long,
        val totalNs: Long,
        val maxNs: Long,
        val lastNs: Long
    ) {
        val avgNs: Long
            get() = if (calls <= 0L) 0L else totalNs / calls
    }

    private class Counter {
        val calls = LongAdder()
        val totalNs = LongAdder()
        val maxNs = AtomicLong()
        val lastNs = AtomicLong()
    }

    private val counters = ConcurrentHashMap<String, Counter>()

    inline fun <T> measure(name: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            record(name, System.nanoTime() - start)
        }
    }

    fun record(name: String, elapsedNs: Long) {
        val safeElapsed = max(0L, elapsedNs)
        val counter = counters.computeIfAbsent(name) { Counter() }
        counter.calls.increment()
        counter.totalNs.add(safeElapsed)
        counter.lastNs.set(safeElapsed)
        while (true) {
            val currentMax = counter.maxNs.get()
            if (safeElapsed <= currentMax) {
                break
            }
            if (counter.maxNs.compareAndSet(currentMax, safeElapsed)) {
                break
            }
        }
    }

    fun snapshot(limit: Int = 16): List<Sample> {
        return counters.entries
            .map { (name, counter) ->
                Sample(
                    name = name,
                    calls = counter.calls.sum(),
                    totalNs = counter.totalNs.sum(),
                    maxNs = counter.maxNs.get(),
                    lastNs = counter.lastNs.get()
                )
            }
            .sortedByDescending { it.totalNs }
            .take(limit.coerceAtLeast(0))
    }

    fun clear() {
        counters.clear()
    }
}
