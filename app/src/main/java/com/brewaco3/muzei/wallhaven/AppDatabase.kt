package com.brewaco3.muzei.wallhaven

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.brewaco3.muzei.wallhaven.settings.blockArtist.BlockedArtistDao
import com.brewaco3.muzei.wallhaven.settings.blockArtist.BlockArtistEntity
import com.brewaco3.muzei.wallhaven.settings.deleteArtwork.DeletedArtworkIdDao
import com.brewaco3.muzei.wallhaven.settings.deleteArtwork.DeletedArtworkIdEntity

@Database(
    entities = [DeletedArtworkIdEntity::class, BlockArtistEntity::class], version = 1, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "word_database"
                    ).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }

    abstract fun deletedArtworkIdDao(): DeletedArtworkIdDao
    abstract fun blockedArtistDao(): BlockedArtistDao
}
