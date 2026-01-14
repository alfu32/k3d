package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3

class DraftLineStore {
    data class Segment(val start: Vector3, val end: Vector3)

    private val segments = mutableListOf<Segment>()

    fun addSegment(start: Vector3, end: Vector3) {
        segments.add(Segment(Vector3(start), Vector3(end)))
    }

    fun getSegments(): List<Segment> = segments
}
