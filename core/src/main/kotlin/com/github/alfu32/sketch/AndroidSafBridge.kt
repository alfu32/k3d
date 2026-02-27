package com.github.alfu32.sketch

interface AndroidSafBridge {
    fun openDocument(onResult: (uri: String?, displayName: String?) -> Unit)
    fun createDocument(defaultName: String, onResult: (uri: String?, displayName: String?) -> Unit)
    fun readBytes(uri: String): ByteArray?
    fun writeBytes(uri: String, data: ByteArray): Boolean
}

object AndroidSaf {
    @Volatile
    var bridge: AndroidSafBridge? = null
}
