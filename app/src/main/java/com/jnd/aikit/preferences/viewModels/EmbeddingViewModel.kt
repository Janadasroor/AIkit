package com.jnd.aikit.preferences.viewModels

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jnd.aikit.database.*
import com.jnd.aikit.embedding.CLIPImageEncoder
import com.jnd.aikit.embedding.CLIPTextEncoder
import com.jnd.aikit.embedding.ViTEncoder
import com.jnd.aikit.model.ModelManager
import com.jnd.aikit.model.ModelType as ModelTypeNew
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class EmbeddingViewModel(application: Application) : AndroidViewModel(application) {
    private val clipImageEncoder = CLIPImageEncoder(application)
    private val clipTextEncoder = CLIPTextEncoder(application)
    private val vitEncoder = ViTEncoder(application)
    private val qdrantManager = QdrantDatabaseManager(application)
    private val modelManager = ModelManager(application)

    // Mutex to ensure only one model operation (init or inference) happens at a time
    private val modelMutex = Mutex()
    
    init {
        viewModelScope.launch {
            modelMutex.withLock {
                Log.d("EmbeddingViewModel", "Initializing models...")

                // Initialize encoders with error handling
                val clipImageResult = clipImageEncoder.initialize()
                clipImageResult.onSuccess {
                    Log.d("EmbeddingViewModel", "CLIP Image encoder initialized")
                }.onFailure { e ->
                    Log.e("EmbeddingViewModel", "Failed to initialize CLIP Image encoder", e)
                }

                val clipTextResult = clipTextEncoder.initialize()
                clipTextResult.onSuccess {
                    Log.d("EmbeddingViewModel", "CLIP Text encoder initialized")
                }.onFailure { e ->
                    Log.e("EmbeddingViewModel", "Failed to initialize CLIP Text encoder", e)
                }

                val vitResult = vitEncoder.initialize()
                vitResult.onSuccess {
                    Log.d("EmbeddingViewModel", "ViT encoder initialized")
                }.onFailure { e ->
                    Log.e("EmbeddingViewModel", "Failed to initialize ViT encoder", e)
                }

                // Initialize VexDB (formerly Qdrant)
                val vexResult = qdrantManager.initialize(
                    QdrantDatabaseManager.Config(
                        host = "localhost", // Change this to your VexDB server if needed
                        port = 6334,
                        enableLogging = true // Set to false in production
                    )
                )

                vexResult.onSuccess {
                    Log.d("EmbeddingViewModel", "VexDB initialized successfully.")

                    // Auto-cleanup incompatible vectors (e.g. 768-dim vectors from wrong model)
                    try {
                        val deleted = qdrantManager.removeIncompatibleVectors(
                            "images",
                            com.jnd.aikit.database.ModelType.CLIP_IMAGE,
                            512 // Expected dimension for CLIP ViT-B/32
                        )
                        if (deleted > 0) {
                            Log.w("EmbeddingViewModel", "Deleted $deleted incompatible vectors (wrong dimension) from database. Please re-process these images.")
                        }
                    } catch (e: Exception) {
                        Log.e("EmbeddingViewModel", "Failed to cleanup incompatible vectors", e)
                    }

                }.onFailure { e ->
                    Log.e("EmbeddingViewModel", "Failed to initialize VexDB", e)
                }

                Log.d("EmbeddingViewModel", "Models initialization complete.")
            }
        }
    }
    
    suspend fun processImage(bitmap: Bitmap, description: String? = null, tags: List<String> = emptyList(), imageUri: android.net.Uri? = null): Result<Unit> = modelMutex.withLock {
        try {
            Log.d("EmbeddingViewModel", "Processing image...")

            // Check if required models are available
            val clipImageAvailable = modelManager.isModelAvailable(ModelTypeNew.CLIP_VISION)
            val vitAvailable = modelManager.isModelAvailable(ModelTypeNew.VIT_BASE)

            if (!clipImageAvailable && !vitAvailable) {
                val errorMsg = "No image models available. Please download and load CLIP Vision or ViT models."
                Log.e("Embedding", errorMsg)
                return@withLock Result.failure(Exception(errorMsg))
            }

            if (!clipImageAvailable) {
                Log.w("Embedding", "CLIP Vision model not available, skipping CLIP processing")
            }

            if (!vitAvailable) {
                Log.w("Embedding", "ViT model not available, skipping ViT processing")
            }

            val vectorId = UUID.randomUUID().toString()
            var hasErrors = false
            val errorMessages = mutableListOf<String>()

            // Get CLIP image embedding (if available)
            if (clipImageAvailable) {
                try {
                    val clipEmbedding = clipImageEncoder.getEmbedding(bitmap)
                    Log.d("Embedding", "CLIP image embedding generated: ${clipEmbedding.size} dims")
                    
                    // Log embedding statistics
                    val min = clipEmbedding.minOrNull() ?: 0f
                    val max = clipEmbedding.maxOrNull() ?: 0f
                    val mean = clipEmbedding.average().toFloat()
                    Log.d("Embedding", "CLIP image embedding stats - min: $min, max: $max, mean: $mean, first 5: ${clipEmbedding.take(5).joinToString()}")

                    // Store CLIP image vector
                    val clipVectorData = VectorData(
                        id = vectorId,
                        vector = clipEmbedding,
                        payload = VectorPayload(
                            type = VectorType.IMAGE,
                            model = com.jnd.aikit.database.ModelType.CLIP_IMAGE,
                            source = "camera/gallery",
                            imageUri = imageUri?.toString(),
                            tags = tags,
                            description = description
                        )
                    )

                    val clipResult = qdrantManager.storeVector(clipVectorData)
                    clipResult.onSuccess {
                        Log.d("Embedding", "CLIP image vector stored successfully with ID: $vectorId")
                    }.onFailure { e ->
                        Log.e("Embedding", "Failed to store CLIP image vector", e)
                        hasErrors = true
                        errorMessages.add("Failed to store CLIP vector: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e("Embedding", "Failed to process CLIP image embedding", e)
                    hasErrors = true
                    errorMessages.add("CLIP processing failed: ${e.message}")
                }
            }

            // Get ViT embedding (if available)
            if (vitAvailable) {
                try {
                    val vitEmbedding = vitEncoder.getEmbedding(bitmap)
                    Log.d("Embedding", "ViT embedding generated: ${vitEmbedding.size} dims")

                    // Store ViT vector
                    val vitVectorData = VectorData(
                        id = "${vectorId}_vit",
                        vector = vitEmbedding,
                        payload = VectorPayload(
                            type = VectorType.IMAGE,
                            model = com.jnd.aikit.database.ModelType.VIT,
                            source = "camera/gallery",
                            imageUri = imageUri?.toString(),
                            tags = tags,
                            description = description
                        )
                    )

                    val vitResult = qdrantManager.storeVector(vitVectorData)
                    vitResult.onSuccess {
                        Log.d("Embedding", "ViT vector stored successfully with ID: ${vectorId}_vit")
                    }.onFailure { e ->
                        Log.e("Embedding", "Failed to store ViT vector", e)
                        hasErrors = true
                        errorMessages.add("Failed to store ViT vector: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e("Embedding", "Failed to process ViT embedding", e)
                    hasErrors = true
                    errorMessages.add("ViT processing failed: ${e.message}")
                }
            }

            if (hasErrors) {
                val combinedError = errorMessages.joinToString("; ")
                Log.e("Embedding", "Image processing completed with errors: $combinedError")
                Result.failure(Exception(combinedError))
            } else {
                Log.d("Embedding", "Image processing completed successfully")
                Result.success(Unit)
            }

        } catch (e: Exception) {
            Log.e("Embedding", "Error processing image:", e)
            Result.failure(e)
        }
    }
    
    suspend fun searchByText(
        query: String,
        limit: Int = 10,
        minScore: Float? = null
    ): List<com.jnd.aikit.database.SearchResult> {
        return try {
            // Check if CLIP text model is available
            if (!modelManager.isModelAvailable(ModelTypeNew.CLIP_TEXT)) {
                Log.e("Search", "CLIP Text model not available. Please download and load the model.")
                return emptyList()
            }

            Log.d("EmbeddingViewModel", "Searching for text query: $query")
            val textEmbedding = clipTextEncoder.getEmbedding(query)
            Log.d("Embedding", "CLIP Text Embedding generated: ${textEmbedding.size} dims, first 5 values: ${textEmbedding.take(5).joinToString()}")
            
            // Log embedding statistics
            val min = textEmbedding.minOrNull() ?: 0f
            val max = textEmbedding.maxOrNull() ?: 0f
            val mean = textEmbedding.average().toFloat()
            Log.d("Embedding", "Text embedding stats - min: $min, max: $max, mean: $mean")

            // Search for similar images using CLIP text embedding
            val imageResults = qdrantManager.searchSimilar(
                queryVector = textEmbedding,
                parameters = SearchParameters(
                    limit = limit,
                    scoreThreshold = minScore ?: 0.01f, // Lower threshold for CLIP similarity
                    vectorType = VectorType.IMAGE,
                    modelType = com.jnd.aikit.database.ModelType.CLIP_IMAGE // Only search CLIP image embeddings for text queries
                )
            )

            Log.d("Search", "Found ${imageResults.size} similar images for text query")
            imageResults.forEach { result ->
                Log.d("Search", "Image result: ${result.vectorData.id}, score: ${result.score}")
            }

            imageResults

        } catch (e: Exception) {
            Log.e("Search", "Error searching by text:", e)
            emptyList()
        }
    }

    suspend fun searchByImage(
        bitmap: Bitmap,
        limit: Int = 10,
        minScore: Float? = null,
        useViT: Boolean = true
    ): List<com.jnd.aikit.database.SearchResult> {
        return try {
            // Check if required model is available
            val requiredModel = if (useViT) ModelTypeNew.VIT_BASE else ModelTypeNew.CLIP_VISION
            if (!modelManager.isModelAvailable(requiredModel)) {
                Log.e("Search", "${requiredModel.name} model not available. Please download and load the model.")
                return emptyList()
            }

            Log.d("EmbeddingViewModel", "Searching by image")
            val queryEmbedding = if (useViT) {
                // Use ViT for image-to-image search (better for visual similarity)
                vitEncoder.getEmbedding(bitmap)
            } else {
                // Use CLIP vision model
                clipImageEncoder.getEmbedding(bitmap)
            }

            val modelType = if (useViT) com.jnd.aikit.database.ModelType.VIT else com.jnd.aikit.database.ModelType.CLIP_IMAGE
            Log.d("Embedding", "${modelType.name} Embedding generated: ${queryEmbedding.size} dims, first 5 values: ${queryEmbedding.take(5).joinToString()}")

            // Search for visually similar images
            val imageResults = qdrantManager.searchSimilar(
                queryVector = queryEmbedding,
                parameters = SearchParameters(
                    limit = limit,
                    scoreThreshold = minScore ?: 0.1f,
                    vectorType = VectorType.IMAGE
                    // Note: We're not filtering by modelType to find any similar images
                )
            )

            Log.d("Search", "Found ${imageResults.size} visually similar images")
            imageResults.forEach { result ->
                Log.d("Search", "Similar image: ${result.vectorData.id}, score: ${result.score}")
            }

            imageResults

        } catch (e: Exception) {
            Log.e("Search", "Error searching by image:", e)
            emptyList()
        }
    }

    /**
     * Store text content with embedding
     */
    fun processText(text: String, description: String? = null, tags: List<String> = emptyList()) {
        viewModelScope.launch {
            modelMutex.withLock {
                try {
                    Log.d("EmbeddingViewModel", "Processing text...")
                    val vectorId = UUID.randomUUID().toString()

                    val textEmbedding = clipTextEncoder.getEmbedding(text)
                    Log.d("Embedding", "Text embedding: ${textEmbedding.size} dims")

                    val textVectorData = VectorData(
                        id = vectorId,
                        vector = textEmbedding,
                        payload = VectorPayload(
                            type = VectorType.TEXT,
                            model = ModelType.CLIP_TEXT,
                            source = "user_input",
                            tags = tags,
                            description = description ?: text.take(100)
                        )
                    )

                    val storeResult = qdrantManager.storeVector(textVectorData)
                    storeResult.onSuccess {
                        Log.d("Embedding", "Text vector stored successfully")
                    }.onFailure { e ->
                        Log.e("Embedding", "Failed to store text vector", e)
                    }

                } catch (e: Exception) {
                    Log.e("Embedding", "Error processing text:", e)
                }
            }
        }
    }

    /**
     * Search by image (reverse image search)
     */
    fun searchSimilarByImage(bitmap: Bitmap, limit: Int = 10, minScore: Float? = null) {
        viewModelScope.launch {
            modelMutex.withLock {
                try {
                    Log.d("EmbeddingViewModel", "Searching by image...")
                    val imageEmbedding = clipImageEncoder.getEmbedding(bitmap)
                    Log.d("Embedding", "Image embedding generated: ${imageEmbedding.size} dims")

                    val results = qdrantManager.searchSimilar(
                        queryVector = imageEmbedding,
                        parameters = SearchParameters(
                            limit = limit,
                            scoreThreshold = minScore,
                            vectorType = VectorType.IMAGE
                        )
                    )

                    Log.d("Search", "Found ${results.size} visually similar images")
                    results.forEach { result ->
                        Log.d("Search", "Similar image: ${result.vectorData.id}, score: ${result.score}")
                    }

                } catch (e: Exception) {
                    Log.e("Search", "Error searching by image:", e)
                }
            }
        }
    }

    /**
     * Get database statistics
     */
    fun getDatabaseStats() {
        viewModelScope.launch {
            try {
                val collections = qdrantManager.listCollections()
                Log.d("Database", "Collections: $collections")

                collections.forEach { collection ->
                    val stats = qdrantManager.getCollectionStats(collection)
                    Log.d("Database", "Stats for $collection: $stats")
                }
            } catch (e: Exception) {
                Log.e("Database", "Error getting stats:", e)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            modelMutex.withLock {
                clipImageEncoder.close()
                clipTextEncoder.close()
                vitEncoder.close()
                qdrantManager.close()
            }
        }
    }
}
