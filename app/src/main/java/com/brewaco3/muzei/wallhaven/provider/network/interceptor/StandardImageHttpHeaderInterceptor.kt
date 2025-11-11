package com.brewaco3.muzei.wallhaven.provider.network.interceptor

import com.brewaco3.muzei.wallhaven.PixivProviderConst.APP_USER_AGENT
import com.brewaco3.muzei.wallhaven.PixivProviderConst.WALLHAVEN_BASE_URL
import okhttp3.Interceptor
import okhttp3.Response

// This interceptor makes all outgoing requests to download images look just like the ones made with the official Pixiv app
class StandardImageHttpHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originRequest = chain.request()

        val newRequest = originRequest.newBuilder()
            .header("user-agent", APP_USER_AGENT)
            .header("Referer", WALLHAVEN_BASE_URL)
            .build()

        return chain.proceed(newRequest)
    }
}
