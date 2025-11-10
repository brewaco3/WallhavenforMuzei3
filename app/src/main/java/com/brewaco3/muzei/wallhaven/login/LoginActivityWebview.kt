package com.brewaco3.muzei.wallhaven.login

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import com.brewaco3.muzei.wallhaven.PixivMuzeiSupervisor
import com.brewaco3.muzei.wallhaven.PixivProviderConst
import com.brewaco3.muzei.wallhaven.R
import com.brewaco3.muzei.wallhaven.common.PixivMuzeiActivity
import com.brewaco3.muzei.wallhaven.databinding.ActivityLoginWebviewBinding

class LoginActivityWebview : PixivMuzeiActivity() {

    private lateinit var binding: ActivityLoginWebviewBinding
    private val cookieManager = CookieManager.getInstance()
    private var sessionPersisted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityLoginWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cookieManager.setAcceptCookie(true)

        val webView: WebView = binding.webview
        configureWebView(webView)
        webView.loadUrl("https://wallhaven.cc/login")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = PixivProviderConst.BROWSER_USER_AGENT

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url: Uri = request.url
                val host = url.host ?: return false
                if (!host.endsWith("wallhaven.cc")) {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                handlePossibleLogin(view, url)
            }
        }
    }

    private fun handlePossibleLogin(view: WebView, url: String) {
        if (sessionPersisted) {
            return
        }
        if (!url.startsWith("https://wallhaven.cc")) {
            return
        }
        val uri = Uri.parse(url)
        if (uri.path?.startsWith("/login") == true) {
            return
        }
        val cookieHeader = cookieManager.getCookie("https://wallhaven.cc") ?: return
        val hasSessionCookie = cookieHeader.contains("wallhaven_session")
        val hasRememberToken = cookieHeader.contains("remember_token")
        if (!hasSessionCookie || !hasRememberToken) {
            return
        }

        view.evaluateJavascript(
            "(function(){var name=document.querySelector('a[href^=\\'/user/\\'] span.username');return name?name.textContent:'';})()"
        ) { usernameJson ->
            val parsedUsername = usernameJson
                .takeUnless { it == "null" }
                ?.trim('"')
                ?.ifBlank { null }
            persistSession(cookieHeader, parsedUsername)
        }
    }

    private fun persistSession(cookieHeader: String, username: String?) {
        sessionPersisted = true
        PixivMuzeiSupervisor.storeSession(applicationContext, cookieHeader, username)
        cookieManager.flush()

        val resultIntent = Intent().putExtra("username", username.orEmpty())
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
