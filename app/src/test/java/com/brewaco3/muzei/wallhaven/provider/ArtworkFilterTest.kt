package com.brewaco3.muzei.wallhaven.provider

import com.brewaco3.muzei.wallhaven.provider.network.moshi.RankingArtwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkFilterTest {

    private fun artwork(
        id: String = "abc123",
        purity: String = "sfw",
        width: Int = 1920,
        height: Int = 1080,
        path: String = "https://w.wallhaven.cc/full/ab/wallhaven-$id.jpg",
    ) = RankingArtwork(
        id = id,
        url = "https://wallhaven.cc/w/$id",
        path = path,
        purity = purity,
        category = "general",
        width = width,
        height = height,
        resolution = "${width}x$height",
        ratio = "1.78",
        fileSize = 1_000_000,
        fileType = "image/jpeg",
        createdAt = "2024-05-01 12:00:00",
        colors = emptyList(),
        tags = emptyList(),
        uploader = null,
        views = 100,
        favorites = 10,
        source = null,
    )

    @Test
    fun `nsfw is never requested without an api key`() {
        val selection = setOf("sfw", "sketchy", "nsfw")

        assertEquals("110", ArtworkFilter.purityQuery(selection, hasApiKey = false))
        assertEquals("111", ArtworkFilter.purityQuery(selection, hasApiKey = true))
    }

    @Test
    fun `an empty purity selection still asks for something`() {
        assertEquals("100", ArtworkFilter.purityQuery(emptySet(), hasApiKey = false))
        assertEquals("100", ArtworkFilter.purityQuery(setOf("nsfw"), hasApiKey = false))
    }

    @Test
    fun `an empty category selection means every category`() {
        assertEquals("111", ArtworkFilter.categoryQuery(emptySet()))
        assertEquals("011", ArtworkFilter.categoryQuery(setOf("anime", "people")))
    }

    @Test
    fun `ratios drop the any placeholder and stay stable`() {
        assertEquals("", ArtworkFilter.ratiosQuery(setOf("")))
        assertEquals("21x9,portrait", ArtworkFilter.ratiosQuery(setOf("portrait", "", "21x9")))
    }

    @Test
    fun `nsfw candidates are dropped when there is no api key`() {
        assertEquals(setOf("sfw", "sketchy"), ArtworkFilter.allowedPurities(setOf("sfw", "sketchy", "nsfw"), false))
        assertEquals(setOf("sfw"), ArtworkFilter.allowedPurities(emptySet(), true))
    }

    @Test
    fun `slider values are read as tens of pixels`() {
        // Slider at 0 disables the check for that dimension
        assertTrue(ArtworkFilter.meetsMinimumSize(100, 100, minWidth = 0, minHeight = 0))
        assertTrue(ArtworkFilter.meetsMinimumSize(1920, 1080, minWidth = 192, minHeight = 0))
        assertFalse(ArtworkFilter.meetsMinimumSize(1919, 1080, minWidth = 192, minHeight = 0))
        // A portrait wallpaper only has to satisfy the height it was asked for
        assertTrue(ArtworkFilter.meetsMinimumSize(1080, 1920, minWidth = 0, minHeight = 192))
        assertFalse(ArtworkFilter.meetsMinimumSize(1080, 1900, minWidth = 0, minHeight = 192))
        assertFalse(ArtworkFilter.meetsMinimumSize(1000, 1000, minWidth = 192, minHeight = 192))
    }

    @Test
    fun `already downloaded and deleted artworks are never offered again`() {
        val candidate = artwork()
        val defaults = arrayOf<Set<String>>(emptySet(), emptySet())

        assertTrue(matches(candidate, existingTokens = defaults[0], deletedIds = defaults[1]))
        assertFalse(matches(candidate, existingTokens = setOf("abc123"), deletedIds = emptySet()))
        assertFalse(matches(candidate, existingTokens = emptySet(), deletedIds = setOf("abc123")))
    }

    @Test
    fun `candidates have to match purity and size to be offered`() {
        assertFalse(matches(artwork(purity = "nsfw"), allowedPurities = setOf("sfw")))
        assertFalse(matches(artwork(width = 800, height = 600), minWidth = 192))
        assertFalse(matches(artwork(path = "")))
    }

    private fun matches(
        candidate: RankingArtwork,
        allowedPurities: Set<String> = setOf("sfw", "sketchy"),
        minWidth: Int = 0,
        minHeight: Int = 0,
        existingTokens: Set<String> = emptySet(),
        deletedIds: Set<String> = emptySet(),
    ) = ArtworkFilter.matches(
        candidate, allowedPurities, minWidth, minHeight, existingTokens, deletedIds
    )
}
