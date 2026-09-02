package com.shyan.dreamin.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shyan.dreamin.data.local.dao.DownloadDao
import com.shyan.dreamin.data.local.dao.FavoriteDao
import com.shyan.dreamin.data.local.dao.PlayHistoryDao
import com.shyan.dreamin.data.local.dao.PlaylistDao
import com.shyan.dreamin.data.local.entity.DownloadedSongEntity
import com.shyan.dreamin.data.local.entity.FavoriteEntity
import com.shyan.dreamin.data.local.entity.PlayHistoryEntity
import com.shyan.dreamin.data.local.entity.PlaylistEntity
import com.shyan.dreamin.data.local.dao.ImportMatchDao
import com.shyan.dreamin.data.local.entity.ImportMatchEntity
import com.shyan.dreamin.data.local.entity.PlaylistSongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlayHistoryEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        DownloadedSongEntity::class,
        ImportMatchEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun importMatchDao(): ImportMatchDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }

        private fun buildDatabase(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dreamin_db")
                .fallbackToDestructiveMigration()
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA synchronous = NORMAL;")
                        db.execSQL("PRAGMA temp_store = MEMORY;")
                        db.execSQL("PRAGMA cache_size = -4000;")
                        db.execSQL("PRAGMA mmap_size = 268435456;") // 256MB kernel mmap
                    }
                })
                .build()

        private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Warm up the database connection on a background thread so the first
         * Room query doesn't pay the connection-open cost on the main thread.
         */
        fun warmUp(context: Context) {
            dbScope.launch {
                try {
                    getInstance(context).openHelper.readableDatabase
                } catch (e: Exception) {
                    android.util.Log.w("AppDatabase", "DB warmup error: ${e.message}")
                }
            }
        }
    }
}
