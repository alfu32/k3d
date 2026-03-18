package com.github.alfu32.sketch.lwjgl3.console

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.io.FileInputStream
import java.io.InputStream

object WindowsConsole {
    private const val ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004
    private const val DISABLE_NEWLINE_AUTO_RETURN = 0x0008
    private const val STD_OUTPUT_HANDLE = -11
    private const val STD_INPUT_HANDLE = -10
    private const val ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200
    private const val ENABLE_WINDOW_INPUT = 0x0008
    private const val ENABLE_EXTENDED_FLAGS = 0x0080
    private const val ENABLE_QUICK_EDIT_MODE = 0x0040
    private const val ENABLE_ECHO_INPUT = 0x0004
    private const val ENABLE_LINE_INPUT = 0x0002
    private const val ENABLE_PROCESSED_INPUT = 0x0001
    private const val ENABLE_PROCESSED_OUTPUT = 0x0001
    private const val ENABLE_WRAP_AT_EOL_OUTPUT = 0x0002
    private const val KEY_EVENT = 0x0001
    private const val WINDOW_BUFFER_SIZE_EVENT = 0x0004
    private const val SHIFT_PRESSED = 0x0010
    private const val LEFT_ALT_PRESSED = 0x0002
    private const val RIGHT_ALT_PRESSED = 0x0001
    private const val LEFT_CTRL_PRESSED = 0x0008
    private const val RIGHT_CTRL_PRESSED = 0x0004
    private const val FILE_TYPE_CHAR = 0x0002
    private const val GENERIC_READ = 0x80000000.toInt()
    private const val GENERIC_WRITE = 0x40000000
    private const val FILE_SHARE_READ = 0x00000001
    private const val FILE_SHARE_WRITE = 0x00000002
    private const val OPEN_EXISTING = 3
    private const val CP_UTF8 = 65001

    private val isWindows =
        System.getProperty("os.name")?.startsWith("Windows", ignoreCase = true) == true
    private val kernel32: Kernel32? = if (isWindows) {
        runCatching { Native.load("kernel32", Kernel32::class.java) as Kernel32 }.getOrNull()
    } else {
        null
    }
    private val readConsoleInputExW: Function? = if (isWindows) {
        loadReadConsoleInputExW()
    } else {
        null
    }
    @Volatile private var savedInputMode: Int? = null
    @Volatile private var savedOutputMode: Int? = null
    @Volatile private var savedInputCp: Int = 0
    @Volatile private var savedOutputCp: Int = 0
    @Volatile private var stdinHandle: Pointer? = null
    @Volatile private var stdoutHandle: Pointer? = null
    @Volatile private var inputThreadStarted = false
    private val eventQueue = ArrayDeque<ConsoleEvent>()
    private val queueLock = Any()

    fun isWindows(): Boolean = isWindows

    fun openInputStream(): InputStream {
        if (!isWindows) return System.`in`
        val k32 = kernel32 ?: return System.`in`
        val handle = k32.GetStdHandle(STD_INPUT_HANDLE)
        val fileType = if (handle == null) 0 else k32.GetFileType(handle)
        if (fileType == FILE_TYPE_CHAR) {
            return System.`in`
        }
        return runCatching { FileInputStream("CONIN$") }.getOrDefault(System.`in`)
    }

    fun enableVirtualTerminalProcessing(): Boolean {
        if (!isWindows) return true
        val k32 = kernel32 ?: return false
        val handle = ensureStdoutHandle(k32) ?: return false
        val modeRef = IntByReference()
        if (!k32.GetConsoleMode(handle, modeRef)) return false
        if (savedOutputMode == null) {
            savedOutputMode = modeRef.value
        }
        val mode = modeRef.value or ENABLE_VIRTUAL_TERMINAL_PROCESSING or DISABLE_NEWLINE_AUTO_RETURN or
            ENABLE_PROCESSED_OUTPUT or ENABLE_WRAP_AT_EOL_OUTPUT
        if (!k32.SetConsoleMode(handle, mode)) return false
        ensureUtf8CodePage(k32)
        return true
    }

