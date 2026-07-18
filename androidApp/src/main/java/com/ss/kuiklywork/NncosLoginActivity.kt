package com.ss.kuiklywork

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ss.kuiklywork.module.KRBridgeModule
import com.ss.kuiklywork.module.NncosLoginCallbackStore

class NncosLoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var callbackSent = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 0, 16.dp, 0)
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                48.dp
            )
        }
        val closeButton = toolbarButton("\u5173\u95ed") {
            finishWithCurrentState("\u5df2\u5173\u95ed\u767b\u5f55\u9875")
        }
        val titleView = TextView(this).apply {
            text = "\u534a\u6b21\u5143\u56fe\u767b\u5f55"
            textSize = 16f
            setTextColor(Color.rgb(51, 51, 51))
            gravity = Gravity.CENTER
        }
        val doneButton = toolbarButton("\u5b8c\u6210") {
            finishWithCurrentState("\u5df2\u5b8c\u6210\u767b\u5f55\u68c0\u67e5")
        }
        toolbar.addView(closeButton, LinearLayout.LayoutParams(72.dp, ViewGroup.LayoutParams.MATCH_PARENT))
        toolbar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        toolbar.addView(doneButton, LinearLayout.LayoutParams(72.dp, ViewGroup.LayoutParams.MATCH_PARENT))

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.userAgentString = DESKTOP_USER_AGENT
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {}
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return !request.url.isWebUrl()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                }
            }
        }

        root.addView(toolbar)
        root.addView(webView)
        setContentView(root)
        webView.loadUrl(LOGIN_URL)
    }

    override fun onBackPressed() {
        finishWithCurrentState("\u5df2\u8fd4\u56de")
    }

    override fun onDestroy() {
        if (!callbackSent) {
            NncosLoginCallbackStore.finish(KRBridgeModule.isNncosLoggedIn(), "\u767b\u5f55\u9875\u5df2\u5173\u95ed")
            callbackSent = true
        }
        webView.destroy()
        super.onDestroy()
    }

    private fun toolbarButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.rgb(30, 107, 255))
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
    }

    private fun finishWithCurrentState(message: String) {
        CookieManager.getInstance().flush()
        val isLoggedIn = KRBridgeModule.isNncosLoggedIn()
        if (isLoggedIn) {
            KRBridgeModule.markNncosLoginSucceeded()
        }
        if (!callbackSent) {
            NncosLoginCallbackStore.finish(isLoggedIn, message)
            callbackSent = true
        }
        finish()
    }

    private fun Uri.isWebUrl(): Boolean {
        return scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val LOGIN_URL =
            "https://www.nncos.com/login/?redirect_to=https%3A%2F%2Fwww.nncos.com%2F"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
