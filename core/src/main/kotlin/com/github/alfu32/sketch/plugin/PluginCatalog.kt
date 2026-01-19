package com.github.alfu32.sketch.plugin

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import java.io.File

data class PluginEntry(
    var url: String = "",
    var enabled: Boolean = true,
    var fileName: String? = null
)

class PluginCatalog(
    var plugins: MutableList<PluginEntry> = mutableListOf()
) {
    fun save(file: File) {
        val json = Json().apply { setOutputType(JsonWriter.OutputType.json) }
        file.parentFile?.mkdirs()
        file.writeText(json.prettyPrint(this))
    }

    companion object {
        fun load(file: File): PluginCatalog {
            if (!file.exists()) {
                return PluginCatalog()
            }
            val json = Json()
            return try {
                json.fromJson(PluginCatalog::class.java, file.readText()) ?: PluginCatalog()
            } catch (_: Exception) {
                PluginCatalog()
            }
        }
    }
}

data class PluginEntryInfo(
    val url: String,
    val enabled: Boolean,
    val installed: Boolean,
    val name: String?,
    val version: String?,
    val lastError: String?
)
