package com.Huffer342.shiyin.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.Huffer342.shiyin.CompanionGuard
import com.Huffer342.shiyin.MusicLibrary
import com.Huffer342.shiyin.ScheduleScheduler

@Composable
fun StartupSetupDialog(
    activity: ComponentActivity,
    onPermissions: () -> Unit,
    onOpenGuard: () -> Unit,
    onDismiss: () -> Unit
) {
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        revision++
        onPermissions()
        if (it.values.any { granted -> !granted }) error = "未授权的功能暂不可用；可稍后在应用设置中开启，也可只选择音乐目录。"
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    fun open(action: String) {
        try { activity.startActivity(Intent(action, Uri.parse("package:${activity.packageName}"))) }
        catch (_: Exception) { error = "无法打开此设置，请从系统应用信息进入。" }
    }
    val audio = remember(revision) { MusicLibrary.hasPermission(activity) }
    val notification = remember(revision) { NotificationManagerCompat.from(activity).areNotificationsEnabled() }
    val exact = remember(revision) { ScheduleScheduler.canSchedule(activity) }
    val fullscreen = remember(revision) {
        Build.VERSION.SDK_INT < 34 || activity.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val guardStatus = remember(revision) { CompanionGuard.queryStatus(activity) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("权限与后台设置") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("读取本地音乐用于扫描歌曲，通知用于播放控制和闹钟提示。可跳过，之后从设置重新打开。")
                Text("音乐：${if (audio) "已允许" else "未允许"} · 通知：${if (notification) "已允许" else "未允许"}")
                OutlinedButton(onClick = {
                    val missing = buildList {
                        if (!audio) add(MusicLibrary.permission)
                        if (Build.VERSION.SDK_INT >= 33 && !notification) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
                }) { Text("申请音乐与通知权限") }
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 31) open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                }, enabled = !exact) { Text(if (exact) "精确闹钟已允许" else "允许精确闹钟") }
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 34) open(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                }, enabled = !fullscreen) { Text(if (fullscreen) "全屏通知已允许" else "允许全屏闹钟") }
                Text("在 vivo OriginOS 等国产系统上，若从系统最近任务手动上划单应用卡片，系统会强行停止该应用并清空 AlarmManager 闹钟。")
                Text("推荐安装可选的「闹钟守护包」（无桌面图标、无常驻卡片）：安装并完成守护授权后，即使在最近任务中划掉卡片或点击「清除全部」，闹钟仍可准时唤醒。")
                OutlinedButton(onClick = onOpenGuard) {
                    Text(if (guardStatus.ready) "闹钟守护包：已就绪（点击管理）" else if (guardStatus.installed) "闹钟守护包：待完成设置" else "安装可选「闹钟守护包」")
                }
                Text("如果不安装守护包，请使用单应用保活方式：\n1. 给最近任务卡片【加锁】后按 Home 退回桌面（仅防「清除全部」，切勿手动上划）。\n2. 或在应用底部导航栏向上滑动，由应用主动收起卡片。")
                Text("vivo 权限：在应用信息 → 查看所有权限中开启“自启动”“锁屏显示”；在电池 → 后台耗电管理中选择“允许后台高耗电”。")
                OutlinedButton(onClick = { open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) }) { Text("打开应用设置") }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后设置") } })
}
