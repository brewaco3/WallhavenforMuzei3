package com.brewaco3.muzei.wallhaven.provider.network

import com.brewaco3.muzei.wallhaven.provider.network.interceptor.StandardImageHttpHeaderInterceptor
import okhttp3.OkHttpClient

object OkHttpSingleton {

    /** Shared client for Wallhaven API and GitHub traffic. */
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Shared client for wallpaper downloads. Built once so downloads reuse connections instead of
     * tearing down a client per file.
     */
    val imageClient: OkHttpClient by lazy {
        instance.newBuilder()
            .addInterceptor(StandardImageHttpHeaderInterceptor())
            .build()
    }
}
