package com.github.alfu32.sketch.android

import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.math.Vector2
import com.github.alfu32.sketch.InputProcessorDecorator
import com.github.alfu32.sketch.TouchGestureButtons
import kotlin.math.roundToInt

class AndroidTouchInputDecorator : InputProcessorDecorator {
    override fun wrap(inputProcessor: InputProcessor): InputProcessor {
        return AndroidTouchInputProcessor(inputProcessor)
    }
}

private class AndroidTouchInputProcessor(
    private val delegate: InputProcessor
) : InputAdapter() {
    private enum class GestureMode(val syntheticButton: Int?) {
        NONE(null),
        LEFT(Input.Buttons.LEFT),
        TWO_FINGER_VERTICAL(TouchGestureButtons.TWO_FINGER_VERTICAL_DRAG),
        ORBIT(TouchGestureButtons.THREE_FINGER_ORBIT),
        PAN(TouchGestureButtons.FOUR_FINGER_PAN)
    }

    private data class PointerState(val position: Vector2 = Vector2())

    private val pointers = LinkedHashMap<Int, PointerState>()
    private var activeMode = GestureMode.NONE
    private var pendingMode = GestureMode.NONE
    private var pendingSinceNanos = 0L
    private var lastGestureX = 0
    private var lastGestureY = 0
    private var maxPointerCountInGesture = 0
    private var tapCandidatePointer = -1
    private var tapCandidateX = 0f
    private var tapCandidateY = 0f

    private val debounceNanos = 140_000_000L
    private val tapSlopSquared = 14f * 14f

    override fun keyDown(keycode: Int): Boolean = delegate.keyDown(keycode)

    override fun keyUp(keycode: Int): Boolean = delegate.keyUp(keycode)

    override fun keyTyped(character: Char): Boolean = delegate.keyTyped(character)

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean = delegate.mouseMoved(screenX, screenY)

    override fun scrolled(amountX: Float, amountY: Float): Boolean = delegate.scrolled(amountX, amountY)

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        pointers.getOrPut(pointer) { PointerState() }.position.set(screenX.toFloat(), screenY.toFloat())
        maxPointerCountInGesture = maxOf(maxPointerCountInGesture, pointers.size)
        if (pointers.size == 1) {
            tapCandidatePointer = pointer
            tapCandidateX = screenX.toFloat()
            tapCandidateY = screenY.toFloat()
        } else {
            clearTapCandidate()
        }
        refreshGestureState(screenX, screenY)
        handleActiveGestureMotion()
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        pointers[pointer]?.position?.set(screenX.toFloat(), screenY.toFloat())
        invalidateTapCandidateIfMoved(pointer, screenX.toFloat(), screenY.toFloat())
        refreshGestureState(screenX, screenY)
        handleActiveGestureMotion()
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        pointers[pointer]?.position?.set(screenX.toFloat(), screenY.toFloat())
        val shouldEmitTap = activeMode == GestureMode.NONE &&
            pointers.size == 1 &&
            pointer == tapCandidatePointer &&
            distanceSquared(screenX.toFloat(), screenY.toFloat(), tapCandidateX, tapCandidateY) <= tapSlopSquared
        val modeBeforeRemoval = if (activeMode != GestureMode.NONE) activeMode else pendingMode
        pointers.remove(pointer)
        if (pointers.size < minimumPointerCountForMode(modeBeforeRemoval)) {
            finishActiveGesture(screenX, screenY)
            pendingMode = GestureMode.NONE
            pendingSinceNanos = 0L
            maxPointerCountInGesture = pointers.size
        }
        refreshGestureState(screenX, screenY)
        if (shouldEmitTap) {
            delegate.touchDown(screenX, screenY, 0, Input.Buttons.LEFT)
            delegate.touchUp(screenX, screenY, 0, Input.Buttons.LEFT)
        }
        if (pointers.isEmpty()) {
            maxPointerCountInGesture = 0
        }
        clearTapCandidate()
        return true
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        pointers.remove(pointer)
        finishActiveGesture(screenX, screenY)
        pendingMode = GestureMode.NONE
        clearTapCandidate()
        maxPointerCountInGesture = pointers.size
        return true
    }

    private fun refreshGestureState(screenX: Int, screenY: Int) {
        if (pointers.isEmpty()) {
            maxPointerCountInGesture = 0
        } else {
            maxPointerCountInGesture = maxOf(maxPointerCountInGesture, pointers.size)
        }
        val desiredMode = desiredModeForPointerCount(maxPointerCountInGesture)
        val now = System.nanoTime()
        if (desiredMode != activeMode && activeMode != GestureMode.NONE) {
            val previousMode = activeMode
            finishActiveGesture(
                screenX,
                screenY,
                preferLastAnchor = activeMode == GestureMode.LEFT && pointers.size > 1
            )
            if (minimumPointerCountForMode(desiredMode) > minimumPointerCountForMode(previousMode)) {
                activateGesture(desiredMode, screenX, screenY)
                pendingMode = GestureMode.NONE
                pendingSinceNanos = 0L
                return
            }
        }
        if (desiredMode == GestureMode.NONE) {
            pendingMode = GestureMode.NONE
            pendingSinceNanos = 0L
            return
        }
        if (desiredMode == activeMode) {
            pendingMode = GestureMode.NONE
            pendingSinceNanos = 0L
            return
        }
        if (desiredMode != pendingMode) {
            pendingMode = desiredMode
            pendingSinceNanos = now
            return
        }
        if (now - pendingSinceNanos >= debounceNanos) {
            activateGesture(desiredMode, screenX, screenY)
            pendingMode = GestureMode.NONE
            pendingSinceNanos = 0L
        }
    }

    private fun activateGesture(mode: GestureMode, fallbackX: Int, fallbackY: Int) {
        activeMode = mode
        val anchor = currentAnchor(fallbackX, fallbackY)
        lastGestureX = anchor.x
        lastGestureY = anchor.y
        mode.syntheticButton?.let { button -> delegate.touchDown(anchor.x, anchor.y, 0, button) }
    }

    private fun finishActiveGesture(fallbackX: Int, fallbackY: Int, preferLastAnchor: Boolean = false) {
        if (activeMode == GestureMode.NONE) {
            return
        }
        val anchor = if (preferLastAnchor) {
            Anchor(lastGestureX, lastGestureY)
        } else {
            currentAnchor(fallbackX, fallbackY)
        }
        activeMode.syntheticButton?.let { button -> delegate.touchUp(anchor.x, anchor.y, 0, button) }
        activeMode = GestureMode.NONE
    }

    private fun handleActiveGestureMotion() {
        when (activeMode) {
            GestureMode.LEFT, GestureMode.TWO_FINGER_VERTICAL, GestureMode.ORBIT, GestureMode.PAN -> {
                val anchor = currentAnchor(lastGestureX, lastGestureY)
                if (anchor.x == lastGestureX && anchor.y == lastGestureY) {
                    return
                }
                lastGestureX = anchor.x
                lastGestureY = anchor.y
                delegate.touchDragged(anchor.x, anchor.y, 0)
            }
            GestureMode.NONE -> Unit
        }
    }

    private fun currentAnchor(fallbackX: Int, fallbackY: Int): Anchor {
        if (pointers.isEmpty()) {
            return Anchor(fallbackX, fallbackY)
        }
        val averageX = pointers.values.sumOf { it.position.x.toDouble() }.toFloat() / pointers.size.toFloat()
        val averageY = pointers.values.sumOf { it.position.y.toDouble() }.toFloat() / pointers.size.toFloat()
        return Anchor(averageX.roundToInt(), averageY.roundToInt())
    }

    private fun desiredModeForPointerCount(pointerCount: Int): GestureMode {
        return when (pointerCount) {
            0 -> GestureMode.NONE
            1 -> GestureMode.LEFT
            2 -> GestureMode.TWO_FINGER_VERTICAL
            3 -> GestureMode.ORBIT
            else -> GestureMode.PAN
        }
    }

    private fun minimumPointerCountForMode(mode: GestureMode): Int {
        return when (mode) {
            GestureMode.NONE -> 0
            GestureMode.LEFT -> 1
            GestureMode.TWO_FINGER_VERTICAL -> 2
            GestureMode.ORBIT -> 3
            GestureMode.PAN -> 4
        }
    }

    private fun invalidateTapCandidateIfMoved(pointer: Int, x: Float, y: Float) {
        if (pointer != tapCandidatePointer) {
            clearTapCandidate()
            return
        }
        if (distanceSquared(x, y, tapCandidateX, tapCandidateY) > tapSlopSquared) {
            clearTapCandidate()
        }
    }

    private fun clearTapCandidate() {
        tapCandidatePointer = -1
    }

    private fun distanceSquared(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return dx * dx + dy * dy
    }

    private data class Anchor(val x: Int, val y: Int)
}
