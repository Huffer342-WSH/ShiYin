package com.Huffer342.shiyin

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class PlaybackUi(
    val title: String = "", val playing: Boolean = false, val position: Long = 0,
    val duration: Long = 0, val alarm: Boolean = false, val repeat: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false, val queue: List<String> = emptyList(), val index: Int = -1,
    val uri: String = "", val artist: String = "", val sleepRemaining: Long = 0,
    val alarmRemaining: Long = 0
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val mutex = Mutex()
    private val preferences = context.getSharedPreferences("shiyin", Context.MODE_PRIVATE)
    private var lastNotice = ""
    private var monitoring = false
    private var libraryDirty = true
    private var lastScanAt = 0L
    private var watchedUris = emptySet<Uri>()
    private var registeredUris = emptySet<Uri>()
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { libraryDirty = true }
    }
    private val taskChanges = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "schedules") viewModelScope.launch {
            mutex.withLock {
                try { schedules = withContext(Dispatchers.IO) { synchronized(AppStore.lock) { AppStore.schedules(context) } } }
                catch (e: Exception) { message = e.message ?: "读取闹钟失败" }
            }
        }
    }
    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var playlists by mutableStateOf<List<MusicPlaylist>>(emptyList()); private set
    var folders by mutableStateOf<List<WatchedFolder>>(emptyList()); private set
    var folderScans by mutableStateOf<List<FolderScan>>(emptyList()); private set
    var schedules by mutableStateOf<List<Schedule>>(emptyList()); private set
    var calendarYears by mutableStateOf<List<Int>>(emptyList()); private set
    var playback by mutableStateOf(PlaybackUi()); private set
    var message by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var connected by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    private val defaultTheme = ThemeSettings()
    var theme by mutableStateOf(ThemeSettings(
        palette = runCatching { ThemePalette.valueOf(preferences.getString("theme_palette", defaultTheme.palette.name)!!) }.getOrDefault(defaultTheme.palette),
        mode = runCatching { ThemeMode.valueOf(preferences.getString("theme_mode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        customHex = preferences.getString("theme_custom", defaultTheme.customHex)!!.takeIf { parseThemeColor(it) != null } ?: defaultTheme.customHex,
        wallpaper = preferences.getBoolean("dynamic_color", false)
    )); private set

    private var controller: MediaController? = null
    private val future = MediaController.Builder(context,
        SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()

    init {
        preferences.registerOnSharedPreferenceChangeListener(taskChanges)
        future.addListener({
            try { controller = future.get(); connected = true; poll() }
            catch (e: Exception) { message = "连接播放服务失败：${e.cause?.message ?: e.message}" }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun operation(showBusy: Boolean = true, block: suspend () -> Unit) {
        viewModelScope.launch {
            mutex.withLock {
                if (showBusy) busy = true
                try { block() } catch (e: Exception) { message = e.message ?: "操作失败" }
                finally { if (showBusy) busy = false }
            }
        }
    }

    fun refresh() = operation {
        withContext(Dispatchers.IO) { ScheduleScheduler.reschedule(context) }
        loadState()
        scanLibrary()
    }

    private suspend fun scanLibrary() {
        val result = withContext(Dispatchers.IO) {
            if (folders.isNotEmpty()) MusicLibrary.scanFolders(context, folders)
            else LibraryScan(if (MusicLibrary.hasPermission(context)) MusicLibrary.scan(context) else emptyList(), emptyList())
        }
        songs = result.songs
        folderScans = result.folders
        watchedUris = result.watchUris
        lastScanAt = SystemClock.elapsedRealtime()
        libraryDirty = false
        configureObservers()
    }

    fun scan(silent: Boolean = false) = operation {
        scanLibrary()
        if (!silent) message = folderScans.firstOrNull { it.error.isNotEmpty() }?.error ?: "已更新 ${songs.size} 首歌曲"
    }

    fun setMonitoring(enabled: Boolean) {
        monitoring = enabled
        configureObservers()
    }

    private fun configureObservers() {
        val desired = if (monitoring) watchedUris + MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else emptySet()
        if (desired == registeredUris) return
        context.contentResolver.unregisterContentObserver(observer)
        registeredUris = desired
        desired.forEach {
            try { context.contentResolver.registerContentObserver(it, true, observer) }
            catch (_: SecurityException) { /* Foreground rescan also covers providers without observation access. */ }
        }
    }

    private suspend fun loadState() {
        withContext(Dispatchers.IO) {
            synchronized(AppStore.lock) {
                playlists = AppStore.playlists(context)
                folders = AppStore.folders(context)
                schedules = AppStore.schedules(context)
                calendarYears = AppStore.calendars(context).keys.sorted()
            }
        }
        val notice = AppStore.notice(context)
        if (notice.isNotEmpty() && notice != lastNotice) { message = notice; lastNotice = notice }
        loaded = true
    }

    fun addFolder(uri: Uri) = operation {
        val hadGrant = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val folder = MusicLibrary.folder(context, uri)
                synchronized(AppStore.lock) {
                    AppStore.saveFolders(context, (AppStore.folders(context) + folder).distinctBy { it.uri })
                }
            } catch (e: Exception) {
                if (!hadGrant) runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                throw e
            }
        }
        loadState(); scanLibrary()
        message = folderScans.firstOrNull { it.error.isNotBlank() }?.error ?: "目录已添加"
    }

    fun removeFolder(folder: WatchedFolder) = operation {
        withContext(Dispatchers.IO) {
            synchronized(AppStore.lock) { AppStore.saveFolders(context, AppStore.folders(context).filterNot { it.uri == folder.uri }) }
        }
        // Existing playlists and alarms keep their granted file access after stopping directory monitoring.
        loadState(); scanLibrary()
        message = "已停止收录此目录"
    }

    fun createPlaylist(name: String, values: List<Song> = emptyList(), onCreated: (String) -> Unit = {}) = operation {
        val playlist = MusicPlaylist(name = name.trim(), songs = values.distinctBy { it.uri })
        withContext(Dispatchers.IO) {
            synchronized(AppStore.lock) { AppStore.savePlaylists(context, AppStore.playlists(context) + playlist) }
        }
        loadState(); onCreated(playlist.id); message = "播放列表已创建"
    }

    fun changePlaylist(id: String, onChanged: () -> Unit = {}, change: (MusicPlaylist) -> MusicPlaylist) = operation {
        withContext(Dispatchers.IO) {
            synchronized(AppStore.lock) {
                val current = AppStore.playlists(context)
                require(current.any { it.id == id }) { "播放列表不存在" }
                AppStore.savePlaylists(context, current.map { if (it.id == id) change(it) else it })
            }
        }
        loadState(); onChanged()
    }

    fun deletePlaylist(id: String) = operation {
        withContext(Dispatchers.IO) {
            synchronized(AppStore.lock) { AppStore.savePlaylists(context, AppStore.playlists(context).filterNot { it.id == id }) }
        }
        loadState(); message = "播放列表已删除"
    }

    fun save(schedule: Schedule, onSaved: () -> Unit = {}) = operation(showBusy = false) {
        require(MusicLibrary.canRead(context, schedule.uri)) { "音频授权失效，请重新选择歌曲或目录" }
        withContext(Dispatchers.IO) { ScheduleScheduler.save(context, schedule) }
        loadState()
        message = schedules.find { it.id == schedule.id }?.status?.takeIf { it.isNotBlank() } ?: "闹钟已保存"
        onSaved()
    }

    fun delete(schedule: Schedule) = operation {
        withContext(Dispatchers.IO) { ScheduleScheduler.delete(context, schedule.id) }
        loadState(); message = "闹钟已删除"
    }

    fun updateCalendar() = operation {
        val outcomes = withContext(Dispatchers.IO) {
            (LocalDate.now().year..LocalDate.now().year + 1).map { year ->
                try { AppStore.updateCalendar(context, year); "${year} 年已更新" }
                catch (e: Exception) { e.message ?: "${year} 年更新失败" }
            }.also { ScheduleScheduler.reschedule(context) }
        }
        loadState(); message = outcomes.joinToString("；")
    }

    fun play(song: Song, queue: List<Song>, shuffled: Boolean = false) = act {
        val index = queue.indexOfFirst { it.uri == song.uri }
        require(index >= 0) { "歌曲不在列表中" }
        require(queue.all { value -> MusicLibrary.canRead(context, value.uri) }) { "列表中有音频授权失效，请重新选择目录或移除失效歌曲" }
        it.setMediaItems(queue.map(Song::mediaItem), index, 0)
        it.shuffleModeEnabled = shuffled
        it.prepare(); it.play()
    }

    fun enqueue(song: Song, next: Boolean = false) = act {
        check(!playback.alarm) { "闹钟播放期间不能修改队列" }
        check(MusicLibrary.canRead(context, song.uri)) { "音频授权失效" }
        if (it.mediaItemCount == 0) { it.setMediaItem(song.mediaItem()); it.prepare(); it.play() }
        else if (next) it.addMediaItem(it.currentMediaItemIndex + 1, song.mediaItem())
        else it.addMediaItem(song.mediaItem())
        message = if (next) "已设为下一首" else "已加入队列"
    }

    fun queueSongs(): List<Song> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).map { index ->
            val item = c.getMediaItemAt(index)
            songs.find { it.uri == item.mediaId } ?: Song(item.mediaId,
                item.mediaMetadata.title?.toString() ?: "音频", item.mediaMetadata.artist?.toString().orEmpty(), 0)
        }
    }

    fun preview(song: Song, fadeSeconds: Int = 0) {
        try {
            check(MusicLibrary.canRead(context, song.uri)) { "音频授权失效" }
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_PREVIEW).putExtra("uri", song.uri)
                .putExtra("title", song.title).putExtra("fadeSeconds", fadeSeconds))
        } catch (e: Exception) { message = e.message ?: "无法试听" }
    }

    fun setSleepTimer(minutes: Int) = act { c ->
        val result = c.sendCustomCommand(SessionCommand(PlaybackService.COMMAND_SLEEP, Bundle.EMPTY),
            Bundle().apply { putInt("minutes", minutes) })
        result.addListener({
            runCatching { result.get() }.onSuccess {
                message = if (it.resultCode == SessionResult.RESULT_SUCCESS) {
                    if (minutes == 0) "睡眠定时已取消" else "$minutes 分钟后停止播放"
                } else "请先手动播放音乐，再设置睡眠定时"
            }.onFailure { message = "睡眠定时设置失败" }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun act(block: (MediaController) -> Unit) {
        try { block(controller ?: error("播放服务尚未连接")); poll() }
        catch (e: Exception) { message = e.message ?: "播放操作失败" }
    }
    fun toggle() = act { if (it.playWhenReady) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } }
    fun stop() = act { it.stop(); it.clearMediaItems() }
    fun previous() = act { it.seekToPreviousMediaItem() }
    fun next() = act { it.seekToNextMediaItem() }
    fun seek(position: Long) = act { it.seekTo(position) }
    fun selectQueue(index: Int) = act { it.seekTo(index, 0); it.play() }
    fun removeQueue(index: Int) = act { it.removeMediaItem(index); if (it.mediaItemCount == 0) it.stop() }
    fun moveQueue(from: Int, to: Int) = act { it.moveMediaItem(from, to) }
    fun repeat() = act { it.repeatMode = (it.repeatMode + 1) % 3 }
    fun shuffle() = act { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun poll() {
        if (monitoring && !busy && (libraryDirty || SystemClock.elapsedRealtime() - lastScanAt >= 15_000)) scan(silent = true)
        val c = controller ?: return
        if (!c.isConnected) { connected = false; return }
        val now = SystemClock.elapsedRealtime()
        playback = PlaybackUi(
            title = c.mediaMetadata.title?.toString().orEmpty(), playing = c.playWhenReady,
            position = c.currentPosition.coerceAtLeast(0),
            duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            alarm = c.audioAttributes.usage == C.USAGE_ALARM, repeat = c.repeatMode, shuffle = c.shuffleModeEnabled,
            queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaMetadata.title?.toString() ?: "音频" },
            index = c.currentMediaItemIndex, uri = c.currentMediaItem?.mediaId.orEmpty(),
            artist = c.mediaMetadata.artist?.toString().orEmpty(),
            sleepRemaining = (c.sessionExtras.getLong("sleepDeadline", 0) - now).coerceAtLeast(0),
            alarmRemaining = (c.sessionExtras.getLong("alarmDeadline", 0) - now).coerceAtLeast(0)
        )
        c.playerError?.let { message = "无法播放：${it.errorCodeName}" }
        val notice = AppStore.notice(context)
        if (notice.isNotEmpty() && notice != lastNotice) { message = notice; lastNotice = notice }
    }

    fun notify(message: String) { this.message = message }
    fun consumeMessage(value: String) {
        if (message == value) message = ""
        synchronized(AppStore.lock) {
            if (AppStore.notice(context) == value) AppStore.notice(context, "")
        }
    }
    fun updateTheme(value: ThemeSettings) {
        theme = value
        preferences.edit().putString("theme_palette", value.palette.name).putString("theme_mode", value.mode.name)
            .putString("theme_custom", value.customHex).putBoolean("dynamic_color", value.wallpaper).apply()
    }

    override fun onCleared() {
        context.contentResolver.unregisterContentObserver(observer)
        preferences.unregisterOnSharedPreferenceChangeListener(taskChanges)
        MediaController.releaseFuture(future)
        super.onCleared()
    }
}
