package com.brewaco3.muzei.wallhaven.util;


import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.brewaco3.muzei.wallhaven.WallhavenMuzei;

import java.net.InetAddress;
import java.util.List;
import java.util.Random;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;
import retrofit2.Call;
import retrofit2.Callback;

/*
This class is called everytime an image is downloaded
 */
public class HostManager {

    public static final String HOST_OLD = "w.wallhaven.cc";
    public static final String HOST_NEW = "w.wallhaven.cc";
    private static final String HTTP_HEAD = "https://";

    public static HostManager get() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final HostManager INSTANCE = new HostManager();
    }

    public String replaceUrl(String before) {
        // Allows routing through a user-provided image proxy when enabled
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(WallhavenMuzei.Companion.getContext().getApplicationContext());

        boolean useWallhavenProxy = false;
        String wallhavenProxyHost = "";

        if(WallhavenMuzei.Companion.getContext() != null){
            useWallhavenProxy = prefs.getBoolean("pref_useWallhavenProxy",false);
        }

        if (useWallhavenProxy) {
            wallhavenProxyHost = prefs.getString("pref_wallhavenProxyHost", HOST_NEW);
            return before.replace(HOST_OLD, wallhavenProxyHost);
        } else {
            return resizeUrl(before);
        }
    }

    private String resizeUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            return HTTP_HEAD + HOST_OLD + uri.getPath();
        } catch (Exception e) {
            e.printStackTrace();
            return HTTP_HEAD + HOST_OLD + url.substring(19);
        }
    }

    public static int flatRandom(int left, int right) {
        Random r = new Random();
        return r.nextInt(right - left) + left;
    }

    public static int flatRandom(int right) {
        return flatRandom(0, right);
    }
}
