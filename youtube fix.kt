package dev.android.youtube

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.android.youtube.ui.theme.YouTubeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YouTubeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainWrapper(modifier = Modifier.fillMaxSize().padding(innerPadding))
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "NewApi")
@Composable
fun MainWrapper(modifier: Modifier = Modifier) {
    val targetUrl = "https://m.youtube.com"
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val windowDecorView = remember { activity.window.decorView as FrameLayout }
    val windowInsetsController = remember { WindowCompat.getInsetsController(activity.window, windowDecorView) }

    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }

    var pendingPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingGeoCallback by remember { mutableStateOf<GeolocationPermissions.Callback?>(null) }
    var pendingGeoOrigin by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results.values.all { it }
        pendingPermissionRequest?.let {
            if (granted) {
                it.grant(it.resources)
            } else {
                it.deny()
            }
            pendingPermissionRequest = null
        }
        pendingGeoCallback?.let {
            it.invoke(pendingGeoOrigin, granted, false)
            pendingGeoCallback = null
            pendingGeoOrigin = null
        }
    }

    var filePathCallback by remember {
        mutableStateOf<ValueCallback<Array<Uri>>?>(null)
    }

    val fileChooserLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val callback = filePathCallback ?: return@rememberLauncherForActivityResult
            val uris =
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    WebChromeClient.FileChooserParams.parseResult(
                        result.resultCode,
                        result.data
                    )
                } else {
                    null
                }
            callback.onReceiveValue(uris)
            filePathCallback = null
        }

    val allowedHosts = setOf(
        "youtube.com",
        "youtube-nocookie.com",
        "google.com",
        "gstatic.com",
        "googleusercontent.com",
        "googleapis.com"
    )

    fun isAllowedHost(host: String?, allowed: Set<String>): Boolean {
        if (host.isNullOrEmpty()) return false
        return allowed.any { allowedHost ->
            host == allowedHost || host.endsWith(".$allowedHost")
        }
    }

    val webView = remember {
        WebView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

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

                userAgentString = userAgentString
                    .replace("; wv", "")
                    .replace("; )", ")")
                    .replace(" )", ")")
                    .replace("(;", "(")
                    .replace("( ", "(")
                    .replace(Regex("\\s{2,}"), " ")
                    .trim()
            }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                try {
                    val request = DownloadManager.Request(url.toUri())
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    request.setTitle(fileName)
                    request.setDescription("Downloading file…")
                    request.setMimeType(mimeType)
                    request.addRequestHeader("User-Agent", userAgent)
                    request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        fileName
                    )
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                } catch (_: Exception) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    val host = uri.host
                    if (uri.scheme == "intent") {
                        return try {
                            val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                            activity.startActivity(intent)
                            true
                        } catch (_: ActivityNotFoundException) {
                            uri.getQueryParameter("browser_fallback_url")?.let {
                                activity.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                            }
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                    if (isAllowedHost(host, allowedHosts)) {
                        return false
                    }
                    if (uri.scheme == "http" || uri.scheme == "https") {
                        return try {
                            view?.context?.startActivity(
                                Intent(Intent.ACTION_VIEW, uri)
                            )
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                    return try {
                        view?.context?.startActivity(
                            Intent(Intent.ACTION_VIEW, uri)
                        )
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress / 100f
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    val permissions = mutableListOf<String>()
                    request.resources.forEach {
                        when (it) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                                permissions.add(Manifest.permission.CAMERA)

                            PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                                permissions.add(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    val missing = permissions.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) {
                        request.grant(request.resources)
                    } else {
                        pendingPermissionRequest = request
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }

                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        callback.invoke(origin, true, false)
                    } else {
                        pendingGeoOrigin = origin
                        pendingGeoCallback = callback
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    return false
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    callback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = callback
                    val intent = fileChooserParams?.createIntent() ?: return false
                    fileChooserLauncher.launch(intent)
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
                    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    windowDecorView.addView(view, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                    ))
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }

                override fun onHideCustomView() {
                    customView?.let {
                        windowDecorView.removeView(it)
                        customView = null
                    }
                    customViewCallback?.onCustomViewHidden()
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    LaunchedEffect(webView) {
        webView.loadUrl(targetUrl)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.apply {
                stopLoading()
                clearHistory()
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }
        }
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { webView }
        )
        if (isLoading && customView == null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    BackHandler(enabled = true) {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            activity.finish()
        }
    }
}
