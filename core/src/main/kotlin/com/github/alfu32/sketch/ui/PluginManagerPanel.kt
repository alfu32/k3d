package com.github.alfu32.sketch.ui

import com.github.alfu32.sketch.plugin.PluginHost
import com.github.alfu32.sketch.plugin.PluginInfo
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisList
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.kotcrab.vis.ui.widget.VisWindow
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener

/**
 * Panel for managing plugins - shows installed plugins and allows enabling/disabling
 */
class PluginManagerPanel(private val pluginHost: PluginHost) : VisWindow("Plugin Manager") {
    
    private val pluginList = VisList<String>()
    private val pluginDetails = VisTable()
    private var selectedPlugin: PluginInfo? = null
    
    init {
        setupUI()
        refresh()
    }
    
    private fun setupUI() {
        defaults().pad(10f)
        
        // Left side - Plugin list
        val listContainer = VisTable()
        listContainer.add(VisLabel("Installed Plugins")).row()
        listContainer.add(pluginList).growX().height(300f).row()

        // Right side - Plugin details
        pluginDetails.add(VisLabel("Plugin Details")).colspan(2).row()
        pluginDetails.add(VisLabel("ID: ")).left()
        pluginDetails.add(VisLabel("")).growX().row()
        pluginDetails.add(VisLabel("Name: ")).left()
        pluginDetails.add(VisLabel("")).growX().row()
        pluginDetails.add(VisLabel("Version: ")).left()
        pluginDetails.add(VisLabel("")).growX().row()
        pluginDetails.add(VisLabel("Author: ")).left()
        pluginDetails.add(VisLabel("")).growX().row()
        pluginDetails.add(VisLabel("Description: ")).left().top()
        val descriptionValue = VisLabel("").apply { setWrap(true) }
        pluginDetails.add(descriptionValue).growX().row()
        pluginDetails.add(VisLabel("Status: ")).left()
        pluginDetails.add(VisLabel("")).growX().row()
        pluginDetails.add(VisLabel("Type: ")).left()
        pluginDetails.add(VisLabel("")).growX().row()
        
        // Setup plugin list
        pluginList.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                selectedPlugin = pluginHost.pluginEntries().find { 
                    "${it.name} v${it.version}" == pluginList.selected
                }
                refreshPluginDetails()
            }
        })
        
        // Layout
        add(listContainer).width(250f)
        add(pluginDetails).grow().padLeft(10f)
        
        // Buttons at bottom
        val buttons = VisTable()
        val refreshBtn = VisTextButton("Refresh")
        refreshBtn.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                pluginHost.reloadEnabledAndInit()
                refresh()
            }
        })
        
        buttons.add(refreshBtn)
        row()
        add(buttons).colspan(2).padTop(10f)
        
        pack()
        setPosition(100f, 100f)
    }
    
    fun refresh() {
        pluginList.setItems()
        val plugins = pluginHost.pluginEntries().sortedBy { it.name }
        val items = plugins.map { plugin ->
            val status = if (plugin.isEnabled) "OK " else "OFF "
            val type = if (plugin.isScript) "[G] " else "[J] "
            "$status$type${plugin.name} v${plugin.version}"
        }
        if (items.isNotEmpty()) {
            pluginList.setItems(*items.toTypedArray())
        }
    }
    
    private fun refreshPluginDetails() {
        selectedPlugin?.let { plugin ->
            pluginDetails.clearChildren()
            
            pluginDetails.add(VisLabel("Plugin Details")).colspan(2).row()
            pluginDetails.add(VisLabel("ID: ")).left()
            pluginDetails.add(VisLabel(plugin.id)).growX().row()
            pluginDetails.add(VisLabel("Name: ")).left()
            pluginDetails.add(VisLabel(plugin.name)).growX().row()
            pluginDetails.add(VisLabel("Version: ")).left()
            pluginDetails.add(VisLabel(plugin.version)).growX().row()
            pluginDetails.add(VisLabel("Author: ")).left()
            pluginDetails.add(VisLabel(plugin.author)).growX().row()
            pluginDetails.add(VisLabel("Description: ")).left().top()
            val descriptionValue = VisLabel(plugin.description).apply { setWrap(true) }
            pluginDetails.add(descriptionValue).growX().row()
            pluginDetails.add(VisLabel("Status: ")).left()
            pluginDetails.add(VisLabel(if (plugin.isEnabled) "Enabled" else "Disabled")).growX().row()
            pluginDetails.add(VisLabel("Type: ")).left()
            pluginDetails.add(VisLabel(if (plugin.isScript) "Groovy Script" else "Java Plugin")).growX().row()
        }
    }
}
