package com.github.alfu32.sketch.input

enum class SnapType(val label: String, val priority: Int) {
    NONE("Free", 0),
    GRID("Grid", 3),
    ENDPOINT("Endpoint", 4),
    MIDPOINT("Midpoint", 4),
    LINE("Line", 2),
    GRID_LINE("Grid Line", 2),
    GRID_GUIDE("Grid Guide", 3),
    AXIS_GUIDE("Axis Guide", 3),
    FACE("Face", 1)
}
