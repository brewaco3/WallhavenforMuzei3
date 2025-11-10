package com.brewaco3.muzei.wallhaven.provider.network.interceptor

import com.brewaco3.muzei.wallhaven.PixivProviderConst.APP_USER_AGENT
import okhttp3.Interceptor
import okhttp3.Response

// This interceptor ensures Wallhaven requests consistently report a desktop Firefox user agent.
class StandardAuthHttpHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originRequest = chain.request()

        val newRequest = originRequest.newBuilder()
            .header("user-agent", APP_USER_AGENT)
            .build()

        return chain.proceed(newRequest)
    }
}
