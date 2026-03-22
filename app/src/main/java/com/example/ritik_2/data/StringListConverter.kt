package com.example.ritik_2.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StringListConverter {
    private val gson = Gson()

    // List<String> → String (stored in DB as comma-separated)
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return value?.joinToString(",") ?: ""
    }

    // String → List<String> (read from DB)
    // KEPT ONLY ONE — removed duplicate fromJsonString which caused the conflict
    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList()
        else value.split(",").filter { it.isNotEmpty() }
    }

    // NOTE: fromJsonString and fromListToJsonString were REMOVED.
    // They caused "Multiple functions define the same conversion" error
    // because both fromJsonString and toStringList had signature: String → List<String>
    // Room cannot have two @TypeConverters with the same input and output type.
    //
    // If you need JSON-based list storage elsewhere, use a separate converter class
    // with a different type, e.g. List<SomeObject> not List<String>.
}