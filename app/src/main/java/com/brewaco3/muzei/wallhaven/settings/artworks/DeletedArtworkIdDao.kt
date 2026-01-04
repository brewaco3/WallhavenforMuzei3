package com.brewaco3.muzei.wallhaven.settings.artworks

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeletedArtworkIdDao {
    @Query("SELECT artworkId FROM DeletedArtworkIdEntity")
    fun getAllIds(): List<String>

    // Returns true if the passed artworkId is present in the table
    @Query("SELECT EXISTS(SELECT * FROM DeletedArtworkIdEntity WHERE artworkId = (:artworkId))")
    fun isRowIsExist(artworkId : String) : Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertDeletedArtworkId(deletedArtworkIds: List<DeletedArtworkIdEntity>)
}
