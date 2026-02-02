package com.github.alfu32.sketch.tui

sealed class InputEvent {
    data class Key(val keyCode: Int, val modifiers: Int) : InputEvent()
    data class Mouse(val x: Int, val y: Int, val button: Int) : InputEvent()
}

object InputKeys {
    const val TAB = -13
    const val ENTER = -1
    const val BACKSPACE = -2
    const val DELETE = -3
    const val UP = -4
    const val DOWN = -5
    const val LEFT = -6
    const val RIGHT = -7
    const val HOME = -8
    const val END = -9
    const val PAGE_UP = -10
    const val PAGE_DOWN = -11
    const val ESC = -12
}

object InputModifiers {
    const val CTRL = 1
    const val ALT = 2
    const val SHIFT = 4
}
