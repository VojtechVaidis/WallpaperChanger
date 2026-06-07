package com.example.wallpaperchanger

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Data class representing a photo album from the device gallery.
 */
data class Album(
    val bucketId: Long,
    val name: String,
    val imageCount: Int,
    val coverUri: Uri
)

/**
 * Repository for querying albums and images from MediaStore.
 */
class AlbumRepository(private val context: Context) {

    private val contentResolver = context.contentResolver

    private val imageCollection: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    /**
     * Get all photo albums (buckets) from the device.
     * Returns a list of Album objects with name, image count, and cover image.
     */
    fun getAlbums(): List<Album> {
        val albums = mutableMapOf<Long, Album>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        contentResolver.query(
            imageCollection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val imageId = cursor.getLong(idColumn)
                val bucketId = cursor.getLong(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"

                val imageUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageId
                )

                val existing = albums[bucketId]
                if (existing != null) {
                    albums[bucketId] = existing.copy(imageCount = existing.imageCount + 1)
                } else {
                    albums[bucketId] = Album(
                        bucketId = bucketId,
                        name = bucketName,
                        imageCount = 1,
                        coverUri = imageUri
                    )
                }
            }
        }

        return albums.values.sortedByDescending { it.imageCount }
    }

    /**
     * Get all image URIs for a specific album bucket.
     * This is used for "whole album" mode and is re-queried on each wallpaper change
     * to pick up newly added photos.
     */
    fun getImagesForAlbum(bucketId: Long): List<Uri> {
        val images = mutableListOf<Uri>()

        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        contentResolver.query(
            imageCollection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                images.add(uri)
            }
        }

        return images
    }

    /**
     * Get all image URIs for a specific album, with thumbnail info for the grid picker.
     */
    data class ImageItem(
        val uri: Uri,
        val id: Long,
        val dateModified: Long
    )

    fun getImageItemsForAlbum(bucketId: Long): List<ImageItem> {
        val images = mutableListOf<ImageItem>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        contentResolver.query(
            imageCollection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val date = cursor.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                images.add(ImageItem(uri = uri, id = id, dateModified = date))
            }
        }

        return images
    }

    /**
     * Retrieves one landscape photo URI and one portrait photo URI from the specified album bucket.
     * Used to populate correct orientation previews in the positioning configurator.
     *
     * Why: The user wants to see actual images from their album matching the crop step (horizontal vs vertical photo).
     */
    fun getPreviewImages(bucketId: Long): Pair<Uri?, Uri?> {
        var landscapeUri: Uri? = null
        var portraitUri: Uri? = null

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        try {
            contentResolver.query(
                imageCollection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (cursor.moveToNext() && (landscapeUri == null || portraitUri == null)) {
                    val id = cursor.getLong(idColumn)
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    if (width > height && landscapeUri == null) {
                        landscapeUri = uri
                    } else if (height >= width && portraitUri == null) {
                        portraitUri = uri
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AlbumRepository", "Error querying preview images", e)
        }

        // Fallbacks if one orientation is completely missing from the album
        if (landscapeUri == null && portraitUri != null) landscapeUri = portraitUri
        if (portraitUri == null && landscapeUri != null) portraitUri = landscapeUri

        return Pair(landscapeUri, portraitUri)
    }
}

