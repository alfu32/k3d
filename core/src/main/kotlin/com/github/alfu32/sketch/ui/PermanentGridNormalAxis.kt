package com.github.alfu32.sketch.ui

enum class PermanentGridNormalAxis(
    val prefValue: String,
    private val label: String
) {
    Y_UP("y_up", "Y (up, default)"),
    X_RIGHT("x_right", "X (right)"),
    Z_BACK("z_back", "Z (back)");

    override fun toString(): String = label

    companion object {
        fun fromPrefValue(value: String?): PermanentGridNormalAxis {
            return values().firstOrNull { it.prefValue == value } ?: Y_UP
        }
    }
}
