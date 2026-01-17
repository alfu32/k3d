package com.github.alfu32.sketch.render

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.github.alfu32.sketch.ui.ShadowSettings

class SketchDefaultShader(
    renderable: Renderable,
    config: Config,
    private val shadowSettingsProvider: () -> ShadowSettings,
    private val shadowLightProvider: () -> DirectionalShadowLight
) : DefaultShader(renderable, config) {

    private val uShadowBias = register("u_shadowBias")
    private val uShadowNormalBias = register("u_shadowNormalBias")
    private val uShadowMapSize = register("u_shadowMapSize")
    private val uShadowPcfMode = register("u_shadowPcfMode")
    private val uShadowDither = register("u_shadowDither")
    private val uShadowUseCsm = register("u_shadowUseCsm")
    private val uShadowLightDir = register("u_shadowLightDir")

    override fun begin(camera: Camera, context: RenderContext) {
        super.begin(camera, context)
        val settings = shadowSettingsProvider()
        if (has(uShadowBias)) {
            set(uShadowBias, settings.shadowBias)
        }
        if (has(uShadowNormalBias)) {
            set(uShadowNormalBias, settings.shadowNormalBias)
        }
        if (has(uShadowPcfMode)) {
            set(uShadowPcfMode, settings.pcfMode.toFloat())
        }
        if (has(uShadowDither)) {
            set(uShadowDither, if (settings.dither) 1f else 0f)
        }
        if (has(uShadowUseCsm)) {
            set(uShadowUseCsm, if (settings.useCsm) 1f else 0f)
        }

        val light = shadowLightProvider()
        val mapSize = light.frameBuffer.width.toFloat()
        if (has(uShadowMapSize)) {
            set(uShadowMapSize, mapSize)
        }
        if (has(uShadowLightDir)) {
            set(uShadowLightDir, light.direction)
        }
    }
}