    fun enableVirtualTerminalInput(): Boolean {
        if (!isWindows) return true
        val k32 = kernel32 ?: return false
        val handle = ensureStdinHandle(k32) ?: return false
        val modeRef = IntByReference()
        if (!k32.GetConsoleMode(handle, modeRef)) return false
        if (savedInputMode == null) {
            savedInputMode = modeRef.value
        }
        var mode = modeRef.value
        // Keep VT input off here. This controller uses Win32 console events directly,
        // and VT input causes Windows Terminal to feed CSI mouse/key sequences as text.
        mode = mode or ENABLE_WINDOW_INPUT or ENABLE_EXTENDED_FLAGS
        mode = mode and ENABLE_QUICK_EDIT_MODE.inv()
        mode = mode and (ENABLE_ECHO_INPUT or ENABLE_LINE_INPUT or ENABLE_PROCESSED_INPUT).inv()
        val ok = k32.SetConsoleMode(handle, mode)
        ensureUtf8CodePage(k32)
        return ok
    }

    fun restoreInputMode() {
        if (!isWindows) return
        val k32 = kernel32 ?: return
        savedInputMode?.let { saved ->
            val handle = stdinHandle ?: k32.GetStdHandle(STD_INPUT_HANDLE)
            if (handle != null) {
                k32.SetConsoleMode(handle, saved)
            }
        }
        savedOutputMode?.let { saved ->
            val handle = stdoutHandle ?: k32.GetStdHandle(STD_OUTPUT_HANDLE)
            if (handle != null) {
                k32.SetConsoleMode(handle, saved)
            }
        }
        if (savedInputCp != 0) {
            k32.SetConsoleCP(savedInputCp)
        }
        if (savedOutputCp != 0) {
            k32.SetConsoleOutputCP(savedOutputCp)
        }
    }

    fun getConsoleSize(): Pair<Int, Int>? {
        if (!isWindows) return null
        val k32 = kernel32 ?: return null
        val handle = ensureStdoutHandle(k32) ?: return null
        val info = CONSOLE_SCREEN_BUFFER_INFO()
        if (!k32.GetConsoleScreenBufferInfo(handle, info)) return null
        val cols = info.srWindow.Right - info.srWindow.Left + 1
        val rows = info.srWindow.Bottom - info.srWindow.Top + 1
        return rows.toInt() to cols.toInt()
    }

    fun pollConsoleEvent(): ConsoleEvent? {
        if (!isWindows) return null
        startInputPump()
        return synchronized(queueLock) {
            if (eventQueue.isEmpty()) null else eventQueue.removeFirst()
        }
    }

