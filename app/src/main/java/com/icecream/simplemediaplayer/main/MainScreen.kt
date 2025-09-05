package com.icecream.simplemediaplayer.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.icecream.simplemediaplayer.common.ui.SimpleMediaViewModel

private const val MY_REQUEST_CODE = 100

@Composable
internal fun SimpleMediaScreen(
    vm: SimpleMediaViewModel,
    navController: NavController,
    startService: () -> Unit,
) {
    val context = LocalContext.current

    var webViewReference by remember { mutableStateOf<WebView?>(null) }
    var isWebViewReady by remember { mutableStateOf(false) }
    var pendingPlayerState by remember { mutableStateOf<String?>(null) }

    val playbackState by vm.playbackState.collectAsStateWithLifecycle(
        lifecycleOwner = LocalContext.current as LifecycleOwner
    )

    var playbackStateString by remember { mutableStateOf<String>("null") }

    // WebView가 준비되고 pending state가 있으면 전달
    LaunchedEffect(isWebViewReady, pendingPlayerState) {
        if (isWebViewReady && pendingPlayerState != null) {
            webViewReference?.evaluateJavascript("playerRef.current.nativePlayerState('$pendingPlayerState')", null)
            pendingPlayerState = null
        }
    }

    // playbackState 변경 처리 개선
    LaunchedEffect(playbackState) {
        val newState = when {
            playbackState.toString().indexOf("Buffering") > -1 -> "buffer"
            playbackState.toString() == "Playing(isPlaying=true)" -> "playing"
            playbackState.toString() == "Playing(isPlaying=false)" -> "paused"
            else -> return@LaunchedEffect
        }

        playbackStateString = newState

        // WebView가 준비되어 있으면 즉시 전달, 아니면 pending으로 저장
        if (isWebViewReady && webViewReference != null) {
            // JavaScript 함수가 존재하는지 확인 후 호출
            webViewReference?.evaluateJavascript("""
                (function() {
                    try {
                        if (typeof playerRef !== 'undefined' && playerRef.current && typeof playerRef.current.nativePlayerState === 'function') {
                            playerRef.current.nativePlayerState('$newState');
                            return 'success';
                        } else {
                            return 'not_ready';
                        }
                    } catch(e) {
                        return 'error: ' + e.message;
                    }
                })();
            """.trimIndent()) { result ->
                if (result == "\"not_ready\"" || result?.contains("error") == true) {
                    // JavaScript 함수가 준비되지 않았거나 오류가 발생하면 pending으로 저장
                    pendingPlayerState = newState
                }
            }
        } else {
            pendingPlayerState = newState
        }
    }

    DisposableEffect(Unit) {
        onDispose { }
    }

    SideEffect {
        val window = (context as Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
    }

    var isNetworkAvailable by remember { mutableStateOf(true) }
    var showNetworkDialog by remember { mutableStateOf(false) }

    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun checkNetworkState() {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isNetworkAvailable = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        showNetworkDialog = !isNetworkAvailable
    }

    LaunchedEffect(Unit) {
        checkNetworkState()
    }

    val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isNetworkAvailable = true
            showNetworkDialog = false
        }

        override fun onLost(network: Network) {
            isNetworkAvailable = false
            showNetworkDialog = true
        }
    }

    DisposableEffect(Unit) {
        val networkRequest = NetworkRequest.Builder().build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    if (showNetworkDialog) {
        AlertDialog(
            onDismissRequest = { showNetworkDialog = false },
            title = { Text("네트워크 연결 변경됨") },
            text = { Text("현재 네트워크 연결 상태가 변경되었어요.\n스트리밍이 끊길 수 있으니 연결 상태를 확인해주세요.") },
            confirmButton = {
                Button(onClick = { showNetworkDialog = false }) {
                    Text("확인")
                }
            }
        )
    }

    val sharedPreferences = context.getSharedPreferences("com.icecream.simplemediaplayer.PREFERENCE", Context.MODE_PRIVATE)
    val preventScreenOff = sharedPreferences.getBoolean("settings_prevent_screen_off", false)

    if (preventScreenOff) {
        (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    CheckForAppUpdate((context as? Activity))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets(0))
    ) {
        WebViewContent(
            context = context,
            viewModel = vm,
            webViewReference = { webView ->
                webViewReference = webView
            },
            onWebViewReady = { ready ->
                isWebViewReady = ready
            },
            startService = startService
        )
    }
}

@Composable
private fun WebViewContent(
    context: Context,
    viewModel: SimpleMediaViewModel,
    webViewReference: (WebView) -> Unit,
    onWebViewReady: (Boolean) -> Unit,
    startService: () -> Unit
) {
    var isPageLoading by remember { mutableStateOf(true) }
    var webView: WebView? by remember { mutableStateOf(null) }

    AndroidView(
        factory = {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        isPageLoading = true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isPageLoading = false
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url?.startsWith("mailto:") == true) {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(url))
                            context.startActivity(intent)
                            return true
                        } else if (url != null && !url.contains("yuntae.in")) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                            return true
                        }
                        return super.shouldOverrideUrlLoading(view, url)
                    }

                }

                isVerticalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.userAgentString = settings.userAgentString + "AndroidNative "+ context.applicationContext.packageManager.getPackageInfo(context.applicationContext.packageName, 0).versionName
                overScrollMode = WebView.OVER_SCROLL_NEVER
                addJavascriptInterface(
                    JavascriptBridge(viewModel, context, startService),
                    "Native"
                )
                loadUrl("https://radio.yuntae.in") //https://radio.yuntae.in
                webViewReference(this)
                webView = this
            }
        },
        modifier = Modifier
            .fillMaxSize()
    )

    BackHandler(enabled = true) {
        webView?.evaluateJavascript("playerRef.current.nativeBackHandler()", null)
    }

    if (isPageLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
            )
        }
    }

    // WebView 준비 상태 콜백
    LaunchedEffect(webView) {
        if (webView != null) {
            onWebViewReady(true)
        }
    }
}

@Composable
fun CheckForAppUpdate(activity: Activity?) {
    val context = LocalContext.current
    val appUpdateManager = AppUpdateManagerFactory.create(context)

    val appUpdateInfoTask: Task<AppUpdateInfo> = appUpdateManager.appUpdateInfo

    LaunchedEffect(Unit) {
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                if (activity != null) {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        activity,
                        MY_REQUEST_CODE
                    )
                }
            }
        }
    }
}
