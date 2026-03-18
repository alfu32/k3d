package com.github.alfu32.sketch.console

object TerminalControllerFactory {
    fun createInteractive(): TerminalController {
        val className = "com.github.alfu32.sketch.lwjgl3.console.LwjglTerminalController"
        return runCatching {
            val clazz = Class.forName(className)
            clazz.getDeclaredConstructor().newInstance() as TerminalController
        }.getOrElse {
            TerminalController()
        }
    }
}
