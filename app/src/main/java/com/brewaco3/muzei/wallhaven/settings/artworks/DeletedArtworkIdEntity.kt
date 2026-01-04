package com.brewaco3.muzei.wallhaven.settings.artworks

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class DeletedArtworkIdEntity(
        @PrimaryKey val artworkId: String
)
