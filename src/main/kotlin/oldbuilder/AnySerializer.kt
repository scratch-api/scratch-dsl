package de.thecommcraft.scratchdsl.oldbuilder

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

object AnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("Only works with JSON")
        val element = when (value) {
            is Int -> JsonPrimitive(value)
            is Double -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is List<*> -> JsonArray(value.map { it?.let { serializeToJsonElement(it) } ?: JsonNull })
            is Map<*, *> -> JsonObject(value.map { (k, v) ->
                k.toString() to (v?.let { serializeToJsonElement(it) } ?: JsonNull)
            }.toMap())
            else -> error("Unsupported type: $value")
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("Only works with JSON")
        return decodeFromJsonElement(jsonDecoder.decodeJsonElement())
    }

    private fun serializeToJsonElement(value: Any): JsonElement = when (value) {
        is Int -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { it?.let { serializeToJsonElement(it) } ?: JsonNull })
        is Map<*, *> -> JsonObject(value.map { (k, v) ->
            k.toString() to (v?.let { serializeToJsonElement(it) } ?: JsonNull)
        }.toMap())
        else -> error("Unsupported type: $value")
    }

    private fun decodeFromJsonElement(element: JsonElement): Any = when (element) {
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            else -> element.content
        }
        is JsonArray -> element.map { decodeFromJsonElement(it) }
        is JsonObject -> element.mapValues { decodeFromJsonElement(it.value) }
    }
}