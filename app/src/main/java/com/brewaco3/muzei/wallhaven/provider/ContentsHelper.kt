package com.brewaco3.muzei.wallhaven.provider

import com.brewaco3.muzei.wallhaven.provider.network.RestClient
import com.brewaco3.muzei.wallhaven.provider.network.WallhavenSearchService
import com.brewaco3.muzei.wallhaven.provider.network.moshi.Contents

/**
 * Runs one Wallhaven search query.
 *
 * Page numbers are passed in so the caller owns the paging loop, and every optional parameter is
 * nullable so it is left out of the URL instead of being sent empty.
 */
class ContentsHelper(
    private val sorting: String,
    private val purity: String,
    private val categories: String,
    private val tagFilter: String? = null,
    private val atleast: String? = null,
    private val ratios: String? = null,
    private val topRange: String? = null,
    private val order: String = "desc",
    private val seed: String? = null,
    private val colors: String? = null
) {
    private val service = RestClient.rankingService

    fun getPage(page: Int): Contents =
        service.getSearchResults(
            sorting, purity, categories, page, order, tagFilter, atleast, ratios, topRange, seed, colors
        ).execute().body() ?: throw IllegalStateException("Empty Wallhaven response")
}
