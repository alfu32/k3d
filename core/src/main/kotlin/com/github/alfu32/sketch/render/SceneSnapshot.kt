package com.github.alfu32.sketch.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray

data class RenderTriangle(
    val a: Vector3,
    val b: Vector3,
    val c: Vector3,
    val normal: Vector3,
    val albedo: Color
)

data class RenderPointLight(
    val position: Vector3,
    val color: Color,
    val intensity: Float
)

sealed interface RenderCameraSnapshot {
    val width: Int
    val height: Int
    fun rayForPixel(x: Float, y: Float): Ray

    companion object {
        fun from(camera: com.badlogic.gdx.graphics.Camera, width: Int, height: Int): RenderCameraSnapshot {
            return when (camera) {
                is PerspectiveCamera -> Perspective(
                    width = width,
                    height = height,
                    position = Vector3(camera.position),
                    direction = Vector3(camera.direction),
                    up = Vector3(camera.up),
                    near = camera.near,
                    far = camera.far,
                    fieldOfViewDeg = camera.fieldOfView
                )
                is OrthographicCamera -> Ortho(
                    width = width,
                    height = height,
                    position = Vector3(camera.position),
                    direction = Vector3(camera.direction),
                    up = Vector3(camera.up),
                    near = camera.near,
                    far = camera.far,
                    zoom = camera.zoom
                )
                else -> error("Unsupported camera type: ${camera.javaClass.simpleName}")
            }
        }
    }

    data class Perspective(
        override val width: Int,
        override val height: Int,
        val position: Vector3,
        val direction: Vector3,
        val up: Vector3,
        val near: Float,
        val far: Float,
        val fieldOfViewDeg: Float
    ) : RenderCameraSnapshot {
        private val camera = PerspectiveCamera(fieldOfViewDeg, width.toFloat(), height.toFloat()).apply {
            this.position.set(position)
            this.direction.set(direction).nor()
            this.up.set(up).nor()
            this.near = near
            this.far = far
            update()
        }

        override fun rayForPixel(x: Float, y: Float): Ray = camera.getPickRay(x, y)
    }

    data class Ortho(
        override val width: Int,
        override val height: Int,
        val position: Vector3,
        val direction: Vector3,
        val up: Vector3,
        val near: Float,
        val far: Float,
        val zoom: Float
    ) : RenderCameraSnapshot {
        private val camera = OrthographicCamera(width.toFloat(), height.toFloat()).apply {
            this.position.set(position)
            this.direction.set(direction).nor()
            this.up.set(up).nor()
            this.near = near
            this.far = far
            this.zoom = zoom
            update()
        }

        override fun rayForPixel(x: Float, y: Float): Ray = camera.getPickRay(x, y)
    }
}

data class SceneSnapshot(
    val camera: RenderCameraSnapshot,
    val triangles: List<RenderTriangle>,
    val lights: List<RenderPointLight>,
    val skyColor: Color
)
