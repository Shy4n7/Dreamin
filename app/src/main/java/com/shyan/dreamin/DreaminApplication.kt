package com.shyan.dreamin

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class DreaminApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(com.shyan.dreamin.data.network.NetworkService.httpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(350L * 1024 * 1024) // 350 MB
                    .build()
            }
            .bitmapConfig(Bitmap.Config.HARDWARE)
            .allowHardware(true)
            .allowRgb565(true)
            .crossfade(100)
            .respectCacheHeaders(false)
            .build()
    }
}
