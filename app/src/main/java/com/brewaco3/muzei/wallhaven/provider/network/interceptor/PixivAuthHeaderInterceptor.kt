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

package com.brewaco3.muzei.wallhaven.provider.network.interceptor

import android.text.TextUtils
import com.brewaco3.muzei.wallhaven.PixivMuzeiSupervisor
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Pixiv auth request [Interceptor] that automatic append access token to headers
 *
 * Created by alvince on 2020/6/13
 *
 * @author alvince.zy@gmail.com
 */
class PixivAuthHeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originRequest = chain.request()

        val cookie = PixivMuzeiSupervisor.getSessionCookie()
        if (TextUtils.isEmpty(cookie)) {
            return chain.proceed(originRequest)
        }
        return originRequest.newBuilder().apply {
            addHeader("Cookie", cookie)
        }.let { builder ->
            chain.proceed(builder.build())
        }
    }
}
