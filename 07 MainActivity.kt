package com.sdk.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.*

class MainActivity : ComponentActivity() {

    private val startUrl = "https://m.youtube.com"  // GANTI URL PWA KAMU
    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive PWA fullscreen
        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            MaterialTheme {
                PWAWrap(startUrl, windowInsetsController)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PWAWrap(
    targetUrl: String,
    windowInsetsController: WindowInsetsControllerCompat
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    // Domain whitelist for PWA (boleh wildcard)
    val allowedDomains = listOf(
        "*.google.*",
        "*.gstatic.com",
        "*.firebaseio.com",
        "*.your-pwa-domain.com",
        "*.yourcdn.com"
    )

    fun matchWildcard(pattern: String, host: String?): Boolean {
        if (host == null) return false
        return if (pattern.contains("*")) {
            val regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .toRegex()
            host.matches(regex)
        } else host == pattern || host.endsWith(".$pattern")
    }

    fun isAllowed(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host ?: return false
            allowedDomains.any { matchWildcard(it, host) }
        } catch (_: Exception) { false }
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var fullscreen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this

                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {

                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        setGeolocationEnabled(true)

                        // IMPORTANT for PWA
                        allowFileAccess = true
                        allowContentAccess = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(false)

                        // Service workers PWA
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        // Needed for manifest + SW
                        loadsImagesAutomatically = true
                        userAgentString = "$userAgentString WebViewPWA/1.0"

                        // Enable caching
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    // Enable Service Worker (PWA)
                    try {
                        ServiceWorkerController.getInstance().serviceWorkerWebSettings.setAllowContentAccess(true)
                        ServiceWorkerController.getInstance().serviceWorkerWebSettings.setAllowFileAccess(true)
                        ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient() {
                            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                                return null // biarkan PWA service worker yang handle
                            }
                        })
                    } catch (_: Exception) {}

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    // PWA Navigation Rule
                    webViewClient = object : WebViewClient() {

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {

                            val url = request?.url?.toString() ?: return true

                            return if (!isAllowed(url)) {
                                // PWA stays inside app → block external links
                                false
                            } else false
                        }

                        @Deprecated("Deprecated")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            url ?: return true

                            return if (!isAllowed(url)) false else false
                        }
                    }

                    webChromeClient = object : WebChromeClient() {

                        // fullscreen PWA video / PWAs that use fullscreen API
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            customView = view
                            customCallback = callback
                            fullscreen = true
                            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                        }

                        override fun onHideCustomView() {
                            customView = null
                            customCallback?.onCustomViewHidden()
                            fullscreen = false
                            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                        }

                        // file picker (input type=file)
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>,
                            fileChooserParams: FileChooserParams
                        ): Boolean {
                            val intent = fileChooserParams.createIntent()
                            activity.startActivityForResult(intent, 1000)
                            return true
                        }
                    }

                    loadUrl(targetUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Fullscreen container
        customView?.let { v ->
            AndroidView(
                factory = { FrameLayout(context).apply { addView(v) } },
                update = {
                    it.removeAllViews()
                    it.addView(v)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    // Back button PWA
    BackHandler(true) {
        when {
            fullscreen -> customCallback?.onCustomViewHidden()
            webViewRef?.canGoBack() == true -> webViewRef?.goBack()
            else -> activity.finish()
        }
    }
}
