package com.github.alfu32.sketch.perf

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
        var calls: Long = 0L
        var totalNs: Long = 0L
        var maxNs: Long = 0L
        var lastNs: Long = 0L
    }

    private val lock = Any()
    private val counters = linkedMapOf<String, Counter>()

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
        synchronized(lock) {
            val counter = counters.getOrPut(name) { Counter() }
            counter.calls += 1L
            counter.totalNs += safeElapsed
            counter.lastNs = safeElapsed
            if (safeElapsed > counter.maxNs) {
                counter.maxNs = safeElapsed
            }
        }
    }

    fun snapshot(limit: Int = 16): List<Sample> {
        return synchronized(lock) {
            counters.entries
                .map { (name, counter) ->
                    Sample(
                        name = name,
                        calls = counter.calls,
                        totalNs = counter.totalNs,
                        maxNs = counter.maxNs,
                        lastNs = counter.lastNs
                    )
                }
                .sortedByDescending { it.totalNs }
                .take(limit.coerceAtLeast(0))
        }
    }

    fun clear() {
        synchronized(lock) {
            counters.clear()
        }
    }
}
