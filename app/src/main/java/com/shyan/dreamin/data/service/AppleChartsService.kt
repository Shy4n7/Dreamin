package com.shyan.dreamin.data.service

import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.recommendation.OfficialSongFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AppleChartsService {

    /**
     * Fetches Apple Music's official real-time Top Most Played Songs Chart in India.
     * 100% Free, real-time live streaming data, zero API keys.
     */
    suspend fun fetchLiveAppleTopCharts(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()

        // 1. Apple Music Official Real-Time Most Played Songs (India)
        try {
            val url = URL("https://rss.applemarketingtools.com/api/v2/in/music/most-played/50/songs.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                connectTimeout = 5000
                readTimeout = 5000
            }
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val root = JSONObject(text)
            val feedResults = root.optJSONObject("feed")?.optJSONArray("results")
            if (feedResults != null) {
                for (i in 0 until feedResults.length()) {
                    val item = feedResults.getJSONObject(i)
                    val name = item.optString("name", "")
                    val artist = item.optString("artistName", "")
                    val normKey = OfficialSongFilter.normalizeSongKey(name)
                    if (name.isNotBlank() && normKey.length >= 3 && !seen.contains(normKey)) {
                        seen.add(normKey)
                        results.add(Pair(name, artist))
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Apple Music Real-Time Tamil Top Hits Feed
        try {
            val url = URL("https://itunes.apple.com/search?term=Top+Tamil+Songs+2025&entity=song&country=IN&limit=25")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                connectTimeout = 5000
                readTimeout = 5000
            }
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val root = JSONObject(text)
            val searchResults = root.optJSONArray("results")
            if (searchResults != null) {
                for (i in 0 until searchResults.length()) {
                    val item = searchResults.getJSONObject(i)
                    val name = item.optString("trackName", "")
                    val artist = item.optString("artistName", "")
                    val normKey = OfficialSongFilter.normalizeSongKey(name)
                    if (name.isNotBlank() && normKey.length >= 3 && !seen.contains(normKey)) {
                        seen.add(normKey)
                        results.add(Pair(name, artist))
                    }
                }
            }
        } catch (_: Exception) {}

        results
    }
}
