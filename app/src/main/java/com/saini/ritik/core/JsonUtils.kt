package com.saini.ritik.core

import org.json.JSONObject
import org.json.JSONArray

/** Safely parse a JSON string into a Map<String,String> */
fun parseJsonMap(json: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    return try {
        val obj = JSONObject(json)
        obj.keys().forEach { key -> result[key] = obj.optString(key, "") }
        result
    } catch (_: Exception) { result }
}

/** Safely parse a JSON array string into a List<String> */
fun parseJsonList(json: String): List<String> =
    try {
        val arr = JSONArray(json)
        List(arr.length()) { arr.optString(it) }
    } catch (_: Exception) { emptyList() }

/** Build a JSON object string safely from a Map */
fun buildJsonObject(map: Map<String, Any?>): String =
    JSONObject(map.filterValues { it != null }).toString()