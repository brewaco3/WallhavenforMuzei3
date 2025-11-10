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

package com.brewaco3.muzei.wallhaven;

public final class PixivProviderConst {

    public static final String PIXIV_RANKING_URL = "https://wallhaven.cc/api/v1/";
    public static final String PIXIV_API_HOST_URL = PIXIV_RANKING_URL;
    public static final String WALLHAVEN_BASE_URL = "https://wallhaven.cc/";
    public static final String PIXIV_ARTWORK_URL = "https://wallhaven.cc/w/";
    public static final String PIXIV_REDIRECT_URL = "";
    public static final String PIXIV_IMAGE_URL = "https://w.wallhaven.cc/";
    public static final String MEMBER_ILLUST_URL = "";
    public static final String OAUTH_URL = PIXIV_RANKING_URL;

    // browser strings
    public static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:144.0) Gecko/20100101 Firefox/144.0";

    public static final String APP_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:144.0) Gecko/20100101 Firefox/144.0";

    public static final String PREFERENCE_SESSION_COOKIE = "wallhavenSessionCookie";
    public static final String PREFERENCE_SESSION_USERNAME = "wallhavenUsername";
    public static final String PREFERENCE_SESSION_TIMESTAMP = "wallhavenSessionTimestamp";
    public static final String PREFERENCE_OLDEST_MAX_BOOKMARK_ID = "oldestMaxBookmarkId";


    public static final String[] AUTH_MODES = {};
    public static final String[] RANKING_MODES = {"toplist", "hot"};

    public static final String SHARE_IMAGE_INTENT_CHOOSER_TITLE = "Share image using";
}
