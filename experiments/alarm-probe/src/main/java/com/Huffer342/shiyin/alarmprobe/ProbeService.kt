package com.Huffer342.shiyin.alarmprobe

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*

class ProbeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var focus: AudioFocusRequest? = null
    private var player: MediaPlayer? = null
    private var wake: PowerManager.WakeLock? = null
    private var ended = false
    private var attempts = 0
    private var started = 0L

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        record(this, "service_delivery", "action=${intent?.action}")
        try {
            val channel = NotificationChannel("probe", "实验播放", NotificationManager.IMPORTANCE_LOW)
            channel.setSound(null, null)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val stop = PendingIntent.getService(this, 2, Intent(this, ProbeService::class.java)
                .setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(this, "probe").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("闹钟实验正在运行").setContentText("本轮最多 60 秒")
                .addAction(Notification.Action.Builder(null, "停止", stop).build()).setOngoing(true).build()
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            record(this, "foreground")
            stopService(Intent(this, WaitingService::class.java))
            if (intent?.action == "STOP") { finish("user_stop"); return START_NOT_STICKY }
            val prefs = getSharedPreferences("probe", 0)
            if (!validRun(intent?.getStringExtra("run"), prefs.getString("run", null),
                    prefs.getLong("due", 0), System.currentTimeMillis())) {
                finish("stale_or_late"); return START_NOT_STICKY
            }
            // A PendingIntent is consumed once; duplicate deliveries must not restart the player.
            prefs.edit().remove("due").commit()
            wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:play").apply { acquire(75_000) }
            record(this, "environment", "interactive=${getSystemService(PowerManager::class.java).isInteractive} " +
                "locked=${getSystemService(KeyguardManager::class.java).isKeyguardLocked} " +
                "alarmVolume=${audio.getStreamVolume(AudioManager.STREAM_ALARM)}/${audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)} " +
                "musicVolume=${audio.getStreamVolume(AudioManager.STREAM_MUSIC)}")
            handler.postDelayed({ finish("watchdog") }, 70_000)
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener({ change ->
                    record(this, "focus_change", "$change")
                    if (change < 0) finish("focus_loss_$change")
                }, handler).build()
            requestFocus()
        } catch (error: Exception) { record(this, "error", error.toString()); finish("exception") }
        return START_NOT_STICKY
    }

    private fun requestFocus() {
        if (ended) return
        val result = audio.requestAudioFocus(focus!!)
        attempts++
        record(this, "focus", "attempt=$attempts result=$result")
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) prepare()
        else if (attempts < 12) handler.postDelayed({ requestFocus() }, 250)
        else finish("focus_denied")
    }

    private fun prepare() {
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setWakeMode(this@ProbeService, PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = true
                setVolume(0.15f, 0.15f)
                assets.openFd("dawn-aac.m4a").use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setOnErrorListener { _, what, extra ->
                    record(this@ProbeService, "player_error", "what=$what extra=$extra")
                    finish("player_error")
                    true
                }
                setOnPreparedListener {
                    if (!ended) {
                        start()
                        started = SystemClock.elapsedRealtime()
                        record(this@ProbeService, "play_started", "duration=$duration session=$audioSessionId")
                        handler.postDelayed({ finish("duration_limit") }, 60_000)
                        tick()
                    }
                }
                prepareAsync()
            }
        } catch (error: Exception) { record(this, "prepare_error", error.toString()); finish("prepare_error") }
    }

    private fun tick() {
        if (ended) return
        player?.let {
            record(this, "playing", "isPlaying=${it.isPlaying} position=${it.currentPosition} " +
                "routeType=${it.routedDevice?.type} elapsed=${SystemClock.elapsedRealtime() - started}")
        }
        handler.postDelayed({ tick() }, 5_000)
    }

    private fun finish(reason: String) {
        if (ended) return
        ended = true
        record(this, "stop", "reason=$reason playedMs=${if (started > 0) SystemClock.elapsedRealtime() - started else 0}")
        release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun release() {
        handler.removeCallbacksAndMessages(null)
        player?.release(); player = null
        focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null
        wake?.let { if (it.isHeld) it.release() }; wake = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) { record(this, "task_removed"); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() { ended = true; release(); record(this, "service_destroy"); super.onDestroy() }
}
