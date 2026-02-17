package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement

class HvacPlumbingTool(
    private val scene: GroupScene,
    private val settings: HvacSettings,
    private val onSelectTool: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.HVAC_PLUMBING
    override val message: String = "HVAC plumbing: click to add path points. Enter finishes. Esc cancels."

    private val pointsWorld = mutableListOf<Vector3>()
    private val hoverWorld = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        clear()
        status.message = message
    }

    override fun onExit(status: StatusModel) {
        clear()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clear()
        status.message = "HVAC plumbing canceled."
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        pointsWorld.add(Vector3(world))
        return true
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (valid && world != null) {
            hoverWorld.set(world)
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ENTER -> {
                finalizeRun(status)
                true
            }
            Input.Keys.BACKSPACE -> {
                if (pointsWorld.isNotEmpty()) {
                    pointsWorld.removeAt(pointsWorld.lastIndex)
                    status.message = "Removed last point."
                    true
                } else {
                    false
                }
            }
            Input.Keys.ESCAPE -> {
                clear()
                status.message = "HVAC plumbing canceled."
                onSelectTool()
                true
            }
            else -> false
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        if (pointsWorld.isEmpty() || !hasHover) {
            return null
        }
        return ToolMeasurement(
            startWorld = Vector3(pointsWorld.last()),
            endWorld = Vector3(hoverWorld),
            lineColor = Color(0.55f, 0.8f, 0.95f, 1f)
        )
    }

    override fun render(renderer: ShapeRenderer) {
        if (pointsWorld.isEmpty()) {
            return
        }
        renderer.color = Color(0.55f, 0.8f, 0.95f, 1f)
        for (i in 0 until pointsWorld.lastIndex) {
            renderer.line(pointsWorld[i], pointsWorld[i + 1])
        }
        if (hasHover) {
            renderer.line(pointsWorld.last(), hoverWorld)
        }
    }

    private fun finalizeRun(status: StatusModel) {
        if (pointsWorld.size < 2) {
            clear()
            status.message = "HVAC plumbing requires at least 2 points."
            return
        }
        val created = scene.addHvacPlumbingRun(
            pathWorld = pointsWorld,
            diameter = settings.plumbingDiameter,
            sides = settings.plumbingSides,
            color = settings.plumbingColor
        )
        clear()
        status.message = if (created) "HVAC plumbing created." else "HVAC plumbing creation failed."
    }

    private fun clear() {
        pointsWorld.clear()
        hasHover = false
    }
}

class HvacVentilationTool(
    private val scene: GroupScene,
    private val settings: HvacSettings,
    private val onSelectTool: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.HVAC_VENTILATION
    override val message: String = "HVAC ventilation: pick start, end, then binormal reference."

    private var startWorld: Vector3? = null
    private var endWorld: Vector3? = null
    private val hoverWorld = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        clear()
        status.message = message
    }

    override fun onExit(status: StatusModel) {
        clear()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clear()
        status.message = "HVAC ventilation canceled."
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        when {
            startWorld == null -> {
                startWorld = Vector3(world)
                status.message = "Pick ventilation end point."
            }
            endWorld == null -> {
                val start = startWorld ?: return true
                if (start.dst2(world) <= 1e-6f) {
                    status.message = "Ventilation end point is too close to start."
                } else {
                    endWorld = Vector3(world)
                    status.message = "Pick binormal reference point."
                }
            }
            else -> {
                val start = startWorld ?: return true
                val end = endWorld ?: return true
                val created = scene.addHvacVentilationDuct(
                    startWorld = start,
                    endWorld = end,
                    binormalRefWorld = Vector3(world),
                    autoJoinEnabled = settings.ventilationAutoJoin,
                    width = settings.ventilationWidth,
                    height = settings.ventilationHeight,
                    humpHalfSpan = settings.ventilationHumpHalfSpan,
                    humpClearance = settings.ventilationHumpClearance,
                    color = settings.ventilationColor
                )
                clear()
                status.message = if (created) "HVAC ventilation created." else "HVAC ventilation creation failed."
            }
        }
        return true
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (valid && world != null) {
            hoverWorld.set(world)
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ESCAPE -> {
                clear()
                status.message = "HVAC ventilation canceled."
                onSelectTool()
                true
            }
            Input.Keys.ENTER -> {
                if (startWorld != null && endWorld == null) {
                    status.message = "Pick ventilation end point."
                    true
                } else if (startWorld != null && endWorld != null) {
                    status.message = "Pick binormal reference point."
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val start = startWorld ?: return null
        if (!hasHover) {
            return null
        }
        val end = endWorld
        return ToolMeasurement(
            startWorld = if (end == null) Vector3(start) else Vector3(end),
            endWorld = Vector3(hoverWorld),
            lineColor = Color(0.8f, 0.8f, 0.9f, 1f)
        )
    }

    override fun render(renderer: ShapeRenderer) {
        val start = startWorld ?: return
        renderer.color = Color(0.8f, 0.8f, 0.9f, 1f)
        val end = endWorld
        if (end == null) {
            if (hasHover) {
                renderer.line(start, hoverWorld)
            }
            return
        }
        renderer.line(start, end)
        if (hasHover) {
            renderer.color = Color(0.6f, 0.95f, 0.6f, 1f)
            renderer.line(end, hoverWorld)
        }
    }

    private fun clear() {
        startWorld = null
        endWorld = null
        hasHover = false
    }
}
