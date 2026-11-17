package com.Huffer342.shiyin.ui

import android.os.Build
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.Huffer342.shiyin.*

@Composable
fun SettingsScreen(
    padding: PaddingValues, listState: LazyListState, audioAllowed: Boolean, exactAllowed: Boolean,
    notificationsAllowed: Boolean, calendarYears: List<Int>, busy: Boolean, theme: ThemeSettings,
    onAudio: () -> Unit, onExact: () -> Unit, onNotifications: () -> Unit, onAppSettings: () -> Unit,
    onUpdateCalendar: () -> Unit, onTheme: () -> Unit, onHelp: () -> Unit
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("settings_list"),
        contentPadding = padding) {
        item {
            SettingRow(Icons.Rounded.Palette, "主题", "${theme.palette.label} · ${theme.mode.label}", onTheme)
            HorizontalDivider()
            SectionLabel("权限")
            SettingRow(Icons.Rounded.LibraryMusic, "系统音乐库", if (audioAllowed) "已允许" else "未授权", onAudio)
            SettingRow(Icons.Rounded.Alarm, "闹钟和提醒", if (exactAllowed) "已允许" else "未授权", onExact)
            SettingRow(Icons.Rounded.NotificationsNone, "通知", if (notificationsAllowed) "已开启" else "未开启", onNotifications)
            val context = LocalContext.current
            SettingRow(Icons.Rounded.Fullscreen, "全屏闹钟", "设置锁屏响铃显示权限", {
                val action = if (Build.VERSION.SDK_INT >= 34) android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
                    else android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                runCatching { context.startActivity(android.content.Intent(action, android.net.Uri.parse("package:${context.packageName}"))) }
            })
            SettingRow(Icons.Rounded.BatterySaver, "权限与后台", "授权、自启动与后台锁定", onAppSettings)
            HorizontalDivider()
            SectionLabel("工作日日历")
            SettingRow(Icons.Rounded.CalendarMonth, "中国大陆", if (calendarYears.isEmpty()) "未下载" else calendarYears.joinToString("、") + " 年", onUpdateCalendar)
            TextButton(onClick = onUpdateCalendar, enabled = !busy, modifier = Modifier.padding(horizontal = 12.dp)) { Text("更新日历") }
            HorizontalDivider()
            SettingRow(Icons.AutoMirrored.Rounded.HelpOutline, "使用说明", "", onHelp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeScreen(settings: ThemeSettings, padding: PaddingValues, onChange: (ThemeSettings) -> Unit) {
    var customDialog by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("theme_screen"), contentPadding = padding) {
        item {
            Column(Modifier.padding(16.dp)) {
                SectionLabel("显示模式")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(settings.mode == mode, { onChange(settings.copy(mode = mode)) }, label = { Text(mode.label) })
                    }
                }
            }
        }
        item { SectionLabel("配色") }
        items(ThemePalette.entries.size) { index ->
            val palette = ThemePalette.entries[index]
            val color = if (palette == ThemePalette.CUSTOM) parseThemeColor(settings.customHex)!! else palette.seed
            ListItem(
                headlineContent = { Text(palette.label) },
                supportingContent = { if (palette == ThemePalette.CUSTOM) Text(settings.customHex) },
                leadingContent = { Box(Modifier.size(32.dp).clip(CircleShape).background(Color(color))) },
                trailingContent = {
                    if (settings.palette == palette && !settings.wallpaper) Icon(Icons.Rounded.Check, "已选择")
                    else if (palette == ThemePalette.CUSTOM) Icon(Icons.Rounded.Edit, "编辑颜色")
                },
                modifier = Modifier.selectable(selected = settings.palette == palette && !settings.wallpaper, role = Role.RadioButton) {
                    if (palette == ThemePalette.CUSTOM) customDialog = true
                    else onChange(settings.copy(palette = palette, wallpaper = false))
                })
        }
        if (Build.VERSION.SDK_INT >= 31) item {
            HorizontalDivider()
            ListItem(headlineContent = { Text("壁纸动态配色") },
                trailingContent = { Switch(settings.wallpaper, null) },
                modifier = Modifier.toggleable(settings.wallpaper, role = Role.Switch) { onChange(settings.copy(wallpaper = it)) })
        }
        item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("预览")
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                        Text("歌曲", Modifier.padding(start = 16.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {}) { Icon(Icons.Rounded.PlayArrow, null); Text("播放") }
                    OutlinedButton(onClick = {}) { Text("播放列表") }
                }
            }
        }
    }
    if (customDialog) {
        var hex by rememberSaveable { mutableStateOf(settings.customHex) }
        var error by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { customDialog = false }, title = { Text("自定义主色") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(hex, { hex = it; error = false }, label = { Text("#RRGGBB") }, singleLine = true,
                    isError = error, modifier = Modifier.testTag("theme_hex"),
                    supportingText = { if (error) Text("请输入六位十六进制颜色") })
                parseThemeColor(hex)?.let { value -> Box(Modifier.fillMaxWidth().height(40.dp).clip(MaterialTheme.shapes.small).background(Color(value))) }
            } },
            confirmButton = { TextButton(onClick = {
                if (parseThemeColor(hex) == null) error = true
                else { onChange(settings.copy(palette = ThemePalette.CUSTOM, customHex = hex.trim().uppercase(), wallpaper = false)); customDialog = false }
            }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { customDialog = false }) { Text("取消") } })
    }
}

@Composable
fun PlayerHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("使用手册") },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("1 · 收录音乐", style = MaterialTheme.typography.titleMedium)
            Text("在目录栏目选择本地文件夹，或允许读取音乐库。先试听确认音频可访问。")
            Text("2 · 创建闹钟与倒计时", style = MaterialTheme.typography.titleMedium)
            Text("闹钟栏目可设置时刻；更多菜单中的倒计时按秒开始。计时结束后才播放，睡眠定时则是到点停止播放。请检查通知、闹钟和提醒权限。")
            Text("3 · 正确进入后台", style = MaterialTheme.typography.titleMedium)
            Text("保存任务后，在底部栏目栏向上滑动，等待通知出现，App 会自己关闭卡片。不要通过系统上划清理来退出。")
            Text("4 · 全屏响铃", style = MaterialTheme.typography.titleMedium)
            Text("在设置中允许全屏闹钟。vivo 若仍不显示，请在应用信息 → 查看所有权限中允许“锁屏显示”。使用手机时系统通常显示横幅；未获全屏授权时可点通知进入。收起页面不停止音频，停止按钮结束本次播放。")
            Text("5 · 注意事项", style = MaterialTheme.typography.titleMedium)
            Text("指定目录包含子目录。使用时自动检测文件变化，重新打开补扫；停止监控不删除文件。")
            Text("睡眠定时只停止当前播放，不取消后续闹钟。新闹钟开始会结束这次睡眠定时。")
            Text("闹钟使用闹钟音量，淡入不修改系统音量。单次上限包含暂停与等待时间，试听最多 30 秒。")
            Text("在底部栏目栏向上滑动，或在歌曲首页按返回，可由 App 自己关闭卡片。有有效闹钟时会保留后台等待通知；全部闹钟取消后结束等待。")
            Text("手动上划卡片或强行停止，部分系统会停用整个应用，使闹钟失效。请使用 App 的后台运行入口；长期锁屏和不同系统仍需实测。")
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } })
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) },
        supportingContent = { if (description.isNotBlank()) Text(description) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) }, modifier = Modifier.clickable(onClick = onClick))
}
