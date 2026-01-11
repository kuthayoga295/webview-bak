package dev.android.webview

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.util.Patterns
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.android.webview.ui.theme.WebViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebViewTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainNavigation(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    var urlToLoad by remember { mutableStateOf("") }
    var showWebView by remember { mutableStateOf(false) }

    fun resolveInputToUrl(input: String): String {
        val text = input.trim()
        return when {
            text.startsWith("http://") || text.startsWith("https://") -> text
            Patterns.WEB_URL.matcher("http://$text").matches() -> "https://$text"
            else -> "https://www.google.com/search?q=${Uri.encode(text)}"
        }
    }

    if (showWebView) {
        WebViewScreen(
            modifier = modifier,
            url = urlToLoad,
            onBack = { showWebView = false }
        )
    } else {
        InputScreen(
            modifier = modifier,
            onGoClick = { inputUrl ->
                urlToLoad = resolveInputToUrl(inputUrl)
                showWebView = true
            }
        )
    }
}

@Composable
fun InputScreen(modifier: Modifier = Modifier, onGoClick: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = "App Icon",
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(16.dp))
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("URL or Search:") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (text.isNotBlank()) {
                    Toast.makeText(context, "Loading...", Toast.LENGTH_LONG).show()
                    onGoClick(text.trim())
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Go")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "NewApi")
@Composable
fun WebViewScreen(modifier: Modifier = Modifier, url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    fun Context.findActivity(): ComponentActivity {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is ComponentActivity) return ctx
            ctx = ctx.baseContext
        }
        error("Activity not found")
    }
    val activity = LocalContext.current.findActivity()

    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun openInCustomTab(context: Context, url: String) {
        try {
            val builder = CustomTabsIntent.Builder()
            builder.setShowTitle(true)
            builder.setInstantAppsEnabled(true)
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(context, url.toUri())
        } catch (_: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        }
    }

    var pendingPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingGeoCallback by remember { mutableStateOf<GeolocationPermissions.Callback?>(null) }
    var pendingGeoOrigin by remember { mutableStateOf<String?>(null) }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results.values.all { it }
        pendingPermissionRequest?.let {
            if (granted) {
                it.grant(it.resources)
            } else {
                it.deny()
            }
            pendingPermissionRequest = null
        }
    }

    val geoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results.values.all { it }
        pendingGeoCallback?.let {
            it.invoke(pendingGeoOrigin, granted, false)
            pendingGeoCallback = null
            pendingGeoOrigin = null
        }
    }

    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val resultCode = result.resultCode
        val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        fileChooserCallback?.onReceiveValue(results)
        fileChooserCallback = null
    }

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val windowDecorView = remember { activity.window.decorView as FrameLayout }
    val windowInsetsController = remember { WindowCompat.getInsetsController(activity.window, windowDecorView) }

    fun handleExternalUri(uri: Uri): Boolean {
        val urlStr = uri.toString()
        val scheme = uri.scheme ?: return false

        fun safeStart(intent: Intent): Boolean {
            return try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "Cannot open intent: $urlStr", Toast.LENGTH_SHORT).show()
                false
            }
        }

        return try {
            if (scheme == "intent") {
                try {
                    val intent = Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME)
                    if (safeStart(intent)) return true
                } catch (_: Exception) {
                    Toast.makeText(context, "Cannot open intent: $urlStr", Toast.LENGTH_SHORT).show()
                }
                uri.getQueryParameter("browser_fallback_url")?.let {
                    safeStart(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
                true
            }
            else {
                val intent = when (scheme) {
                    "mailto" -> Intent(Intent.ACTION_SENDTO, uri)
                    "tel" -> Intent(Intent.ACTION_DIAL, uri)
                    "sms" -> Intent(Intent.ACTION_VIEW, uri)
                    "geo" -> Intent(Intent.ACTION_VIEW, uri)
                    else -> null
                }
                if (intent != null && safeStart(intent)) {
                    true
                } else {
                    openInCustomTab(context, urlStr)
                    true
                }
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot open: $urlStr", Toast.LENGTH_SHORT).show()
            true
        }
    }


    BackHandler(enabled = true) {
        if (customView != null) {
            webView?.webChromeClient?.onHideCustomView()
        } else if (webView != null && webView!!.canGoBack()) {
            webView?.goBack()
        }
        else {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                it.stopLoading()
                it.destroy()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        AndroidView(
            modifier = Modifier.weight(1f),
            factory = {
                WebView(context).apply {
                    webView = this

                    layoutParams = ViewGroup.LayoutParams (
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    val originalUA = settings.userAgentString
                    val cleanedUA = originalUA
                        .replace(Regex("(Android \\d+)[^)]+"), "$1")
                        .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        loadsImagesAutomatically = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mediaPlaybackRequiresUserGesture = false
                        javaScriptCanOpenWindowsAutomatically = true
                        blockNetworkLoads = false
                        allowFileAccess = true
                        allowContentAccess = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setGeolocationEnabled(true)
                        setSupportZoom(true)
                        setSupportMultipleWindows(true)
                        userAgentString = cleanedUA
                    }

                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(webView, true)
                    }

                    setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                        when {
                            url.startsWith("blob") -> {
                                Toast.makeText(context, "Download not supported for blob URLs", Toast.LENGTH_LONG).show()
                            }
                            url.startsWith("http://") || url.startsWith("https://") -> {
                                try {
                                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                    val request = DownloadManager.Request(url.toUri()).apply {
                                        setMimeType(mimetype)
                                        addRequestHeader("User-Agent", userAgent)
                                        CookieManager.getInstance().getCookie(url)?.let {
                                            addRequestHeader("cookie", it)
                                        }
                                        setTitle(fileName)
                                        setDescription("Downloading file...")
                                        setNotificationVisibility(
                                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                        )
                                        setDestinationInExternalFilesDir(
                                            context,
                                            Environment.DIRECTORY_DOWNLOADS,
                                            fileName
                                        )
                                    }
                                    val dm = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                                    dm.enqueue(request)
                                    Toast.makeText(context, "Downloading file...", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Error downloading file: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            else -> {
                                Toast.makeText(context, "Unsupported download scheme", Toast.LENGTH_LONG).show()
                            }
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
                            if (uri.scheme == "http" || uri.scheme == "https") {
                                return false
                            }
                            return handleExternalUri(uri)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
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
                                mediaPermissionLauncher.launch(missing.toTypedArray())
                            }
                        }

                        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                callback.invoke(origin, true, false)
                            } else {
                                pendingGeoOrigin = origin
                                pendingGeoCallback = callback
                                geoPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }

                        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                            val tempWebView = WebView(view?.context ?: return false).apply {
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val uri = request.url
                                        if (uri.scheme == "http" || uri.scheme == "https") {
                                            webView?.loadUrl(uri.toString())
                                        } else {
                                            handleExternalUri(uri)
                                        }
                                        view.destroy()
                                        return true
                                    }
                                }
                            }
                            transport.webView = tempWebView
                            resultMsg.sendToTarget()
                            return true
                        }

                        override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                            fileChooserCallback = filePathCallback
                            val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                                putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                                putExtra(Intent.EXTRA_TITLE, "Choose file...")
                            }
                            try {
                                fileChooserLauncher.launch(chooserIntent)
                            } catch (_: Exception) {
                                fileChooserCallback = null
                                return false
                            }
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
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            ))
                        }

                        override fun onHideCustomView() {
                            customView?.let {
                                windowDecorView.removeView(it)
                            }
                            customView = null
                            customViewCallback?.onCustomViewHidden()
                            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                    loadUrl(url)
                }
            },
            update = { view ->
                if (view.url != url) {
                    view.loadUrl(url)
                }
            }
        )
    }
}
