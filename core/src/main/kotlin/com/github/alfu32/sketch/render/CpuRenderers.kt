package com.github.alfu32.sketch.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import kotlin.math.PI
import kotlin.math.max

interface CpuRenderer {
    fun renderTile(snapshot: SceneSnapshot, tile: RenderTile, buffer: RenderBuffer, seed: Long)
}

private data class Hit(
    val triangle: RenderTriangle,
    val point: Vector3,
    val normal: Vector3,
    val t: Float
)

class CpuRayTracer : CpuRenderer {
    override fun renderTile(snapshot: SceneSnapshot, tile: RenderTile, buffer: RenderBuffer, seed: Long) {
        val sampleX = tile.x + tile.width * 0.5f
        val sampleY = tile.y + tile.height * 0.5f
        val ray = snapshot.camera.rayForPixel(sampleX, sampleY)
        val color = shadeDirect(snapshot, ray)
        buffer.setBlock(tile.x, tile.y, tile.width, tile.height, color)
    }

    protected fun shadeDirect(snapshot: SceneSnapshot, ray: Ray): Color {
        val hit = nearestHit(snapshot.triangles, ray) ?: return background(snapshot.skyColor, ray.direction)
        val shaded = Vector3(hit.triangle.albedo.r, hit.triangle.albedo.g, hit.triangle.albedo.b).scl(0.12f)
        snapshot.lights.forEach { light ->
            val toLight = Vector3(light.position).sub(hit.point)
            val dist2 = toLight.len2().coerceAtLeast(1e-4f)
            val dist = kotlin.math.sqrt(dist2)
            toLight.scl(1f / dist)
            val ndotl = max(hit.normal.dot(toLight), 0f)
            if (ndotl <= 0f) {
                return@forEach
            }
            val shadowOrigin = Vector3(hit.point).mulAdd(hit.normal, 0.01f)
            val shadowRay = Ray(shadowOrigin, toLight)
            val shadowHit = nearestHit(snapshot.triangles, shadowRay)
            if (shadowHit != null && shadowHit.t < dist - 0.02f) {
                return@forEach
            }
            val attenuation = light.intensity / dist2
            shaded.x += hit.triangle.albedo.r * light.color.r * attenuation * ndotl
            shaded.y += hit.triangle.albedo.g * light.color.g * attenuation * ndotl
            shaded.z += hit.triangle.albedo.b * light.color.b * attenuation * ndotl
        }
        return Color(
            shaded.x.coerceIn(0f, 1f),
            shaded.y.coerceIn(0f, 1f),
            shaded.z.coerceIn(0f, 1f),
            1f
        )
    }
}

class CpuPathTracer(
    private val maxDepth: Int = 2
) : CpuRenderer {
    override fun renderTile(snapshot: SceneSnapshot, tile: RenderTile, buffer: RenderBuffer, seed: Long) {
        val sampleX = tile.x + tile.width * 0.5f
        val sampleY = tile.y + tile.height * 0.5f
        val ray = snapshot.camera.rayForPixel(sampleX, sampleY)
        val random = RenderRng(seed)
        val rgb = trace(snapshot, ray, random, maxDepth)
        buffer.setBlock(tile.x, tile.y, tile.width, tile.height, Color(rgb.x, rgb.y, rgb.z, 1f))
    }

    private fun trace(snapshot: SceneSnapshot, ray: Ray, random: RenderRng, depth: Int): Vector3 {
        val hit = nearestHit(snapshot.triangles, ray) ?: return Vector3(snapshot.skyColor.r, snapshot.skyColor.g, snapshot.skyColor.b)
        val direct = Vector3()
        snapshot.lights.forEach { light ->
            val toLight = Vector3(light.position).sub(hit.point)
            val dist2 = toLight.len2().coerceAtLeast(1e-4f)
            val dist = kotlin.math.sqrt(dist2)
            toLight.scl(1f / dist)
            val ndotl = max(hit.normal.dot(toLight), 0f)
            if (ndotl <= 0f) {
                return@forEach
            }
            val shadowOrigin = Vector3(hit.point).mulAdd(hit.normal, 0.01f)
            val shadowRay = Ray(shadowOrigin, toLight)
            val shadowHit = nearestHit(snapshot.triangles, shadowRay)
            if (shadowHit != null && shadowHit.t < dist - 0.02f) {
                return@forEach
            }
            val attenuation = light.intensity / dist2
            direct.x += hit.triangle.albedo.r * light.color.r * attenuation * ndotl
            direct.y += hit.triangle.albedo.g * light.color.g * attenuation * ndotl
            direct.z += hit.triangle.albedo.b * light.color.b * attenuation * ndotl
        }
        val ambient = Vector3(hit.triangle.albedo.r, hit.triangle.albedo.g, hit.triangle.albedo.b).scl(0.05f)
        if (depth <= 0) {
            return direct.add(ambient).limit01()
        }
        val bounceDir = cosineHemisphere(hit.normal, random)
        val bounced = trace(
            snapshot,
            Ray(Vector3(hit.point).mulAdd(hit.normal, 0.01f), bounceDir),
            random,
            depth - 1
        )
        bounced.x *= hit.triangle.albedo.r * 0.55f
        bounced.y *= hit.triangle.albedo.g * 0.55f
        bounced.z *= hit.triangle.albedo.b * 0.55f
        return direct.add(ambient).add(bounced).limit01()
    }
}

