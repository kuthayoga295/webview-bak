package com.mobile.x

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    private val targetUrl = "https://x.com"
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        var results: Array<Uri>? = null
        if (result.resultCode == RESULT_OK) {
            results = result.data?.let { intent ->
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, intent)
            }
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebViewScreen(
                        targetUrl = targetUrl,
                        onExternalUrl = this::handleExternalUrl,
                        onShowFileChooser = this::handleFileChooser
                    )
                }
            }
        }
    }

    private fun handleFileChooser(callback: ValueCallback<Array<Uri>>?, params: WebChromeClient.FileChooserParams?) {
        filePathCallback = callback

        val intent: Intent? = if (params?.acceptTypes?.contains("image/*") == true || params?.acceptTypes?.contains("video/*") == true) {
            val chooserIntent = Intent(Intent.ACTION_CHOOSER)
            chooserIntent.putExtra(Intent.EXTRA_INTENT, params.createIntent())
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
            chooserIntent
        } else {
            params?.createIntent()
        }

        try {
            if (intent != null) {
                fileChooserLauncher.launch(intent)
            } else {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
        } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun handleExternalUrl(url: String): Boolean {
        return try {
            val uri = url.toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    targetUrl: String,
    onExternalUrl: (String) -> Boolean,
    onShowFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    val windowDecorView = remember { activity.window.decorView as FrameLayout }

    val windowInsetsController = remember {
        WindowCompat.getInsetsController(activity.window, windowDecorView)
    }

    var customView: View? by remember { mutableStateOf(null) }
    var customViewCallback: WebChromeClient.CustomViewCallback? by remember { mutableStateOf(null) }

    val hideCustomView = {
        customView?.let { videoView ->
            val parent = videoView.parent as? ViewGroup
            parent?.removeView(videoView)
        }
        customView = null

        customViewCallback?.onCustomViewHidden()
        customViewCallback = null

        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                allowContentAccess = true
                allowFileAccess = true
                javaScriptCanOpenWindowsAutomatically = true
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    if (!(url.startsWith("http") || url.startsWith("https")) || url.startsWith(targetUrl)) {
                        return onExternalUrl(url)
                    }
                    return onExternalUrl(url)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    onShowFileChooser(filePathCallback, fileChooserParams)
                    return true
                }

                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (customView != null) {
                        callback.onCustomViewHidden()
                        return
                    }

                    customView = view
                    customViewCallback = callback

                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    windowInsetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                    windowDecorView.addView(view, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ))

                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }

                override fun onHideCustomView() {
                    hideCustomView()
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, true, false)
                }
            }

            loadUrl(targetUrl)
        }
    }

    AndroidView(modifier = Modifier.fillMaxSize(), factory = { webView })

    BackHandler(enabled = true) {
        if (customView != null) {
            hideCustomView()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            activity.finish()
        }
    }
}
