package com.brewaco3.muzei.wallhaven.provider.network

import com.brewaco3.muzei.wallhaven.WallhavenProviderConst.WALLHAVEN_API_BASE_URL
import com.brewaco3.muzei.wallhaven.provider.network.interceptor.WallhavenApiKeyInterceptor
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RestClient {

    /** Search requests always want JSON back and always carry the API key when one is configured. */
    val rankingService: WallhavenSearchService by lazy {
        val client = OkHttpSingleton.instance.newBuilder()
            .addInterceptor(Interceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Accept", "application/json").build())
            })
            .addInterceptor(WallhavenApiKeyInterceptor())
            .build()

        Retrofit.Builder()
            .client(client)
            .baseUrl(WALLHAVEN_API_BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(WallhavenSearchService::class.java)
    }
}
