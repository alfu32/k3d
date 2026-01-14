package com.github.alfu32.sketch.input

enum class SnapType(val label: String, val priority: Int) {
    NONE("Free", 0),
    GRID("Grid", 3),
    ENDPOINT("Endpoint", 3),
    MIDPOINT("Midpoint", 2),
    LINE("Line", 1),
    GRID_LINE("Grid Line", 1),
    GRID_GUIDE("Grid Guide", 2),
    AXIS_GUIDE("Axis Guide", 2)
}
