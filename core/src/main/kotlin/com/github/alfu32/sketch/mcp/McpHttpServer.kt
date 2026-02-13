package com.github.alfu32.sketch.mcp

import com.badlogic.gdx.Input
import com.github.alfu32.sketch.plugin.PaletteCommand
import com.github.alfu32.sketch.plugin.PluginResult
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.Locale
import kotlin.math.max

class McpHttpServer(
    initialPort: Int,
    private val stdoutTap: StdoutTap,
    private val listCommands: () -> List<PaletteCommand>,
    private val executeCommand: (String) -> PluginResult,
    private val executeConsoleCommand: (String) -> McpConsoleResult,
    private val dispatchPointerEvent: (McpPointerEventRequest) -> McpPointerEventResult,
    private val contractProvider: () -> String
) {
    @Volatile
    private var server: HttpServer? = null
    @Volatile
    private var port: Int = sanitizePort(initialPort)
    private val lock = Any()

    fun start(): String {
        synchronized(lock) {
            if (server != null) {
                return "MCP HTTP server already running on port $port."
            }
            return try {
                val http = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
                http.createContext("/scene/listCommands") { exchange -> handleListCommands(exchange) }
                http.createContext("/scene/commands") { exchange -> handleListCommands(exchange) }
                http.createContext("/scene/command") { exchange -> handleExecuteCommand(exchange) }
                http.createContext("/scene/console") { exchange -> handleExecuteConsole(exchange) }
                http.createContext("/scene/meta") { exchange -> handleExecuteConsole(exchange) }
                http.createContext("/scene/pointer") { exchange -> handlePointerEvent(exchange) }
                http.createContext("/mcp/status") { exchange -> handleStatus(exchange) }
                http.createContext("/mcp/contract") { exchange -> handleContract(exchange) }
                http.executor = Executors.newCachedThreadPool { runnable ->
                    Thread(runnable, "k3d-mcp-http").apply { isDaemon = true }
                }
                http.start()
                server = http
                "MCP HTTP server started on port $port."
            } catch (t: Throwable) {
                "MCP HTTP server failed to start: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    fun stop(): String {
        synchronized(lock) {
            val running = server
            if (running == null) {
                return "MCP HTTP server is already stopped."
            }
            running.stop(0)
            server = null
            return "MCP HTTP server stopped."
        }
    }

    fun status(): String = synchronized(lock) {
        if (server != null) "MCP HTTP server running on port $port." else "MCP HTTP server stopped (port $port)."
    }

    fun port(): Int = port

    fun setPort(value: Int): String {
        val next = sanitizePort(value)
        synchronized(lock) {
            if (next == port) {
                return "MCP HTTP server port already set to $port."
            }
            val wasRunning = server != null
            if (wasRunning) {
                server?.stop(0)
                server = null
            }
            port = next
            if (wasRunning) {
                return start()
            }
            return "MCP HTTP server port set to $port."
        }
    }

    private fun handleStatus(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            exchange.respondJson(405, """{"success":false,"message":"Method not allowed"}""")
            return
        }
        val running = server != null
        exchange.respondJson(
            200,
            """{"success":true,"running":$running,"port":$port,"message":"${jsonEscape(status())}"}"""
        )
    }

    private fun handleContract(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            exchange.respondJson(405, """{"success":false,"message":"Method not allowed"}""")
            return
        }
        val payload = try {
            contractProvider()
        } catch (t: Throwable) {
            null
        }
        if (payload.isNullOrBlank()) {
            exchange.respondJson(500, """{"success":false,"message":"Contract payload unavailable"}""")
            return
        }
        exchange.respondJson(200, payload)
    }

    private fun handleListCommands(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            exchange.respondJson(405, """{"success":false,"message":"Method not allowed"}""")
            return
        }
        val payload = listCommands().joinToString(",") { command ->
            val tags = command.tags.joinToString(",") { tag -> "\"${jsonEscape(tag)}\"" }
            val icon = command.icon?.let { "\"${jsonEscape(it)}\"" } ?: "null"
            """{"id":"${jsonEscape(command.id)}","name":"${jsonEscape(command.name)}","category":"${jsonEscape(command.category)}","description":"${jsonEscape(command.description)}","icon":$icon,"priority":${command.priority},"tags":[$tags]}"""
        }
        exchange.respondJson(200, """{"success":true,"commands":[$payload]}""")
    }

    private fun handleExecuteCommand(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET" && exchange.requestMethod != "POST") {
            exchange.respondJson(405, """{"success":false,"message":"Method not allowed"}""")
            return
        }
        val bodyText = exchange.readBodyTextIfPost()
        val commandId = extractCommandId(exchange, bodyText)
        if (commandId.isNullOrBlank()) {
            exchange.respondJson(400, """{"success":false,"message":"Missing command id"}""")
            return
        }
        val startSeq = stdoutTap.currentSeq()
        val startTime = System.currentTimeMillis()
        val result = try {
            executeCommand(commandId)
        } catch (t: Throwable) {
            PluginResult.failure("Command execution failed: ${t.message ?: t.javaClass.simpleName}")
        }
        val duration = max(0L, System.currentTimeMillis() - startTime)
        val lines = stdoutTap.entriesSince(startSeq)
        val stdoutLines = lines.joinToString(",") { entry ->
            """"${jsonEscape("[${entry.stream}] ${entry.line}")}""""
        }
        val stdoutText = lines.joinToString("\n") { "[${it.stream}] ${it.line}" }
        val message = result.message ?: if (result.success) "OK" else "Command failed"
        val status = if (result.success) 200 else 500
        val body = """
            {"success":${result.success},"commandId":"${jsonEscape(commandId)}","message":"${jsonEscape(message)}","durationMs":$duration,"stdout":"${jsonEscape(stdoutText)}","stdoutLines":[$stdoutLines]}
        """.trimIndent()
        exchange.respondJson(status, body)
    }

    private fun handleExecuteConsole(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET" && exchange.requestMethod != "POST") {
            exchange.respondJson(405, """{"success":false,"message":"Method not allowed"}""")
            return
        }
        val bodyText = exchange.readBodyTextIfPost()
        val source = extractConsoleCommand(exchange, bodyText)
        if (source.isNullOrBlank()) {
            exchange.respondJson(400, """{"success":false,"message":"Missing console command"}""")
            return
        }
        val startSeq = stdoutTap.currentSeq()
        val startTime = System.currentTimeMillis()
        val result = try {
            executeConsoleCommand(source)
        } catch (t: Throwable) {
            McpConsoleResult(
                success = false,
                message = "Console execution failed: ${t.message ?: t.javaClass.simpleName}"
            )
        }
        val duration = max(0L, System.currentTimeMillis() - startTime)
        val lines = stdoutTap.entriesSince(startSeq)
        val stdoutLines = lines.joinToString(",") { entry ->
            """"${jsonEscape("[${entry.stream}] ${entry.line}")}""""
        }
        val stdoutText = lines.joinToString("\n") { "[${it.stream}] ${it.line}" }
        val consoleOutput = result.outputLines.joinToString(",") { line -> """"${jsonEscape(line)}"""" }
        val status = if (result.success) 200 else 500
        val body = """
            {"success":${result.success},"command":"${jsonEscape(source)}","message":"${jsonEscape(result.message)}","durationMs":$duration,"outputLines":[$consoleOutput],"stdout":"${jsonEscape(stdoutText)}","stdoutLines":[$stdoutLines]}
        """.trimIndent()
        exchange.respondJson(status, body)
    }

    private fun handlePointerEvent(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET" && exchange.requestMethod != "POST") {
            exchange.respondJson(405, """{"success":false,"message":"Method not allowed"}""")
            return
        }
        val bodyText = exchange.readBodyTextIfPost()
        val request = extractPointerEvent(exchange, bodyText)
        if (request == null) {
            exchange.respondJson(
                400,
                """{"success":false,"message":"Invalid pointer payload. Provide action (down|move|up) with screenX/screenY and/or worldX/worldY/worldZ."}"""
            )
            return
        }
        val startSeq = stdoutTap.currentSeq()
        val startTime = System.currentTimeMillis()
        val result = try {
            dispatchPointerEvent(request)
        } catch (t: Throwable) {
            McpPointerEventResult(
                success = false,
                handled = false,
                message = "Pointer dispatch failed: ${t.message ?: t.javaClass.simpleName}",
                action = request.action,
                pointer = request.pointer,
                button = request.button
            )
        }
        val duration = max(0L, System.currentTimeMillis() - startTime)
        val lines = stdoutTap.entriesSince(startSeq)
        val stdoutLines = lines.joinToString(",") { entry ->
            """"${jsonEscape("[${entry.stream}] ${entry.line}")}""""
        }
        val stdoutText = lines.joinToString("\n") { "[${it.stream}] ${it.line}" }
        val status = if (result.success) 200 else 500
        val body = """
            {"success":${result.success},"handled":${result.handled},"action":"${jsonEscape(result.action)}","pointer":${result.pointer},"button":${result.button},"screenX":${toJsonInt(result.screenX)},"screenY":${toJsonInt(result.screenY)},"worldX":${toJsonFloat(result.worldX)},"worldY":${toJsonFloat(result.worldY)},"worldZ":${toJsonFloat(result.worldZ)},"normalX":${toJsonFloat(result.normalX)},"normalY":${toJsonFloat(result.normalY)},"normalZ":${toJsonFloat(result.normalZ)},"valid":${result.valid},"message":"${jsonEscape(result.message)}","durationMs":$duration,"stdout":"${jsonEscape(stdoutText)}","stdoutLines":[$stdoutLines]}
        """.trimIndent()
        exchange.respondJson(status, body)
    }

    private fun extractCommandId(exchange: HttpExchange, bodyText: String?): String? {
        val fromQuery = parseQuery(exchange.requestURI.rawQuery)["id"]?.trim()
        if (!fromQuery.isNullOrEmpty()) {
            return fromQuery
        }
        if (exchange.requestMethod != "POST") {
            return null
        }
        val body = bodyText?.trim().orEmpty()
        if (body.isBlank()) {
            return null
        }
        if (!body.startsWith("{")) {
            return body
        }
        return jsonStringField(body, "id")?.trim()
    }

    private fun extractConsoleCommand(exchange: HttpExchange, bodyText: String?): String? {
        val query = parseQuery(exchange.requestURI.rawQuery)
        val fromQuery = query["cmd"]?.trim()
            ?: query["command"]?.trim()
            ?: query["script"]?.trim()
        if (!fromQuery.isNullOrBlank()) {
            return fromQuery
        }
        if (exchange.requestMethod != "POST") {
            return null
        }
        val body = bodyText?.trim().orEmpty()
        if (body.isBlank()) {
            return null
        }
        if (!body.startsWith("{")) {
            return body
        }
        return jsonStringField(body, "cmd")
            ?: jsonStringField(body, "command")
            ?: jsonStringField(body, "script")
    }

    private fun extractPointerEvent(exchange: HttpExchange, bodyText: String?): McpPointerEventRequest? {
        val query = parseQuery(exchange.requestURI.rawQuery)
        val body = bodyText?.trim().orEmpty()
        val isJson = body.startsWith("{")
        fun readString(vararg keys: String): String? {
            keys.forEach { key ->
                val fromQuery = query[key]?.trim()
                if (!fromQuery.isNullOrEmpty()) {
                    return fromQuery
                }
                if (isJson) {
                    val fromJson = jsonStringField(body, key)?.trim()
                    if (!fromJson.isNullOrEmpty()) {
                        return fromJson
                    }
                }
            }
            return null
        }
        fun readInt(vararg keys: String): Int? {
            keys.forEach { key ->
                val fromQuery = query[key]?.trim()?.toIntOrNull()
                if (fromQuery != null) {
                    return fromQuery
                }
                if (isJson) {
                    val fromJson = jsonNumberField(body, key)?.toInt()
                    if (fromJson != null) {
                        return fromJson
                    }
                }
            }
            return null
        }
        fun readFloat(vararg keys: String): Float? {
            keys.forEach { key ->
                val fromQuery = query[key]?.trim()?.toFloatOrNull()
                if (fromQuery != null) {
                    return fromQuery
                }
                if (isJson) {
                    val fromJson = jsonNumberField(body, key)?.toFloat()
                    if (fromJson != null) {
                        return fromJson
                    }
                }
            }
            return null
        }
        fun readBoolean(vararg keys: String): Boolean? {
            keys.forEach { key ->
                val fromQuery = parseBoolean(query[key])
                if (fromQuery != null) {
                    return fromQuery
                }
                if (isJson) {
                    val fromJson = jsonBooleanField(body, key)
                    if (fromJson != null) {
                        return fromJson
                    }
                }
            }
            return null
        }
        val actionRaw = readString("action", "event", "type") ?: return null
        val action = actionRaw.lowercase(Locale.US)
        if (action != "down" && action != "move" && action != "up") {
            return null
        }
        val buttonRaw = readString("button")
        val button = parseButton(buttonRaw) ?: readInt("button") ?: Input.Buttons.LEFT
        val pointer = readInt("pointer")?.coerceAtLeast(0) ?: 0
        val screenX = readInt("screenX", "sx")
        val screenY = readInt("screenY", "sy")
        val worldX = readFloat("worldX", "x")
        val worldY = readFloat("worldY", "y")
        val worldZ = readFloat("worldZ", "z")
        val hasWorld = worldX != null && worldY != null && worldZ != null
        if (screenX == null || screenY == null) {
            if (!hasWorld) {
                return null
            }
        }
        return McpPointerEventRequest(
            action = action,
            pointer = pointer,
            button = button,
            screenX = screenX,
            screenY = screenY,
            worldX = worldX,
            worldY = worldY,
            worldZ = worldZ,
            normalX = readFloat("normalX", "nx"),
            normalY = readFloat("normalY", "ny"),
            normalZ = readFloat("normalZ", "nz"),
            valid = readBoolean("valid")
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) {
            return emptyMap()
        }
        return rawQuery.split("&")
            .mapNotNull { piece ->
                val eq = piece.indexOf('=')
                if (eq <= 0) {
                    return@mapNotNull null
                }
                val key = decode(piece.substring(0, eq))
                val value = decode(piece.substring(eq + 1))
                key to value
            }
            .toMap()
    }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

    private fun sanitizePort(value: Int): Int = value.coerceIn(1, 65535)

    private fun parseButton(raw: String?): Int? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return when (raw.trim().lowercase(Locale.US)) {
            "left", "lmb", "0" -> Input.Buttons.LEFT
            "right", "rmb", "1" -> Input.Buttons.RIGHT
            "middle", "mmb", "2" -> Input.Buttons.MIDDLE
            else -> raw.trim().toIntOrNull()
        }
    }

    private fun parseBoolean(raw: String?): Boolean? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return when (raw.trim().lowercase(Locale.US)) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> null
        }
    }

    private fun jsonStringField(body: String, field: String): String? {
        val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"")
        val match = pattern.find(body) ?: return null
        return unescapeJsonString(match.groupValues[1])
    }

    private fun jsonNumberField(body: String, field: String): Double? {
        val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        val match = pattern.find(body) ?: return null
        return match.groupValues.getOrNull(1)?.toDoubleOrNull()
    }

    private fun jsonBooleanField(body: String, field: String): Boolean? {
        val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*(true|false)")
        val match = pattern.find(body) ?: return null
        return match.groupValues.getOrNull(1)?.equals("true", ignoreCase = true)
    }

    private fun unescapeJsonString(value: String): String {
        return value
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun toJsonInt(value: Int?): String = value?.toString() ?: "null"

    private fun toJsonFloat(value: Float?): String = value?.toString() ?: "null"

    private fun jsonEscape(value: String): String {
        val out = StringBuilder(value.length + 16)
        value.forEach { ch ->
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun HttpExchange.respondJson(status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { os -> os.write(bytes) }
    }

    private fun HttpExchange.readBodyTextIfPost(): String? {
        if (requestMethod != "POST") {
            return null
        }
        return requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }
}
