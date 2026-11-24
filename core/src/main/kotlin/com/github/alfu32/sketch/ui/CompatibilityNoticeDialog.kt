package com.github.alfu32.sketch.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisScrollPane
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.kotcrab.vis.ui.widget.VisTextField
import com.kotcrab.vis.ui.widget.VisWindow

/**
 * Blocking notice shown once per release: k3d is converging on Meshcraft
 * (https://github.com/alfu32/meshcraft), what changed, and whether it breaks anything.
 */
object CompatibilityNoticeDialog {
    private const val PREFS = "k3d-runtime"
    private const val ACK_KEY_PREFIX = "meshcraft_compat_notice_ack_"

    /** Bump when the notice text changes materially so users see it again. */
    private const val NOTICE_REVISION = 1

    data class Link(val label: String, val url: String)

    enum class Impact(val label: String, val color: Color) {
        NON_BREAKING("Not breaking", Color(0.45f, 0.85f, 0.45f, 1f)),
        BREAKING("BREAKING", Color(1f, 0.45f, 0.35f, 1f)),
        PLANNED("Planned", Color(0.95f, 0.8f, 0.3f, 1f))
    }

    data class Change(val title: String, val detail: String, val impact: Impact)

    val links = listOf(
        Link("Meshcraft on GitHub", "https://github.com/alfu32/meshcraft"),
        Link("Meshcraft releases (latest)", "https://github.com/alfu32/meshcraft/releases/latest"),
        Link("Compatibility contract", "https://github.com/alfu32/meshcraft/blob/work/docs/rewrite/compatibility.md"),
        Link("Interchange formats", "https://github.com/alfu32/meshcraft/blob/work/docs/interchange.md")
    )

    val changes = listOf(
        Change(
            "Meshcraft files now open",
            "Files saved by Meshcraft (materials, procedural objects, scripts) used to fail to open " +
                "without any message. They now load.",
            Impact.NON_BREAKING
        ),
        Change(
            "Unknown data is preserved on save",
            "Data k3d does not understand yet (for example Meshcraft materials) is kept and written back " +
                "unchanged. Existing k3d files are read and written exactly as before; the file format " +
                "stays schema 17. Files saved this way and containing Meshcraft data cannot be opened by " +
                "older k3d versions.",
            Impact.NON_BREAKING
        ),
        Change(
            "Materials are not shown or editable yet",
            "Meshcraft materials load and round-trip, but k3d still displays and edits plain face colors. " +
                "Editing in k3d can leave a Meshcraft material id out of step with the new color.",
            Impact.PLANNED
        ),
        Change(
            "Textures, shaders and shader objects",
            "Not supported yet. Their data is preserved but not rendered.",
            Impact.PLANNED
        ),
        Change(
            "Icons",
            "Meshcraft's icon set is planned for a later release. No UI layout changes.",
            Impact.PLANNED
        )
    )

    private fun prefs() = try {
        Gdx.app.getPreferences(PREFS)
    } catch (_: Exception) {
        null
    }

    private fun ackKey(version: String) = "$ACK_KEY_PREFIX${version}_r$NOTICE_REVISION"

    fun shouldShow(version: String): Boolean {
        if (System.getenv("K3D_SKIP_NOTICE") != null) return false
        return prefs()?.getBoolean(ackKey(version), false) != true
    }

