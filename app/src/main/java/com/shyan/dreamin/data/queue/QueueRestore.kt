package com.shyan.dreamin.data.queue

import com.shyan.dreamin.data.local.UserPreferencesDataStore.LastSession
import com.shyan.dreamin.data.local.UserPreferencesDataStore.SavedQueue

data class ResumeTarget(val snapshot: QueueSnapshot, val positionMs: Long) {
    val song get() = snapshot.currentSong
}

object QueueRestore {

    fun toSaved(snapshot: QueueSnapshot): SavedQueue? {
        if (snapshot.items.isEmpty() || snapshot.currentSong == null) return null
        return SavedQueue(
            items = snapshot.items,
            currentIndex = snapshot.currentIndex,
            playlistActive = snapshot.playlistActive,
            userQueuedIds = snapshot.userQueuedIds
        )
    }

    fun resumeTarget(inMemory: QueueSnapshot, saved: SavedQueue?, session: LastSession?): ResumeTarget? {
        val base = when {
            inMemory.currentSong != null -> inMemory
            else -> fromSaved(inMemory, saved, session) ?: return null
        }
        val current = base.currentSong ?: return null
        val position = if (session != null && session.song.id == current.id) session.positionMs.coerceAtLeast(0L) else 0L
        return ResumeTarget(base, position)
    }

    private fun fromSaved(inMemory: QueueSnapshot, saved: SavedQueue?, session: LastSession?): QueueSnapshot? {
        val modes = { s: QueueSnapshot -> s.copy(repeatMode = inMemory.repeatMode, isShuffle = inMemory.isShuffle) }
        if (saved != null && saved.items.isNotEmpty()) {
            val index = if (session != null) saved.resumeIndexFor(session.song.id) else -1
            val resolved = if (index >= 0) index else saved.currentIndex.takeIf { it in saved.items.indices }
            if (resolved != null) {
                return modes(QueueSnapshot(saved.items, resolved, saved.userQueuedIds, saved.playlistActive))
            }
        }
        if (session == null) return null
        return modes(QueueSnapshot(items = listOf(session.song), currentIndex = 0))
    }
}
