package com.brewaco3.muzei.wallhaven.provider

import com.brewaco3.muzei.wallhaven.provider.network.WallhavenRankingFeedJsonService
import com.brewaco3.muzei.wallhaven.provider.network.RestClient
import com.brewaco3.muzei.wallhaven.provider.network.moshi.Contents

class ContentsHelper(
    private val updateMode: String,
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
    private lateinit var contents: Contents
    private val service = RestClient.getRetrofitRankingInstance().create(
        WallhavenRankingFeedJsonService::class.java
    )
    private var pageNumber = 1
    private var lastPage = 1

    fun getNewContents(): Contents {
        pageNumber = 1
        val call = service.getSearchResults(updateMode, purity, categories, pageNumber, order, tagFilter, atleast, ratios, topRange, seed, colors)
        contents = call.execute().body() ?: throw IllegalStateException("Empty Wallhaven response")
        lastPage = contents.meta.lastPage
        return contents
    }

    fun getNextContents(): Contents {
        pageNumber = if (pageNumber < lastPage) {
            pageNumber + 1
        } else {
            1
        }
        val call = service.getSearchResults(updateMode, purity, categories, pageNumber, order, tagFilter, atleast, ratios, topRange, seed, colors)
        contents = call.execute().body() ?: throw IllegalStateException("Empty Wallhaven response")
        lastPage = contents.meta.lastPage
        return contents
    }

    fun getContents() = contents
}