private fun nearestHit(triangles: List<RenderTriangle>, ray: Ray): Hit? {
    var closest: Hit? = null
    var closestT = Float.POSITIVE_INFINITY
    triangles.forEach { triangle ->
        val hit = intersectTriangle(ray, triangle) ?: return@forEach
        if (hit.t < closestT) {
            closestT = hit.t
            closest = hit
        }
    }
    return closest
}

private fun intersectTriangle(ray: Ray, triangle: RenderTriangle): Hit? {
    val edge1 = Vector3(triangle.b).sub(triangle.a)
    val edge2 = Vector3(triangle.c).sub(triangle.a)
    val pvec = Vector3(ray.direction).crs(edge2)
    val det = edge1.dot(pvec)
    if (kotlin.math.abs(det) < 1e-6f) {
        return null
    }
    val invDet = 1f / det
    val tvec = Vector3(ray.origin).sub(triangle.a)
    val u = tvec.dot(pvec) * invDet
    if (u < 0f || u > 1f) {
        return null
    }
    val qvec = Vector3(tvec).crs(edge1)
    val v = ray.direction.dot(qvec) * invDet
    if (v < 0f || u + v > 1f) {
        return null
    }
    val t = edge2.dot(qvec) * invDet
    if (t <= 1e-4f) {
        return null
    }
    val point = Vector3(ray.origin).mulAdd(ray.direction, t)
    val normal = Vector3(triangle.normal)
    if (normal.dot(ray.direction) > 0f) {
        normal.scl(-1f)
    }
    return Hit(triangle, point, normal, t)
}

private fun background(skyColor: Color, direction: Vector3): Color {
    val blend = ((direction.y + 1f) * 0.5f).coerceIn(0f, 1f)
    val horizon = Color(1f, 1f, 1f, 1f)
    return Color(
        MathUtils.lerp(horizon.r, skyColor.r, blend),
        MathUtils.lerp(horizon.g, skyColor.g, blend),
        MathUtils.lerp(horizon.b, skyColor.b, blend),
        1f
    )
}

private fun cosineHemisphere(normal: Vector3, random: RenderRng): Vector3 {
    val up = Vector3(normal).nor()
    val tangent = if (kotlin.math.abs(up.x) < 0.9f) {
        Vector3(1f, 0f, 0f)
    } else {
        Vector3(0f, 1f, 0f)
    }.crs(up).nor()
    val bitangent = Vector3(up).crs(tangent).nor()
    val r1 = random.nextFloat()
    val r2 = random.nextFloat()
    val phi = (2.0 * PI * r1).toFloat()
    val r = kotlin.math.sqrt(r2.toDouble()).toFloat()
    val x = kotlin.math.cos(phi.toDouble()).toFloat() * r
    val y = kotlin.math.sin(phi.toDouble()).toFloat() * r
    val z = kotlin.math.sqrt((1f - r2).coerceAtLeast(0f).toDouble()).toFloat()
    return Vector3(tangent).scl(x)
        .mulAdd(bitangent, y)
        .mulAdd(up, z)
        .nor()
}

private fun Vector3.limit01(): Vector3 {
    x = x.coerceIn(0f, 1f)
    y = y.coerceIn(0f, 1f)
    z = z.coerceIn(0f, 1f)
    return this
}

private class RenderRng(seed: Long) {
    private var state = if (seed != 0L) seed else 0x6A09E667F3BCC909L

    fun nextFloat(): Float {
        var z = state
        z = z xor (z shl 13)
        z = z xor (z ushr 7)
        z = z xor (z shl 17)
        state = z
        return ((z ushr 40).toInt() and 0xFFFFFF) / 16777216f
    }
}
