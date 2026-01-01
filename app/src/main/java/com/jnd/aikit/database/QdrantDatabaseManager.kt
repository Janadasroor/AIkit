package com.jnd.aikit.database

import android.content.Context
import android.util.Log
import com.jnd.aikit.database.vexdb.VectorDao
import com.jnd.aikit.database.vexdb.VectorEntity
import com.jnd.aikit.database.vexdb.VexDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * A local implementation of a vector database manager that mimics Qdrant's API.
 * Uses Room (VexDB) for persistence.
 */
class QdrantDatabaseManager(private val context: Context) {

    private var db: VexDatabase? = null
    private var dao: VectorDao? = null
    private var config: Config? = null

    data class Config(
        val host: String = "localhost",
        val port: Int = 6334,
        val enableLogging: Boolean = true
    )

    suspend fun initialize(config: Config): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            this@QdrantDatabaseManager.config = config
            db = VexDatabase.getInstance(context)
            dao = db?.vectorDao()
            Log.d("QdrantDB", "Local VexDB initialized")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("QdrantDB", "Failed to initialize: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Store a single vector in the database
     */
    suspend fun storeVector(vectorData: VectorData, collection: String = "images"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = VectorEntity(
                id = vectorData.id,
                collectionName = collection,
                vector = vectorData.vector,
                dimensions = vectorData.vector.size,
                vectorType = vectorData.payload.type,
                modelType = vectorData.payload.model,
                source = vectorData.payload.source,
                imageUri = vectorData.payload.imageUri,
                tags = vectorData.payload.tags,
                description = vectorData.payload.description,
                confidence = vectorData.payload.confidence,
                metadataJson = "{}", // We could serialize more metadata here
                createdAt = vectorData.payload.timestamp
            )
            dao?.insertVector(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("QdrantDB", "Failed to store vector: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Search for similar vectors using cosine similarity
     */
    suspend fun searchSimilar(
        queryVector: FloatArray,
        parameters: SearchParameters
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val currentDao = dao ?: return@withContext emptyList()
        val collection = "images" // Default collection

        // 1. Fetch candidate vectors from DB
        val candidates = currentDao.getVectorsForSimilaritySearch(
            collectionName = collection,
            vectorType = parameters.vectorType,
            modelType = parameters.modelType,
            limit = 1000 // Get some candidates to rank
        )

        // 2. Calculate cosine similarity and rank
        val results = candidates.map { entity ->
            val similarity = cosineSimilarity(queryVector, entity.vector)
            val vectorData = VectorData(
                id = entity.id,
                vector = entity.vector,
                payload = VectorPayload(
                    type = entity.vectorType,
                    model = entity.modelType,
                    timestamp = entity.createdAt,
                    source = entity.source,
                    imageUri = entity.imageUri,
                    tags = entity.tags,
                    description = entity.description,
                    confidence = entity.confidence
                )
            )
            SearchResult(vectorData, similarity, 0)
        }
        .filter { parameters.scoreThreshold == null || it.score >= parameters.scoreThreshold }
        .sortedByDescending { it.score }
        .take(parameters.limit)
        .mapIndexed { index, result -> result.copy(rank = index + 1) }

        results
    }

    /**
     * Cosine similarity between two vectors.
     * Assumes vectors might already be normalized.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        // If vectors are already normalized (norm approx 1.0),
        // then dot product IS the cosine similarity.
        // We check if they are normalized for better performance.
        if (Math.abs(normA - 1.0) < 0.01 && Math.abs(normB - 1.0) < 0.01) {
            return dotProduct
        }
        
        val denom = (sqrt(normA.toDouble()) * sqrt(normB.toDouble())).toFloat()
        return if (denom > 0) dotProduct / denom else 0f
    }

    suspend fun listCollections(): List<String> = withContext(Dispatchers.IO) {
        dao?.getAllCollections() ?: emptyList()
    }

    suspend fun getCollectionStats(collection: String): Map<String, Any> = withContext(Dispatchers.IO) {
        val count = dao?.getVectorCount(collection) ?: 0
        mapOf("count" to count)
    }

    suspend fun removeIncompatibleVectors(collection: String, modelType: ModelType, expectedDims: Int): Int = withContext(Dispatchers.IO) {
        dao?.deleteVectorsWithDimensionMismatch(collection, modelType, expectedDims) ?: 0
    }

    fun close() {
        // Room database closing is usually handled by the app lifecycle
    }
}
