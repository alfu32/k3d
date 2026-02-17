package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import java.util.UUID

class HvacStore {
    data class PlumbingRun(
        var id: String,
        var name: String,
        var path: MutableList<Vector3>,
        var diameter: Float,
        var sides: Int,
        var color: Color
    )

    data class VentilationDuct(
        var id: String,
        var name: String,
        var start: Vector3,
        var end: Vector3,
        var binormalRef: Vector3,
        var width: Float,
        var height: Float,
        var color: Color
    )

    private val plumbingRuns = mutableListOf<PlumbingRun>()
    private val ventilationDucts = mutableListOf<VentilationDuct>()
    private var plumbingNameCounter = 1
    private var ventilationNameCounter = 1

    fun allPlumbingRuns(): List<PlumbingRun> = plumbingRuns

    fun allVentilationDucts(): List<VentilationDuct> = ventilationDucts

    fun addPlumbingRun(
        path: List<Vector3>,
        diameter: Float,
        sides: Int,
        color: Color,
        name: String = "",
        id: String = UUID.randomUUID().toString()
    ): PlumbingRun {
        val run = PlumbingRun(
            id = id,
            name = nextName(name, "PLUMBING_") { proposed -> plumbingRuns.none { it.name == proposed } },
            path = path.map { Vector3(it) }.toMutableList(),
            diameter = diameter,
            sides = sides,
            color = Color(color)
        )
        plumbingRuns.add(run)
        return run
    }

    fun addVentilationDuct(
        start: Vector3,
        end: Vector3,
        binormalRef: Vector3,
        width: Float,
        height: Float,
        color: Color,
        name: String = "",
        id: String = UUID.randomUUID().toString()
    ): VentilationDuct {
        val duct = VentilationDuct(
            id = id,
            name = nextName(name, "VENT_") { proposed -> ventilationDucts.none { it.name == proposed } },
            start = Vector3(start),
            end = Vector3(end),
            binormalRef = Vector3(binormalRef),
            width = width,
            height = height,
            color = Color(color)
        )
        ventilationDucts.add(duct)
        return duct
    }

    fun clear() {
        plumbingRuns.clear()
        ventilationDucts.clear()
        plumbingNameCounter = 1
        ventilationNameCounter = 1
    }

    private fun nextName(candidate: String, prefix: String, isAvailable: (String) -> Boolean): String {
        val trimmed = candidate.trim()
        if (trimmed.isNotEmpty()) {
            return trimmed
        }
        var index = when (prefix) {
            "PLUMBING_" -> plumbingNameCounter
            "VENT_" -> ventilationNameCounter
            else -> 1
        }
        var generated = "$prefix$index"
        while (!isAvailable(generated)) {
            index++
            generated = "$prefix$index"
        }
        when (prefix) {
            "PLUMBING_" -> plumbingNameCounter = index + 1
            "VENT_" -> ventilationNameCounter = index + 1
        }
        return generated
    }
}
