package com.shyan.dreamin.data.queue

import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.model.TrackRepeatMode

sealed interface AutoEndAction {
    data object ReplayCurrent : AutoEndAction
    data class Play(val snapshot: QueueSnapshot, val song: Song) : AutoEndAction
    data class ContinueWithRadio(val seed: Song, val snapshot: QueueSnapshot, val slot: Int) : AutoEndAction
    data object Stop : AutoEndAction
}

object QueueTransitions {

    private const val EXTEND_THRESHOLD = 2

    fun onItemTransition(snapshot: QueueSnapshot, mediaId: String): QueueSnapshot {
        val items = snapshot.items
        val hint = snapshot.currentIndex
        val matchIdx = when {
            items.getOrNull(hint)?.id == mediaId -> hint
            items.getOrNull(hint + 1)?.id == mediaId -> hint + 1
            else -> items.indexOfFirst { it.id == mediaId }
        }
        if (matchIdx < 0) return snapshot
        val userQueued = snapshot.userQueuedIds.filter { it != mediaId }
        if (matchIdx == hint && userQueued == snapshot.userQueuedIds) return snapshot
        return snapshot.copy(currentIndex = matchIdx, userQueuedIds = userQueued)
    }

    fun onAutoEnd(
        snapshot: QueueSnapshot,
        shuffler: (List<Song>) -> List<Song> = { it.shuffled() }
    ): AutoEndAction = advance(snapshot, isManual = false, shuffler = shuffler)

    fun onUserNext(
        snapshot: QueueSnapshot,
        shuffler: (List<Song>) -> List<Song> = { it.shuffled() }
    ): AutoEndAction = advance(snapshot, isManual = true, shuffler = shuffler)

    private fun advance(
        snapshot: QueueSnapshot,
        isManual: Boolean,
        shuffler: (List<Song>) -> List<Song>
    ): AutoEndAction {
        val current = snapshot.currentSong ?: return AutoEndAction.Stop
        val advance = QueueOps.next(
            snapshot.items,
            snapshot.currentIndex,
            snapshot.repeatMode,
            snapshot.isShuffle,
            isManual,
            shuffler
        )
        return when (advance) {
            QueueAdvance.ReplayCurrent -> AutoEndAction.ReplayCurrent
            is QueueAdvance.PlayIndex -> {
                val items = advance.reshuffledItems ?: snapshot.items
                val song = items.getOrNull(advance.index) ?: return AutoEndAction.Stop
                AutoEndAction.Play(snapshot.copy(items = items, currentIndex = advance.index), song)
            }
            QueueAdvance.EndOfQueue ->
                if (snapshot.playlistActive) AutoEndAction.Stop
                else AutoEndAction.ContinueWithRadio(current, snapshot, snapshot.currentIndex + 1)
        }
    }

    fun onUserPrevious(snapshot: QueueSnapshot, positionMs: Long): AutoEndAction {
        if (snapshot.currentSong == null) return AutoEndAction.ReplayCurrent
        val advance = QueueOps.previous(
            snapshot.items,
            snapshot.currentIndex,
            positionMs,
            snapshot.repeatMode
        )
        if (advance !is QueueAdvance.PlayIndex) return AutoEndAction.ReplayCurrent
        val song = snapshot.items.getOrNull(advance.index) ?: return AutoEndAction.ReplayCurrent
        return AutoEndAction.Play(snapshot.copy(currentIndex = advance.index), song)
    }

    fun skipFailed(snapshot: QueueSnapshot, failedId: String): AutoEndAction {
        val failed = snapshot.currentSong?.takeIf { it.id == failedId } ?: return AutoEndAction.Stop
        val idx = snapshot.currentIndex
        val remaining = QueueOps.removeAt(snapshot.items, idx)
        val loops = snapshot.repeatMode == TrackRepeatMode.ALL
        val target = when {
            idx < remaining.size -> idx
            loops && remaining.isNotEmpty() -> 0
            else -> null
        }
        val pruned = snapshot.copy(items = remaining, userQueuedIds = snapshot.userQueuedIds.filter { it != failedId })
        if (target != null) return AutoEndAction.Play(pruned.copy(currentIndex = target), remaining[target])
        if (snapshot.playlistActive) return AutoEndAction.Stop
        return AutoEndAction.ContinueWithRadio(failed, pruned.copy(currentIndex = -1), idx)
    }

    fun replaceQueue(
        snapshot: QueueSnapshot,
        items: List<Song>,
        playlistActive: Boolean,
        start: Song,
        shuffler: (List<Song>) -> List<Song> = { it.shuffled() }
    ): QueueSnapshot {
        val startIndex = items.indexOfFirst { it.id == start.id }
        val base = if (startIndex >= 0) items else listOf(start) + items
        val index = startIndex.coerceAtLeast(0)
        val ids = base.map { it.id }.toSet()
        val userQueued = snapshot.userQueuedIds.filter { it in ids && it != start.id }
        if (!snapshot.isShuffle) {
            return snapshot.copy(items = base, currentIndex = index, playlistActive = playlistActive, userQueuedIds = userQueued, originalOrder = emptyList())
        }
        val others = base.filterIndexed { i, _ -> i != index }
        return snapshot.copy(
            items = listOf(base[index]) + shuffler(others),
            currentIndex = 0,
            playlistActive = playlistActive,
            userQueuedIds = userQueued,
            originalOrder = base
        )
    }

    fun toggleShuffle(
        snapshot: QueueSnapshot,
        shuffler: (List<Song>) -> List<Song> = { it.shuffled() }
    ): QueueSnapshot {
        if (snapshot.isShuffle) {
            val restored = QueueOps.unshuffleUpcoming(
                snapshot.items,
                snapshot.currentIndex,
                snapshot.originalOrder,
                snapshot.userQueuedIds
            )
            return snapshot.copy(items = restored, isShuffle = false, originalOrder = emptyList())
        }
        val shuffled = QueueOps.shuffleUpcoming(snapshot.items, snapshot.currentIndex, snapshot.userQueuedIds, shuffler)
        return snapshot.copy(items = shuffled, isShuffle = true, originalOrder = snapshot.items)
    }

    fun upcomingSong(snapshot: QueueSnapshot): Song? =
        QueueOps.upcomingIndex(
            snapshot.items,
            snapshot.currentIndex,
            snapshot.repeatMode,
            snapshot.isShuffle
        )?.let { snapshot.items.getOrNull(it) }

    fun needsExtension(snapshot: QueueSnapshot, isExtending: Boolean): Boolean {
        if (snapshot.playlistActive || isExtending || snapshot.currentSong == null) return false
        val remaining = snapshot.items.size - (snapshot.currentIndex + 1)
        return remaining <= EXTEND_THRESHOLD
    }

    fun appendUnique(snapshot: QueueSnapshot, songs: List<Song>): QueueSnapshot {
        if (snapshot.playlistActive) return snapshot
        val existingIds = snapshot.items.map { it.id }.toSet()
        val fresh = songs.filter { it.id !in existingIds }.distinctBy { it.id }
        if (fresh.isEmpty()) return snapshot
        return snapshot.copy(items = snapshot.items + fresh)
    }

    fun playAfterCurrent(snapshot: QueueSnapshot, song: Song): QueueSnapshot {
        val position = QueueOps.positionForPlay(snapshot.items, snapshot.currentIndex, song, null)
        return snapshot.copy(
            items = position.items,
            currentIndex = position.currentIndex,
            userQueuedIds = snapshot.userQueuedIds.filter { it != song.id }
        )
    }
}
