package com.github.alfu32.sketch.console

import com.github.alfu32.sketch.tui.ConsoleTui

class ConsoleThread(
    private val tui: ConsoleTui,
    private val terminal: TerminalController
) : Thread("k3d-dev-console") {
    @Volatile
    private var running = true

    override fun run() {
        terminal.awaitActivationIfNeeded()
        terminal.enterRawMode()
        try {
            tui.history.loadFromDisk()
            tui.outputPane.append(
                """
                ┏━┓┏━╸╺┳╸┏━┓╺┳┓┏━┓┏━┓╻ ╻   ┏━╸┏━┓┏┓╻┏━┓┏━┓╻  ┏━╸
                ┃ ┃┃   ┃ ┃ ┃ ┃┃┣┳┛┣━┫┃╻┃   ┃  ┃ ┃┃┗┫┗━┓┃ ┃┃  ┣╸
                ┗━┛┗━╸ ╹ ┗━┛╺┻┛╹┗╸╹ ╹┗┻┛   ┗━╸┗━┛╹ ╹┗━┛┗━┛┗━╸┗━╸
                """.trimIndent()
            )
            tui.outputPane.append("Octodraw Dev Console ready.")
            tui.printHelp()
            tui.render()
            while (running) {
                val event = terminal.pollEvent(16L)
                if (event != null) {
                    tui.handleInput(event)
                }
                if (tui.needsRender()) {
                    tui.render()
                }
            }
        } finally {
            tui.history.saveToDisk()
            terminal.restore()
        }
    }

    fun shutdown() {
        running = false
    }
}
