package com.shyan.nigharam.data.network

import com.shyan.nigharam.data.model.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ── Retrofit interface — mirrors the Daydreamin FastAPI endpoints exactly ──────

interface MusicApi {

    // Health check
    @GET("api/mobile/health")
    suspend fun health(): Map<String, String>

    // Search songs
    @GET("api/mobile/search")
    suspend fun search(@Query("q") query: String): SearchResponse

    // Trending / charts on launch
    @GET("api/mobile/chart")
    suspend fun getChart(): ChartResponse

    // Get stream URL for a song (also records transitions for recommendations)
    @GET("api/mobile/play")
    suspend fun getStreamUrl(
        @Query("id") id: String,
        @Query("artist") artist: String,
        @Query("title") title: String,
        @Query("previous_song_id") previousSongId: String? = null
    ): PlayResponse

    // Up Next queue suggestions
    @GET("api/mobile/up_next")
    suspend fun getUpNext(
        @Query("song_id") songId: String,
        @Query("limit") limit: Int = 10
    ): UpNextResponse

    // Grouped recommendations
    @GET("api/mobile/recommend")
    suspend fun getRecommendations(@Query("song_id") songId: String): RecommendResponse
}

// ── Singleton — change BASE_URL to your server IP ─────────────────────────────

object NetworkService {

    // ⚠️  Change this to your FastAPI server address:
    //   - Emulator  → "http://10.0.2.2:499/"
    //   - Real phone → "http://192.168.x.x:499/"   (your PC's local IP)
    var BASE_URL = "https://tjlsaxykmlbp.ap-southeast-1.clawcloudrun.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)   // streams take time to resolve
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: MusicApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicApi::class.java)
    }
}
