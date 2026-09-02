package com.shyan.dreamin.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.shyan.dreamin.data.network.NetworkService
import java.io.File

/**
 * Singleton cache provider for ExoPlayer audio streams.
 * Caches up to 512 MB of streamed audio on disk for instant 0ms offline replay and data savings.
 */
@OptIn(UnstableApi::class)
object ExoPlayerCacheManager {
    private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L // 512 MB

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Synchronized
    fun getSimpleCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val cacheDir = File(context.cacheDir, "media3_audio_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
                val databaseProvider = StandaloneDatabaseProvider(context)
                SimpleCache(cacheDir, evictor, databaseProvider).also {
                    simpleCache = it
                }
            }
        }
    }

    /**
     * Builds a caching DataSource.Factory that intercepts all ExoPlayer HTTP reads,
     * writing them to disk cache and serving cached segments directly without network calls.
     */
    fun createCacheDataSourceFactory(context: Context): DataSource.Factory {
        val cache = getSimpleCache(context)
        val httpDataSourceFactory = OkHttpDataSource.Factory(NetworkService.mediaHttpClient)
        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val cacheWriteDataSinkFactory = CacheDataSink.Factory()
            .setCache(cache)
            .setFragmentSize(4L * 1024L * 1024L) // 4 MB chunk size to optimize Linux ext4/f2fs inode allocation

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(cacheWriteDataSinkFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Builds a standalone CacheDataSource instance for background CacheWriter pre-buffering.
     */
    fun createCacheDataSource(context: Context): CacheDataSource {
        val cache = getSimpleCache(context)
        val httpDataSourceFactory = OkHttpDataSource.Factory(NetworkService.mediaHttpClient)
        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        return CacheDataSource(
            cache,
            upstreamFactory.createDataSource(),
            CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
        )
    }

    /**
     * Checks if a stream URL has already cached at least lengthBytes in SimpleCache.
     */
    fun isPartiallyCached(context: Context, streamUrl: String, lengthBytes: Long = 512 * 1024L): Boolean {
        return try {
            val cache = getSimpleCache(context)
            val cachedBytes = cache.getCachedBytes(streamUrl, 0, lengthBytes)
            cachedBytes >= lengthBytes
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Clears cached audio data if requested by the user.
     */
    fun clearCache(context: Context) {
        synchronized(this) {
            try {
                simpleCache?.release()
                simpleCache = null
                val cacheDir = File(context.cacheDir, "media3_audio_cache")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
            } catch (_: Exception) {}
        }
    }
}
