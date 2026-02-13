package com.github.alfu32.sketch.console

class McpFacade(
    private val startFn: () -> String,
    private val stopFn: () -> String,
    private val statusFn: () -> String,
    private val portFn: () -> Int,
    private val setPortFn: (Int) -> String
) {
    fun start(): String = startFn()
    fun stop(): String = stopFn()
    fun status(): String = statusFn()
    fun port(): Int = portFn()
    fun port(value: Int): String = setPortFn(value)
}
