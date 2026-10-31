package com.brewaco3.muzei.wallhaven

object WallhavenProviderConst {

    const val WALLHAVEN_API_BASE_URL = "https://wallhaven.cc/api/v1/"

    /** Host that serves the wallpaper files themselves, used when routing through a mirror. */
    const val WALLHAVEN_IMAGE_HOST = "w.wallhaven.cc"

    /** Site root, sent as the referer when downloading wallpapers. */
    const val WALLHAVEN_BASE_URL = "https://wallhaven.cc/"

    const val WALLHAVEN_ARTWORK_URL = "https://wallhaven.cc/w/"

    // Declared as a desktop Firefox so Wallhaven serves the same responses it serves a browser
    const val APP_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:144.0) Gecko/20100101 Firefox/144.0"

    const val PREFERENCE_API_KEY = "wallhavenApiKey"

    /** Key the worker puts the failure reason under so the settings screen can show it. */
    const val WORK_ERROR_MESSAGE_KEY = "workErrorMessage"
}
