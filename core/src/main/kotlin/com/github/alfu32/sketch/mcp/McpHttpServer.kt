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

private data class McpToolDef(
    val name: String,
    val description: String,
    val inputSchema: String
)

private data class McpResourceDef(
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String = "application/json"
)

class McpHttpServer(
    initialPort: Int,
    private val stdoutTap: StdoutTap,
    private val listCommands: () -> List<PaletteCommand>,
    private val executeCommand: (String) -> PluginResult,
    private val executeConsoleCommand: (String) -> McpConsoleResult,
    private val dispatchPointerEvent: (McpPointerEventRequest) -> McpPointerEventResult,
    private val contractProvider: () -> String,
    private val sceneSummaryProvider: () -> String,
    private val selectionSummaryProvider: () -> String
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
                http.createContext("/") { exchange -> handleMcpProtocol(exchange) }
                http.createContext("/mcp") { exchange -> handleMcpProtocol(exchange) }
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

    private fun handleMcpProtocol(exchange: HttpExchange) {
        if (exchange.requestMethod == "OPTIONS") {
            exchange.respondNoContentWithCors()
            return
        }
        if (exchange.requestMethod == "GET") {
            val body = """{"name":"k3d","transport":"streamable_http","message":"POST JSON-RPC requests to this endpoint"}"""
            exchange.respondJson(200, body, addCors = true)
            return
        }
        if (exchange.requestMethod != "POST") {
            exchange.respondJson(
                405,
                """{"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Method not allowed"}}""",
                addCors = true
            )
            return
        }
        val body = exchange.readBodyTextIfPost()?.trim().orEmpty()
        if (body.isBlank()) {
            exchange.respondJson(
                400,
                """{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"Empty JSON-RPC payload"}}""",
                addCors = true
            )
            return
        }
        val method = jsonStringField(body, "method")
        val idRaw = jsonRawField(body, "id")
        if (method.isNullOrBlank()) {
            exchange.respondJson(
                400,
                jsonRpcError(idRaw, -32600, "Missing JSON-RPC method"),
                addCors = true
            )
            return
        }
        if (method == "notifications/initialized" || method == "initialized") {
            exchange.respondJson(204, "", addCors = true)
            return
        }
        if (idRaw == null) {
            // Notification without id: execute and return no content.
            executeMcpMethod(method, body)
            exchange.respondJson(204, "", addCors = true)
            return
        }
        val response = executeMcpMethod(method, body)
        when {
            response.errorCode != null -> {
                exchange.respondJson(
                    200,
                    jsonRpcError(idRaw, response.errorCode, response.message ?: "Error"),
                    addCors = true
                )
            }

            else -> {
                val resultJson = response.resultJson ?: "{}"
                val payload = """{"jsonrpc":"2.0","id":$idRaw,"result":$resultJson}"""
                exchange.respondJson(200, payload, addCors = true)
            }
        }
    }

    private data class McpMethodResult(
        val resultJson: String? = null,
        val errorCode: Int? = null,
        val message: String? = null
    )

    private fun executeMcpMethod(method: String, body: String): McpMethodResult {
        return when (method) {
            "initialize" -> {
                val params = jsonObjectField(body, "params") ?: ""
                val clientProtocol = jsonStringField(params, "protocolVersion") ?: "2024-11-05"
                val result = """
                    {"protocolVersion":"${jsonEscape(clientProtocol)}","capabilities":{"tools":{},"resources":{}},"serverInfo":{"name":"k3d","version":"1.0.0"}}
                """.trimIndent()
                McpMethodResult(resultJson = result)
            }

            "ping" -> McpMethodResult(resultJson = "{}")
            "tools/list" -> McpMethodResult(resultJson = mcpToolsListResult())
            "tools/call" -> handleMcpToolsCall(body)
            "resources/list" -> McpMethodResult(resultJson = mcpResourcesListResult())
            "resources/read" -> handleMcpResourcesRead(body)
            "prompts/list" -> McpMethodResult(resultJson = """{"prompts":[]}""")
            else -> McpMethodResult(errorCode = -32601, message = "Method not found: $method")
        }
    }

    private fun mcpToolsListResult(): String {
        val tools = mcpToolDefs().joinToString(",") { tool ->
            """{"name":"${jsonEscape(tool.name)}","description":"${jsonEscape(tool.description)}","inputSchema":${tool.inputSchema}}"""
        }
        return """{"tools":[$tools]}"""
    }

    private fun mcpResourcesListResult(): String {
        val resources = mcpResourceDefs().joinToString(",") { resource ->
            """{"uri":"${jsonEscape(resource.uri)}","name":"${jsonEscape(resource.name)}","description":"${jsonEscape(resource.description)}","mimeType":"${jsonEscape(resource.mimeType)}"}"""
        }
        return """{"resources":[$resources]}"""
    }

    private fun mcpResourceDefs(): List<McpResourceDef> {
        return listOf(
            McpResourceDef(
                uri = "k3d://contract",
                name = "K3D MCP Contract",
                description = "Same payload as /mcp/contract."
            ),
            McpResourceDef(
                uri = "k3d://commands",
                name = "K3D Command Catalog Snapshot",
                description = "Current command IDs and metadata."
            ),
            McpResourceDef(
                uri = "k3d://scene/summary",
                name = "K3D Scene Summary",
                description = "Counts (groups/faces/edges/voxels) and active tool/camera."
            ),
            McpResourceDef(
                uri = "k3d://selection",
                name = "K3D Selection Summary",
                description = "Current selection counts and selected entity summaries."
            ),
            McpResourceDef(
                uri = "k3d://logs/recent",
                name = "K3D Recent Logs",
                description = "Recent stdout/stderr lines from the in-app log tap."
            )
        )
    }

    private fun mcpToolDefs(): List<McpToolDef> {
        return listOf(
            McpToolDef(
                name = "k3d_list_commands",
                description = "List available K3D command IDs and metadata.",
                inputSchema = """{"type":"object","properties":{},"additionalProperties":false}"""
            ),
            McpToolDef(
                name = "k3d_execute_command",
                description = "Execute a command by command ID.",
                inputSchema = """{"type":"object","properties":{"id":{"type":"string"}},"required":["id"],"additionalProperties":false}"""
            ),
            McpToolDef(
                name = "k3d_execute_console",
                description = "Execute a K3D console/meta command or Groovy script.",
                inputSchema = """{"type":"object","properties":{"cmd":{"type":"string"}},"required":["cmd"],"additionalProperties":false}"""
            ),
            McpToolDef(
                name = "k3d_pointer_event",
                description = "Dispatch pointer event (down/move/up) using screen and/or world coordinates.",
                inputSchema = """{"type":"object","properties":{"action":{"type":"string","enum":["down","move","up"]},"pointer":{"type":"integer"},"button":{"oneOf":[{"type":"string"},{"type":"integer"}]},"screenX":{"type":"integer"},"screenY":{"type":"integer"},"worldX":{"type":"number"},"worldY":{"type":"number"},"worldZ":{"type":"number"},"normalX":{"type":"number"},"normalY":{"type":"number"},"normalZ":{"type":"number"},"valid":{"type":"boolean"}},"required":["action"],"additionalProperties":false}"""
            ),
            McpToolDef(
                name = "k3d_status",
                description = "Get K3D MCP server status and port.",
                inputSchema = """{"type":"object","properties":{},"additionalProperties":false}"""
            ),
            McpToolDef(
                name = "k3d_contract",
                description = "Get full K3D contract payload.",
                inputSchema = """{"type":"object","properties":{},"additionalProperties":false}"""
            )
        )
    }

    private fun handleMcpToolsCall(body: String): McpMethodResult {
        val params = jsonObjectField(body, "params") ?: return McpMethodResult(
            errorCode = -32602,
            message = "Missing params"
        )
        val name = jsonStringField(params, "name") ?: return McpMethodResult(
            errorCode = -32602,
            message = "Missing tool name"
        )
        val argsObject = jsonObjectField(params, "arguments") ?: "{}"
        return when (name) {
            "k3d_list_commands" -> {
                val commands = listCommands().joinToString(",") { command ->
                    val tags = command.tags.joinToString(",") { tag -> "\"${jsonEscape(tag)}\"" }
                    val icon = command.icon?.let { "\"${jsonEscape(it)}\"" } ?: "null"
                    """{"id":"${jsonEscape(command.id)}","name":"${jsonEscape(command.name)}","category":"${jsonEscape(command.category)}","description":"${jsonEscape(command.description)}","icon":$icon,"priority":${command.priority},"tags":[$tags]}"""
                }
                val payload = """{"success":true,"commands":[$commands]}"""
                McpMethodResult(resultJson = mcpToolCallResultJson(payload))
            }

            "k3d_execute_command" -> {
                val commandId = jsonStringField(argsObject, "id")
                if (commandId.isNullOrBlank()) {
                    return McpMethodResult(resultJson = mcpToolCallResultJson("""{"success":false,"message":"Missing id"}""", isError = true))
                }
                val result = try {
                    executeCommand(commandId)
                } catch (t: Throwable) {
                    PluginResult.failure("Command execution failed: ${t.message ?: t.javaClass.simpleName}")
                }
                val payload = """{"success":${result.success},"message":"${jsonEscape(result.message ?: if (result.success) "OK" else "Command failed")}","commandId":"${jsonEscape(commandId)}"}"""
                McpMethodResult(resultJson = mcpToolCallResultJson(payload, isError = !result.success))
            }

            "k3d_execute_console" -> {
                val cmd = jsonStringField(argsObject, "cmd")
                if (cmd.isNullOrBlank()) {
                    return McpMethodResult(resultJson = mcpToolCallResultJson("""{"success":false,"message":"Missing cmd"}""", isError = true))
                }
                val result = try {
                    executeConsoleCommand(cmd)
                } catch (t: Throwable) {
                    McpConsoleResult(false, "Console execution failed: ${t.message ?: t.javaClass.simpleName}")
                }
                val outputLines = result.outputLines.joinToString(",") { line -> """"${jsonEscape(line)}"""" }
                val payload = """{"success":${result.success},"message":"${jsonEscape(result.message)}","outputLines":[$outputLines]}"""
                McpMethodResult(resultJson = mcpToolCallResultJson(payload, isError = !result.success))
            }

            "k3d_pointer_event" -> {
                val req = parsePointerEventFromArguments(argsObject)
                    ?: return McpMethodResult(
                        resultJson = mcpToolCallResultJson(
                            """{"success":false,"message":"Invalid pointer args"}""",
                            isError = true
                        )
                    )
                val result = try {
                    dispatchPointerEvent(req)
                } catch (t: Throwable) {
                    McpPointerEventResult(
                        success = false,
                        handled = false,
                        message = "Pointer dispatch failed: ${t.message ?: t.javaClass.simpleName}",
                        action = req.action,
                        pointer = req.pointer,
                        button = req.button
                    )
                }
                val payload = """{"success":${result.success},"handled":${result.handled},"message":"${jsonEscape(result.message)}","action":"${jsonEscape(result.action)}","pointer":${result.pointer},"button":${result.button},"screenX":${toJsonInt(result.screenX)},"screenY":${toJsonInt(result.screenY)},"worldX":${toJsonFloat(result.worldX)},"worldY":${toJsonFloat(result.worldY)},"worldZ":${toJsonFloat(result.worldZ)},"normalX":${toJsonFloat(result.normalX)},"normalY":${toJsonFloat(result.normalY)},"normalZ":${toJsonFloat(result.normalZ)},"valid":${result.valid}}"""
                McpMethodResult(resultJson = mcpToolCallResultJson(payload, isError = !result.success))
            }

            "k3d_status" -> {
                val running = server != null
                val payload = """{"success":true,"running":$running,"port":$port,"message":"${jsonEscape(status())}"}"""
                McpMethodResult(resultJson = mcpToolCallResultJson(payload))
            }

            "k3d_contract" -> {
                val payload = try {
                    contractProvider()
                } catch (_: Throwable) {
                    null
                }
                if (payload.isNullOrBlank()) {
                    McpMethodResult(
                        resultJson = mcpToolCallResultJson("""{"success":false,"message":"Contract payload unavailable"}""", isError = true)
                    )
                } else {
                    McpMethodResult(resultJson = mcpToolCallResultJson(payload))
                }
            }

            else -> McpMethodResult(
                resultJson = mcpToolCallResultJson("""{"success":false,"message":"Unknown tool: ${jsonEscape(name)}"}""", isError = true)
            )
        }
    }

    private fun handleMcpResourcesRead(body: String): McpMethodResult {
        val params = jsonObjectField(body, "params") ?: return McpMethodResult(
            errorCode = -32602,
            message = "Missing params"
        )
        val uri = jsonStringField(params, "uri")
            ?: jsonStringField(params, "resourceUri")
            ?: return McpMethodResult(errorCode = -32602, message = "Missing resource uri")
        val resourceText = when {
            uri == "k3d://contract" -> {
                contractProvider()
            }

            uri == "k3d://commands" -> {
                val commands = listCommands().sortedBy { it.id }.joinToString(",") { command ->
                    val tags = command.tags.joinToString(",") { tag -> "\"${jsonEscape(tag)}\"" }
                    val icon = command.icon?.let { "\"${jsonEscape(it)}\"" } ?: "null"
                    """{"id":"${jsonEscape(command.id)}","name":"${jsonEscape(command.name)}","category":"${jsonEscape(command.category)}","description":"${jsonEscape(command.description)}","icon":$icon,"priority":${command.priority},"tags":[$tags]}"""
                }
                """{"success":true,"generatedAt":"${jsonEscape(java.time.LocalDateTime.now().toString())}","commands":[$commands]}"""
            }

            uri == "k3d://scene/summary" -> {
                sceneSummaryProvider()
            }

            uri == "k3d://selection" -> {
                selectionSummaryProvider()
            }

            uri.startsWith("k3d://logs/recent") -> {
                val limit = resolveLogsLimit(uri, params)
                val entries = stdoutTap.entriesSince(0).takeLast(limit)
                val entryJson = entries.joinToString(",") { entry ->
                    """{"seq":${entry.seq},"stream":"${jsonEscape(entry.stream)}","line":"${jsonEscape(entry.line)}","timestampMs":${entry.timestampMs}}"""
                }
                """{"success":true,"count":${entries.size},"limit":$limit,"entries":[$entryJson]}"""
            }

            else -> null
        }
        if (resourceText.isNullOrBlank()) {
            return McpMethodResult(errorCode = -32602, message = "Unknown or unavailable resource: $uri")
        }
        val mimeType = mcpResourceDefs().firstOrNull { uri.startsWith(it.uri) }?.mimeType ?: "application/json"
        val result = """
            {"contents":[{"uri":"${jsonEscape(uri)}","mimeType":"${jsonEscape(mimeType)}","text":"${jsonEscape(resourceText)}"}]}
        """.trimIndent()
        return McpMethodResult(resultJson = result)
    }

    private fun resolveLogsLimit(uri: String, paramsObject: String): Int {
        val fromParams = jsonNumberField(paramsObject, "limit")?.toInt()
        if (fromParams != null) {
            return fromParams.coerceIn(1, 2000)
        }
        val query = runCatching { java.net.URI(uri).rawQuery }.getOrNull()
        if (!query.isNullOrBlank()) {
            parseQuery(query)["limit"]?.toIntOrNull()?.let { parsed ->
                return parsed.coerceIn(1, 2000)
            }
        }
        return 200
    }

    private fun parsePointerEventFromArguments(argsObject: String): McpPointerEventRequest? {
        val action = jsonStringField(argsObject, "action")?.lowercase(Locale.US) ?: return null
        if (action != "down" && action != "move" && action != "up") {
            return null
        }
        val screenX = jsonNumberField(argsObject, "screenX")?.toInt()
        val screenY = jsonNumberField(argsObject, "screenY")?.toInt()
        val worldX = jsonNumberField(argsObject, "worldX")?.toFloat()
        val worldY = jsonNumberField(argsObject, "worldY")?.toFloat()
        val worldZ = jsonNumberField(argsObject, "worldZ")?.toFloat()
        val hasWorld = worldX != null && worldY != null && worldZ != null
        val hasScreen = screenX != null && screenY != null
        if (!hasWorld && !hasScreen) {
            return null
        }
        val button = parseButton(jsonStringField(argsObject, "button"))
            ?: jsonNumberField(argsObject, "button")?.toInt()
            ?: Input.Buttons.LEFT
        val pointer = jsonNumberField(argsObject, "pointer")?.toInt()?.coerceAtLeast(0) ?: 0
        return McpPointerEventRequest(
            action = action,
            pointer = pointer,
            button = button,
            screenX = screenX,
            screenY = screenY,
            worldX = worldX,
            worldY = worldY,
            worldZ = worldZ,
            normalX = jsonNumberField(argsObject, "normalX")?.toFloat(),
            normalY = jsonNumberField(argsObject, "normalY")?.toFloat(),
            normalZ = jsonNumberField(argsObject, "normalZ")?.toFloat(),
            valid = jsonBooleanField(argsObject, "valid")
        )
    }

    private fun mcpToolCallResultJson(payloadJson: String, isError: Boolean = false): String {
        val textPayload = jsonEscape(payloadJson)
        return """{"content":[{"type":"text","text":"$textPayload"}],"structuredContent":$payloadJson,"isError":$isError}"""
    }

    private fun jsonRpcError(idRaw: String?, code: Int, message: String): String {
        val id = idRaw ?: "null"
        return """{"jsonrpc":"2.0","id":$id,"error":{"code":$code,"message":"${jsonEscape(message)}"}}"""
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

    private fun jsonRawField(body: String, field: String): String? {
        val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*(\"(?:\\\\.|[^\\\\\"])*\"|-?\\d+(?:\\.\\d+)?|true|false|null)")
        val match = pattern.find(body) ?: return null
        return match.groupValues.getOrNull(1)?.trim()
    }

    private fun jsonObjectField(body: String, field: String): String? {
        val keyPattern = Regex("\"${Regex.escape(field)}\"\\s*:")
        val match = keyPattern.find(body) ?: return null
        var idx = match.range.last + 1
        while (idx < body.length && body[idx].isWhitespace()) {
            idx++
        }
        if (idx >= body.length || body[idx] != '{') {
            return null
        }
        val end = findJsonBlockEnd(body, idx, '{', '}') ?: return null
        return body.substring(idx, end + 1)
    }

    private fun findJsonBlockEnd(body: String, start: Int, open: Char, close: Char): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < body.length) {
            val ch = body[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                i++
                continue
            }
            if (ch == '"') {
                inString = true
                i++
                continue
            }
            if (ch == open) {
                depth++
            } else if (ch == close) {
                depth--
                if (depth == 0) {
                    return i
                }
            }
            i++
        }
        return null
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

    private fun HttpExchange.respondJson(status: Int, body: String, addCors: Boolean = false) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        if (addCors) {
            responseHeaders.set("Access-Control-Allow-Origin", "*")
            responseHeaders.set("Access-Control-Allow-Headers", "Content-Type, Accept, Authorization")
            responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        }
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { os -> os.write(bytes) }
    }

    private fun HttpExchange.respondNoContentWithCors() {
        responseHeaders.set("Access-Control-Allow-Origin", "*")
        responseHeaders.set("Access-Control-Allow-Headers", "Content-Type, Accept, Authorization")
        responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        sendResponseHeaders(204, -1)
        responseBody.close()
    }

    private fun HttpExchange.readBodyTextIfPost(): String? {
        if (requestMethod != "POST") {
            return null
        }
        return requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }
}
