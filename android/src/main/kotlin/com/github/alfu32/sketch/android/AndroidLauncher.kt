package com.github.alfu32.sketch.android

import android.os.Bundle
import android.view.KeyEvent
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.github.alfu32.sketch.InputModifiers
import com.github.alfu32.sketch.Main
import java.io.File

class AndroidLauncher : AndroidApplication() {
    private fun updateAndroidCtrlMetaState(event: KeyEvent) {
        val ctrlMeta = (event.metaState and KeyEvent.META_CTRL_ON) != 0
        InputModifiers.androidCtrlMetaActive = event.isCtrlPressed || ctrlMeta
    }

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
        val modelFile = File(appDir, "octodraw.octd")

        // Keep plugin-folder semantics on Android by passing an explicit app-private folder.
        val args = arrayOf(
            "--file", modelFile.absolutePath,
            "--plugins-dir", pluginsDir.absolutePath
        )

        initialize(Main(args), cfg)
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.setCatchKey(Input.Keys.ESCAPE, true)
        Gdx.input.setCatchKey(Input.Keys.CONTROL_LEFT, true)
        Gdx.input.setCatchKey(Input.Keys.CONTROL_RIGHT, true)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        updateAndroidCtrlMetaState(event)
        if (event.keyCode == KeyEvent.KEYCODE_CTRL_LEFT || event.keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (event.action == KeyEvent.ACTION_UP) {
                InputModifiers.androidCtrlMetaActive = false
            }
            // Consume bare CTRL key events to prevent OEM keyboard overlays from stealing focus.
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
        updateAndroidCtrlMetaState(event)
        return super.dispatchKeyShortcutEvent(event)
    }
}
