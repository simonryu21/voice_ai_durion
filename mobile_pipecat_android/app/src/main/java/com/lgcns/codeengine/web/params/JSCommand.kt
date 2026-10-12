package com.lgcns.codeengine.web.params

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import org.json.JSONObject

@Serializable
data class JSCommand(
    val serviceName: String = "",
    val action: String = "",
    @Serializable(with = JsonAsStringSerializer::class)
    val params: String = JSONObject().toString(),
    val requestId: String = "",
)

object JsonAsStringSerializer :
    JsonTransformingSerializer<String>(tSerializer = String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonPrimitive(value = element.toString())
    }
}