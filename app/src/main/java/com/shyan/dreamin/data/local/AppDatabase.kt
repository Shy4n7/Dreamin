package com.shyan.dreamin.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shyan.dreamin.data.local.dao.FavoriteDao
import com.shyan.dreamin.data.local.dao.PlayHistoryDao
import com.shyan.dreamin.data.local.dao.PlaylistDao
import com.shyan.dreamin.data.local.entity.FavoriteEntity
import com.shyan.dreamin.data.local.entity.PlayHistoryEntity
import com.shyan.dreamin.data.local.entity.PlaylistEntity
import com.shyan.dreamin.data.local.entity.PlaylistSongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        PlayHistoryEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao

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
                // WAL mode: reads don't block writes, much faster concurrent access
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()

        /**
         * Warm up the database connection on a background thread so the first
         * Room query doesn't pay the connection-open cost on the main thread.
         */
        fun warmUp(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                getInstance(context).openHelper.readableDatabase
            }
        }
    }
}
