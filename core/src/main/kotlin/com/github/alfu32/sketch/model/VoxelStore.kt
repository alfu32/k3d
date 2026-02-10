package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Color

class VoxelStore {
    data class Key(val x: Int, val y: Int, val z: Int)
    data class Voxel(val x: Int, val y: Int, val z: Int, val color: Color)

    private val voxels = linkedMapOf<Key, Color>()
    private val selected = linkedSetOf<Key>()

    fun all(): List<Voxel> {
        return voxels.entries.map { (key, color) ->
            Voxel(key.x, key.y, key.z, Color(color))
        }
    }

    fun contains(x: Int, y: Int, z: Int): Boolean {
        return voxels.containsKey(Key(x, y, z))
    }

    fun contains(key: Key): Boolean {
        return voxels.containsKey(key)
    }

    fun colorAt(x: Int, y: Int, z: Int): Color? {
        return voxels[Key(x, y, z)]?.let { Color(it) }
    }

    fun colorAt(key: Key): Color? {
        return voxels[key]?.let { Color(it) }
    }

    fun set(x: Int, y: Int, z: Int, color: Color): Boolean {
        val key = Key(x, y, z)
        val previous = voxels[key]
        val next = Color(color)
        if (previous != null && sameColor(previous, next)) {
            return false
        }
        voxels[key] = next
        return true
    }

    fun remove(x: Int, y: Int, z: Int): Boolean {
        val key = Key(x, y, z)
        selected.remove(key)
        return voxels.remove(key) != null
    }

    fun clearSelection() {
        selected.clear()
    }

    fun selected(): Set<Key> {
        pruneSelection()
        return selected.toSet()
    }

    fun selectedCount(): Int {
        pruneSelection()
        return selected.size
    }

    fun isSelected(key: Key): Boolean {
        pruneSelection()
        return selected.contains(key)
    }

    fun addSelection(key: Key): Boolean {
        if (!voxels.containsKey(key)) {
            return false
        }
        return selected.add(key)
    }

    fun removeSelection(key: Key): Boolean {
        return selected.remove(key)
    }

    fun toggleSelection(key: Key): Boolean {
        if (!voxels.containsKey(key)) {
            return false
        }
        if (!selected.add(key)) {
            selected.remove(key)
        }
        return true
    }

    fun replaceSelection(keys: Collection<Key>): Int {
        selected.clear()
        keys.forEach { key ->
            if (voxels.containsKey(key)) {
                selected.add(key)
            }
        }
        return selected.size
    }

    fun deleteSelected(): Int {
        pruneSelection()
        if (selected.isEmpty()) {
            return 0
        }
        val keys = selected.toList()
        var removed = 0
        keys.forEach { key ->
            if (voxels.remove(key) != null) {
                removed++
            }
        }
        selected.clear()
        return removed
    }

    fun recolorAll(color: Color): Int {
        var changed = 0
        val next = Color(color)
        voxels.keys.toList().forEach { key ->
            val previous = voxels[key]
            if (previous == null || sameColor(previous, next)) {
                return@forEach
            }
            voxels[key] = Color(next)
            changed++
        }
        return changed
    }

    fun recolorSelected(color: Color): Int {
        pruneSelection()
        var changed = 0
        val next = Color(color)
        selected.forEach { key ->
            val previous = voxels[key]
            if (previous == null || sameColor(previous, next)) {
                return@forEach
            }
            voxels[key] = Color(next)
            changed++
        }
        return changed
    }

    fun moveSelected(dx: Int, dy: Int, dz: Int, copy: Boolean): Int {
        pruneSelection()
        if (selected.isEmpty() || (dx == 0 && dy == 0 && dz == 0)) {
            return 0
        }
        val selectedEntries = selected.mapNotNull { key ->
            voxels[key]?.let { key to Color(it) }
        }
        if (selectedEntries.isEmpty()) {
            selected.clear()
            return 0
        }
        if (!copy) {
            selectedEntries.forEach { (key, _) -> voxels.remove(key) }
        }
        val moved = linkedMapOf<Key, Color>()
        selectedEntries.forEach { (key, color) ->
            moved[Key(key.x + dx, key.y + dy, key.z + dz)] = Color(color)
        }
        var changed = 0
        moved.forEach { (key, color) ->
            val previous = voxels[key]
            if (previous == null || !sameColor(previous, color)) {
                changed++
            }
            voxels[key] = Color(color)
        }
        selected.clear()
        selected.addAll(moved.keys)
        return changed
    }

    fun clear() {
        voxels.clear()
        selected.clear()
    }

    private fun pruneSelection() {
        selected.removeIf { key -> !voxels.containsKey(key) }
    }

    private fun sameColor(a: Color, b: Color): Boolean {
        return kotlin.math.abs(a.r - b.r) <= 1e-6f &&
            kotlin.math.abs(a.g - b.g) <= 1e-6f &&
            kotlin.math.abs(a.b - b.b) <= 1e-6f &&
            kotlin.math.abs(a.a - b.a) <= 1e-6f
    }
}
