package com.example.webview

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    private var webViewRef: WebView? = null
    private var fullscreenView: View? = null
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var webView: WebView? by remember { mutableStateOf(null) }
            var canGoBack by remember { mutableStateOf(false) }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                webViewRef = this
                                webView = this
                                settings.apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    builtInZoomControls = false
                                    displayZoomControls = false
                                    setSupportZoom(false)
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        canGoBack = view?.canGoBack() == true
                                    }
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        return false
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                        val decorView = window.decorView as ViewGroup
                                        fullscreenView?.let { (it.parent as? ViewGroup)?.removeView(it) }
                                        fullscreenView = view
                                        decorView.addView(
                                            fullscreenView,
                                            ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                        )
                                    }
                                    override fun onHideCustomView() {
                                        val decorView = window.decorView as ViewGroup
                                        fullscreenView?.let {
                                            decorView.removeView(it)
                                            fullscreenView = null
                                        }
                                    }
                                }
                                loadUrl("https://www.speedtest.net/")
                            }
                        },
                        update = { }
                    )

                    BackHandler {
                        val wv = webView
                        if (fullscreenView != null) {
                            (wv?.webChromeClient)?.onHideCustomView()
                        } else if (wv != null && wv.canGoBack()) {
                            wv.goBack()
                        } else {
                            finish()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewRef?.apply {
            loadUrl("about:blank")
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        webViewRef = null
    }
}
