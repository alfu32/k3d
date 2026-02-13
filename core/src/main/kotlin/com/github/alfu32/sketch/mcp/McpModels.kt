package com.github.alfu32.sketch.mcp

data class McpPointerEventRequest(
    val action: String,
    val pointer: Int = 0,
    val button: Int = 0,
    val screenX: Int? = null,
    val screenY: Int? = null,
    val worldX: Float? = null,
    val worldY: Float? = null,
    val worldZ: Float? = null,
    val normalX: Float? = null,
    val normalY: Float? = null,
    val normalZ: Float? = null,
    val valid: Boolean? = null
)

data class McpPointerEventResult(
    val success: Boolean,
    val handled: Boolean,
    val message: String,
    val action: String,
    val pointer: Int,
    val button: Int,
    val screenX: Int? = null,
    val screenY: Int? = null,
    val worldX: Float? = null,
    val worldY: Float? = null,
    val worldZ: Float? = null,
    val normalX: Float? = null,
    val normalY: Float? = null,
    val normalZ: Float? = null,
    val valid: Boolean = false
)

data class McpConsoleResult(
    val success: Boolean,
    val message: String,
    val outputLines: List<String> = emptyList()
)
