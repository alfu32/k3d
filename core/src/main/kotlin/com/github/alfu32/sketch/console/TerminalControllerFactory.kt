package com.github.alfu32.sketch.console

object TerminalControllerFactory {
    fun createInteractive(): TerminalController {
        val className = "com.github.alfu32.sketch.lwjgl3.console.LwjglTerminalController"
        val loader = Thread.currentThread().contextClassLoader ?: TerminalControllerFactory::class.java.classLoader
        return runCatching {
            val clazz = Class.forName(className, true, loader)
            clazz.getDeclaredConstructor().newInstance() as TerminalController
        }.getOrElse {
            System.err.println(
                "Octodraw Dev Console: failed to load interactive terminal controller " +
                    "(${it.javaClass.simpleName}: ${it.message}). Falling back to basic terminal mode."
            )
            TerminalController()
        }
    }
}
