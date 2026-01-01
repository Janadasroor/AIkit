package com.jnd.aikit.ui.gallery

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository for accessing device gallery data
 */
class GalleryRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val tag = "GalleryRepository"

    /**
     * Get all image folders/albums from device
     */
    suspend fun getImageFolders(): List<GalleryFolder> = withContext(Dispatchers.IO) {
        val foldersMap = mutableMapOf<String, MutableList<GalleryImage>>()
        val folderNamesMap = mutableMapOf<String, String>()

        // First, get folders from MediaStore
        val projection = mutableListOf(
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

        // Add bucket columns (available on API 29+, but internal strings work on older)
        projection.add("bucket_id")
        projection.add("bucket_display_name")

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
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
                
                // Use string literals to be safe across API levels
                val bucketIdColumn = cursor.getColumnIndex("bucket_id")
                val bucketNameColumn = cursor.getColumnIndex("bucket_display_name")

                Log.d(tag, "Found ${cursor.count} images in MediaStore")

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathColumn) ?: ""
                    
                    // Fallback for bucket info if columns are missing
                    val bucketId = if (bucketIdColumn != -1) {
                        cursor.getString(bucketIdColumn) ?: "unknown"
                    } else {
                        File(path).parent?.hashCode()?.toString() ?: "unknown"
                    }
                    
                    val bucketName = if (bucketNameColumn != -1) {
                        cursor.getString(bucketNameColumn) ?: getBucketNameFromPath(path)
                    } else {
                        getBucketNameFromPath(path)
                    }

                    val image = GalleryImage(
                        id = cursor.getLong(idColumn).toString(),
                        uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn).toString()),
                        folderId = bucketId,
                        displayName = cursor.getString(nameColumn) ?: "Unknown",
                        path = path,
                        size = cursor.getLong(sizeColumn),
                        dateAdded = cursor.getLong(dateAddedColumn),
                        dateModified = cursor.getLong(dateModifiedColumn),
                        mimeType = cursor.getString(mimeTypeColumn) ?: "image/*",
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn)
                    )

                    foldersMap.getOrPut(bucketId) { mutableListOf() }.add(image)
                    if (!folderNamesMap.containsKey(bucketId) || folderNamesMap[bucketId] == "Unknown") {
                        folderNamesMap[bucketId] = bucketName
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error querying MediaStore", e)
        }

        // Also scan file system for additional folders (fallback for MediaStore limitations)
        if (foldersMap.isEmpty()) {
            try {
                Log.d(tag, "MediaStore returned no images, scanning file system...")
                scanFileSystemForImageFolders(foldersMap, folderNamesMap)
            } catch (e: Exception) {
                Log.e(tag, "Error scanning file system", e)
            }
        }

        val galleryFolders = foldersMap.map { (bucketId, images) ->
            val firstImage = images.firstOrNull()
            val bucketName = folderNamesMap[bucketId] ?: "Folder"

            GalleryFolder(
                id = bucketId,
                name = bucketName,
                path = images.firstOrNull()?.path?.let { File(it).parent ?: "" } ?: "",
                imageCount = images.size,
                thumbnailUri = firstImage?.uri,
                lastModified = images.maxOfOrNull { it.dateModified } ?: 0L
            )
        }.sortedByDescending { it.lastModified }

        Log.d(tag, "Total folders found: ${galleryFolders.size}")
        galleryFolders
    }

    /**
     * Scan file system for image folders (fallback method)
     */
    private fun scanFileSystemForImageFolders(
        folders: MutableMap<String, MutableList<GalleryImage>>,
        folderNames: MutableMap<String, String>
    ) {
        try {
            val imageExtensions = arrayOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp")
            val directoriesToScan = mutableSetOf<File>()

            // Get common image directories
            val commonDirs = mutableListOf<File>()
            
            commonDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
            commonDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
            commonDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            
            val externalStorage = Environment.getExternalStorageDirectory()
            if (externalStorage != null) {
                commonDirs.add(File(externalStorage, "DCIM"))
                commonDirs.add(File(externalStorage, "Pictures"))
                commonDirs.add(File(externalStorage, "Download"))
                commonDirs.add(File(externalStorage, "WhatsApp/Media/WhatsApp Images"))
                commonDirs.add(File(externalStorage, "Telegram/Telegram Images"))
            }

            commonDirs.forEach { dir ->
                if (dir != null && dir.exists() && dir.canRead()) {
                    directoriesToScan.add(dir)
                }
            }

            val scannedDirectories = mutableSetOf<String>()
            directoriesToScan.forEach { rootDir ->
                scanDirectoryForImages(rootDir, imageExtensions, folders, folderNames, scannedDirectories)
            }

        } catch (e: Exception) {
            Log.e(tag, "FileSystem scan failed", e)
        }
    }

    private fun scanDirectoryForImages(
        directory: File,
        imageExtensions: Array<String>,
        folders: MutableMap<String, MutableList<GalleryImage>>,
        folderNames: MutableMap<String, String>,
        scannedDirectories: MutableSet<String>
    ) {
        if (!directory.exists() || !directory.canRead() || directory.absolutePath in scannedDirectories) {
            return
        }

        scannedDirectories.add(directory.absolutePath)

        try {
            val files = directory.listFiles() ?: return

            files.forEach { file ->
                if (file.isDirectory) {
                    if (scannedDirectories.size < 500) { // Increased limit
                        scanDirectoryForImages(file, imageExtensions, folders, folderNames, scannedDirectories)
                    }
                } else if (file.isFile && imageExtensions.any { ext ->
                    file.name.lowercase().endsWith(ext)
                }) {
                    try {
                        val uri = Uri.fromFile(file)
                        val bucketId = directory.absolutePath.hashCode().toString()

                        if (!folders.containsKey(bucketId)) {
                            folders[bucketId] = mutableListOf()
                            folderNames[bucketId] = directory.name
                        }

                        val image = GalleryImage(
                            id = file.absolutePath.hashCode().toString(),
                            uri = uri,
                            folderId = bucketId,
                            displayName = file.name,
                            path = file.absolutePath,
                            size = file.length(),
                            dateAdded = file.lastModified() / 1000,
                            dateModified = file.lastModified() / 1000,
                            mimeType = getMimeTypeFromExtension(file.name),
                            width = 0,
                            height = 0
                        )

                        folders[bucketId]?.add(image)
                    } catch (e: Exception) {
                        // Skip
                    }
                }
            }
        } catch (e: Exception) {
            // Skip
        }
    }

    private fun getMimeTypeFromExtension(fileName: String): String {
        return when (fileName.substringAfterLast('.').lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            else -> "image/*"
        }
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

        val selection = "bucket_id = ?"
        val selectionArgs = arrayOf(folderId)
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        try {
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
        } catch (e: Exception) {
            Log.e(tag, "Error querying MediaStore for images in folder $folderId", e)
        }

        // Fallback for file system based folders (where folderId is a hashcode string)
        if (images.isEmpty()) {
            // Note: In a production app, we should store the folder path to make this efficient
            // For now, if MediaStore returns nothing, we might need a better way to handle these
        }

        images
    }

    /**
     * Get image by URI
     */
    suspend fun getImageByUri(uri: Uri): GalleryImage? = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return@withContext null)
            if (file.exists()) {
                return@withContext GalleryImage(
                    id = file.absolutePath.hashCode().toString(),
                    uri = uri,
                    folderId = file.parentFile?.absolutePath?.hashCode().toString() ?: "unknown",
                    displayName = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    dateAdded = file.lastModified() / 1000,
                    dateModified = file.lastModified() / 1000,
                    mimeType = getMimeTypeFromExtension(file.name),
                    width = 0,
                    height = 0
                )
            }
            return@withContext null
        }

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
            "bucket_id"
        )

        try {
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
                    val bucketIdColumn = cursor.getColumnIndex("bucket_id")

                    val bucketId = if (bucketIdColumn != -1) cursor.getString(bucketIdColumn) ?: "unknown" else "unknown"

                    return@withContext GalleryImage(
                        id = cursor.getLong(idColumn).toString(),
                        uri = uri,
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
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting image by URI: $uri", e)
        }
        null
    }

    /**
     * Extract bucket name from file path
     */
    private fun getBucketNameFromPath(path: String): String {
        return try {
            val file = File(path)
            val parent = file.parentFile
            if (parent != null && parent.name.isNotEmpty()) {
                parent.name
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
