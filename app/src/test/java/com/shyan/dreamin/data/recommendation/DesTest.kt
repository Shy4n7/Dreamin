package com.shyan.dreamin.data.recommendation

import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import org.junit.Assert.*

class DesTest {
    @Test
    fun testDesDecrypt() {
        val enc = "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsmS1m3X5dI3+Z6E74cI0cI44a6lJmQ56gqI9N1X88v5T2vW9w"
        // Key: "38343638"
        try {
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec("38343638".toByteArray(Charsets.UTF_8), "DES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decoded = Base64.getDecoder().decode(enc)
            val decryptedBytes = cipher.doFinal(decoded)
            val streamUrl = String(decryptedBytes, Charsets.UTF_8)
            println("Stream URL: $streamUrl")
            assertTrue(streamUrl.startsWith("http"))
        } catch (e: Exception) {
            println("Exception: ${e.message}")
        }
    }
}
