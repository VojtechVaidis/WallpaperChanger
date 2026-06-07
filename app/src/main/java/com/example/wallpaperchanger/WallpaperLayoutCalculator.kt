package com.example.wallpaperchanger

/**
 * Utility for mapping coordinates and sizing of an image inside a relative percentage-based bounding box.
 * This math determines where the photo will be drawn on the screen canvas to ensure it fits perfectly
 * inside the user's custom rectangle bounds without distorting the photo's original aspect ratio.
 */
object WallpaperLayoutCalculator {

    /**
     * Result of the layout calculation containing coordinates and dimensions in pixel space.
     * Use these coordinates to render the photo inside the canvas of the wallpaper.
     */
    data class LayoutResult(
        val drawX: Float,
        val drawY: Float,
        val drawWidth: Float,
        val drawHeight: Float
    )

    /**
     * Computes the absolute pixel coordinates and dimensions where the photo should be drawn.
     * The photo is scaled down to fit inside the custom rectangular area defined by [leftPct], [topPct],
     * [rightPct], and [bottomPct] (each from 0.0 to 1.0) while preserving the photo's original aspect ratio.
     *
     * Why: Preserving the aspect ratio ensures the photo does not stretch or compress, and relative coordinate
     * mapping keeps the custom positioning functional across devices with different screen resolutions.
     */
    fun calculateFitPosition(
        imageWidth: Int,
        imageHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
        leftPct: Float,
        topPct: Float,
        rightPct: Float,
        bottomPct: Float
    ): LayoutResult {
        // Convert the relative percentage offsets into absolute pixel coordinates of the screen canvas
        val rectLeft = leftPct * screenWidth
        val rectTop = topPct * screenHeight
        val rectRight = rightPct * screenWidth
        val rectBottom = bottomPct * screenHeight

        // Coerce dimensions to be at least 1 pixel to prevent division by zero or invalid dimension crashes
        val rectWidth = (rectRight - rectLeft).coerceAtLeast(1f)
        val rectHeight = (rectBottom - rectTop).coerceAtLeast(1f)

        val rectRatio = rectWidth / rectHeight
        val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()

        var drawWidth: Float
        var drawHeight: Float
        if (imageRatio > rectRatio) {
            // Photo is wider than the target frame aspect ratio. Fit to target width.
            drawWidth = rectWidth
            drawHeight = rectWidth / imageRatio
        } else {
            // Photo is taller than the target frame aspect ratio. Fit to target height.
            drawHeight = rectHeight
            drawWidth = rectHeight * imageRatio
        }

        // Prevent upscaling: if the fitted size is larger than the original image dimensions,
        // clamp to original resolution (keep original pixels, don't stretch).
        // Why: The user wants to avoid blur/loss of quality from scaling up low-resolution photos.
        if (drawWidth > imageWidth || drawHeight > imageHeight) {
            drawWidth = imageWidth.toFloat()
            drawHeight = imageHeight.toFloat()
        }

        // Center the scaled photo horizontally, but align it to the top of the target rectangle frame
        // Why: The user wants to specify the top edge of the photo precisely (e.g. to fit under lock screen notifications)
        val drawX = rectLeft + (rectWidth - drawWidth) / 2f
        val drawY = rectTop

        return LayoutResult(
            drawX = drawX,
            drawY = drawY,
            drawWidth = drawWidth,
            drawHeight = drawHeight
        )
    }
}
