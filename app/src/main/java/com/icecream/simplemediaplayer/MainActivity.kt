package com.icecream.simplemediaplayer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.icecream.simplemediaplayer.data.model.RadioStation
import com.icecream.simplemediaplayer.data.preferences.StartTab
import com.icecream.simplemediaplayer.data.preferences.ThemeMode
import com.icecream.simplemediaplayer.player.RadioService
import com.icecream.simplemediaplayer.ui.PlayerViewModel
import com.icecream.simplemediaplayer.ui.SettingsViewModel
import com.icecream.simplemediaplayer.ui.StationViewModel
import com.icecream.simplemediaplayer.ui.components.StationListItem
import com.icecream.simplemediaplayer.ui.navigation.BottomNavDestinations
import com.icecream.simplemediaplayer.ui.theme.RadioTheme
import com.icecream.simplemediaplayer.util.PermissionHelper
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showBatteryDialog = true
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )
                ) {
                    showSettingsDialog = true
                } else {
                    showBatteryDialog = true
                }
            }
        }
    }

    var showNotificationDialog by mutableStateOf(false)
        private set

    var showBatteryDialog by mutableStateOf(false)
        private set

    var showSettingsDialog by mutableStateOf(false)
        private set

    var showExitDialog by mutableStateOf(false)

    var showNetworkDialog by mutableStateOf(false)

    var showDataRestoreDialog by mutableStateOf(false)
        private set

    var favoritesToRestore by mutableStateOf<List<String>>(emptyList())
        private set

    private var initialSetupComplete by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsState()

            RadioTheme(
                themeMode = settings.themeMode,
                uiScale = settings.uiScale
            ) {
                AppRoot(activity = this@MainActivity)
                PermissionDialogs()
                DataRestoreDialog(settingsViewModel)
            }

            // 데이터 복원 체크 - 한 번만 실행
            LaunchedEffect(Unit) {
                val actualSettings = settingsViewModel.settings.drop(1).first()
                Log.e("MainActivity", "needsDataRestore (actual): ${actualSettings.needsDataRestore}")
                if (actualSettings.needsDataRestore != false) {
                    checkAndRestoreOldFavorites(settingsViewModel)
                }
            }

            // 초기 설정 완료 체크 및 광고 활성화
            LaunchedEffect(
                showNotificationDialog,
                showBatteryDialog,
                showSettingsDialog,
                showDataRestoreDialog,
                showNetworkDialog
            ) {
                // 모든 다이얼로그가 닫혔고, 아직 초기 설정이 완료되지 않았으면
                if (!showNotificationDialog &&
                    !showBatteryDialog &&
                    !showSettingsDialog &&
                    !showDataRestoreDialog &&
                    !showNetworkDialog &&
                    !initialSetupComplete) {

                    initialSetupComplete = true
                    // 앱 오프닝 광고 활성화
                    (application as? RadioApp)?.appOpenAdManager?.enableAdShowing()
                    Log.d("MainActivity", "Initial setup complete, app open ad enabled")
                }
            }
        }
        checkPermissions()
    }

    private fun checkPermissions() {
        if (!PermissionHelper.hasNotificationPermission(this)) {
            showNotificationDialog = true
        } else {
            if (!PermissionHelper.isBatteryOptimizationDisabled(this)) {
                showBatteryDialog = true
            }
        }
    }

    private fun checkAndRestoreOldFavorites(settingsViewModel: SettingsViewModel) {
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // localStorage에서 favoritesData 가져오기
                    evaluateJavascript("javascript:window.localStorage.getItem('favoritesData')") { value ->
                        handleLocalStorageResult(value, settingsViewModel)
                    }
                }
            }
        }
        webView.loadUrl("https://radio.yuntae.in/")
    }

    private fun handleLocalStorageResult(value: String?, settingsViewModel: SettingsViewModel) {
        Log.e("MainActivity", "handleLocalStorageResult: $value")
        if (value.isNullOrEmpty() || value == "null") {
            // 데이터가 없으면 복원 불필요로 설정
            settingsViewModel.setNeedsDataRestore(false)
            return
        }

        try {
            val jsonString = value.trim('"').replace("\\\"", "\"")
            if (jsonString.isEmpty() || jsonString == "null") {
                settingsViewModel.setNeedsDataRestore(false)
                return
            }

            val favorites = parseJsonArray(jsonString)

            if (favorites.isEmpty()) {
                settingsViewModel.setNeedsDataRestore(false)
            } else {
                favoritesToRestore = favorites
                showDataRestoreDialog = true
            }
        } catch (e: Exception) {
            settingsViewModel.setNeedsDataRestore(false)
        }
    }

    private fun parseJsonArray(jsonString: String): List<String> {
        if (!jsonString.startsWith("[") || !jsonString.endsWith("]")) {
            return emptyList()
        }

        val content = jsonString.substring(1, jsonString.length - 1)
        if (content.isEmpty()) {
            return emptyList()
        }

        val items = mutableListOf<String>()
        var current = StringBuilder()
        var inString = false
        var escaped = false

        for (char in content) {
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' -> {
                    escaped = true
                }
                char == '"' -> {
                    if (inString) {
                        items.add(current.toString())
                        current = StringBuilder()
                    }
                    inString = !inString
                }
                inString -> {
                    current.append(char)
                }
            }
        }

        return items
    }

    @Composable
    private fun DataRestoreDialog(settingsViewModel: SettingsViewModel) {
        if (showDataRestoreDialog) {
            val stationViewModel: StationViewModel = hiltViewModel()

            AlertDialog(
                onDismissRequest = { },
                title = { Text("자주 듣는 스테이션 복원") },
                text = {
                    Text("더 쾌적한 경험을 위해 앱 구조가 변경되었습니다.\n\n기존에 저장되어 있던 자주 듣는 스테이션 목록 ${favoritesToRestore.size}개를 불러올까요?")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDataRestoreDialog = false
                        // 복원 수행
                        stationViewModel.addFavoritesByTitle(favoritesToRestore)
                        settingsViewModel.setNeedsDataRestore(false)
                        favoritesToRestore = emptyList()
                    }) {
                        Text("불러오기")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDataRestoreDialog = false
                        settingsViewModel.setNeedsDataRestore(false)
                        favoritesToRestore = emptyList()
                    }) {
                        Text("건너뛰기")
                    }
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            )
        }
    }

    @Composable
    private fun PermissionDialogs() {
        if (showNotificationDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("알림 권한 필요") },
                text = { Text("미디어 재생 컨트롤을 알림 영역에 표시하려면 알림 권한이 필요합니다.") },
                confirmButton = {
                    TextButton(onClick = {
                        showNotificationDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }) {
                        Text("허용")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showNotificationDialog = false
                        showBatteryDialog = true
                    }) {
                        Text("나중에")
                    }
                }
            )
        }

        if (showBatteryDialog) {
            AlertDialog(
                onDismissRequest = { showBatteryDialog = false },
                title = { Text("배터리 최적화 제외") },
                text = { Text("백그라운드에서 끊김 없이 라디오를 재생하려면 배터리 최적화를 제외해주세요.") },
                confirmButton = {
                    TextButton(onClick = {
                        showBatteryDialog = false
                        PermissionHelper.requestBatteryOptimizationExemption(this)
                    }) {
                        Text("설정")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showBatteryDialog = false
                    }) {
                        Text("나중에")
                    }
                }
            )
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("알림 권한 거부됨") },
                text = { Text("시스템 설정에서 알림 권한을 직접 허용할 수 있습니다.") },
                confirmButton = {
                    TextButton(onClick = {
                        showSettingsDialog = false
                        val intent =
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", packageName, null)
                            }
                        startActivity(intent)
                    }) {
                        Text("설정 열기")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showSettingsDialog = false
                    }) {
                        Text("취소")
                    }
                }
            )
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("앱 종료") },
                text = { Text("백그라운드 재생을 계속할까요? \n완전 종료를 선택하면 재생이 중지됩니다.") },
                confirmButton = {
                    TextButton(onClick = {
                        // keep background playback
                        (application as? RadioApp)?.allowBackgroundPlayback = true
                        showExitDialog = false
                        finish()
                    }) { Text("백그라운드 계속") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        // full stop
                        (application as? RadioApp)?.stopAllPlayback()
                        val stopIntent = Intent(this, RadioService::class.java).apply {
                            action = RadioService.ACTION_STOP_ALL
                        }
                        startService(stopIntent)
                        stopService(Intent(this, RadioService::class.java))
                        showExitDialog = false
                        finishAndRemoveTask()
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }) { Text("완전 종료") }
                }
            )
        }

        if (showNetworkDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("네트워크 연결 없음") },
                text = { Text("인터넷에 연결되어 있지 않습니다.\n라디오를 재생하려면 네트워크 연결이 필요합니다.") },
                confirmButton = {
                    TextButton(onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                        startActivity(intent)
                    }) {
                        Text("네트워크 설정")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        finishAndRemoveTask()
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }) {
                        Text("앱 종료")
                    }
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
    }
}

