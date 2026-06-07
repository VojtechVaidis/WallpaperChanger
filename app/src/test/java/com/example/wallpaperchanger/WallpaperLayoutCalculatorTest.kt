package com.example.wallpaperchanger

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying that [WallpaperLayoutCalculator.calculateFitPosition] properly computes
 * aspect-ratio preserving coordinates and prevents upscaling for low-resolution photos.
 */
class WallpaperLayoutCalculatorTest {

    @Test
    fun testCalculateFitPosition_lowResSquareImage_doesNotScaleUp() {
        // Low-res square image 100x100, Screen 1000x1000
        // Target rect: 10% to 90% in both dimensions -> width 800, height 800
        // Result: Since image is 100x100 (smaller than target 800x800), it remains 100x100
        // to prevent upscaling. Centered horizontally: (100 + (800 - 100)/2) = 450.
        // Aligned to top: drawY = 100.
        val result = WallpaperLayoutCalculator.calculateFitPosition(
            imageWidth = 100,
            imageHeight = 100,
            screenWidth = 1000,
            screenHeight = 1000,
            leftPct = 0.1f,
            topPct = 0.1f,
            rightPct = 0.9f,
            bottomPct = 0.9f
        )

        assertEquals(450f, result.drawX, 0.01f)
        assertEquals(100f, result.drawY, 0.01f)
        assertEquals(100f, result.drawWidth, 0.01f)
        assertEquals(100f, result.drawHeight, 0.01f)
    }

    @Test
    fun testCalculateFitPosition_lowResWideImage_doesNotScaleUp() {
        // Low-res wide image 200x100, Screen 1000x1000
        // Target rect: 0.0 to 1.0 (Full screen) -> width 1000, height 1000
        // Result: Remains 200x100. Centered horizontally: (0 + (1000 - 200)/2) = 400.
        // Aligned to top: drawY = 0.
        val result = WallpaperLayoutCalculator.calculateFitPosition(
            imageWidth = 200,
            imageHeight = 100,
            screenWidth = 1000,
            screenHeight = 1000,
            leftPct = 0.0f,
            topPct = 0.0f,
            rightPct = 1.0f,
            bottomPct = 1.0f
        )

        assertEquals(400f, result.drawX, 0.01f)
        assertEquals(0f, result.drawY, 0.01f)
        assertEquals(200f, result.drawWidth, 0.01f)
        assertEquals(100f, result.drawHeight, 0.01f)
    }

    @Test
    fun testCalculateFitPosition_highResImage_scalesDownToFit() {
        // High-res image 2000x1000, Screen 1000x1000
        // Target rect: 0.0 to 1.0 (Full screen) -> width 1000, height 1000
        // Result: Scales down to fit width 1000, height 500, centered horizontally: 0.
        // Aligned to top: drawY = 0.
        val result = WallpaperLayoutCalculator.calculateFitPosition(
            imageWidth = 2000,
            imageHeight = 1000,
            screenWidth = 1000,
            screenHeight = 1000,
            leftPct = 0.0f,
            topPct = 0.0f,
            rightPct = 1.0f,
            bottomPct = 1.0f
        )

        assertEquals(0f, result.drawX, 0.01f)
        assertEquals(0f, result.drawY, 0.01f)
        assertEquals(1000f, result.drawWidth, 0.01f)
        assertEquals(500f, result.drawHeight, 0.01f)
    }
}
