package com.jnd.aikit.database.vexdb

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jnd.aikit.database.ModelType
import com.jnd.aikit.database.VectorType

/**
 * Type converters for Room database
 */
class VectorTypeConverter {

    private val gson = Gson()

    @TypeConverter
    fun fromVectorType(value: VectorType): String {
        return value.name
    }

    @TypeConverter
    fun toVectorType(value: String): VectorType {
        return try {
            VectorType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            VectorType.IMAGE // Default fallback
        }
    }

    @TypeConverter
    fun fromModelType(value: ModelType): String {
        return value.name
    }

    @TypeConverter
    fun toModelType(value: String): ModelType {
        return try {
            ModelType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            ModelType.CLIP_IMAGE // Default fallback
        }
    }

    @TypeConverter
    fun fromFloatArray(value: FloatArray): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toFloatArray(value: String): FloatArray {
        return try {
            val type = object : TypeToken<FloatArray>() {}.type
            gson.fromJson(value, type)
        } catch (e: Exception) {
            floatArrayOf() // Return empty array on error
        }
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(value, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
