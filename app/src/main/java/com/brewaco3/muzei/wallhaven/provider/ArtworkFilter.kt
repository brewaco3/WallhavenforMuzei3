package com.brewaco3.muzei.wallhaven.provider

import com.brewaco3.muzei.wallhaven.provider.network.moshi.RankingArtwork

/**
 * Builds the Wallhaven search query and filters the results that come back.
 *
 * Every parameter here was checked against https://wallhaven.cc/help/api and against the live API,
 * because Wallhaven answers a malformed query in three different unhelpful ways: an empty result
 * set, a result set with the filter quietly dropped, or a 500 with no body.
 *
 * Deliberately free of Android APIs so [ArtworkFilterTest] can exercise it on the JVM.
 */
object ArtworkFilter {

    /** The minimum width/height sliders are stored in units of [PIXEL_STEP] pixels. */
    const val PIXEL_STEP = 10

    /**
     * `sorting` values the API accepts. `relevance` is valid but returns nothing without a search
     * query, so the settings screen does not offer it. `hot` is missing from the documentation but
     * both the API and the website accept it.
     */
    val SORTINGS = setOf("date_added", "relevance", "random", "views", "favorites", "toplist", "hot")

    /** Only the toplist has a time range; `topRange` is ignored under every other sorting. */
    const val SORTING_TOPLIST = "toplist"

    /**
     * `ratios` takes either these keywords or exact ratios such as `16x9`, never both: as soon as an
     * exact ratio is in the list the API drops the keyword, so "all wide, or square" would quietly
     * become "square". The settings screen keeps the two kinds apart for that reason, and
     * [ratiosQuery] mirrors the API's rule so the debug URL shows what really gets asked for.
     */
    val RATIO_KEYWORDS = setOf("landscape", "portrait")

    private const val SFW = 0
    private const val SKETCHY = 1
    private const val NSFW = 2

    private const val MULTIPLICATION_SIGN = '×'

    private val RESOLUTION = Regex("""\d+x\d+""")
    private val COLOR = Regex("""[0-9a-f]{6}""")

    /**
     * Builds the `purity` query parameter. NSFW is only ever requested when an API key is available,
     * because Wallhaven rejects NSFW queries without one.
     */
    fun purityQuery(selection: Set<String>, hasApiKey: Boolean): String {
        val flags = charArrayOf('0', '0', '0')
        val normalized = selection.map { it.lowercase() }
        if ("sfw" in normalized) flags[SFW] = '1'
        if ("sketchy" in normalized) flags[SKETCHY] = '1'
        if ("nsfw" in normalized && hasApiKey) flags[NSFW] = '1'
        // Wallhaven returns nothing when every flag is clear, so fall back to SFW
        if (flags.all { it == '0' }) flags[SFW] = '1'
        return String(flags)
    }

    /** Builds the `categories` query parameter. */
    fun categoryQuery(selection: Set<String>): String {
        val flags = charArrayOf('0', '0', '0')
        val normalized = selection.map { it.lowercase() }
        if ("general" in normalized) flags[0] = '1'
        if ("anime" in normalized) flags[1] = '1'
        if ("people" in normalized) flags[2] = '1'
        if (flags.all { it == '0' }) return "111"
        return String(flags)
    }

    /**
     * Builds the `sorting` query parameter. An unknown value is not an error to Wallhaven, it just
     * returns no results at all, so anything unrecognised falls back to the toplist.
     */
    fun sortingQuery(value: String?, fallback: String = SORTING_TOPLIST): String {
        val candidate = value?.trim()?.lowercase().orEmpty()
        return if (candidate in SORTINGS) candidate else fallback
    }

    /** Builds the `ratios` query parameter. The empty value is "Any", which is not sent at all. */
    fun ratiosQuery(selection: Set<String>): String {
        val values = selection.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val exact = values.filterNot { it in RATIO_KEYWORDS }
        // Mirrors the API: a mixed list means the keyword is ignored
        return (if (exact.isNotEmpty()) exact else values).distinct().sorted().joinToString(",")
    }

    /**
     * Builds the `atleast` query parameter. Wallhaven answers a resolution it cannot parse with a
     * 500 and no body, so a typo in the free text field would take the whole feed down. Anything
     * that is not a `WxH` pair is dropped instead.
     */
    fun atleastQuery(value: String?): String {
        val normalised = value?.trim()?.lowercase()?.replace(MULTIPLICATION_SIGN, 'x').orEmpty()
        return if (RESOLUTION.matches(normalised)) normalised else ""
    }

    /**
     * Builds the `colors` query parameter. Wallhaven wants six hex digits with no hash, and a value
     * it does not recognise matches nothing rather than failing, which would leave the feed empty
     * for good. Anything else is dropped instead.
     */
    fun colorsQuery(value: String?): String {
        val normalised = value?.trim()?.removePrefix("#")?.lowercase().orEmpty()
        return if (COLOR.matches(normalised)) normalised else ""
    }

    /** Builds the `seed` parameter. The API ignores it unless the sorting is random. */
    fun seedQuery(value: String?, sorting: String): String =
        if (sorting == "random") value?.trim().orEmpty() else ""

    /** The purities a candidate may have, dropping NSFW when there is no API key to request it with. */
    fun allowedPurities(selection: Set<String>, hasApiKey: Boolean): Set<String> =
        selection.map { it.lowercase() }
            .filterNot { it == "nsfw" && !hasApiKey }
            .toSet()
            .ifEmpty { setOf("sfw") }

    /**
     * Applies the minimum width/height settings. Slider values are in units of [PIXEL_STEP] pixels,
     * and a slider left at zero means "no minimum" for that dimension.
     */
    fun meetsMinimumSize(width: Int, height: Int, minWidth: Int, minHeight: Int): Boolean {
        if (minWidth > 0 && width < minWidth * PIXEL_STEP) return false
        if (minHeight > 0 && height < minHeight * PIXEL_STEP) return false
        return true
    }

    /**
     * Whether a candidate may be shown. The API has already applied purity, category and ratio
     * filters; this re-checks the ones that can change without the cached query changing.
     */
    fun matches(
        artwork: RankingArtwork,
        allowedPurities: Set<String>,
        minWidth: Int,
        minHeight: Int,
        existingTokens: Set<String>,
        deletedIds: Set<String>,
    ): Boolean =
        artwork.path.isNotBlank() &&
            artwork.id !in existingTokens &&
            artwork.id !in deletedIds &&
            artwork.purity.lowercase() in allowedPurities &&
            meetsMinimumSize(artwork.width, artwork.height, minWidth, minHeight)
}
