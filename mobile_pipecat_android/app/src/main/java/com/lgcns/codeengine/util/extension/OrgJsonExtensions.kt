package com.lgcns.codeengine.util.extension

import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.optStringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return MutableList(array.length()) { index ->
        array.optString(index, "")
    }.filter { it.isNotEmpty() }
}

fun JSONObject.putString(name: String, value: String?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}

fun JSONObject.putLong(name: String, value: Long?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}

fun JSONObject.putInt(name: String, value: Int?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}

fun JSONObject.putJsonObject(name: String, value: JSONObject?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}

fun JSONObject.getStringOrNull(name: String): String? {
    return try {
        val value = opt(name)
        if (value == null || value == JSONObject.NULL) {
            // { "name:" null } 인 경우 null 로 반환함
            null
        } else {
            optString(name)
        }
    } catch (exception: Exception) {
        null
    }
}

fun JSONArray.sequenceObject(): Sequence<JSONObject> =
    (0 until length()).asSequence().mapNotNull { optJSONObject(it) }

fun JSONArray.sequenceArray(): Sequence<JSONArray> =
    (0 until length()).asSequence().mapNotNull { optJSONArray(it) }

fun JSONObject.putStringList(key: String, value: List<String>?) {
    if (value != null) {
        put(key, JSONArray(value))
    } else {
        put(key, JSONObject.NULL)
    }
}

@JvmInline
value class JsonArrayString(val json: String)

fun JSONArray.toJsonString(): JsonArrayString = JsonArrayString(toString())

fun JsonArrayString.toJsonArray() = JSONArray(json)

fun JSONArray?.toStringList(): List<String> {
    if (this == null || length() == 0) return emptyList()
    val result = mutableListOf<String>()
    for (index in 0 until length()) {
        result.add(optString(index, ""))
    }
    return result
}

fun <T, R> List<T>.mapJSONArray(transform: (T) -> R): JSONArray {
    val destination = JSONArray()
    for (item in this)
        destination.put(transform(item))
    return destination
}

fun JSONObject.toJsonString() = toString()