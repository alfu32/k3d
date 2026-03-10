package com.github.alfu32.sketch

fun interface WebOpenTextHandler {
    fun onResult(fileName: String?, content: String?)
}

fun interface WebSaveTextHandler {
    fun onResult(success: Boolean, message: String?)
}

interface WebRuntimeBridge {
    fun openTextDocument(accept: String, handler: WebOpenTextHandler)
    fun saveTextDocument(fileName: String, content: String, mimeType: String, handler: WebSaveTextHandler)
    fun readLocalStorage(key: String): String?
    fun writeLocalStorage(key: String, value: String): Boolean
    fun readEmbedConfigJson(): String? = null
    fun pollEmbedCommandJson(): String? = null
    fun resolveEmbedCommand(requestId: String, success: Boolean, payloadJson: String?, message: String?) {}
    fun emitEmbedEvent(eventType: String, detailJson: String?) {}
}

object WebRuntime {
    @Volatile
    var bridge: WebRuntimeBridge? = null
}
