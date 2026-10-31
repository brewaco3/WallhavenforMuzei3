package com.brewaco3.muzei.wallhaven.provider.network

import com.brewaco3.muzei.wallhaven.provider.network.moshi.Contents
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/** Wallhaven's `GET /search` endpoint, also used for the toplist and hot feeds. */
interface WallhavenSearchService {
    @GET("search")
    fun getSearchResults(
        @Query("sorting") sorting: String,
        @Query("purity") purity: String,
        @Query("categories") categories: String,
        @Query("page") page: Int,
        @Query("order") order: String,
        @Query("q") query: String,
        @Query("atleast") atleast: String,
        @Query("ratios") ratios: String,
        @Query("topRange") topRange: String,
        @Query("seed") seed: String,
        @Query("colors") colors: String
    ): Call<Contents>
}
