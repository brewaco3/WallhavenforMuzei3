package com.brewaco3.muzei.wallhaven.settings.deleteArtwork

import android.net.Uri

data class ArtworkItem(
    val token: String,
    val persistent_uri: Uri,
) {
    var selected: Boolean = false
}
