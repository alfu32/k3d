package com.github.alfu32.sketch.mcp

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

class StdoutTap private constructor(
    private val originalOut: PrintStream,
    private val originalErr: PrintStream,
    private val maxEntries: Int
) {
    data class Entry(
        val seq: Long,
        val stream: String,
        val line: String,
        val timestampMs: Long
    )

    private val seq = AtomicLong(0L)
    private val lock = Any()
    private val entries = ArrayDeque<Entry>()

    init {
        val outStream = LineTapOutputStream(originalOut, "stdout", ::appendLine)
        val errStream = LineTapOutputStream(originalErr, "stderr", ::appendLine)
        System.setOut(PrintStream(outStream, true, Charsets.UTF_8.name()))
        System.setErr(PrintStream(errStream, true, Charsets.UTF_8.name()))
    }

    fun currentSeq(): Long = seq.get()

    fun entriesSince(seqExclusive: Long): List<Entry> = synchronized(lock) {
        entries.filter { it.seq > seqExclusive }.toList()
    }

    fun stop() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    private fun appendLine(stream: String, line: String) {
        val next = seq.incrementAndGet()
        val entry = Entry(next, stream, line, System.currentTimeMillis())
        synchronized(lock) {
            if (entries.size >= maxEntries) {
                entries.removeFirst()
            }
            entries.addLast(entry)
        }
    }

    private class LineTapOutputStream(
        private val delegate: PrintStream,
        private val streamName: String,
        private val onLine: (String, String) -> Unit
    ) : OutputStream() {
        private val lineBuffer = ByteArrayOutputStream(256)

        override fun write(b: Int) {
            delegate.write(b)
            when (b) {
                '\n'.code -> flushLine()
                '\r'.code -> Unit
                else -> lineBuffer.write(b)
            }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            for (index in off until off + len) {
                val value = b[index].toInt() and 0xFF
                when (value) {
                    '\n'.code -> flushLine()
                    '\r'.code -> Unit
                    else -> lineBuffer.write(value)
                }
            }
        }

        override fun flush() {
            delegate.flush()
        }

        override fun close() {
            flushLine()
            delegate.flush()
        }

        private fun flushLine() {
            if (lineBuffer.size() == 0) {
                return
            }
            val line = lineBuffer.toString(Charsets.UTF_8.name())
            lineBuffer.reset()
            onLine(streamName, line)
        }
    }

    companion object {
        @Volatile
        private var installed: StdoutTap? = null

        fun install(maxEntries: Int = 4_000): StdoutTap {
            val existing = installed
            if (existing != null) {
                return existing
            }
            synchronized(this) {
                val cached = installed
                if (cached != null) {
                    return cached
                }
                val tap = StdoutTap(System.out, System.err, maxEntries.coerceAtLeast(200))
                installed = tap
                return tap
            }
        }
    }
}
