package com.shyan.dreamin.data.queue

import com.shyan.dreamin.data.model.Song

data class QueueRow(val index: Int, val song: Song, val key: String)

data class QueueSections(
    val history: List<QueueRow>,
    val current: QueueRow?,
    val fromYou: List<QueueRow>,
    val nextUp: List<QueueRow>
) {
    val upcoming: List<QueueRow> get() = fromYou + nextUp

    companion object {
        private const val SECONDS_UNIT_CEILING = 10_000L
        private const val MS_PER_SECOND = 1_000L

        fun build(items: List<Song>, currentIndex: Int, userQueuedIds: List<String>): QueueSections {
            val rows = rowsWithKeys(items)
            if (currentIndex !in items.indices) return QueueSections(emptyList(), null, emptyList(), rows)
            val upcoming = rows.drop(currentIndex + 1)
            val fromYouCount = pinnedPrefixLength(upcoming, userQueuedIds.toSet())
            return QueueSections(
                history = rows.take(currentIndex),
                current = rows[currentIndex],
                fromYou = upcoming.take(fromYouCount),
                nextUp = upcoming.drop(fromYouCount)
            )
        }

        fun normalizedDurationMs(rawDuration: Long): Long = when {
            rawDuration <= 0L -> 0L
            rawDuration > SECONDS_UNIT_CEILING -> rawDuration
            else -> rawDuration * MS_PER_SECOND
        }

        fun upcomingDurationMs(sections: QueueSections): Long =
            sections.upcoming.sumOf { normalizedDurationMs(it.song.duration) }

        fun remainingDurationMs(sections: QueueSections, positionMs: Long): Long {
            val current = sections.current?.let { (normalizedDurationMs(it.song.duration) - positionMs).coerceAtLeast(0L) } ?: 0L
            return current + upcomingDurationMs(sections)
        }

        fun songCountLabel(count: Int): String = if (count == 1) "1 song" else "$count songs"

        private fun pinnedPrefixLength(upcoming: List<QueueRow>, pinned: Set<String>): Int {
            val remaining = pinned.toMutableSet()
            return upcoming.takeWhile { remaining.remove(it.song.id) }.size
        }

        private fun rowsWithKeys(items: List<Song>): List<QueueRow> {
            val seen = HashMap<String, Int>(items.size)
            return items.mapIndexed { index, song ->
                val occurrence = seen[song.id] ?: 0
                seen[song.id] = occurrence + 1
                QueueRow(index, song, if (occurrence == 0) song.id else "${song.id}#$occurrence")
            }
        }
    }
}
