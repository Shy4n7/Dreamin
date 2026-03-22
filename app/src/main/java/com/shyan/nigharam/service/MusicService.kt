package com.shyan.nigharam.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.shyan.nigharam.MainActivity

/**
 * MusicService — the heart of background playback.
 *
 * Extends Media3's MediaSessionService which:
 *  ✅ Runs as a foreground service (keeps playing screen-off)
 *  ✅ Publishes a MediaSession (lock screen controls)
 *  ✅ Auto-generates a rich playback notification (play/pause/next/prev)
 *  ✅ Integrates with Android's media button receiver
 *
 * No manual notification code needed — Media3 handles all of it.
 */
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Build ExoPlayer with proper audio attributes for music
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)   // pause on headphone unplug
            .build()

        // Tapping the notification opens MainActivity
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    // Called by MediaController in ViewModel — gives access to the session
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
