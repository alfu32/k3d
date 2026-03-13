package com.github.alfu32.sketch.tools

import com.badlogic.gdx.math.Vector3

internal fun appendPathFeedbackLines(
    out: MutableList<Pair<Vector3, Vector3>>,
    points: List<Vector3>,
    close: Boolean = false
) {
    if (points.size < 2) {
        return
    }
    for (i in 0 until points.lastIndex) {
        out += points[i] to points[i + 1]
    }
    if (close) {
        out += points.last() to points.first()
    }
}

internal fun pathFeedbackLines(
    points: List<Vector3>,
    close: Boolean = false
): List<Pair<Vector3, Vector3>> {
    val out = mutableListOf<Pair<Vector3, Vector3>>()
    appendPathFeedbackLines(out, points, close)
    return out
}
