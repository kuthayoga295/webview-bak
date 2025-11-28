package com.mobile.xxx

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    val url = "https://x.com"
    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContent {
            MaterialTheme {
                ContentWrapper(
                    target = url,
                    windowInsetsController = windowInsetsController
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ContentWrapper(target: String, windowInsetsController: WindowInsetsControllerCompat) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    val baseAllowedUrl = "https://x.com"

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var isInCustomView by remember { mutableStateOf(false) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        allowContentAccess = true
                        allowFileAccess = true
                        loadsImagesAutomatically = true
                        mediaPlaybackRequiresUserGesture = false
                        builtInZoomControls = false
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = System.getProperty("http.agent")
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val newUrl = request?.url?.toString() ?: ""
                            return if (!newUrl.startsWith(baseAllowedUrl)) {
                                pendingExternalUrl = newUrl
                                true
                            } else {
                                false
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?):
                            Boolean { url ?: return true
                            return if (!url.startsWith(baseAllowedUrl)) {
                                pendingExternalUrl = url
                                true
                            } else {
                                false
                            }
                        }
                    }

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
                        ): Boolean {
                            return false
                        }
                    }

                    loadUrl(target)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        customView?.let { view ->
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        }

        if (pendingExternalUrl != null) {
            AlertDialog(
                onDismissRequest = { pendingExternalUrl = null },
                title = { Text("Open in browser?") },
                text = { Text("Link will open in external browser. Continue?") },
                confirmButton = {
                    TextButton(onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(pendingExternalUrl))
                        context.startActivity(intent)
                        pendingExternalUrl = null
                    }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingExternalUrl = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let {
                it.stopLoading()
                it.clearHistory()
                it.removeAllViews()
                it.destroy()
            }
            webViewRef = null
        }
    }

    BackHandler(enabled = true) {
        if (isInCustomView && customViewCallback != null) {
            customViewCallback?.onCustomViewHidden()
        } else {
            webViewRef?.let { webView ->
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    activity?.finish()
                }
            }
        }
    }
}
