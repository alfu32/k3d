package com.github.alfu32.sketch

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input

object InputModifiers {
    @Volatile
    var androidCtrlMetaActive: Boolean = false

    @Volatile
    var androidCtrlKeyDownCount: Int = 0

    fun isCtrlPressed(): Boolean {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) ||
            Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT) ||
            Gdx.input.isKeyPressed(Input.Keys.SYM) ||
            androidCtrlMetaActive ||
            androidCtrlKeyDownCount > 0
    }
}
