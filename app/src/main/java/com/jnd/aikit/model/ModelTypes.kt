package com.jnd.aikit.model

/**
 * Model types and configurations for dynamic ONNX model loading
 */

enum class ModelType {
    CLIP_TEXT,
    CLIP_VISION,
    VIT_BASE
}

data class ModelConfig(
    val type: ModelType,
    val name: String,
    val filename: String,
    val version: String,
    val description: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val checksum: String? = null,
    val inputShape: IntArray,
    val outputShape: IntArray
) {
    companion object {
        // Default model configurations
        val CLIP_TEXT_MODEL = ModelConfig(
            type = ModelType.CLIP_TEXT,
            name = "CLIP Text Encoder",
            filename = "clip_text_xenova_quantized.onnx", // Changed to force re-download
            version = "1.0.0",
            description = "CLIP text encoder for natural language processing",
            sizeBytes = 45 * 1024 * 1024, // ~45MB
            downloadUrl = "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/text_model_quantized.onnx",
            inputShape = intArrayOf(1, 77),
            outputShape = intArrayOf(1, 512)
        )

        val CLIP_VISION_MODEL = ModelConfig(
            type = ModelType.CLIP_VISION,
            name = "CLIP Vision Encoder",
            filename = "clip_vision_xenova_quantized.onnx", // Changed to force re-download
            version = "1.0.0",
            description = "CLIP vision encoder for image understanding",
            sizeBytes = 90 * 1024 * 1024, // ~90MB
            downloadUrl = "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/vision_model_quantized.onnx",
            inputShape = intArrayOf(1, 3, 224, 224),
            outputShape = intArrayOf(1, 512)
        )

        val VIT_BASE_MODEL = ModelConfig(
            type = ModelType.VIT_BASE,
            name = "ViT Base Encoder",
            filename = "vit_base_quantized.onnx",
            version = "1.0.0",
            description = "Vision Transformer base model for image classification",
            sizeBytes = 384 * 1024 * 1024, // ~384MB
            downloadUrl = "https://huggingface.co/google/vit-base-patch16-224/resolve/main/vit_base_quantized.onnx",
            inputShape = intArrayOf(1, 3, 224, 224),
            outputShape = intArrayOf(1, 768)
        )

        val ALL_MODELS = listOf(CLIP_TEXT_MODEL, CLIP_VISION_MODEL, VIT_BASE_MODEL)
    }
}

data class ModelInfo(
    val config: ModelConfig,
    val localPath: String? = null,
    val isDownloaded: Boolean = false,
    val downloadProgress: Float = 0f,
    val lastUsed: Long = 0L,
    val fileSize: Long = 0L
)

enum class ModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    LOADING,
    READY,
    ERROR
}

data class ModelState(
    val config: ModelConfig,
    val status: ModelStatus,
    val progress: Float = 0f,
    val errorMessage: String? = null
)
