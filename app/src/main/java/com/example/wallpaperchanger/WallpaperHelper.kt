package com.example.wallpaperchanger

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

/**
 * Helper for loading images and setting them as wallpaper.
 * Handles downscaling to avoid OOM errors while maintaining quality.
 */
class WallpaperHelper(private val context: Context) {

    companion object {
        private const val TAG = "WallpaperHelper"
    }

    /**
     * Set a wallpaper from a content URI.
     * @param uri The content:// URI of the image
     * @param target One of PreferencesManager.TARGET_HOME, TARGET_LOCK, TARGET_BOTH
     * @return true if successful
     */
    fun setWallpaper(uri: Uri, target: Int): Boolean {
        val processed = processWallpaperBitmap(uri) ?: return false
        val success = setWallpaperFromBitmap(processed, target)
        processed.recycle()
        if (success) {
            Log.i(TAG, "Wallpaper set successfully from: $uri")
        }
        return success
    }

    /**
     * Process a URI into a screen-sized Bitmap according to preferences,
     * without setting it as wallpaper.
     * Note: Caller is responsible for recycling the returned bitmap.
     */
    fun processWallpaperBitmap(uri: Uri): Bitmap? {
        return try {
            val bitmap = decodeSampledBitmap(uri) ?: run {
                Log.e(TAG, "Failed to decode bitmap from URI: $uri")
                return null
            }

            // Get screen dimensions safely in background service context
            val metrics = android.content.res.Resources.getSystem().displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels

            // Get user's scaling mode preference
            val scalingMode = PreferencesManager(context).scalingMode
            val processedBitmap = if (scalingMode == PreferencesManager.SCALING_FIT) {
                createFitWallpaper(bitmap, screenWidth, screenHeight)
            } else {
                cropAndScaleToScreen(bitmap, screenWidth, screenHeight)
            }

            if (processedBitmap != bitmap) {
                bitmap.recycle()
            }
            processedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error processing wallpaper bitmap from $uri", e)
            null
        }
    }

    /**
     * Set a pre-processed Bitmap as wallpaper.
     */
    fun setWallpaperFromBitmap(bitmap: Bitmap, target: Int): Boolean {
        return try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val flag = when (target) {
                PreferencesManager.TARGET_HOME -> WallpaperManager.FLAG_SYSTEM
                PreferencesManager.TARGET_LOCK -> WallpaperManager.FLAG_LOCK
                PreferencesManager.TARGET_BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            wallpaperManager.setBitmap(bitmap, null, true, flag)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting wallpaper from bitmap", e)
            false
        }
    }

    /**
     * Crop the bitmap to the screen aspect ratio (centering it horizontally or vertically)
     * and scale it to the exact screen resolution.
     */
    private fun cropAndScaleToScreen(bitmap: Bitmap, screenWidth: Int, screenHeight: Int): Bitmap {
        val imageWidth = bitmap.width
        val imageHeight = bitmap.height

        val screenRatio = screenWidth.toFloat() / screenHeight.toFloat()
        val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()

        val cropWidth: Int
        val cropHeight: Int
        val cropX: Int
        val cropY: Int

        if (imageRatio > screenRatio) {
            // Image is wider than the screen aspect ratio. Crop the sides (horizontal center).
            cropHeight = imageHeight
            cropWidth = (imageHeight * screenRatio).toInt()
            cropX = (imageWidth - cropWidth) / 2
            cropY = 0
        } else {
            // Image is taller than the screen aspect ratio. Crop the top/bottom (vertical center).
            cropWidth = imageWidth
            cropHeight = (imageWidth / screenRatio).toInt()
            cropX = 0
            cropY = (imageHeight - cropHeight) / 2
        }

        // Safety bounds checks to prevent illegal argument exception during cropping
        val finalCropWidth = cropWidth.coerceAtMost(imageWidth).coerceAtLeast(1)
        val finalCropHeight = cropHeight.coerceAtMost(imageHeight).coerceAtLeast(1)
        val finalCropX = cropX.coerceIn(0, imageWidth - finalCropWidth)
        val finalCropY = cropY.coerceIn(0, imageHeight - finalCropHeight)

        Log.i(TAG, "Cropping bitmap: original ${imageWidth}x${imageHeight}, crop rect ($finalCropX, $finalCropY, $finalCropWidth, $finalCropHeight) for screen ${screenWidth}x${screenHeight}")

        val croppedBitmap = Bitmap.createBitmap(bitmap, finalCropX, finalCropY, finalCropWidth, finalCropHeight)
        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, screenWidth, screenHeight, true)

        if (croppedBitmap != bitmap && croppedBitmap != scaledBitmap) {
            croppedBitmap.recycle()
        }

        return scaledBitmap
    }

    /**
     * Scale the photo so that the entire image fits inside the screen,
     * centered, with a beautiful blurred and dimmed version of the image
     * filling the remaining background area.
     */
    private fun createFitWallpaper(bitmap: Bitmap, screenWidth: Int, screenHeight: Int): Bitmap {
        val result = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)

        // 1. Draw the blurred background (cropped to fill, then downscaled/upscaled for cheap fast blur)
        val filledBg = cropAndScaleToScreen(bitmap, screenWidth, screenHeight)
        val tinySize = 32
        val tinyBg = Bitmap.createScaledBitmap(filledBg, tinySize, tinySize, true)
        
        if (filledBg != bitmap) {
            filledBg.recycle()
        }
        
        val blurredBg = Bitmap.createScaledBitmap(tinyBg, screenWidth, screenHeight, true)
        if (tinyBg != filledBg && tinyBg != bitmap) {
            tinyBg.recycle()
        }

        canvas.drawBitmap(blurredBg, 0f, 0f, null)
        blurredBg.recycle()

        // Apply a dark dimming overlay to the background to make the main image stand out
        val dimPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            alpha = 153 // ~60% opacity dark overlay
        }
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), dimPaint)

        // 2. Draw the original photo centered and scaled to fit the screen
        val imageWidth = bitmap.width
        val imageHeight = bitmap.height
        val screenRatio = screenWidth.toFloat() / screenHeight.toFloat()
        val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()

        val fitWidth: Int
        val fitHeight: Int
        if (imageRatio > screenRatio) {
            // Image is wider than screen. Fit to screen width, calculate height.
            fitWidth = screenWidth
            fitHeight = (screenWidth / imageRatio).toInt()
        } else {
            // Image is taller than screen. Fit to screen height, calculate width.
            fitHeight = screenHeight
            fitWidth = (screenHeight * imageRatio).toInt()
        }

        // Safety minimum bounds
        val finalFitWidth = fitWidth.coerceAtLeast(1)
        val finalFitHeight = fitHeight.coerceAtLeast(1)

        val fitX = (screenWidth - finalFitWidth) / 2f
        val fitY = (screenHeight - finalFitHeight) / 2f

        val scaledPhoto = Bitmap.createScaledBitmap(bitmap, finalFitWidth, finalFitHeight, true)
        canvas.drawBitmap(scaledPhoto, fitX, fitY, null)
        
        if (scaledPhoto != bitmap) {
            scaledPhoto.recycle()
        }

        return result
    }

    /**
     * Decode a bitmap from a URI, sampling down to screen resolution to save memory.
     */
    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        val metrics = android.content.res.Resources.getSystem().displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        return try {
            // First pass: get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Calculate sample size
            options.inSampleSize = calculateInSampleSize(options, screenWidth, screenHeight)
            options.inJustDecodeBounds = false

            // Second pass: decode with sample size
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap from $uri", e)
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
