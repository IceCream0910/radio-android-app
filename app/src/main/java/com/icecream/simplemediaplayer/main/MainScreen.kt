package com.icecream.simplemediaplayer.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.icecream.simplemediaplayer.common.ui.SimpleMediaViewModel

@Composable
internal fun SimpleMediaScreen(
    vm: SimpleMediaViewModel,
    navController: NavController,
    startService: () -> Unit,
) {
    val systemUiController = rememberSystemUiController()
    val context = LocalContext.current

    var webViewReference by remember { mutableStateOf<WebView?>(null) }

    val playbackState by vm.playbackState.collectAsStateWithLifecycle(
        lifecycleOwner = LocalContext.current as LifecycleOwner
    )

    LaunchedEffect(playbackState) {
        if(playbackState.toString().indexOf("Buffering") > -1) {
            webViewReference?.evaluateJavascript("playerRef.current.nativePlayerState('buffer')", null)
        } else if(playbackState.toString() == "Playing(isPlaying=true)") {
            webViewReference?.evaluateJavascript("playerRef.current.nativePlayerState('playing')", null)
        } else if(playbackState.toString() == "Playing(isPlaying=false)") {
            webViewReference?.evaluateJavascript("playerRef.current.nativePlayerState('paused')", null)
        }
    }

    DisposableEffect(Unit) {
        startService()
        onDispose { }
    }

    SideEffect {
        systemUiController.setSystemBarsColor(color = Color.Black)
        systemUiController.setStatusBarColor(color = Color.Black)
    }

    var isNetworkAvailable by remember { mutableStateOf(true) }
    var showNetworkDialog by remember { mutableStateOf(false) }

    // ConnectivityManager 인스턴스를 가져옵니다.
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // 현재 네트워크 상태를 확인하는 함수
    fun checkNetworkState() {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isNetworkAvailable = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        showNetworkDialog = !isNetworkAvailable
    }

    // 앱 시작 시 네트워크 상태 확인
    LaunchedEffect(Unit) {
        checkNetworkState()
    }

    // 네트워크 콜백을 정의합니다.
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

    // 네트워크 콜백을 등록합니다.
    DisposableEffect(Unit) {
        val networkRequest = NetworkRequest.Builder().build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    // 네트워크 연결 상태에 따라 AlertDialog를 표시합니다.
    if (showNetworkDialog) {
        AlertDialog(
            onDismissRequest = { showNetworkDialog = false },
            title = { Text("네트워크 연결 끊김") },
            text = { Text("현재 네트워크 연결 상태가 원활하지 않아요.\n라디오 스트리밍을 위해서는 인터넷 연결이 필요합니다.\n연결 상태를 확인한 후 다시 시도해주세요. 네트워크에 연결되면 이 창이 사라집니다.") },
            confirmButton = {
                Button(onClick = { showNetworkDialog = false }) {
                    Text("확인")
                }
            }
        )
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WebViewContent(
            context = context,
            viewModel = vm,
            webViewReference = { webView -> webViewReference = webView }
        )
    }
}

@Composable
private fun WebViewContent(context: Context, viewModel: SimpleMediaViewModel, webViewReference: (WebView) -> Unit ) {
    var isPageLoading by remember { mutableStateOf(true) }

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
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.userAgentString = settings.userAgentString + "AndroidNative"
                overScrollMode = WebView.OVER_SCROLL_NEVER
                addJavascriptInterface(
                    JavascriptBridge(viewModel),
                    "Native"
                )
                loadUrl("https://radio.yuntae.in") //https://radio.yuntae.in
                webViewReference(this)
            }
        },
        modifier = Modifier.fillMaxSize()
    )

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
}
