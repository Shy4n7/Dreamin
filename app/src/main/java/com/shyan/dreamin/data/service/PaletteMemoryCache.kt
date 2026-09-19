package com.shyan.dreamin.data.service

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.core.graphics.ColorUtils

@Immutable
data class PaletteTriad(
    val dominant: Int = 0xFF6C5CE7.toInt(),
    val secondary: Int = 0xFF8E44AD.toInt(),
    val accent: Int = 0xFF00CEC9.toInt()
)

/**
 * High-performance, memory-cached Palette and dynamic harmonic color triad extractor.
 */
object PaletteMemoryCache {
    private val colorCache = LruCache<String, Int>(150)
    private val triadCache = LruCache<String, PaletteTriad>(150)

    val DEFAULT_TRIAD = PaletteTriad()

    /**
     * 🌟 Boosts color vibrancy and brightness via HSL.
     * Clamps lightness (L >= minLightness) and saturation (S >= minSaturation) so even dark/muddy
     * album covers radiate rich, luminous color instead of drowning in black.
     */
    private fun boostColorVibrancy(
        colorInt: Int,
        minLightness: Float = 0.32f,
        maxLightness: Float = 0.68f,
        minSaturation: Float = 0.50f
    ): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colorInt, hsl)

        // If artwork is almost monochromatic / grayscale, infuse Dreamin signature amethyst tint
        if (hsl[1] < 0.08f) {
            hsl[0] = 255f
        }

        hsl[1] = hsl[1].coerceIn(minSaturation, 1.0f)
        hsl[2] = hsl[2].coerceIn(minLightness, maxLightness)

        return ColorUtils.HSLToColor(hsl)
    }

    fun getCachedColor(key: String?): Color? {
        if (key.isNullOrBlank()) return null
        return colorCache.get(key)?.let { Color(it) }
    }

    fun putColor(key: String, colorInt: Int) {
        if (key.isNotBlank()) {
            colorCache.put(key, colorInt)
        }
    }

    fun getCachedTriad(key: String?): PaletteTriad? {
        if (key.isNullOrBlank()) return null
        return triadCache.get(key)
    }

    fun putTriad(key: String, triad: PaletteTriad) {
        if (key.isNotBlank()) {
            triadCache.put(key, triad)
            colorCache.put(key, triad.dominant)
        }
    }

    fun clearCache() {
        triadCache.evictAll()
        colorCache.evictAll()
    }

    /**
     * Extracts full harmonic color triad (dominant, secondary, accent) from an image URL.
     */
    suspend fun extractPaletteTriad(context: Context, url: String?): PaletteTriad = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext DEFAULT_TRIAD
        getCachedTriad(url)?.let { return@withContext it }

        try {
            val req = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = Coil.imageLoader(context).execute(req)
            if (result is coil.request.SuccessResult) {
                val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                if (bmp != null) {
                    val scaledBmp = if (bmp.width > 48 || bmp.height > 48) {
                        android.graphics.Bitmap.createScaledBitmap(bmp, 48, 48, true)
                    } else bmp

                    val triad = withContext(Dispatchers.Default) {
                        val palette = Palette.from(scaledBmp).generate()
                        val swatches = palette.swatches
                        if (swatches.isEmpty()) return@withContext DEFAULT_TRIAD

                        val totalPopulation = swatches.sumOf { it.population }.coerceAtLeast(1)

                        // 🎯 Population-Weighted Swatch Scoring:
                        // Heavily penalizes tiny corner logos/badges (< 4% population) while rewarding
                        // authentic saturated artwork hues over dull black/grey backgrounds.
                        val scoredSwatches = swatches.map { swatch ->
                            val popRatio = swatch.population.toFloat() / totalPopulation
                            val sat = swatch.hsl[1]
                            val lightness = swatch.hsl[2]

                            // Tiny corner badges (< 4% of image area) receive 95% penalty
                            val popFactor = if (popRatio < 0.04f) popRatio * 0.05f else popRatio

                            // Colorfulness bonus: vibrant hues beat dull greys and black letterboxing
                            val colorFactor = when {
                                sat >= 0.25f -> 1.0f + (sat * 1.6f)
                                sat >= 0.12f -> 0.7f + sat
                                else -> 0.20f
                            }

                            // Lightness suitability: prefer comfortable mid-range tones over pure black/white
                            val lightnessFactor = when {
                                lightness in 0.15f..0.82f -> 1.0f
                                lightness in 0.07f..0.92f -> 0.65f
                                else -> 0.25f
                            }

                            swatch to (popFactor * colorFactor * lightnessFactor)
                        }.sortedByDescending { it.second }

                        val bestDominantSwatch = scoredSwatches.firstOrNull()?.first
                        val rawDominant = bestDominantSwatch?.rgb
                            ?: palette.getDominantColor(0xFF6C5CE7.toInt())

                        // Secondary: distinct hue/tone with meaningful population (>= 2.5%)
                        val domHsl = bestDominantSwatch?.hsl
                        val rawSecondary = if (domHsl != null) {
                            scoredSwatches.map { it.first }.firstOrNull { s ->
                                s != bestDominantSwatch &&
                                (s.population.toFloat() / totalPopulation) >= 0.025f &&
                                (kotlin.math.abs(s.hsl[0] - domHsl[0]) > 22f || kotlin.math.abs(s.hsl[2] - domHsl[2]) > 0.18f)
                            }?.rgb
                                ?: palette.getDarkVibrantColor(
                                    palette.getMutedColor(
                                        palette.getDarkMutedColor(0xFF8E44AD.toInt())
                                    )
                                )
                        } else {
                            palette.getDarkVibrantColor(0xFF8E44AD.toInt())
                        }

                        // Accent: high-vibrancy or light swatch with meaningful population (>= 2%)
                        val rawAccent = if (domHsl != null) {
                            scoredSwatches.map { it.first }.firstOrNull { s ->
                                s != bestDominantSwatch &&
                                (s.population.toFloat() / totalPopulation) >= 0.02f &&
                                s.hsl[1] >= 0.25f &&
                                kotlin.math.abs(s.hsl[0] - domHsl[0]) > 18f
                            }?.rgb
                                ?: palette.getLightVibrantColor(
                                    palette.getLightMutedColor(0xFF00CEC9.toInt())
                                )
                        } else {
                            palette.getLightVibrantColor(0xFF00CEC9.toInt())
                        }

                        val dominant = boostColorVibrancy(rawDominant, minLightness = 0.32f, maxLightness = 0.68f, minSaturation = 0.50f)
                        val secondary = boostColorVibrancy(rawSecondary, minLightness = 0.28f, maxLightness = 0.58f, minSaturation = 0.45f)
                        val accent = boostColorVibrancy(rawAccent, minLightness = 0.36f, maxLightness = 0.76f, minSaturation = 0.55f)

                        PaletteTriad(dominant, secondary, accent)
                    }

                    putTriad(url, triad)
                    return@withContext triad
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to default palette triad
        }
        DEFAULT_TRIAD
    }

    suspend fun extractDominantColor(context: Context, url: String?): Color? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        getCachedColor(url)?.let { return@withContext it }
        val triad = extractPaletteTriad(context, url)
        Color(triad.dominant)
    }
}
