package com.shyan.dreamin.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.shyan.dreamin.data.model.Song
import java.io.File

object MediaItemFactory {

    fun build(song: Song, streamUrl: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(parseAudioUri(streamUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.displayArtworkUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                    .build()
            )
            .build()

    private fun parseAudioUri(streamUrl: String): Uri {
        val trimmed = streamUrl.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") ->
                Uri.parse(trimmed.replace("\\/", "/").replace(" ", "%20"))
            trimmed.startsWith("file://") -> Uri.parse(trimmed)
            else -> {
                val file = File(trimmed)
                if (file.exists() && file.length() > 0) Uri.fromFile(file) else Uri.parse(trimmed)
            }
        }
    }
}
