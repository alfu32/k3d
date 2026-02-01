package com.github.alfu32.sketch.console

import com.github.alfu32.sketch.tui.OutputPane
import java.io.StringWriter
import java.lang.reflect.Modifier

class ConsoleUtils(
    private val outputPane: OutputPane
) {
    fun log(vararg args: Any?) {
        if (args.isEmpty()) {
            outputPane.append("")
            return
        }
        outputPane.append(args.joinToString(" ") { it?.toString() ?: "null" })
    }

    fun dir(obj: Any?) {
        if (obj == null) {
            outputPane.append("null")
            return
        }
        val klass = obj.javaClass
        val lines = mutableListOf<String>()
        lines.add("${klass.name} {")
        klass.declaredFields.sortedBy { it.name }.forEach { field ->
            if (Modifier.isStatic(field.modifiers)) {
                return@forEach
            }
            field.isAccessible = true
            val value = runCatching { field.get(obj) }.getOrNull()
            lines.add("  ${field.name} = ${value ?: "null"}")
        }
        lines.add("}")
        outputPane.append(lines.joinToString("\n"))
    }

    fun type(obj: Any?) {
        outputPane.append(obj?.javaClass?.name ?: "null")
    }

    fun exception(ex: Throwable) {
        val writer = StringWriter()
        ex.printStackTrace(java.io.PrintWriter(writer))
        outputPane.append(writer.toString().trimEnd())
    }
}
