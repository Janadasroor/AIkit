package com.jnd.aikit.database

import com.google.gson.annotations.SerializedName

/**
 * Data class representing a vector with its metadata for storage in Qdrant
 */
data class VectorData(
    @SerializedName("id")
    val id: String,

    @SerializedName("vector")
    val vector: FloatArray,

    @SerializedName("payload")
    val payload: VectorPayload
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VectorData

        if (id != other.id) return false
        if (!vector.contentEquals(other.vector)) return false
        if (payload != other.payload) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + payload.hashCode()
        return result
    }

    override fun toString(): String {
        return "VectorData(id='$id', vectorSize=${vector.size}, payload=$payload)"
    }
}

/**
 * Payload containing metadata about the vector
 */
data class VectorPayload(
    @SerializedName("type")
    val type: VectorType,

    @SerializedName("model")
    val model: ModelType,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("source")
    val source: String? = null,

    @SerializedName("imageUri")
    val imageUri: String? = null, // URI of the original image

    @SerializedName("tags")
    val tags: List<String> = emptyList(),

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("confidence")
    val confidence: Float? = null
)

/**
 * Types of vectors supported
 */
enum class VectorType {
    @SerializedName("image")
    IMAGE,

    @SerializedName("text")
    TEXT,

    @SerializedName("multimodal")
    MULTIMODAL
}

/**
 * ML models that generate vectors
 */
enum class ModelType {
    @SerializedName("clip_image")
    CLIP_IMAGE,

    @SerializedName("clip_text")
    CLIP_TEXT,

    @SerializedName("vit")
    VIT,

    @SerializedName("combined")
    COMBINED
}

/**
 * Search result containing vector data and similarity score
 */
data class SearchResult(
    @SerializedName("vectorData")
    val vectorData: VectorData,

    @SerializedName("score")
    val score: Float,

    @SerializedName("rank")
    val rank: Int
)

/**
 * Batch operation result
 */
data class BatchResult(
    @SerializedName("successful")
    val successful: Int,

    @SerializedName("failed")
    val failed: Int,

    @SerializedName("errors")
    val errors: List<String> = emptyList()
)

/**
 * Search parameters for similarity search
 */
data class SearchParameters(
    @SerializedName("limit")
    val limit: Int = 10,

    @SerializedName("scoreThreshold")
    val scoreThreshold: Float? = null,

    @SerializedName("vectorType")
    val vectorType: VectorType? = null,

    @SerializedName("modelType")
    val modelType: ModelType? = null,

    @SerializedName("tags")
    val tags: List<String>? = null,

    @SerializedName("timeRange")
    val timeRange: TimeRange? = null
)

/**
 * Time range for filtering search results
 */
data class TimeRange(
    @SerializedName("startTime")
    val startTime: Long,

    @SerializedName("endTime")
    val endTime: Long
)
