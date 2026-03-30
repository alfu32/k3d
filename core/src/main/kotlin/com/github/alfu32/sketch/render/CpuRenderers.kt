package com.github.alfu32.sketch.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import kotlin.math.PI
import kotlin.math.max

interface CpuRenderer {
    fun renderTile(
        snapshot: SceneSnapshot,
        tile: RenderTile,
        buffer: RenderBuffer,
        seed: Long,
        glassTransmission: Float
    ): Boolean
}

private data class Hit(
    val triangle: RenderTriangle,
    val point: Vector3,
    val normal: Vector3,
    val geometricNormal: Vector3,
    val t: Float
)

private data class SampleResult(
    val color: Color,
    val hit: Boolean
)

private data class TraceResult(
    val color: Vector3,
    val hit: Boolean
)

private const val GLASS_IOR = 1.52f
private const val HIT_EPSILON = 0.01f

class CpuRayTracer(
    private val maxDepth: Int = 3
) : CpuRenderer {
    override fun renderTile(
        snapshot: SceneSnapshot,
        tile: RenderTile,
        buffer: RenderBuffer,
        seed: Long,
        glassTransmission: Float
    ): Boolean {
        val random = RenderRng(seed)
        val sampleCount = when {
            tile.pixelStep <= 4 -> 2
            else -> 1
        }
        val rgb = Vector3()
        var hitCount = 0
        repeat(sampleCount) {
            val ray = snapshot.camera.rayForPixel(
                tile.x + random.nextFloat() * tile.width,
                tile.y + random.nextFloat() * tile.height
            )
            val sample = trace(snapshot, ray, maxDepth, glassTransmission)
            if (sample.hit) {
                hitCount += 1
            }
            rgb.x += sample.color.r
            rgb.y += sample.color.g
            rgb.z += sample.color.b
        }
        rgb.scl(1f / sampleCount.toFloat())
        val alpha = if (hitCount > 0) 1f else 0f
        buffer.setBlock(tile.x, tile.y, tile.width, tile.height, Color(rgb.x, rgb.y, rgb.z, alpha), tile.passIndex)
        return hitCount > 0
    }

    private fun trace(snapshot: SceneSnapshot, ray: Ray, depth: Int, glassTransmission: Float): SampleResult {
        val hit = nearestHit(snapshot.triangles, ray)
            ?: return SampleResult(background(snapshot.skyColor, ray.direction), false)
        val direct = directLightingAtHit(snapshot, hit, glassTransmission)
        val transmission = transmissionWeight(hit.triangle, glassTransmission)
        if (transmission > 0.001f && depth > 0) {
            val transmittedRay = transmissionRay(hit, ray)
            val transmitted = if (transmittedRay != null) {
                trace(snapshot, transmittedRay, depth - 1, glassTransmission).color
            } else {
                val reflected = reflectDirection(ray.direction, hit.normal)
                trace(
                    snapshot,
                    Ray(Vector3(hit.point).mulAdd(hit.normal, HIT_EPSILON), reflected),
                    depth - 1,
                    glassTransmission
                ).color
            }
            val localWeight = (1f - transmission).coerceIn(0f, 1f)
            return SampleResult(
                Color(
                    (direct.x * localWeight + transmitted.r * transmission).coerceIn(0f, 1f),
                    (direct.y * localWeight + transmitted.g * transmission).coerceIn(0f, 1f),
                    (direct.z * localWeight + transmitted.b * transmission).coerceIn(0f, 1f),
                    1f
                ),
                true
            )
        }
        return SampleResult(
            Color(
                direct.x.coerceIn(0f, 1f),
                direct.y.coerceIn(0f, 1f),
                direct.z.coerceIn(0f, 1f),
                1f
            ),
            true
        )
    }
}

