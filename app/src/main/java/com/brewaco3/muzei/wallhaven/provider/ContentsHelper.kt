package com.brewaco3.muzei.wallhaven.provider

import com.brewaco3.muzei.wallhaven.provider.network.RestClient
import com.brewaco3.muzei.wallhaven.provider.network.WallhavenSearchService
import com.brewaco3.muzei.wallhaven.provider.network.moshi.Contents

/** Runs one Wallhaven search query. Page numbers are passed in so the caller owns the paging loop. */
class ContentsHelper(
    private val sorting: String,
    private val purity: String,
    private val categories: String,
    private val tagFilter: String = "",
    private val atleast: String = "",
    private val ratios: String = "",
    private val topRange: String = "1M",
    private val order: String = "desc",
    private val seed: String = "",
    private val colors: String = ""
) {
    private val service = RestClient.rankingService

    fun getPage(page: Int): Contents =
        service.getSearchResults(
            sorting, purity, categories, page, order, tagFilter, atleast, ratios, topRange, seed, colors
        ).execute().body() ?: throw IllegalStateException("Empty Wallhaven response")
}