@UnstableApi
@Composable
private fun AppRoot(activity: MainActivity) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsState()

    // Network monitoring
    val app = activity.application as RadioApp
    val isNetworkConnected by app.networkMonitor.isConnected.collectAsState(
        initial = app.networkMonitor.isCurrentlyConnected()
    )

    LaunchedEffect(isNetworkConnected) {
        activity.showNetworkDialog = !isNetworkConnected
    }

    LaunchedEffect(settings.autoPlayOnStart) {
        playerViewModel.restoreLastPlayback(settings.autoPlayOnStart)
    }

    LaunchedEffect(settings.startTab) {
        val targetRoute = when (settings.startTab) {
            "favorites" -> BottomNavDestinations.FAVORITES.route
            else -> BottomNavDestinations.HOME.route
        }
        navController.navigate(targetRoute) {
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val playerState by playerViewModel.state.collectAsState()
                    val timerViewModel: com.icecream.simplemediaplayer.ui.SleepTimerViewModel = hiltViewModel()
                    val timerState by timerViewModel.state.collectAsState()

                    com.icecream.simplemediaplayer.ui.components.player.PlayerBottomSheet(
                        playerState = playerState,
                        timerState = timerState,
                        remainingTimeString = timerViewModel.getRemainingTimeString(),
                        onPlayPauseClick = { playerViewModel.togglePlayPause() },
                        onPreviousClick = { playerViewModel.playPrev() },
                        onNextClick = { playerViewModel.playNext() },
                        onFavoriteClick = { playerViewModel.toggleFavorite() },
                        onStartTimer = { hours, minutes ->
                            timerViewModel.startTimer(hours, minutes)
                        },
                        onCancelTimer = {
                            timerViewModel.cancelTimer()
                        }
                    )
                    AppBottomBar(navController)
                }
            }
        ) { innerPadding ->
            NavGraph(
                navController = navController,
                contentPadding = innerPadding,
                playerViewModel = playerViewModel,
                settingsViewModel = settingsViewModel
            )
        }
    }

    androidx.activity.compose.BackHandler { activity.showExitDialog = true }
}

