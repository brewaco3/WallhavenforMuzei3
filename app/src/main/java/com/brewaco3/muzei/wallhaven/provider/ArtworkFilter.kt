package com.brewaco3.muzei.wallhaven.provider

import com.brewaco3.muzei.wallhaven.provider.network.moshi.RankingArtwork

/**
 * Builds the Wallhaven search query and filters the results that come back.
 *
 * Deliberately free of Android APIs so [com.brewaco3.muzei.wallhaven.provider.ArtworkFilterTest]
 * can exercise it on the JVM.
 */
object ArtworkFilter {

    /** The minimum width/height sliders are stored in units of [PIXEL_STEP] pixels. */
    const val PIXEL_STEP = 10

    private const val SFW = 0
    private const val SKETCHY = 1
    private const val NSFW = 2

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

    /** Builds the `ratios` query parameter. Wallhaven accepts `landscape`, `portrait` and exact ratios. */
    fun ratiosQuery(selection: Set<String>): String =
        selection.filter { it.isNotBlank() }.sorted().joinToString(",")

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
