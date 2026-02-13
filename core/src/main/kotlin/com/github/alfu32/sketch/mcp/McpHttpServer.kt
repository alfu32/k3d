package com.github.alfu32.sketch.mcp

import com.github.alfu32.sketch.plugin.PaletteCommand
import com.github.alfu32.sketch.plugin.PluginResult
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.math.max

class McpHttpServer(
    initialPort: Int,
    private val stdoutTap: StdoutTap,
    private val listCommands: () -> List<PaletteCommand>,
    private val executeCommand: (String) -> PluginResult
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
                http.createContext("/mcp/status") { exchange -> handleStatus(exchange) }
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
        val commandId = extractCommandId(exchange)
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

    private fun extractCommandId(exchange: HttpExchange): String? {
        val fromQuery = parseQuery(exchange.requestURI.rawQuery)["id"]?.trim()
        if (!fromQuery.isNullOrEmpty()) {
            return fromQuery
        }
        if (exchange.requestMethod != "POST") {
            return null
        }
        val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.trim()
        if (body.isBlank()) {
            return null
        }
        if (!body.startsWith("{")) {
            return body
        }
        val match = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body) ?: return null
        return match.groupValues.getOrNull(1)?.trim()
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
}
