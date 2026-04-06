package com.shyan.dreamin.data.network

import com.shyan.dreamin.BuildConfig
import com.shyan.dreamin.data.model.*

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface MusicApi {
    @GET("api/mobile/health")
    suspend fun health(): Map<String, String>

    @GET("api/mobile/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 15
    ): SearchResponse

    @GET("api/mobile/chart")
    suspend fun getChart(@Query("language") language: String = "tamil"): ChartResponse

    @GET("api/mobile/play")
    suspend fun recordPlay(
        @Query("id") id: String,
        @Query("artist") artist: String,
        @Query("title") title: String,
    ): PlayResponse

    @GET("api/mobile/up_next")
    suspend fun getUpNext(
        @Query("song_id") songId: String,
        @Query("exclude") exclude: String = "",
        @Query("limit") limit: Int = 10
    ): UpNextResponse

    @GET("api/mobile/recommend")
    suspend fun getRecommendations(@Query("song_id") songId: String): RecommendResponse

    @POST("api/mobile/register")
    suspend fun registerUser(@Body body: RegisterRequest): Map<String, String>
}

object NetworkService {
    val BASE_URL = "https://tjlsaxykmlbp.ap-southeast-1.clawcloudrun.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
