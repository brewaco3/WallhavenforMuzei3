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

package com.brewaco3.muzei.wallhaven.provider.network;

import com.brewaco3.muzei.wallhaven.provider.network.moshi.Contents;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WallhavenRankingFeedJsonService
{
    @GET("search")
    Call<Contents> getSearchResults(
            @Query("sorting") String sorting,
            @Query("purity") String purity,
            @Query("categories") String categories,
            @Query("page") int page,
            @Query("order") String order
    );
}