    fun show(stage: Stage, version: String, onClosed: () -> Unit = {}) {
        val blocker = buildBlocker(stage)
        val window = VisWindow("Octodraw $version: moving towards Meshcraft compatibility", true).apply {
            isModal = true
            isMovable = false
            isResizable = false
            setKeepWithinParent(true)
        }

        val content = VisTable().apply { defaults().left().pad(4f) }
        content.add(
            VisLabel(
                "Octodraw (k3d) is being brought to compatibility and feature parity with Meshcraft, " +
                    "its Rust successor. Both use the same .octd project format. Here is what this " +
                    "version changes:"
            ).apply { setWrap(true) }
        ).growX().row()

        changes.forEach { change ->
            val row = VisTable().apply { defaults().left().top().pad(2f) }
            row.add(VisLabel("[${change.impact.label}]").apply { color = change.impact.color }).width(110f)
            val text = VisTable().apply { defaults().left().growX() }
            text.add(VisLabel(change.title).apply { color = Color.WHITE }).row()
            text.add(VisLabel(change.detail).apply { setWrap(true) }).growX().row()
            row.add(text).growX()
            content.add(row).growX().padTop(6f).row()
        }

        content.add(VisLabel("Summary: nothing in this version breaks existing files or workflows.").apply {
            setWrap(true)
            color = Impact.NON_BREAKING.color
        }).growX().padTop(10f).row()

        content.add(VisLabel("Links (click Open, or select the address and copy it):")).padTop(10f).row()
        links.forEach { link ->
            val row = VisTable().apply { defaults().pad(2f) }
            row.add(VisLabel(link.label)).width(190f).left()
            val field = VisTextField(link.url).apply {
                // Selectable and copyable (Ctrl+C), but not editable.
                setTextFieldListener(null)
                setProgrammaticChangeEvents(false)
                isDisabled = false
                addListener(object : InputListener() {
                    override fun keyTyped(event: InputEvent?, character: Char): Boolean {
                        // Swallow edits; Ctrl+C is handled in keyDown and is not a typed character.
                        text = link.url
                        return false
                    }
                })
            }
            row.add(field).growX().minWidth(300f)
            val open = VisTextButton("Open")
            open.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    try {
                        Gdx.net.openURI(link.url)
                    } catch (_: Exception) {
                        copy(link.url)
                    }
                }
            })
            val copy = VisTextButton("Copy")
            copy.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    copy(link.url)
                    copy.setText("Copied")
                }
            })
            row.add(open)
            row.add(copy)
            content.add(row).growX().row()
        }

        val scroll = VisScrollPane(content).apply {
            setFadeScrollBars(false)
            setScrollingDisabled(true, false)
        }

        val ok = VisTextButton("I understand, continue")
        ok.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                prefs()?.let {
                    it.putBoolean(ackKey(version), true)
                    it.flush()
                }
                window.remove()
                blocker.remove()
                onClosed()
            }
        })

        val sw = stage.viewport.worldWidth.takeIf { it > 0f } ?: Gdx.graphics.width.toFloat()
        val sh = stage.viewport.worldHeight.takeIf { it > 0f } ?: Gdx.graphics.height.toFloat()
        val width = (sw * 0.8f).coerceIn(360f, 820f)
        val height = (sh * 0.8f).coerceIn(280f, 640f)
        window.add(scroll).width(width).height(height - 90f).pad(8f).row()
        window.add(ok).right().pad(8f)
        window.pack()
        window.setPosition((sw - window.width) * 0.5f, (sh - window.height) * 0.5f)

        stage.addActor(blocker)
        stage.addActor(window)
        stage.keyboardFocus = ok
        stage.scrollFocus = scroll
    }

    private fun copy(text: String) {
        try {
            Gdx.app.clipboard.contents = text
        } catch (_: Exception) {
            // Clipboard can be unavailable in sandboxed runtimes; the address stays selectable.
        }
    }

    /** Full-stage dimmed actor that swallows pointer input so the viewport cannot be used meanwhile. */
    private fun buildBlocker(stage: Stage): Actor {
        val sw = stage.viewport.worldWidth.takeIf { it > 0f } ?: Gdx.graphics.width.toFloat()
        val sh = stage.viewport.worldHeight.takeIf { it > 0f } ?: Gdx.graphics.height.toFloat()
        return VisTable().apply {
            setSize(sw, sh)
            setFillParent(true)
            background(VisUI.getSkin().newDrawable("white", 0f, 0f, 0f, 0.55f))
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
            addListener(object : InputListener() {
                override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) = true
                override fun scrolled(event: InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float) = true
            })
        }
    }
}
