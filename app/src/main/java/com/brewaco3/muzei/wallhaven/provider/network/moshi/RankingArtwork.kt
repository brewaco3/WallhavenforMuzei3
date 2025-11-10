/*
 *     This file is part of PixivforMuzei3.
 *
 *     PixivforMuzei3 is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program  is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.brewaco3.muzei.wallhaven.provider.network.moshi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RankingArtwork(
    val id: String,
    val url: String,
    val path: String,
    val purity: String,
    val category: String?,
    @Json(name = "dimension_x") val width: Int,
    @Json(name = "dimension_y") val height: Int,
    val resolution: String?,
    val ratio: String?,
    @Json(name = "file_size") val fileSize: Long?,
    @Json(name = "file_type") val fileType: String?,
    @Json(name = "created_at") val createdAt: String?,
    val colors: List<String>?,
    val tags: List<WallpaperTag>?,
    val uploader: WallpaperUploader?,
    val views: Int,
    val favorites: Int,
    val source: String?
)

@JsonClass(generateAdapter = true)
data class WallpaperTag(
    val id: Int?,
    val name: String?,
    val alias: String?,
    val category: String?,
    val purity: String?
)

@JsonClass(generateAdapter = true)
data class WallpaperUploader(
    val username: String?
)
