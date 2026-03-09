package com.github.alfu32.sketch.ui

import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.kotcrab.vis.ui.widget.VisImageTextButton

/**
 * TeaVM-safe wrapper for VisImageTextButton.
 *
 * VisUI 1.5.7's VisImageTextButton#toString() uses a Label#getText() signature
 * that is incompatible with libGDX 1.14.0. Overriding it here avoids linking
 * to that method in web builds while preserving desktop behavior.
 */
class AppImageTextButton(text: String, icon: Drawable?) : VisImageTextButton(text, icon) {
    override fun toString(): String = "AppImageTextButton"
}
