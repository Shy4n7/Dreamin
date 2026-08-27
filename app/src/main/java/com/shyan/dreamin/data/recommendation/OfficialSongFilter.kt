package com.shyan.dreamin.data.recommendation

import com.shyan.dreamin.data.model.Song

object OfficialSongFilter {

    private val UNWANTED_TITLE_PATTERNS = Regex(
        "(?i)(~|//|\\b(cover|mashup|status|whatsapp|lyrical|8d|8d audio|bass boosted|slowed|reverb|shorts?|remix|bgm|teaser|trailer|speech|scene|full video|video song|hd|4k|unplugged|acoustic version|acoustic|karaoke|dubbed|instrumental|tribute|reaction|performance|theme music|audio jukebox|jukebox|ost|special edit|female version|male version|ringtone|ring tone|sped up|spedup|lo-fi|lofi|dj|creator|recreation|recreated|re-created|ai cover)\\b)"
    )

    private val UNWANTED_ARTISTS = listOf(
        "various artists", "unknown", "manup", "dj ", "remix", "records", "channel",
        "media", "creator", "soundtrack", "instrumental", "karaoke", "tribute", "bgm", "ray rala"
    )

    private val PARTY_FAST_KEYWORDS = listOf(
        "kuthu", "kacheri", "hukum", "badass", "naa ready", "matta", "party", "dance", "beat",
        "local", "mass", "marana", "jalabulajangu", "vaathi coming", "chilla chilla", "arabic kuthu",
        "whistle podu", "jimikki", "verithanam", "kaavaalaa", "chillax", "danga maari", "raga of revenge",
        "revenge", "hangover", "madras to madurai", "aambala", "machaan machaan", "machi open",
        "donu donu", "senjitaley", "selfie pulla", "pottala", "tasakku", "open the bottle", "patt poochihan",
        "past life", "dheera dheera"
    )

    private val NON_SOUTH_KEYWORDS = listOf(
        "arijit singh", "neha kakkar", "badshah", "pritam", "t-series", "jubin nautiyal", 
        "atif aslam", "vishal mishra", "b praak", "yo yo honey singh", "sonu nigam", 
        "armaan malik hindi", "sachet tandon", "parampara", "guru randhawa",
        "bollywood", "hindi song", "bhojpuri", "punjabi", "himesh reshammiya",
        "satinder sartaaj", "sartaaj", "shashwat sachdev", "jasleen royal", "karan aujla",
        "diljit dosanjh", "sidhu moose", "ap dhillon", "shubh", "raftaar", "mc stan",
        "divine", "king", "anuv jain", "prateek kuhad", "speed records", "desi music factory",
        "zee music", "tips official", "yrf", "tseries"
    )

    fun normalizeSongKey(title: String): String {
        var clean = title.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "")
            .replace(Regex("""[-–—|].*$"""), "")
            .lowercase()
            .trim()
        clean = clean.replace(Regex("[^a-zA-Z0-9]"), "")
        clean = clean.replace(Regex("aa+"), "a")
            .replace(Regex("ee+"), "e")
            .replace(Regex("oo+"), "o")
            .replace(Regex("ii+"), "i")
            .replace(Regex("uu+"), "u")
        return clean
    }

    private val HINDI_EXCLUSIVE_KEYWORDS = listOf(
        "arijit singh", "neha kakkar", "badshah", "pritam", "t-series", "jubin nautiyal", 
        "atif aslam", "vishal mishra", "b praak", "yo yo honey singh", "sonu nigam", 
        "armaan malik hindi", "sachet tandon", "parampara", "guru randhawa",
        "bollywood", "hindi song", "bhojpuri", "punjabi", "himesh reshammiya"
    )

    fun isHindiTrack(song: Song): Boolean {
        val text = "${song.displayTitle} ${song.artist}".lowercase()
        return HINDI_EXCLUSIVE_KEYWORDS.any { text.contains(it) }
    }

    fun isOfficial(song: Song, rejectHindi: Boolean = true): Boolean {
        val title = song.displayTitle.trim()
        val artist = song.artist.trim().lowercase()

        // 1. Reject if title contains noise patterns
        if (UNWANTED_TITLE_PATTERNS.containsMatchIn(title)) return false

        // 2. Reject if artist is unknown, generic, or unofficial
        if (artist.isBlank() || UNWANTED_ARTISTS.any { artist.contains(it) }) return false

        // 3. Reject abnormal durations (under 80s or over 9 minutes)
        if (song.duration in 1..79_999L || song.duration > 540_000L) return false

        // 4. Reject suspicious title formatting
        if (title.contains(":") && title.contains("Song", ignoreCase = true)) return false

        // 5. Strict Language Filter: Reject invading Hindi tracks in South Indian queue (only when rejectHindi=true)
        if (rejectHindi && isHindiTrack(song)) return false

        return true
    }

    fun isPartyFastSong(song: Song): Boolean {
        val text = "${song.displayTitle} ${song.artist}".lowercase()
        return PARTY_FAST_KEYWORDS.any { text.contains(it) }
    }

    fun cleanOfficialList(songs: List<Song>, targetVibe: SongVibe? = null, rejectHindi: Boolean = true): List<Song> {
        val seen = mutableSetOf<String>()
        return songs.filter { isOfficial(it, rejectHindi = rejectHindi) }
            .filter { s ->
                // If we are playing a melody/romantic track, filter out fast kuthu / party mismatch tracks
                if (targetVibe != null && targetVibe != SongVibe.HIGH_ENERGY_PARTY_KUTHU && isPartyFastSong(s)) {
                    false
                } else true
            }
            .filter {
                val norm = normalizeSongKey(it.displayTitle)
                if (norm.length >= 3 && !seen.contains(norm)) {
                    seen.add(norm)
                    true
                } else false
            }
    }
}
