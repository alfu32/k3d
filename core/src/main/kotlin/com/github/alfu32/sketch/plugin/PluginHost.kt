package com.github.alfu32.sketch.plugin

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.model.ModelPersistence
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.ToolId
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.ServiceLoader
import kotlin.math.absoluteValue

class PluginHost(
    private val scene: GroupScene,
    private val statusModel: StatusModel,
    private val camera: com.badlogic.gdx.graphics.PerspectiveCamera,
    private val lighting: com.github.alfu32.sketch.ui.LightingSettings,
    private val shadow: com.github.alfu32.sketch.ui.ShadowSettings,
    private val getActiveTool: () -> ToolId,
    private val getCopyMode: () -> Boolean,
    private val getCursorSnap: () -> com.github.alfu32.sketch.input.SnapResult?
) : PluginRegistry {
    override val plugins: MutableSet<Plugin> = mutableSetOf()
    private val pluginStates = mutableMapOf<String, PluginState>()
    private val pluginIdToEntry = mutableMapOf<String, String>()
    private val pluginsDir = File("plugins")
    private val catalogFile = File(pluginsDir, "plugins.json")
    private var catalog = PluginCatalog()

    fun loadCatalog() {
        catalog = PluginCatalog.load(catalogFile)
        pluginsDir.mkdirs()
        val known = catalog.plugins.map { entryFile(it).name }.toSet()
        var added = false
        pluginsDir.listFiles { file -> file.isFile && file.extension.equals("jar", ignoreCase = true) }
            ?.forEach { file ->
                if (!known.contains(file.name)) {
                    catalog.plugins.add(
                        PluginEntry(
                            url = "file:${file.absolutePath}",
                            enabled = true,
                            fileName = file.name
                        )
                    )
                    added = true
                }
            }
        if (added) {
            saveCatalog()
        }
    }

    fun saveCatalog() {
        catalog.save(catalogFile)
    }

    fun pluginEntries(): List<PluginEntryInfo> {
        return catalog.plugins.map { entry ->
            val state = pluginStates[entry.url]
            PluginEntryInfo(
                url = entry.url,
                enabled = entry.enabled,
                installed = entryFile(entry).exists(),
                name = state?.plugin?.name,
                version = state?.plugin?.version,
                lastError = state?.lastError
            )
        }
    }

    fun addPlugin(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return
        }
        if (catalog.plugins.any { it.url == trimmed }) {
            return
        }
        catalog.plugins.add(PluginEntry(url = trimmed, enabled = true))
        saveCatalog()
    }

    fun removePlugin(url: String) {
        val entry = catalog.plugins.firstOrNull { it.url == url } ?: return
        unloadEntry(entry)
        catalog.plugins.remove(entry)
        saveCatalog()
    }

    fun setEnabled(url: String, enabled: Boolean) {
        val entry = catalog.plugins.firstOrNull { it.url == url } ?: return
        entry.enabled = enabled
        if (!enabled) {
            unloadEntry(entry)
        } else {
            reloadEnabledAndInit()
        }
        saveCatalog()
    }

    fun download(url: String?): Boolean {
        if (url == null) {
            var ok = true
            catalog.plugins.forEach { entry ->
                if (!downloadEntry(entry)) {
                    ok = false
                }
            }
            return ok
        }
        val entry = catalog.plugins.firstOrNull { it.url == url } ?: return false
        return downloadEntry(entry)
    }

    fun reloadEnabled() {
        unloadAll()
        catalog.plugins.filter { it.enabled }.forEach { entry ->
            loadEntry(entry)
        }
    }

    fun reloadEnabledAndInit() {
        reloadEnabled()
        dispatchLoad()
        dispatchCreate()
    }

    fun dispatchLoad() {
        plugins.forEach { plugin ->
            applyResult(plugin, safeCall(plugin) { it.onLoad(buildContext()) })
        }
    }

    fun dispatchCreate() {
        plugins.forEach { plugin ->
            applyResult(plugin, safeCall(plugin) { it.onCreate(buildContext()) })
        }
    }

    fun dispatchUpdate(deltaSeconds: Float) {
        plugins.forEach { plugin ->
            applyResult(plugin, safeCall(plugin) { it.onUpdate(buildContext(), deltaSeconds) })
        }
    }

    fun dispatchSave() {
        plugins.forEach { plugin ->
            applyResult(plugin, safeCall(plugin) { it.onSave(buildContext()) })
        }
    }

    fun dispatchClose() {
        plugins.forEach { plugin ->
            applyResult(plugin, safeCall(plugin) { it.onClose(buildContext()) })
        }
        unloadAll()
    }

    fun collectDrawLines(): List<PluginLine> {
        val lines = mutableListOf<PluginLine>()
        plugins.forEach { plugin ->
            val draw = safeCall(plugin) { it.onDraw(buildContext()) }
            draw?.lines?.let { lines.addAll(it) }
        }
        return lines
    }

    private fun applyResult(plugin: Plugin, result: PluginResult?) {
        val safe = result ?: return
        safe.changes.forEach { change ->
            when (change) {
                is PluginChange.ReplaceModel -> {
                    ModelPersistence.applySnapshot(change.snapshot, scene, camera, lighting, shadow)
                    scene.applyChangeListenerToAll()
                }
                is PluginChange.StatusMessage -> {
                    statusModel.message = change.message
                }
            }
        }
    }

    private fun buildContext(): PluginContext {
        val modelSnapshot = ModelPersistence.snapshot(scene, camera, lighting, shadow)
        val selection = buildSelectionSnapshot()
        val snap = getCursorSnap()
        val cursor = CursorSnapshot(
            screen = Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()),
            world = snap?.world?.let { Vector3(it) },
            snapLabel = snap?.type?.label ?: "No hit"
        )
        return PluginContext(
            model = modelSnapshot,
            selection = selection,
            cursor = cursor,
            screenSize = Vector2(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()),
            activeTool = getActiveTool(),
            copyMode = getCopyMode()
        )
    }

    private fun buildSelectionSnapshot(): SelectionSnapshot {
        val group = scene.activeGroup()
        val edges = group.lineStore.getSelected().map { segment ->
            ModelPersistence.SegmentDto(
                ModelPersistence.Vec3Dto(group.toWorld(segment.start)),
                ModelPersistence.Vec3Dto(group.toWorld(segment.end))
            )
        }
        val faces = group.faceStore.getSelected().map { triangle ->
            ModelPersistence.FaceDto(
                ModelPersistence.Vec3Dto(group.toWorld(triangle.a)),
                ModelPersistence.Vec3Dto(group.toWorld(triangle.b)),
                ModelPersistence.Vec3Dto(group.toWorld(triangle.c)),
                ModelPersistence.ColorDto(group.faceStore.colorFor(triangle))
            )
        }
        val groups = scene.selectedGroups().map { it.id }
        return SelectionSnapshot(edges, faces, groups)
    }

    private fun downloadEntry(entry: PluginEntry): Boolean {
        if (entry.url.isBlank()) {
            return false
        }
        if (entry.url.startsWith("file:")) {
            return entryFile(entry).exists()
        }
        val target = entryFile(entry)
        target.parentFile?.mkdirs()
        return try {
            val temp = File(target.parentFile, "${target.name}.download")
            URL(entry.url).openStream().use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            temp.copyTo(target, overwrite = true)
            temp.delete()
            true
        } catch (ex: Exception) {
            pluginStates[entry.url] = PluginState(null, null, ex.message ?: "Download failed")
            false
        }
    }

    private fun entryFile(entry: PluginEntry): File {
        val fileName = entry.fileName ?: fileNameFromUrl(entry.url).also { entry.fileName = it }
        return File(pluginsDir, fileName)
    }

    private fun fileNameFromUrl(url: String): String {
        val sanitized = url.substringAfterLast('/').ifBlank {
            "plugin-${url.hashCode().absoluteValue}.jar"
        }
        return if (sanitized.endsWith(".jar")) sanitized else "$sanitized.jar"
    }

    private fun loadEntry(entry: PluginEntry) {
        val jar = entryFile(entry)
        if (!jar.exists()) {
            pluginStates[entry.url] = PluginState(null, null, "Missing jar ${jar.name}")
            return
        }
        try {
            val loader = URLClassLoader(arrayOf(jar.toURI().toURL()), javaClass.classLoader)
            val loaded = ServiceLoader.load(Plugin::class.java, loader).iterator().asSequence().toList()
            if (loaded.isEmpty()) {
                pluginStates[entry.url] = PluginState(loader, null, "No Plugin services found")
                return
            }
            loaded.forEach { plugin ->
                plugins.add(plugin)
                pluginStates[entry.url] = PluginState(loader, plugin, null)
                pluginIdToEntry[plugin.id] = entry.url
            }
        } catch (ex: Exception) {
            pluginStates[entry.url] = PluginState(null, null, ex.message ?: "Load failed")
        }
    }

    private fun unloadEntry(entry: PluginEntry) {
        val state = pluginStates.remove(entry.url) ?: return
        state.plugin?.let { plugins.remove(it) }
        state.plugin?.let { pluginIdToEntry.remove(it.id) }
        try {
            state.loader?.close()
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun unloadAll() {
        pluginStates.values.forEach { state ->
            state.plugin?.let { plugins.remove(it) }
            state.plugin?.let { pluginIdToEntry.remove(it.id) }
            try {
                state.loader?.close()
            } catch (_: Exception) {
                // ignore
            }
        }
        pluginStates.clear()
    }

    private fun <T> safeCall(plugin: Plugin, block: (Plugin) -> T): T? {
        return try {
            block(plugin)
        } catch (ex: Exception) {
            val entryUrl = pluginIdToEntry[plugin.id]
            if (entryUrl != null) {
                pluginStates[entryUrl]?.lastError = ex.message ?: "Plugin error"
            }
            null
        }
    }

    private data class PluginState(
        val loader: URLClassLoader?,
        val plugin: Plugin?,
        var lastError: String?
    )
}
