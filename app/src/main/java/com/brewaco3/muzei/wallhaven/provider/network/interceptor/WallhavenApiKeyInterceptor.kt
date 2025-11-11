package com.brewaco3.muzei.wallhaven.provider.network.interceptor

import com.brewaco3.muzei.wallhaven.PixivMuzeiSupervisor
import okhttp3.Interceptor
import okhttp3.Response

class WallhavenApiKeyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = PixivMuzeiSupervisor.getApiKey()
        val requestBuilder = chain.request().newBuilder()
        if (apiKey.isNotBlank()) {
            requestBuilder.header("X-API-Key", apiKey)
        }
        return chain.proceed(requestBuilder.build())
    }
}
