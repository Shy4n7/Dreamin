package com.shyan.dreamin.data.util

object IndicRomanizer {

    private val TAMIL_VOWELS = mapOf(
        'அ' to "a", 'ஆ' to "aa", 'இ' to "i", 'ஈ' to "ee", 'உ' to "u",
        'ஊ' to "oo", 'எ' to "e", 'ஏ' to "ae", 'ஐ' to "ai", 'ஒ' to "o",
        'ஓ' to "oa", 'ஔ' to "au", 'ஃ' to "k"
    )

    private val TAMIL_CONSONANTS = mapOf(
        'க' to "ka", 'ங' to "nga", 'ச' to "sa", 'ஞ' to "nya", 'ட' to "ta",
        'ண' to "na", 'த' to "tha", 'ந' to "na", 'ப' to "pa", 'ம' to "ma",
        'ய' to "ya", 'ர' to "ra", 'ல' to "la", 'வ' to "va", 'ழ' to "zha",
        'ள' to "la", 'ற' to "ra", 'ன' to "na", 'ஜ' to "ja", 'ஷ' to "sha",
        'ஸ' to "sa", 'ஹ' to "ha"
    )

    private val TAMIL_VOWEL_SIGNS = mapOf(
        '\u0BBE' to "aa", // ா
        '\u0BBF' to "i",  // ி
        '\u0BC0' to "ee", // ீ
        '\u0BC1' to "u",  // ு
        '\u0BC2' to "oo", // ூ
        '\u0BC6' to "e",  // ெ
        '\u0BC7' to "ae", // ே
        '\u0BC8' to "ai", // ை
        '\u0BCA' to "o",  // ொ
        '\u0BCB' to "oa", // ோ
        '\u0BCC' to "au"  // ௌ
    )

    private const val VIRAMA = '\u0BCD' // ்

    fun isTamil(text: String): Boolean {
        return text.any { it in '\u0B80'..'\u0BFF' }
    }

    fun transliterateTamilToEnglish(input: String): String {
        if (!isTamil(input)) return input

        val sb = StringBuilder()
        var i = 0
        val len = input.length

        while (i < len) {
            val ch = input[i]

            // 1. Standalone Vowels
            if (TAMIL_VOWELS.containsKey(ch)) {
                sb.append(TAMIL_VOWELS[ch])
                i++
                continue
            }

            // 2. Consonants
            if (TAMIL_CONSONANTS.containsKey(ch)) {
                val base = TAMIL_CONSONANTS[ch] ?: ""
                val root = base.substring(0, base.length - 1) // e.g. "th" from "tha"

                if (i + 1 < len) {
                    val next = input[i + 1]
                    if (next == VIRAMA) {
                        // Virama removes inherent vowel 'a' -> "th"
                        sb.append(root)
                        i += 2
                        continue
                    } else if (TAMIL_VOWEL_SIGNS.containsKey(next)) {
                        // Vowel sign attaches to consonant
                        sb.append(root).append(TAMIL_VOWEL_SIGNS[next])
                        i += 2
                        continue
                    }
                }
                // Inherent 'a' remains
                sb.append(base)
                i++
                continue
            }

            // Other characters (whitespace, punctuation, English letters, numbers)
            sb.append(ch)
            i++
        }

        return sb.toString()
    }
}
