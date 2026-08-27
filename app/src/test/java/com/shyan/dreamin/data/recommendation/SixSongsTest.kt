package com.shyan.dreamin.data.recommendation

import com.shyan.dreamin.data.model.Song
import org.junit.Assert.*
import org.junit.Test

class SixSongsTest {

    @Test
    fun testAllSixSongs() {
        // 1. Manasellam Mazhaiye
        val (base1, full1) = IntelliMatchEngine.decomposeTitle("Manasellam Mazhaiye")
        println("1. Manasellam: base='$base1', full='$full1'")
        val res1 = IntelliMatchEngine.evaluateCandidate(
            targetTitle = "Manasellam Mazhaiye",
            targetArtist = "Sonu Nigam, Saindhavi, G. V. Prakash Kumar",
            targetDurationMs = 315000L,
            candidate = Song("1", "Manasellam Mazhaiye", "Sonu Nigam, Saindhavi, G.V. Prakash Kumar", "", 315000L),
            targetLanguage = "tamil"
        )
        println("   Result 1 score: ${res1.score}, confidence: ${res1.confidence}")

        // 2. Theethiriyaai (From "Brahmastra (Tamil)")
        val (base2, full2) = IntelliMatchEngine.decomposeTitle("Theethiriyaai (From \"Brahmastra (Tamil)\")")
        println("2. Theethiriyaai: base='$base2', full='$full2'")
        val res2 = IntelliMatchEngine.evaluateCandidate(
            targetTitle = "Theethiriyaai (From \"Brahmastra (Tamil)\")",
            targetArtist = "Pritam, Sid Sriram",
            targetDurationMs = 260000L,
            candidate = Song("2", "Theethiriyaai", "Pritam, Sid Sriram", "", 260000L),
            targetLanguage = "tamil"
        )
        println("   Result 2 score: ${res2.score}, confidence: ${res2.confidence}")

        // 3. Agar Tum Saath Ho
        val (base3, full3) = IntelliMatchEngine.decomposeTitle("Agar Tum Saath Ho")
        println("3. Agar Tum Saath Ho: base='$base3', full='$full3'")
        val res3 = IntelliMatchEngine.evaluateCandidate(
            targetTitle = "Agar Tum Saath Ho",
            targetArtist = "Alka Yagnik, Arijit Singh",
            targetDurationMs = 341000L,
            candidate = Song("3", "Agar Tum Saath Ho", "Alka Yagnik, Arijit Singh", "", 341000L),
            targetLanguage = "hindi"
        )
        println("   Result 3 score (targetLang=hindi): ${res3.score}, confidence: ${res3.confidence}")

        val res3_tamil = IntelliMatchEngine.evaluateCandidate(
            targetTitle = "Agar Tum Saath Ho",
            targetArtist = "Alka Yagnik, Arijit Singh",
            targetDurationMs = 341000L,
            candidate = Song("3", "Agar Tum Saath Ho", "Alka Yagnik, Arijit Singh", "", 341000L),
            targetLanguage = "tamil"
        )
        println("   Result 3 score (playlist=tamil): ${res3_tamil.score}, confidence: ${res3_tamil.confidence}")

        // 4. Veshangalil Poiyillai - Additional Song
        val (base4, full4) = IntelliMatchEngine.decomposeTitle("Veshangalil Poiyillai - Additional Song")
        println("4. Veshangalil: base='$base4', full='$full4'")
        val res4 = IntelliMatchEngine.evaluateCandidate(
            targetTitle = "Veshangalil Poiyillai - Additional Song",
            targetArtist = "Anirudh Ravichander",
            targetDurationMs = 180000L,
            candidate = Song("4", "Veshangalil Poiyillai (Additional Song)", "Anirudh Ravichander", "", 180000L),
            targetLanguage = "tamil"
        )
        println("   Result 4 score: ${res4.score}, confidence: ${res4.confidence}")

        // 5. Pagal Iravai | Maraigirai
        val (base5, full5) = IntelliMatchEngine.decomposeTitle("Pagal Iravai | Maraigirai")
        println("5. Pagal Iravai: base='$base5', full='$full5'")
        val res5 = IntelliMatchEngine.evaluateCandidate(
            targetTitle = "Pagal Iravai | Maraigirai",
            targetArtist = "Pranav Das, Adheef Muhamed",
            targetDurationMs = 220000L,
            candidate = Song("5", "Pagal Iravai | Maraigirai", "Pranav Das, Adheef Muhamed", "", 220000L),
            targetLanguage = "tamil"
        )
        println("   Result 5 score: ${res5.score}, confidence: ${res5.confidence}")
    }
}
