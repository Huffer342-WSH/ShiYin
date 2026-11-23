package com.Huffer342.shiyin.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.Huffer342.shiyin.CompanionGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GuardSetupDialog(
    activity: ComponentActivity,
    firstAlarmPrompt: Boolean,
    onSyncGuard: () -> Unit,
    onDismiss: () -> Unit
) {
    var revision by remember { mutableIntStateOf(0) }
    var pendingInstall by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun launchInstaller() {
        if (installing) return
        if (!CompanionGuard.hasBundledApk(activity)) {
            error = "当前构建未内置守护包，请使用 Debug 安装包或单独安装同签名的守护组件。"
            return
        }
        if (!CompanionGuard.canRequestPackageInstalls(activity)) {
            pendingInstall = true
            error = "请在系统设置中允许「时音」安装应用后返回，将自动继续安装守护包。"
            val intent = CompanionGuard.unknownSourcesIntent(activity)
            if (intent != null) {
                runCatching { activity.startActivity(intent) }
                    .onFailure { error = "无法打开安装权限设置，请从系统设置手动允许安装未知应用。" }
            }
            return
        }
        installing = true
        error = ""
        scope.launch {
            runCatching {
                val intent = withContext(Dispatchers.IO) { CompanionGuard.prepareInstallerIntent(activity) }
                activity.startActivity(intent)
            }.onFailure {
                error = it.message ?: "启动守护包安装失败"
            }
            installing = false
        }
    }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                revision++
                val nowStatus = CompanionGuard.queryStatus(activity)
                if (nowStatus.installed && nowStatus.signatureMatches) {
                    pendingInstall = false
                    onSyncGuard()
                } else if (pendingInstall && CompanionGuard.canRequestPackageInstalls(activity)) {
                    pendingInstall = false
                    launchInstaller()
                }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    val status = remember(revision) { CompanionGuard.queryStatus(activity) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (firstAlarmPrompt && !status.installed) "开启防划掉闹钟守护（可选）" else "闹钟守护包")
        },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (firstAlarmPrompt && !status.installed) {
                    Text(
                        "在 vivo 等国产系统上，若在系统最近任务中手动上划卡片或点击「清除全部」，系统会停止单应用并清空闹钟。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "安装配套的轻量「时音闹钟守护」后：无桌面图标、无常驻任务卡片，即使划掉时音卡片或清理全部后台，闹钟仍可准时唤醒响铃。也可稍后在「设置 → 闹钟守护包」中随时安装或卸载。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "守护组件为可选独立组件：无桌面图标、无常驻最近任务卡片，用于在最近任务被手动上划或一键清理后唤醒时音响铃。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    buildString {
                        append("安装状态：")
                        append(if (status.installed) "已安装" else "未安装（当前使用单应用模式）")
                        if (status.installed) {
                            append("\n签名校验：")
                            append(if (status.signatureMatches) "通过" else "不匹配（请卸载后从本应用重装）")
                            if (status.signatureMatches) {
                                append("\n守护精确闹钟：")
                                append(if (status.canScheduleExactAlarms) "已允许" else "未允许（需进入守护设置开启）")
                                append("\n守护通知：")
                                append(if (status.notificationsEnabled) "已开启" else "未开启（需进入守护设置开启）")
                                append("\n已同步未来闹钟：${status.activeCount} 个")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!status.installed) {
                    Button(
                        onClick = { launchInstaller() },
                        enabled = !installing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (installing) "正在准备安装包…" else "安装守护包")
                    }
                } else {
                    if (status.signatureMatches) {
                        Button(
                            onClick = {
                                runCatching { activity.startActivity(CompanionGuard.setupIntent()) }
                                    .onFailure { error = "无法打开守护设置页面：${it.message}" }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (status.ready) "打开守护设置与自启动引导" else "继续守护授权（精确闹钟与通知）")
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching { activity.startActivity(CompanionGuard.appSettingsIntent()) }
                                    .onFailure { error = "无法打开守护系统应用信息" }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("配置守护自启动 / 关联启动 / 后台耗电")
                        }
                        Text(
                            "vivo / OriginOS 必看：除允许守护精确闹钟外，还须在「守护系统应用信息」中开启「自启动」「关联启动」并将电池后台耗电设为「允许后台高耗电」，否则系统省电会延迟或拦截唤醒。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (status.hasBundledApk) {
                        OutlinedButton(
                            onClick = { launchInstaller() },
                            enabled = !installing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("覆盖安装配套守护包")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching { activity.startActivity(CompanionGuard.uninstallIntent()) }
                                .onFailure { error = "无法发起卸载：${it.message}" }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("卸载守护包")
                    }
                }

                Text(
                    "卸载说明：卸载时音主应用后，守护包会立即清空已同步闹钟；若已授予守护通知权限，守护包会弹出通知提示一键卸载自身。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (status.installed) "完成" else "暂不安装")
            }
        }
    )
}
