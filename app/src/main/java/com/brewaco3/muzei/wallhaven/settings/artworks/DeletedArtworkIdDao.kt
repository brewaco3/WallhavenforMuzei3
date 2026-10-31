package com.brewaco3.muzei.wallhaven.settings.artworks

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeletedArtworkIdDao {
    @Query("SELECT artworkId FROM DeletedArtworkIdEntity")
    fun getAllIds(): List<String>

    /**
     * Records artworks the user deleted so the worker never downloads them again.
     * IGNORE because deleting an artwork that was already deleted is not an error.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertDeletedArtworkId(deletedArtworkIds: List<DeletedArtworkIdEntity>)
}
