package dev.android.webview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.android.webview.ui.theme.WebViewTheme
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WebViewTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WebViewWrapper(
                        targetUrl = "https://www.google.com"
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewWrapper(targetUrl: String) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var customView: View? by remember { mutableStateOf(null) }
    var customViewCallback: WebChromeClient.CustomViewCallback? by remember { mutableStateOf(null) }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    val customViewContainer = remember {
        FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
    }

    fun createCameraIntent(): Intent? {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoUri = activity.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            android.content.ContentValues()
        )
        cameraImageUri = photoUri
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        return cameraIntent
    }

    fun createGalleryIntent(): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uris = mutableListOf<Uri>()
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    uris.add(data.clipData!!.getItemAt(i).uri)
                }
            } else if (data?.data != null) {
                uris.add(data.data!!)
            } else if (cameraImageUri != null) {
                uris.add(cameraImageUri!!)
            }

            if (uris.isNotEmpty()) {
                callback.onReceiveValue(uris.toTypedArray())
            } else {
                callback.onReceiveValue(null)
            }
        } else {
            callback.onReceiveValue(null)
        }
        filePathCallback = null
        cameraImageUri = null
    }

    fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1234
            )
        }
    }

    fun hideSystemBars() {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun showSystemBars() {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    val webView = remember {
        WebView(context).apply {

            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT

                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadsImagesAutomatically = true

                useWideViewPort = true
                loadWithOverviewMode = true

                setSupportZoom(true)
                builtInZoomControls = true
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

                userAgentString = WebSettings.getDefaultUserAgent(context)
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    if (URLUtil.isNetworkUrl(url)) {
                        return false
                    }
                    if (url.startsWith("intent://")) {
                        try {
                            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                            activity.startActivity(intent)
                            return true
                        } catch (e: Exception) {
                            try {
                                val packageName = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).`package`
                                if (!packageName.isNullOrEmpty()) {
                                    activity.startActivity(
                                        Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
                                    )
                                    return true
                                }
                            } catch (_: Exception) {}
                            activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            return true
                        }
                    }
                    if (!url.startsWith("http")) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                            activity.startActivity(intent)
                            return true
                        } catch (_: ActivityNotFoundException) {
                            activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            return true
                        }
                    }
                    activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    val newWebView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                    }
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    transport?.webView = newWebView
                    resultMsg?.sendToTarget()
                    return true
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallBack: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    filePathCallback = filePathCallBack
                    val cameraIntent = createCameraIntent()
                    val galleryIntent = createGalleryIntent()
                    val intents = mutableListOf<Intent>()
                    cameraIntent?.let { intents.add(it) }
                    val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                        putExtra(Intent.EXTRA_INTENT, galleryIntent)
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toTypedArray())
                    }
                    fileChooserLauncher.launch(chooser)
                    return true
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    requestLocationPermission()
                    callback?.invoke(origin, true, false)
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        onHideCustomView()
                        return
                    }
                    customView = view
                    customViewCallback = callback
                    customViewContainer.addView(
                        customView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    customViewContainer.visibility = View.VISIBLE
                    hideSystemBars()
                }

                override fun onHideCustomView() {
                    customViewContainer.visibility = View.GONE
                    customView?.let { customViewContainer.removeView(it) }
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                    showSystemBars()
                }
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            FrameLayout(context).apply {
                addView(
                    webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                addView(
                    customViewContainer,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        },
        update = { webView.loadUrl(targetUrl) }
    )

    BackHandler(enabled = true) {
        if (webView.canGoBack()) webView.goBack()
        else activity.finish()
    }
}
