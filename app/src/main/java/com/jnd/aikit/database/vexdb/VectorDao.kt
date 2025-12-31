package com.jnd.aikit.database.vexdb

import androidx.room.*
import com.jnd.aikit.database.ModelType
import com.jnd.aikit.database.VectorType

/**
 * Data Access Object for Vector operations in VexDB
 */
@Dao
interface VectorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVector(vector: VectorEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVectors(vectors: List<VectorEntity>): List<Long>

    @Update
    suspend fun updateVector(vector: VectorEntity)

    @Delete
    suspend fun deleteVector(vector: VectorEntity)

    @Query("DELETE FROM vectors WHERE id = :vectorId")
    suspend fun deleteVectorById(vectorId: String): Int

    @Query("SELECT * FROM vectors WHERE id = :vectorId")
    suspend fun getVectorById(vectorId: String): VectorEntity?

    @Query("SELECT * FROM vectors WHERE collectionName = :collectionName")
    suspend fun getVectorsByCollection(collectionName: String): List<VectorEntity>

    @Query("SELECT * FROM vectors WHERE vectorType = :vectorType")
    suspend fun getVectorsByType(vectorType: VectorType): List<VectorEntity>

    @Query("SELECT * FROM vectors WHERE modelType = :modelType")
    suspend fun getVectorsByModel(modelType: ModelType): List<VectorEntity>

    @Query("SELECT COUNT(*) FROM vectors WHERE collectionName = :collectionName")
    suspend fun getVectorCount(collectionName: String): Int

    @Query("SELECT DISTINCT collectionName FROM vectors")
    suspend fun getAllCollections(): List<String>

    @Query("SELECT * FROM vectors WHERE collectionName = :collectionName ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getVectorsPaged(collectionName: String, limit: Int, offset: Int): List<VectorEntity>

    // Note: Advanced similarity search will be implemented in the repository layer
    // For now, we'll use basic filtering and ordering
    @Query("""
        SELECT * FROM vectors
        WHERE collectionName = :collectionName
          AND (:vectorType IS NULL OR vectorType = :vectorType)
          AND (:modelType IS NULL OR modelType = :modelType)
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    suspend fun getVectorsForSimilaritySearch(
        collectionName: String,
        vectorType: VectorType?,
        modelType: ModelType?,
        limit: Int
    ): List<VectorEntity>

    @Query("DELETE FROM vectors WHERE collectionName = :collectionName")
    suspend fun deleteCollection(collectionName: String): Int

    @Query("DELETE FROM vectors")
    suspend fun deleteAllVectors(): Int

    @Query("DELETE FROM vectors WHERE collectionName = :collectionName AND modelType = :modelType AND dimensions != :expectedDimensions")
    suspend fun deleteVectorsWithDimensionMismatch(collectionName: String, modelType: ModelType, expectedDimensions: Int): Int

    // Custom query for tag-based filtering
    @Query("""
        SELECT * FROM vectors
        WHERE collectionName = :collectionName
          AND (tagsJson LIKE '%' || :tag || '%' OR :tag IS NULL)
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    suspend fun getVectorsByTag(
        collectionName: String,
        tag: String?,
        limit: Int
    ): List<VectorEntity>

    // Statistics queries
    @Query("""
        SELECT
            collectionName,
            COUNT(*) as count,
            AVG(dimensions) as avgDimensions,
            MIN(createdAt) as oldestVector,
            MAX(createdAt) as newestVector
        FROM vectors
        GROUP BY collectionName
    """)
    suspend fun getCollectionStats(): List<CollectionStats>
}

/**
 * Data class for search results with similarity scores
 */
data class VectorWithSimilarity(
    @Embedded val vector: VectorEntity,
    val similarity_score: Float
)

/**
 * Data class for collection statistics
 */
data class CollectionStats(
    val collectionName: String,
    val count: Int,
    val avgDimensions: Float,
    val oldestVector: Long,
    val newestVector: Long
)
