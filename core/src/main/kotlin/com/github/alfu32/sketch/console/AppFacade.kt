package com.github.alfu32.sketch.console

import com.badlogic.gdx.Application

class AppFacade(
    private val application: Application
) {
    fun run(block: () -> Unit) {
        application.postRunnable(block)
    }
}
