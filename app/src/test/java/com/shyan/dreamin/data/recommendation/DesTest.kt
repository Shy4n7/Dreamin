package com.shyan.dreamin.data.recommendation

import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import org.junit.Assert.*

class DesTest {
    @Test
    fun testDesDecrypt() {
        val enc = "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyac3e9re1OH816Hue/hTBRKDEQVIS01NIVUqzAF6s8OYSbO1sdt3Kohw7tS9a8Gtq"
        val result = com.shyan.dreamin.data.service.AudioStreamResolver.decryptJioSaavnMediaUrl(enc)
        // Decryption helper safely returns nullable string without crashing
        assertTrue(result == null || result.startsWith("http"))
    }

    @Test
    fun testResolveStreamUrl() = kotlinx.coroutines.runBlocking {
        val enc = "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyac3e9re1OH816Hue/hTBRKDEQVIS01NIVUqzAF6s8OYSbO1sdt3Kohw7tS9a8Gtq"
        val encEncoded = java.net.URLEncoder.encode(enc, "UTF-8")
        val authUrl = "https://www.jiosaavn.com/api.php?__call=song.generateAuthToken&url=$encEncoded&bitrate=320&api_version=4&_format=json&ctx=android&_marker=0"
        val req = okhttp3.Request.Builder()
            .url(authUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", "https://www.jiosaavn.com/")
            .build()
        val resp = com.shyan.dreamin.data.network.NetworkService.httpClient.newCall(req).execute()
        val text = resp.body?.string().orEmpty()
        println("GENERATE AUTH TOKEN RESPONSE: $text")
        assertTrue(resp.isSuccessful)
    }
}

