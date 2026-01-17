package com.github.alfu32.sketch.plugin

import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.github.alfu32.sketch.Main
import com.github.alfu32.sketch.ui.SketchUiOverlay

interface Plugin {
    fun load(application: Main)

    fun create(application: Main)
    fun inputProcessor(): InputProcessor

    fun update(application: Main)
    fun draw(modelBatch: ModelBatch)
    fun draw(shapeRenderer: ShapeRenderer)
    fun draw(uiOverlay: SketchUiOverlay)

    fun save(application: Main)
    fun close(application: Main)

}
