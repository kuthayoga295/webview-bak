package com.sdk.webview

import android.annotation.SuppressLint
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.*

class MainActivity : ComponentActivity() {

    private val url = "https://m.youtube.com"
    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full immersive mode
        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            MaterialTheme {
                ContentWrapper(url, windowInsetsController)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ContentWrapper(
    target: String,
    windowInsetsController: WindowInsetsControllerCompat
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    // ————————————————————————————————————————————————
    // Allowed domains (support wildcard)
    // ————————————————————————————————————————————————
    val allowedDomains = listOf(
        "youtube.com",
        "*.google.*",
        "gstatic.com",
        "googleusercontent.com",
        "accounts.google.com",
        "play.googleapis.com",
        "googleapis.com"
    )

    // Wildcard matcher
    fun matchesWildcard(pattern: String, host: String): Boolean {
        return if (pattern.contains("*")) {
            val regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .toRegex()
            host.matches(regex)
        } else {
            host == pattern || host.endsWith(".$pattern")
        }
    }

    // Check allowed domain
    fun isAllowed(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host ?: return false
            allowedDomains.any { matchesWildcard(it, host) }
        } catch (_: Exception) { false }
    }

    // YouTube intent → redirect
    fun handleYoutubeIntent(url: String, webView: WebView?): Boolean {
        if (url.startsWith("intent://") ||
            url.startsWith("vnd.youtube://") ||
            url.startsWith("youtube://")
        ) {
            val fixed = url
                .replace("intent://", "https://")
                .replace("vnd.youtube://", "https://m.youtube.com/watch?v=")
                .replace("youtube://", "https://m.youtube.com/watch?v=")

            webView?.loadUrl(fixed)
            return true
        }
        return false
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var isInCustomView by remember { mutableStateOf(false) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {

        // ————————————————————————————————————————————————
        // Main WebView
        // ————————————————————————————————————————————————
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this

                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Remove scrollbars
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mediaPlaybackRequiresUserGesture = false
                        allowContentAccess = true
                        allowFileAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        loadsImagesAutomatically = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(false)
                        userAgentString = System.getProperty("http.agent")
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    // URL Filter + Intent Block
                    webViewClient = object : WebViewClient() {

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val newUrl = request?.url?.toString() ?: return true

                            if (handleYoutubeIntent(newUrl, view)) return true

                            return if (!isAllowed(newUrl)) {
                                pendingExternalUrl = newUrl
                                true
                            } else false
                        }

                        @Deprecated("Deprecated")
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            newUrl: String?
                        ): Boolean {
                            newUrl ?: return true

                            if (handleYoutubeIntent(newUrl, view)) return true

                            return if (!isAllowed(newUrl)) {
                                pendingExternalUrl = newUrl
                                true
                            } else false
                        }
                    }

                    // FULLSCREEN (YouTube, Vimeo, dll.)
                    webChromeClient = object : WebChromeClient() {

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            customView = view
                            customViewCallback = callback
                            isInCustomView = true
                            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                        }

                        override fun onHideCustomView() {
                            customView = null
                            customViewCallback?.onCustomViewHidden()
                            customViewCallback = null
                            isInCustomView = false
                            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                        }

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ) = false
                    }

                    loadUrl(target)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ————————————————————————————————————————————————
        // Fullscreen holder
        // ————————————————————————————————————————————————
        customView?.let { v ->
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        addView(v)
                    }
                },
                update = {
                    it.removeAllViews()
                    it.addView(v)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ————————————————————————————————————————————————
        // External link dialog
        // ————————————————————————————————————————————————
        if (pendingExternalUrl != null) {
            AlertDialog(
                onDismissRequest = { pendingExternalUrl = null },
                title = { Text("Open in browser?") },
                text = { Text("This link will be opened externally.") },
                confirmButton = {
                    TextButton(onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            Uri.parse(pendingExternalUrl)
                        )
                        context.startActivity(intent)
                        pendingExternalUrl = null
                    }) { Text("Yes") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingExternalUrl = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // ————————————————————————————————————————————————
    // Cleanup
    // ————————————————————————————————————————————————
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewRef = null
        }
    }

    // ————————————————————————————————————————————————
    // Back handler
    // ————————————————————————————————————————————————
    BackHandler(true) {
        when {
            isInCustomView -> customViewCallback?.onCustomViewHidden()
            webViewRef?.canGoBack() == true -> webViewRef?.goBack()
            else -> activity.finish()
        }
    }
}
