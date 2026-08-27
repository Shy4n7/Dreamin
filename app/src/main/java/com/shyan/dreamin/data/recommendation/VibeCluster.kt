package com.shyan.dreamin.data.recommendation

import com.shyan.dreamin.data.model.Song

enum class SongVibe(
    val label: String,
    val emoji: String,
    val searchKeywords: List<String>,
    val fallbackEraSearch: String,
    val allowModernTrending: Boolean
) {
    VINTAGE_90S_80S_CLASSIC(
        "90s & Golden Era Evergreen",
        "🎙️",
        listOf(
            "spb evergreen tamil hits",
            "90s ar rahman melody songs",
            "ilaiyaraaja timeless melodies",
            "ks chithra 90s tamil hits",
            "classic 90s tamil love melodies",
            "hariharan 90s tamil songs",
            "swarnalatha tamil melody hits",
            "deva evergreen melody songs",
            "vidyasagar 90s tamil hits"
        ),
        fallbackEraSearch = "best 90s tamil melody songs",
        allowModernTrending = false
    ),

    MELODIC_2000S_GOLDEN(
        "2000s Melodic Titans",
        "🎸",
        listOf(
            "harris jayaraj evergreen hits",
            "yuvan shankar raja 2000s melodies",
            "karthik hit songs tamil",
            "best of 2000s tamil melodies",
            "ar rahman 2000s melody hits",
            "haricharan tamil hits",
            "naresh iyer tamil hits",
            "d imman melodic songs"
        ),
        fallbackEraSearch = "2000s tamil evergreen melodies",
        allowModernTrending = false
    ),

    MODERN_INDIE_ROMANTIC(
        "Modern Romantic & Indie Groove",
        "💖",
        listOf(
            "sai abhyankkar songs",
            "pradeep kumar melody hits",
            "sid sriram tamil love hits",
            "sean roldan romantic songs",
            "latest tamil melody songs 2024",
            "justin prabhakaran hits",
            "govind vasantha melody songs"
        ),
        fallbackEraSearch = "trending tamil romantic songs 2025",
        allowModernTrending = true
    ),

    HIGH_ENERGY_PARTY_KUTHU(
        "High-Energy Party & Kuthu",
        "🔥",
        listOf(
            "anirudh party kuthu hits",
            "high energy tamil fast songs",
            "hiphop tamizha mass hits",
            "santhosh narayanan fast beats",
            "trending tamil kuthu songs 2024"
        ),
        fallbackEraSearch = "trending tamil songs 2025",
        allowModernTrending = true
    ),

    LATE_NIGHT_CHILL(
        "Late-Night Acoustic & Calm",
        "🌙",
        listOf(
            "late night tamil chill acoustic",
            "soft calm tamil melodies",
            "pradeep kumar acoustic songs",
            "relaxing tamil acoustic songs",
            "peaceful tamil night melodies"
        ),
        fallbackEraSearch = "calm tamil acoustic melodies",
        allowModernTrending = false
    );

    companion object {
        // Legendary Vintage artists
        private val vintageArtists = listOf(
            "s. p. balasubrahmanyam", "spb", "s.p.b", "k. s. chithra", "k.s. chithra", "chithra",
            "s. janaki", "janaki", "p. susheela", "ilaiyaraaja", "ilayaraja", "mano",
            "swarnalatha", "unni menon", "hariharan", "sujatha", "k. j. yesudas", "yesudas",
            "malaysia vasudevan", "deva", "sirpy", "s. a. rajkumar", "soundaryan"
        )

        // 2000s era titans
        private val melodic2000sArtists = listOf(
            "harris jayaraj", "yuvan", "yuvan shankar raja", "karthik", "haricharan",
            "naresh iyer", "chinmayi", "bombay jayashri", "sadhana sargam", "shreya ghoshal",
            "srinivas", "unnikrishnan", "tippu", "vijay prakash", "joshua sridhar", "james vasanthan"
        )

        // Modern indie artists
        private val modernIndieArtists = listOf(
            "sai abhyankkar", "pradeep kumar", "sid sriram", "sean roldan", "leon james",
            "dhibu ninan thomas", "justin prabhakaran", "govind vasantha", "stephen zechariah", "keba jeremiah"
        )

        // Explicit modern melody title dictionary
        private val modernMelodyKeywords = listOf(
            "idhazhin oram", "nee paartha", "po nee po", "kannazhaga", "kannaana kanne",
            "megham karukatha", "thenmozhi", "velicha poove", "kaathalae", "mudhal nee",
            "en rojaa", "porkanda singam", "bae", "hayyoda", "chillanjirukkiye", "kadharalz",
            "thangamey", "naan pizhai", "un vizhigalil", "sirikkadhey", "jodi nilave",
            "kadhal kanave", "neeyum naanum", "inaye", "usure", "bodhai", "adiye", "aasa kooda",
            "ala balelo", "katchi sera", "mellinamae", "adada mazhaida", "unakku thaan",
            "maya nadhi", "paravaiye", "agayam theepiditha", "avatha paiya", "kadalalle",
            "kanave kanave", "enna solla", "thaen thaen", "anbil avan", "kurumugil"
        )

        // Party / Kuthu keywords & artists
        private val partyKeywords = listOf(
            "kuthu", "kacheri", "hukum", "badass", "naa ready", "matta", "party", "dance", "beat",
            "local", "mass", "marana", "jalabulajangu", "vaathi", "chilla chilla", "arabic kuthu",
            "whistle", "jimikki", "verithanam", "kaavaalaa", "chillax", "danga maari", "raga of revenge",
            "revenge", "theme", "ost", "score", "hangover", "madras to madurai", "aambala", "aalu ma",
            "donu donu", "senjitaley", "selfie pulla", "chellama", "pottala", "tasakku", "open the bottle"
        )

        private val chillKeywords = listOf(
            "mudhal nee", "o kadhale", "en rojaa", "unakkul", "thanimai", "sad", "alone", "sleep",
            "soft", "acoustic", "breeze", "raasathi", "kanmani", "vizhiyil", "poove sempoove"
        )

        fun detectVibe(song: Song): SongVibe {
            val titleLower = song.displayTitle.lowercase()
            val artistLower = song.artist.lowercase()
            val fullText = "$titleLower $artistLower"

            // 1. Explicit Modern Melody matching
            if (modernMelodyKeywords.any { titleLower.contains(it) }) {
                return MODERN_INDIE_ROMANTIC
            }

            // 2. Party / Fast beats matching
            if (partyKeywords.any { fullText.contains(it) } && !vintageArtists.any { artistLower.contains(it) }) {
                return HIGH_ENERGY_PARTY_KUTHU
            }

            // 3. Vintage 80s/90s Classic artist recognition (SPB, Chithra, Ilaiyaraaja, Deva, etc.)
            if (vintageArtists.any { artistLower.contains(it) }) {
                return VINTAGE_90S_80S_CLASSIC
            }

            // 4. 2000s Melodic Titans (Harris, Yuvan, Karthik, etc.)
            if (melodic2000sArtists.any { artistLower.contains(it) }) {
                return MELODIC_2000S_GOLDEN
            }

            // 5. Modern Indie / Romantic artists (Sai Abhyankkar, Pradeep Kumar, Sid Sriram)
            if (modernIndieArtists.any { artistLower.contains(it) }) {
                return MODERN_INDIE_ROMANTIC
            }

            // 6. Chill keywords
            if (chillKeywords.any { fullText.contains(it) }) {
                return LATE_NIGHT_CHILL
            }

            // 7. A.R. Rahman special handling by track title/era
            if (artistLower.contains("a.r. rahman") || artistLower.contains("ar rahman")) {
                val vintageRahmanTitles = listOf("anjali", "duet", "roja", "gentleman", "kadhalan", "bombay", "muthu", "rangeela", "indian", "minsaara", "jeans", "sangamam", "mudhalvan", "kandukondain", "alaipayuthey", "kannalane", "chinna chinna", "usilampatti", "urvasi", "kappaleri", "thenmerku", "en kaadhale")
                if (vintageRahmanTitles.any { titleLower.contains(it) }) {
                    return VINTAGE_90S_80S_CLASSIC
                }
                return MELODIC_2000S_GOLDEN
            }

            // 8. Anirudh / Hiphop Tamizha: check for fast/party keywords, else default to romance/pop
            if (artistLower.contains("anirudh") || artistLower.contains("hiphop tamizha")) {
                return if (partyKeywords.any { fullText.contains(it) }) HIGH_ENERGY_PARTY_KUTHU else MODERN_INDIE_ROMANTIC
            }

            return MODERN_INDIE_ROMANTIC
        }
    }
}
