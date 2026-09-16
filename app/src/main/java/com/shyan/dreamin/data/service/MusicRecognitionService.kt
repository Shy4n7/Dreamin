package com.shyan.dreamin.data.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.shyan.dreamin.data.local.AppDatabase
import com.shyan.dreamin.data.local.entity.RecognizedSongEntity
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.network.NetworkService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 🎙️ Acoustic Music Recognition Service.
 *
 * Captures microphone audio using [AudioRecord], calculates real-time live amplitudes for
 * 120fps animated ripple visualizers, generates an in-memory 16-bit PCM WAV fingerprint,
 * and matches it against global music databases to recognize ambient playing music.
 */
object MusicRecognitionService {

    private const val TAG = "MusicRecognitionService"
    private const val SAMPLE_RATE = 16000
    private const val RECORD_DURATION_MS = 4500L

    sealed class RecognitionState {
        object Idle : RecognitionState()
        object Recording : RecognitionState()
        object Analyzing : RecognitionState()
        data class Success(val song: Song, val album: String = "", val releaseDate: String = "") : RecognitionState()
        object NotFound : RecognitionState()
        data class Error(val message: String) : RecognitionState()
    }

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    private val _liveAmplitude = MutableStateFlow(0f)
    val liveAmplitude: StateFlow<Float> = _liveAmplitude.asStateFlow()

