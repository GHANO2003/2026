package com.example.analysis

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.core.models.LutConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.floor
import kotlin.math.pow

data class CubeLutData(
    val id: String,
    val title: String,
    val size: Int,
    val table: FloatArray // size^3 * 3 RGB floats [0..1]
)

class CubeLutEngine {

    private var cachedLutId: String? = null
    private var cachedLut: CubeLutData? = null

    suspend fun loadCubeFromUri(context: Context, uri: Uri, id: String): Result<CubeLutData> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val text = BufferedReader(InputStreamReader(stream)).readText()
                    parseCubeText(id, uri.lastPathSegment ?: "Custom LUT", text)
                } ?: error("Could not open .cube file")
            }
        }

    fun parseCubeText(id: String, fallbackTitle: String, content: String): CubeLutData {
        var title = fallbackTitle
        var size = 0
        val entries = ArrayList<Float>()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            when {
                line.startsWith("TITLE", ignoreCase = true) -> {
                    title = line.substringAfter("TITLE", "").trim().trim('"').ifEmpty { fallbackTitle }
                }
                line.startsWith("LUT_3D_SIZE", ignoreCase = true) -> {
                    size = line.substringAfter("LUT_3D_SIZE", "").trim().toIntOrNull() ?: 0
                }
                line.startsWith("DOMAIN_MIN", ignoreCase = true) ||
                    line.startsWith("DOMAIN_MAX", ignoreCase = true) ||
                    line.startsWith("LUT_1D_SIZE", ignoreCase = true) -> { /* handled */ }
                else -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val r = parts[0].toFloatOrNull()
                        val g = parts[1].toFloatOrNull()
                        val b = parts[2].toFloatOrNull()
                        if (r != null && g != null && b != null) {
                            entries.add(r.coerceIn(0f, 1f))
                            entries.add(g.coerceIn(0f, 1f))
                            entries.add(b.coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }

        require(size in 2..65) { "Invalid LUT_3D_SIZE ($size)" }
        val expectedFloats = size * size * size * 3
        require(entries.size >= expectedFloats) {
            "Incomplete .cube LUT data (${entries.size / 3} entries, expected ${size * size * size})"
        }
        return CubeLutData(id, title, size, entries.take(expectedFloats).toFloatArray())
    }

    fun getOrGenerateBuiltInLut(id: String): CubeLutData {
        if (cachedLutId == id && cachedLut != null) return cachedLut!!
        val size = 17
        val table = FloatArray(size * size * size * 3)
        var idx = 0
        for (bIdx in 0 until size) {
            val b = bIdx / (size - 1f)
            for (gIdx in 0 until size) {
                val g = gIdx / (size - 1f)
                for (rIdx in 0 until size) {
                    val r = rIdx / (size - 1f)
                    val (outR, outG, outB) = when (id) {
                        "builtin_cinema_teal" -> {
                            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                            val sR = smoothCurve(r, 1.12f) + (luma - 0.35f) * 0.14f
                            val sG = smoothCurve(g, 1.06f) + (0.5f - luma) * 0.04f
                            val sB = smoothCurve(b, 1.08f) + (0.45f - luma) * 0.18f
                            Triple(sR, sG, sB)
                        }
                        "builtin_warm_film" -> {
                            Triple(
                                smoothCurve(r, 1.10f) * 1.04f + 0.015f,
                                smoothCurve(g, 1.08f) * 1.00f,
                                smoothCurve(b, 1.05f) * 0.90f
                            )
                        }
                        "builtin_noir_bw" -> {
                            val luma = smoothCurve(0.2126f * r + 0.7152f * g + 0.0722f * b, 1.35f)
                            Triple(luma, luma, luma)
                        }
                        "builtin_nikon_flat" -> {
                            Triple(smoothCurve(r, 1.25f), smoothCurve(g, 1.25f), smoothCurve(b, 1.25f))
                        }
                        "builtin_canon_neutral" -> {
                            Triple(
                                smoothCurve(r, 1.15f) * 1.01f,
                                smoothCurve(g, 1.15f),
                                smoothCurve(b, 1.15f) * 0.98f
                            )
                        }
                        else -> Triple(r, g, b)
                    }
                    table[idx++] = outR.coerceIn(0f, 1f)
                    table[idx++] = outG.coerceIn(0f, 1f)
                    table[idx++] = outB.coerceIn(0f, 1f)
                }
            }
        }
        return CubeLutData(id, id, size, table).also {
            cachedLutId = id
            cachedLut = it
        }
    }

    fun registerExternalLut(lut: CubeLutData) {
        cachedLutId = lut.id
        cachedLut = lut
    }

    suspend fun applyPreviewTransform(
        source: Bitmap,
        config: LutConfig,
        activeLut: CubeLutData?
    ): Bitmap = withContext(Dispatchers.Default) {
        val needLut = config.enabled && activeLut != null && config.intensity > 0.01f
        val needGrading = config.brightness != 0f || config.contrast != 1f || config.saturation != 1f
        if (!needLut && !needGrading) return@withContext source

        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val splitX = if (config.splitCompareEnabled) (w * config.splitPosition).toInt() else w + 1
        val intensity = config.intensity.coerceIn(0f, 1f)

        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                if (config.splitCompareEnabled && x >= splitX) continue
                val px = pixels[rowOff + x]
                var r = ((px ushr 16) and 0xFF) / 255f
                var g = ((px ushr 8) and 0xFF) / 255f
                var b = (px and 0xFF) / 255f

                if (needLut && activeLut != null) {
                    val (lr, lg, lb) = sampleTrilinear(activeLut, r, g, b)
                    r = r + (lr - r) * intensity
                    g = g + (lg - g) * intensity
                    b = b + (lb - b) * intensity
                }

                if (needGrading) {
                    r = ((r - 0.5f) * config.contrast + 0.5f + config.brightness).coerceIn(0f, 1f)
                    g = ((g - 0.5f) * config.contrast + 0.5f + config.brightness).coerceIn(0f, 1f)
                    b = ((b - 0.5f) * config.contrast + 0.5f + config.brightness).coerceIn(0f, 1f)
                    if (config.saturation != 1f) {
                        val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                        r = (luma + (r - luma) * config.saturation).coerceIn(0f, 1f)
                        g = (luma + (g - luma) * config.saturation).coerceIn(0f, 1f)
                        b = (luma + (b - luma) * config.saturation).coerceIn(0f, 1f)
                    }
                }

                val ri = (r * 255f).toInt().coerceIn(0, 255)
                val gi = (g * 255f).toInt().coerceIn(0, 255)
                val bi = (b * 255f).toInt().coerceIn(0, 255)
                pixels[rowOff + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun sampleTrilinear(lut: CubeLutData, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val maxIdx = lut.size - 1
        val rf = r.coerceIn(0f, 1f) * maxIdx
        val gf = g.coerceIn(0f, 1f) * maxIdx
        val bf = b.coerceIn(0f, 1f) * maxIdx

        val r0 = floor(rf).toInt().coerceIn(0, maxIdx - 1)
        val g0 = floor(gf).toInt().coerceIn(0, maxIdx - 1)
        val b0 = floor(bf).toInt().coerceIn(0, maxIdx - 1)

        val rd = rf - r0
        val gd = gf - g0
        val bd = bf - b0

        val s = lut.size
        fun at(ri: Int, gi: Int, bi: Int, ch: Int): Float {
            val index = ((bi * s + gi) * s + ri) * 3 + ch
            return lut.table[index]
        }

        fun interpChannel(ch: Int): Float {
            val c000 = at(r0, g0, b0, ch)
            val c100 = at(r0 + 1, g0, b0, ch)
            val c010 = at(r0, g0 + 1, b0, ch)
            val c110 = at(r0 + 1, g0 + 1, b0, ch)
            val c001 = at(r0, g0, b0 + 1, ch)
            val c101 = at(r0 + 1, g0, b0 + 1, ch)
            val c011 = at(r0, g0 + 1, b0 + 1, ch)
            val c111 = at(r0 + 1, g0 + 1, b0 + 1, ch)

            val c00 = c000 + (c100 - c000) * rd
            val c10 = c010 + (c110 - c010) * rd
            val c01 = c001 + (c101 - c001) * rd
            val c11 = c011 + (c111 - c011) * rd
            val c0 = c00 + (c10 - c00) * gd
            val c1 = c01 + (c11 - c01) * gd
            return c0 + (c1 - c0) * bd
        }

        return Triple(interpChannel(0), interpChannel(1), interpChannel(2))
    }

    private fun smoothCurve(x: Float, contrastPower: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return if (c < 0.5f) {
            0.5f * (2f * c).pow(contrastPower)
        } else {
            1f - 0.5f * (2f * (1f - c)).pow(contrastPower)
        }
    }
}
