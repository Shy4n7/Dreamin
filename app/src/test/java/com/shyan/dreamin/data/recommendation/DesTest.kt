package com.shyan.dreamin.data.recommendation

import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import org.junit.Assert.*

class DesTest {
    @Test
    fun testAudioStreamResolverDecrypt() {
        val enc = "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyryhkSYK5IH2E7FCO52VR6yhNbcEbes5iCcja4+W8xhE0SwtCJToN4Bw7tS9a8Gtq"
        val decrypted = com.shyan.dreamin.data.service.AudioStreamResolver.decryptJioSaavnMediaUrl(enc)
        assertNotNull(decrypted)
        assertTrue(decrypted!!.startsWith("https://aac.saavncdn.com/"))
        assertTrue(decrypted.endsWith("_320.mp4"))
    }

    @Test
    fun testResolveStreamUrl() = kotlinx.coroutines.runBlocking {
        try {
            val enc = "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyac3e9re1OH816Hue/hTBRKDEQVIS01NIVUqzAF6s8OYSbO1sdt3Kohw7tS9a8Gtq"
            val encEncoded = java.net.URLEncoder.encode(enc, "UTF-8")
            val authUrl = "https://www.jiosaavn.com/api.php?__call=song.generateAuthToken&url=$encEncoded&bitrate=320&api_version=4&_format=json&ctx=android&_marker=0"
            val req = okhttp3.Request.Builder()
                .url(authUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()
            val resp = com.shyan.dreamin.data.network.NetworkService.httpClient.newCall(req).execute()
            // Let's search a popular 5-minute song like "Kesariya" or "Tum Hi Ho" or "Arabic Kuthu"
            val testQueries = listOf("Kesariya", "Arabic Kuthu", "Hukum")
            for (query in testQueries) {
                val encQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&ctx=android&api_version=4&p=1&n=5&q=$encQuery"
                val sReq = okhttp3.Request.Builder().url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro)")
                    .build()
                val sText = com.shyan.dreamin.data.network.NetworkService.httpClient.newCall(sReq).execute().body?.string().orEmpty()
                val sJson = org.json.JSONObject(sText)
                val results = sJson.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val first = results.getJSONObject(0)
                    val title = first.optString("title")
                    val duration = first.optString("duration")
                    val moreInfo = first.optJSONObject("more_info")
                    val encUrl = first.optString("encrypted_media_url", "").ifBlank { moreInfo?.optString("encrypted_media_url", "") ?: "" }
                    val mediaPreviewUrl = first.optString("media_preview_url", "").ifBlank { moreInfo?.optString("media_preview_url", "") ?: "" }
                    
                    println("--- SONG: $title | Duration in API: $duration seconds ---")
                    println("media_preview_url: $mediaPreviewUrl")
                    println("encrypted_media_url: $encUrl")

                    if (encUrl.isNotBlank()) {
                        val encEnc = java.net.URLEncoder.encode(encUrl, "UTF-8")
                        val aUrl = "https://www.jiosaavn.com/api.php?__call=song.generateAuthToken&url=$encEnc&bitrate=320&api_version=4&_format=json&ctx=android&_marker=0"
                        val aText = com.shyan.dreamin.data.network.NetworkService.httpClient.newCall(
                            okhttp3.Request.Builder().url(aUrl).header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro)").build()
                        ).execute().body?.string().orEmpty()
                        println("generateAuthToken: $aText")
                        val aJson = org.json.JSONObject(aText)
                        val authUrl = aJson.optString("auth_url")
                        if (authUrl.isNotBlank()) {
                            val head = com.shyan.dreamin.data.network.NetworkService.httpClient.newCall(
                                okhttp3.Request.Builder().url(authUrl).head().build()
                            ).execute()
                            val len = head.header("Content-Length")?.toLongOrNull() ?: 0L
                            println("320kbps Auth URL Content-Length: $len (approx ${len / 40000} seconds)")
                        }
                    }

                    // Check 160 or 96 or direct decrypted
                    val dec = com.shyan.dreamin.data.service.AudioStreamResolver.decryptJioSaavnMediaUrl(encUrl)
                    println("Direct Decrypted: $dec")
                    if (dec != null) {
                        val headDec = com.shyan.dreamin.data.network.NetworkService.httpClient.newCall(
                            okhttp3.Request.Builder().url(dec).head().build()
                        ).execute()
                        val lenDec = headDec.header("Content-Length")?.toLongOrNull() ?: 0L
                        println("Decrypted URL Code: ${headDec.code}, Content-Length: $lenDec")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