@Composable
private fun AppBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    fun navigateSingleTop(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { inclusive = false }
            launchSingleTop = true
            restoreState = false
        }
    }

    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == BottomNavDestinations.HOME.route,
            onClick = { navigateSingleTop(BottomNavDestinations.HOME.route) },
            icon = { Icon(Icons.Filled.Radio, contentDescription = null) },
            label = { Text("전체") }
        )
        NavigationBarItem(
            selected = currentRoute == BottomNavDestinations.FAVORITES.route,
            onClick = { navigateSingleTop(BottomNavDestinations.FAVORITES.route) },
            icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
            label = { Text("자주 듣는") }
        )
        NavigationBarItem(
            selected = currentRoute == BottomNavDestinations.SETTINGS.route,
            onClick = { navigateSingleTop(BottomNavDestinations.SETTINGS.route) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("설정") }
        )
    }
}

@UnstableApi
@Composable
private fun NavGraph(
    navController: NavHostController,
    contentPadding: PaddingValues,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel
) {
    NavHost(
        navController = navController,
        enterTransition = {
            androidx.compose.animation.slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = androidx.compose.animation.core.tween(300)
            )
        },
        exitTransition = {
            androidx.compose.animation.slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = androidx.compose.animation.core.tween(300)
            )
        },
        popEnterTransition = {
            androidx.compose.animation.slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = androidx.compose.animation.core.tween(300)
            )
        },
        popExitTransition = {
            androidx.compose.animation.slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = androidx.compose.animation.core.tween(300)
            )
        },
        startDestination = BottomNavDestinations.HOME.route,
        modifier = Modifier.padding(contentPadding)
    ) {
        composable(BottomNavDestinations.HOME.route) {
            val stationViewModel: StationViewModel = hiltViewModel()
            HomeScreen(stationViewModel, playerViewModel)
        }
        composable(BottomNavDestinations.FAVORITES.route) {
            val stationViewModel: StationViewModel = hiltViewModel()
            FavoritesScreen(stationViewModel, playerViewModel)
        }
        composable(BottomNavDestinations.SETTINGS.route) {
            SettingsScreen(settingsViewModel)
        }
    }
}

@UnstableApi
@Composable
private fun HomeScreen(
    stationViewModel: StationViewModel,
    playerViewModel: PlayerViewModel
) {
    val ui by stationViewModel.state.collectAsState()
    val playerState by playerViewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "스테이션",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp)
        )

        CityTabs(
            cities = ui.cities,
            selectedCity = ui.selectedCity,
            onSelect = stationViewModel::selectCity
        )

        AdMobBanner(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val currentList =
            if (ui.selectedCity == null) emptyList() else ui.groupedStations[ui.selectedCity].orEmpty()

        LaunchedEffect(ui.selectedCity, currentList) {
            playerViewModel.setCurrentStations(currentList)
        }

        StationList(
            stations = currentList,
            favorites = ui.favorites,
            currentPlayingUrl = playerState.currentStation?.url,
            isPlaying = playerState.isPlaying,
            onToggleFavorite = { stationViewModel.toggleFavorite(it) },
            onClick = { playerViewModel.playStation(it) }
        )
    }
}

