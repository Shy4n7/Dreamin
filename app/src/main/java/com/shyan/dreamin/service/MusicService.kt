package com.shyan.dreamin.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.shyan.dreamin.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@UnstableApi
class CoilBitmapLoader(private val context: android.content.Context) : BitmapLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imageLoader = ImageLoader(context)

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
        if (bitmap != null) {
            future.set(bitmap)
        } else {
            future.setException(IllegalArgumentException("Could not decode bitmap"))
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri.toString())
                    .allowHardware(false) // Software bitmap required for RemoteViews/Notification
                    .build()
                val result = (imageLoader.execute(request) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    future.set(bitmap)
                } else {
                    future.setException(IllegalStateException("Could not load bitmap from uri: $uri"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        const val ACTION_PLAY_NEXT = "com.shyan.dreamin.ACTION_PLAY_NEXT"
        const val ACTION_PLAY_PREVIOUS = "com.shyan.dreamin.ACTION_PLAY_PREVIOUS"
        const val ACTION_TOGGLE_FAVORITE = "com.shyan.dreamin.ACTION_TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_SHUFFLE = "com.shyan.dreamin.ACTION_TOGGLE_SHUFFLE"

        const val CUSTOM_COMMAND_FAVORITE = "com.shyan.dreamin.COMMAND_FAVORITE"
        const val CUSTOM_COMMAND_SHUFFLE = "com.shyan.dreamin.COMMAND_SHUFFLE"
    }

    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Exception) {}

        AudioFxManager.init(applicationContext)
        AutoEqManager.init(applicationContext)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // ⚡ ExoPlayer LRU Disk Cache: 512 MB disk cache for instant offline replay
        val universalDataSourceFactory = ExoPlayerCacheManager.createCacheDataSourceFactory(this)

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true) // 32-bit float audio: dynamic headroom prevents DSP clipping
                    .setAudioOffloadSupportProvider(androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider(context))
                    .build()
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }

        // High-stability buffer: 1000ms start, 2000ms rebuffer, 30s-120s buffer window + 30s back-buffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000,  // Min buffer (30s)
                120_000, // Max buffer (120s)
                1_000,   // Buffer for playback start (1.0s)
                2_000    // Buffer for playback after rebuffer/seek (2.0s)
            )
            .setBackBuffer(30_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelectionParameters = androidx.media3.common.TrackSelectionParameters.Builder(this)
            .setAudioOffloadPreferences(
                androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                    .build()
            )
            .build()

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(universalDataSourceFactory)
            )
            .build()

        player.trackSelectionParameters = trackSelectionParameters

        // Attach Hardware AudioFX Equalizer & BassBoost to ExoPlayer session
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioFxManager.attachAudioSession(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWifiLock(isPlaying)
            }
        })
        AudioFxManager.attachAudioSession(player.audioSessionId)

        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun seekToNext() {
                seekToNextMediaItem()
            }

            override fun seekToNextMediaItem() {
                sendBroadcast(Intent(ACTION_PLAY_NEXT).setPackage(packageName))
            }

            override fun seekToPrevious() {
                seekToPreviousMediaItem()
            }

            override fun seekToPreviousMediaItem() {
                sendBroadcast(Intent(ACTION_PLAY_PREVIOUS).setPackage(packageName))
            }
        }

        val customFavoriteButton = CommandButton.Builder()
            .setDisplayName("Favorite")
            .setIconResId(android.R.drawable.star_on)
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_FAVORITE, Bundle.EMPTY))
            .build()

        val customShuffleButton = CommandButton.Builder()
            .setDisplayName("Shuffle")
            .setIconResId(android.R.drawable.ic_menu_rotate)
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY))
            .build()

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CUSTOM_COMMAND_FAVORITE, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY))
                    .build()

                val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .setAvailablePlayerCommands(availablePlayerCommands)
                    .setCustomLayout(listOf(customFavoriteButton, customShuffleButton))
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    CUSTOM_COMMAND_FAVORITE -> sendBroadcast(Intent(ACTION_TOGGLE_FAVORITE).setPackage(packageName))
                    CUSTOM_COMMAND_SHUFFLE -> sendBroadcast(Intent(ACTION_TOGGLE_SHUFFLE).setPackage(packageName))
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(sessionCallback)
            .setBitmapLoader(CoilBitmapLoader(this))
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    private fun updateWifiLock(isPlaying: Boolean) {
        try {
            if (isPlaying) {
                if (wifiLock == null) {
                    val wm = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        @Suppress("DEPRECATION")
                        android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    }
                    wifiLock = wm?.createWifiLock(mode, "Dreamin:StreamingWifiLock")?.apply { setReferenceCounted(false) }
                }
                wifiLock?.acquire()
            } else {
                if (wifiLock?.isHeld == true) {
                    wifiLock?.release()
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
        } catch (_: Exception) {}
        AudioFxManager.release()
        mediaSession?.run { player.release(); release(); mediaSession = null }
        super.onDestroy()
    }
}
