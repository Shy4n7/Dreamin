package com.shyan.dreamin.data.recommendation

import com.shyan.dreamin.data.model.Song

object SmartQueueEngine {

    /**
     * Allocates queue and recommendations 100% directly from YouTube Music Official Radio Engine
     * while enforcing Zero-Repeat phonetic deduplication and official track filtering.
     */
    fun buildQueue(
        ytRadioHits: List<Song>,
        fallbackHits: List<Song> = emptyList(),
        currentSong: Song?,
        existingQueue: List<Song>,
        limit: Int = 15
    ): List<Song> {
        val seenIds = existingQueue.map { it.id }.toMutableSet()
        val seenNormKeys = mutableSetOf<String>()

        if (currentSong != null) {
            seenIds.add(currentSong.id)
            seenNormKeys.add(OfficialSongFilter.normalizeSongKey(currentSong.displayTitle))
        }
        for (q in existingQueue) {
            seenNormKeys.add(OfficialSongFilter.normalizeSongKey(q.displayTitle))
        }

        fun filterCandidates(list: List<Song>): List<Song> {
            val officialCleaned = OfficialSongFilter.cleanOfficialList(list)
            return officialCleaned.filter { s ->
                val normKey = OfficialSongFilter.normalizeSongKey(s.displayTitle)
                val isUnique = !seenIds.contains(s.id) && !seenNormKeys.contains(normKey)
                val notSuppressed = !FeedbackEngine.isSuppressed(s)
                if (isUnique && notSuppressed) {
                    seenIds.add(s.id)
                    seenNormKeys.add(normKey)
                    true
                } else false
            }
        }

        val primaryList = filterCandidates(ytRadioHits)
        if (primaryList.size >= limit) {
            return primaryList.take(limit)
        }

        val combined = (primaryList + filterCandidates(fallbackHits)).distinctBy { it.id }
        return combined.take(limit)
    }
}
