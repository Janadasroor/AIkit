package com.jnd.aikit.ui.gallery

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository for accessing device gallery data
 */
class GalleryRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Get all image folders/albums from device
     */
    suspend fun getImageFolders(): List<GalleryFolder> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, MutableList<GalleryImage>>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(bucketIdColumn) ?: "unknown"
                val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"

                val image = GalleryImage(
                    id = cursor.getLong(idColumn).toString(),
                    uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn).toString()),
                    folderId = bucketId,
                    displayName = cursor.getString(nameColumn) ?: "Unknown",
                    path = cursor.getString(pathColumn) ?: "",
                    size = cursor.getLong(sizeColumn),
                    dateAdded = cursor.getLong(dateAddedColumn),
                    dateModified = cursor.getLong(dateModifiedColumn),
                    mimeType = cursor.getString(mimeTypeColumn) ?: "image/*",
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn)
                )

                folders.getOrPut(bucketId) { mutableListOf() }.add(image)
            }
        }

        folders.map { (bucketId, images) ->
            val firstImage = images.firstOrNull()
            GalleryFolder(
                id = bucketId,
                name = images.firstOrNull()?.let { getBucketNameFromPath(it.path) } ?: "Unknown",
                path = images.firstOrNull()?.path?.let { File(it).parent ?: "" } ?: "",
                imageCount = images.size,
                thumbnailUri = firstImage?.uri,
                lastModified = images.maxOfOrNull { it.dateModified } ?: 0L
            )
        }.sortedByDescending { it.lastModified }
    }

    /**
     * Get images from a specific folder
     */
    suspend fun getImagesFromFolder(folderId: String): List<GalleryImage> = withContext(Dispatchers.IO) {
        val images = mutableListOf<GalleryImage>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )

        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(folderId)
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val image = GalleryImage(
                    id = cursor.getLong(idColumn).toString(),
                    uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn).toString()),
                    folderId = folderId,
                    displayName = cursor.getString(nameColumn) ?: "Unknown",
                    path = cursor.getString(pathColumn) ?: "",
                    size = cursor.getLong(sizeColumn),
                    dateAdded = cursor.getLong(dateAddedColumn),
                    dateModified = cursor.getLong(dateModifiedColumn),
                    mimeType = cursor.getString(mimeTypeColumn) ?: "image/*",
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn)
                )
                images.add(image)
            }
        }

        images
    }

    /**
     * Get image by URI
     */
    suspend fun getImageByUri(uri: Uri): GalleryImage? = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID
        )

        contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)

                GalleryImage(
                    id = cursor.getLong(idColumn).toString(),
                    uri = uri,
                    folderId = cursor.getString(bucketIdColumn) ?: "unknown",
                    displayName = cursor.getString(nameColumn) ?: "Unknown",
                    path = cursor.getString(pathColumn) ?: "",
                    size = cursor.getLong(sizeColumn),
                    dateAdded = cursor.getLong(dateAddedColumn),
                    dateModified = cursor.getLong(dateModifiedColumn),
                    mimeType = cursor.getString(mimeTypeColumn) ?: "image/*",
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn)
                )
            } else null
        }
    }

    /**
     * Extract bucket name from file path
     */
    private fun getBucketNameFromPath(path: String): String {
        return try {
            val file = File(path)
            val parent = file.parentFile
            parent?.name ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
