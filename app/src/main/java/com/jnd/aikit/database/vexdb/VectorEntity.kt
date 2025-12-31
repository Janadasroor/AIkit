package com.jnd.aikit.database.vexdb

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.jnd.aikit.database.ModelType
import com.jnd.aikit.database.VectorType

/**
 * Room entity for storing vector data in VexDB
 */
@Entity(tableName = "vectors")
@TypeConverters(VectorTypeConverter::class)
data class VectorEntity(
    @PrimaryKey
    val id: String,

    // Vector data stored as JSON string for flexibility
    val vectorData: String, // JSON array of floats

    // Vector dimensions for validation
    val dimensions: Int,

    // Metadata
    val vectorType: VectorType,
    val modelType: ModelType,
    val source: String?,
    val imageUri: String?, // URI of the original image
    val description: String?,

    // Tags stored as JSON string
    val tagsJson: String, // JSON array of strings

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Processing info
    val confidence: Float? = null,
    val collectionName: String = "default"
)
