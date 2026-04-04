package com.shyan.dreamin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shyan.dreamin.data.model.Song
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesDataStore(private val context: Context) {

    companion object {
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val RECENT_SEARCHES_KEY = stringPreferencesKey("recent_searches")
        private const val MAX_RECENT_SEARCHES = 8
        private val LAST_SONG_ID_KEY = stringPreferencesKey("last_song_id")
        private val LAST_SONG_TITLE_KEY = stringPreferencesKey("last_song_title")
        private val LAST_SONG_ARTIST_KEY = stringPreferencesKey("last_song_artist")
        private val LAST_SONG_ARTWORK_KEY = stringPreferencesKey("last_song_artwork")
        private val LAST_POSITION_KEY = longPreferencesKey("last_position_ms")
        private val CACHED_CHART_KEY = stringPreferencesKey("cached_chart")
        private val gson = Gson()
        private val songListType = object : TypeToken<List<Song>>() {}.type
    }

    val userName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[USER_NAME_KEY] ?: ""
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { prefs -> prefs[USER_NAME_KEY] = name }
    }

    val recentSearches: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[RECENT_SEARCHES_KEY]
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[RECENT_SEARCHES_KEY]
                ?.split("|")
                ?.filter { it.isNotBlank() && it != trimmed }
                ?: emptyList()
            val updated = (listOf(trimmed) + existing).take(MAX_RECENT_SEARCHES)
            prefs[RECENT_SEARCHES_KEY] = updated.joinToString("|")
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { prefs -> prefs.remove(RECENT_SEARCHES_KEY) }
    }

    data class LastSession(val song: Song, val positionMs: Long)

    val lastSession: Flow<LastSession?> = context.dataStore.data.map { prefs ->
        val id = prefs[LAST_SONG_ID_KEY] ?: return@map null
        val title = prefs[LAST_SONG_TITLE_KEY] ?: return@map null
        val artist = prefs[LAST_SONG_ARTIST_KEY] ?: return@map null
        val artwork = prefs[LAST_SONG_ARTWORK_KEY] ?: ""
        val position = prefs[LAST_POSITION_KEY] ?: 0L
        LastSession(Song(id = id, title = title, artist = artist, artworkUrl = artwork), position)
    }

    val cachedChart: Flow<List<Song>> = context.dataStore.data.map { prefs ->
        prefs[CACHED_CHART_KEY]?.let { json ->
            runCatching { gson.fromJson<List<Song>>(json, songListType) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun saveChartCache(songs: List<Song>) {
        context.dataStore.edit { prefs ->
            prefs[CACHED_CHART_KEY] = gson.toJson(songs)
        }
    }

    suspend fun saveLastSession(song: Song, positionMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_SONG_ID_KEY] = song.id
            prefs[LAST_SONG_TITLE_KEY] = song.title
            prefs[LAST_SONG_ARTIST_KEY] = song.artist
            prefs[LAST_SONG_ARTWORK_KEY] = song.artworkUrl
            prefs[LAST_POSITION_KEY] = positionMs
        }
    }
}
