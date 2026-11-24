package com.github.alfu32.sketch.model

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import java.lang.reflect.Modifier

/**
 * A DTO that keeps JSON members it does not know about, so documents written by newer
 * tools (Meshcraft adds `meshcraftMaterials`, `meshcraftMaterialId`, ...) open here and
 * are written back unchanged instead of failing to parse.
 */
interface JsonExtras {
    val extras: MutableMap<String, JsonValue>
}

/**
 * Document-level store for unknown JSON members. Keys are scoped strings such as
 * `doc`, `proto:<id>`, `seg:<proto>:<id>`, `face:<proto>:<id>` and `inst:<id>`.
 */
class SceneExtensions {
    private val scopes = linkedMapOf<String, MutableMap<String, JsonValue>>()

    fun clear() = scopes.clear()

    fun get(scope: String): Map<String, JsonValue> = scopes[scope].orEmpty()

    fun value(scope: String, key: String): JsonValue? = scopes[scope]?.get(key)

    fun set(scope: String, entries: Map<String, JsonValue>) {
        if (entries.isEmpty()) scopes.remove(scope) else scopes[scope] = LinkedHashMap(entries)
    }

    fun put(scope: String, key: String, value: JsonValue?) {
        if (value == null) {
            scopes[scope]?.remove(key)
        } else {
            scopes.getOrPut(scope) { linkedMapOf() }[key] = value
        }
    }

    fun removeScope(scope: String) {
        scopes.remove(scope)
    }

    companion object {
        const val DOC = "doc"
        fun proto(id: String) = "proto:$id"
        fun segment(protoId: String, id: String) = "seg:$protoId:$id"
        fun face(protoId: String, id: String) = "face:$protoId:$id"
        fun instance(id: String) = "inst:$id"
    }
}

/** libGDX [Json] that tolerates and round-trips unknown members of [JsonExtras] DTOs. */
class ExtrasJson : Json() {
    private val knownFields = HashMap<Class<*>, Set<String>>()

    init {
        setOutputType(JsonWriter.OutputType.json)
        setIgnoreUnknownFields(true)
    }

    private fun knownFieldsOf(type: Class<*>): Set<String> = knownFields.getOrPut(type) {
        val names = hashSetOf<String>()
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            current.declaredFields.forEach { field ->
                if (!Modifier.isStatic(field.modifiers) && !Modifier.isTransient(field.modifiers)) {
                    names.add(field.name)
                }
            }
            current = current.superclass
        }
        names
    }

    override fun readFields(`object`: Any, jsonMap: JsonValue) {
        if (`object` is JsonExtras) {
            val known = knownFieldsOf(`object`.javaClass)
            `object`.extras.clear()
            var child = jsonMap.child
            while (child != null) {
                if (child.name != null && child.name !in known && child.name != "class") {
                    `object`.extras[child.name] = copyOf(child)
                }
                child = child.next
            }
        }
        super.readFields(`object`, jsonMap)
    }

    override fun writeFields(`object`: Any) {
        super.writeFields(`object`)
        if (`object` is JsonExtras) {
            `object`.extras.forEach { (name, value) ->
                writer.name(name)
                writeJsonValue(value)
            }
        }
    }

    private fun writeJsonValue(value: JsonValue) {
        when {
            value.isObject -> {
                writer.`object`()
                var child = value.child
                while (child != null) {
                    writer.name(child.name)
                    writeJsonValue(child)
                    child = child.next
                }
                writer.pop()
            }
            value.isArray -> {
                writer.array()
                var child = value.child
                while (child != null) {
                    writeJsonValue(child)
                    child = child.next
                }
                writer.pop()
            }
            value.isString -> writer.value(value.asString())
            value.isBoolean -> writer.value(value.asBoolean())
            value.isNull -> writer.value(null)
            value.isLong -> writer.value(value.asLong())
            else -> writer.value(value.asDouble())
        }
    }

    private fun copyOf(value: JsonValue): JsonValue {
        val copy = when {
            value.isObject -> JsonValue(JsonValue.ValueType.`object`)
            value.isArray -> JsonValue(JsonValue.ValueType.array)
            value.isString -> JsonValue(value.asString())
            value.isBoolean -> JsonValue(value.asBoolean())
            value.isNull -> JsonValue(JsonValue.ValueType.nullValue)
            value.isLong -> JsonValue(value.asLong())
            else -> JsonValue(value.asDouble())
        }
        var child = value.child
        while (child != null) {
            val childCopy = copyOf(child)
            if (value.isObject) childCopy.setName(child.name)
            copy.addChild(childCopy)
            child = child.next
        }
        return copy
    }
}
