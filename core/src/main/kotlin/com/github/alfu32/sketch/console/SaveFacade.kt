package com.github.alfu32.sketch.console

import java.io.File

class SaveFacade(
    private val getFile: () -> File,
    private val setFile: (File) -> Unit
) {
    fun path(): String = getFile().absolutePath

    fun name(): String = getFile().name

    fun set(path: String) {
        setFile(File(path).absoluteFile)
    }

    override fun toString(): String = getFile().absolutePath
}
