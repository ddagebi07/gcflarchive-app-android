package kr.co.gcflarchive.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Lenient JSON reading with org.json-like semantics (missing / wrong type → default),
 * so the parsing rules ported from the Android app read the same on every platform.
 * Null values read as missing.
 */
class JObject(val raw: JsonObject = JsonObject(emptyMap())) {
    fun has(key: String): Boolean = raw.containsKey(key)
    fun isNull(key: String): Boolean = raw[key] == null || raw[key] is JsonNull
    fun keys(): List<String> = raw.keys.toList()
    fun opt(key: String): JsonElement? = raw[key]?.takeIf { it !is JsonNull }

    fun optString(key: String, default: String = ""): String = when (val v = opt(key)) {
        is JsonPrimitive -> v.contentOrNull ?: default
        is JsonObject, is JsonArray -> v.toString()
        else -> default
    }

    /** Trimmed, null when missing or blank. */
    fun str(key: String): String? = optString(key).trim().ifEmpty { null }

    fun optDouble(key: String, default: Double = Double.NaN): Double {
        val p = opt(key) as? JsonPrimitive ?: return default
        return p.doubleOrNull ?: p.contentOrNull?.trim()?.toDoubleOrNull() ?: default
    }

    fun optLong(key: String, default: Long = 0): Long {
        val p = opt(key) as? JsonPrimitive ?: return default
        return p.longOrNull ?: p.contentOrNull?.trim()?.toDoubleOrNull()?.toLong() ?: default
    }

    fun optInt(key: String, default: Int = 0): Int = optLong(key, default.toLong()).toInt()

    fun optBoolean(key: String, default: Boolean = false): Boolean {
        val p = opt(key) as? JsonPrimitive ?: return default
        return p.booleanOrNull ?: when (p.contentOrNull?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> default
        }
    }

    fun optJSONObject(key: String): JObject? = (opt(key) as? JsonObject)?.let(::JObject)
    fun optJSONArray(key: String): JArray? = (opt(key) as? JsonArray)?.let(::JArray)

    override fun toString(): String = raw.toString()
}

class JArray(val raw: JsonArray = JsonArray(emptyList())) {
    fun length(): Int = raw.size
    fun optJSONObject(i: Int): JObject? = (raw.getOrNull(i) as? JsonObject)?.let(::JObject)
    fun optString(i: Int): String = (raw.getOrNull(i) as? JsonPrimitive)?.contentOrNull ?: ""
    fun optInt(i: Int): Int = (raw.getOrNull(i) as? JsonPrimitive)?.let { it.longOrNull?.toInt() ?: it.contentOrNull?.toDoubleOrNull()?.toInt() } ?: 0

    fun objects(): List<JObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    fun ints(): List<Int> = (0 until length()).map(::optInt)

    /** Non-blank string values (numbers included as text). */
    fun strings(): List<String> = raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() && s != "null" } }
}

fun JArray?.objects(): List<JObject> = this?.objects().orEmpty()
fun JArray?.strings(): List<String> = this?.strings().orEmpty()

internal val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun parseObject(text: String): JObject =
    runCatching { lenientJson.parseToJsonElement(text) as? JsonObject }.getOrNull()?.let(::JObject) ?: JObject()

fun parseArray(text: String): JArray =
    runCatching { lenientJson.parseToJsonElement(text) as? JsonArray }.getOrNull()?.let(::JArray) ?: JArray()

/** Builds request bodies: values may be String, Number, Boolean, null, List, Map, JsonElement. */
fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (k, v) -> k to toElement(v) })

fun toElement(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is JObject -> v.raw
    is JArray -> v.raw
    is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to toElement(it.value) })
    is Iterable<*> -> JsonArray(v.map(::toElement))
    else -> JsonPrimitive(v.toString())
}
