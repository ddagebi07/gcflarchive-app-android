package kr.co.gcflarchive.admin.features

import org.json.JSONArray
import org.json.JSONObject

/** Small JSON helpers shared by the feature screens. */
fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { i -> opt(i)?.toString()?.takeIf { it.isNotBlank() && it != "null" } }

fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key).trim()

/** Flattens an object to display strings (nested values as compact JSON). */
fun JSONObject.flat(): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    keys().forEach { k ->
        val v = opt(k)
        out[k] = when (v) {
            null, JSONObject.NULL -> ""
            is JSONArray -> v.strings().joinToString(", ")
            else -> v.toString()
        }
    }
    return out
}

fun jsonArray(values: Collection<String>): JSONArray = JSONArray().apply { values.forEach { put(it) } }