class CpuPathTracer(
    private val maxDepth: Int = 4
) : CpuRenderer {
    override fun renderTile(
        snapshot: SceneSnapshot,
        tile: RenderTile,
        buffer: RenderBuffer,
        seed: Long,
        glassTransmission: Float
    ): Boolean {
        val random = RenderRng(seed)
        val sampleCount = when {
            tile.pixelStep <= 4 -> 4
            tile.pixelStep <= 8 -> 2
            else -> 1
        }
        val rgb = Vector3()
        var hitCount = 0
        repeat(sampleCount) {
            val ray = snapshot.camera.rayForPixel(
                tile.x + random.nextFloat() * tile.width,
                tile.y + random.nextFloat() * tile.height
            )
            val sample = tracePrimary(snapshot, ray, random, maxDepth, glassTransmission)
            if (sample.hit) {
                hitCount += 1
            }
            rgb.add(sample.color)
        }
        rgb.scl(1f / sampleCount.toFloat())
        val alpha = if (hitCount > 0) 1f else 0f
        buffer.setBlock(tile.x, tile.y, tile.width, tile.height, Color(rgb.x, rgb.y, rgb.z, alpha), tile.passIndex)
        return hitCount > 0
    }

    private fun tracePrimary(
        snapshot: SceneSnapshot,
        ray: Ray,
        random: RenderRng,
        depth: Int,
        glassTransmission: Float
    ): TraceResult {
        val hit = nearestHit(snapshot.triangles, ray)
            ?: return TraceResult(Vector3(snapshot.skyColor.r, snapshot.skyColor.g, snapshot.skyColor.b), false)
        return TraceResult(traceFromHit(snapshot, hit, ray, random, depth, glassTransmission), true)
    }

    private fun trace(
        snapshot: SceneSnapshot,
        ray: Ray,
        random: RenderRng,
        depth: Int,
        glassTransmission: Float
    ): Vector3 {
        val hit = nearestHit(snapshot.triangles, ray)
            ?: return Vector3(snapshot.skyColor.r, snapshot.skyColor.g, snapshot.skyColor.b)
        return traceFromHit(snapshot, hit, ray, random, depth, glassTransmission)
    }

    private fun traceFromHit(
        snapshot: SceneSnapshot,
        hit: Hit,
        ray: Ray,
        random: RenderRng,
        depth: Int,
        glassTransmission: Float
    ): Vector3 {
        val direct = directLightingAtHit(snapshot, hit, glassTransmission)
        if (depth <= 0) {
            return direct.limit01()
        }
        val transmission = transmissionWeight(hit.triangle, glassTransmission)
        if (transmission > 0.001f) {
            val transmittedRay = transmissionRay(hit, ray)
            val transmitted = if (transmittedRay != null) {
                trace(snapshot, transmittedRay, random, depth - 1, glassTransmission)
            } else {
                val reflected = reflectDirection(ray.direction, hit.normal)
                trace(
                    snapshot,
                    Ray(Vector3(hit.point).mulAdd(hit.normal, HIT_EPSILON), reflected),
                    random,
                    depth - 1,
                    glassTransmission
                )
            }
            val localWeight = (1f - transmission).coerceIn(0f, 1f)
            return direct.scl(localWeight).add(transmitted.scl(transmission)).limit01()
        }
        val bounceDir = cosineHemisphere(hit.normal, random)
        val bounced = trace(
            snapshot,
            Ray(Vector3(hit.point).mulAdd(hit.normal, HIT_EPSILON), bounceDir),
            random,
            depth - 1,
            glassTransmission
        )
        bounced.x *= hit.triangle.albedo.r * 0.55f
        bounced.y *= hit.triangle.albedo.g * 0.55f
        bounced.z *= hit.triangle.albedo.b * 0.55f
        return direct.add(bounced).limit01()
    }
}

