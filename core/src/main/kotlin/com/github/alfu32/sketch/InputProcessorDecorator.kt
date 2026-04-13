package com.github.alfu32.sketch

import com.badlogic.gdx.InputProcessor

interface InputProcessorDecorator {
    fun wrap(inputProcessor: InputProcessor): InputProcessor
}

object PassthroughInputProcessorDecorator : InputProcessorDecorator {
    override fun wrap(inputProcessor: InputProcessor): InputProcessor = inputProcessor
}
