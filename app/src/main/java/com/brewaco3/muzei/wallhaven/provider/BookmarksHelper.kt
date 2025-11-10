package com.brewaco3.muzei.wallhaven.provider

import com.brewaco3.muzei.wallhaven.provider.network.PixivAuthFeedJsonService
import com.brewaco3.muzei.wallhaven.provider.network.RestClient
import com.brewaco3.muzei.wallhaven.provider.network.moshi.Illusts

class BookmarksHelper(private val userId: String) {
    private lateinit var illusts: Illusts
    private val service: PixivAuthFeedJsonService = RestClient.getRetrofitAuthInstance()
        .create(PixivAuthFeedJsonService::class.java)

    fun getNewPublicBookmarks(maxBookmarkId: String): Illusts {
        val call = service.getPublicBookmarkOffsetJson(userId, maxBookmarkId)
        illusts = call.execute().body()!!
        return illusts
    }

    fun getNewPrivateIllusts(maxBookmarkId: String): Illusts {
        val call = service.getPrivateBookmarkOffsetJson(userId, maxBookmarkId)
        illusts = call.execute().body()!!
        return illusts
    }

    fun getNewPublicBookmarks(): Illusts {
        val call = service.getPublicBookmarkJson(userId)
        illusts = call.execute().body()!!
        return illusts
    }

    fun getNewPrivateIllusts(): Illusts {
        val call = service.getPrivateBookmarkJson(userId)
        illusts = call.execute().body()!!
        return illusts
    }

    fun getNextBookmarks(): Illusts {
        val call = service.getNextUrl(illusts.next_url)
        illusts = call.execute().body()!!
        return illusts
    }

    fun getBookmarks() = illusts
}
