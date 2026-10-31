package com.brewaco3.muzei.wallhaven

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.brewaco3.muzei.wallhaven.settings.artworks.DeletedArtworkIdDao
import com.brewaco3.muzei.wallhaven.settings.artworks.DeletedArtworkIdEntity

@Database(
    entities = [DeletedArtworkIdEntity::class], version = 2, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        // The name is a leftover from the fork; renaming it would drop every
                        // user's list of deleted artwork, so it stays
                        "word_database"
                    )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    abstract fun deletedArtworkIdDao(): DeletedArtworkIdDao
}
