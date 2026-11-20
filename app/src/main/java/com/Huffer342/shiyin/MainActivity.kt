package com.Huffer342.shiyin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Huffer342.shiyin.ui.*
import com.Huffer342.shiyin.ui.theme.ShiYinTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent {
            val model: PlayerViewModel = viewModel()
            ShiYinTheme(settings = model.theme) { PlayerApp(this, model) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerApp(activity: ComponentActivity, model: PlayerViewModel) {
    val pager = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val folderState = rememberSaveableStateHolder()
    val tab = pager.currentPage
    var page by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
        var playlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var folderUri by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftUri by rememberSaveable { mutableStateOf("") }
    var draftTitle by rememberSaveable { mutableStateOf("") }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var sleepDialog by rememberSaveable { mutableStateOf(false) }
    val helpPrefs = remember { activity.getSharedPreferences("shiyin", android.content.Context.MODE_PRIVATE) }
    var help by rememberSaveable { mutableStateOf(false) }
    var setup by rememberSaveable { mutableStateOf(false) }
    var guardDialog by rememberSaveable { mutableStateOf(false) }
    var guardFirstAlarmPrompt by rememberSaveable { mutableStateOf(false) }
    var countdownDialog by rememberSaveable { mutableStateOf(false) }
    var rootMenu by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var nameAction by rememberSaveable { mutableStateOf<String?>(null) }
    var addTrackUri by rememberSaveable { mutableStateOf<String?>(null) }
    var manageSongs by rememberSaveable { mutableStateOf(false) }
    var deletePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var removeFolderUri by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteAlarmId by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionDialog by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val libraryList = rememberLazyListState()
    val scheduleList = rememberLazyListState()
    val settingsList = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val currentPlaylist = model.playlists.find { it.id == playlistId }
    val currentFolder = model.folderScans.find { it.folder.uri == folderUri }
    val primary = page.isEmpty()

    fun settings(action: String = Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
        try { activity.startActivity(Intent(action, Uri.parse("package:${activity.packageName}"))) }
        catch (_: Exception) { model.notify("无法打开系统设置") }
    }
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) model.addFolder(uri)
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        revision++
        if (granted) model.scan()
        else permissionDialog = if (ActivityCompat.shouldShowRequestPermissionRationale(activity, MusicLibrary.permission)) "rationale" else "settings"
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        revision++
        if (!granted) permissionDialog = "notificationSettings"
    }
    val initialPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        revision++
        if (MusicLibrary.hasPermission(activity)) model.scan()
        help = !helpPrefs.getBoolean("manual_seen_v1", false)
    }
    LaunchedEffect(Unit) {
        if (!helpPrefs.getBoolean("runtime_requested_v1", false)) {
            val missing = buildList {
                if (!MusicLibrary.hasPermission(activity)) add(MusicLibrary.permission)
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    add(Manifest.permission.POST_NOTIFICATIONS)
            }
            helpPrefs.edit().putBoolean("runtime_requested_v1", true).apply()
            if (missing.isNotEmpty()) initialPermissions.launch(missing.toTypedArray())
            else help = !helpPrefs.getBoolean("manual_seen_v1", false)
        }
    }
    val requestAudio = {
        if (model.folders.isNotEmpty() || MusicLibrary.hasPermission(activity)) model.scan()
        else directoryPicker.launch(null)
    }
    val requestExact = {
        if (Build.VERSION.SDK_INT >= 31) settings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        else model.notify("无需单独授权")
    }
    fun resolveSong(uri: String): Song? = model.songs.find { it.uri == uri }
        ?: model.playlists.flatMap { it.songs }.find { it.uri == uri }
        ?: model.queueSongs().find { it.uri == uri }

    fun newAlarm(song: Song? = null) {
        val selected = song ?: model.songs.firstOrNull()
        if (selected == null) { page = ""; scope.launch { pager.scrollToPage(0) }; model.notify("请先选择音频目录或扫描歌曲") }
        else { draftUri = selected.uri; draftTitle = selected.title; editorId = "new"; focus.clearFocus() }
    }
    fun runInBackground() {
        if (closing) return
        if (!model.loaded || model.busy) { model.notify("正在更新，请稍后重试"); return }
        closing = true
        scope.launch {
            try {
                AlarmStandbyService.prepareForExit(activity)
                activity.finishAndRemoveTask()
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                model.notify("后台等待启动超时，页面已保留，请重试")
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                model.notify(error.message ?: "后台运行失败，页面已保留")
            } finally { closing = false }
        }
    }

    fun up() {
        when {
            page == "theme" -> page = "settings"
            page.isNotEmpty() -> page = ""
            playlistId != null -> playlistId = null
            folderUri != null -> folderUri = null
            tab != 0 -> scope.launch { pager.animateScrollToPage(0) }
        }
    }

    LaunchedEffect(activity, model) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.setMonitoring(true)
            try {
                revision++; model.refresh()
                while (true) { model.poll(); delay(500) }
            } finally { model.setMonitoring(false) }
        }
    }
    LaunchedEffect(model.playback.alarm) {
        activity.volumeControlStream = if (model.playback.alarm) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
    }
    LaunchedEffect(model.message) {
        val message = model.message
        if (message.isNotBlank()) {
            snackbar.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Long)
            model.consumeMessage(message)
        }
    }
    LaunchedEffect(model.playback.title) {
        if (model.playback.title.isEmpty()) { showPlayer = false; showQueue = false; sleepDialog = false }
    }
    LaunchedEffect(model.playlists, model.loaded) {
        if (model.loaded && playlistId != null && currentPlaylist == null) playlistId = null
    }
    BackHandler(enabled = editorId == null && !showPlayer && primary && playlistId == null && folderUri == null && tab == 0) { runInBackground() }
    BackHandler(enabled = editorId == null && !showPlayer && (page.isNotEmpty() || playlistId != null || folderUri != null || tab != 0)) { up() }
    BackHandler(enabled = editorId == null && showPlayer && !showQueue && !sleepDialog) { showPlayer = false }

    var backgroundProgress by remember { mutableFloatStateOf(0f) }
    val backgroundAction by rememberUpdatedState({ runInBackground() })
    val audioAllowed = remember(revision) { MusicLibrary.hasPermission(activity) }
    val exactAllowed = remember(revision) { ScheduleScheduler.canSchedule(activity) }
    val notificationsAllowed = remember(revision) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val guardStatus = remember(revision) { CompanionGuard.queryStatus(activity) }

    LaunchedEffect(primary, tab, help, setup, guardDialog, guardStatus.installed, model.loaded) {
        if (model.loaded && primary && tab == 3 && !help && !setup && !guardDialog &&
            !guardStatus.installed && !helpPrefs.getBoolean("guard_prompt_seen_v1", false)) {
            helpPrefs.edit().putBoolean("guard_prompt_seen_v1", true).apply()
            guardFirstAlarmPrompt = true
            guardDialog = true
        }
    }

    BackgroundTransition({ if (closing) 1f else backgroundProgress }, closing) {
        when {
            editorId != null -> {
                val original = model.schedules.find { it.id == editorId }
                if (editorId != "new" && original == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (!model.loaded) CircularProgressIndicator()
                        else TextButton(onClick = { editorId = null }) { Text("闹钟不存在，返回") }
                    }
                    BackHandler { editorId = null }
                } else key(editorId) {
                    ScheduleEditorScreen(original,
                        if (original == null) Song(draftUri, draftTitle, "", 0) else Song(original.uri, original.title, "", 0),
                        model.songs, model.busy, { model.playback.alarm && model.playback.playing && model.playback.uri == it },
                        model.calendarYears, { editorId = null }, { song, fade -> model.preview(song, fade) }, model::stop,
                        { model.save(it) { editorId = null; showPlayer = false; page = ""; scope.launch { pager.scrollToPage(3) } } })
                }
            }
            showPlayer -> NowPlayingScreen(model.playback, { showPlayer = false }, model::toggle,
                { model.stop(); showPlayer = false }, model::previous, model::next, model::seek, model::repeat, model::shuffle,
                { showQueue = true }, { resolveSong(model.playback.uri)?.let { newAlarm(it) } },
                { sleepDialog = true }, { nameAction = "queue" })
            else -> BoxWithConstraints {
                val wide = maxWidth >= 600.dp
                val labels = listOf("歌曲", "播放列表", "目录", "闹钟")
                val icons = listOf(Icons.Rounded.MusicNote, Icons.AutoMirrored.Rounded.PlaylistPlay, Icons.Rounded.Folder, Icons.Rounded.Alarm)
                val title = when (page) {
                    "settings" -> "设置"; "theme" -> "主题"; "alarms" -> "闹钟"
                    else -> currentPlaylist?.name ?: labels[tab]
                }
                val navigate: (Int) -> Unit = {
                    focus.clearFocus(); page = ""; playlistId = null; folderUri = null
                    scope.launch { pager.animateScrollToPage(it) }
                }
                Row(Modifier.fillMaxSize()) {
                    if (wide && primary) NavigationRail {
                        labels.forEachIndexed { index, label ->
                            NavigationRailItem(selected = tab == index, onClick = { navigate(index) },
                                icon = { Icon(icons[index], null) }, label = { Text(label) }, modifier = Modifier.testTag("nav_$index"))
                        }
                    }
                    Scaffold(modifier = Modifier.weight(1f), contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(snackbar) },
                        topBar = {
                            TopAppBar(title = { Text(title, maxLines = 1) },
                                navigationIcon = {
                                    if (!primary || playlistId != null)
                                        ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", { up() })
                                }, actions = {
                                    if (primary && currentPlaylist == null && currentFolder == null && tab == 0)
                                        ActionIcon(Icons.Rounded.Search, "搜索", { searchVisible = !searchVisible })
                                    if (currentPlaylist != null && primary)
                                        ActionIcon(Icons.Rounded.EditNote, "管理歌曲", { manageSongs = true })
                                    Box {
                                        ActionIcon(Icons.Rounded.MoreVert, "更多", { rootMenu = true })
                                        DropdownMenu(rootMenu, { rootMenu = false }) {
                                            if (primary && currentPlaylist != null) {
                                                DropdownMenuItem(text = { Text("重命名") }, onClick = { rootMenu = false; nameAction = "rename" })
                                                DropdownMenuItem(text = { Text("删除播放列表") }, onClick = { rootMenu = false; deletePlaylistId = playlistId })
                                            }
                                            DropdownMenuItem(text = { Text("倒计时闹钟") }, onClick = { rootMenu = false; countdownDialog = true })
                                            if (primary) DropdownMenuItem(text = { Text("刷新音乐库") }, onClick = { rootMenu = false; requestAudio() })
                                            if (page != "settings") DropdownMenuItem(text = { Text("设置") }, onClick = { rootMenu = false; page = "settings" })
                                            DropdownMenuItem(text = { Text("帮助") }, onClick = { rootMenu = false; help = true })
                                        }
                                    }
                                })
                        },
                        bottomBar = {
                            Column {
                                if (model.playback.title.isNotEmpty()) MiniPlayer(model.playback,
                                    { focus.clearFocus(); showPlayer = true }, model::toggle, model::next)
                                if (!wide && primary) BackgroundNavigation(backgroundAction, { backgroundProgress = it }) {
                                    labels.forEachIndexed { index, label ->
                                        NavigationBarItem(selected = tab == index, onClick = { navigate(index) },
                                            icon = { Icon(icons[index], null) }, label = { Text(label) }, modifier = Modifier.testTag("nav_$index"))
                                    }
                                } else Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                            }
                        },
                        floatingActionButton = {
                            when {
                                primary && tab == 3 -> FloatingActionButton({ newAlarm() }, Modifier.size(48.dp).testTag("add_schedule")) { Icon(Icons.Rounded.Add, "添加闹钟") }
                                primary && tab == 1 && currentPlaylist == null -> FloatingActionButton({ nameAction = "new" }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Add, "新建播放列表") }
                                primary && tab == 2 && currentFolder == null -> FloatingActionButton({ directoryPicker.launch(null) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.CreateNewFolder, "选择目录") }
                            }
                        }) { padding ->
                        when (page) {
                            "settings" -> SettingsScreen(padding, settingsList, audioAllowed, exactAllowed, notificationsAllowed,
                                guardStatus, model.calendarYears, model.busy, model.theme,
                                { if (audioAllowed) settings() else audioPermission.launch(MusicLibrary.permission) },
                                requestExact, {
                                    if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else settings()
                                }, { setup = true }, {
                                    guardFirstAlarmPrompt = false
                                    guardDialog = true
                                }, model::updateCalendar, { page = "theme" }, { help = true })
                            "theme" -> ThemeScreen(model.theme, padding, model::updateTheme)
                            else -> when {
                                currentPlaylist != null -> PlaylistDetailScreen(currentPlaylist, padding,
                                    { song, shuffled -> model.play(song, currentPlaylist.songs, shuffled) },
                                    { song -> model.changePlaylist(currentPlaylist.id) { it.removing(song.uri) } },
                                    { from, to -> model.changePlaylist(currentPlaylist.id) { it.moving(from, to) } },
                                    { manageSongs = true }, { song, next -> model.enqueue(song, next) })
                                currentFolder != null -> folderState.SaveableStateProvider(currentFolder.folder.uri) {
                                    FolderBrowserScreen(currentFolder, padding, model.playlists, { folderUri = null },
                                        { song, queue -> model.play(song, queue) },
                                        { id, songs, done -> model.changePlaylist(id, onChanged = done) { it.adding(songs) } },
                                        { name, songs, done -> model.createPlaylist(name, songs) { done() } })
                                }
                                else -> HorizontalPager(pager, Modifier.fillMaxSize().padding(padding)
                                    .consumeWindowInsets(padding).testTag("main_pager")) { index ->
                                    val inner = PaddingValues(0.dp)
                                    when (index) {
                                        1 -> PlaylistListScreen(model.playlists, inner, { playlistId = it }, { nameAction = "new" })
                                        2 -> FolderListScreen(model.folders, model.folderScans, inner,
                                            { directoryPicker.launch(null) }, { folderUri = it }, { removeFolderUri = it.uri })
                                        3 -> ScheduleListScreen(model.schedules, model.busy, exactAllowed, inner, scheduleList,
                                            { newAlarm() }, { if (!model.busy) {
                                                if (it.countdownSeconds > 0) model.save(restartCountdown(it, System.currentTimeMillis()))
                                                else if (it.countdownAt > 0) { countdownDialog = true; model.notify("旧倒计时未保存总时长，请重新设置一次") }
                                                else editorId = it.id
                                            } }, { schedule, enabled ->
                                                if (enabled && schedule.countdownSeconds > 0) model.save(restartCountdown(schedule, System.currentTimeMillis()))
                                                else model.save(schedule.copy(enabled = enabled))
                                            },
                                            { deleteAlarmId = it.id }, { model.preview(Song(it.uri, it.title, "", 0), it.fadeSeconds) }, requestExact)
                                        else -> LibraryScreen(model.songs, model.playback, audioAllowed || model.folders.isNotEmpty(),
                                            model.connected, model.busy, inner, libraryList, query, { query = it },
                                            requestAudio, { song, queue -> model.play(song, queue) }, { newAlarm(it) },
                                            { addTrackUri = it.uri }, { song, next -> model.enqueue(song, next) },
                                            searchVisible = searchVisible,
                                            sourceError = model.folderScans.firstOrNull { it.error.isNotEmpty() }?.error.orEmpty())
                                    }
                                }
                            }
                        }
                        if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = padding.calculateTopPadding()))
                    }
                }
            }
        }
        if (editorId != null || showPlayer) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp))
    }
    if (setup) StartupSetupDialog(activity, { model.scan(); revision++ }, {
        setup = false
        guardFirstAlarmPrompt = false
        guardDialog = true
    }) {
        helpPrefs.edit().putBoolean("setup_seen_v1", true).apply()
        setup = false
        help = !helpPrefs.getBoolean("manual_seen_v1", false)
    }
    if (guardDialog) GuardSetupDialog(activity, guardFirstAlarmPrompt, {
        revision++
        model.syncGuard()
    }) {
        guardDialog = false
        guardFirstAlarmPrompt = false
        revision++
    }
    if (showQueue) QueueSheet(model.playback, { showQueue = false }, { model.selectQueue(it) },
        model::removeQueue, model::moveQueue, { showQueue = false; nameAction = "queue" })
    if (sleepDialog) SleepTimerDialog(model.playback.sleepRemaining, { sleepDialog = false }) {
        model.setSleepTimer(it); sleepDialog = false
    }
    if (help) PlayerHelpDialog { helpPrefs.edit().putBoolean("manual_seen_v1", true).apply(); help = false }
    if (countdownDialog) CountdownDialog(model.songs, model.busy, { countdownDialog = false }) { value ->
        model.save(value) { countdownDialog = false; page = ""; scope.launch { pager.scrollToPage(3) } }
    }
    if (manageSongs && currentPlaylist != null) ManageSongsSheet(currentPlaylist, model.songs, { manageSongs = false }) { values ->
        model.changePlaylist(currentPlaylist.id, onChanged = { manageSongs = false }) { it.copy(songs = values) }
    }
    if (addTrackUri != null && nameAction == null) AddToPlaylistDialog(model.playlists,
        { addTrackUri = null }, { id ->
            resolveSong(addTrackUri!!)?.let { song -> model.changePlaylist(id, onChanged = { addTrackUri = null }) { it.adding(listOf(song)) } }
        }, { nameAction = "track" })
    nameAction?.let { action ->
        key(action, playlistId) {
            PlaylistNameDialog(if (action == "rename") "重命名" else "新建播放列表",
                if (action == "rename") currentPlaylist?.name.orEmpty() else "", { nameAction = null }) { name ->
                if (action == "rename" && currentPlaylist != null)
                    model.changePlaylist(currentPlaylist.id, onChanged = { nameAction = null }) { it.copy(name = name) }
                else {
                    val values = when (action) { "queue" -> model.queueSongs(); "track" -> listOfNotNull(addTrackUri?.let { resolveSong(it) }); else -> emptyList() }
                    model.createPlaylist(name, values) { id ->
                        nameAction = null; addTrackUri = null; playlistId = id; scope.launch { pager.scrollToPage(1) }; page = ""; showPlayer = false
                    }
                }
            }
        }
    }
    model.playlists.find { it.id == deletePlaylistId }?.let { list ->
        ConfirmDelete("删除播放列表？", list.name, { deletePlaylistId = null }) { model.deletePlaylist(list.id); deletePlaylistId = null }
    }
    model.folders.find { it.uri == removeFolderUri }?.let { folder ->
        ConfirmDelete("停止监控目录？", folder.name, { removeFolderUri = null }) {
            model.removeFolder(folder); removeFolderUri = null; folderUri = null
        }
    }
    model.schedules.find { it.id == deleteAlarmId }?.let { alarm ->
        ConfirmDelete("删除闹钟？", "${timeText(alarm.hour, alarm.minute)} · ${alarm.title}", { deleteAlarmId = null }) {
            model.delete(alarm); deleteAlarmId = null
        }
    }
    permissionDialog?.let { type ->
        AlertDialog(onDismissRequest = { permissionDialog = null }, title = { Text("权限未开启") },
            text = { Text(if (type == "notificationSettings") "可在系统设置开启通知。" else "可授权读取系统音乐库，也可通过目录选择器指定音乐目录。") },
            confirmButton = { TextButton(onClick = {
                permissionDialog = null
                if (type == "rationale") audioPermission.launch(MusicLibrary.permission) else settings()
            }) { Text(if (type == "rationale") "授权" else "系统设置") } },
            dismissButton = { TextButton(onClick = { permissionDialog = null }) { Text("取消") } })
    }
}

@Composable
private fun ConfirmDelete(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
