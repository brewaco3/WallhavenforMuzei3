package com.brewaco3.muzei.wallhaven.settings.deleteArtwork

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class DeletedArtworkIdEntity(
        @PrimaryKey val artworkId: String
)
