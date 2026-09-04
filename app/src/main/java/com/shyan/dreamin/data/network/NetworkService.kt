package com.shyan.dreamin.data.network

import com.shyan.dreamin.BuildConfig
import com.shyan.dreamin.data.model.*

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

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
    val BASE_URL = "http://10.0.2.2:8080/"

    private val connectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val orig = chain.request()
            val host = orig.url.host
            val builder = orig.newBuilder()
            if (host.contains("jiosaavn") || host.contains("saavncdn")) {
                builder.header("Referer", "https://www.jiosaavn.com/")
                builder.header("Origin", "https://www.jiosaavn.com")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            } else if (host.contains("lrclib")) {
                builder.header("User-Agent", "DreaminApp/1.0 (https://github.com/shyan/dreamin)")
            }
            chain.proceed(builder.build())
        }
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Dedicated OkHttpClient optimized for continuous audio media streaming.
     * Features resilient 30-second read and write timeouts to prevent socket stalls
     * during ExoPlayer buffer windows.
     */
    val mediaHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val api: MusicApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicApi::class.java)
    }

    private val networkScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    fun prewarmSockets() {
        networkScope.launch {
            val prewarmHosts = listOf(
                "https://www.jiosaavn.com/api.php",
                "https://ac.cf.saavncdn.com",
                "https://i.scdn.co"
            )
            for (url in prewarmHosts) {
                runCatching {
                    val req = okhttp3.Request.Builder()
                        .url(url)
                        .head()
                        .build()
                    httpClient.newCall(req).execute().use { }
                }
            }
        }
    }
}