    private var activeJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Starts listening and identifying ambient music using the microphone.
     */
    @SuppressLint("MissingPermission")
    fun startRecognition(context: Context, apiToken: String = "test") {
        stopRecognition()

        activeJob = serviceScope.launch {
            _recognitionState.value = RecognitionState.Recording
            _liveAmplitude.value = 0f

            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            var audioRecord: AudioRecord? = null
            val pcmStream = ByteArrayOutputStream()

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    _recognitionState.value = RecognitionState.Error("Microphone initialization failed. Please check permissions.")
                    return@launch
                }

                audioRecord.startRecording()
                val readBuffer = ShortArray(1024)
                val byteBuffer = ByteBuffer.allocate(readBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)

                val startTime = System.currentTimeMillis()

                while (isActive && (System.currentTimeMillis() - startTime < RECORD_DURATION_MS)) {
                    val read = audioRecord.read(readBuffer, 0, readBuffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        byteBuffer.clear()
                        for (i in 0 until read) {
                            val sample = readBuffer[i]
                            byteBuffer.putShort(sample)
                            sum += sample * sample
                        }
                        pcmStream.write(byteBuffer.array(), 0, read * 2)

                        // Calculate RMS amplitude normalized to 0f..1f for smooth UI ripple rings
                        val rms = Math.sqrt(sum / read) / 32768.0
                        _liveAmplitude.value = (rms.toFloat() * 3.8f).coerceIn(0f, 1f)
                    }
                    delay(20)
                }

                audioRecord.stop()
                _liveAmplitude.value = 0f

                if (!isActive) return@launch

                _recognitionState.value = RecognitionState.Analyzing

                val pcmData = pcmStream.toByteArray()
                if (pcmData.isEmpty()) {
                    _recognitionState.value = RecognitionState.NotFound
                    return@launch
                }

                val wavData = createWavFile(pcmData, SAMPLE_RATE, 1, 16)
                val recognized = queryAudDApi(wavData, apiToken)

                if (recognized != null) {
                    val (title, artist, album, releaseDate, artworkUrl) = recognized

                    // Try to resolve track in Dreamin catalogue (JioSaavn / YouTube fallback)
                    val resolvedSong = resolveDreaminSong(title, artist, album, artworkUrl)

                    // Persist to Room history
                    try {
                        AppDatabase.getInstance(context).recognizedSongDao().insert(
                            RecognizedSongEntity(
                                title = resolvedSong.title,
                                artist = resolvedSong.artist,
                                album = album,
                                artworkUrl = resolvedSong.artworkUrl,
                                songId = resolvedSong.id
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to save recognized song to Room: ${e.message}")
                    }

                    _recognitionState.value = RecognitionState.Success(
                        song = resolvedSong,
                        album = album,
                        releaseDate = releaseDate
                    )
                } else {
                    _recognitionState.value = RecognitionState.NotFound
                }

            } catch (se: SecurityException) {
                _recognitionState.value = RecognitionState.Error("Microphone permission denied. Enable microphone in Settings.")
            } catch (e: Exception) {
                Log.e(TAG, "Audio recognition error", e)
                _recognitionState.value = RecognitionState.Error(e.localizedMessage ?: "Recognition failed. Please try again.")
            } finally {
                try {
                    audioRecord?.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
                _liveAmplitude.value = 0f
            }
        }
    }

    /**
     * Stops any ongoing recognition session and resets to Idle state.
     */
    fun stopRecognition() {
        activeJob?.cancel()
        activeJob = null
        _liveAmplitude.value = 0f
        _recognitionState.value = RecognitionState.Idle
    }

    /**
     * Resets the recognition state back to Idle without side effects.
     */
    fun resetState() {
        _recognitionState.value = RecognitionState.Idle
        _liveAmplitude.value = 0f
    }

    private data class RawMatch(
        val title: String,
        val artist: String,
        val album: String,
        val releaseDate: String,
        val artworkUrl: String
    )

    private fun queryAudDApi(wavData: ByteArray, apiToken: String): RawMatch? {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("api_token", apiToken)
            .addFormDataPart("return", "apple_music,spotify")
            .addFormDataPart(
                "file",
                "sample.wav",
                wavData.toRequestBody("audio/wav".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("https://api.audd.io/")
            .post(requestBody)
            .build()

        return try {
            NetworkService.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val jsonStr = response.body?.string() ?: return null
                val root = JSONObject(jsonStr)

                if (root.optString("status") == "success" && !root.isNull("result")) {
                    val result = root.getJSONObject("result")
                    val title = result.optString("title", "").trim()
                    val artist = result.optString("artist", "").trim()
                    val album = result.optString("album", "").trim()
                    val releaseDate = result.optString("release_date", "").trim()

                    // Try to get highest resolution album artwork available from Spotify or Apple Music
                    var artworkUrl = ""
                    val spotify = result.optJSONObject("spotify")
                    val appleMusic = result.optJSONObject("apple_music")

                    if (spotify != null) {
                        val albumObj = spotify.optJSONObject("album")
                        val images = albumObj?.optJSONArray("images")
                        if (images != null && images.length() > 0) {
                            artworkUrl = images.getJSONObject(0).optString("url", "")
                        }
                    }
                    if (artworkUrl.isBlank() && appleMusic != null) {
                        val artwork = appleMusic.optJSONObject("artwork")
                        val rawUrl = artwork?.optString("url", "") ?: ""
                        if (rawUrl.isNotBlank()) {
                            artworkUrl = rawUrl.replace("{w}", "500").replace("{h}", "500")
                        }
                    }

                    if (title.isNotBlank() && artist.isNotBlank()) {
                        RawMatch(
                            title = title,
                            artist = artist,
                            album = album,
                            releaseDate = releaseDate,
                            artworkUrl = artworkUrl
                        )
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudD API query error", e)
            null
        }
    }

    private suspend fun resolveDreaminSong(title: String, artist: String, album: String, fallbackArtwork: String): Song {
        return withContext(Dispatchers.IO) {
            try {
                // Search JioSaavn first
                val searchRes = NetworkService.api.search("$title $artist", limit = 3)
                val firstMatch = searchRes.results.firstOrNull()
                if (firstMatch != null) {
                    return@withContext firstMatch.copy(
                        artworkUrl = if (firstMatch.artworkUrl.isNotBlank()) firstMatch.artworkUrl else fallbackArtwork
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "JioSaavn search failed for recognized track: ${e.message}")
            }

            // Fallback: Construct valid Dreamin Song with YouTube fallback ID prefix
            val cleanId = "rec_${System.currentTimeMillis()}_${title.hashCode()}"
            Song(
                id = cleanId,
                title = title,
                artist = artist,
                artworkUrl = Song.resolvePoster(title, fallbackArtwork),
                duration = 210L,
                album = album
            )
        }
    }

    /**
     * Constructs a valid RIFF/WAV 44-byte binary container for raw PCM data.
     */
    internal fun createWavFile(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)

        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * (bitsPerSample / 8)).toByte() // Block align
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        val wavStream = ByteArrayOutputStream(44 + pcmData.size)
        wavStream.write(header)
        wavStream.write(pcmData)
        return wavStream.toByteArray()
    }
}
