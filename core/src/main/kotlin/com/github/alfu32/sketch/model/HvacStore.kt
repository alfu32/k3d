package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import java.util.UUID

class HvacStore {
    enum class ElementKind {
        PLUMBING,
        VENTILATION
    }

    data class ElementSelection(val kind: ElementKind, val id: String)

    enum class VentilationControlKind {
        START,
        END,
        BINORMAL_REF
    }

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
        var autoJoinEnabled: Boolean,
        var width: Float,
        var height: Float,
        var humpHalfSpan: Float,
        var humpClearance: Float,
        var color: Color
    )

    private val plumbingRuns = mutableListOf<PlumbingRun>()
    private val ventilationDucts = mutableListOf<VentilationDuct>()
    private var plumbingNameCounter = 1
    private var ventilationNameCounter = 1
    private var selectedElement: ElementSelection? = null
    private val selectedElements = linkedSetOf<ElementSelection>()

    fun allPlumbingRuns(): List<PlumbingRun> = plumbingRuns

    fun allVentilationDucts(): List<VentilationDuct> = ventilationDucts

    fun selectedElement(): ElementSelection? = selectedElement

    fun selectedElements(): Set<ElementSelection> = selectedElements

    fun clearSelectedElement() {
        selectedElement = null
        selectedElements.clear()
    }

    fun plumbingById(id: String): PlumbingRun? = plumbingRuns.firstOrNull { it.id == id }

    fun ventilationById(id: String): VentilationDuct? = ventilationDucts.firstOrNull { it.id == id }

    fun setSelectedElement(kind: ElementKind, id: String): Boolean {
        val exists = when (kind) {
            ElementKind.PLUMBING -> plumbingRuns.any { it.id == id }
            ElementKind.VENTILATION -> ventilationDucts.any { it.id == id }
        }
        if (!exists) {
            clearSelectedElement()
            return false
        }
        val selection = ElementSelection(kind, id)
        selectedElement = selection
        selectedElements.clear()
        selectedElements.add(selection)
        return true
    }

    fun addSelectedElement(kind: ElementKind, id: String): Boolean {
        val exists = when (kind) {
            ElementKind.PLUMBING -> plumbingRuns.any { it.id == id }
            ElementKind.VENTILATION -> ventilationDucts.any { it.id == id }
        }
        if (!exists) {
            return false
        }
        val selection = ElementSelection(kind, id)
        selectedElement = selection
        return selectedElements.add(selection)
    }

    fun removeSelectedElement(kind: ElementKind, id: String): Boolean {
        val selection = ElementSelection(kind, id)
        val removed = selectedElements.remove(selection)
        if (selectedElement == selection) {
            selectedElement = selectedElements.lastOrNull()
        }
        return removed
    }

    fun isSelectedElement(kind: ElementKind, id: String): Boolean {
        return selectedElements.contains(ElementSelection(kind, id))
    }

    fun deleteSelectedElements(): Int {
        val targets = selectedElements.toList()
        if (targets.isEmpty()) {
            return 0
        }
        var removed = 0
        targets.forEach { selection ->
            val deleted = when (selection.kind) {
                ElementKind.PLUMBING -> plumbingRuns.removeIf { it.id == selection.id }
                ElementKind.VENTILATION -> ventilationDucts.removeIf { it.id == selection.id }
            }
            if (deleted) {
                removed++
            }
        }
        clearSelectedElement()
        return removed
    }

    fun updatePlumbingPathPoint(id: String, pointIndex: Int, world: Vector3): Boolean {
        val run = plumbingById(id) ?: return false
        if (pointIndex !in run.path.indices) {
            return false
        }
        val updatedPath = run.path.map { Vector3(it) }.toMutableList()
        updatedPath[pointIndex] = Vector3(world)
        val cleaned = deduplicatePath(updatedPath)
        if (cleaned.size < 2) {
            return false
        }
        run.path = cleaned.toMutableList()
        return true
    }

    fun updateVentilationControlPoint(id: String, kind: VentilationControlKind, world: Vector3): Boolean {
        val duct = ventilationById(id) ?: return false
        when (kind) {
            VentilationControlKind.START -> {
                if (Vector3(world).dst2(duct.end) <= 1e-6f) {
                    return false
                }
                duct.start.set(world)
            }
            VentilationControlKind.END -> {
                if (Vector3(world).dst2(duct.start) <= 1e-6f) {
                    return false
                }
                duct.end.set(world)
            }
            VentilationControlKind.BINORMAL_REF -> duct.binormalRef.set(world)
        }
        return true
    }

    fun updatePlumbingName(id: String, name: String): Boolean {
        val run = plumbingById(id) ?: return false
        run.name = nextName(name, "PLUMBING_") { proposed ->
            plumbingRuns.none { it.id != id && it.name == proposed }
        }
        return true
    }

    fun updateVentilationName(id: String, name: String): Boolean {
        val duct = ventilationById(id) ?: return false
        duct.name = nextName(name, "VENT_") { proposed ->
            ventilationDucts.none { it.id != id && it.name == proposed }
        }
        return true
    }

    fun addPlumbingRun(
        path: List<Vector3>,
        diameter: Float,
        sides: Int,
        color: Color,
        name: String = "",
        id: String = UUID.randomUUID().toString()
    ): PlumbingRun {
        val cleanedPath = deduplicatePath(path.map { Vector3(it) })
        val run = PlumbingRun(
            id = id,
            name = nextName(name, "PLUMBING_") { proposed -> plumbingRuns.none { it.name == proposed } },
            path = if (cleanedPath.size >= 2) cleanedPath.toMutableList() else path.map { Vector3(it) }.toMutableList(),
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
        autoJoinEnabled: Boolean,
        width: Float,
        height: Float,
        humpHalfSpan: Float,
        humpClearance: Float,
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
            autoJoinEnabled = autoJoinEnabled,
            width = width,
            height = height,
            humpHalfSpan = humpHalfSpan,
            humpClearance = humpClearance,
            color = Color(color)
        )
        ventilationDucts.add(duct)
        return duct
    }

    fun clear() {
        clearSelectedElement()
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

    private fun deduplicatePath(input: List<Vector3>): List<Vector3> {
        if (input.isEmpty()) {
            return emptyList()
        }
        val out = mutableListOf<Vector3>()
        input.forEach { point ->
            if (out.isEmpty() || out.last().dst2(point) > 1e-6f) {
                out.add(Vector3(point))
            }
        }
        return out
    }
}
