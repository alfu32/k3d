package com.github.alfu32.sketch

import com.badlogic.gdx.InputProcessor

interface InputProcessorDecorator {
    fun wrap(inputProcessor: InputProcessor): InputProcessor
}

object PassthroughInputProcessorDecorator : InputProcessorDecorator {
    override fun wrap(inputProcessor: InputProcessor): InputProcessor = inputProcessor
}

object TouchGestureButtons {
    const val TWO_FINGER_VERTICAL_DRAG = 10_001
    const val THREE_FINGER_ORBIT = 10_002
    const val FOUR_FINGER_PAN = 10_003
}
