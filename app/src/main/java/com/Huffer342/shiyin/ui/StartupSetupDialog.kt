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
import androidx.compose.ui.Alignment
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
    onChanged: () -> Unit,
    onOpenGuard: () -> Unit,
    onDismiss: () -> Unit
) {
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    val preferences = remember { activity.getSharedPreferences("shiyin", android.content.Context.MODE_PRIVATE) }
    var oemConfirmed by remember { mutableStateOf(preferences.getBoolean("oem_setup_confirmed_v1", false)) }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        revision++
        onChanged()
        if (!granted) error = "系统音乐库未授权。仍可使用自选目录，也可稍后从运行环境重新授权。"
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        revision++
        onChanged()
        if (!granted) error = "通知未授权，播放控制、后台等待和闹钟提醒可能无法显示。"
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                revision++
                onChanged()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    fun open(intent: Intent) {
        runCatching { activity.startActivity(intent) }
            .onFailure { error = "无法打开此设置，请从系统应用信息进入。" }
    }

    fun open(action: String) {
        open(Intent(action, Uri.parse("package:${activity.packageName}")))
    }

    fun openAutoStartSettings() {
        runCatching {
            activity.startActivity(
                Intent("com.iqoo.secure.BGSTARTUPMANAGER")
                    .addCategory(Intent.CATEGORY_DEFAULT)
            )
        }.onFailure { open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) }
    }

    val audio = remember(revision) { MusicLibrary.hasPermission(activity) }
    val notification = remember(revision) { NotificationManagerCompat.from(activity).areNotificationsEnabled() }
    val exact = remember(revision) { ScheduleScheduler.canSchedule(activity) }
    val fullscreen = remember(revision) {
        Build.VERSION.SDK_INT < 34 ||
            activity.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val guardStatus = remember(revision) { CompanionGuard.queryStatus(activity) }
    val completed = listOf(audio, notification, exact, fullscreen, guardStatus.ready, oemConfirmed).count { it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("运行环境配置 · $completed/6") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("全部完成后，系统音乐扫描、后台播放、锁屏响铃和划卡后的守护唤醒才能完整工作。所有步骤均可跳过并稍后继续。")

                Text("1 · 系统音乐库", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = { audioPermission.launch(MusicLibrary.permission) },
                    enabled = !audio,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (audio) "已允许读取系统音乐库" else "允许读取系统音乐库")
                }

                Text("2 · 通知", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    },
                    enabled = !notification,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (notification) "通知已开启" else "允许通知")
                }

                Text("3 · 闹钟授权", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = { if (Build.VERSION.SDK_INT >= 31) open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) },
                    enabled = !exact,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (exact) "精确闹钟已允许" else "允许精确闹钟")
                }
                OutlinedButton(
                    onClick = { if (Build.VERSION.SDK_INT >= 34) open(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) },
                    enabled = !fullscreen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (fullscreen) "全屏闹钟已允许" else "允许锁屏全屏响铃")
                }

                Text("4 · 闹钟守护包", style = MaterialTheme.typography.titleSmall)
                Text("安装同签名守护包并完成守护授权后，手动划掉时音卡片或清除全部仍可唤醒响铃。")
                OutlinedButton(onClick = onOpenGuard, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            guardStatus.ready -> "守护包已就绪 · 点击管理"
                            guardStatus.installed -> "继续配置守护包"
                            else -> "安装并配置闹钟守护包"
                        }
                    )
                }
                if (guardStatus.installed) {
                    Text(
                        "签名：${if (guardStatus.signatureMatches) "通过" else "不匹配"} · " +
                            "精确闹钟：${if (guardStatus.canScheduleExactAlarms) "已允许" else "未允许"} · " +
                            "通知：${if (guardStatus.notificationsEnabled) "已开启" else "未开启"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text("5 · OriginOS 后台设置", style = MaterialTheme.typography.titleSmall)
                Text("以下守护包组合已在 vivo OriginOS 5 实测通过。系统不提供可靠检测接口，完成后需要手动确认。")
                Text("① 在自启动管理中允许“时音闹钟守护”。")
                OutlinedButton(onClick = { openAutoStartSettings() }, modifier = Modifier.fillMaxWidth()) {
                    Text("打开 vivo 自启动管理")
                }
                Text("② 打开守护包应用信息，开启“关联启动”，并将后台耗电设为“允许后台高耗电”。")
                OutlinedButton(
                    onClick = { open(CompanionGuard.appSettingsIntent()) },
                    enabled = guardStatus.installed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (guardStatus.installed) "打开守护包应用信息" else "请先安装守护包")
                }
                Text("③ 时音需开启“锁屏显示”。时音的自启动、关联启动和允许后台高耗电仅建议开启，不计入必要完成度。")
                OutlinedButton(
                    onClick = { open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("打开时音应用信息")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = oemConfirmed,
                        onCheckedChange = { checked ->
                            oemConfirmed = checked
                            preferences.edit().putBoolean("oem_setup_confirmed_v1", checked).apply()
                            onChanged()
                        }
                    )
                    Text("守护包后台设置和时音锁屏显示已完成")
                }

                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后设置") } }
    )
}
