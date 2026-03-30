package com.github.alfu32.sketch.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldListener
import com.badlogic.gdx.utils.Pools
import com.badlogic.gdx.utils.TimeUtils
import com.kotcrab.vis.ui.widget.VisTextField

class AppTextField(text: String = "") : VisTextField(text) {
    companion object {
        private const val DEFAULT_DEBOUNCE_MS = 1_600L
        private const val INDICATOR_GAP = 6f
    }

    private val deferredChangeListeners = mutableListOf<ChangeListener>()
    private var delegatedTextFieldListener: TextFieldListener? = null
    private var dirty = false
    private var lastEditTimeMs = 0L
    private var lastCommittedText = text

    init {
        super.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                markDirty()
            }
        })
        super.setTextFieldListener { field, character ->
            if (character == '\r' || character == '\n') {
                flushPendingChange()
            }
            delegatedTextFieldListener?.keyTyped(field, character)
        }
    }

    override fun addListener(listener: EventListener): Boolean {
        if (listener is ChangeListener) {
            if (!deferredChangeListeners.contains(listener)) {
                deferredChangeListeners += listener
                return true
            }
            return false
        }
        return super.addListener(listener)
    }

    override fun removeListener(listener: EventListener): Boolean {
        if (listener is ChangeListener) {
            return deferredChangeListeners.remove(listener)
        }
        return super.removeListener(listener)
    }

    override fun setTextFieldListener(listener: TextFieldListener?) {
        delegatedTextFieldListener = listener
    }

    override fun setText(str: String?) {
        super.setText(str)
        resetCommittedState()
    }

    override fun act(delta: Float) {
        super.act(delta)
        if (dirty && TimeUtils.timeSinceMillis(lastEditTimeMs) >= DEFAULT_DEBOUNCE_MS) {
            flushPendingChange()
        }
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (!dirty) {
            return
        }
        val currentStyle = style
        val background = when {
            isDisabled && currentStyle.disabledBackground != null -> currentStyle.disabledBackground
            hasKeyboardFocus() && currentStyle.focusedBackground != null -> currentStyle.focusedBackground
            else -> currentStyle.background
        }
        val font = currentStyle.font
        val indicatorText = "."
        val color = getColor()
        val fontColor = when {
            isDisabled && currentStyle.disabledFontColor != null -> currentStyle.disabledFontColor
            hasKeyboardFocus() && currentStyle.focusedFontColor != null -> currentStyle.focusedFontColor
            else -> currentStyle.fontColor
        }
        val layout = com.badlogic.gdx.graphics.g2d.GlyphLayout(font, indicatorText)
        val x = x + width - (background?.rightWidth ?: 0f) - layout.width - INDICATOR_GAP
        val yOffset = if (font.isFlipped) -textHeight else 0f
        val y = y + getTextY(font, background) + yOffset
        val previousColor = font.color.cpy()
        font.setColor(fontColor.r, fontColor.g, fontColor.b, fontColor.a * color.a * parentAlpha)
        font.draw(batch, indicatorText, x, y)
        font.color = previousColor
    }

    fun flushPendingChange() {
        if (!dirty) {
            return
        }
        dirty = false
        Gdx.graphics.requestRendering()
        if (text == lastCommittedText) {
            return
        }
        dispatchCommittedChange()
        lastCommittedText = text
    }

    private fun markDirty() {
        dirty = true
        lastEditTimeMs = TimeUtils.millis()
        Gdx.graphics.requestRendering()
    }

    private fun resetCommittedState() {
        dirty = false
        lastEditTimeMs = 0L
        lastCommittedText = text
        Gdx.graphics.requestRendering()
    }

    private fun dispatchCommittedChange() {
        if (deferredChangeListeners.isEmpty()) {
            return
        }
        val event = Pools.obtain(ChangeListener.ChangeEvent::class.java)
        try {
            event.stage = stage
            event.target = this
            event.listenerActor = this
            for (listener in deferredChangeListeners.toList()) {
                listener.handle(event)
                if (event.isStopped) {
                    break
                }
            }
        } finally {
            Pools.free(event)
        }
    }
}
