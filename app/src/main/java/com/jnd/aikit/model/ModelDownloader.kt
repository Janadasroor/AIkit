package com.jnd.aikit.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ModelDownloader handles downloading ONNX models from remote sources
 */
class ModelDownloader(private val context: Context) {

    private val tag = "ModelDownloader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Download model with progress tracking
     */
    fun downloadModel(config: ModelConfig): Flow<DownloadResult> = flow {
        try {
            emit(DownloadResult.Started(config))

            val request = Request.Builder()
                .url(config.downloadUrl)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty response body")
            val contentLength = body.contentLength()

            if (contentLength <= 0) {
                Log.w(tag, "Content length unknown for ${config.name}")
            }

            // Create temporary file
            val tempFile = File(context.cacheDir, "${config.filename}.tmp")
            var totalBytesRead = 0L

            FileOutputStream(tempFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Report progress
                        if (contentLength > 0) {
                            val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                            emit(DownloadResult.Progress(config, progress, totalBytesRead, contentLength))
                        }
                    }
                }
            }

            emit(DownloadResult.Completed(config, tempFile, totalBytesRead))

        } catch (e: Exception) {
            Log.e(tag, "Download failed for ${config.name}", e)
            emit(DownloadResult.Error(config, e))
        }
    }

    /**
     * Download model synchronously (for simple use cases)
     */
    suspend fun downloadModelSync(config: ModelConfig): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(config.downloadUrl)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body ?: return@withContext Result.failure(IOException("Empty response body"))

            // Create temporary file
            val tempFile = File(context.cacheDir, "${config.filename}.tmp")

            FileOutputStream(tempFile).use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }

            Result.success(tempFile)

        } catch (e: Exception) {
            Log.e(tag, "Sync download failed for ${config.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Validate downloaded model
     */
    fun validateModel(config: ModelConfig, file: File): Boolean {
        // Basic validation
        if (!file.exists() || file.length() == 0L) {
            return false
        }

        // Check minimum size (ONNX models should be at least a few MB)
        if (file.length() < 1024 * 1024) { // 1MB minimum
            Log.w(tag, "Model file too small: ${file.length()} bytes")
            return false
        }

        // TODO: Add more sophisticated validation (e.g., ONNX format checking)

        return true
    }

    /**
     * Cleanup temporary files
     */
    fun cleanupTempFiles() {
        try {
            context.cacheDir?.listFiles { file ->
                file.name.endsWith(".tmp")
            }?.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to cleanup temp files", e)
        }
    }
}

/**
 * Download result states
 */
sealed class DownloadResult {
    data class Started(val config: ModelConfig) : DownloadResult()
    data class Progress(
        val config: ModelConfig,
        val progress: Float, // 0.0 to 1.0
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadResult()
    data class Completed(
        val config: ModelConfig,
        val file: File,
        val totalBytes: Long
    ) : DownloadResult()
    data class Error(
        val config: ModelConfig,
        val exception: Exception
    ) : DownloadResult()
}
