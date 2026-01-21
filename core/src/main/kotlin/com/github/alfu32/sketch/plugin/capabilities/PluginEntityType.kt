package com.github.alfu32.sketch.plugin.capabilities

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.plugin.PluginContext

interface PluginEntityType {
    val id: String
    val name: String
    val icon: String
    val isSelectable: Boolean
    val isEditable: Boolean

    fun createEntity(context: PluginContext, properties: Map<String, Any>): PluginEntity
    fun getPropertyDefinitions(): List<EntityProperty>
    fun getDefaultProperties(): Map<String, Any>
    fun canConnectTo(entityType: String): Boolean = false
}

data class EntityProperty(
    val id: String,
    val name: String,
    val type: PropertyType,
    val defaultValue: Any,
    val isRequired: Boolean = false
)

enum class PropertyType {
    STRING, NUMBER, BOOLEAN, COLOR, VECTOR3, ENUM, REFERENCE
}

// Simple plugin entity interface
interface PluginEntity {
    val id: String
    val type: String
    val properties: Map<String, Any>
    fun toWorld(context: PluginContext): Any?
    fun toLocal(context: PluginContext): Any?
}