package com.example.ritik_2.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StringListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return if (value == null) {
            ""
        } else {
            value.joinToString(",")
        }
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) {
            emptyList()
        } else {
            value.split(",").filter { it.isNotEmpty() }
        }
    }

    // For complex JSON objects (if needed)
    @TypeConverter
    fun fromJsonString(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromListToJsonString(list: List<String>): String {
        return gson.toJson(list)
    }
}