@UnstableApi
@Composable
private fun FavoritesScreen(
    stationViewModel: StationViewModel,
    playerViewModel: PlayerViewModel
) {
    val ui by stationViewModel.state.collectAsState()
    val playerState by playerViewModel.state.collectAsState()
    val allStations = ui.groupedStations.values.flatten()
    val favList = allStations
        .filter { ui.favorites.contains(it.url) }
        // URL을 기준으로 중복 제거
        .associateBy { it.url }
        .values
        .toList()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "자주 듣는",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(16.dp)
        )

        AdMobBanner(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LaunchedEffect(favList) {
            playerViewModel.setCurrentStations(favList)
        }

        if (favList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 80.dp).alpha(0.5f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "즐겨찾기한 스테이션이 없습니다",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "목록에서 하트 아이콘을 눌러 추가하세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            StationList(
                stations = favList,
                favorites = ui.favorites,
                currentPlayingUrl = playerState.currentStation?.url,
                isPlaying = playerState.isPlaying,
                onToggleFavorite = { stationViewModel.toggleFavorite(it) },
                onClick = { playerViewModel.playStation(it) }
            )
        }
    }
}

// City code -> display label mapping
private val cityLabel: Map<String, String> = mapOf(
    "seoul" to "수도권",
    "busan" to "부산·울산·경남",
    "daegu" to "대구·경북",
    "gwangju" to "광주·전남",
    "daejeon" to "대전·세종·충남",
    "jeonbuk" to "전북",
    "gangwon" to "강원",
    "chungbuk" to "충북",
    "jeju" to "제주"
)

@Composable
private fun CityTabs(
    cities: List<String>,
    selectedCity: String?,
    onSelect: (String) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedCity, cities) {
        val target = selectedCity ?: cities.firstOrNull()
        val idx = if (target != null) cities.indexOf(target) else -1
        if (idx >= 0) listState.animateScrollToItem((idx - 1).coerceAtLeast(0))
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(cities) { _, city ->
            val isSelected =
                city == selectedCity || (selectedCity == null && cities.firstOrNull() == city)
            val label = cityLabel[city] ?: city
            FilterChip(
                selected = isSelected,
                onClick = { if (!isSelected) onSelect(city) },
                label = { Text(label) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun StationList(
    stations: List<RadioStation>,
    favorites: Set<String>,
    currentPlayingUrl: String?,
    isPlaying: Boolean,
    onToggleFavorite: (String) -> Unit,
    onClick: (RadioStation) -> Unit
) {
    LazyColumn {
        items(stations) { station ->
            StationListItem(
                station = station,
                isFavorite = favorites.contains(station.url),
                isPlaying = currentPlayingUrl == station.url && isPlaying,
                onToggleFavorite = { onToggleFavorite(station.url) },
                onClick = { onClick(station) }
            )
        }
    }
}

@Composable
private fun SettingsScreen(settingsViewModel: SettingsViewModel) {
    val settings by settingsViewModel.settings.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "설정",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            AdMobBanner(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )


        }

        item { Spacer(modifier = Modifier.height(16.dp)) }


        // 테마 설정
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "테마",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ThemeMode.values().forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode.value,
                            onClick = { settingsViewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.values().size
                            )
                        ) {
                            Text(mode.displayName)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // UI 크기 설정
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "화면 크기",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "앱 내 글자와 UI 요소의 크기를 조절합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val scaleOptions = listOf(
                    0.85f to "작게",
                    1.0f to "보통",
                    1.15f to "크게",
                    1.3f to "매우 크게"
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    scaleOptions.forEachIndexed { index, (scale, label) ->
                        SegmentedButton(
                            selected = (settings.uiScale - scale).let { kotlin.math.abs(it) < 0.01f },
                            onClick = { settingsViewModel.setUiScale(scale) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = scaleOptions.size
                            )
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // 시작 탭 설정
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "시작 탭",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "앱 실행 시 처음으로 표시할 탭을 선택하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StartTab.values().forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = settings.startTab == tab.value,
                            onClick = { settingsViewModel.setStartTab(tab) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = StartTab.values().size
                            )
                        ) {
                            Text(tab.displayName)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // 자동 재생 설정
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "앱 실행 시 자동 재생",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "앱 실행 시 마지막으로 재생하던 스테이션을 자동으로 재생합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.autoPlayOnStart,
                    onCheckedChange = { settingsViewModel.setAutoPlayOnStart(it) }
                )
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 20.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }


    }
}

@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-7178712602934912/8801591425" // Demo ad unit ID: ca-app-pub-3940256099942544/9214589741
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                setAdUnitId(adUnitId)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