    private fun startInputPump() {
        if (!isWindows || inputThreadStarted) return
        val k32 = kernel32 ?: return
        val handle = ensureStdinHandle(k32) ?: return
        inputThreadStarted = true
        Thread {
            val record = INPUT_RECORD()
            val readRef = IntByReference()
            while (true) {
                val ok = if (readConsoleInputExW != null) {
                    readConsoleInputExW.invokeInt(arrayOf(handle, record, 1, readRef, 0)) != 0
                } else {
                    k32.ReadConsoleInputW(handle, record, 1, readRef)
                }
                if (!ok) break
                if (readRef.value <= 0) continue
                record.read()
                val event = when (record.EventType.toInt()) {
                    KEY_EVENT -> {
                        record.Event.setType(KEY_EVENT_RECORD::class.java)
                        record.Event.read()
                        val keyEvent = record.Event.KeyEvent
                        if (!keyEvent.bKeyDown) {
                            null
                        } else {
                            val control = keyEvent.dwControlKeyState
                            val shift = (control and SHIFT_PRESSED) != 0
                            val alt = (control and (LEFT_ALT_PRESSED or RIGHT_ALT_PRESSED)) != 0
                            val ctrl = (control and (LEFT_CTRL_PRESSED or RIGHT_CTRL_PRESSED)) != 0
                            val key = decodeKey(keyEvent, ctrl)
                            if (key == null) null else KeyEvent(key, shift, alt, ctrl)
                        }
                    }
                    WINDOW_BUFFER_SIZE_EVENT -> {
                        record.Event.setType(WINDOW_BUFFER_SIZE_RECORD::class.java)
                        record.Event.read()
                        val cols = record.Event.WindowBufferSizeEvent.dwSize.X.toInt()
                        val rows = record.Event.WindowBufferSizeEvent.dwSize.Y.toInt()
                        ResizeEvent(cols, rows)
                    }
                    else -> null
                }
                if (event != null) {
                    synchronized(queueLock) {
                        if (eventQueue.size >= 512) {
                            eventQueue.removeFirst()
                        }
                        eventQueue.addLast(event)
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "octodraw-win32-input"
            start()
        }
    }

    private fun decodeKey(keyEvent: KEY_EVENT_RECORD, ctrl: Boolean): String? {
        val ch = keyEvent.uChar
        if (ch.code != 0) {
            when (ch) {
                '\r', '\n' -> return "Enter"
                '\t' -> return "Tab"
                '\u0008', '\u007f' -> return "Backspace"
                '\u001b' -> return "Escape"
            }
            if (!Character.isISOControl(ch)) {
                return ch.toString()
            }
        }
        val vk = keyEvent.wVirtualKeyCode.toInt()
        if (vk == 0x10 || vk == 0x11 || vk == 0x12) return null
        if (ctrl && vk in 0x41..0x5A) {
            return vk.toChar().toString()
        }
        return when (vk) {
            0x08 -> "Backspace"
            0x09 -> "Tab"
            0x0D -> "Enter"
            0x1B -> "Escape"
            0x21 -> "PageUp"
            0x22 -> "PageDown"
            0x23 -> "End"
            0x24 -> "Home"
            0x25 -> "Left"
            0x26 -> "Up"
            0x27 -> "Right"
            0x28 -> "Down"
            0x2E -> "Delete"
            else -> when (vk) {
                in 0x30..0x39 -> (vk - 0x30).toString()
                in 0x41..0x5A -> vk.toChar().toString()
                else -> null
            }
        }
    }

    private fun ensureStdinHandle(k32: Kernel32): Pointer? {
        stdinHandle?.let { return it }
        var handle = k32.GetStdHandle(STD_INPUT_HANDLE)
        if (handle == null || k32.GetFileType(handle) != FILE_TYPE_CHAR) {
            handle = k32.CreateFileW(
                "CONIN$",
                GENERIC_READ or GENERIC_WRITE,
                FILE_SHARE_READ or FILE_SHARE_WRITE,
                null,
                OPEN_EXISTING,
                0,
                null
            )
        }
        stdinHandle = handle
        return handle
    }

    private fun ensureStdoutHandle(k32: Kernel32): Pointer? {
        stdoutHandle?.let { return it }
        val handle = k32.GetStdHandle(STD_OUTPUT_HANDLE)
        stdoutHandle = handle
        return handle
    }

    private fun ensureUtf8CodePage(k32: Kernel32) {
        if (savedInputCp == 0) savedInputCp = k32.GetConsoleCP()
        if (savedOutputCp == 0) savedOutputCp = k32.GetConsoleOutputCP()
        k32.SetConsoleCP(CP_UTF8)
        k32.SetConsoleOutputCP(CP_UTF8)
    }

    private fun loadReadConsoleInputExW(): Function? {
        return loadFunction("kernel32", "ReadConsoleInputExW")
            ?: loadFunction("kernelbase", "ReadConsoleInputExW")
    }

    private fun loadFunction(lib: String, name: String): Function? {
        return runCatching {
            val library = NativeLibrary.getInstance(lib)
            library.getFunction(name)
        }.getOrNull()
    }

    private interface Kernel32 : Library {
        fun GetStdHandle(nStdHandle: Int): Pointer?
        fun GetConsoleMode(hConsoleHandle: Pointer?, lpMode: IntByReference): Boolean
        fun SetConsoleMode(hConsoleHandle: Pointer?, dwMode: Int): Boolean
        fun GetConsoleScreenBufferInfo(hConsoleHandle: Pointer?, lpConsoleScreenBufferInfo: CONSOLE_SCREEN_BUFFER_INFO): Boolean
        fun ReadConsoleInputW(
            hConsoleInput: Pointer?,
            lpBuffer: INPUT_RECORD,
            nLength: Int,
            lpNumberOfEventsRead: IntByReference
        ): Boolean
        fun GetFileType(hFile: Pointer?): Int
        fun CreateFileW(
            lpFileName: String,
            dwDesiredAccess: Int,
            dwShareMode: Int,
            lpSecurityAttributes: Pointer?,
            dwCreationDisposition: Int,
            dwFlagsAndAttributes: Int,
            hTemplateFile: Pointer?
        ): Pointer?
        fun GetConsoleCP(): Int
        fun SetConsoleCP(wCodePageID: Int): Boolean
        fun GetConsoleOutputCP(): Int
        fun SetConsoleOutputCP(wCodePageID: Int): Boolean
    }

    @Suppress("unused")
    class COORD : com.sun.jna.Structure() {
        @JvmField var X: Short = 0
        @JvmField var Y: Short = 0
        override fun getFieldOrder(): List<String> = listOf("X", "Y")
    }

    @Suppress("unused")
    class SMALL_RECT : com.sun.jna.Structure() {
        @JvmField var Left: Short = 0
        @JvmField var Top: Short = 0
        @JvmField var Right: Short = 0
        @JvmField var Bottom: Short = 0
        override fun getFieldOrder(): List<String> = listOf("Left", "Top", "Right", "Bottom")
    }

    @Suppress("unused")
    class CONSOLE_SCREEN_BUFFER_INFO : com.sun.jna.Structure() {
        @JvmField var dwSize: COORD = COORD()
        @JvmField var dwCursorPosition: COORD = COORD()
        @JvmField var wAttributes: Short = 0
        @JvmField var srWindow: SMALL_RECT = SMALL_RECT()
        @JvmField var dwMaximumWindowSize: COORD = COORD()
        override fun getFieldOrder(): List<String> =
            listOf("dwSize", "dwCursorPosition", "wAttributes", "srWindow", "dwMaximumWindowSize")
    }

    @Suppress("unused")
    class KEY_EVENT_RECORD : com.sun.jna.Structure() {
        @JvmField var bKeyDown: Boolean = false
        @JvmField var wRepeatCount: Short = 0
        @JvmField var wVirtualKeyCode: Short = 0
        @JvmField var wVirtualScanCode: Short = 0
        @JvmField var uChar: Char = 0.toChar()
        @JvmField var dwControlKeyState: Int = 0
        override fun getFieldOrder(): List<String> =
            listOf("bKeyDown", "wRepeatCount", "wVirtualKeyCode", "wVirtualScanCode", "uChar", "dwControlKeyState")
    }

    @Suppress("unused")
    class WINDOW_BUFFER_SIZE_RECORD : com.sun.jna.Structure() {
        @JvmField var dwSize: COORD = COORD()
        override fun getFieldOrder(): List<String> = listOf("dwSize")
    }

    @Suppress("unused")
    class INPUT_RECORD : com.sun.jna.Structure() {
        @JvmField var EventType: Short = 0
        @JvmField var Event: EventUnion = EventUnion()
        override fun getFieldOrder(): List<String> = listOf("EventType", "Event")
    }

    @Suppress("unused")
    class EventUnion : com.sun.jna.Union() {
        @JvmField var KeyEvent: KEY_EVENT_RECORD = KEY_EVENT_RECORD()
        @JvmField var WindowBufferSizeEvent: WINDOW_BUFFER_SIZE_RECORD = WINDOW_BUFFER_SIZE_RECORD()
    }

    sealed interface ConsoleEvent
    data class KeyEvent(
        val key: String,
        val shift: Boolean,
        val alt: Boolean,
        val ctrl: Boolean
    ) : ConsoleEvent
    data class ResizeEvent(val cols: Int, val rows: Int) : ConsoleEvent
}
