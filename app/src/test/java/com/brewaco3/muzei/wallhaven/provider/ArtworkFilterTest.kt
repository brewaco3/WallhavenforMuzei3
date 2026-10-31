package com.brewaco3.muzei.wallhaven.provider

import com.brewaco3.muzei.wallhaven.provider.network.moshi.RankingArtwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkFilterTest {

    /** Mirrors pref_updateMode_entryValues. Every one of these has to reach the API unchanged. */
    private val sortingsOfferedBySettings =
        setOf("toplist", "hot", "date_added", "views", "favorites", "random")

    /** Mirrors pref_aspectRatio_entryValues. */
    private val ratiosOfferedBySettings =
        setOf("", "landscape", "portrait", "16x9", "16x10", "3x2", "4x3", "5x4",
            "21x9", "32x9", "48x9", "9x16", "10x16", "9x18", "1x1")

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

    /**
     * Wallhaven answers an unknown sorting with an empty result set rather than an error, so the
     * whole feed would go quiet if one of the settings values ever drifted from the accepted list.
     */
    @Test
    fun `every sorting the settings offer is one the api accepts`() {
        assertEquals(emptySet<String>(), sortingsOfferedBySettings - ArtworkFilter.SORTINGS)
    }

    @Test
    fun `an unknown sorting falls back to the toplist`() {
        assertEquals("toplist", ArtworkFilter.sortingQuery(null))
        assertEquals("toplist", ArtworkFilter.sortingQuery(""))
        assertEquals("toplist", ArtworkFilter.sortingQuery("most_viewed"))
        assertEquals("views", ArtworkFilter.sortingQuery("views"))
        assertEquals("date_added", ArtworkFilter.sortingQuery("  DATE_ADDED  "))
    }

    @Test
    fun `the seed only travels with random sorting`() {
        assertEquals("abc123", ArtworkFilter.seedQuery("abc123", "random"))
        assertEquals("", ArtworkFilter.seedQuery("abc123", "toplist"))
        assertEquals("", ArtworkFilter.seedQuery(null, "random"))
    }

    /**
     * The API keeps exact ratios and drops the keyword when a list contains both, so the query has
     * to mirror that rather than promise the user something it will not do.
     */
    @Test
    fun `ratios that the api cannot combine are reduced the way the api reduces them`() {
        assertEquals("", ArtworkFilter.ratiosQuery(setOf("")))
        assertEquals("landscape", ArtworkFilter.ratiosQuery(setOf("landscape")))
        assertEquals("landscape,portrait", ArtworkFilter.ratiosQuery(setOf("portrait", "landscape")))
        // The keyword is what the API throws away, so it is what gets left out here
        assertEquals("21x9", ArtworkFilter.ratiosQuery(setOf("portrait", "21x9")))
        assertEquals("1x1", ArtworkFilter.ratiosQuery(setOf("landscape", "1x1")))
        assertEquals("1x1,21x9", ArtworkFilter.ratiosQuery(setOf("landscape", "21x9", "1x1", "")))
    }

    @Test
    fun `every ratio the settings offer is one the api understands`() {
        val exact = Regex("""\d+x\d+""")
        val unusable = ratiosOfferedBySettings.filterNot {
            it.isEmpty() || it in ArtworkFilter.RATIO_KEYWORDS || exact.matches(it)
        }
        assertEquals(emptyList<String>(), unusable)
    }

    /**
     * A resolution the API cannot parse comes back as a 500 with no body, which would take the whole
     * feed down, so anything that is not a WxH pair is dropped before it is ever sent.
     */
    @Test
    fun `only a width by height resolution is sent`() {
        assertEquals("1920x1080", ArtworkFilter.atleastQuery("1920x1080"))
        assertEquals("1920x1080", ArtworkFilter.atleastQuery(" 1920X1080 "))
        assertEquals("1920x1080", ArtworkFilter.atleastQuery("1920×1080"))
        assertEquals("", ArtworkFilter.atleastQuery("abc"))
        assertEquals("", ArtworkFilter.atleastQuery("1920"))
        assertEquals("", ArtworkFilter.atleastQuery("1920x1080,2560x1440"))
        assertEquals("", ArtworkFilter.atleastQuery(null))
        assertEquals("", ArtworkFilter.atleastQuery("   "))
    }

    /**
     * A colour the API does not recognise matches nothing rather than failing, so a stray hash would
     * leave the feed permanently empty.
     */
    @Test
    fun `only a six digit colour is sent`() {
        assertEquals("663399", ArtworkFilter.colorsQuery("663399"))
        assertEquals("663399", ArtworkFilter.colorsQuery("#663399"))
        assertEquals("663399", ArtworkFilter.colorsQuery("  #663399  "))
        assertEquals("", ArtworkFilter.colorsQuery("663399ff"))
        assertEquals("", ArtworkFilter.colorsQuery("zzz"))
        assertEquals("", ArtworkFilter.colorsQuery(null))
        assertEquals("", ArtworkFilter.colorsQuery(""))
    }

    @Test
    fun `nsfw candidates are dropped when there is no api key`() {
        assertEquals(
            setOf("sfw", "sketchy"),
            ArtworkFilter.allowedPurities(setOf("sfw", "sketchy", "nsfw"), false)
        )
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

        assertTrue(matches(candidate))
        assertFalse(matches(candidate, existingTokens = setOf("abc123")))
        assertFalse(matches(candidate, deletedIds = setOf("abc123")))
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
