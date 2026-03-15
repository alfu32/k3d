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
import kotlin.math.absoluteValue

class PluginHost(
    private val scene: GroupScene,
    private val statusModel: StatusModel,
    private val camera: com.badlogic.gdx.graphics.PerspectiveCamera,
    private val getCameraTarget: () -> Vector3,
    private val lighting: com.github.alfu32.sketch.ui.LightingSettings,
    private val shadow: com.github.alfu32.sketch.ui.ShadowSettings,
    private val getActiveTool: () -> ToolId,
    private val getCopyMode: () -> Boolean,
    private val getCursorSnap: () -> com.github.alfu32.sketch.input.SnapResult?,
    private val setActiveTool: (ToolId) -> Unit,
    private val getModelUnit: () -> com.github.alfu32.sketch.model.ModelUnit,
    private val getSnapEpsilon: () -> Float,
    private val getGridSpacing: () -> Float,
    private val pluginsDir: File,
    private val getCurrentFile: () -> File? = { null }
) : PluginRegistry {
    override val plugins: MutableSet<Plugin> = mutableSetOf()
    private val pluginStates = mutableMapOf<String, PluginState>()
    private val pluginIdToEntry = mutableMapOf<String, String>()
    private val catalogFile = File(pluginsDir, "plugins.json")
    private var catalog = PluginCatalog()

    // New properties for enhanced plugin system
    private val commandPalette = CommandPalette()
    private val enabledPlugins = mutableSetOf<String>()
    private val pluginTools = mutableMapOf<String, com.github.alfu32.sketch.plugin.capabilities.PluginTool>()
    private var activePluginToolId: String? = null
    private val helperScripts = setOf("encode_base64.groovy", "polyline.groovy")
    private var showPluginPanelHandler: (String) -> Unit = {}
    private var cachedUpdateModelSnapshot: ModelPersistence.ModelSnapshot? = null

    fun loadCatalog() {
        catalog = PluginCatalog.load(catalogFile)
        pluginsDir.mkdirs()
        seedPluginsDirFromScripts()
        val known = catalog.plugins.map { entryFile(it).absolutePath }.toSet()
        var added = false
        pluginsDir.listFiles { file ->
            file.isFile &&
                file.extension.equals("groovy", ignoreCase = true) &&
                !isHelperScript(file)
        }
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
        scriptsDir.listFiles { file ->
            file.isFile &&
                file.extension.equals("groovy", ignoreCase = true) &&
                !isHelperScript(file)
        }
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

    fun setShowPluginPanelHandler(handler: (String) -> Unit) {
        showPluginPanelHandler = handler
    }

    fun pluginUiElements(): List<PluginUiEntry> {
        return plugins.filter { it.id in enabledPlugins }.flatMap { plugin ->
            plugin.registerUIElements().map { element ->
                PluginUiEntry(plugin.id, plugin.name, element)
            }
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
        pluginTools.clear()
        activePluginToolId = null
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
                    execute = {
                        val result = command.execute(buildContext())
                        applyResult(result)
                        result
                    }
                )
            )
        }

        // Register tools
        plugin.registerTools().forEach { tool ->
            val toolKey = "${plugin.id}.${tool.id}"
            pluginTools[toolKey] = tool
            if (activePluginToolId == null) {
                activePluginToolId = toolKey
            }
            if (tool.visibleInPalette) {
                commandPalette.registerCommand(
                    PaletteCommand(
                        id = "tool.$toolKey",
                        name = "Tool> ${tool.name}",
                        description = tool.description,
                        icon = tool.icon,
                        category = "Tools",
                        tags = listOf(tool.name.lowercase(), tool.category.name.lowercase()),
                        priority = 1,
                        execute = {
                            activatePluginTool(toolKey)
                            PluginResult.success()
                        }
                    )
                )
            }
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
        if (plugins.isEmpty()) {
            return
        }
        plugins.forEach { plugin ->
            applyResult(plugin, safeCall(plugin) { it.onUpdate(buildContext(), deltaSeconds) })
        }
    }

    fun invalidateUpdateContextModelSnapshot() {
        cachedUpdateModelSnapshot = null
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

    fun activePluginTool(): com.github.alfu32.sketch.plugin.capabilities.PluginTool? {
        return activePluginToolId?.let { pluginTools[it] }
    }

    fun activePluginToolId(): String? = activePluginToolId

    fun pluginToolEntries(): List<PluginToolInfo> {
        return pluginTools.map { (id, tool) ->
            val pluginId = id.substringBefore('.')
            val pluginName = plugins.firstOrNull { it.id == pluginId }?.name ?: pluginId
            PluginToolInfo(
                id = id,
                pluginId = pluginId,
                pluginName = pluginName,
                name = tool.name,
                description = tool.description,
                icon = tool.icon,
                iconDrawable = tool.iconDrawable
            )
        }.sortedBy { it.name }
    }

    fun activatePluginTool(toolId: String) {
        if (pluginTools.containsKey(toolId)) {
            activePluginToolId = toolId
            setActiveTool(ToolId.PLUGIN)
        }
    }

    fun pluginContext(): PluginContext = buildContext()

    fun pluginsDirectory(): File = pluginsDir

    private fun applyResult(plugin: Plugin, result: PluginResult?) {
        val safe = result ?: return
        applyResult(safe)
    }

    fun applyResult(result: PluginResult) {
        val safe = result
        safe.changes.forEach { change ->
            when (change) {
                is PluginChange.ReplaceModel -> {
                    ModelPersistence.applySnapshot(change.snapshot, scene, camera, getCameraTarget(), lighting, shadow)
                    scene.applyChangeListenerToAll()
                }
                is PluginChange.StatusMessage -> {
                    statusModel.message = change.message
                }
                is PluginChange.ShowPluginPanel -> {
                    showPluginPanelHandler(change.panelId)
                }
                is PluginChange.AddToActiveGroup -> {
                    val group = scene.activeGroup()
                    change.segments.forEach { segment ->
                        group.lineStore.addSegment(
                            segment.start.toVector3(),
                            segment.end.toVector3(),
                            autoCleanup = false
                        )
                    }
                    change.faces.forEach { face ->
                        group.faceStore.addTriangle(
                            face.a.toVector3(),
                            face.b.toVector3(),
                            face.c.toVector3(),
                            face.color.toColor()
                        )
                    }
                    scene.applyChangeListenerToAll()
                }
            }
        }
    }

    private fun buildContext(): PluginContext {
        val modelSnapshot = buildUpdateModelSnapshot()
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
            copyMode = getCopyMode(),
            installDir = pluginsDir.parentFile ?: pluginsDir,
            pluginsDir = pluginsDir,
            currentDir = File(System.getProperty("user.dir")),
            currentFile = getCurrentFile(),
            applyResult = { result -> applyResult(result) }
        )
    }

    private fun buildUpdateModelSnapshot(): ModelPersistence.ModelSnapshot {
        val snapshot = cachedUpdateModelSnapshot ?: ModelPersistence.snapshot(
            scene,
            camera,
            getCameraTarget(),
            lighting,
            shadow,
            getModelUnit(),
            getSnapEpsilon(),
            getGridSpacing(),
            null
        ).also { cachedUpdateModelSnapshot = it }

        // Refresh transient runtime state without rebuilding large geometry lists every frame.
        snapshot.cameraState = ModelPersistence.CameraDto(camera, getCameraTarget())
        snapshot.lightingState = ModelPersistence.LightingDto(lighting)
        snapshot.shadowState = ModelPersistence.ShadowDto(shadow)
        snapshot.modelUnit = ModelPersistence.ModelUnitDto(getModelUnit())
        snapshot.snapEpsilon = getSnapEpsilon()
        snapshot.gridSpacing = getGridSpacing()
        snapshot.undoHistory = null
        return snapshot
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
        if (isHelperScript(script)) {
            return
        }
        if (!script.exists()) {
            pluginStates[entry.url] = PluginState(null, null, "Missing script ${script.name}")
            println("Plugin load failed: ${entry.url} (missing script ${script.name})")
            return
        }
        try {
            val context = createGroovyLoaderContext()
            if (context == null) {
                pluginStates[entry.url] = PluginState(null, null, "Groovy runtime unavailable on this platform.")
                println("Plugin load failed: ${entry.url} (Groovy runtime unavailable)")
                return
            }
            addPluginApiJar(context.loader)
            val scriptText = script.readText()
            val plugin = instantiatePlugin(scriptText, script.name, context.loader, context.config)
            if (plugin == null) {
                pluginStates[entry.url] = PluginState(context.loader, null, "No Plugin instance returned")
                return
            }
            plugins.add(plugin)
            pluginStates[entry.url] = PluginState(context.loader, plugin, null)
            pluginIdToEntry[plugin.id] = entry.url
            if (entry.enabled) {
                enabledPlugins.add(plugin.id)
            }
        } catch (ex: Throwable) {
            val errorDetail = buildString {
                val typeName = ex::class.java.simpleName.takeIf { it.isNotBlank() } ?: "Load failed"
                append(typeName)
                ex.message?.takeIf { it.isNotBlank() }?.let {
                    append(": ")
                    append(it)
                }
            }
            pluginStates[entry.url] = PluginState(null, null, errorDetail)
            println("Plugin load failed: ${entry.url} ($errorDetail)")
            ex.printStackTrace()
        }
    }

    private fun unloadEntry(entry: PluginEntry) {
        val state = pluginStates.remove(entry.url) ?: return
        state.plugin?.let { plugin ->
            plugins.remove(plugin)
            pluginIdToEntry.remove(plugin.id)
            enabledPlugins.remove(plugin.id)
            val prefix = "${plugin.id}."
            pluginTools.keys.filter { it.startsWith(prefix) }.forEach { pluginTools.remove(it) }
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
        pluginTools.clear()
        activePluginToolId = null
        pluginStates.clear()
    }

    private fun <T> safeCall(plugin: Plugin, block: (Plugin) -> T): T? {
        return try {
            block(plugin)
        } catch (ex: Exception) {
            val entryUrl = pluginIdToEntry[plugin.id]
            if (entryUrl != null) {
                pluginStates[entryUrl]?.lastError = ex.message ?: "Plugin error"
                println("Plugin error: $entryUrl (${ex.message ?: "Plugin error"})")
            }
            null
        }
    }

    private data class PluginState(
        val loader: AutoCloseable?,
        val plugin: Plugin?,
        var lastError: String?
    )

    private data class GroovyLoaderContext(
        val loader: AutoCloseable,
        val config: Any
    )

    private fun createGroovyLoaderContext(): GroovyLoaderContext? {
        return try {
            val configClass = Class.forName("org.codehaus.groovy.control.CompilerConfiguration")
            val importCustomizerClass = Class.forName("org.codehaus.groovy.control.customizers.ImportCustomizer")
            val compilationCustomizerClass = Class.forName("org.codehaus.groovy.control.customizers.CompilationCustomizer")
            val loaderClass = Class.forName("groovy.lang.GroovyClassLoader")

            val config = configClass.getDeclaredConstructor().newInstance()
            val imports = importCustomizerClass.getDeclaredConstructor().newInstance()
            importCustomizerClass.getMethod("addStarImports", Array<String>::class.java)
                .invoke(imports, arrayOf("com.github.alfu32.sketch.plugin"))
            val customizers = java.lang.reflect.Array.newInstance(compilationCustomizerClass, 1)
            java.lang.reflect.Array.set(customizers, 0, imports)
            configClass.getMethod("addCompilationCustomizers", customizers.javaClass)
                .invoke(config, customizers)

            val loader = loaderClass.getConstructor(ClassLoader::class.java, configClass)
                .newInstance(javaClass.classLoader, config) as? AutoCloseable
                ?: return null
            GroovyLoaderContext(loader, config)
        } catch (_: Throwable) {
            null
        }
    }

    private fun instantiatePlugin(
        scriptText: String,
        scriptName: String,
        loader: AutoCloseable,
        config: Any
    ): Plugin? {
        val bindingClass = Class.forName("groovy.lang.Binding")
        val shellClass = Class.forName("groovy.lang.GroovyShell")
        val loaderClass = Class.forName("groovy.lang.GroovyClassLoader")
        val shell = shellClass.getConstructor(ClassLoader::class.java, bindingClass, config.javaClass)
            .newInstance(loader as ClassLoader, bindingClass.getDeclaredConstructor().newInstance(), config)
        val result = shellClass.getMethod("evaluate", String::class.java, String::class.java)
            .invoke(shell, scriptText, scriptName)
        if (result is Plugin) {
            return result
        }
        val scriptClass = loaderClass.getMethod("parseClass", String::class.java, String::class.java)
            .invoke(loader, scriptText, scriptName) as? Class<*> ?: return null
        if (Plugin::class.java.isAssignableFrom(scriptClass)) {
            return scriptClass.getDeclaredConstructor().newInstance() as Plugin
        }
        return null
    }

    private fun addPluginApiJar(loader: AutoCloseable) {
        val apiJar = pluginsDir.listFiles { file ->
            file.isFile &&
                (file.name.startsWith("octodraw-plugin-api") || file.name.startsWith("k3d-plugin-api")) &&
                file.extension.equals("jar", true)
        }?.firstOrNull() ?: return
        try {
            loader.javaClass.getMethod("addURL", URL::class.java).invoke(loader, apiJar.toURI().toURL())
        } catch (_: Throwable) {
            // Ignore; loader may not expose addURL.
        }
    }

    private fun isHelperScript(file: File): Boolean {
        return file.name in helperScripts
    }
}
