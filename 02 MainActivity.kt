package com.mobile.youtube

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    val mUrl = "https://m.youtube.com"
    lateinit var windowInsetsController: WindowInsetsControllerCompat
    lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        setContent {
            WebViewer(mUrl)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webView.isInitialized) {
            webView.destroy()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewer(mUrl: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val activity = context as MainActivity
            WebView(context).apply {
                activity.webView = this
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                webViewClient = WebViewClient()

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                webChromeClient = object : WebChromeClient() {
                    private var customView: View? = null
                    private var customViewCallback: CustomViewCallback? = null

                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        if (customView != null) {
                            callback?.onCustomViewHidden()
                            return
                        }
                        customView = view
                        customViewCallback = callback
                        activity.windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                        activity.window.decorView.let { decorView ->
                            (decorView as ViewGroup).addView(view, ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            ))
                        }
                    }

                    override fun onHideCustomView() {
                        customView?.let { view ->
                            activity.windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                            activity.window.decorView.let { decorView ->
                                (decorView as ViewGroup).removeView(view)
                            }
                        }
                        customView = null
                        customViewCallback?.onCustomViewHidden()
                        customViewCallback = null
                    }
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    javaScriptCanOpenWindowsAutomatically = true
                    allowContentAccess = true
                    allowFileAccess = true
                    loadsImagesAutomatically = true
                    mediaPlaybackRequiresUserGesture = false
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                loadUrl(mUrl)
            }
        }
    )
}
