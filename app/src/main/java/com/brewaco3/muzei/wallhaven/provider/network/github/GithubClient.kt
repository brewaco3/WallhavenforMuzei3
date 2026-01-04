/*
 *     This file is part of WallhavenforMuzei3.
 *
 *     WallhavenforMuzei3 is free software: you can redistribute it and/or modify
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
package com.brewaco3.muzei.wallhaven.provider.network.github

import com.brewaco3.muzei.wallhaven.provider.network.OkHttpSingleton
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object GithubClient {
    private const val BASE_URL = "https://api.github.com/"

    val service: GithubService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(OkHttpSingleton.getInstance())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GithubService::class.java)
    }
}
