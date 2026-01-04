package com.brewaco3.muzei.wallhaven.settings.blockArtist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BlockedArtistDao {
    @Query("SELECT artistId FROM BlockArtistEntity")
    fun getAllIds(): List<String>

    @Query("SELECT EXISTS(SELECT * FROM BlockArtistEntity WHERE artistId = (:artistId))")
    fun isRowIsExist(artistId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertBlockedArtistId(blockedArtistIds: List<BlockArtistEntity>)
}