private fun directLightingAtHit(snapshot: SceneSnapshot, hit: Hit, glassTransmission: Float): Vector3 {
    val shaded = Vector3(
        hit.triangle.albedo.r * snapshot.ambientLight.r,
        hit.triangle.albedo.g * snapshot.ambientLight.g,
        hit.triangle.albedo.b * snapshot.ambientLight.b
    )
    snapshot.directionalLights.forEach { light ->
        val toLight = Vector3(light.direction).scl(-1f).nor()
        val ndotl = max(hit.normal.dot(toLight), 0f)
        if (ndotl <= 0f) {
            return@forEach
        }
        val shadowOrigin = Vector3(hit.point).mulAdd(hit.normal, HIT_EPSILON)
        val visibility = shadowTransmittance(snapshot.triangles, Ray(shadowOrigin, toLight), Float.POSITIVE_INFINITY, glassTransmission)
        if (visibility <= 0.001f) {
            return@forEach
        }
        shaded.x += hit.triangle.albedo.r * light.color.r * light.intensity * ndotl * visibility
        shaded.y += hit.triangle.albedo.g * light.color.g * light.intensity * ndotl * visibility
        shaded.z += hit.triangle.albedo.b * light.color.b * light.intensity * ndotl * visibility
    }
    snapshot.lights.forEach { light ->
        val toLight = Vector3(light.position).sub(hit.point)
        val dist2 = toLight.len2().coerceAtLeast(1e-4f)
        val dist = kotlin.math.sqrt(dist2)
        toLight.scl(1f / dist)
        val ndotl = max(hit.normal.dot(toLight), 0f)
        if (ndotl <= 0f) {
            return@forEach
        }
        val shadowOrigin = Vector3(hit.point).mulAdd(hit.normal, HIT_EPSILON)
        val visibility = shadowTransmittance(snapshot.triangles, Ray(shadowOrigin, toLight), dist - 0.02f, glassTransmission)
        if (visibility <= 0.001f) {
            return@forEach
        }
        val attenuation = light.intensity / dist2
        shaded.x += hit.triangle.albedo.r * light.color.r * attenuation * ndotl * visibility
        shaded.y += hit.triangle.albedo.g * light.color.g * attenuation * ndotl * visibility
        shaded.z += hit.triangle.albedo.b * light.color.b * attenuation * ndotl * visibility
    }
    return shaded
}

private fun shadowTransmittance(
    triangles: List<RenderTriangle>,
    ray: Ray,
    maxDistance: Float,
    glassTransmission: Float
): Float {
    var transmittance = 1f
    triangles.forEach { triangle ->
        if (transmittance <= 0.001f) {
            return 0f
        }
        val weight = transmissionWeight(triangle, glassTransmission)
        if (weight >= 0.999f) {
            return@forEach
        }
        val hit = intersectTriangle(ray, triangle) ?: return@forEach
        if (hit.t < maxDistance) {
            if (weight <= 0.001f) {
                return 0f
            }
            transmittance *= weight
        }
    }
    return transmittance.coerceIn(0f, 1f)
}

private fun transmissionWeight(triangle: RenderTriangle, glassTransmission: Float): Float {
    val alpha = triangle.albedo.a.coerceIn(0f, 1f)
    return ((1f - alpha) * glassTransmission).coerceIn(0f, 1f)
}

private fun transmissionRay(hit: Hit, ray: Ray): Ray? {
    val geometricNormal = Vector3(hit.geometricNormal).nor()
    val entering = geometricNormal.dot(ray.direction) < 0f
    val orientedNormal = if (entering) geometricNormal else Vector3(geometricNormal).scl(-1f)
    val eta = if (entering) 1f / GLASS_IOR else GLASS_IOR
    val refracted = refractDirection(ray.direction, orientedNormal, eta)
        ?: return null
    return Ray(Vector3(hit.point).mulAdd(refracted, HIT_EPSILON), refracted)
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
    val geometricNormal = Vector3(triangle.normal).nor()
    val normal = Vector3(geometricNormal)
    if (normal.dot(ray.direction) > 0f) {
        normal.scl(-1f)
    }
    return Hit(triangle, point, normal, geometricNormal, t)
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

private fun reflectDirection(direction: Vector3, normal: Vector3): Vector3 {
    return Vector3(direction)
        .sub(Vector3(normal).scl(2f * direction.dot(normal)))
        .nor()
}

private fun refractDirection(direction: Vector3, normal: Vector3, eta: Float): Vector3? {
    val incident = Vector3(direction).nor()
    val n = Vector3(normal).nor()
    val cosI = (-incident.dot(n)).coerceIn(-1f, 1f)
    val sinT2 = eta * eta * (1f - cosI * cosI)
    if (sinT2 > 1f) {
        return null
    }
    val cosT = kotlin.math.sqrt((1f - sinT2).coerceAtLeast(0f).toDouble()).toFloat()
    return Vector3(incident).scl(eta)
        .mulAdd(n, eta * cosI - cosT)
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
