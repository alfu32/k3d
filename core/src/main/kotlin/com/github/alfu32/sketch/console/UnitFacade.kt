package com.github.alfu32.sketch.console

import com.github.alfu32.sketch.model.ModelUnit

class UnitFacade(
    private val getUnit: () -> ModelUnit,
    private val setUnit: (String, Float) -> Unit
) {
    fun name(): String = getUnit().name

    fun size(): Float = getUnit().size

    fun set(name: String, size: Float) {
        setUnit(name, size)
    }

    fun setName(name: String) {
        setUnit(name, getUnit().size)
    }

    fun setSize(size: Float) {
        setUnit(getUnit().name, size)
    }

    override fun toString(): String = "ModelUnit(name=${getUnit().name}, size=${getUnit().size})"
}
