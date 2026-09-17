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
                    val scaledBmp = if (bmp.width > 32 || bmp.height > 32) {
                        android.graphics.Bitmap.createScaledBitmap(bmp, 24, 24, true)
                    } else bmp

                    val triad = withContext(Dispatchers.Default) {
                        val palette = Palette.from(scaledBmp).generate()
                        val rawDominant = palette.getVibrantColor(
                            palette.getDominantColor(
                                palette.getMutedColor(0xFF6C5CE7.toInt())
                            )
                        )
                        val rawSecondary = palette.getDarkVibrantColor(
                            palette.getMutedColor(
                                palette.getDarkMutedColor(0xFF8E44AD.toInt())
                            )
                        )
                        val rawAccent = palette.getLightVibrantColor(
                            palette.getLightMutedColor(0xFF00CEC9.toInt())
                        )

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
