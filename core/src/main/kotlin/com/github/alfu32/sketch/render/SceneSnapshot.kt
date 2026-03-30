package com.github.alfu32.sketch.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import kotlin.math.tan

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
                    fieldOfViewDeg = camera.fieldOfView
                )
                is OrthographicCamera -> Ortho(
                    width = width,
                    height = height,
                    position = Vector3(camera.position),
                    direction = Vector3(camera.direction),
                    up = Vector3(camera.up),
                    worldViewportWidth = camera.viewportWidth,
                    worldViewportHeight = camera.viewportHeight,
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
        val fieldOfViewDeg: Float
    ) : RenderCameraSnapshot {
        private val forward = Vector3(direction).nor()
        private val right = Vector3(forward).crs(up).nor()
        private val correctedUp = Vector3(right).crs(forward).nor()
        private val tanHalfFov = tan(Math.toRadians((fieldOfViewDeg * 0.5f).toDouble())).toFloat()

        override fun rayForPixel(x: Float, y: Float): Ray {
            val sx = ((x / width.toFloat()) * 2f - 1f).coerceIn(-1f, 1f)
            val sy = (1f - (y / height.toFloat()) * 2f).coerceIn(-1f, 1f)
            val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
            val dir = Vector3(forward)
                .mulAdd(right, sx * aspect * tanHalfFov)
                .mulAdd(correctedUp, sy * tanHalfFov)
                .nor()
            return Ray(Vector3(position), dir)
        }
    }

    data class Ortho(
        override val width: Int,
        override val height: Int,
        val position: Vector3,
        val direction: Vector3,
        val up: Vector3,
        val worldViewportWidth: Float,
        val worldViewportHeight: Float,
        val zoom: Float
    ) : RenderCameraSnapshot {
        private val forward = Vector3(direction).nor()
        private val right = Vector3(forward).crs(up).nor()
        private val correctedUp = Vector3(right).crs(forward).nor()

        override fun rayForPixel(x: Float, y: Float): Ray {
            val sx = ((x / width.toFloat()) * 2f - 1f).coerceIn(-1f, 1f)
            val sy = (1f - (y / height.toFloat()) * 2f).coerceIn(-1f, 1f)
            val halfWidth = worldViewportWidth * zoom * 0.5f
            val halfHeight = worldViewportHeight * zoom * 0.5f
            val origin = Vector3(position)
                .mulAdd(right, sx * halfWidth)
                .mulAdd(correctedUp, sy * halfHeight)
            return Ray(origin, Vector3(forward))
        }
    }
}

data class SceneSnapshot(
    val camera: RenderCameraSnapshot,
    val triangles: List<RenderTriangle>,
    val lights: List<RenderPointLight>,
    val skyColor: Color
)
