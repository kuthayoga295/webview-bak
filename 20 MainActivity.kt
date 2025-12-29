package dev.android.web

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.android.web.ui.theme.WebTheme

class MainActivity : ComponentActivity() {
    private val appPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private fun hasAllPermissions(): Boolean {
        return appPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result -> }

        if (!hasAllPermissions()) {
            permissionLauncher.launch(appPermissions)
        }

        enableEdgeToEdge()
        setContent {
            WebTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainWrapper(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainWrapper(modifier: Modifier = Modifier) {
    val targetUrl = "https://google.com"
    val context = LocalContext.current
    val activity = context as ComponentActivity

    val window = activity.window
    var customView by remember { mutableStateOf<View?>(null) }

    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }

    val webView = remember {
        WebView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = true
                allowFileAccess = true
                allowContentAccess = true
                setSupportMultipleWindows(true)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                setGeolocationEnabled(true)
                blockNetworkLoads = false
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()

                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        return false
                    }

                    if (URLUtil.isNetworkUrl(url)) {
                        return false
                    }

                    if (url.startsWith("intent://")) {
                        return try {
                            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                            activity.startActivity(intent)
                            true
                        } catch (e: Exception) {
                            try {
                                val packageName =
                                    Intent.parseUri(url, Intent.URI_INTENT_SCHEME).`package`
                                if (!packageName.isNullOrEmpty()) {
                                    activity.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "market://details?id=$packageName".toUri()
                                        )
                                    )
                                    true
                                } else false
                            } catch (_: Exception) {
                                false
                            }
                        }
                    }

                    return try {
                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                        activity.startActivity(intent)
                        true
                    } catch (_: ActivityNotFoundException) {
                        false
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, true, false)
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view

                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }

                override fun onHideCustomView() {
                    customView = null

                    WindowCompat.setDecorFitsSystemWindows(window, true)
                    WindowInsetsControllerCompat(window, window.decorView).show(
                        WindowInsetsCompat.Type.systemBars()
                    )
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress / 100f
                }
            }
            loadUrl(targetUrl)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { webView }
        )

        if (customView != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { customView!! }
            )
        }

        if (isLoading && customView == null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    BackHandler(enabled = true) {
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            activity.finish()
        }
    }
}
