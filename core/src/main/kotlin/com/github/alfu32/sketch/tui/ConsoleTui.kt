package com.github.alfu32.sketch.tui

import com.github.alfu32.sketch.console.ConsoleGroovyRuntime
import com.github.alfu32.sketch.console.TerminalController
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import java.io.File
import java.io.StringWriter
import java.lang.management.ManagementFactory
import java.util.Locale
import kotlin.math.ln

class ConsoleTui(
    private val runtime: ConsoleGroovyRuntime,
    private val terminal: TerminalController,
    outputPane: OutputPane,
    history: HistoryManager,
    private val terminalRunner: () -> Unit,
    private val onQuit: () -> Unit
) {
    val outputPane: OutputPane = outputPane
    val editorPane: EditorPane = EditorPane()
    val history: HistoryManager = history

    private var state: ConsoleState = ConsoleState.IDLE
    private var editorScrollTop = 0
    @Volatile
    private var dirty = true

    init {
        outputPane.setOnAppend { markDirty() }
    }

    fun render() {
        dirty = false
        val size = terminal.size()
        val width = size.columns.coerceAtLeast(20)
        val height = size.rows.coerceAtLeast(10)
        val editorHeight = (height / 3).coerceIn(3, 8)
        val outputHeight = (height - editorHeight - 1).coerceAtLeast(1)
        val builder = StringBuilder()
        builder.append(ANSI_HIDE_CURSOR)
        builder.append(ANSI_CLEAR)
        builder.append(ANSI_HOME)

        val outputLines = outputPane.visibleLines(outputHeight, width)
        for (i in 0 until outputHeight) {
            val line = outputLines.getOrNull(i) ?: ""
            builder.append(padLine(line, width)).append('\n')
        }
        builder.append("""
            █▄▀ ▀▀█ █▀▄   █▀▀ █▀█ █▄ █ █▀▀ █▀█ █   █▀▀
            █ █ ▄██ █▄▀   █▄▄ █▄█ █ ▀█ ▄▄█ █▄█ █▄▄ ██▄
        """.trimIndent()).append('\n')
        builder.append("-".repeat(width)).append('\n')

        val lines = editorPane.lines()
        val (cursorLine, cursorColumn) = editorPane.lineAndColumn(editorPane.cursorPosition)
        if (cursorLine < editorScrollTop) {
            editorScrollTop = cursorLine
        } else if (cursorLine >= editorScrollTop + editorHeight) {
            editorScrollTop = (cursorLine - editorHeight + 1).coerceAtLeast(0)
        }
        for (i in 0 until editorHeight) {
            val lineIndex = editorScrollTop + i
            val rawLine = lines.getOrNull(lineIndex) ?: ""
            val prefix = if (lineIndex == 0) "> " else "  "
            val highlighted = highlight(rawLine)
            val trimmed = truncateAnsi(highlighted, width - prefix.length)
            builder.append(padLine(prefix + trimmed, width)).append('\n')
        }
        val cursorRow = outputHeight + 1 + (cursorLine - editorScrollTop)
        val prefixLength = if (cursorLine == 0) 2 else 2
        val cursorCol = (prefixLength + cursorColumn + 1).coerceAtLeast(1)
        builder.append(ANSI_MOVE_CURSOR.format(Locale.US, cursorRow, cursorCol))
        builder.append(ANSI_SHOW_CURSOR)
        print(builder.toString())
        System.out.flush()
    }

    fun handleInput(event: InputEvent) {
        when (event) {
            is InputEvent.Key -> handleKey(event)
            is InputEvent.Mouse -> {
                // No-op for now; mouse selection is optional.
            }
        }
        markDirty()
    }

    fun needsRender(): Boolean = dirty

    private fun handleKey(event: InputEvent.Key) {
        val key = event.keyCode
        val modifiers = event.modifiers
        val isHistoryModifier = (modifiers and (InputModifiers.CTRL or InputModifiers.ALT)) != 0
        if (state == ConsoleState.OUTPUT_SCROLL && key != InputKeys.PAGE_UP && key != InputKeys.PAGE_DOWN) {
            state = if (editorPane.buffer.isEmpty()) ConsoleState.IDLE else ConsoleState.EDITING
        }
        when (key) {
            InputKeys.ENTER -> onEnter()
            InputKeys.TAB -> handleAutocomplete()
            InputKeys.BACKSPACE -> {
                editorPane.delete()
                if (editorPane.buffer.isNotEmpty()) {
                    state = ConsoleState.EDITING
                } else {
                    state = ConsoleState.IDLE
                }
            }
            InputKeys.DELETE -> editorPane.deleteForward()
            InputKeys.LEFT -> editorPane.moveCursor(-1)
            InputKeys.RIGHT -> editorPane.moveCursor(1)
            InputKeys.UP -> {
                if (isHistoryModifier || editorPane.buffer.isEmpty()) {
                    recallHistory(previous = true)
                } else {
                    editorPane.moveCursorVertical(-1)
                }
            }
            InputKeys.DOWN -> {
                if (isHistoryModifier || editorPane.buffer.isEmpty()) {
                    recallHistory(previous = false)
                } else {
                    editorPane.moveCursorVertical(1)
                }
            }
            InputKeys.HOME -> editorPane.moveCursorHome()
            InputKeys.END -> editorPane.moveCursorEnd()
            InputKeys.PAGE_UP -> {
                outputPane.scroll(5)
                state = ConsoleState.OUTPUT_SCROLL
            }
            InputKeys.PAGE_DOWN -> {
                outputPane.scroll(-5)
                state = ConsoleState.OUTPUT_SCROLL
            }
            InputKeys.ESC -> {
                if (state == ConsoleState.HISTORY_NAVIGATION) {
                    editorPane.clear()
                    state = ConsoleState.IDLE
                }
            }
            else -> {
                if (key >= 32) {
                    editorPane.insert(key.toChar().toString())
                    state = ConsoleState.EDITING
                }
            }
        }
    }

    private fun onEnter() {
        val source = editorPane.buffer.toString()
        if (source.isBlank()) {
            return
        }
        if (handleMetaCommand(source)) {
            history.add(source)
            editorPane.clear()
            state = ConsoleState.IDLE
            return
        }
        state = ConsoleState.EXECUTING
        try {
            val result = runtime.shell.evaluate(source)
            outputPane.append(result?.toString() ?: "null")
            history.add(source)
            editorPane.clear()
            state = ConsoleState.IDLE
        } catch (ex: Exception) {
            if (isIncompleteInput(ex)) {
                state = ConsoleState.EDITING
                return
            }
            val writer = StringWriter()
            ex.printStackTrace(java.io.PrintWriter(writer))
            outputPane.append(writer.toString().trimEnd())
            state = ConsoleState.EDITING
        }
    }

    private fun handleMetaCommand(source: String): Boolean {
        val trimmed = source.trim()
        if (!trimmed.startsWith(":") || trimmed.contains('\n')) {
            return false
        }
        return when (trimmed.lowercase()) {
            ":help" -> {
                printHelp()
                true
            }
            ":exit", ":x" -> {
                outputPane.append("Exiting application.")
                val app = runtime.binding.getProperty("app") as? com.github.alfu32.sketch.console.AppFacade
                app?.run { app.exit() }
                true
            }
            ":term", ":terminal" -> {
                handleTerminal(trimmed)
                true
            }
            ":examples" -> {
                handleExamples()
                true
            }
            ":version", ":ver", ":v" -> {
                handleVersion()
                true
            }
            ":objects" -> {
                handleObjects()
                true
            }
            ":history", ":hist" -> {
                handleHistory()
                true
            }
            ":perf" -> {
                handlePerf()
                true
            }
            else -> {
                return handleMetaCommandWithArgs(trimmed)
            }
        }
    }

    private fun handleMetaCommandWithArgs(trimmed: String): Boolean {
        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        val command = parts.firstOrNull()?.lowercase() ?: return false
        val args = parts.getOrNull(1)?.trim().orEmpty()
        return when (command) {
            ":list", ":ls" -> {
                handleList(trimmed, args)
                true
            }
            ":line", ":l" -> {
                handleLine(trimmed, args)
                true
            }
            ":poly", ":polyline", ":pl" -> {
                handlePoly(trimmed, args)
                true
            }
            ":circle", ":c" -> {
                handleCircle(trimmed, args)
                true
            }
            else -> {
                outputPane.append("Unknown command: $trimmed (try :help)")
                true
            }
        }
    }

    private fun handleList(commandText: String, args: String) {
        outputPane.append("")
        outputPane.append(commandText)
        outputPane.append("")
        if (args.isBlank()) {
            val vars = runtime.binding.variables.keys.map { it.toString() }.sorted()
            outputPane.append(
                if (vars.isEmpty()) "No top-level bindings."
                else vars.joinToString("\n")
            )
            return
        }
        val target = resolveBindingTarget(args)
        if (target == null) {
            outputPane.append("Unknown target: $args")
            return
        }
        val klass = target.javaClass
        val fields = klass.fields.map { "${it.name}: ${it.type.simpleName}" }
        val methods = klass.methods.map { method ->
            val params = method.parameterTypes.joinToString(", ") { it.simpleName }
            "${method.name}(${params}): ${method.returnType.simpleName}"
        }
        val lines = (fields + methods).sorted()
        outputPane.append(lines.joinToString("\n"))
    }

    private fun handleObjects() {
        val scene = runtime.binding.getProperty("scene") as? com.github.alfu32.sketch.model.GroupScene
        val objects = scene?.root?.children.orEmpty()
        if (objects.isEmpty()) {
            outputPane.append("No top-level objects.")
        } else {
            val lines = objects.mapIndexed { index, group ->
                val name = group.name.ifBlank { "(unnamed)" }
                "${index + 1}. ${name} [${group.id}]"
            }
            outputPane.append(lines.joinToString("\n"))
        }
    }

    private fun handleHistory() {
        val items = history.entries()
        if (items.isEmpty()) {
            outputPane.append("History is empty.")
            return
        }
        val lines = items.mapIndexed { index, entry ->
            "${index + 1}. ${entry}"
        }
        outputPane.append(lines.joinToString("\n"))
    }

    private fun handlePerf() {
        val runtime = Runtime.getRuntime()
        val usedMem = runtime.totalMemory() - runtime.freeMemory()
        val totalMem = runtime.totalMemory()
        val maxMem = runtime.maxMemory()
        val heapInfo = "Memory (heap): ${formatBytes(usedMem)} used / ${formatBytes(totalMem)} total / ${formatBytes(maxMem)} max"

        val osBean = ManagementFactory.getOperatingSystemMXBean()
        val cpuInfo = buildCpuInfo(osBean)

        val threadBean = ManagementFactory.getThreadMXBean()
        val threadInfo = "Threads: ${threadBean.threadCount} live / ${threadBean.peakThreadCount} peak"

        val userDir = System.getProperty("user.dir") ?: "."
        val disk = File(userDir)
        val diskInfo = "Disk ($userDir): ${formatBytes(disk.usableSpace)} usable / ${formatBytes(disk.totalSpace)} total"

        outputPane.append(listOf(heapInfo, cpuInfo, threadInfo, diskInfo).joinToString("\n"))
    }

    private fun buildCpuInfo(osBean: java.lang.management.OperatingSystemMXBean): String {
        val processors = osBean.availableProcessors
        val systemLoad = osBean.systemLoadAverage
        val extra = if (osBean is com.sun.management.OperatingSystemMXBean) {
            val processLoad = osBean.processCpuLoad
            val systemCpu = osBean.systemCpuLoad
            val parts = mutableListOf<String>()
            if (processLoad >= 0.0) {
                parts.add("process ${(processLoad * 100.0).formatPercent()}")
            }
            if (systemCpu >= 0.0) {
                parts.add("system ${(systemCpu * 100.0).formatPercent()}")
            }
            if (parts.isNotEmpty()) " (${parts.joinToString(", ")})" else ""
        } else {
            ""
        }
        val loadText = if (systemLoad >= 0.0) String.format(Locale.US, "%.2f", systemLoad) else "n/a"
        return "CPU: $processors cores, load $loadText$extra"
    }

    private fun formatBytes(value: Long): String {
        val unit = 1024.0
        if (value < unit) {
            return "$value B"
        }
        val exp = (ln(value.toDouble()) / ln(unit)).toInt()
        val prefix = "KMGTPE"[exp - 1]
        val scaled = value / Math.pow(unit, exp.toDouble())
        return String.format(Locale.US, "%.2f %sB", scaled, prefix)
    }

    private fun Double.formatPercent(): String = String.format(Locale.US, "%.1f%%", this)

    fun printHelp() {
        outputPane.append(
            """

            Console commands:
                  :help                 Show this help
                  :exit / :x            Exit the application
                  :term / :terminal     Open a shell (exit returns to console)
                  :examples             Show example snippets
                  :version / :ver / :v  Show version info
                  :history / :hist      Show history
                  :perf                 Show performance stats
                  :objects              List top-level objects
                  :list / :ls [name]    List fields/methods
                  :line / :l x,z[,y] .. Draw polyline
                  :poly / :polyline / :pl x,z[,y] ..  Draw closed poly + fill
                  :circle / :c cx,cz[,y],r  Draw circle

            Basic access to the application object model:
                (use app.run { ... } for mutating the model):

                - Plugin host: pluginHost.reloadEnabledAndInit()
                - Lighting: app.run { lighting.ambientLightValue = 0.8f; lightingCtl.apply() }
                - Camera: app.run { cameraCtl.setPosition(10f, 8f, 6f); cameraCtl.setTarget(0f, 0f, 0f) }
                - Color: app.run { status.paintColor.set(1f, 0f, 0f, 1f) }
                - Unit: app.run { unit.set("mm", 0.001f) }
                - Save name: app.run { save.set("examples/new-name.k3d") }
            """.trimIndent()
        )
    }

    private fun handleTerminal(commandText: String) {
        outputPane.append("")
        outputPane.append(commandText)
        outputPane.append("")
        terminalRunner()
        markDirty()
    }

    private fun handleExamples() {
        outputPane.append(
            """
            Meta commands examples:
                // list top-level bindings
                :list

                // list methods/fields for cameraCtl
                :list cameraCtl

                // draw a polyline (x,z[,y])
                :line 0,0 2,0 2,2 0,2

                // draw a filled polygon
                :poly 0,0 3,0 3,3 0,3

                // draw a circle (cx,cz[,y],r)
                :circle 0,0,0,2

                // change unit
                app.run { unit.set("mm", 0.001f) }

                // change save name
                app.run { save.set("examples/new-file.k3d") }


            Editing and executing groovy snippets

            ```groovy
              // define a variable and use it
              def step = 0.5
              console.log("step =", step)

              // loop
              for (i in 0..<5) {
                  console.log("i =", i)
              }

              // define a function
              def moveX(dx) {
                  app.run {
                      cameraCtl.setPosition(
                          camera.position.x + dx,
                          camera.position.y,
                          camera.position.z
                      )
                  }
              }
              moveX(1.0f)
            ```

            Basic access to the application object model:
                (use app.run { ... } for mutating the model):

                - Plugin host: pluginHost.reloadEnabledAndInit()
                - Lighting: app.run { lighting.ambientLightValue = 0.8f; lightingCtl.apply() }
                - Camera: app.run { cameraCtl.setPosition(10f, 8f, 6f); cameraCtl.setTarget(0f, 0f, 0f) }
                - Color: app.run { status.paintColor.set(1f, 0f, 0f, 1f) }
                - Unit: app.run { unit.set("mm", 0.001f) }
                - Save name: app.run { save.set("examples/new-name.k3d") }
            """.trimIndent()
        )
    }

    private fun handleVersion() {
        val version = runtime.binding.getProperty("version")
        if (version == null) {
            outputPane.append("Version info unavailable.")
            return
        }
        val klass = version.javaClass
        val fields = klass.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .associate { field ->
                field.isAccessible = true
                field.name to (field.get(version)?.toString() ?: "")
            }
        val lines = fields.entries.joinToString(",\n") { (key, value) ->
            "  \"$key\": \"${value.replace("\"", "\\\"")}\""
        }
        outputPane.append("{\n$lines\n}")
    }

    private fun handleLine(commandText: String, args: String) {
        outputPane.append("")
        outputPane.append(commandText)
        outputPane.append("")
        val points = parsePointList(args)
        if (points.size < 2) {
            outputPane.append("Usage: :line x,z[,y] x,z[,y] ...")
            return
        }
        val app = runtime.binding.getProperty("app") as? com.github.alfu32.sketch.console.AppFacade
        val scene = runtime.binding.getProperty("scene") as? com.github.alfu32.sketch.model.GroupScene
        if (app == null || scene == null) {
            outputPane.append("Line: missing app or scene binding.")
            return
        }
        app.run {
            val group = scene.activeGroup()
            points.zipWithNext().forEach { (a, b) ->
                group.addSketchSegment(a, b)
            }
        }
    }

    private fun handleCircle(commandText: String, args: String) {
        outputPane.append("")
        outputPane.append(commandText)
        outputPane.append("")
        val circle = parseCircle(args) ?: run {
            outputPane.append("Usage: :circle cx,cz[,y],r")
            return
        }
        val (center, radius) = circle
        val app = runtime.binding.getProperty("app") as? com.github.alfu32.sketch.console.AppFacade
        val scene = runtime.binding.getProperty("scene") as? com.github.alfu32.sketch.model.GroupScene
        if (app == null || scene == null) {
            outputPane.append("Circle: missing app or scene binding.")
            return
        }
        val segments = 32
        val points = (0..segments).map { i ->
            val angle = (Math.PI * 2.0 * i) / segments
            val x = center.x + kotlin.math.cos(angle).toFloat() * radius
            val z = center.z + kotlin.math.sin(angle).toFloat() * radius
            com.badlogic.gdx.math.Vector3(x, center.y, z)
        }
        app.run {
            val group = scene.activeGroup()
            points.zipWithNext().forEach { (a, b) ->
                group.addSketchSegment(a, b)
            }
        }
    }

    private fun handlePoly(commandText: String, args: String) {
        outputPane.append("")
        outputPane.append(commandText)
        outputPane.append("")
        val points = parsePointList(args)
        if (points.size < 3) {
            outputPane.append("Usage: :poly x,z[,y] x,z[,y] ...")
            return
        }
        val app = runtime.binding.getProperty("app") as? com.github.alfu32.sketch.console.AppFacade
        val scene = runtime.binding.getProperty("scene") as? com.github.alfu32.sketch.model.GroupScene
        if (app == null || scene == null) {
            outputPane.append("Poly: missing app or scene binding.")
            return
        }
        val closed = if (points.first().epsilonEquals(points.last(), 1e-5f)) points else points + points.first()
        app.run {
            val group = scene.activeGroup()
            closed.zipWithNext().forEach { (a, b) ->
                group.addSketchSegment(a, b)
            }
            group.faceStore.addPolygon(closed)
        }
    }

    private fun parsePointList(args: String): List<com.badlogic.gdx.math.Vector3> {
        if (args.isBlank()) {
            return emptyList()
        }
        return args.split(Regex("\\s+")).mapNotNull { token ->
            val parts = token.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size !in 2..3) {
                return@mapNotNull null
            }
            val x = parts[0].toFloatOrNull()
            val z = parts[1].toFloatOrNull()
            val y = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
            if (x == null || z == null) {
                return@mapNotNull null
            }
            com.badlogic.gdx.math.Vector3(x, y, z)
        }
    }

    private fun parseCircle(args: String): Pair<com.badlogic.gdx.math.Vector3, Float>? {
        val parts = args.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size !in 3..4) {
            return null
        }
        val cx = parts[0].toFloatOrNull() ?: return null
        val cz = parts[1].toFloatOrNull() ?: return null
        val (cy, radius) = if (parts.size == 3) {
            0f to (parts[2].toFloatOrNull() ?: return null)
        } else {
            (parts[2].toFloatOrNull() ?: return null) to (parts[3].toFloatOrNull() ?: return null)
        }
        return com.badlogic.gdx.math.Vector3(cx, cy, cz) to radius
    }

    private fun resolveBindingTarget(path: String): Any? {
        val parts = path.split('.').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return null
        }
        var current: Any? = runtime.binding.variables[parts.first()] ?: return null
        parts.drop(1).forEach { name ->
            if (current == null) {
                return null
            }
            val klass = current!!.javaClass
            val field = runCatching { klass.getField(name) }.getOrNull()
            if (field != null) {
                current = field.get(current)
                return@forEach
            }
            val getter = "get" + name.replaceFirstChar { it.uppercaseChar() }
            val isser = "is" + name.replaceFirstChar { it.uppercaseChar() }
            val method = klass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
                ?: klass.methods.firstOrNull { it.name == isser && it.parameterCount == 0 }
            if (method != null) {
                current = method.invoke(current)
                return@forEach
            }
            return null
        }
        return current
    }

    private fun handleAutocomplete() {
        val buffer = editorPane.buffer
        val cursor = editorPane.cursorPosition
        if (cursor == 0) {
            return
        }
        val start = findTokenStart(buffer, cursor)
        if (start == cursor) {
            return
        }
        val token = buffer.substring(start, cursor)
        val (base, prefix) = splitToken(token)
        val candidates = if (base == null) {
            topLevelCandidates()
        } else {
            memberCandidates(base)
        }.filter { it.startsWith(prefix) }.sorted()
        if (candidates.isEmpty()) {
            return
        }
        if (candidates.size == 1) {
            val remainder = candidates.first().substring(prefix.length)
            editorPane.insert(remainder)
            return
        }
        outputPane.append(candidates.joinToString("  "))
    }

    private fun findTokenStart(buffer: StringBuilder, cursor: Int): Int {
        var idx = cursor - 1
        while (idx >= 0) {
            val ch = buffer[idx]
            if (ch.isLetterOrDigit() || ch == '_' || ch == '.') {
                idx--
            } else {
                break
            }
        }
        return idx + 1
    }

    private fun splitToken(token: String): Pair<String?, String> {
        val dot = token.lastIndexOf('.')
        return if (dot == -1) {
            null to token
        } else {
            token.substring(0, dot) to token.substring(dot + 1)
        }
    }

    private fun topLevelCandidates(): List<String> {
        val vars = runtime.binding.variables.keys
        return (vars + KEYWORDS).map { it.toString() }.distinct()
    }

    private fun memberCandidates(baseName: String): List<String> {
        val value = runtime.binding.variables[baseName] ?: return emptyList()
        val klass = value.javaClass
        val members = mutableSetOf<String>()
        klass.fields.forEach { members.add(it.name) }
        klass.methods.forEach { method ->
            if (method.parameterCount == 0) {
                val name = method.name
                if (name.startsWith("get") && name.length > 3) {
                    members.add(name.substring(3).replaceFirstChar { it.lowercaseChar() })
                } else if (name.startsWith("is") && name.length > 2) {
                    members.add(name.substring(2).replaceFirstChar { it.lowercaseChar() })
                } else {
                    members.add(name)
                }
            }
        }
        return members.toList()
    }

    private fun recallHistory(previous: Boolean) {
        val entry = if (previous) history.previous() else history.next()
        if (entry == null) {
            return
        }
        if (entry.isEmpty()) {
            editorPane.clear()
            state = ConsoleState.IDLE
            return
        }
        editorPane.clear()
        editorPane.insert(entry)
        state = ConsoleState.HISTORY_NAVIGATION
    }

    private fun isIncompleteInput(ex: Exception): Boolean {
        if (ex is MultipleCompilationErrorsException) {
            val message = ex.message ?: ""
            if (message.contains("unexpected EOF", ignoreCase = true)) {
                return true
            }
            if (message.contains("expecting EOF", ignoreCase = true)) {
                return true
            }
            if (message.contains("unexpected token: EOF", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun markDirty() {
        dirty = true
    }

    private fun padLine(line: String, width: Int): String {
        val visibleLen = stripAnsi(line).length
        return if (visibleLen >= width) {
            line
        } else {
            line + " ".repeat(width - visibleLen)
        }
    }

    private fun highlight(line: String): String {
        if (line.isEmpty()) {
            return line
        }
        var result = line
        KEYWORDS.forEach { keyword ->
            result = result.replace(Regex("\\b${Regex.escape(keyword)}\\b")) { matchResult ->
                "$ANSI_KEYWORD${matchResult.value}$ANSI_RESET"
            }
        }
        return result
    }

    private fun stripAnsi(text: String): String {
        return text.replace(ANSI_REGEX, "")
    }

    private fun truncateAnsi(text: String, width: Int): String {
        if (width <= 0) {
            return ""
        }
        var visible = 0
        val builder = StringBuilder()
        var i = 0
        while (i < text.length && visible < width) {
            if (text[i] == '\u001B') {
                val end = text.indexOf('m', i)
                if (end != -1) {
                    builder.append(text.substring(i, end + 1))
                    i = end + 1
                    continue
                }
            }
            builder.append(text[i])
            visible++
            i++
        }
        return builder.toString()
    }

    companion object {
        private const val ANSI_CLEAR = "\u001B[2J"
        private const val ANSI_HOME = "\u001B[H"
        private const val ANSI_HIDE_CURSOR = "\u001B[?25l"
        private const val ANSI_SHOW_CURSOR = "\u001B[?25h"
        private const val ANSI_KEYWORD = "\u001B[96m"
        private const val ANSI_RESET = "\u001B[0m"
        private const val ANSI_MOVE_CURSOR = "\u001B[%d;%dH"
        private val ANSI_REGEX = Regex("\\u001B\\[[0-9;]*m")
        private val KEYWORDS = listOf(
            "def", "class", "if", "else", "for", "while", "return", "true", "false",
            "null", "new", "try", "catch", "finally", "import", "package", "switch",
            "case", "break", "continue", "as", "in"
        )
    }
}

enum class ConsoleState {
    IDLE,
    EDITING,
    EXECUTING,
    HISTORY_NAVIGATION,
    OUTPUT_SCROLL
}
