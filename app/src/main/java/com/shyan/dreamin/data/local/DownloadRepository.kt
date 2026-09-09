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
        for (ext in listOf("m4a", "opus")) {
            val prefetched = File(prefetchDir, "$songId.$ext")
            if (prefetched.exists()) {
                if (prefetched.length() > 100_000L) {
                    return@withContext prefetched.absolutePath
                } else {
                    prefetched.delete()
                }
            }
        }
        null
    }

    suspend fun deletePrefetch(songId: String) = withContext(Dispatchers.IO) {
        try {
            for (ext in listOf("m4a", "opus")) {
                val file = File(prefetchDir, "$songId.$ext")
                if (file.exists()) file.delete()
            }
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
            val isYt = song.id.startsWith("yt_") || streamUrl.contains("googlevideo.com")
            val ext = if (isYt) "opus" else "m4a"
            val targetFile = File(downloadsDir, "${song.id}.$ext")
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

            var totalBytes = -1L
            // 1. Probe content length via HEAD or URL parameters
            try {
                val headReq = okhttp3.Request.Builder()
                    .url(streamUrl)
                    .head()
                    .build()
                com.shyan.dreamin.data.network.NetworkService.mediaHttpClient.newCall(headReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        totalBytes = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                }
            } catch (_: Exception) {}

            if (totalBytes <= 0L && streamUrl.contains("clen=")) {
                val clenMatch = Regex("""[?&]clen=(\d+)""").find(streamUrl)
                totalBytes = clenMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: -1L
            }

            // 2. High-speed downloading: 1MB range chunking for YouTube/throttled streams
            if (isYt && totalBytes > 0L) {
                val chunkSize = 1024 * 1024L // 1MB chunks to bypass YouTube playback throttle
                var downloadedBytes = 0L
                FileOutputStream(tempFile).use { output ->
                    var start = 0L
                    while (start < totalBytes) {
                        val end = minOf(start + chunkSize - 1, totalBytes - 1)
                        val rangeReq = okhttp3.Request.Builder()
                            .url(streamUrl)
                            .header("Range", "bytes=$start-$end")
                            .build()
                        com.shyan.dreamin.data.network.NetworkService.mediaHttpClient.newCall(rangeReq).execute().use { resp ->
                            if (!resp.isSuccessful && resp.code != 206) {
                                throw java.io.IOException("Chunk download failed with HTTP ${resp.code}")
                            }
                            val body = resp.body ?: throw java.io.IOException("Empty chunk response body")
                            val buf = ByteArray(16384)
                            body.byteStream().use { input ->
                                var read: Int
                                while (input.read(buf).also { read = it } != -1) {
                                    output.write(buf, 0, read)
                                    downloadedBytes += read
                                    onProgress((downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                                }
                            }
                        }
                        start = end + 1
                    }
                }
            } else {
                val req = okhttp3.Request.Builder()
                    .url(streamUrl)
                    .build()
                com.shyan.dreamin.data.network.NetworkService.mediaHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw java.io.IOException("Download HTTP error: ${resp.code}")

                    val body = resp.body ?: throw java.io.IOException("Empty download response body")
                    val streamTotal = if (totalBytes > 0L) totalBytes else body.contentLength()
                    var downloadedBytes = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                if (streamTotal > 0) {
                                    onProgress((downloadedBytes.toFloat() / streamTotal).coerceIn(0f, 1f))
                                }
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
            val isOpus = sourceFile.name.endsWith(".opus") || song.id.startsWith("yt_")
            val ext = if (isOpus) "opus" else "m4a"
            val mime = if (isOpus) "audio/opus" else "audio/mp4"
            val fileName = "$cleanTitle - $cleanArtist.$ext"

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
                put(android.provider.MediaStore.Audio.Media.MIME_TYPE, mime)
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
                    arrayOf(mime),
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
        for (ext in listOf("m4a", "opus")) {
            val f = File(downloadsDir, "$songId.$ext")
            if (f.exists()) f.delete()
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
