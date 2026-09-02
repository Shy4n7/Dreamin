package com.shyan.dreamin.data.local

import android.content.Context
import com.shyan.dreamin.data.local.dao.DownloadDao
import com.shyan.dreamin.data.local.entity.DownloadedSongEntity
import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val downloadsDir: File
        get() {
            val dir = File(context.filesDir, "downloads")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val prefetchDir: File
        get() {
            val dir = File(context.cacheDir, "smart_prefetch")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun observeAllDownloads(): Flow<List<Song>> {
        return downloadDao.getAllDownloads().map { list ->
            list.map { it.toSong() }
        }
    }

    suspend fun isDownloaded(songId: String): Boolean = withContext(Dispatchers.IO) {
        val entity = downloadDao.getDownload(songId) ?: return@withContext false
        val file = File(entity.localFilePath)
        if (!file.exists() || file.length() == 0L) {
            downloadDao.deleteDownload(songId)
            false
        } else {
            true
        }
    }

    suspend fun getLocalFilePath(songId: String): String? = withContext(Dispatchers.IO) {
        val entity = downloadDao.getDownload(songId) ?: return@withContext null
        val file = File(entity.localFilePath)
        if (file.exists() && file.length() > 0L) entity.localFilePath else null
    }

    /**
     * Checks if song is available offline, either through permanent downloads or smart prefetch cache.
     */
    suspend fun getPrefetchedOrDownloadedPath(songId: String): String? = withContext(Dispatchers.IO) {
        // 1. Permanent Download
        getLocalFilePath(songId)?.let { return@withContext it }

        // 2. Smart Prefetch Cache (minimum 100KB to ensure valid audio header)
        val prefetched = File(prefetchDir, "$songId.m4a")
        if (prefetched.exists()) {
            if (prefetched.length() > 100_000L) {
                return@withContext prefetched.absolutePath
            } else {
                prefetched.delete()
            }
        }
        null
    }

    suspend fun deletePrefetch(songId: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(prefetchDir, "$songId.m4a")
            if (file.exists()) file.delete()
            val tmp = File(prefetchDir, "$songId.tmp")
            if (tmp.exists()) tmp.delete()
        } catch (_: Exception) {}
    }

    /**
     * Silently pre-fetches a queue song in background for instant 0ms offline playback.
     */
    suspend fun prefetchSong(song: Song, streamUrl: String) = withContext(Dispatchers.IO) {
        try {
            cleanStalePrefetchCache()

            val targetFile = File(prefetchDir, "${song.id}.m4a")
            if (targetFile.exists() && targetFile.length() > 50_000L) return@withContext

            val tempFile = File(prefetchDir, "${song.id}.tmp")
            if (tempFile.exists()) tempFile.delete()

            val req = okhttp3.Request.Builder()
                .url(streamUrl)
                .build()
            val ok = com.shyan.dreamin.data.network.NetworkService.mediaHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body ?: return@use false
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                true
            }
            if (!ok) return@withContext

            if (tempFile.length() > 100_000L) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                android.util.Log.d("DownloadRepo", "Smart Queue pre-cached: ${song.title}")
            } else {
                tempFile.delete()
            }
        } catch (e: Exception) {
            android.util.Log.w("DownloadRepo", "Prefetch skipped for ${song.title}: ${e.message}")
        }
    }

    private fun cleanStalePrefetchCache() {
        val files = prefetchDir.listFiles() ?: return
        val now = System.currentTimeMillis()
        val oneDayAgo = now - (24 * 60 * 60 * 1000L)

        // 1. Evict files older than 24 hours
        for (f in files) {
            if (f.lastModified() < oneDayAgo || f.name.endsWith(".tmp")) {
                f.delete()
            }
        }

        // 2. Cap cache size to 60MB (LRU eviction)
        val validFiles = prefetchDir.listFiles() ?: return
        val totalSize = validFiles.sumOf { it.length() }
        val maxCapBytes = 60 * 1024 * 1024L
        if (totalSize > maxCapBytes) {
            val sorted = validFiles.sortedBy { it.lastModified() }
            var currentSize = totalSize
            for (f in sorted) {
                if (currentSize <= maxCapBytes) break
                currentSize -= f.length()
                f.delete()
            }
        }
    }

    suspend fun downloadSong(
        song: Song,
        streamUrl: String,
        onProgress: (Float) -> Unit = {}
    ): Result<DownloadedSongEntity> = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(downloadsDir, "${song.id}.m4a")
            val tempFile = File(downloadsDir, "${song.id}.tmp")
            if (tempFile.exists()) tempFile.delete()

            if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
                val srcFile = File(streamUrl)
                if (srcFile.exists() && srcFile.length() > 50_000L) {
                    srcFile.copyTo(targetFile, overwrite = true)
                    val entity = DownloadedSongEntity(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        artworkUrl = song.artworkUrl,
                        duration = song.duration,
                        localFilePath = targetFile.absolutePath,
                        fileSizeBytes = targetFile.length(),
                        downloadedAt = System.currentTimeMillis()
                    )
                    downloadDao.insertDownload(entity)
                    onProgress(1f)
                    return@withContext Result.success(entity)
                }
            }

            val req = okhttp3.Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()
            com.shyan.dreamin.data.network.NetworkService.mediaHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("Download HTTP error: ${resp.code}")

                val body = resp.body ?: throw java.io.IOException("Empty download response body")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                onProgress((downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }

            if (tempFile.length() < 100_000L) {
                tempFile.delete()
                throw java.io.IOException("Downloaded file too small / corrupt")
            }

            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)

            // Export copy directly to public Music/Dreamin folder on device for universal offline listening
            saveToPublicMusicFolder(song, targetFile)

            val entity = DownloadedSongEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                artworkUrl = song.artworkUrl,
                duration = song.duration,
                localFilePath = targetFile.absolutePath,
                fileSizeBytes = targetFile.length(),
                downloadedAt = System.currentTimeMillis()
            )
            downloadDao.insertDownload(entity)
            Result.success(entity)
        } catch (e: Exception) {
            android.util.Log.e("DownloadRepo", "Download failed for ${song.title}: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Exports the downloaded audio file to the public phone storage (Music/Dreamin) via MediaStore
     * so that it is universally accessible by all file managers and music player apps on the device.
     */
    fun saveToPublicMusicFolder(song: Song, sourceFile: File): String? {
        try {
            val cleanTitle = song.displayTitle.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
            val cleanArtist = song.artist.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
            val fileName = "$cleanTitle - $cleanArtist.m4a"

            val contentResolver = context.contentResolver
            val audioCollection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Audio.Media.TITLE, song.displayTitle)
                put(android.provider.MediaStore.Audio.Media.ARTIST, song.artist)
                put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "Music/Dreamin")
                    put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val itemUri = contentResolver.insert(audioCollection, values)
            if (itemUri != null) {
                contentResolver.openOutputStream(itemUri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
                    contentResolver.update(itemUri, values, null, null)
                }

                // Trigger MediaScanner so other apps immediately index the new track
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(itemUri.toString()),
                    arrayOf("audio/mp4"),
                    null
                )
                android.util.Log.d("DownloadRepo", "Exported to public folder: Music/Dreamin/$fileName")
                return itemUri.toString()
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadRepo", "Failed to export to public folder: ${e.message}", e)
        }
        return null
    }

    suspend fun deleteDownload(songId: String) = withContext(Dispatchers.IO) {
        val entity = downloadDao.getDownload(songId)
        if (entity != null) {
            val file = File(entity.localFilePath)
            if (file.exists()) file.delete()
            downloadDao.deleteDownload(songId)
        }
    }

    suspend fun updateArtwork(songId: String, artworkUrl: String) = withContext(Dispatchers.IO) {
        downloadDao.updateArtwork(songId, artworkUrl)
    }

    suspend fun getTotalStorageUsedBytes(): Long = withContext(Dispatchers.IO) {
        val files = downloadsDir.listFiles() ?: return@withContext 0L
        files.sumOf { it.length() }
    }
}
