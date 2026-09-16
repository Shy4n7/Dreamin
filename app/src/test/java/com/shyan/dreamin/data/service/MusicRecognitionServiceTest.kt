package com.shyan.dreamin.data.service

import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicRecognitionServiceTest {

    @Test
    fun `createWavFile builds valid 44 byte RIFF WAVE header`() = runTest {
        val dummyPcm = ByteArray(3200) { 0x1A.toByte() }
        val sampleRate = 16000
        val channels = 1
        val bitsPerSample = 16

        val wavBytes = MusicRecognitionService.createWavFile(
            pcmData = dummyPcm,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample
        )

        assertEquals(44 + dummyPcm.size, wavBytes.size)

        // RIFF header
        val riff = String(wavBytes.copyOfRange(0, 4))
        assertEquals("RIFF", riff)

        val wave = String(wavBytes.copyOfRange(8, 12))
        assertEquals("WAVE", wave)

        // fmt chunk
        val fmt = String(wavBytes.copyOfRange(12, 16))
        assertEquals("fmt ", fmt)

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Subchunk1Size = 16
        val subchunk1Size = buffer.getInt(16)
        assertEquals(16, subchunk1Size)

        // AudioFormat = 1 (PCM)
        val audioFormat = buffer.getShort(20).toInt()
        assertEquals(1, audioFormat)

        // Channels = 1
        val numChannels = buffer.getShort(22).toInt()
        assertEquals(channels, numChannels)

        // SampleRate = 16000
        val extractedSampleRate = buffer.getInt(24)
        assertEquals(sampleRate, extractedSampleRate)

        // ByteRate = 16000 * 1 * 2 = 32000
        val byteRate = buffer.getInt(28)
        assertEquals(32000, byteRate)

        // BlockAlign = 2
        val blockAlign = buffer.getShort(32).toInt()
        assertEquals(2, blockAlign)

        // BitsPerSample = 16
        val extractedBits = buffer.getShort(34).toInt()
        assertEquals(bitsPerSample, extractedBits)

        // data chunk
        val dataHeader = String(wavBytes.copyOfRange(36, 40))
        assertEquals("data", dataHeader)

        val dataSize = buffer.getInt(40)
        assertEquals(dummyPcm.size, dataSize)

        // Verify PCM payload matches
        for (i in dummyPcm.indices) {
            assertEquals(dummyPcm[i], wavBytes[44 + i])
        }
    }

    @Test
    fun `resetState resets recognition state to Idle and amplitude to zero`() = runTest {
        MusicRecognitionService.resetState()
        assertEquals(MusicRecognitionService.RecognitionState.Idle, MusicRecognitionService.recognitionState.value)
        assertEquals(0f, MusicRecognitionService.liveAmplitude.value)
    }

    @Test
    fun `stopRecognition resets active state and clears amplitude`() = runTest {
        MusicRecognitionService.stopRecognition()
        assertEquals(MusicRecognitionService.RecognitionState.Idle, MusicRecognitionService.recognitionState.value)
        assertEquals(0f, MusicRecognitionService.liveAmplitude.value)
    }
}
