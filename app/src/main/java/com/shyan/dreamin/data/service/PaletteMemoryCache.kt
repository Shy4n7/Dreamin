package com.shyan.dreamin.data.service

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PaletteMemoryCache {
    private val colorCache = LruCache<String, Int>(150)

    fun getCachedColor(key: String?): Color? {
        if (key.isNullOrBlank()) return null
        return colorCache.get(key)?.let { Color(it) }
    }

    fun putColor(key: String, colorInt: Int) {
        if (key.isNotBlank()) {
            colorCache.put(key, colorInt)
        }
    }

    suspend fun extractDominantColor(context: Context, url: String?): Color? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        getCachedColor(url)?.let { return@withContext it }

        try {
            val req = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = Coil.imageLoader(context).execute(req)
            if (result is coil.request.SuccessResult) {
                val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                if (bmp != null) {
                    val palette = Palette.from(bmp).generate()
                    val dominantRgb = palette.dominantSwatch?.rgb
                        ?: palette.vibrantSwatch?.rgb
                        ?: palette.mutedSwatch?.rgb
                    if (dominantRgb != null) {
                        putColor(url, dominantRgb)
                        return@withContext Color(dominantRgb)
                    }
                }
            }
        } catch (e: Exception) {
            // Fail silently
        }
        null
    }
}
