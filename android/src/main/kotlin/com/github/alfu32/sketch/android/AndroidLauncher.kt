package com.github.alfu32.sketch.android

import android.os.Bundle
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.github.alfu32.sketch.Main
import java.io.File

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cfg = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useCompass = false
            useAccelerometer = false
            useGyroscope = false
            useRotationVectorSensor = false
        }

        val appDir = (getExternalFilesDir(null) ?: filesDir).absoluteFile
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        val pluginsDir = File(appDir, "plugins").apply { mkdirs() }
        val modelFile = File(appDir, "sketch3d.k3d")

        // Keep plugin-folder semantics on Android by passing an explicit app-private folder.
        val args = arrayOf(
            "--file", modelFile.absolutePath,
            "--plugins-dir", pluginsDir.absolutePath
        )

        initialize(Main(args), cfg)
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.setCatchKey(Input.Keys.ESCAPE, true)
    }
}
