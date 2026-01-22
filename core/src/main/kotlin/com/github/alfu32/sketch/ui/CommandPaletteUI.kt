package com.github.alfu32.sketch.ui

import com.github.alfu32.sketch.plugin.CommandPalette
import com.github.alfu32.sketch.plugin.PaletteCommand
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.kotcrab.vis.ui.widget.Separator
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisList
import com.kotcrab.vis.ui.widget.VisScrollPane
import com.kotcrab.vis.ui.widget.VisTextField
import com.kotcrab.vis.ui.widget.VisWindow

/**
 * UI for the command palette that appears when Ctrl+Shift+P is pressed
 */
class CommandPaletteUI(
    private val commandPalette: CommandPalette,
    private val stage: Stage
) {
    private open class CollapsibleWindow(title: String) : VisWindow(title, true) {
        private var collapsed = false

        init {
            isMovable = true
            isResizable = true
            isModal = false
            setKeepWithinParent(false)
            addCloseButton()
            getTitleTable().addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (tapCount >= 2) {
                        toggleCollapsed()
                    }
                }
            })
        }

        override fun close() {
            isVisible = false
        }

        override fun getPrefWidth(): Float {
            return if (isVisible) super.getPrefWidth() else 0f
        }

        override fun getPrefHeight(): Float {
            if (!isVisible) {
                return 0f
            }
            return if (collapsed) getTitleTable().prefHeight else super.getPrefHeight()
        }

        private fun toggleCollapsed() {
            collapsed = !collapsed
            val title = getTitleTable()
            children.forEach { child ->
                if (child !== title) {
                    child.isVisible = !collapsed
                }
            }
            invalidateHierarchy()
            pack()
        }
    }

    private val window = CollapsibleWindow("Command Palette")
    private val searchField = VisTextField()
    private val commandList = VisList<String>()
    private val statusLabel = VisLabel("")
    private var isVisible = false
    
    init {
        setupUI()
        setupShortcuts()
    }
    
    private fun setupUI() {
        window.defaults().pad(5f)
        window.isModal = false
        window.isResizable = true
        window.setSize(600f, 400f)
        
        // Search field
        searchField.messageText = "Type a command name..."
        searchField.addListener(object : InputListener() {
            override fun keyTyped(event: InputEvent?, character: Char): Boolean {
                updateCommandList()
                return true
            }

            override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
                if (keycode == Input.Keys.ENTER) {
                    executeSelectedCommand()
                    return true
                }
                return false
            }
        })

        // Command list
        commandList.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (tapCount >= 2) {
                    executeSelectedCommand()
                }
            }
        })
        
        // Status label
        statusLabel.setColor(com.badlogic.gdx.graphics.Color.GRAY)
        
        // Layout
        window.add(searchField).growX().row()
        window.add(Separator()).growX().padTop(5f).padBottom(5f).row()
        val listScroll = VisScrollPane(commandList).apply {
            setFadeScrollBars(false)
            setScrollingDisabled(true, false)
        }
        window.add(listScroll).grow().row()
        window.add(statusLabel).growX().padTop(5f)
        
        // Hide initially
        window.isVisible = false
        stage.addActor(window)
    }
    
    private fun setupShortcuts() {
        stage.addListener(object : InputListener() {
            override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
                if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) &&
                    Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) &&
                    keycode == Input.Keys.P) {
                    
                    toggleVisibility()
                    return true
                }
                return false
            }
        })
    }
    
    fun toggleVisibility() {
        if (isVisible) {
            hide()
        } else {
            show()
        }
    }

    fun show() {
        isVisible = true
        window.isVisible = true
        searchField.text = ""
        stage.keyboardFocus = searchField
        updateCommandList()
        
        // Position in center
        window.setPosition(
            (stage.width - window.width) / 2,
            (stage.height - window.height) / 2
        )
    }
    
    fun hide() {
        isVisible = false
        window.isVisible = false
        stage.scrollFocus = null
        stage.keyboardFocus = null
    }

    fun showDefaultAt(x: Float, y: Float) {
        isVisible = true
        window.isVisible = true
        window.setPosition(x, y)
    }

    fun windowWidth(): Float = window.width

    fun windowHeight(): Float = window.height
    
    private fun updateCommandList() {
        val query = searchField.text
        val commands = commandPalette.searchCommands(query)
        
        commandList.setItems()

        if (query.isBlank()) {
            if (commands.isEmpty()) {
                commandList.setItems("No commands available")
                statusLabel.setText("Type to search commands...")
            } else {
                val items = commands.map { command ->
                    val icon = getIcon(command.icon)
                    "$icon ${command.name} - ${command.description}"
                }
                commandList.setItems(*items.toTypedArray())
                statusLabel.setText("${commands.size} commands available")
            }
        } else {
            if (commands.isEmpty()) {
                commandList.setItems("No commands found")
                statusLabel.setText("No matches for \"$query\"")
            } else {
                val items = commands.map { command ->
                    val icon = getIcon(command.icon)
                    "$icon ${command.name} - ${command.description}"
                }
                commandList.setItems(*items.toTypedArray())
                statusLabel.setText("${commands.size} commands found")
            }
        }
    }
    
    private fun executeSelectedCommand() {
        val selectedIndex = commandList.selectedIndex
        if (selectedIndex < 0) return
        
        val query = searchField.text
        val commands = commandPalette.searchCommands(query)
        
        if (selectedIndex < commands.size) {
            val command = commands[selectedIndex]
            val result = command.execute()
            
            if (result.success) {
                statusLabel.setText("OK: ${command.name} executed")
                commandPalette.addToHistory(command.id)
                hide()
            } else {
                statusLabel.setText("ERR: ${command.name} failed: ${result.message}")
            }
        }
    }

    private fun getIcon(iconName: String?): String {
        return when (iconName) {
            "export" -> "[EXP]"
            "import" -> "[IMP]"
            "tool" -> "[TOOL]"
            "edit" -> "[EDIT]"
            "view" -> "[VIEW]"
            "file" -> "[FILE]"
            else -> "[CMD]"
        }
    }
}
