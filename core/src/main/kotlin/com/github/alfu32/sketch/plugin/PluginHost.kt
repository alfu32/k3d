package com.github.alfu32.sketch.plugin

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.model.ModelPersistence
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.ToolId
import java.io.File
import groovy.lang.GroovyClassLoader
import groovy.lang.GroovyShell
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import java.net.URL
import kotlin.math.absoluteValue

class PluginHost(
    private val scene: GroupScene,
    private val statusModel: StatusModel,
    private val camera: com.badlogic.gdx.graphics.PerspectiveCamera,
    private val lighting: com.github.alfu32.sketch.ui.LightingSettings,
    private val shadow: com.github.alfu32.sketch.ui.ShadowSettings,
    private val getActiveTool: () -> ToolId,
    private val getCopyMode: () -> Boolean,
    private val getCursorSnap: () -> com.github.alfu32.sketch.input.SnapResult?,
    private val pluginsDir: File
) : PluginRegistry {
    override val plugins: MutableSet<Plugin> = mutableSetOf()
    private val pluginStates = mutableMapOf<String, PluginState>()
    private val pluginIdToEntry = mutableMapOf<String, String>()
    private val catalogFile = File(pluginsDir, "plugins.json")
    private var catalog = PluginCatalog()
    
    // New properties for enhanced plugin system
    private val commandPalette = CommandPalette()
    private val enabledPlugins = mutableSetOf<String>()

    fun loadCatalog() {
        catalog = PluginCatalog.load(catalogFile)
        pluginsDir.mkdirs()
        seedPluginsDirFromScripts()
        val known = catalog.plugins.map { entryFile(it).absolutePath }.toSet()
        var added = false
        pluginsDir.listFiles { file -> file.isFile && file.extension.equals("groovy", ignoreCase = true) }
            ?.forEach { file ->
                if (!known.contains(file.absolutePath)) {
                    catalog.plugins.add(
                        PluginEntry(
                            url = file.toURI().toString(),
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

    private fun seedPluginsDirFromScripts() {
        val scriptsDir = pluginsDir.parentFile?.let { File(it, "scripts") } ?: return
        if (!scriptsDir.exists()) {
            return
        }
        val plugins = pluginsDir.listFiles { file ->
            file.isFile && file.extension.equals("groovy", ignoreCase = true)
        } ?: emptyArray()
        if (plugins.isNotEmpty()) {
            return
        }
        scriptsDir.listFiles { file -> file.isFile && file.extension.equals("groovy", ignoreCase = true) }
            ?.forEach { file ->
                val target = File(pluginsDir, file.name)
                if (!target.exists()) {
                    file.copyTo(target)
                }
            }
    }

    fun saveCatalog() {
        catalog.save(catalogFile)
    }

    // New method to get plugin info for the enhanced system
    fun pluginEntries(): List<PluginInfo> {
        return plugins.map { plugin ->
            PluginInfo(
                id = plugin.id,
                name = plugin.name,
                version = plugin.version,
                author = plugin.author,
                description = plugin.description,
                isEnabled = plugin.id in enabledPlugins,
                isScript = pluginIdToEntry[plugin.id]?.startsWith("file:") == true
            )
        }
    }
    
    // Old method kept for backward compatibility
    fun pluginEntriesLegacy(): List<PluginEntryInfo> {
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
        enabledPlugins.clear()
        catalog.plugins.filter { it.enabled }.forEach { entry ->
            loadEntry(entry)
        }
    }

    fun reloadEnabledAndInit() {
        loadCatalog()
        reloadEnabled()
        dispatchLoad()
        dispatchCreate()
        setupPluginCapabilities()  // Setup new capabilities after loading
    }
    
    // New method to setup plugin capabilities
    private fun setupPluginCapabilities() {
        plugins.forEach { plugin ->
            if (plugin.id in enabledPlugins) {
                registerPluginCapabilities(plugin)
            }
        }
    }
    
    private fun registerPluginCapabilities(plugin: Plugin) {
        // Register commands
        plugin.registerCommands().forEach { command ->
            commandPalette.registerCommand(
                PaletteCommand(
                    id = "${plugin.id}.${command.id}",
                    name = command.name,
                    description = command.description,
                    icon = command.icon,
                    category = command.category,
                    tags = command.getSearchTags(),
                    priority = if (command.isVisibleInPalette) 1 else 0,
                    execute = { command.execute(buildContext()) }
                )
            )
        }
        
        // Register other capabilities would go here
        // (Tools, EntityTypes, etc.)
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
        // Also call new save method
        plugins.forEach { plugin ->
            if (plugin.id in enabledPlugins) {
                safeCall(plugin) { it.onSceneSave(buildContext()) }
            }
        }
    }
    
    // New dispatch methods for enhanced plugin system
    fun dispatchSceneLoad() {
        plugins.forEach { plugin ->
            if (plugin.id in enabledPlugins) {
                safeCall(plugin) { it.onSceneLoad(buildContext()) }
            }
        }
    }
    
    fun dispatchSelectionChanged() {
        plugins.forEach { plugin ->
            if (plugin.id in enabledPlugins) {
                safeCall(plugin) { it.onSelectionChanged(buildContext()) }
            }
        }
    }
    
    fun dispatchToolChanged(newTool: ToolId) {
        plugins.forEach { plugin ->
            if (plugin.id in enabledPlugins) {
                safeCall(plugin) { it.onToolChanged(buildContext(), newTool) }
            }
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
    
    // Getter for command palette
    fun getCommandPalette(): CommandPalette = commandPalette

    fun pluginsDirectory(): File = pluginsDir

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
        if (entry.url.startsWith("file:")) {
            return try {
                File(java.net.URI(entry.url))
            } catch (_: Exception) {
                File(entry.url.removePrefix("file:"))
            }
        }
        val fileName = entry.fileName ?: fileNameFromUrl(entry.url).also { entry.fileName = it }
        return File(pluginsDir, fileName)
    }

    private fun fileNameFromUrl(url: String): String {
        val sanitized = url.substringAfterLast('/').ifBlank {
            "plugin-${url.hashCode().absoluteValue}.groovy"
        }
        return if (sanitized.endsWith(".groovy")) sanitized else "$sanitized.groovy"
    }

    private fun loadEntry(entry: PluginEntry) {
        val script = entryFile(entry)
        if (!script.exists()) {
            pluginStates[entry.url] = PluginState(null, null, "Missing script ${script.name}")
            return
        }
        try {
            val config = CompilerConfiguration()
            val imports = ImportCustomizer().apply {
                addStarImports("com.github.alfu32.sketch.plugin")
            }
            config.addCompilationCustomizers(imports)
            val loader = GroovyClassLoader(javaClass.classLoader, config)
            addPluginApiJar(loader)
            val scriptText = script.readText()
            val plugin = instantiatePlugin(scriptText, script.name, loader, config)
            if (plugin == null) {
                pluginStates[entry.url] = PluginState(loader, null, "No Plugin instance returned")
                return
            }
            plugins.add(plugin)
            pluginStates[entry.url] = PluginState(loader, plugin, null)
            pluginIdToEntry[plugin.id] = entry.url
            if (entry.enabled) {
                enabledPlugins.add(plugin.id)
            }
        } catch (ex: Exception) {
            pluginStates[entry.url] = PluginState(null, null, ex.message ?: "Load failed")
        }
    }

    private fun unloadEntry(entry: PluginEntry) {
        val state = pluginStates.remove(entry.url) ?: return
        state.plugin?.let { plugin ->
            plugins.remove(plugin)
            pluginIdToEntry.remove(plugin.id)
            enabledPlugins.remove(plugin.id)
        }
        try {
            state.loader?.close()
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun unloadAll() {
        pluginStates.values.forEach { state ->
            state.plugin?.let { plugin ->
                plugins.remove(plugin)
                pluginIdToEntry.remove(plugin.id)
                enabledPlugins.remove(plugin.id)
            }
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
        val loader: GroovyClassLoader?,
        val plugin: Plugin?,
        var lastError: String?
    )

    private fun instantiatePlugin(
        scriptText: String,
        scriptName: String,
        loader: GroovyClassLoader,
        config: CompilerConfiguration
    ): Plugin? {
        val shell = GroovyShell(loader, groovy.lang.Binding(), config)
        val result = shell.evaluate(scriptText, scriptName)
        if (result is Plugin) {
            return result
        }
        val scriptClass = loader.parseClass(scriptText, scriptName)
        if (Plugin::class.java.isAssignableFrom(scriptClass)) {
            return scriptClass.getDeclaredConstructor().newInstance() as Plugin
        }
        return null
    }

    private fun addPluginApiJar(loader: GroovyClassLoader) {
        val apiJar = pluginsDir.listFiles { file ->
            file.isFile && file.name.startsWith("katechup3d-plugin-api") && file.extension.equals("jar", true)
        }?.firstOrNull() ?: return
        loader.addURL(apiJar.toURI().toURL())
    }
}
