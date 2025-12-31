package com.jnd.aikit.database.vexdb

import android.util.Log
import com.google.gson.Gson
import com.jnd.aikit.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Repository for VexDB operations with cosine similarity search
 */
class VexRepository(private val database: VexDatabase) {

    private val vectorDao = database.vectorDao()
    private val gson = Gson()

    companion object {
        // CLIP's learned temperature parameter (logit_scale)
        private const val LOGIT_SCALE = 100f
    }

    /**
     * Initialize the database
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Database is initialized by Room automatically
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Store a single vector
     */
    suspend fun storeVector(vectorData: VectorData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = vectorData.toEntity()
            vectorDao.insertVector(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Store multiple vectors in batch
     */
    suspend fun storeVectorsBatch(vectors: List<VectorData>): Result<BatchResult> = withContext(Dispatchers.IO) {
        try {
            val entities = vectors.map { it.toEntity() }
            vectorDao.insertVectors(entities)

            Result.success(BatchResult(vectors.size, 0, emptyList()))
        } catch (e: Exception) {
            Result.success(BatchResult(0, vectors.size, listOf(e.message ?: "Unknown error")))
        }
    }

    /**
     * Search for similar vectors using cosine similarity with CLIP logit scaling
     */
    suspend fun searchSimilar(
        queryVector: FloatArray,
        parameters: SearchParameters = SearchParameters()
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val collectionName = getCollectionNameForType(parameters.vectorType ?: VectorType.IMAGE)

            // Get candidate vectors from database
            val candidates = vectorDao.getVectorsForSimilaritySearch(
                collectionName = collectionName,
                vectorType = parameters.vectorType,
                modelType = parameters.modelType,
                limit = parameters.limit * 3 // Get more candidates for better similarity
            )

            Log.d("VexDB", "Found ${candidates.size} candidate vectors in collection '$collectionName' for search (vectorType: ${parameters.vectorType}, modelType: ${parameters.modelType})")

            // Check query vector normalization
            val queryNorm = sqrt(queryVector.sumOf { (it * it).toDouble() }.toFloat())
            Log.d("VexDB", "Query vector norm: $queryNorm (should be ~1.0 for normalized vectors)")
            if (kotlin.math.abs(queryNorm - 1f) > 0.01f) {
                Log.w("VexDB", "Query vector is NOT normalized! This may cause incorrect similarity scores.")
            }

            // Calculate cosine similarity for each candidate
            val similarities = candidates.mapNotNull { entity ->
                try {
                    val vector = entity.getVectorArray()
                    if (vector.size != queryVector.size) {
                        Log.w("VexDB", "Vector size mismatch for ${entity.id}: expected ${queryVector.size}, got ${vector.size}")
                        return@mapNotNull null
                    }

                    // Calculate raw cosine similarity
                    val rawSimilarity = cosineSimilarity(queryVector, vector)
                    
                    // Check stored vector normalization
                    val storedNorm = sqrt(vector.sumOf { (it * it).toDouble() }.toFloat())
                    if (kotlin.math.abs(storedNorm - 1f) > 0.01f) {
                        Log.w("VexDB", "Stored vector ${entity.id} is NOT normalized! norm=$storedNorm")
                    }

                    // Use raw cosine similarity for all models
                    // Note: For CLIP models, the logit_scale is only relevant when computing
                    // probabilities with softmax. For search/ranking, raw similarity is better.
                    // Match logit scaling from Python: similarity = dot_product * 100
                    val scaledScore = rawSimilarity * LOGIT_SCALE

                    Log.d("VexDB", "Similarity for ${entity.id}: raw=$rawSimilarity, logit=$scaledScore, threshold=${parameters.scoreThreshold}")

                    // Apply threshold filter on RAW similarity
                    val thresholdToUse = parameters.scoreThreshold ?: 0.01f

                    if (rawSimilarity >= thresholdToUse) {
                        val vectorData = entity.toVectorData()
                        SearchResult(
                            vectorData = vectorData,
                            score = scaledScore, // Use scaled score for sorting and softmax
                            rank = 0
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("VexDB", "Error calculating similarity for ${entity.id}: ${e.message}", e)
                    null
                }
            }

            // Sort by scaled similarity
            val topCandidates = similarities
                .sortedByDescending { it.score }
                .take(parameters.limit)

            // Apply softmax to top results as in the Python reference to get probabilities
            val finalResults = if (topCandidates.size > 1) {
                applySoftmax(topCandidates)
            } else {
                topCandidates
            }

            // Assign ranks and return
            finalResults.mapIndexed { index, result ->
                result.copy(rank = index + 1)
            }

        } catch (e: Exception) {
            Log.e("VexDB", "Error in searchSimilar", e)
            emptyList()
        }
    }

    /**
     * Apply softmax normalization to search results
     * Converts scaled logits to probabilities
     */
    private fun applySoftmax(results: List<SearchResult>): List<SearchResult> {
        if (results.isEmpty()) return results

        val scores = results.map { it.score.toDouble() }
        val maxScore = scores.maxOrNull() ?: 0.0

        // Subtract max for numerical stability
        val expScores = scores.map { exp(it - maxScore) }
        val sumExp = expScores.sum()

        // Calculate probabilities
        val probabilities = expScores.map { (it / sumExp).toFloat() }

        return results.mapIndexed { index, result ->
            result.copy(score = probabilities[index])
        }
    }

    /**
     * Get vector by ID
     */
    suspend fun getVector(collectionName: String, vectorId: String): VectorData? = withContext(Dispatchers.IO) {
        try {
            val entity = vectorDao.getVectorById(vectorId)
            entity?.toVectorData()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete vector by ID
     */
    suspend fun deleteVector(collectionName: String, vectorId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            vectorDao.deleteVectorById(vectorId) > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Remove vectors that don't match the expected dimension
     */
    suspend fun removeIncompatibleVectors(collectionName: String, modelType: ModelType, expectedDimensions: Int): Int = withContext(Dispatchers.IO) {
        try {
            vectorDao.deleteVectorsWithDimensionMismatch(collectionName, modelType, expectedDimensions)
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get collection statistics
     */
    suspend fun getCollectionStats(collectionName: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        try {
            val stats = vectorDao.getCollectionStats()
            val collectionStat = stats.find { it.collectionName == collectionName }

            collectionStat?.let {
                mapOf(
                    "vectors_count" to it.count,
                    "avg_dimensions" to it.avgDimensions,
                    "oldest_vector" to it.oldestVector,
                    "newest_vector" to it.newestVector
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * List all collections
     */
    suspend fun listCollections(): List<String> = withContext(Dispatchers.IO) {
        try {
            vectorDao.getAllCollections()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if collection exists
     */
    suspend fun collectionExists(collectionName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            vectorDao.getVectorCount(collectionName) > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Close the database
     */
    fun close() {
        try {
            database.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
    }

    // Helper functions

    /**
     * Calculate cosine similarity between two vectors
     * For CLIP models, vectors are already L2-normalized, so we can use dot product directly
     * Returns normalized similarity in range [-1, 1]
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray, isNormalized: Boolean = true): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            if (!isNormalized) {
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
        }

        // For normalized vectors, cosine similarity = dot product
        if (isNormalized) {
            // Verify normalization (for debugging)
            val normCheckA = sqrt(a.sumOf { (it * it).toDouble() }.toFloat())
            val normCheckB = sqrt(b.sumOf { (it * it).toDouble() }.toFloat())
            
            if (kotlin.math.abs(normCheckA - 1f) > 0.01f || kotlin.math.abs(normCheckB - 1f) > 0.01f) {
                Log.w("VexDB", "Vectors not properly normalized! normA=$normCheckA, normB=$normCheckB - falling back to full cosine calculation")
                return cosineSimilarity(a, b, isNormalized = false)
            }
            
            return dotProduct
        }

        val magnitude = sqrt(normA) * sqrt(normB)
        return if (magnitude > 0f) dotProduct / magnitude else 0f
    }

    /**
     * Get collection name for vector type
     */
    private fun getCollectionNameForType(vectorType: VectorType): String {
        return when (vectorType) {
            VectorType.IMAGE -> "images"
            VectorType.TEXT -> "text"
            VectorType.MULTIMODAL -> "multimodal"
        }
    }

    /**
     * Convert VectorData to VectorEntity
     */
    private fun VectorData.toEntity(): VectorEntity {
        return VectorEntity(
            id = id,
            vectorData = gson.toJson(vector),
            dimensions = vector.size,
            vectorType = payload.type,
            modelType = payload.model,
            source = payload.source,
            imageUri = payload.imageUri,
            description = payload.description,
            tagsJson = gson.toJson(payload.tags),
            confidence = payload.confidence,
            collectionName = getCollectionNameForType(payload.type)
        )
    }

    /**
     * Convert VectorEntity to VectorData
     */
    private fun VectorEntity.toVectorData(): VectorData {
        val vectorArray = getVectorArray()
        val tags = try {
            gson.fromJson(tagsJson, Array<String>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList<String>()
        }

        return VectorData(
            id = id,
            vector = vectorArray,
            payload = VectorPayload(
                type = vectorType,
                model = modelType,
                timestamp = createdAt,
                source = source,
                imageUri = imageUri,
                tags = tags,
                description = description,
                confidence = confidence
            )
        )
    }

    /**
     * Get vector array from entity
     */
    private fun VectorEntity.getVectorArray(): FloatArray {
        return try {
            gson.fromJson(vectorData, Array<Float>::class.java)?.toFloatArray() ?: floatArrayOf()
        } catch (e: Exception) {
            floatArrayOf()
        }
    }
}