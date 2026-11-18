package com.Huffer342.shiyin.alarmprobe

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*

class WaitingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("waiting", "闹钟实验等待", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
            val open = PendingIntent.getActivity(this, 0, Intent(this, ProbeActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, "waiting")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("等待本轮闹钟")
                .setContentText("点此打开实验页，可取消预约").setContentIntent(open).setOngoing(true).build()
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            val prefs = getSharedPreferences("probe", 0)
            if (!validWaitingRun(intent?.getStringExtra("run"), prefs.getString("run", null),
                    prefs.getLong("due", 0), System.currentTimeMillis())) {
                record(this, "waiting_rejected")
                stopSelf()
                return START_NOT_STICKY
            }
            record(this, "waiting_foreground", "pid=${Process.myPid()} type=systemExempted")
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ record(this, "waiting_timeout"); stopSelf() }, 300_000)
        } catch (error: Exception) {
            record(this, "waiting_error", error.toString())
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        record(this, "waiting_task_removed", "pid=${Process.myPid()}")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        record(this, "waiting_destroy")
        super.onDestroy()
    }
}
