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
package com.brewaco3.muzei.wallhaven.provider.network

import com.brewaco3.muzei.wallhaven.PixivProviderConst.OAUTH_URL
import com.brewaco3.muzei.wallhaven.PixivProviderConst.PIXIV_API_HOST_URL
import com.brewaco3.muzei.wallhaven.PixivProviderConst.PIXIV_IMAGE_URL
import com.brewaco3.muzei.wallhaven.PixivProviderConst.PIXIV_RANKING_URL
import com.brewaco3.muzei.wallhaven.provider.network.interceptor.StandardAuthHttpHeaderInterceptor
import com.brewaco3.muzei.wallhaven.provider.network.interceptor.StandardImageHttpHeaderInterceptor
import com.brewaco3.muzei.wallhaven.provider.network.interceptor.WallhavenApiKeyInterceptor
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RestClient {
    private val okHttpClientAuthBuilder
        get() = OkHttpSingleton.getInstance().newBuilder()
            .apply {
                addInterceptor(WallhavenApiKeyInterceptor())
                addInterceptor(StandardAuthHttpHeaderInterceptor())
            }

    // Used for acquiring Ranking JSON
    fun getRetrofitRankingInstance(): Retrofit {
        val okHttpClientRanking = OkHttpSingleton.getInstance().newBuilder()
            .addInterceptor(Interceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                    .header("Accept", "application/json")
                chain.proceed(requestBuilder.build())
            })
            .addInterceptor(WallhavenApiKeyInterceptor())
            .build()
        return Retrofit.Builder()
            .client(okHttpClientRanking)
            .baseUrl(PIXIV_RANKING_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    // Used for acquiring auth feed mode JSON
    fun getRetrofitAuthInstance(): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClientAuthBuilder.build())
            .baseUrl(PIXIV_API_HOST_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    // Used to add artworks to your list of bookmarks
    fun getRetrofitBookmarkInstance(): Retrofit {
        return Retrofit.Builder()
            .client(OkHttpSingleton.getInstance().newBuilder()
                .addInterceptor(WallhavenApiKeyInterceptor())
                .build())
            .baseUrl(PIXIV_API_HOST_URL)
            .build()
    }

    // Downloads images from any source
    fun getRetrofitImageInstance(): Retrofit {
        val imageHttpClient = OkHttpSingleton.getInstance().newBuilder()
            .addInterceptor(WallhavenApiKeyInterceptor())
            .addInterceptor(StandardImageHttpHeaderInterceptor())
            .build()
        return Retrofit.Builder()
            .client(imageHttpClient)
            .baseUrl(PIXIV_IMAGE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    // Used for getting an accessToken from a refresh token or username / password
    @JvmStatic
    fun getRetrofitOauthInstance(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(OAUTH_URL)
            .client(okHttpClientAuthBuilder.build())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }
}
