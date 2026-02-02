package com.github.alfu32.sketch.console

import com.github.alfu32.sketch.ui.LightingSettings
import com.github.alfu32.sketch.ui.ShadowSettings

class LightingFacade(
    private val lighting: LightingSettings,
    private val shadow: ShadowSettings,
    private val apply: () -> Unit
) {
    fun lighting(): LightingSettings = lighting

    fun shadow(): ShadowSettings = shadow

    fun apply() {
        apply.invoke()
    }
}
