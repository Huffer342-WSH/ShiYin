package com.Huffer342.shiyin.alarmprobe

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ProbeActivity : Activity() {
    private lateinit var status: TextView
    private val alarms get() = getSystemService(AlarmManager::class.java)
    private val prefs get() = getSharedPreferences("probe", 0)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }
        panel.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(32, bars.top + 32, 32, bars.bottom + 32)
            insets
        }
        status = TextView(this).apply { textSize = 18f }
        panel.addView(status)
        fun button(label: String, action: () -> Unit) {
            panel.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        button("授权通知") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        button("授权精确闹钟") {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
        button("20 秒后播放 · 无等待服务") { schedule(false) }
        button("20 秒后播放 · 前台等待服务") { schedule(true) }
        button("移除当前卡片 · 保留已启动的服务") {
            record(this, "self_remove_task")
            finishAndRemoveTask()
        }
        button("取消预约并停止") {
            alarms.cancel(operation())
            prefs.edit().remove("run").remove("due").commit()
            stopService(Intent(this, ProbeService::class.java))
            stopService(Intent(this, WaitingService::class.java))
            record(this, "cancel")
            refresh()
        }
        setContentView(panel)
        record(this, "activity_create")
    }

    private fun operation(token: String = "") = PendingIntent.getForegroundService(
        this, 1, Intent(this, ProbeService::class.java).setAction("PLAY").putExtra("run", token),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun schedule(keepWaiting: Boolean) {
        if (!alarms.canScheduleExactAlarms() || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            status.text = "请先允许通知和精确闹钟"
            return
        }
        stopService(Intent(this, ProbeService::class.java))
        stopService(Intent(this, WaitingService::class.java))
        val token = UUID.randomUUID().toString()
        val due = System.currentTimeMillis() + 20_000
        try {
            val show = PendingIntent.getActivity(this, 0, Intent(this, ProbeActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            check(prefs.edit().putString("run", token).putLong("due", due).commit())
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(due, show), operation(token))
            record(this, "scheduled", "due=$due durationMs=60000 waiting=$keepWaiting asset=dawn-aac.m4a")
            if (keepWaiting) startForegroundService(Intent(this, WaitingService::class.java).putExtra("run", token))
            refresh()
        } catch (error: Exception) {
            alarms.cancel(operation())
            stopService(Intent(this, WaitingService::class.java))
            prefs.edit().remove("run").remove("due").commit()
            record(this, "schedule_error", error.toString())
            status.text = "预约失败：${error.javaClass.simpleName}"
        }
    }

    private fun refresh() {
        val due = prefs.getLong("due", 0)
        status.text = "实验 targetSdk ${applicationInfo.targetSdkVersion}\n" +
            "精确闹钟：${alarms.canScheduleExactAlarms()}\n" +
            if (due > 0) "本轮时间：${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(due))}" else "没有预约"
    }

    override fun onResume() { super.onResume(); refresh(); record(this, "activity_resume") }
    override fun onPause() { record(this, "activity_pause"); super.onPause() }
    override fun onStop() { record(this, "activity_stop"); super.onStop() }
}
