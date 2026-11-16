package com.Huffer342.shiyin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var alarmMode = false
    private var alarmFocus = false
    private var focusRequest: AudioFocusRequest? = null
    private var deadline = 0L
    private var sleepDeadline = 0L
    private var fadeSeconds = 0
    private var fadeElapsed = 0L
    private var fadeTickAt = 0L
    private val sleepTimer = Runnable {
        if (PlaybackTiming.expired(sleepDeadline, SystemClock.elapsedRealtime())) {
            finishPlayback()
            AppStore.notice(this, "睡眠定时已结束")
        }
    }
    private val fadeTick = object : Runnable {
        override fun run() {
            if (!alarmMode || !player.isPlaying) return
            val now = SystemClock.elapsedRealtime()
            if (fadeTickAt > 0) fadeElapsed += (now - fadeTickAt).coerceAtLeast(0)
            fadeTickAt = now
            player.volume = PlaybackTiming.fadeGain(fadeElapsed, fadeSeconds)
            if (player.volume < 1f) handler.postDelayed(this, 100)
        }
    }
    private var generation = 0
    private var preferredDeviceId: Int? = null
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (alarmMode) preferHeadphones()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == preferredDeviceId }) {
                player.pause()
                preferredDeviceId = null
                player.setPreferredAudioDevice(null)
                AppStore.notice(this@PlaybackService, "耳机已断开，播放已暂停")
            }
        }
    }
    private val stopTimer = Runnable { finishPlayback() }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            player.pause()
            AppStore.notice(this, "播放因其他音频占用而暂停，可手动继续")
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setWakeMode(C.WAKE_MODE_LOCAL)
            setHandleAudioBecomingNoisy(true)
            setAudioAttributes(attributes(false), true)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    reportFailure("播放失败：${error.errorCodeName}。请检查音频权限或重新扫描文件")
                    finishPlayback()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    handler.removeCallbacks(fadeTick)
                    fadeTickAt = 0
                    if (alarmMode && isPlaying) handler.post(fadeTick)
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady && PlaybackTiming.expired(sleepDeadline, SystemClock.elapsedRealtime())) {
                        finishPlayback()
                        return
                    }
                    if (!alarmMode) return
                    if (playWhenReady) {
                        if (deadline > 0 && SystemClock.elapsedRealtime() >= deadline) finishPlayback()
                        else if (!alarmFocus && !requestAlarmFocus()) {
                            pause()
                            AppStore.notice(this@PlaybackService, "无法取得闹钟音频焦点，请稍后重试")
                        }
                    } else releaseAlarmFocus()
                }
            })
        }
        audioManager.registerAudioDeviceCallback(devices, handler)
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIFICATION_ID).build())
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp())
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    return if (controller.uid == Process.myUid()) {
                        MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                .add(SessionCommand(COMMAND_SLEEP, Bundle.EMPTY)).build()).build()
                    } else if (controller.isTrusted) super.onConnect(session, controller)
                    else MediaSession.ConnectionResult.reject()
                }

                override fun onPlayerCommandRequest(
                    session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int
                ): Int {
                    if (playerCommand == Player.COMMAND_STOP) finishPlayback()
                    return SessionResult.RESULT_SUCCESS
                }

                override fun onCustomCommand(
                    session: MediaSession, controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand, args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (controller.uid != Process.myUid() || customCommand.customAction != COMMAND_SLEEP)
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                    val minutes = args.getInt("minutes", -1)
                    if (minutes !in 0..1440 || (minutes > 0 && (alarmMode || player.mediaItemCount == 0)))
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    handler.removeCallbacks(sleepTimer)
                    sleepDeadline = PlaybackTiming.sleepDeadline(SystemClock.elapsedRealtime(), minutes)
                    if (minutes > 0) handler.postDelayed(sleepTimer, minutes * 60_000L)
                    publishTimers()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                override fun onSetMediaItems(
                    mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    if (controller.uid != Process.myUid() || mediaItems.any { !MusicLibrary.canRead(this@PlaybackService, it.mediaId) })
                        return Futures.immediateFailedFuture(SecurityException("音频不可访问"))
                    useManualMode()
                    return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(
                        mediaItems.map { it.buildUpon().setUri(it.mediaId).build() }, startIndex, startPositionMs))
                }

                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>
                ): ListenableFuture<List<MediaItem>> {
                    if (controller.uid != Process.myUid() || alarmMode || mediaItems.any { !MusicLibrary.canRead(this@PlaybackService, it.mediaId) }) {
                        return Futures.immediateFailedFuture(SecurityException("仅允许应用选择本地音频"))
                    }
                    return Futures.immediateFuture(mediaItems.map {
                        it.buildUpon().setUri(it.mediaId).build()
                    })
                }
            }).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_ALARM && intent?.action != ACTION_PREVIEW) {
            return super.onStartCommand(intent, flags, startId)
        }
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, startupChannel())
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("时音").setContentText("正在准备定时音频")
            .setContentIntent(openApp()).setSilent(true).setOngoing(true).build())
        val request = ++generation
        scope.launch {
            try {
                val schedule = if (intent.action == ACTION_ALARM) {
                    withContext(Dispatchers.IO) {
                        ScheduleScheduler.consume(this@PlaybackService,
                            intent.getStringExtra("id") ?: "", intent.getLongExtra("expectedAt", 0))
                    }
                } else {
                    Schedule(
                        title = intent.getStringExtra("title") ?: "试听",
                        uri = intent.getStringExtra("uri") ?: "",
                        hour = 0, minute = 0, stopMinutes = 1440,
                        fadeSeconds = intent.getIntExtra("fadeSeconds", 0)
                    )
                }
                if (request != generation) return@launch
                if (schedule == null) {
                    if (!player.isPlaying) finishPlayback()
                    return@launch
                }
                startAlarm(schedule, intent.action == ACTION_PREVIEW)
            } catch (e: Exception) {
                if (request == generation) {
                    reportFailure(e.message ?: "无法启动定时播放")
                    finishPlayback()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(schedule: Schedule, preview: Boolean) {
        check(MusicLibrary.canRead(this, schedule.uri)) { "音频不可访问，请重新授权或选择目录" }
        player.pause()
        releaseAlarmFocus()
        handler.removeCallbacks(stopTimer)
        handler.removeCallbacks(sleepTimer)
        handler.removeCallbacks(fadeTick)
        sleepDeadline = 0
        fadeSeconds = schedule.fadeSeconds
        fadeElapsed = 0
        fadeTickAt = 0
        player.volume = PlaybackTiming.fadeGain(0, fadeSeconds)
        alarmMode = true
        player.setAudioAttributes(attributes(true), false)
        preferHeadphones()
        val duration = if (preview) 30_000L else schedule.stopMinutes * 60_000L
        deadline = SystemClock.elapsedRealtime() + duration
        publishTimers()
        check(requestAlarmFocus()) { "闹钟音频焦点被拒绝，未开始播放" }
        player.shuffleModeEnabled = false
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.setMediaItem(MediaItem.Builder().setMediaId(schedule.uri).setUri(schedule.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(schedule.title)
                .setArtist(if (preview) "闹钟试听 · 30 秒" else "定时播放 · ${schedule.stopMinutes} 分钟").build())
            .build())
        player.prepare()
        player.play()
        handler.postDelayed(stopTimer, duration)
        AppStore.notice(this, "")
    }

    private fun useManualMode() {
        ++generation
        handler.removeCallbacks(stopTimer)
        player.pause()
        releaseAlarmFocus()
        deadline = 0
        handler.removeCallbacks(fadeTick)
        player.volume = 1f
        alarmMode = false
        publishTimers()
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.setPreferredAudioDevice(null)
        preferredDeviceId = null
        player.setAudioAttributes(attributes(false), true)
    }

    private fun publishTimers() {
        session.setSessionExtras(Bundle().apply {
            putLong("sleepDeadline", sleepDeadline)
            putLong("alarmDeadline", deadline)
        })
    }

    private fun preferHeadphones() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val wired = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            (Build.VERSION.SDK_INT >= 26 && it.type == AudioDeviceInfo.TYPE_USB_HEADSET) }
        val bluetooth = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) }
        val selected = wired ?: bluetooth
        preferredDeviceId = selected?.id
        player.setPreferredAudioDevice(selected)
    }

    private fun reportFailure(message: String) {
        AppStore.notice(this, message)
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("errors", "播放异常", NotificationManager.IMPORTANCE_DEFAULT))
        try {
            manager.notify(1002, NotificationCompat.Builder(this, "errors")
                .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("时音：播放未完成")
                .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(openApp()).setAutoCancel(true).build())
        } catch (_: SecurityException) {
            // The persistent in-app message remains available when notifications are denied.
        }
    }

    private fun requestAlarmFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(focusListener, handler).build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN)
        }
        alarmFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return alarmFocus
    }

    private fun releaseAlarmFocus() {
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        focusRequest = null
        alarmFocus = false
    }

    private fun finishPlayback() {
        ++generation
        handler.removeCallbacks(stopTimer)
        player.pause()
        player.stop()
        player.clearMediaItems()
        handler.removeCallbacks(fadeTick)
        handler.removeCallbacks(sleepTimer)
        player.volume = 1f
        releaseAlarmFocus()
        deadline = 0
        sleepDeadline = 0
        publishTimers()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun startupChannel(): String {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("playback_start", "播放状态", NotificationManager.IMPORTANCE_LOW))
        return "playback_start"
    }

    override fun onDestroy() {
        scope.cancel()
        audioManager.unregisterAudioDeviceCallback(devices)
        handler.removeCallbacksAndMessages(null)
        releaseAlarmFocus()
        session.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_ALARM = "com.Huffer342.shiyin.ALARM"
        const val ACTION_PREVIEW = "com.Huffer342.shiyin.PREVIEW"
        const val COMMAND_SLEEP = "com.Huffer342.shiyin.SET_SLEEP"
        private const val NOTIFICATION_ID = 1001

        private fun attributes(alarm: Boolean) = AudioAttributes.Builder()
            .setUsage(if (alarm) C.USAGE_ALARM else C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()


    }
}
