package com.example.analysis

import android.graphics.Bitmap
import android.graphics.Color
import com.example.core.models.ExposureToolsConfig
import com.example.core.models.FocusToolsConfig
import com.example.core.models.FrameAnalysisResult
import com.example.core.models.ScopeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ImageAnalysisEngine {
    companion object {
        private const val ANALYSIS_MAX_W = 320
        private const val WAVEFORM_COLS = 64
        private const val WAVEFORM_ROWS = 64
    }

    suspend fun analyzeFrame(
        frame: Bitmap,
        focusConfig: FocusToolsConfig,
        exposureConfig: ExposureToolsConfig,
        isFromHardware: Boolean
    ): FrameAnalysisResult = withContext(Dispatchers.Default) {
        val scale = if (frame.width > ANALYSIS_MAX_W) ANALYSIS_MAX_W.toFloat() / frame.width else 1f
        val w = (frame.width * scale).toInt().coerceAtLeast(32)
        val h = (frame.height * scale).toInt().coerceAtLeast(18)

        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(frame, w, h, false) else frame
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== frame) scaled.recycle()

        val lumaHist = IntArray(256)
        val rHist = IntArray(256)
        val gHist = IntArray(256)
        val bHist = IntArray(256)

        val needWaveform = exposureConfig.scopeMode == ScopeMode.WAVEFORM
        val needParade = exposureConfig.scopeMode == ScopeMode.RGB_PARADE

        val waveform = if (needWaveform) Array(WAVEFORM_COLS) { IntArray(WAVEFORM_ROWS) } else emptyArray()
        val paradeR = if (needParade) Array(WAVEFORM_COLS) { IntArray(WAVEFORM_ROWS) } else emptyArray()
        val paradeG = if (needParade) Array(WAVEFORM_COLS) { IntArray(WAVEFORM_ROWS) } else emptyArray()
        val paradeB = if (needParade) Array(WAVEFORM_COLS) { IntArray(WAVEFORM_ROWS) } else emptyArray()

        val lumaPlane = IntArray(w * h)
        var sumLuma = 0L
        var highlightClipped = 0
        var shadowClipped = 0

        val expOverlayPixels = if (
            exposureConfig.falseColorEnabled ||
            exposureConfig.zebraEnabled ||
            exposureConfig.highlightClippingEnabled ||
            exposureConfig.shadowClippingEnabled
        ) IntArray(w * h) else null

        val zebraCutoff = (exposureConfig.zebraThresholdIre * 255) / 100
        val overlayAlpha = (exposureConfig.overlayOpacity * 255).toInt().coerceIn(40, 255)

        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                val px = pixels[rowOff + x]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val luma = ((2126 * r + 7152 * g + 722 * b) / 10000).coerceIn(0, 255)
                lumaPlane[rowOff + x] = luma

                lumaHist[luma]++
                rHist[r]++
                gHist[g]++
                bHist[b]++
                sumLuma += luma

                if (luma >= exposureConfig.highlightThreshold) highlightClipped++
                if (luma <= exposureConfig.shadowThreshold) shadowClipped++

                if (needWaveform) {
                    val col = (x * WAVEFORM_COLS) / w
                    val bin = ((255 - luma) * (WAVEFORM_ROWS - 1)) / 255
                    waveform[col][bin]++
                }
                if (needParade) {
                    val col = (x * WAVEFORM_COLS) / w
                    paradeR[col][((255 - r) * (WAVEFORM_ROWS - 1)) / 255]++
                    paradeG[col][((255 - g) * (WAVEFORM_ROWS - 1)) / 255]++
                    paradeB[col][((255 - b) * (WAVEFORM_ROWS - 1)) / 255]++
                }

                if (expOverlayPixels != null) {
                    var outColor = 0
                    if (exposureConfig.falseColorEnabled) {
                        outColor = falseColorForLuma(luma, overlayAlpha)
                    } else {
                        if (exposureConfig.highlightClippingEnabled && luma >= exposureConfig.highlightThreshold) {
                            outColor = (overlayAlpha shl 24) or 0xFF1744
                        } else if (exposureConfig.shadowClippingEnabled && luma <= exposureConfig.shadowThreshold) {
                            outColor = (overlayAlpha shl 24) or 0x38BDF8
                        } else if (exposureConfig.zebraEnabled && luma >= zebraCutoff && ((x + y) % 8) < 3) {
                            outColor = (overlayAlpha shl 24) or (exposureConfig.zebraColorArgb and 0x00FFFFFF)
                        }
                    }
                    expOverlayPixels[rowOff + x] = outColor
                }
            }
        }

        // Sobel Focus Peaking & Sharpness Score
        var sharpnessAccum = 0L
        val peakingPixels = if (focusConfig.peakingEnabled) IntArray(w * h) else null
        val edgeThreshold = ((1f - focusConfig.peakingSensitivity) * 210f).toInt().coerceIn(18, 220)
        val peakAlphaBase = (focusConfig.peakingIntensity * 255).toInt().coerceIn(50, 255)
        val peakRgb = focusConfig.peakingColor.argb and 0x00FFFFFF

        for (y in 1 until h - 1) {
            val prevRow = (y - 1) * w
            val curRow = y * w
            val nextRow = (y + 1) * w
            for (x in 1 until w - 1) {
                val gx = -lumaPlane[prevRow + x - 1] + lumaPlane[prevRow + x + 1] -
                    2 * lumaPlane[curRow + x - 1] + 2 * lumaPlane[curRow + x + 1] -
                    lumaPlane[nextRow + x - 1] + lumaPlane[nextRow + x + 1]
                val gy = -lumaPlane[prevRow + x - 1] - 2 * lumaPlane[prevRow + x] - lumaPlane[prevRow + x + 1] +
                    lumaPlane[nextRow + x - 1] + 2 * lumaPlane[nextRow + x] + lumaPlane[nextRow + x + 1]
                val mag = abs(gx) + abs(gy)
                sharpnessAccum += mag
                if (peakingPixels != null && mag >= edgeThreshold) {
                    val alpha = (peakAlphaBase * ((mag - edgeThreshold) / 140f).coerceIn(0.45f, 1f)).toInt()
                    peakingPixels[curRow + x] = (alpha shl 24) or peakRgb
                }
            }
        }

        val peakingBmp = peakingPixels?.let {
            Bitmap.createBitmap(it, w, h, Bitmap.Config.ARGB_8888)
        }
        val exposureBmp = expOverlayPixels?.let {
            Bitmap.createBitmap(it, w, h, Bitmap.Config.ARGB_8888)
        }

        val totalPx = (w * h).toFloat().coerceAtLeast(1f)
        FrameAnalysisResult(
            lumaHistogram = lumaHist,
            rHistogram = rHist,
            gHistogram = gHist,
            bHistogram = bHist,
            waveformColumns = waveform,
            paradeR = paradeR,
            paradeG = paradeG,
            paradeB = paradeB,
            peakingOverlay = peakingBmp,
            exposureOverlay = exposureBmp,
            highlightClippedPercent = (highlightClipped * 100f) / totalPx,
            shadowClippedPercent = (shadowClipped * 100f) / totalPx,
            averageIre = ((sumLuma / totalPx) / 255f) * 100f,
            sharpnessScore = (sharpnessAccum / totalPx).coerceIn(0f, 300f),
            isFromHardwareFrame = isFromHardware
        )
    }

    private fun falseColorForLuma(luma: Int, alpha: Int): Int {
        val ire = (luma * 100) / 255
        val rgb = when {
            ire <= 5 -> 0x6A1B9A   // Deep Purple (crushed blacks)
            ire <= 15 -> 0x1E88E5  // Blue (deep shadows)
            ire <= 35 -> 0x00ACC1  // Cyan (shadows)
            ire in 40..46 -> 0x00E676 // Green (18% middle grey)
            ire in 47..53 -> 0x9E9E9E // Neutral grey
            ire in 54..63 -> 0xF48FB1 // Pink (skin tone IRE)
            ire in 64..78 -> 0xFDD835 // Yellow (bright skin/highlights)
            ire in 79..93 -> 0xFB8C00 // Orange (high highlights)
            ire >= 94 -> 0xFF1744     // Red (clipping)
            else -> Color.rgb(luma, luma, luma) and 0x00FFFFFF
        }
        return (alpha shl 24) or rgb
    }
}
