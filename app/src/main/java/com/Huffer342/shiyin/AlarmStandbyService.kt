package com.Huffer342.shiyin

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** User-visible waiting service; AlarmManager remains responsible for waking at the due time. */
class AlarmStandbyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences("shiyin", MODE_PRIVATE) }
    private var refreshJob: Job? = null
    private var closing = false
    private var mediaSession: android.media.session.MediaSession? = null
    private val changes = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "schedules") refresh()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "onCreate pid=${Process.myPid()}")
        mediaSession = android.media.session.MediaSession(this, "ShiYinStandby").apply {
            setPlaybackState(
                android.media.session.PlaybackState.Builder()
                    .setState(android.media.session.PlaybackState.STATE_PAUSED, 0L, 0f)
                    .setActions(android.media.session.PlaybackState.ACTION_PLAY or android.media.session.PlaybackState.ACTION_PAUSE)
                    .build()
            )
            setMetadata(
                android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "定时播放已就绪")
                    .build()
            )
            isActive = true
        }
        preferences.registerOnSharedPreferenceChangeListener(changes)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand pid=${Process.myPid()} flags=$flags startId=$startId intentNull=${intent == null}")
        @Suppress("DEPRECATION")
        val reply = intent?.getParcelableExtra<ResultReceiver>("reply")
        try {
            if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "闹钟后台等待", NotificationManager.IMPORTANCE_LOW))
            val notification = notification("正在检查闹钟")
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(ID, notification)
            closing = false
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ScheduleScheduler.reschedule(this@AlarmStandbyService)
                    }
                    val schedules = snapshot()
                    check(ScheduleScheduler.canSchedule(this@AlarmStandbyService)) { "请先允许闹钟和提醒" }
                    check(schedules.none { it.enabled && it.nextAt <= 0 }) { "部分闹钟尚未注册，请返回闹钟页处理" }
                    update(schedules)
                    reply?.send(0, Bundle.EMPTY)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    val message = error.message ?: "无法启动闹钟后台等待"
                    Log.w(TAG, "onStartCommand inner error: $message", error)
                    AppStore.notice(this@AlarmStandbyService, message)
                    reply?.send(1, Bundle().apply { putString("error", message) })
                    close()
                }
            }
        } catch (error: Exception) {
            val message = error.message ?: "无法启动闹钟后台等待"
            Log.w(TAG, "onStartCommand outer error: $message", error)
            AppStore.notice(this, message)
            reply?.send(1, Bundle().apply { putString("error", message) })
            close()
        }
        // System-managed restart only; this cannot override force-stop or vendor package suspension.
        return if (closing) START_NOT_STICKY else START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved pid=${Process.myPid()}")
        runCatching { ScheduleScheduler.reschedule(this, force = false) }
        super.onTaskRemoved(rootIntent)
    }

    private suspend fun snapshot() = withContext(Dispatchers.IO) {
        synchronized(AppStore.lock) { AppStore.schedules(this@AlarmStandbyService) }
    }

    private fun refresh() {
        if (closing) return
        refreshJob?.cancel()
        refreshJob = scope.launch {
            try { update(snapshot()) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                AppStore.notice(this@AlarmStandbyService, error.message ?: "读取闹钟失败")
                close()
            }
        }
    }

    private fun update(schedules: List<Schedule>) {
        val now = System.currentTimeMillis()
        handler.removeCallbacksAndMessages(null)
        if (!ScheduleScheduler.canSchedule(this) || !needsAlarmStandby(schedules, now, playbackStarting)) {
            close()
            return
        }
        val next = standbyNextAt(schedules, now)
        val text = if (next == null || next <= now) "正在准备闹钟"
            else "下次播放：" + java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(next))
        getSystemService(NotificationManager::class.java).notify(ID, notification(text))
        next?.let {
            handler.postDelayed({ refresh() }, (it + 120_001 - now).coerceAtLeast(1_000))
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("定时播放已就绪").setContentText(text).setContentIntent(open)
            .setSilent(true).setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }

    private fun close() {
        closing = true
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        mediaSession?.let {
            it.isActive = false
            it.release()
        }
        mediaSession = null
        preferences.unregisterOnSharedPreferenceChangeListener(changes)
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ShiYinAlarm"
        private const val CHANNEL = "alarm_standby"
        private const val ID = 1004
        private var instance: AlarmStandbyService? = null
        @Volatile
        private var playbackStarting = false

        fun isPlaybackPreparing(): Boolean = playbackStarting

        // Called on the main thread, after promotion and before consuming a one-shot schedule.
        fun playbackPreparing(value: Boolean) {
            playbackStarting = value
            instance?.refresh()
        }

        fun sync(context: Context, schedules: List<Schedule>) {
            val app = context.applicationContext
            val active = instance
            if (active != null) {
                active.refresh()
                return
            }
            if (!shouldRestoreStandby(schedules, System.currentTimeMillis())) return
            if (!ScheduleScheduler.canSchedule(app)) return
            if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
            if (Build.VERSION.SDK_INT >= 26 &&
                app.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
                return
            }
            app.getSharedPreferences("shiyin", MODE_PRIVATE).edit()
                .putBoolean("standby_requested", true).apply()
            runCatching {
                Log.i(TAG, "sync starting AlarmStandbyService pid=${Process.myPid()}")
                ContextCompat.startForegroundService(app, Intent(app, AlarmStandbyService::class.java))
            }.onFailure {
                Log.w(TAG, "sync startForegroundService failed: ${it.message}", it)
            }
        }

        suspend fun restoreAfterBoot(context: Context) {
            val schedules = synchronized(AppStore.lock) { AppStore.schedules(context) }
            if (shouldRestoreStandby(schedules, System.currentTimeMillis())) prepareForExit(context)
        }

        suspend fun prepareForExit(context: Context) {
            val app = context.applicationContext
            val schedules = withContext(Dispatchers.IO) {
                synchronized(AppStore.lock) { AppStore.schedules(app) }
            }
            if (schedules.none { it.enabled }) return
            check(ScheduleScheduler.canSchedule(app)) { "请先允许闹钟和提醒，再进入后台运行" }
            check(schedules.none { it.enabled && it.nextAt <= 0 }) { "部分闹钟尚未注册，请返回闹钟页处理" }
            check(standbyNextAt(schedules, System.currentTimeMillis()) != null) { "闹钟已过期，请刷新后重试" }
            check(NotificationManagerCompat.from(app).areNotificationsEnabled()) { "请先允许通知，再进入后台运行" }
            if (Build.VERSION.SDK_INT >= 26) {
                check(app.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE) {
                    "请在系统设置中开启闹钟后台等待通知"
                }
            }
            withTimeout(8_000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                        override fun onReceiveResult(resultCode: Int, data: Bundle?) {
                            if (!continuation.isActive) return
                            if (resultCode == 0) {
                                app.getSharedPreferences("shiyin", MODE_PRIVATE).edit()
                                    .putBoolean("standby_requested", true).apply()
                                continuation.resume(Unit)
                            }
                            else continuation.resumeWithException(IllegalStateException(data?.getString("error") ?: "后台等待启动失败"))
                        }
                    }
                    try {
                        ContextCompat.startForegroundService(app,
                            Intent(app, AlarmStandbyService::class.java).putExtra("reply", receiver))
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        }
    }
}
