package com.github.alfu32.sketch.ui

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.github.alfu32.sketch.plugin.PluginEntryInfo
import com.github.alfu32.sketch.plugin.PluginHost
import com.kotcrab.vis.ui.widget.VisCheckBox
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisScrollPane
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextArea
import com.kotcrab.vis.ui.widget.VisTextButton
import com.kotcrab.vis.ui.widget.VisTextField
import com.kotcrab.vis.ui.widget.VisWindow

class PluginManagerPanel(private val pluginHost: PluginHost) : VisWindow("Plugin Manager") {
    private val pluginListTable = VisTable()
    private val pluginLogArea = VisTextArea()
    private val pluginUrlField = VisTextField()
    private val pluginPathLabel = VisLabel("")
    private var lastSnapshot: List<PluginEntryInfo> = emptyList()

    init {
        isResizable = true
        isModal = false
        setupUI()
        refresh()
    }

    private fun setupUI() {
        defaults().pad(8f).left()

        val header = VisTable()
        header.defaults().padRight(6f)
        header.add(VisLabel("Dir:"))
        header.add(pluginPathLabel).growX().left()
        add(header).growX().row()

        val addRow = VisTable()
        addRow.defaults().padRight(6f)
        addRow.add(pluginUrlField).growX().minWidth(260f)
        val addButton = VisTextButton("Add")
        addButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val url = pluginUrlField.text
                pluginUrlField.text = ""
                pluginHost.addPlugin(url)
                refresh()
            }
        })
        addRow.add(addButton)
        add(addRow).growX().row()

        val actionRow = VisTable()
        actionRow.defaults().padRight(6f)
        val downloadButton = VisTextButton("Download")
        downloadButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                pluginHost.download(null)
                refresh()
            }
        })
        val reloadButton = VisTextButton("Reload")
        reloadButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                pluginHost.reloadEnabledAndInit()
                refresh(force = true)
            }
        })
        actionRow.add(downloadButton)
        actionRow.add(reloadButton)
        add(actionRow).left().row()

        pluginListTable.defaults().left().pad(4f)
        val listScroll = VisScrollPane(pluginListTable).apply {
            setFadeScrollBars(false)
            setScrollingDisabled(true, false)
        }
        add(listScroll).growX().height(260f).row()

        pluginLogArea.isDisabled = true
        val logScroll = VisScrollPane(pluginLogArea).apply {
            setFadeScrollBars(false)
        }
        add(logScroll).growX().height(120f).row()

        pack()
        setPosition(100f, 100f)
    }

    fun refresh(force: Boolean = false) {
        val entries = pluginHost.pluginEntriesLegacy()
        if (!force && entries == lastSnapshot) {
            return
        }
        lastSnapshot = entries
        pluginPathLabel.setText(pluginHost.pluginsDirectory().absolutePath)
        pluginListTable.clearChildren()
        val logLines = mutableListOf<String>()

        entries.forEach { entry ->
            val row = VisTable()
            row.defaults().left().padRight(6f)
            val enabled = VisCheckBox("", entry.enabled)
            enabled.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                    pluginHost.setEnabled(entry.url, enabled.isChecked)
                    refresh(force = true)
                }
            })
            val label = VisLabel(formatPluginLabel(entry))
            val download = VisTextButton("Get")
            download.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    pluginHost.download(entry.url)
                    refresh(force = true)
                }
            })
            val remove = VisTextButton("Remove")
            remove.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    pluginHost.removePlugin(entry.url)
                    refresh(force = true)
                }
            })
            row.add(enabled)
            row.add(label).expandX().left()
            row.add(download)
            row.add(remove)
            pluginListTable.add(row).growX().row()
            entry.lastError?.let { message ->
                val title = entry.name ?: entry.url
                logLines.add("$title: $message")
            }
        }
        pluginLogArea.text = logLines.joinToString("\n")
    }

    private fun formatPluginLabel(entry: PluginEntryInfo): String {
        val title = entry.name ?: entry.url
        val version = entry.version?.let { " v$it" } ?: ""
        val installed = if (entry.installed) "" else " (missing)"
        val error = entry.lastError?.let { " ! $it" } ?: ""
        return "$title$version$installed$error"
    }
}
