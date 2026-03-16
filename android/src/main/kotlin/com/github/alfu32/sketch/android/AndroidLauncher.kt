package com.github.alfu32.sketch.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.github.alfu32.sketch.AndroidSaf
import com.github.alfu32.sketch.AndroidSafBridge
import com.github.alfu32.sketch.InputModifiers
import com.github.alfu32.sketch.Main
import java.io.File
import java.io.FileOutputStream

class AndroidLauncher : AndroidApplication() {
    private companion object {
        const val REQUEST_OPEN_DOCUMENT = 48011
        const val REQUEST_CREATE_DOCUMENT = 48012
        const val BUNDLED_CONTENT_VERSION_FILE = ".bundled_content_version"
    }

    private var openDocumentCallback: ((String?, String?) -> Unit)? = null
    private var createDocumentCallback: ((String?, String?) -> Unit)? = null

    private fun updateAndroidCtrlMetaState(event: KeyEvent) {
        val ctrlMeta = (event.metaState and KeyEvent.META_CTRL_ON) != 0
        InputModifiers.androidCtrlMetaActive = event.isCtrlPressed || ctrlMeta
    }

    private fun dispatchDocumentResult(callback: ((String?, String?) -> Unit)?, uri: Uri?) {
        val cb = callback ?: return
        val value = uri?.toString()
        val name = uri?.let { queryDisplayName(it) }
        val runner = Runnable { cb(value, name) }
        if (Gdx.app == null) {
            runner.run()
        } else {
            Gdx.app.postRunnable(runner)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun installSafBridge() {
        AndroidSaf.bridge = object : AndroidSafBridge {
            override fun openDocument(onResult: (uri: String?, displayName: String?) -> Unit) {
                openDocumentCallback = onResult
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/json", "text/plain"))
                }
                startActivityForResult(intent, REQUEST_OPEN_DOCUMENT)
            }

            override fun createDocument(defaultName: String, onResult: (uri: String?, displayName: String?) -> Unit) {
                createDocumentCallback = onResult
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, defaultName)
                }
                startActivityForResult(intent, REQUEST_CREATE_DOCUMENT)
            }

            override fun readBytes(uri: String): ByteArray? {
                return try {
                    contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
                } catch (_: Throwable) {
                    null
                }
            }

            override fun writeBytes(uri: String, data: ByteArray): Boolean {
                return try {
                    contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { out ->
                        out.write(data)
                        out.flush()
                    } != null
                } catch (_: Throwable) {
                    false
                }
            }
        }
    }

    private fun ensureBundledContent(appDir: File) {
        val bundledVersionFile = File(appDir, BUNDLED_CONTENT_VERSION_FILE)
        val currentVersion = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
        } catch (_: Throwable) {
            "0"
        }

        copyAssetTree("bootstrap/tutorials", File(appDir, "tutorials"), overwriteExisting = true)

        bundledVersionFile.writeText(currentVersion)
    }

    private fun copyAssetTree(assetPath: String, targetDir: File, overwriteExisting: Boolean) {
        val entries = assets.list(assetPath) ?: return
        targetDir.mkdirs()
        entries.forEach { entry ->
            val childAssetPath = "$assetPath/$entry"
            val childEntries = assets.list(childAssetPath) ?: emptyArray()
            if (childEntries.isNotEmpty()) {
                copyAssetTree(childAssetPath, File(targetDir, entry), overwriteExisting)
            } else {
                copyAssetFile(childAssetPath, File(targetDir, entry), overwriteExisting)
            }
        }
    }

    private fun copyAssetFile(assetPath: String, target: File, overwriteExisting: Boolean) {
        if (target.exists() && !overwriteExisting) {
            return
        }
        target.parentFile?.mkdirs()
        assets.open(assetPath).use { input ->
            FileOutputStream(target, false).use { output ->
                input.copyTo(output)
                output.flush()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSafBridge()

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
        ensureBundledContent(appDir)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_OPEN_DOCUMENT -> {
                val cb = openDocumentCallback
                openDocumentCallback = null
                val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
                if (uri != null) {
                    val flags = data?.flags ?: 0
                    val persistableFlags =
                        flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (persistableFlags != 0) {
                        try {
                            contentResolver.takePersistableUriPermission(uri, persistableFlags)
                        } catch (_: SecurityException) {
                            // ignore, provider may not grant persistable permissions
                        }
                    }
                }
                dispatchDocumentResult(cb, uri)
            }

            REQUEST_CREATE_DOCUMENT -> {
                val cb = createDocumentCallback
                createDocumentCallback = null
                val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
                if (uri != null) {
                    val flags = data?.flags ?: 0
                    val persistableFlags =
                        flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (persistableFlags != 0) {
                        try {
                            contentResolver.takePersistableUriPermission(uri, persistableFlags)
                        } catch (_: SecurityException) {
                            // ignore, provider may not grant persistable permissions
                        }
                    }
                }
                dispatchDocumentResult(cb, uri)
            }
        }
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

    override fun onDestroy() {
        if (AndroidSaf.bridge != null) {
            AndroidSaf.bridge = null
        }
        super.onDestroy()
    }
}
